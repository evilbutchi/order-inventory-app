package edu.cit.berou.supplier;

import java.util.function.Supplier;

/**
 * Holds the current LegacySupply session token. Signs in lazily, and signs
 * in again when told the token was rejected (or, optionally, when it is older
 * than a configured max age - fill that in once you have measured the real
 * lifetime). Nobody ever pastes a token by hand.
 */
final class SessionManager {

    private final long maxAgeMs;
    private String token;
    private long issuedAtMs;

    /** @param maxAgeSeconds 0 = never expire proactively, only re-sign-in when rejected */
    SessionManager(long maxAgeSeconds) {
        this.maxAgeMs = maxAgeSeconds * 1000L;
    }

    synchronized String token(Supplier<String> signIn) {
        long now = System.currentTimeMillis();
        if (token != null && maxAgeMs > 0 && now - issuedAtMs >= maxAgeMs) {
            token = null;
        }
        if (token == null) {
            token = signIn.get();
            issuedAtMs = System.currentTimeMillis();
        }
        return token;
    }

    /**
     * Forget the token if it is still the one that was rejected (another
     * thread may already have replaced it).
     *
     * @return how many seconds that session lived, or -1 if nothing was dropped
     */
    synchronized long invalidate(String rejectedToken) {
        if (rejectedToken != null && rejectedToken.equals(token)) {
            long ageSeconds = (System.currentTimeMillis() - issuedAtMs) / 1000L;
            token = null;
            return ageSeconds;
        }
        return -1;
    }
}
