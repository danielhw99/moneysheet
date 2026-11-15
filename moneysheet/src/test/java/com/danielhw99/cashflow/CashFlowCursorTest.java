package com.danielhw99.cashflow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import com.danielhw99.cashflow.service.CashFlowCursor;
import com.danielhw99.cashflow.api.CashFlowKindValue;
import com.danielhw99.common.error.ApiException;
import com.danielhw99.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
class CashFlowCursorTest {
    final CashFlowCursor codec = new CashFlowCursor();
    final YearMonth month = YearMonth.of(2026,11);
    final CashFlowCursor.Anchor anchor = new CashFlowCursor.Anchor(LocalDate.of(2026,11,5),LocalTime.of(12,30),91L);
    @Test void signedCursorRoundTripsAndRejectsDifferentScope() {
        String token = codec.encode(1L,month,null,null,anchor);
        assertThat(codec.decode(token,1L,month,null,null)).isEqualTo(anchor);
        invalid(() -> codec.decode(token,2L,month,null,null));
        invalid(() -> codec.decode(token,1L,month.plusMonths(1),null,null));
        invalid(() -> codec.decode(token,1L,month,month.atDay(5),null));
        invalid(() -> codec.decode(token,1L,month,null,CashFlowKindValue.INCOME));
    }
    @Test void tamperingAndOversizedCursorAreValidationErrors() {
        String token=codec.encode(1L,month,null,null,anchor);
        invalid(() -> codec.decode("A"+token,1L,month,null,null));
        invalid(() -> codec.decode("x".repeat(4096),1L,month,null,null));
        invalid(() -> codec.decode("",1L,month,null,null));
        assertThat(codec.decode(null,1L,month,null,null)).isNull();
    }
    @Test void dateAndKindFilteredCursorsAreBoundToTheSameScopeAndKey() {
        String token=codec.encode(1L,month,month.atDay(5),CashFlowKindValue.EXTRA_EXPENSE,anchor);
        assertThat(codec.decode(token,1L,month,month.atDay(5),CashFlowKindValue.EXTRA_EXPENSE)).isEqualTo(anchor);
        invalid(()->codec.decode(token,1L,month,month.atDay(5),CashFlowKindValue.INCOME));
        invalid(()->new CashFlowCursor().decode(token,1L,month,month.atDay(5),CashFlowKindValue.EXTRA_EXPENSE));
        String wrongDate=codec.encode(1L,month,month.atDay(6),null,anchor);
        invalid(()->codec.decode(wrongDate,1L,month,month.atDay(6),null));
    }
    private void invalid(Runnable call) {
        assertThatThrownBy(call::run).isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.VALIDATION);
    }
}
