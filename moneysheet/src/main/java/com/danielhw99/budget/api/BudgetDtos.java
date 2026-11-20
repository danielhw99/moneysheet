package com.danielhw99.budget.api;

import java.util.List;

import com.danielhw99.common.api.ApiId;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

public final class BudgetDtos {
    private BudgetDtos() {}


    public record Food(
            @JsonProperty(required=true) @Schema(nullable=true,description="NULL은 미선택 DRAFT 응답에서만 허용. 쓰기는 true/false 필수") Boolean enabled,
            @JsonProperty(required=true) @Schema(requiredMode=Schema.RequiredMode.REQUIRED) com.danielhw99.budget.entity.FoodPaymentMode paymentMode,
            @JsonProperty(required=true) @Schema(nullable=true,minimum="1",maximum="9999999999") Long weekday,
            @JsonProperty(required=true) @Schema(nullable=true,minimum="1",maximum="9999999999") Long saturday,
            @JsonProperty(required=true) @Schema(nullable=true,minimum="1",maximum="9999999999") Long sunday) {
        public Food(Long weekday, Long saturday, Long sunday) { this(true,com.danielhw99.budget.entity.FoodPaymentMode.THREE_INSTALLMENTS,weekday,saturday,sunday); }
    }
    public record ItemInput(String name, @Schema(nullable = true) ApiId categoryId, Long amount) {}
    public record SetupItem(
            @JsonProperty(required = true)
            @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) ApiId id, String name,
            @Schema(nullable = true) ApiId categoryId, Long amount) {}
    public record SetupInput(List<SetupItem> items, Food food) {}
    public record Item(ApiId id, String name, @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) ApiId categoryId, long amount) {}
    public record DraftItem(ApiId id, String name, @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) ApiId categoryId, Long amount) {}
    public record Preparation(String month, String status, String copiedFromMonth, List<DraftItem> items, Food food) {}
    public record ItemSummary(ApiId id, String name, @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) ApiId categoryId, long amount,
                              long paid, long remaining, boolean complete) {}
    public record FoodSummary(long total, List<Long> installments) {}
    public record Summary(String month, String status, List<ItemSummary> items, Food food,
                          long fixedTotal, FoodSummary foodSummary) {}
}
