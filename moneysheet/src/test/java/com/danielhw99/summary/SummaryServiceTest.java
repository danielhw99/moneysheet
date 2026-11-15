package com.danielhw99.summary;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.danielhw99.summary.service.SummaryService;
import com.danielhw99.summary.service.SummaryCalculator;
import com.danielhw99.summary.service.SummaryFacts.Ledger;
import com.danielhw99.summary.service.SummaryFacts.Amounts;
import com.danielhw99.summary.repository.SummaryQueries;
import com.danielhw99.summary.api.SummaryDtos;
import com.danielhw99.budget.service.BudgetCalendar;
import com.danielhw99.budget.entity.MonthlyBudget;
import com.danielhw99.budget.entity.BudgetSetupStatus;
import com.danielhw99.budget.repository.MonthlyBudgetRepository;
import com.danielhw99.common.error.ApiException;
import com.danielhw99.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
class SummaryServiceTest {
    final Clock clock=Clock.fixed(Instant.parse("2026-11-15T16:00:00Z"),ZoneOffset.UTC);
    final MonthlyBudgetRepository budgets=mock(MonthlyBudgetRepository.class);
    final SummaryQueries queries=mock(SummaryQueries.class);
    final SummaryService service=new SummaryService(budgets,queries,new SummaryCalculator(new BudgetCalendar(clock)),clock);
    final YearMonth month=YearMonth.of(2026,11);
    void ready(YearMonth selected) {
        var budget=mock(MonthlyBudget.class);when(budget.getId()).thenReturn(51L);when(budget.getSetupStatus()).thenReturn(BudgetSetupStatus.READY);
        when(budget.getFoodWeekday()).thenReturn(11000L);when(budget.getFoodSaturday()).thenReturn(11000L);when(budget.getFoodSunday()).thenReturn(11000L);
        when(budgets.findByUser_IdAndMonthStart(1L,selected.atDay(1))).thenReturn(Optional.of(budget));
        when(queries.ledger(51L)).thenReturn(Ledger.empty());when(queries.totals(51L)).thenReturn(new SummaryQueries.Totals(BigDecimal.valueOf(650000),BigDecimal.valueOf(4000000),BigDecimal.valueOf(100000)));
        when(queries.dailyRows(51L,selected.atDay(1))).thenReturn(new SummaryQueries.DailyRows(List.of(),List.of(),List.of(),Map.of()));
    }
    @Test void dailyUsesOnlyTheOwnedReadyBudgetAndExposesAllApprovedFields() {
        ready(month);var daily=service.daily(1L,month.atDay(1));
        assertThat(daily.freeBalance()).isEqualTo(11000);assertThat(daily.transactions()).isEmpty();assertThat(daily.activeMemos()).isEmpty();assertThat(daily.payments()).isEmpty();
        verify(budgets).findByUser_IdAndMonthStart(1L,month.atDay(1));
    }
    @Test void missingAndDraftBudgetsAreBlockedForAllSummaryRoutes() {
        code(ErrorCode.BUDGET_NOT_READY,()->service.daily(1L,month.atDay(1)));
        code(ErrorCode.BUDGET_NOT_READY,()->service.monthly(1L,month));code(ErrorCode.BUDGET_NOT_READY,()->service.home(1L,month));
        var draft=mock(MonthlyBudget.class);when(draft.getSetupStatus()).thenReturn(BudgetSetupStatus.DRAFT);
        when(budgets.findByUser_IdAndMonthStart(1L,month.atDay(1))).thenReturn(Optional.of(draft));
        code(ErrorCode.BUDGET_NOT_READY,()->service.daily(1L,month.atDay(1)));verifyNoInteractions(queries);
    }
    @Test void homeUsesSeoulTodayShowsEveryDateAndOmitsOnlyFutureFreeEntries() {
        ready(month);when(queries.ledger(51L)).thenReturn(new Ledger(Map.of(month.atDay(20),new Amounts(BigDecimal.valueOf(100),BigDecimal.ZERO)),Map.of(),List.of()));
        var home=service.home(1L,month);assertThat(home.asOfDate()).isEqualTo(LocalDate.of(2026,11,16));assertThat(home.days()).hasSize(30);
        assertThat(home.days().get(15).entries()).anyMatch(row->row instanceof SummaryDtos.AmountEntry a && a.kind().equals("free"));
        assertThat(home.days().get(19).entries()).anyMatch(row->row instanceof SummaryDtos.AmountEntry a && a.kind().equals("transaction") && a.amount()==-100);
        assertThat(home.days().get(19).entries()).noneMatch(row->row instanceof SummaryDtos.AmountEntry a && a.kind().equals("free"));
        assertThat(home.freeBalance()).isEqualTo(16*11000);assertThat(home.expense()).isEqualTo(650000+330000+100000);
        verify(queries,times(1)).ledger(51L);
    }
    @Test void historicalHomeUsesMonthEndAndFutureMonthIsRejectedWithoutDatabaseReads() {
        var past=month.minusMonths(1);ready(past);var home=service.home(1L,past);
        assertThat(home.asOfDate()).isEqualTo(past.atEndOfMonth());assertThat(home.days()).hasSize(31);assertThat(home.freeBalance()).isEqualTo(31*11000);
        clearInvocations(budgets,queries);code(ErrorCode.FUTURE_MONTH_NOT_ALLOWED,()->service.home(1L,month.plusMonths(1)));verifyNoInteractions(budgets,queries);
    }
    @Test void monthlyUsesBudgetTotalsAndNotDailySpendingOrMainDeposits() {
        ready(month);var monthly=service.monthly(1L,month);assertThat(monthly.income()).isEqualTo(4000000);assertThat(monthly.totalExpense()).isEqualTo(1080000);assertThat(monthly.net()).isEqualTo(2920000);
        verify(queries,never()).ledger(any());verify(queries,never()).dailyRows(any(),any());
    }
    void code(ErrorCode expected,Runnable call) { assertThatThrownBy(call::run).isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(expected); }
}
