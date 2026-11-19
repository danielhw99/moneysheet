package com.danielhw99.category.api;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

public record CategoryNameInput(@NotBlank @Schema(minLength = 1, maxLength = 100) String name) {
}
