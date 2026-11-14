package com.danielhw99.transaction.api;

import java.time.LocalDate;
import java.time.LocalTime;
import com.danielhw99.common.api.ApiId;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

public record TransactionInput(
        @NotNull LocalDate date,
        @NotNull LocalTime time,
        @NotBlank @Schema(minLength = 1, maxLength = 255) String title,
        @NotNull @Min(1) @Max(9_999_999_999L) Long amount,
        @NotNull TransactionDirectionValue direction,
        @JsonProperty(required = true)
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) ApiId categoryId) {
}
