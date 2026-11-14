package com.danielhw99.payment;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import com.danielhw99.payment.repository.PaymentQueries;
import com.danielhw99.payment.entity.FixedPayment;
import com.danielhw99.payment.service.PaymentReadService;
import com.danielhw99.payment.service.PaymentCursor;
import com.danielhw99.budget.entity.MonthlyBudget;
import com.danielhw99.budget.entity.FixedBudgetItem;
import com.danielhw99.budget.entity.BudgetSetupStatus;
import com.danielhw99.budget.repository.MonthlyBudgetRepository;
import com.danielhw99.budget.repository.FixedBudgetItemRepository;
import com.danielhw99.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
class PaymentReadServiceTest {
 final MonthlyBudgetRepository budgets=mock(MonthlyBudgetRepository.class);
 final FixedBudgetItemRepository items=mock(FixedBudgetItemRepository.class);
 final PaymentQueries queries=mock(PaymentQueries.class);
 final PaymentCursor cursors=new PaymentCursor();
 final PaymentReadService reads=new PaymentReadService(budgets,items,queries,cursors);
 final YearMonth month=YearMonth.of(2026,11);
 FixedBudgetItem ready() {
  var budget=mock(MonthlyBudget.class);when(budget.getId()).thenReturn(10L);when(budget.getSetupStatus()).thenReturn(BudgetSetupStatus.READY);
  when(budgets.findByUser_IdAndMonthStart(1L,month.atDay(1))).thenReturn(Optional.of(budget));
  var item=new FixedBudgetItem(budget,null,"고정비",100000L);ReflectionTestUtils.setField(item,"id",51L);when(items.findById(51L)).thenReturn(Optional.of(item));return item;
 }
 @Test void filteredPageKeepsPositiveFullExpenseTotals() {
  var item=ready();var row=new FixedPayment(item,month.atDay(2),LocalTime.NOON,"지급",10000L);ReflectionTestUtils.setField(row,"id",91L);
  when(queries.page(10L,null,51L,null,1)).thenReturn(List.of(row,row));when(queries.total(10L,null,51L)).thenReturn(BigDecimal.valueOf(90000));
  var result=reads.list(1L,month,null,51L,null,1);assertThat(result.rows()).hasSize(1);assertThat(result.nextCursor()).isNotNull();assertThat(result.expenseTotal()).isEqualTo(90000);assertThat(result.total()).isEqualTo(90000);assertThat(result.incomeTotal()).isZero();
 }
 @Test void invalidDateLimitOrItemIdDoesNotQueryTheDatabase() {
  PaymentServiceTest.code(ErrorCode.VALIDATION,()->reads.list(1L,month,month.plusMonths(1).atDay(1),null,null,50));
  PaymentServiceTest.code(ErrorCode.VALIDATION,()->reads.list(1L,month,null,null,null,0));
  PaymentServiceTest.code(ErrorCode.VALIDATION,()->reads.list(1L,month,null,null,null,101));
  PaymentServiceTest.code(ErrorCode.VALIDATION,()->reads.list(1L,month,null,0L,null,50));verifyNoInteractions(budgets,items,queries);
 }
 @Test void itemFilterMustBelongToTheOwnersSelectedMonthlyBudget() {
  ready();var other=mock(MonthlyBudget.class);when(other.getId()).thenReturn(11L);
  var item=new FixedBudgetItem(other,null,"다른 월 또는 소유자",1L);when(items.findById(52L)).thenReturn(Optional.of(item));
  PaymentServiceTest.code(ErrorCode.NOT_FOUND,()->reads.list(1L,month,null,52L,null,50));verifyNoInteractions(queries);
 }
 @Test void unsafeAggregateIsRejected() {
  ready();when(queries.page(10L,null,null,null,50)).thenReturn(List.of());when(queries.total(10L,null,null)).thenReturn(new BigDecimal("9007199254740992"));
  PaymentServiceTest.code(ErrorCode.AMOUNT_RANGE_EXCEEDED,()->reads.list(1L,month,null,null,null,50));
 }
}
