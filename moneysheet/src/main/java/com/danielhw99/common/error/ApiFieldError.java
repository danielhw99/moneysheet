package com.danielhw99.common.error;

import io.swagger.v3.oas.annotations.media.Schema;

public record ApiFieldError(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "amount") String field,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "입력값을 확인해주세요.") String message) {
}
