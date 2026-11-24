package com.danielhw99.summary.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class SummaryFacts {
    private SummaryFacts() {}
    public record Food(com.danielhw99.budget.entity.FoodPaymentMode paymentMode,Long weekday,Long saturday,Long sunday,LocalDate foodStartDate) {
        public Food(com.danielhw99.budget.entity.FoodPaymentMode mode,Long weekday,Long saturday,Long sunday) { this(mode,weekday,saturday,sunday,null); }
        public Food(long weekday,long saturday,long sunday) { this(com.danielhw99.budget.entity.FoodPaymentMode.THREE_INSTALLMENTS,weekday,saturday,sunday,null); }
    }
    public record Amounts(BigDecimal expense, BigDecimal income) {
        public static Amounts zero() { return new Amounts(BigDecimal.ZERO, BigDecimal.ZERO); }
    }
    public record MemoWindow(LocalDate date, LocalDate completedOn, BigDecimal expense, BigDecimal income, long count) {}
    public record Ledger(Map<LocalDate, Amounts> transactions, Map<LocalDate, BigDecimal> payments, List<MemoWindow> memos) {
        public Ledger { transactions=Map.copyOf(transactions); payments=Map.copyOf(payments); memos=List.copyOf(memos); }
        public static Ledger empty() { return new Ledger(Map.of(),Map.of(),List.of()); }
    }
}
