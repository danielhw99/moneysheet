package com.danielhw99.transaction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import com.danielhw99.transaction.service.TransactionCursor;
import com.danielhw99.transaction.api.TransactionDirectionValue;
import com.danielhw99.common.error.ApiException;
import com.danielhw99.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
class TransactionCursorTest {
    final TransactionCursor codec = new TransactionCursor();
    final YearMonth month = YearMonth.of(2026,11);
    final TransactionCursor.Anchor anchor = new TransactionCursor.Anchor(LocalDate.of(2026,11,5),LocalTime.of(12,30),91L);
    @Test void signedCursorRoundTripsAndRejectsDifferentScope() {
        String token = codec.encode(1L,month,null,null,anchor);
        assertThat(codec.decode(token,1L,month,null,null)).isEqualTo(anchor);
        invalid(() -> codec.decode(token,2L,month,null,null));
        invalid(() -> codec.decode(token,1L,month.plusMonths(1),null,null));
        invalid(() -> codec.decode(token,1L,month,month.atDay(5),null));
        invalid(() -> codec.decode(token,1L,month,null,TransactionDirectionValue.INCOME));
    }
    @Test void tamperingAndOversizedCursorAreValidationErrors() {
        String token=codec.encode(1L,month,null,null,anchor);
        invalid(() -> codec.decode("A"+token,1L,month,null,null));
        invalid(() -> codec.decode("x".repeat(4096),1L,month,null,null));
        invalid(() -> codec.decode("",1L,month,null,null));
        assertThat(codec.decode(null,1L,month,null,null)).isNull();
    }
    private void invalid(Runnable call) {
        assertThatThrownBy(call::run).isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.VALIDATION);
    }
}
