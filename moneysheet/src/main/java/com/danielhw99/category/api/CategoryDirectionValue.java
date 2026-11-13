package com.danielhw99.category.api;

import com.danielhw99.category.entity.CategoryDirection;
import com.danielhw99.common.api.ApiEnum;
import com.fasterxml.jackson.annotation.JsonCreator;

public enum CategoryDirectionValue implements ApiEnum {
    INCOME("income", CategoryDirection.INCOME),
    EXPENSE("expense", CategoryDirection.EXPENSE);

    private final String value;
    private final CategoryDirection entityValue;

    CategoryDirectionValue(String value, CategoryDirection entityValue) {
        this.value = value;
        this.entityValue = entityValue;
    }

    @Override
    public String getValue() {
        return value;
    }

    public CategoryDirection entityValue() {
        return entityValue;
    }

    @JsonCreator
    public static CategoryDirectionValue fromValue(String value) {
        return ApiEnum.fromValue(CategoryDirectionValue.class, value);
    }

    public static CategoryDirectionValue fromEntity(CategoryDirection value) {
        return switch (value) {
            case NONE -> null;
            case INCOME -> INCOME;
            case EXPENSE -> EXPENSE;
        };
    }
}
