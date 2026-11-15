package com.danielhw99.summary.api;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import com.danielhw99.transaction.api.TransactionResponse;
import com.danielhw99.memo.api.MemoResponse;
import com.danielhw99.payment.api.PaymentResponse;
import io.swagger.v3.oas.annotations.media.Schema;

public final class SummaryDtos {
    private SummaryDtos() {}
    @Schema(name="DailyPaymentSummary",requiredProperties={"record","itemName","paid","remaining","complete"})
    public record DailyPayment(PaymentResponse record,String itemName,@Schema(minimum="-9007199254740991",maximum="9007199254740991") long paid,@Schema(minimum="-9007199254740991",maximum="9007199254740991") long remaining,boolean complete) {}
    @Schema(name="DailySummary",requiredProperties={"date","mainBalance","freeBalance","foodBudget","carryoverFreeBalance","availableBeforeSpending","remainingFood","expenseTotal","incomeTotal","transactions","activeMemos","payments"})
    public record Daily(LocalDate date,@Schema(minimum="-9007199254740991",maximum="9007199254740991") long mainBalance,@Schema(minimum="-9007199254740991",maximum="9007199254740991") long freeBalance,@Schema(minimum="-9007199254740991",maximum="9007199254740991") long foodBudget,@Schema(minimum="-9007199254740991",maximum="9007199254740991") long carryoverFreeBalance,
            @Schema(minimum="-9007199254740991",maximum="9007199254740991") long availableBeforeSpending,@Schema(minimum="-9007199254740991",maximum="9007199254740991") long remainingFood,@Schema(minimum="-9007199254740991",maximum="9007199254740991") long expenseTotal,@Schema(minimum="-9007199254740991",maximum="9007199254740991") long incomeTotal,
            List<TransactionResponse> transactions,List<MemoResponse> activeMemos,List<DailyPayment> payments) {}
    @Schema(name="MonthlySummary",requiredProperties={"month","income","extra","fixedBudget","foodBudget","basicTotal","totalExpense","net"})
    public record Monthly(YearMonth month,@Schema(minimum="-9007199254740991",maximum="9007199254740991") long income,@Schema(minimum="-9007199254740991",maximum="9007199254740991") long extra,@Schema(minimum="-9007199254740991",maximum="9007199254740991") long fixedBudget,@Schema(minimum="-9007199254740991",maximum="9007199254740991") long foodBudget,@Schema(minimum="-9007199254740991",maximum="9007199254740991") long basicTotal,@Schema(minimum="-9007199254740991",maximum="9007199254740991") long totalExpense,@Schema(minimum="-9007199254740991",maximum="9007199254740991") long net) {}
    @Schema(name="CalendarEntry",oneOf={AmountEntry.class,MemoEntry.class})
    public sealed interface CalendarEntry permits AmountEntry,MemoEntry {}
    @Schema(name="CalendarAmountEntry",requiredProperties={"kind","label","amount"})
    public record AmountEntry(@Schema(allowableValues={"fixed","transaction","free"}) String kind,String label,@Schema(minimum="-9007199254740991",maximum="9007199254740991") long amount) implements CalendarEntry {}
    @Schema(name="CalendarMemoEntry",requiredProperties={"kind","label","count"})
    public record MemoEntry(@Schema(allowableValues={"memo"}) String kind,String label,@Schema(minimum="0") int count) implements CalendarEntry {}
    @Schema(name="CalendarDay",requiredProperties={"date","entries"})
    public record CalendarDay(LocalDate date,List<CalendarEntry> entries) {}
    @Schema(name="HomeSummary",requiredProperties={"month","asOfDate","days","expense","freeBalance"})
    public record Home(YearMonth month,LocalDate asOfDate,List<CalendarDay> days,@Schema(minimum="-9007199254740991",maximum="9007199254740991") long expense,@Schema(minimum="-9007199254740991",maximum="9007199254740991") long freeBalance) {}
}
