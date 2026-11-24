package com.danielhw99.summary;
import static org.assertj.core.api.Assertions.*;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.math.BigDecimal;
import java.util.Map;
import java.util.List;
import com.danielhw99.budget.entity.FoodPaymentMode;
import com.danielhw99.budget.service.BudgetCalendar;
import com.danielhw99.summary.service.SummaryCalculator;
import com.danielhw99.summary.service.SummaryFacts.Food;
import com.danielhw99.summary.service.SummaryFacts.Amounts;
import com.danielhw99.summary.service.SummaryFacts.Ledger;
import org.junit.jupiter.api.Test;
class FoodStartDateTest {
 final SummaryCalculator calc=new SummaryCalculator(new BudgetCalendar(Clock.systemUTC()));
 Food food(FoodPaymentMode mode,LocalDate start) throws Exception {return new Food(mode,11000L,11000L,11000L,start);}
 @Test void priorRecordsRemainButPriorFoodAndFundingAreZero() throws Exception {
  var start=LocalDate.of(2026,11,17);var food=food(FoodPaymentMode.THREE_INSTALLMENTS,start);
  var ledger=new Ledger(Map.of(start.minusDays(1),new Amounts(BigDecimal.valueOf(3000),BigDecimal.ZERO)),Map.of(),List.of());
  assertThat(calc.daily(start.minusDays(1),food,ledger).remainingFood()).isZero();
  var day=calc.daily(start,food,ledger);assertThat(day.freeBalance()).isEqualTo(8000);assertThat(day.remainingFood()).isEqualTo(33000);assertThat(day.mainBalance()).isEqualTo(41000);
  assertThat(calc.monthly(YearMonth.from(start),food,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO).foodBudget()).isEqualTo(154000);
 }
 @Test void fullPaymentStartsOnChosenDayAndDailyFreeStillReceivesOneDay() throws Exception {
  var start=LocalDate.of(2026,11,17);var day=calc.daily(start,food(FoodPaymentMode.MONTH_START,start),Ledger.empty());
  assertThat(day.freeBalance()).isEqualTo(11000);assertThat(day.remainingFood()).isEqualTo(143000);assertThat(day.mainBalance()).isEqualTo(154000);
 }

 @Test void boundaryDaysAndLeapMonthsSumOnlyTheSelectedPeriod() throws Exception {
  for(var month:List.of(YearMonth.of(2026,11),YearMonth.of(2024,2),YearMonth.of(2025,2)))
   for(int startDay:new int[]{1,10,11,20,21,month.lengthOfMonth()})
    for(var mode:FoodPaymentMode.values()) {
     var start=month.atDay(startDay);var f=food(mode,start);
     assertThat(calc.monthly(month,f,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO).foodBudget()).isEqualTo((month.lengthOfMonth()-startDay+1)*11000L);
     assertThat(calc.daily(month.atEndOfMonth(),f,Ledger.empty()).freeBalance()).isEqualTo((month.lengthOfMonth()-startDay+1)*11000L);
     assertThat(calc.daily(month.atEndOfMonth(),f,Ledger.empty()).remainingFood()).isZero();
    }
 }
}
