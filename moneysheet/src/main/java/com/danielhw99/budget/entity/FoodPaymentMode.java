package com.danielhw99.budget.entity;
import com.danielhw99.common.api.ApiEnum;
import com.fasterxml.jackson.annotation.JsonCreator;
public enum FoodPaymentMode implements ApiEnum {
    THREE_INSTALLMENTS("threeInstallments"), MONTH_START("monthStart");
    private final String value;
    FoodPaymentMode(String value) { this.value=value; }
    public String getValue() { return value; }
    @JsonCreator public static FoodPaymentMode parse(String value) { return ApiEnum.fromValue(FoodPaymentMode.class,value); }
}
