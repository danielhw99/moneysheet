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

    public YearMonth currentMonth() { return YearMonth.from(LocalDate.now(clock.withZone(SEOUL))); }

    public void requireWritable(YearMonth month) {
        int comparison = month.compareTo(currentMonth());
        if (comparison < 0) throw new ApiException(ErrorCode.BUDGET_READ_ONLY);
        if (comparison > 0) throw new ApiException(ErrorCode.FUTURE_MONTH_NOT_ALLOWED);
    }

    public List<Long> foodInstallments(YearMonth month, long weekday, long saturday, long sunday) {
        long[] totals = new long[3];
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            LocalDate date = month.atDay(day);
            int installment = day <= 11 ? 0 : day <= 21 ? 1 : 2;
            long amount = switch (date.getDayOfWeek()) {
                case SATURDAY -> saturday;
                case SUNDAY -> sunday;
                default -> weekday;
            };
            totals[installment] = safeAdd(totals[installment], amount);
        }
        return List.of(totals[0], totals[1], totals[2]);
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
