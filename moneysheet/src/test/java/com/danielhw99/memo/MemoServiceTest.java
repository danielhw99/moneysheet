package com.danielhw99.memo;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import com.danielhw99.memo.api.MemoInput;
import com.danielhw99.memo.api.MemoDirectionValue;
import com.danielhw99.memo.entity.MoneyMemo;
import com.danielhw99.memo.entity.MemoDirection;
import com.danielhw99.memo.repository.MoneyMemoRepository;
import com.danielhw99.memo.service.MemoService;
import com.danielhw99.budget.entity.MonthlyBudget;
import com.danielhw99.budget.entity.BudgetSetupStatus;
import com.danielhw99.budget.repository.MonthlyBudgetRepository;
import com.danielhw99.common.error.ApiException;
import com.danielhw99.common.error.ErrorCode;
import com.danielhw99.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
class MemoServiceTest {
 final MoneyMemoRepository records=mock(MoneyMemoRepository.class);
 final MonthlyBudgetRepository budgets=mock(MonthlyBudgetRepository.class);
 final MemoService service=new MemoService(records,budgets);
 final YearMonth month=YearMonth.of(2026,11);
 MemoInput input(long amount) { return new MemoInput(month.atDay(2),LocalTime.NOON,"  정산  ",amount,MemoDirectionValue.EXPENSE); }
 MonthlyBudget ready() {
  var user=mock(User.class);when(user.getId()).thenReturn(1L);
  var budget=new MonthlyBudget(user,month.atDay(1),BudgetSetupStatus.READY,1L,1L,1L);
  ReflectionTestUtils.setField(budget,"id",10L);
  when(budgets.findByUser_IdAndMonthStart(1L,month.atDay(1))).thenReturn(Optional.of(budget));return budget;
 }
 @Test void createsTrimmedPendingMemoWithDirectId() {
  ready();when(records.saveAndFlush(any())).thenAnswer(call->{var row=call.getArgument(0);ReflectionTestUtils.setField(row,"id",91L);return row;});
  var result=service.create(1L,month,input(45000));
  assertThat(result.id().value()).isEqualTo(91);assertThat(result.title()).isEqualTo("정산");assertThat(result.completedOn()).isNull();assertThat(result.direction()).isEqualTo(MemoDirectionValue.EXPENSE);
 }
 @Test void missingAndDraftBudgetCannotCreate() {
  code(ErrorCode.BUDGET_NOT_READY,()->service.create(1L,month,input(1)));
  var draft=new MonthlyBudget(mock(User.class),month.atDay(1),BudgetSetupStatus.DRAFT,null,null,null);
  when(budgets.findByUser_IdAndMonthStart(1L,month.atDay(1))).thenReturn(Optional.of(draft));
  code(ErrorCode.BUDGET_NOT_READY,()->service.create(1L,month,input(1)));verifyNoInteractions(records);
 }
 @Test void validatesMoneyMonthTimeTitleAndDirection() {
  ready();
  for(long amount:new long[]{0,-1,10_000_000_000L}) code(ErrorCode.VALIDATION,()->service.create(1L,month,input(amount)));
  for(MemoInput value:List.of(new MemoInput(month.plusMonths(1).atDay(1),LocalTime.NOON,"x",1L,MemoDirectionValue.EXPENSE),new MemoInput(month.atDay(1),LocalTime.of(1,2,3),"x",1L,MemoDirectionValue.EXPENSE),new MemoInput(month.atDay(1),LocalTime.NOON," ",1L,MemoDirectionValue.EXPENSE),new MemoInput(month.atDay(1),LocalTime.NOON,"x".repeat(256),1L,MemoDirectionValue.EXPENSE),new MemoInput(month.atDay(1),LocalTime.NOON,"x",1L,null))) code(ErrorCode.VALIDATION,()->service.create(1L,month,value));
  verifyNoInteractions(records);
 }
 @Test void missingMemoIsNotFoundRegardlessOfOwnersBudget() {
  code(ErrorCode.NOT_FOUND,()->service.get(1L,month,99L));code(ErrorCode.NOT_FOUND,()->service.update(1L,month,99L,input(1)));code(ErrorCode.NOT_FOUND,()->service.delete(1L,month,99L));
 }
 MoneyMemo record(LocalDate completed) {
  var row=new MoneyMemo(ready(),month.atDay(2),LocalTime.NOON,"원본",100L,MemoDirection.EXPENSE,completed);
  ReflectionTestUtils.setField(row,"id",91L);
  when(records.findById(91L)).thenReturn(Optional.of(row));when(records.findLockedById(91L)).thenReturn(Optional.of(row));return row;
 }
 @Test void otherOwnerOrMonthCannotReadOrWrite() {
  record(null);
  code(ErrorCode.NOT_FOUND,()->service.get(2L,month,91L));
  code(ErrorCode.NOT_FOUND,()->service.update(2L,month,91L,input(1)));
  code(ErrorCode.NOT_FOUND,()->service.delete(1L,month.plusMonths(1),91L));
  verify(records,never()).delete(any());
 }
 @Test void updatesDoNotEraseCurrentCompletionDate() {
  var row=record(month.atDay(4));
  var result=service.update(1L,month,91L,new MemoInput(month.atDay(3),LocalTime.of(10,30),"  변경  ",500L,MemoDirectionValue.INCOME));
  assertThat(result.completedOn()).isEqualTo(month.atDay(4));assertThat(row.getTitle()).isEqualTo("변경");assertThat(result.amount()).isEqualTo(500);
  verify(records).findLockedById(91L);
 }
 @Test void movingOccurrenceAfterCompletionIsRejectedWithoutMutation() {
  var row=record(month.atDay(4));
  code(ErrorCode.VALIDATION,()->service.update(1L,month,91L,new MemoInput(month.atDay(5),LocalTime.NOON,"변경",1L,MemoDirectionValue.EXPENSE)));
  assertThat(row.getOccurredOn()).isEqualTo(month.atDay(2));assertThat(row.getTitle()).isEqualTo("원본");
 }
 @Test void deletionUsesTheSameWriteLockAsOtherEdits() {
  var row=record(null);service.delete(1L,month,91L);verify(records).findLockedById(91L);verify(records).delete(row);
 }
 @Test void completingOverwritesOnlyTheCurrentDateAndCancellationIsIdempotent() {
  var row=record(null);
  assertThat(service.complete(1L,month,91L,month.atDay(4)).completedOn()).isEqualTo(month.atDay(4));
  assertThat(service.complete(1L,month,91L,month.atDay(5)).completedOn()).isEqualTo(month.atDay(5));
  assertThat(service.cancelCompletion(1L,month,91L).completedOn()).isNull();
  assertThat(service.cancelCompletion(1L,month,91L).completedOn()).isNull();
  assertThat(row.getAmount()).isEqualTo(100);
 }
 @Test void invalidCompletionsDoNotChangeTheMemo() {
  var row=record(null);
  for(LocalDate date:List.of(month.atDay(1),month.plusMonths(1).atDay(1))) code(ErrorCode.VALIDATION,()->service.complete(1L,month,91L,date));
  code(ErrorCode.VALIDATION,()->service.complete(1L,month,91L,null));
  code(ErrorCode.NOT_FOUND,()->service.complete(2L,month,91L,month.atDay(4)));
  assertThat(row.getCompletedOn()).isNull();
 }
 static void code(ErrorCode expected,Runnable call) { assertThatThrownBy(call::run).isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(expected); }
}
