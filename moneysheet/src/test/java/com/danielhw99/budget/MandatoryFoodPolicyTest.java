package com.danielhw99.budget;
import static org.assertj.core.api.Assertions.*;
import java.time.Clock;
import java.time.YearMonth;
import com.danielhw99.budget.api.BudgetDtos;
import com.danielhw99.budget.service.BudgetCalendar;
import com.danielhw99.common.config.ApiJsonConfiguration;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
class MandatoryFoodPolicyTest {
 @Test void splitUsesFirstTenSecondTenAndRemainder() {
  assertThat(new BudgetCalendar(Clock.systemUTC()).foodInstallments(YearMonth.of(2026,11),11000,15000,10000)).containsExactly(112000L,113000L,116000L);
 }
 @Test void enabledIsNoLongerAnInputField() {
  var builder=JsonMapper.builder();new ApiJsonConfiguration().strictApiJson().customize(builder);
  assertThatThrownBy(()->builder.build().readValue("{\"enabled\":true,\"paymentMode\":\"monthStart\",\"weekday\":1,\"saturday\":1,\"sunday\":1}",BudgetDtos.Food.class)).isInstanceOf(Exception.class);
 }
}
