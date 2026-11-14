package com.danielhw99.payment.api;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;
@Schema(requiredProperties={"rows","nextCursor","expenseTotal","incomeTotal","total"})
public record PaymentList(List<PaymentResponse> rows,
        @Schema(nullable=true,requiredMode=Schema.RequiredMode.REQUIRED) String nextCursor,
        @Schema(minimum="0",maximum="9007199254740991") long expenseTotal,
        @Schema(minimum="0",maximum="0") long incomeTotal,
        @Schema(minimum="0",maximum="9007199254740991") long total) {}
