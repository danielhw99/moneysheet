package com.danielhw99.memo.api;

import com.danielhw99.common.api.ApiEnum;
import com.danielhw99.memo.entity.MemoDirection;
import com.fasterxml.jackson.annotation.JsonCreator;

public enum MemoDirectionValue implements ApiEnum {
    INCOME("income"), EXPENSE("expense");
    private final String value;
    MemoDirectionValue(String value) { this.value = value; }
    @Override public String getValue() { return value; }
    @JsonCreator public static MemoDirectionValue fromValue(String value) {
        return ApiEnum.fromValue(MemoDirectionValue.class, value);
    }
    public MemoDirection entityValue() { return MemoDirection.valueOf(name()); }
    public static MemoDirectionValue fromEntity(MemoDirection value) { return valueOf(value.name()); }
}
