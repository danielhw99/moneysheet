package com.danielhw99.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;

import com.danielhw99.budget.service.BudgetCalendar;
import com.danielhw99.common.error.ApiException;
import com.danielhw99.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

class BudgetCalendarTest {
    @Test void seoulMonthWinsAtUtcMonthBoundary() {
        var calendar = new BudgetCalendar(Clock.fixed(Instant.parse("2026-10-31T15:30:00Z"), ZoneOffset.UTC));
        assertThat(calendar.currentMonth()).isEqualTo(YearMonth.of(2026, 11));
        assertThatThrownBy(() -> calendar.requireWritable(YearMonth.of(2026, 10)))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.BUDGET_READ_ONLY);
        assertThatThrownBy(() -> calendar.requireWritable(YearMonth.of(2026, 12)))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.FUTURE_MONTH_NOT_ALLOWED);
    }

    @Test void installmentTotalsUseActualWeekdaysAndMonthEnd() {
        var calendar = new BudgetCalendar(Clock.systemUTC());
        assertThat(calendar.foodInstallments(YearMonth.of(2026, 2), 10, 20, 30))
                .containsExactly(160L, 140L, 100L);
    }
}
