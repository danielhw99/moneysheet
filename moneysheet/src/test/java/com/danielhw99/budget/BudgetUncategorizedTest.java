package com.danielhw99.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import com.danielhw99.budget.api.BudgetDtos;
import com.danielhw99.budget.entity.BudgetSetupStatus;
import com.danielhw99.budget.entity.FixedBudgetItem;
import com.danielhw99.budget.entity.MonthlyBudget;
import com.danielhw99.budget.repository.MonthlyBudgetRepository;
import com.danielhw99.budget.repository.FixedBudgetItemRepository;
import com.danielhw99.budget.service.BudgetService;
import com.danielhw99.budget.service.BudgetCalendar;
import com.danielhw99.category.repository.CategoryRepository;
import com.danielhw99.payment.repository.FixedPaymentRepository;
import com.danielhw99.user.entity.User;
import com.danielhw99.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class BudgetUncategorizedTest {
    private final MonthlyBudgetRepository budgets = mock(MonthlyBudgetRepository.class);
    private final FixedBudgetItemRepository items = mock(FixedBudgetItemRepository.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final BudgetService service = new BudgetService(budgets, items, categories,
            mock(FixedPaymentRepository.class), users,
            new BudgetCalendar(Clock.fixed(Instant.parse("2026-11-19T00:00:00Z"), ZoneOffset.UTC)));
    private final YearMonth month = YearMonth.of(2026, 11);
    private final User owner = mock(User.class);
    private MonthlyBudget budget;

    @BeforeEach void setup() {
        when(owner.getId()).thenReturn(7L);
        when(users.findWithLockingById(7L)).thenReturn(Optional.of(owner));
        budget = new MonthlyBudget(owner, month.atDay(1), BudgetSetupStatus.READY, 11000L, 15000L, 10000L);
        ReflectionTestUtils.setField(budget, "id", 11L);
        when(budgets.findByUser_IdAndMonthStart(7L, month.atDay(1))).thenReturn(Optional.of(budget));
    }

    @Test void createsAndUpdatesAnItemWithoutCategory() {
        when(items.saveAndFlush(any(FixedBudgetItem.class))).thenAnswer(call -> {
            FixedBudgetItem item = call.getArgument(0);
            ReflectionTestUtils.setField(item, "id", 21L);
            return item;
        });
        var saved = service.createItem(7L, month, new BudgetDtos.ItemInput("기타 고정비", null, 100L));
        assertThat(saved.categoryId()).isNull();
        var existing = item(budget);
        when(items.findById(21L)).thenReturn(Optional.of(existing));
        assertThat(service.updateItem(7L, month, 21L, new BudgetDtos.ItemInput("수정", null, 200L)).categoryId()).isNull();
        verifyNoInteractions(categories);
    }

    @Test void summaryReturnsNullCategoryAndPreservesAmounts() {
        when(items.findByMonthlyBudget_IdOrderByIdAsc(11L)).thenReturn(List.of(item(budget)));
        var result = service.get(7L, month);
        assertThat(result.items().getFirst().categoryId()).isNull();
        assertThat(result.fixedTotal()).isEqualTo(100L);
        assertThat(result.items().getFirst().remaining()).isEqualTo(100L);
    }

    @Test void preparationCopiesUncategorizedItemIntoIndependentMonth() {
        var previous = new MonthlyBudget(owner, month.minusMonths(1).atDay(1), BudgetSetupStatus.READY, 11000L, 15000L, 10000L);
        ReflectionTestUtils.setField(previous, "id", 10L);
        when(budgets.findByUser_IdAndMonthStart(7L, month.atDay(1))).thenReturn(Optional.empty());
        when(budgets.findByUser_IdAndMonthStart(7L, previous.getMonthStart())).thenReturn(Optional.of(previous));
        when(budgets.saveAndFlush(any(MonthlyBudget.class))).thenAnswer(call -> {
            var value = (MonthlyBudget) call.getArgument(0);
            ReflectionTestUtils.setField(value, "id", 11L);
            return value;
        });
        when(items.findByMonthlyBudget_IdOrderByIdAsc(10L)).thenReturn(List.of(item(previous)));
        when(items.findByMonthlyBudget_IdOrderByIdAsc(11L)).thenReturn(List.of(item(budget)));
        var result = service.prepare(7L, month);
        assertThat(result.items().getFirst().categoryId()).isNull();
        assertThat(result.copiedFromMonth()).isEqualTo("2026-10");
        verify(items).save(argThat(value -> value.getCategory() == null && value.getMonthlyBudget() != previous));
    }

    private FixedBudgetItem item(MonthlyBudget parent) {
        var item = new FixedBudgetItem(parent, null, "기타", 100L);
        ReflectionTestUtils.setField(item, "id", 21L);
        return item;
    }
}
