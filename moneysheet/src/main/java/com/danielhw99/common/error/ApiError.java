package com.danielhw99.common.error;

import java.util.List;

public record ApiError(String code, String message, List<ApiFieldError> fieldErrors, String traceId) {

    public ApiError {
        fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }
}
