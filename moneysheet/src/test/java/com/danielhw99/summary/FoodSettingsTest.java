package com.danielhw99.summary;

import static org.assertj.core.api.Assertions.*;
import java.time.Clock;
import java.time.YearMonth;
import java.math.BigDecimal;
import com.danielhw99.budget.entity.FoodPaymentMode;
import com.danielhw99.budget.service.BudgetCalendar;
import com.danielhw99.summary.service.SummaryCalculator;
import com.danielhw99.summary.service.SummaryFacts.Food;
import com.danielhw99.summary.service.SummaryFacts.Ledger;
import org.junit.jupiter.api.Test;

class FoodSettingsTest {
    final SummaryCalculator calculator = new SummaryCalculator(new BudgetCalendar(Clock.systemUTC()));
    @Test void zeroFoodCannotBeUsedAsACompletedBudget() {
        Food food = new Food(0, 11000, 11000);
        assertThatThrownBy(()->calculator.daily(YearMonth.of(2026,11).atDay(2),food,Ledger.empty())).isInstanceOf(com.danielhw99.common.error.ApiException.class);
    }
    @Test void monthStartPaysAllButDailyFreeStillReceivesOnlyTodaysFood() {
        for (YearMonth month : new YearMonth[]{YearMonth.of(2026,11),YearMonth.of(2028,2)}) {
            Food food = new Food(FoodPaymentMode.MONTH_START,11000L,11000L,11000L);
            for(int day : new int[]{1,11,12,21,22,month.lengthOfMonth()}) {
                var result=calculator.daily(month.atDay(day),food,Ledger.empty());
                assertThat(result.freeBalance()).isEqualTo(day*11000L);
                assertThat(result.remainingFood()).isEqualTo((month.lengthOfMonth()-day)*11000L);
                assertThat(result.mainBalance()).isEqualTo(month.lengthOfMonth()*11000L);
            }
        }
    }
}
