package com.danielhw99.category.api;

import com.danielhw99.category.entity.CategoryScope;
import com.danielhw99.common.api.ApiEnum;
import com.fasterxml.jackson.annotation.JsonCreator;

public enum CategoryScopeValue implements ApiEnum {
    FIXED_BUDGET("fixedBudget", CategoryScope.FIXED_BUDGET),
    MAIN_TRANSACTION("mainTransaction", CategoryScope.MAIN_TRANSACTION);

    private final String value;
    private final CategoryScope entityValue;

    CategoryScopeValue(String value, CategoryScope entityValue) {
        this.value = value;
        this.entityValue = entityValue;
    }

    @Override
    public String getValue() {
        return value;
    }

    public CategoryScope entityValue() {
        return entityValue;
    }

    @JsonCreator
    public static CategoryScopeValue fromValue(String value) {
        return ApiEnum.fromValue(CategoryScopeValue.class, value);
    }

    public static CategoryScopeValue fromEntity(CategoryScope value) {
        return switch (value) {
            case FIXED_BUDGET -> FIXED_BUDGET;
            case MAIN_TRANSACTION -> MAIN_TRANSACTION;
        };
    }
}
