package com.danielhw99.auth.dto;
import io.swagger.v3.oas.annotations.media.Schema;
public record CsrfResponse(@Schema(requiredMode=Schema.RequiredMode.REQUIRED) String token, @Schema(requiredMode=Schema.RequiredMode.REQUIRED) String headerName) {
    @Override public String toString() { return "CsrfResponse[REDACTED]"; }
}
