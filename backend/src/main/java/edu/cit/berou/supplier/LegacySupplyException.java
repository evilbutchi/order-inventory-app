package edu.cit.berou.supplier;

/**
 * Everything that can go wrong talking to LegacySupply, boiled down to what
 * the rest of the module needs to decide: retry later, fix configuration,
 * or give up on this order. Package-private on purpose.
 */
class LegacySupplyException extends RuntimeException {

    enum Kind {
        /** Timeout, refused connection, 5xx, quota. The order is fine; try again later. */
        TRANSIENT,
        /** Our credentials/configuration are wrong. Not the order's fault; keep it PENDING. */
        CREDENTIALS,
        /** LegacySupply understood us and said no (4xx). Retrying the same request is pointless. */
        REJECTED
    }

    private final Kind kind;
    private final String code;
    private final int httpStatus;
    private final boolean retryable;

    private LegacySupplyException(Kind kind, String code, int httpStatus, boolean retryable,
                                  String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
        this.code = code;
        this.httpStatus = httpStatus;
        this.retryable = retryable;
    }

    /** Worth retrying immediately (with backoff). */
    static LegacySupplyException transientFailure(String message, Throwable cause) {
        return new LegacySupplyException(Kind.TRANSIENT, null, 0, true, message, cause);
    }

    /** Transient, but do NOT retry right now (quota, interruption). */
    static LegacySupplyException backOff(String message) {
        return new LegacySupplyException(Kind.TRANSIENT, null, 0, false, message, null);
    }

    static LegacySupplyException credentials(String message) {
        return new LegacySupplyException(Kind.CREDENTIALS, null, 0, false, message, null);
    }

    static LegacySupplyException rejected(String code, int httpStatus, String message) {
        return new LegacySupplyException(Kind.REJECTED, code, httpStatus, false,
                "LegacySupply refused (" + code + ", HTTP " + httpStatus + "): " + message, null);
    }

    Kind kind() {
        return kind;
    }

    String code() {
        return code;
    }

    int httpStatus() {
        return httpStatus;
    }

    boolean isRetryable() {
        return retryable;
    }
}
