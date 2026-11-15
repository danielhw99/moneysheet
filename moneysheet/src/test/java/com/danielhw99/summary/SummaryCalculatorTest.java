package com.danielhw99.summary;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import com.danielhw99.budget.service.BudgetCalendar;
import com.danielhw99.summary.service.SummaryCalculator;
import com.danielhw99.summary.service.SummaryFacts.Amounts;
import com.danielhw99.summary.service.SummaryFacts.Food;
import com.danielhw99.summary.service.SummaryFacts.Ledger;
import com.danielhw99.summary.service.SummaryFacts.MemoWindow;
import com.danielhw99.common.error.ApiException;
import com.danielhw99.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

class SummaryCalculatorTest {
    final SummaryCalculator calculator=new SummaryCalculator(new BudgetCalendar(Clock.systemUTC()));
    final Food uniform=new Food(11000,11000,11000);
    final YearMonth month=YearMonth.of(2026,11);
    BigDecimal amount(long value) { return BigDecimal.valueOf(value); }
    @Test void firstDayStartsAtZeroAndFoodSavingsBelongToTheSameDay() {
        var date=month.atDay(1);var facts=new Ledger(Map.of(date,new Amounts(amount(9000),amount(50000))),Map.of(date,amount(100000)),List.of());
        var day=calculator.daily(date,uniform,facts);
        assertThat(day.carryoverFreeBalance()).isZero();assertThat(day.availableBeforeSpending()).isEqualTo(11000);
        assertThat(day.freeBalance()).isEqualTo(2000);assertThat(day.remainingFood()).isEqualTo(110000);
        assertThat(day.mainBalance()).isEqualTo(162000);assertThat(day.incomeTotal()).isEqualTo(50000);
    }
    @Test void nextDayCarriesOnlySameMonthFreeBalanceAndMainIncomeIsCumulative() {
        var facts=new Ledger(Map.of(month.atDay(1),new Amounts(amount(9000),amount(50000)),month.atDay(2),new Amounts(amount(22000),amount(2000)),month.minusMonths(1).atEndOfMonth(),new Amounts(amount(999999),amount(999999))),Map.of(),List.of());
        var day=calculator.daily(month.atDay(2),uniform,facts);
        assertThat(day.carryoverFreeBalance()).isEqualTo(2000);assertThat(day.freeBalance()).isEqualTo(-9000);
        assertThat(day.expenseTotal()).isEqualTo(22000);assertThat(day.incomeTotal()).isEqualTo(2000);
        assertThat(day.mainBalance()).isEqualTo(-9000+99000+52000);
    }
    @Test void installmentEndsAndNewInstallmentsHaveNoDuplicateAllocation() {
        for(int value:new int[]{11,21,month.lengthOfMonth()}) assertThat(calculator.daily(month.atDay(value),uniform,Ledger.empty()).remainingFood()).isZero();
        assertThat(calculator.daily(month.atDay(12),uniform,Ledger.empty()).remainingFood()).isEqualTo(9*11000);
        assertThat(calculator.daily(month.atDay(22),uniform,Ledger.empty()).remainingFood()).isEqualTo(8*11000);
        assertThat(calculator.daily(month.atDay(12),uniform,Ledger.empty()).freeBalance()).isEqualTo(12*11000);
        assertThat(calculator.daily(month.atDay(11),uniform,Ledger.empty()).mainBalance()).isEqualTo(11*11000);
        assertThat(calculator.daily(month.atDay(12),uniform,Ledger.empty()).mainBalance()).isEqualTo(21*11000);
        assertThat(calculator.daily(month.atDay(21),uniform,Ledger.empty()).mainBalance()).isEqualTo(21*11000);
        assertThat(calculator.daily(month.atDay(22),uniform,Ledger.empty()).mainBalance()).isEqualTo(30*11000);
    }
    @Test void weekdaySaturdaySundayAndAllMonthLengthsIncludingLeapDayAreRespected() {
        Food food=new Food(11000,15000,10000);
        assertThat(calculator.daily(LocalDate.of(2026,11,6),food,Ledger.empty()).foodBudget()).isEqualTo(11000);
        assertThat(calculator.daily(LocalDate.of(2026,11,7),food,Ledger.empty()).foodBudget()).isEqualTo(15000);
        assertThat(calculator.daily(LocalDate.of(2026,11,8),food,Ledger.empty()).foodBudget()).isEqualTo(10000);
        for(var value:List.of(YearMonth.of(2027,2),YearMonth.of(2028,2),YearMonth.of(2026,4),YearMonth.of(2026,1))) {
            var last=calculator.daily(value.atEndOfMonth(),uniform,Ledger.empty());
            assertThat(last.remainingFood()).isZero();assertThat(last.freeBalance()).isEqualTo(value.lengthOfMonth()*11000L);
        }
    }
    @Test void memoAffectsOnlyMainFromOccurrenceThroughDayBeforeCompletion() {
        var facts=new Ledger(Map.of(),Map.of(),List.of(
                new MemoWindow(month.atDay(2),month.atDay(4),amount(45000),amount(50000),4),
                new MemoWindow(month.atDay(5),null,amount(99999),BigDecimal.ZERO,1),
                new MemoWindow(month.minusMonths(1).atDay(1),null,amount(99999),BigDecimal.ZERO,1)));
        var noMemo=calculator.daily(month.atDay(3),uniform,Ledger.empty());var active=calculator.daily(month.atDay(3),uniform,facts);
        assertThat(active.freeBalance()).isEqualTo(noMemo.freeBalance());assertThat(active.mainBalance()).isEqualTo(noMemo.mainBalance()+5000);
        assertThat(active.activeMemos()).isEqualTo(4);assertThat(active.completedMemos()).isZero();
        var completed=calculator.daily(month.atDay(4),uniform,facts);assertThat(completed.activeMemos()).isZero();assertThat(completed.completedMemos()).isEqualTo(4);
        assertThat(completed.mainBalance()).isEqualTo(calculator.daily(month.atDay(4),uniform,Ledger.empty()).mainBalance());
        assertThat(calculator.daily(month.atDay(1),uniform,facts).activeMemos()).isZero();
    }
    @Test void monthlyNetUsesBudgetsAndMonthlyFlowsInsteadOfActualPaymentsOrMainDeposits() {
        var result=calculator.monthly(month,new Food(10000,10000,10000),amount(650000),amount(4000000),amount(100000));
        assertThat(result.foodBudget()).isEqualTo(300000);assertThat(result.basicTotal()).isEqualTo(950000);
        assertThat(result.totalExpense()).isEqualTo(1050000);assertThat(result.net()).isEqualTo(2950000);
    }
    @Test void exactAndIntermediateUnsafeTotalsAreRejectedInsteadOfBeingCancelledOrRounded() {
        for(String value:List.of("9007199254740992","0.5","-1")) code(()->calculator.positive(new BigDecimal(value)));
        assertThat(calculator.positive(new BigDecimal("9007199254740991"))).isEqualTo(9_007_199_254_740_991L);
        code(()->calculator.monthly(month,uniform,new BigDecimal("9007199254740991"),BigDecimal.ZERO,BigDecimal.ZERO));
        var overflow=new Ledger(Map.of(month.atDay(1),new Amounts(BigDecimal.ZERO,new BigDecimal("9007199254740991")),month.atDay(2),new Amounts(BigDecimal.ZERO,BigDecimal.ONE)),Map.of(),List.of());
        code(()->calculator.daily(month.atDay(2),uniform,overflow));
    }
    @Test void futureSpendingAndIncomeDoNotChangeHistoricalDailyValues() {
        var facts=new Ledger(Map.of(month.atDay(30),new Amounts(amount(999999),amount(999999))),Map.of(),List.of());
        assertThat(calculator.daily(month.atDay(1),uniform,facts)).isEqualTo(calculator.daily(month.atDay(1),uniform,Ledger.empty()));
    }
    void code(Runnable call) { assertThatThrownBy(call::run).isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.AMOUNT_RANGE_EXCEEDED); }
}
