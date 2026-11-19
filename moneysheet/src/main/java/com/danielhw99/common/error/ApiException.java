package com.danielhw99.common.error;

import java.util.Objects;
import java.util.List;

public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Long retryAfterSeconds;
    private final List<ApiFieldError> fieldErrors;

    public ApiException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    public ApiException(ErrorCode errorCode, Long retryAfterSeconds) {
        this(errorCode, retryAfterSeconds, List.of());
    }

    public ApiException(ErrorCode errorCode, String field, String message) {
        this(errorCode, null, List.of(new ApiFieldError(field, message)));
    }

    private ApiException(ErrorCode errorCode, Long retryAfterSeconds, List<ApiFieldError> fieldErrors) {
        super(Objects.requireNonNull(errorCode).getMessage());
        if (errorCode == ErrorCode.RATE_LIMITED && (retryAfterSeconds == null || retryAfterSeconds <= 0)) {
            throw new IllegalArgumentException("RATE_LIMITED requires positive Retry-After seconds.");
        }
        if (errorCode != ErrorCode.RATE_LIMITED && retryAfterSeconds != null) {
            throw new IllegalArgumentException("Retry-After is only supported for RATE_LIMITED.");
        }
        this.errorCode = errorCode;
        this.retryAfterSeconds = retryAfterSeconds;
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public List<ApiFieldError> getFieldErrors() {
        return fieldErrors;
    }
}
