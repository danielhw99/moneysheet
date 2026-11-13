package com.danielhw99.category.api;

import com.danielhw99.category.entity.Category;
import com.danielhw99.common.api.ApiId;

import io.swagger.v3.oas.annotations.media.Schema;

public record CategoryResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ApiId id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 100) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) CategoryScopeValue scope,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) CategoryDirectionValue direction) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(new ApiId(category.getId()), category.getName(),
                CategoryScopeValue.fromEntity(category.getScope()),
                CategoryDirectionValue.fromEntity(category.getDirection()));
    }
}
