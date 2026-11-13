package com.danielhw99.budget;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.danielhw99.auth.security.AuthPrincipal;
import com.danielhw99.budget.api.BudgetController;
import com.danielhw99.budget.service.BudgetService;
import com.danielhw99.common.error.ApiException;
import com.danielhw99.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

class BudgetControllerTest {
    private final BudgetController controller = new BudgetController(mock(BudgetService.class));
    private final AuthPrincipal principal = new AuthPrincipal(1L, "test@example.com", "test");

    @Test void malformedMonthIsValidationError() {
        assertThatThrownBy(() -> controller.get(principal, "2026-13"))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.VALIDATION);
    }

    @Test void malformedItemIdIsValidationError() {
        assertThatThrownBy(() -> controller.deleteItem(principal, "2026-11", "abc"))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.VALIDATION);
    }
}
