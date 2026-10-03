package edu.cit.berou.channel;


class ChannelException extends RuntimeException {

    private final boolean retryable;

    ChannelException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    ChannelException(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.retryable = retryable;
    }

    boolean isRetryable() {
        return retryable;
    }
}
