package com.danielhw99.budget;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Optional;
import com.danielhw99.budget.entity.BudgetSetupStatus;
import com.danielhw99.budget.entity.FixedBudgetItem;
import com.danielhw99.budget.entity.MonthlyBudget;
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

class BudgetOwnershipTest {
    @Test void foreignItemReturnsNotFoundEvenWhenCallerHasNoBudget() {
        var users = mock(UserRepository.class);
        var budgets = mock(MonthlyBudgetRepository.class);
        var items = mock(FixedBudgetItemRepository.class);
        var owner = mock(User.class);
        when(owner.getId()).thenReturn(2L);
        var foreignBudget = new MonthlyBudget(owner, LocalDate.of(2026, 11, 1), BudgetSetupStatus.READY, 1L, 1L, 1L);
        var foreignItem = mock(FixedBudgetItem.class);
        when(foreignItem.getMonthlyBudget()).thenReturn(foreignBudget);
        when(items.findById(9L)).thenReturn(Optional.of(foreignItem));
        when(users.findWithLockingById(1L)).thenReturn(Optional.of(mock(User.class)));
        var service = new BudgetService(budgets, items, mock(CategoryRepository.class),
                mock(FixedPaymentRepository.class), users,
                new BudgetCalendar(Clock.fixed(Instant.parse("2026-11-13T00:00:00Z"), ZoneOffset.UTC)));
        assertThatThrownBy(() -> service.updateItem(1L, YearMonth.of(2026, 11), 9L, null))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
        assertThatThrownBy(() -> service.deleteItem(1L, YearMonth.of(2026, 11), 9L))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.NOT_FOUND);
        verify(items, never()).delete(any());
    }
}
