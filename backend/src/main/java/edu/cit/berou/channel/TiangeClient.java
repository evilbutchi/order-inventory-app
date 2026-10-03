package edu.cit.berou.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cit.berou.InstanceIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;


@Component
class TiangeClient {

    private static final Logger log = LoggerFactory.getLogger(TiangeClient.class);

    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();
    private final ChannelProperties props;
    private final InstanceIdentity instanceIdentity;

    TiangeClient(ChannelProperties props, InstanceIdentity instanceIdentity) {
        this.props = props;
        this.instanceIdentity = instanceIdentity;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(props.timeoutMs())).build();
    }

    void heartbeat() {
        var req = new TiangeMessages.HeartbeatRequest(
                props.appName(), instanceIdentity.startedAt().toString(), instanceIdentity.uptimeSeconds());
        call("POST", "/instances/heartbeat", req, TiangeMessages.HeartbeatResponse.class, "heartbeat");
    }

    void putListings(List<TiangeMessages.Listing> listings) {
        if (listings.isEmpty()) {
            return;
        }
        call("PUT", "/listings", listings, Void.class, "putListings");
    }

    void putStock(List<TiangeMessages.StockEntry> entries) {
        if (entries.isEmpty()) {
            return;
        }
        call("PUT", "/stock", entries, Void.class, "putStock");
    }

    TiangeMessages.FeedResponse getFeed(long after, int limit) {
        String path = "/feed?after=" + after + "&limit=" + limit;
        return call("GET", path, null, TiangeMessages.FeedResponse.class, "getFeed");
    }

    
    boolean decide(String tiangeOrderId, String decision, String shopOrderId, String reason) {
        var req = new TiangeMessages.DecisionRequest(decision, shopOrderId, reason);
        return tryCall("POST", "/orders/" + enc(tiangeOrderId) + "/decision", req, "decide " + tiangeOrderId);
    }

    boolean resolve(String tiangeOrderId, String status) {
        var req = new TiangeMessages.ResolutionRequest(status);
        return tryCall("POST", "/orders/" + enc(tiangeOrderId) + "/resolution", req, "resolve " + tiangeOrderId);
    }

    boolean confirmCancellation(String tiangeOrderId) {
        var req = new TiangeMessages.CancellationRequest(true);
        return tryCall("POST", "/orders/" + enc(tiangeOrderId) + "/cancellation", req, "confirmCancellation " + tiangeOrderId);
    }

    
    private boolean tryCall(String method, String path, Object body, String label) {
        try {
            call(method, path, body, Void.class, label);
            return true;
        } catch (ChannelException e) {
            log.warn("Tiangge {} did not succeed: {}", label, e.getMessage());
            return false;
        }
    }

    

    private <T> T call(String method, String path, Object body, Class<T> responseType, String label) {
        ChannelException last = null;

        for (int attempt = 1; attempt <= props.maxAttempts(); attempt++) {
            if (attempt > 1) {
                backoff(attempt);
            }
            try {
                HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(props.baseUrl() + path))
                        .timeout(Duration.ofMillis(props.timeoutMs()))
                        .header("X-Client-Id", props.clientId())
                        .header("Authorization", "Bearer " + props.apiKey())
                        .header("X-Client-Instance", instanceIdentity.instanceId())
                        .header("Accept", "application/json");

                if (body != null) {
                    b.header("Content-Type", "application/json")
                            .method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body), StandardCharsets.UTF_8));
                } else {
                    b.method(method, HttpRequest.BodyPublishers.noBody());
                }

                HttpResponse<String> res = http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int status = res.statusCode();
                log.info("Tiangge {} {} attempt={}/{} -> HTTP {}", method, path, attempt, props.maxAttempts(), status);

                if (status >= 200 && status < 300) {
                    if (responseType == Void.class || res.body() == null || res.body().isBlank()) {
                        return null;
                    }
                    return json.readValue(res.body(), responseType);
                }

                String errBody = res.body();
                String code = parseErrorCode(errBody);

                
                if (status == 409 && ("decision_conflict".equals(code) || "not_backordered".equals(code) || "not_cancelled".equals(code))) {
                    log.info("Tiangge {} already recorded ({}); treating as success", label, code);
                    return null;
                }
                if (status == 503) {
                    last = new ChannelException("Tiangge unavailable (503) for " + label, true);
                    continue;
                }
                if (status >= 500) {
                    last = new ChannelException("Tiangge " + status + " for " + label, true);
                    continue;
                }
                
                throw new ChannelException("Tiangge rejected " + label + ": HTTP " + status + " " + errBody, false);

            } catch (ChannelException e) {
                if (!e.isRetryable()) {
                    throw e;
                }
                last = e;
            } catch (IOException e) {
                last = new ChannelException(label + " failed: " + e.getClass().getSimpleName() + " " + e.getMessage(), e, true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ChannelException("Interrupted while calling Tiangge (" + label + ")", false);
            }
        }
        throw last != null ? last : new ChannelException("No attempt made for " + label, true);
    }

    private String parseErrorCode(String body) {
        try {
            return json.readValue(body, TiangeMessages.ErrorBody.class).error();
        } catch (Exception e) {
            return null;
        }
    }

    private void backoff(int nextAttempt) {
        long base = props.backoffBaseMs();
        long exp = base * (1L << Math.min(nextAttempt - 2, 4));
        long jitter = base > 0 ? ThreadLocalRandom.current().nextLong(base / 2 + 1) : 0;
        try {
            Thread.sleep(exp + jitter);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String enc(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
