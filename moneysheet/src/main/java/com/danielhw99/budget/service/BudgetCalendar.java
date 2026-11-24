package com.danielhw99.budget.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import com.danielhw99.common.error.ApiException;
import com.danielhw99.common.error.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class BudgetCalendar {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final long MAX_SAFE = 9_007_199_254_740_991L;
    private final Clock clock;

    public BudgetCalendar(Clock clock) { this.clock = clock; }

    public LocalDate today() { return LocalDate.now(clock.withZone(SEOUL)); }
    public YearMonth currentMonth() { return YearMonth.from(LocalDate.now(clock.withZone(SEOUL))); }

    public void requireWritable(YearMonth month) {
        int comparison = month.compareTo(currentMonth());
        if (comparison < 0) throw new ApiException(ErrorCode.BUDGET_READ_ONLY);
        if (comparison > 0) throw new ApiException(ErrorCode.FUTURE_MONTH_NOT_ALLOWED);
    }

    public List<Long> foodInstallments(YearMonth month, long weekday, long saturday, long sunday) {
        return foodInstallments(month,weekday,saturday,sunday,month.atDay(1));
    }
    public List<Long> foodInstallments(YearMonth month,long weekday,long saturday,long sunday,LocalDate start) {
        long[] totals = new long[3];
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            LocalDate date = month.atDay(day);
            if(date.isBefore(start))continue;
            int installment = day <= 10 ? 0 : day <= 20 ? 1 : 2;
            long amount = switch (date.getDayOfWeek()) {
                case SATURDAY -> saturday;
                case SUNDAY -> sunday;
                default -> weekday;
            };
            totals[installment] = safeAdd(totals[installment], amount);
        }
        return List.of(totals[0], totals[1], totals[2]);
    }

    public List<Long> foodInstallments(YearMonth month,com.danielhw99.budget.entity.FoodPaymentMode mode,Long weekday,Long saturday,Long sunday) {
        return foodInstallments(month,mode,weekday,saturday,sunday,month.atDay(1));
    }
    public List<Long> foodInstallments(YearMonth month,com.danielhw99.budget.entity.FoodPaymentMode mode,Long weekday,Long saturday,Long sunday,LocalDate start) {
        var totals=foodInstallments(month,weekday,saturday,sunday,start);
        if(mode==com.danielhw99.budget.entity.FoodPaymentMode.MONTH_START) return List.of(safeAdd(safeAdd(totals.get(0),totals.get(1)),totals.get(2)),0L,0L);
        return totals;
    }

    public long safeAdd(long left, long right) {
        try {
            long result = Math.addExact(left, right);
            if (result > MAX_SAFE || result < -MAX_SAFE) throw new ArithmeticException();
            return result;
        } catch (ArithmeticException overflow) {
            throw new ApiException(ErrorCode.AMOUNT_RANGE_EXCEEDED);
        }
    }
}
