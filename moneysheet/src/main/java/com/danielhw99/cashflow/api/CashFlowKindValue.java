package com.danielhw99.cashflow.api;

import com.danielhw99.common.api.ApiEnum;
import com.danielhw99.cashflow.entity.CashFlowKind;
import com.fasterxml.jackson.annotation.JsonCreator;

public enum CashFlowKindValue implements ApiEnum {
    INCOME("income"), EXTRA_EXPENSE("extraExpense");
    private final String value;
    CashFlowKindValue(String value) { this.value = value; }
    @Override public String getValue() { return value; }
    @JsonCreator public static CashFlowKindValue fromValue(String value) {
        return ApiEnum.fromValue(CashFlowKindValue.class, value);
    }
    public CashFlowKind entityValue() { return CashFlowKind.valueOf(name()); }
    public static CashFlowKindValue fromEntity(CashFlowKind value) { return valueOf(value.name()); }
}
