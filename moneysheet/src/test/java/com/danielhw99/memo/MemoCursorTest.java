package com.danielhw99.memo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import com.danielhw99.memo.service.MemoCursor;
import com.danielhw99.memo.api.MemoStatus;
import com.danielhw99.common.error.ApiException;
import com.danielhw99.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
class MemoCursorTest {
    final MemoCursor codec = new MemoCursor();
    final YearMonth month = YearMonth.of(2026,11);
    final MemoCursor.Anchor anchor = new MemoCursor.Anchor(LocalDate.of(2026,11,5),LocalTime.of(12,30),91L);
    @Test void signedCursorRoundTripsAndRejectsDifferentScope() {
        String token = codec.encode(1L,month,month.atDay(5),MemoStatus.PENDING,anchor);
        assertThat(codec.decode(token,1L,month,month.atDay(5),MemoStatus.PENDING)).isEqualTo(anchor);
        invalid(() -> codec.decode(token,2L,month,month.atDay(5),MemoStatus.PENDING));
        invalid(() -> codec.decode(token,1L,month.plusMonths(1),month.plusMonths(1).atDay(5),MemoStatus.PENDING));
        invalid(() -> codec.decode(token,1L,month,month.atDay(6),MemoStatus.PENDING));
        invalid(() -> codec.decode(token,1L,month,month.atDay(5),MemoStatus.COMPLETED));
    }
    @Test void tamperingAndOversizedCursorAreValidationErrors() {
        String token=codec.encode(1L,month,month.atDay(5),MemoStatus.PENDING,anchor);
        invalid(() -> codec.decode("A"+token,1L,month,month.atDay(5),MemoStatus.PENDING));
        invalid(() -> codec.decode("x".repeat(4096),1L,month,month.atDay(5),MemoStatus.PENDING));
        invalid(() -> codec.decode("",1L,month,month.atDay(5),MemoStatus.PENDING));
        assertThat(codec.decode(null,1L,month,month.atDay(5),MemoStatus.PENDING)).isNull();
    }
    private void invalid(Runnable call) {
        assertThatThrownBy(call::run).isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(ErrorCode.VALIDATION);
    }
}
