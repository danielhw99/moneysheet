package com.danielhw99.common.api;

import com.fasterxml.jackson.annotation.JsonValue;

import io.swagger.v3.oas.annotations.media.Schema;

import tools.jackson.databind.annotation.JsonDeserialize;

@Schema(type = "string", pattern = "^[1-9][0-9]*$", example = "101",
        description = "양수 BIGINT ID를 나타내는 숫자 문자열")
@JsonDeserialize(using = ApiIdDeserializer.class)
public record ApiId(long value) {

    public ApiId {
        if (value < 1) {
            throw new IllegalArgumentException("ID must be positive.");
        }
    }

    public static ApiId parse(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) {
            throw new IllegalArgumentException("ID must be a positive decimal string.");
        }
        return new ApiId(Long.parseLong(value));
    }

    @JsonValue
    public String jsonValue() {
        return Long.toString(value);
    }
}
