package com.danielhw99.common.api;

import com.fasterxml.jackson.annotation.JsonValue;

public interface ApiEnum {

    @JsonValue
    String getValue();

    static <T extends ApiEnum> T fromValue(Class<T> type, String value) {
        if (!type.isEnum()) {
            throw new IllegalArgumentException("API enum type must be an enum.");
        }
        for (T constant : type.getEnumConstants()) {
            if (constant.getValue().equals(value)) {
                return constant;
            }
        }
        throw new IllegalArgumentException("Invalid API enum value.");
    }
}
