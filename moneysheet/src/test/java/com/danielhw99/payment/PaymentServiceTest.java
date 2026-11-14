package com.danielhw99.payment;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import com.danielhw99.payment.api.PaymentInput;
import com.danielhw99.payment.entity.FixedPayment;
import com.danielhw99.payment.repository.FixedPaymentRepository;
import com.danielhw99.payment.service.PaymentService;
import com.danielhw99.budget.entity.MonthlyBudget;
import com.danielhw99.budget.entity.FixedBudgetItem;
import com.danielhw99.budget.entity.BudgetSetupStatus;
import com.danielhw99.budget.repository.MonthlyBudgetRepository;
import com.danielhw99.budget.repository.FixedBudgetItemRepository;
import com.danielhw99.user.entity.User;
import com.danielhw99.user.repository.UserRepository;
import com.danielhw99.common.api.ApiId;
import com.danielhw99.common.error.ApiException;
import com.danielhw99.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
class PaymentServiceTest {
 final FixedPaymentRepository records=mock(FixedPaymentRepository.class);
 final FixedBudgetItemRepository items=mock(FixedBudgetItemRepository.class);
 final MonthlyBudgetRepository budgets=mock(MonthlyBudgetRepository.class);
 final UserRepository users=mock(UserRepository.class);
 final PaymentService service=new PaymentService(records,items,budgets,users);
 final YearMonth month=YearMonth.of(2026,11);
 PaymentInput input(long amount,long item) { return new PaymentInput(month.atDay(2),LocalTime.NOON,"  지급  ",amount,new ApiId(item)); }
 FixedBudgetItem ready() {
  User user=mock(User.class);when(user.getId()).thenReturn(1L);when(users.findWithLockingById(1L)).thenReturn(Optional.of(user));
  var budget=new MonthlyBudget(user,month.atDay(1),BudgetSetupStatus.READY,1L,1L,1L);ReflectionTestUtils.setField(budget,"id",10L);
  when(budgets.findByUser_IdAndMonthStart(1L,month.atDay(1))).thenReturn(Optional.of(budget));
  var item=new FixedBudgetItem(budget,null,"일반 고정비",100000L);ReflectionTestUtils.setField(item,"id",51L);when(items.findById(51L)).thenReturn(Optional.of(item));return item;
 }
 FixedPayment existing() {
  var row=new FixedPayment(ready(),month.atDay(2),LocalTime.NOON,"원본",60000L);ReflectionTestUtils.setField(row,"id",91L);when(records.findById(91L)).thenReturn(Optional.of(row));return row;
 }
 @Test void splitPaymentAtRemainingBudgetIsAcceptedAndOwnerLockComesFirst() {
  ready();when(records.sumAmountByItemId(51L)).thenReturn(60000L);
  when(records.saveAndFlush(any())).thenAnswer(call->{var row=call.getArgument(0);ReflectionTestUtils.setField(row,"id",91L);return row;});
  var result=service.create(1L,month,input(40000,51));assertThat(result.amount()).isEqualTo(40000);assertThat(result.title()).isEqualTo("지급");
  var order=inOrder(users,budgets,items,records);order.verify(users).findWithLockingById(1L);order.verify(budgets).findByUser_IdAndMonthStart(1L,month.atDay(1));order.verify(items).findById(51L);order.verify(records).sumAmountByItemId(51L);
 }
 @Test void overpaymentIsRejectedBeforeSaving() {
  ready();when(records.sumAmountByItemId(51L)).thenReturn(60000L);code(ErrorCode.PAYMENT_EXCEEDS_BUDGET,()->service.create(1L,month,input(40001,51)));verify(records,never()).saveAndFlush(any());
 }
 @Test void updateExcludesItsOwnExistingAmount() {
  var row=existing();when(records.sumAmountByItemId(51L)).thenReturn(60000L);
  assertThat(service.update(1L,month,91L,input(70000,51)).amount()).isEqualTo(70000);assertThat(row.getAmount()).isEqualTo(70000);
  var order=inOrder(users,records);order.verify(users).findWithLockingById(1L);order.verify(records).findById(91L);
 }
 @Test void movingToAnotherItemUsesOnlyTheTargetsAvailableBudget() {
  var row=existing();var target=new FixedBudgetItem(row.getFixedBudgetItem().getMonthlyBudget(),null,"대상",50000L);ReflectionTestUtils.setField(target,"id",52L);when(items.findById(52L)).thenReturn(Optional.of(target));when(records.sumAmountByItemId(52L)).thenReturn(20000L);
  code(ErrorCode.PAYMENT_EXCEEDS_BUDGET,()->service.update(1L,month,91L,input(30001,52)));
  assertThat(row.getFixedBudgetItem().getId()).isEqualTo(51);assertThat(row.getAmount()).isEqualTo(60000);
  assertThat(service.update(1L,month,91L,input(30000,52)).budgetItemId().value()).isEqualTo(52);
 }
 @Test void invalidInputAndUnreadyBudgetCannotCreate() {
  ready();for(long amount:new long[]{0,-1,10000000000L})code(ErrorCode.VALIDATION,()->service.create(1L,month,input(amount,51)));
  for(PaymentInput value:List.of(new PaymentInput(month.plusMonths(1).atDay(1),LocalTime.NOON,"x",1L,new ApiId(51L)),new PaymentInput(month.atDay(1),LocalTime.of(1,2,3),"x",1L,new ApiId(51L)),new PaymentInput(month.atDay(1),LocalTime.NOON," ",1L,new ApiId(51L)),new PaymentInput(month.atDay(1),LocalTime.NOON,"x",1L,null)))code(ErrorCode.VALIDATION,()->service.create(1L,month,value));
  when(budgets.findByUser_IdAndMonthStart(1L,month.atDay(1))).thenReturn(Optional.empty());code(ErrorCode.BUDGET_NOT_READY,()->service.create(1L,month,input(1,51)));verify(records,never()).saveAndFlush(any());
 }
 @Test void ownershipAndManagementMonthProtectReadsAndWrites() {
  existing();var other=mock(User.class);when(other.getId()).thenReturn(2L);when(users.findWithLockingById(2L)).thenReturn(Optional.of(other));
  code(ErrorCode.NOT_FOUND,()->service.get(2L,month,91L));code(ErrorCode.NOT_FOUND,()->service.update(2L,month,91L,input(1,51)));code(ErrorCode.NOT_FOUND,()->service.delete(1L,month.plusMonths(1),91L));verify(records,never()).delete(any());
 }
 @Test void deleteAcquiresTheSharedOwnerLockBeforeReadingThePayment() {
  var row=existing();service.delete(1L,month,91L);var order=inOrder(users,records);order.verify(users).findWithLockingById(1L);order.verify(records).findById(91L);order.verify(records).delete(row);
 }
 @Test void anUpdateStillIncludesOtherPaymentsAndPreservesTheOriginalOnFailure() {
  var row=existing();when(records.sumAmountByItemId(51L)).thenReturn(100000L);
  code(ErrorCode.PAYMENT_EXCEEDS_BUDGET,()->service.update(1L,month,91L,input(60001,51)));assertThat(row.getAmount()).isEqualTo(60000);
 }
 @Test void draftBudgetsAndItemsFromAnotherMonthCannotReceivePayments() {
  var item=ready();var budget=item.getMonthlyBudget();
  var wrongMonth=new FixedBudgetItem(new MonthlyBudget(budget.getUser(),month.minusMonths(1).atDay(1),BudgetSetupStatus.READY,1L,1L,1L),null,"다른 월",100000L);
  ReflectionTestUtils.setField(wrongMonth,"id",52L);when(items.findById(52L)).thenReturn(Optional.of(wrongMonth));
  code(ErrorCode.NOT_FOUND,()->service.create(1L,month,input(1,52)));
  when(budgets.findByUser_IdAndMonthStart(1L,month.atDay(1))).thenReturn(Optional.of(new MonthlyBudget(budget.getUser(),month.atDay(1),BudgetSetupStatus.DRAFT,null,null,null)));
  code(ErrorCode.BUDGET_NOT_READY,()->service.create(1L,month,input(1,51)));verify(records,never()).saveAndFlush(any());
 }
 static void code(ErrorCode expected,Runnable action) { assertThatThrownBy(action::run).isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(expected); }
}
