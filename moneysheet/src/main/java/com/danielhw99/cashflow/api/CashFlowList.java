package com.danielhw99.cashflow.api;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;
@Schema(name = "MonthlyCashFlowList", requiredProperties = {"rows", "nextCursor", "expenseTotal", "incomeTotal", "total"})
public record CashFlowList(List<CashFlowResponse> rows,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) String nextCursor,
        @Schema(minimum = "0", maximum = "9007199254740991") long expenseTotal,
        @Schema(minimum = "0", maximum = "9007199254740991") long incomeTotal,
        @Schema(minimum = "-9007199254740991", maximum = "9007199254740991") long total) {}
