package com.danielhw99.memo.api;
import com.danielhw99.common.api.ApiEnum;
public enum MemoStatus implements ApiEnum {
 PENDING("pending"), COMPLETED("completed"), ALL("all");
 private final String value;
 MemoStatus(String value) { this.value=value; }
 @Override public String getValue() { return value; }
}
