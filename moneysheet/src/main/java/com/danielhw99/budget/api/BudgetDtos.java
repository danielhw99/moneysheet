package com.danielhw99.budget.api;

import java.util.List;

import com.danielhw99.common.api.ApiId;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

public final class BudgetDtos {
    private BudgetDtos() {}


    public record Food(Long weekday, Long saturday, Long sunday) {}
    public record ItemInput(String name, ApiId categoryId, Long amount) {}
    public record SetupItem(
            @JsonProperty(required = true)
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) ApiId id, String name, ApiId categoryId, Long amount) {}
    public record SetupInput(List<SetupItem> items, Food food) {}
    public record Item(ApiId id, String name, ApiId categoryId, long amount) {}
    public record DraftItem(ApiId id, String name, ApiId categoryId, Long amount) {}
    public record Preparation(String month, String status, String copiedFromMonth, List<DraftItem> items, Food food) {}
    public record ItemSummary(ApiId id, String name, ApiId categoryId, long amount,
                              long paid, long remaining, boolean complete) {}
    public record FoodSummary(long total, List<Long> installments) {}
    public record Summary(String month, String status, List<ItemSummary> items, Food food,
                          long fixedTotal, FoodSummary foodSummary) {}
}
