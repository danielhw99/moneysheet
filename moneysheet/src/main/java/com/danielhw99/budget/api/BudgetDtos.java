package com.danielhw99.budget.api;

import java.util.List;

import com.danielhw99.common.api.ApiId;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public final class BudgetDtos {
    private BudgetDtos() {}

    public enum Kind {
        FIXED_EXPENSE("fixedExpense"), FREE_ALLOWANCE("freeAllowance");
        private final String value;
        Kind(String value) { this.value = value; }
        @JsonValue public String value() { return value; }
        @JsonCreator public static Kind fromValue(String value) {
            for (Kind kind : values()) if (kind.value.equals(value)) return kind;
            throw new IllegalArgumentException("Invalid budget kind");
        }
    }

    public record Food(Long weekday, Long saturday, Long sunday) {}
    public record ItemInput(Kind kind, String name, ApiId categoryId, Long amount) {}
    public record SetupItem(ApiId id, Kind kind, String name, ApiId categoryId, Long amount) {}
    public record SetupInput(List<SetupItem> items, Food food) {}
    public record Item(ApiId id, Kind kind, String name, ApiId categoryId, long amount) {}
    public record DraftItem(ApiId id, Kind kind, String name, ApiId categoryId, Long amount) {}
    public record Preparation(String month, String status, String copiedFromMonth, List<DraftItem> items, Food food) {}
    public record ItemSummary(ApiId id, Kind kind, String name, ApiId categoryId, long amount,
                              long paid, long remaining, boolean complete, boolean automatic) {}
    public record FoodSummary(long total, List<Long> installments) {}
    public record Summary(String month, String status, List<ItemSummary> items, Food food,
                          long fixedTotal, FoodSummary foodSummary) {}
}
