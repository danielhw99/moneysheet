package com.danielhw99.transaction;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import java.time.YearMonth;
import java.time.LocalTime;
import java.util.Optional;
import com.danielhw99.transaction.api.TransactionInput;
import com.danielhw99.transaction.api.TransactionDirectionValue;
import com.danielhw99.transaction.service.TransactionService;
import com.danielhw99.transaction.repository.MainTransactionRepository;
import com.danielhw99.budget.entity.MonthlyBudget;
import com.danielhw99.budget.entity.BudgetSetupStatus;
import com.danielhw99.budget.repository.MonthlyBudgetRepository;
import com.danielhw99.category.repository.CategoryRepository;
import com.danielhw99.category.entity.Category;
import com.danielhw99.category.entity.CategoryScope;
import com.danielhw99.category.entity.CategoryDirection;
import com.danielhw99.user.entity.User;
import com.danielhw99.common.api.ApiId;
import com.danielhw99.common.error.ApiException;
import com.danielhw99.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
class TransactionServiceTest {
    final MainTransactionRepository records = mock(MainTransactionRepository.class);
    final MonthlyBudgetRepository budgets = mock(MonthlyBudgetRepository.class);
    final CategoryRepository categories = mock(CategoryRepository.class);
    final TransactionService service = new TransactionService(records, budgets, categories);
    final YearMonth month = YearMonth.of(2026, 11);
    TransactionInput input(long amount, ApiId category) {
        return new TransactionInput(month.atDay(1), LocalTime.NOON, "점심", amount, TransactionDirectionValue.EXPENSE, category);
    }
    void ready() {
        var budget = new MonthlyBudget(mock(User.class),month.atDay(1),BudgetSetupStatus.READY,1L,1L,1L);
        when(budgets.findByUser_IdAndMonthStart(1L,month.atDay(1))).thenReturn(Optional.of(budget));
    }
    @Test void unpreparedBudgetRejectsWrites() {
        assertCode(ErrorCode.BUDGET_NOT_READY, () -> service.create(1L,month,input(1L,null)));
        verifyNoInteractions(records);
    }
    @Test void zeroAndWrongMonthAreRejectedBeforeSave() {
        ready(); assertCode(ErrorCode.VALIDATION, () -> service.create(1L,month,input(0L,null)));
        var wrong = new TransactionInput(month.plusMonths(1).atDay(1),LocalTime.NOON,"x",1L,TransactionDirectionValue.EXPENSE,null);
        assertCode(ErrorCode.VALIDATION, () -> service.create(1L,month,wrong));
        verifyNoInteractions(records);
    }
    @Test void categoryScopeAndDirectionMustMatch() {
        ready(); var user=mock(User.class);when(user.getId()).thenReturn(1L);
        when(categories.findById(8L)).thenReturn(Optional.of(new Category(user,CategoryScope.MAIN_TRANSACTION,CategoryDirection.INCOME,"給与","給与")));
        assertCode(ErrorCode.VALIDATION, () -> service.create(1L,month,input(1L,new ApiId(8L))));
        verifyNoInteractions(records);
    }
    @Test void absentRecordIsNotFoundEvenWhenOwnerHasNoBudget() {
        assertCode(ErrorCode.NOT_FOUND, () -> service.get(1L,month,99L));
        assertCode(ErrorCode.NOT_FOUND, () -> service.update(1L,month,99L,input(1L,null)));
        assertCode(ErrorCode.NOT_FOUND, () -> service.delete(1L,month,99L));
    }
    private void assertCode(ErrorCode code,Runnable call) {
        assertThatThrownBy(call::run).isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(code);
    }
}
