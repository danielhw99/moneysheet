package com.danielhw99.common.api;

public final class ApiText {

    private ApiText() {
    }

    // Explicitly for title/name/nickname fields; never apply to passwords.
    public static String normalize(String value) {
        return value == null ? null : value.strip();
    }
}
