package com.danielhw99.common.error;

import java.util.Objects;

public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Long retryAfterSeconds;

    public ApiException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    public ApiException(ErrorCode errorCode, Long retryAfterSeconds) {
        super(Objects.requireNonNull(errorCode).getMessage());
        if (errorCode == ErrorCode.RATE_LIMITED && (retryAfterSeconds == null || retryAfterSeconds <= 0)) {
            throw new IllegalArgumentException("RATE_LIMITED requires positive Retry-After seconds.");
        }
        if (errorCode != ErrorCode.RATE_LIMITED && retryAfterSeconds != null) {
            throw new IllegalArgumentException("Retry-After is only supported for RATE_LIMITED.");
        }
        this.errorCode = errorCode;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
