package com.danielhw99.transaction.api;

import com.danielhw99.common.api.ApiEnum;
import com.danielhw99.transaction.entity.TransactionDirection;
import com.fasterxml.jackson.annotation.JsonCreator;

public enum TransactionDirectionValue implements ApiEnum {
    INCOME("income"), EXPENSE("expense");
    private final String value;
    TransactionDirectionValue(String value) { this.value = value; }
    @Override public String getValue() { return value; }
    @JsonCreator public static TransactionDirectionValue fromValue(String value) {
        return ApiEnum.fromValue(TransactionDirectionValue.class, value);
    }
    public TransactionDirection entityValue() { return TransactionDirection.valueOf(name()); }
    public static TransactionDirectionValue fromEntity(TransactionDirection value) { return valueOf(value.name()); }
}
