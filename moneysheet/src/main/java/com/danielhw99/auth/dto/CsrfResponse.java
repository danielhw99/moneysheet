package com.danielhw99.auth.dto;
public record CsrfResponse(String token, String headerName) {
    @Override public String toString() { return "CsrfResponse[REDACTED]"; }
}
