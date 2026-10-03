package edu.cit.berou.supplier;


class LegacySupplyException extends RuntimeException {

    enum Kind {
        TRANSIENT,
        CREDENTIALS,
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

    
    static LegacySupplyException transientFailure(String message, Throwable cause) {
        return new LegacySupplyException(Kind.TRANSIENT, null, 0, true, message, cause);
    }

    
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
