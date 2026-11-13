package com.danielhw99.category.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

public record CategoryInput(
        @NotBlank @Schema(minLength = 1, maxLength = 100) String name,
        @NotNull CategoryScopeValue scope,
        @JsonProperty(required = true) @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
        CategoryDirectionValue direction) {
}
