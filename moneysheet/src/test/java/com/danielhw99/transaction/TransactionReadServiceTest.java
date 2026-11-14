package com.danielhw99.transaction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Optional;
import java.util.List;
import com.danielhw99.budget.entity.MonthlyBudget;
import com.danielhw99.budget.entity.BudgetSetupStatus;
import com.danielhw99.budget.repository.MonthlyBudgetRepository;
import com.danielhw99.transaction.repository.TransactionQueries;
import com.danielhw99.transaction.service.TransactionCursor;
import com.danielhw99.transaction.service.TransactionReadService;
import com.danielhw99.transaction.api.TransactionDirectionValue;
import com.danielhw99.common.error.ApiException;
import com.danielhw99.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
class TransactionReadServiceTest {
    final YearMonth month=YearMonth.of(2026,11);
    final MonthlyBudgetRepository budgets=mock(MonthlyBudgetRepository.class);
    final TransactionQueries queries=mock(TransactionQueries.class);
    final TransactionReadService service=new TransactionReadService(budgets,queries,new TransactionCursor());
    void ready(BigDecimal expense,BigDecimal income) {
        var budget=mock(MonthlyBudget.class);when(budget.getId()).thenReturn(55L);when(budget.getSetupStatus()).thenReturn(BudgetSetupStatus.READY);
        when(budgets.findByUser_IdAndMonthStart(1L,month.atDay(1))).thenReturn(Optional.of(budget));
        when(queries.page(55L,null,null,null,50)).thenReturn(List.of());
        when(queries.total(55L,null,null,TransactionDirectionValue.EXPENSE)).thenReturn(expense);
        when(queries.total(55L,null,null,TransactionDirectionValue.INCOME)).thenReturn(income);
    }
    @Test void totalsComeFromWholeFilterQueriesRatherThanRows() {
        ready(BigDecimal.valueOf(500),BigDecimal.valueOf(1000));
        var result=service.list(1L,month,null,null,null,50);
        assertThat(result.rows()).isEmpty();assertThat(result.total()).isEqualTo(500);
        assertThat(result.expenseTotal()).isEqualTo(500);assertThat(result.incomeTotal()).isEqualTo(1000);
    }
    @Test void unsafeTotalIsAnExplicitBusinessError() {
        ready(new BigDecimal("9007199254740992"),BigDecimal.ZERO);
        assertThatThrownBy(()->service.list(1L,month,null,null,null,50)).isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AMOUNT_RANGE_EXCEEDED);
    }
}
