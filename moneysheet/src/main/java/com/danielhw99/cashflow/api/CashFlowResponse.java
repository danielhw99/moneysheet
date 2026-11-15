package com.danielhw99.cashflow.api;

import java.time.LocalDate;
import java.time.LocalTime;
import com.danielhw99.common.api.ApiId;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "MonthlyCashFlow", requiredProperties = {"id", "date", "time", "title", "amount", "kind"})
public record CashFlowResponse(ApiId id, LocalDate date, LocalTime time, String title,
        @Schema(minimum = "1", maximum = "9999999999") long amount, CashFlowKindValue kind) {}
