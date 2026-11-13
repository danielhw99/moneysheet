package com.danielhw99.category.api;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

public record CategoriesResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<CategoryResponse> items) {
}
