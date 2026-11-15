package com.danielhw99.cashflow;
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
import com.danielhw99.cashflow.repository.CashFlowQueries;
import com.danielhw99.cashflow.service.CashFlowCursor;
import com.danielhw99.cashflow.service.CashFlowReadService;
import com.danielhw99.cashflow.api.CashFlowKindValue;
import com.danielhw99.common.error.ApiException;
import com.danielhw99.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
class CashFlowReadServiceTest {
    final YearMonth month=YearMonth.of(2026,11);
    final MonthlyBudgetRepository budgets=mock(MonthlyBudgetRepository.class);
    final CashFlowQueries queries=mock(CashFlowQueries.class);
    final CashFlowReadService service=new CashFlowReadService(budgets,queries,new CashFlowCursor());
    void ready(BigDecimal expense,BigDecimal income) {
        var budget=mock(MonthlyBudget.class);when(budget.getId()).thenReturn(55L);when(budget.getSetupStatus()).thenReturn(BudgetSetupStatus.READY);
        when(budgets.findByUser_IdAndMonthStart(1L,month.atDay(1))).thenReturn(Optional.of(budget));
        when(queries.page(55L,null,null,null,50)).thenReturn(List.of());
        when(queries.total(55L,null,null,CashFlowKindValue.EXTRA_EXPENSE)).thenReturn(expense);
        when(queries.total(55L,null,null,CashFlowKindValue.INCOME)).thenReturn(income);
    }
    @Test void totalsComeFromWholeFilterQueriesRatherThanRows() {
        ready(BigDecimal.valueOf(500),BigDecimal.valueOf(1000));
        var result=service.list(1L,month,null,null,null,50);
        assertThat(result.rows()).isEmpty();assertThat(result.total()).isEqualTo(500);
        assertThat(result.expenseTotal()).isEqualTo(500);assertThat(result.incomeTotal()).isEqualTo(1000);
    }
    @Test void negativeFractionalAndOutOfRangeTotalsAreRejected() {
        for(BigDecimal value:List.of(new BigDecimal("-1"),new BigDecimal("0.5"),new BigDecimal("9007199254740992"))) {
            ready(BigDecimal.ZERO,value);
            assertThatThrownBy(()->service.list(1L,month,null,null,null,50)).isInstanceOf(ApiException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.AMOUNT_RANGE_EXCEEDED);
        }
    }
    @Test void draftMissingBudgetAndInvalidScopeCannotQueryRecords() {
        assertThatThrownBy(()->service.list(1L,month,null,null,null,50)).isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BUDGET_NOT_READY);
        for(int limit:new int[]{0,101}) assertThatThrownBy(()->service.list(1L,month,null,null,null,limit)).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->service.list(1L,month,month.plusMonths(1).atDay(1),null,null,50)).isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION);
        var draft=mock(MonthlyBudget.class);when(draft.getSetupStatus()).thenReturn(BudgetSetupStatus.DRAFT);
        when(budgets.findByUser_IdAndMonthStart(1L,month.atDay(1))).thenReturn(Optional.of(draft));
        assertThatThrownBy(()->service.list(1L,month,null,null,null,50)).isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BUDGET_NOT_READY);
        verifyNoInteractions(queries);
    }
    @Test void safeIntegerLimitAndNegativeNetAreSupported() {
        ready(new BigDecimal("9007199254740991"),BigDecimal.ZERO);
        assertThat(service.list(1L,month,null,null,null,50).total()).isEqualTo(-9_007_199_254_740_991L);
    }
    @Test void unsafeTotalIsAnExplicitBusinessError() {
        ready(new BigDecimal("9007199254740992"),BigDecimal.ZERO);
        assertThatThrownBy(()->service.list(1L,month,null,null,null,50)).isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AMOUNT_RANGE_EXCEEDED);
    }
}
