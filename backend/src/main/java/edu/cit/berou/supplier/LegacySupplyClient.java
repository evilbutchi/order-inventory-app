package edu.cit.berou.supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The HTTP adapter for LegacySupply. It owns everything ugly about the
 * conversation: XML bodies, the session header, timeouts, retry with backoff,
 * and the quota. Callers just say "place this order" or "how is this PO".
 *
 * Rules implemented here (Part D):
 *  - every call has a hard timeout (supplier.timeout-ms, default 3000)
 *  - at most supplier.max-attempts (3) attempts per call, exponential backoff + jitter
 *  - a rejected session (any 401 on a normal call) is dropped and re-created, then the call is replayed
 *  - 4xx answers other than 401/429 are final: retrying the same request cannot help
 *  - 429 (quota) is never retried immediately; we stay quiet for a cooldown window
 *  - the X-Request-Id we are given is sent on EVERY attempt, so a retry can never create a second PO
 */
@Component
class LegacySupplyClient {

    private static final Logger log = LoggerFactory.getLogger(LegacySupplyClient.class);
    private static final long QUOTA_COOLDOWN_MS = 30_000;

    private final HttpClient http;
    private final String baseUrl;
    private final String clientId;
    private final String apiKey;
    private final Duration timeout;
    private final int maxAttempts;
    private final long backoffBaseMs;
    private final SessionManager sessions;
    private volatile long quotaBlockedUntilMs = 0;

    LegacySupplyClient(@Value("${supplier.base-url:https://legacysupply.onrender.com/api/v1}") String baseUrl,
                       @Value("${supplier.client-id:}") String clientId,
                       @Value("${supplier.api-key:}") String apiKey,
                       @Value("${supplier.timeout-ms:3000}") long timeoutMs,
                       @Value("${supplier.max-attempts:3}") int maxAttempts,
                       @Value("${supplier.session-max-age-seconds:0}") long sessionMaxAgeSeconds,
                       @Value("${supplier.backoff-base-ms:400}") long backoffBaseMs) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.clientId = clientId;
        this.apiKey = apiKey;
        this.timeout = Duration.ofMillis(timeoutMs);
        this.maxAttempts = Math.max(1, maxAttempts);
        this.backoffBaseMs = backoffBaseMs;
        this.sessions = new SessionManager(sessionMaxAgeSeconds);
        this.http = HttpClient.newBuilder().connectTimeout(this.timeout).build();
    }

    // ---- operations -----------------------------------------------------

    /** Place a purchase order. Safe to call again with the same requestId. */
    LegacyXml.PoDoc placeOrder(String sku, int qtyInCases, String buyerRef, String requestId) {
        String body = LegacyXml.purchaseOrder(sku, qtyInCases, buyerRef);
        Reply reply = call("POST", "/purchase-orders", body, requestId, "placeOrder " + buyerRef);
        return LegacyXml.parsePurchaseOrder(reply.body());
    }

    /** Current status of a PO we already placed. */
    LegacyXml.PoDoc getOrder(String poNumber) {
        String path = "/purchase-orders/" + URLEncoder.encode(poNumber, StandardCharsets.UTF_8);
        Reply reply = call("GET", path, null, null, "getOrder " + poNumber);
        return LegacyXml.parsePurchaseOrder(reply.body());
    }

    // ---- the retry loop -------------------------------------------------

    private record Reply(int status, String body) {
    }

    private Reply call(String method, String path, String body, String requestId, String label) {
        LegacySupplyException last = null;
        boolean sleepBeforeNext = false;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (attempt > 1 && sleepBeforeNext) {
                backoff(attempt);
            }
            sleepBeforeNext = true;

            long blockedFor = quotaBlockedUntilMs - System.currentTimeMillis();
            if (blockedFor > 0) {
                throw LegacySupplyException.backOff("Quota cooldown, " + blockedFor + " ms left; not calling " + label);
            }

            String token = null;
            try {
                token = sessions.token(this::signIn);

                HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl + path))
                        .timeout(timeout)
                        .header("X-LS-Session", token)
                        .header("Accept", "application/xml");
                if (requestId != null) {
                    b.header("X-Request-Id", requestId);
                }
                if ("POST".equals(method)) {
                    b.header("Content-Type", "application/xml")
                            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
                } else {
                    b.GET();
                }

                HttpResponse<String> res = http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int status = res.statusCode();
                log.info("LS {} {} requestId={} attempt={}/{} -> HTTP {}", method, path, requestId, attempt, maxAttempts, status);

                if (status >= 200 && status < 300) {
                    return new Reply(status, res.body());
                }

                LegacyXml.ErrDoc err = LegacyXml.parseError(res.body());

                if (status == 401) {
                    long age = sessions.invalidate(token);
                    log.warn("LS session rejected ({}) after {} s of use; signing in again", err.code(), age);
                    last = LegacySupplyException.transientFailure("Session rejected (" + err.code() + ")", null);
                    sleepBeforeNext = false; // re-auth is not congestion, replay straight away
                    continue;
                }
                if (status == 429) {
                    quotaBlockedUntilMs = System.currentTimeMillis() + QUOTA_COOLDOWN_MS;
                    throw LegacySupplyException.backOff("Quota exceeded (" + err.code() + "); pausing "
                            + (QUOTA_COOLDOWN_MS / 1000) + " s");
                }
                if (status >= 500) {
                    log.warn("LS {} answered {} {}", label, status, err.code());
                    last = LegacySupplyException.transientFailure("LegacySupply " + status + " " + err.code(), null);
                    continue;
                }
                throw LegacySupplyException.rejected(err.code(), status, err.message());

            } catch (LegacySupplyException e) {
                if (!e.isRetryable()) {
                    throw e;
                }
                last = e;
                log.warn("LS {} attempt {}/{} failed: {}", label, attempt, maxAttempts, e.getMessage());
            } catch (IOException e) { // includes HttpTimeoutException and ConnectException
                last = LegacySupplyException.transientFailure(describe(e), e);
                log.warn("LS {} attempt {}/{} failed: {}", label, attempt, maxAttempts, last.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw LegacySupplyException.backOff("Interrupted while calling LegacySupply");
            }
        }
        throw last != null ? last : LegacySupplyException.backOff("No attempt was made for " + label);
    }

    // ---- sign-in --------------------------------------------------------

    /** Called by SessionManager only when a fresh session is needed. */
    private String signIn() {
        if (clientId.isBlank() || apiKey.isBlank()) {
            throw LegacySupplyException.credentials(
                    "LS_CLIENT_ID / LS_API_KEY are not set; cannot sign in to LegacySupply");
        }
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/auth/token"))
                    .timeout(timeout)
                    .header("Content-Type", "application/xml")
                    .header("Accept", "application/xml")
                    .POST(HttpRequest.BodyPublishers.ofString(LegacyXml.authRequest(clientId, apiKey), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = res.statusCode();
            log.info("LS POST /auth/token -> HTTP {}", status);

            if (status >= 200 && status < 300) {
                return LegacyXml.parseSessionToken(res.body());
            }
            LegacyXml.ErrDoc err = LegacyXml.parseError(res.body());
            if (status == 401) {
                throw LegacySupplyException.credentials("LegacySupply rejected our credentials (" + err.code() + ")");
            }
            if (status == 429) {
                quotaBlockedUntilMs = System.currentTimeMillis() + QUOTA_COOLDOWN_MS;
                throw LegacySupplyException.backOff("Quota exceeded during sign-in (" + err.code() + ")");
            }
            if (status >= 500) {
                throw LegacySupplyException.transientFailure("Sign-in got " + status + " " + err.code(), null);
            }
            throw LegacySupplyException.credentials("Sign-in refused (" + err.code() + ", HTTP " + status + ")");
        } catch (IOException e) {
            throw LegacySupplyException.transientFailure("Sign-in failed: " + describe(e), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw LegacySupplyException.backOff("Interrupted while signing in");
        }
    }

    // ---- small helpers --------------------------------------------------

    private void backoff(int nextAttempt) {
        long exp = backoffBaseMs * (1L << Math.min(nextAttempt - 2, 4)); // 400, 800, 1600...
        long jitter = backoffBaseMs > 0 ? ThreadLocalRandom.current().nextLong(backoffBaseMs / 2 + 1) : 0;
        try {
            Thread.sleep(exp + jitter);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String describe(IOException e) {
        return e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
    }
}
