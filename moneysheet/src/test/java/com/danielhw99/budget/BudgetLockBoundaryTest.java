package com.danielhw99.budget;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.danielhw99.budget.repository.FixedBudgetItemRepository;
import com.danielhw99.budget.repository.MonthlyBudgetRepository;
import com.danielhw99.budget.service.BudgetCalendar;
import com.danielhw99.budget.service.BudgetService;
import com.danielhw99.category.repository.CategoryRepository;
import com.danielhw99.common.error.ApiException;
import com.danielhw99.common.error.ErrorCode;
import com.danielhw99.payment.repository.FixedPaymentRepository;
import com.danielhw99.user.entity.User;
import com.danielhw99.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

class BudgetLockBoundaryTest {
    @Test void preparationRechecksCurrentMonthAfterAcquiringUserLock() {
        var now = new AtomicReference<>(Instant.parse("2026-11-30T14:59:59Z"));
        Clock clock = new Clock() {
            @Override public ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now.get(), zone); }
            @Override public Instant instant() { return now.get(); }
        };
        var budgets = mock(MonthlyBudgetRepository.class);
        var users = mock(UserRepository.class);
        when(users.findWithLockingById(1L)).thenAnswer(invocation -> {
            now.set(Instant.parse("2026-11-30T15:00:00Z"));
            return Optional.of(mock(User.class));
        });
        var service = new BudgetService(budgets, mock(FixedBudgetItemRepository.class),
                mock(CategoryRepository.class), mock(FixedPaymentRepository.class),
                users, new BudgetCalendar(clock));
        assertThatThrownBy(() -> service.prepare(1L, YearMonth.of(2026, 11)))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.BUDGET_READ_ONLY);
        verifyNoInteractions(budgets);
    }
}
