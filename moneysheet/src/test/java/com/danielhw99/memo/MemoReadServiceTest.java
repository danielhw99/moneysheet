package com.danielhw99.memo;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import com.danielhw99.budget.entity.MonthlyBudget;
import com.danielhw99.budget.entity.BudgetSetupStatus;
import com.danielhw99.budget.repository.MonthlyBudgetRepository;
import com.danielhw99.memo.api.MemoStatus;
import com.danielhw99.memo.api.MemoDirectionValue;
import com.danielhw99.memo.entity.MoneyMemo;
import com.danielhw99.memo.entity.MemoDirection;
import com.danielhw99.memo.repository.MemoQueries;
import com.danielhw99.memo.service.MemoCursor;
import com.danielhw99.memo.service.MemoReadService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
class MemoReadServiceTest {
 final MonthlyBudgetRepository budgets=mock(MonthlyBudgetRepository.class);
 final MemoQueries queries=mock(MemoQueries.class);
 final MemoCursor cursors=new MemoCursor();
 final MemoReadService reads=new MemoReadService(budgets,queries,cursors);
 final YearMonth month=YearMonth.of(2026,11);final LocalDate date=month.atDay(4);
 void ready() {
  var budget=mock(MonthlyBudget.class);when(budget.getSetupStatus()).thenReturn(BudgetSetupStatus.READY);when(budget.getId()).thenReturn(10L);
  when(budgets.findByUser_IdAndMonthStart(1L,month.atDay(1))).thenReturn(Optional.of(budget));
 }
 @Test void queryDateAndStatusAreRequiredAndScopedToMonth() {
  MemoServiceTest.code(com.danielhw99.common.error.ErrorCode.VALIDATION,()->reads.list(1L,month,null,MemoStatus.PENDING,null,50));
  MemoServiceTest.code(com.danielhw99.common.error.ErrorCode.VALIDATION,()->reads.list(1L,month,date,null,null,50));
  MemoServiceTest.code(com.danielhw99.common.error.ErrorCode.VALIDATION,()->reads.list(1L,month,date.plusMonths(1),MemoStatus.PENDING,null,50));
  MemoServiceTest.code(com.danielhw99.common.error.ErrorCode.VALIDATION,()->reads.list(1L,month,date,MemoStatus.PENDING,null,0));
  MemoServiceTest.code(com.danielhw99.common.error.ErrorCode.VALIDATION,()->reads.list(1L,month,date,MemoStatus.PENDING,null,101));verifyNoInteractions(budgets,queries);
 }
 @Test void pagedRowsDoNotLimitTotalsOrIndependentPendingBalance() {
  ready();var row=new MoneyMemo(null,date.minusDays(1),LocalTime.NOON,"정산",100L,MemoDirection.EXPENSE,date);ReflectionTestUtils.setField(row,"id",91L);
  when(queries.page(10L,date,MemoStatus.COMPLETED,null,1)).thenReturn(List.of(row,row));
  when(queries.total(10L,date,MemoStatus.COMPLETED,MemoDirectionValue.INCOME)).thenReturn(BigDecimal.valueOf(200));
  when(queries.total(10L,date,MemoStatus.COMPLETED,MemoDirectionValue.EXPENSE)).thenReturn(BigDecimal.valueOf(100));
  when(queries.total(10L,date,MemoStatus.PENDING,MemoDirectionValue.INCOME)).thenReturn(BigDecimal.valueOf(50));
  when(queries.total(10L,date,MemoStatus.PENDING,MemoDirectionValue.EXPENSE)).thenReturn(BigDecimal.valueOf(20));
  var result=reads.list(1L,month,date,MemoStatus.COMPLETED,null,1);
  assertThat(result.rows()).hasSize(1);assertThat(result.total()).isEqualTo(100);assertThat(result.pendingMemoTotal()).isEqualTo(30);assertThat(result.nextCursor()).isNotNull();
 }
 @Test void aggregateAboveJavascriptSafeIntegerIsRejected() {
  ready();when(queries.page(10L,date,MemoStatus.PENDING,null,50)).thenReturn(List.of());
  when(queries.total(10L,date,MemoStatus.PENDING,MemoDirectionValue.EXPENSE)).thenReturn(new BigDecimal("9007199254740992"));
  when(queries.total(10L,date,MemoStatus.PENDING,MemoDirectionValue.INCOME)).thenReturn(BigDecimal.ZERO);
  MemoServiceTest.code(com.danielhw99.common.error.ErrorCode.AMOUNT_RANGE_EXCEEDED,()->reads.list(1L,month,date,MemoStatus.PENDING,null,50));
 }
}
