package edu.cit.berou.supplier;

import java.util.function.Supplier;


final class SessionManager {

    private final long maxAgeMs;
    private String token;
    private long issuedAtMs;

    
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

    
    synchronized long invalidate(String rejectedToken) {
        if (rejectedToken != null && rejectedToken.equals(token)) {
            long ageSeconds = (System.currentTimeMillis() - issuedAtMs) / 1000L;
            token = null;
            return ageSeconds;
        }
        return -1;
    }
}
