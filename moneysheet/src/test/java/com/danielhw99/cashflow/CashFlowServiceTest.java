package com.danielhw99.cashflow;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.Optional;
import com.danielhw99.cashflow.api.CashFlowInput;
import com.danielhw99.cashflow.api.CashFlowKindValue;
import com.danielhw99.cashflow.entity.CashFlowKind;
import com.danielhw99.cashflow.entity.MonthlyCashFlow;
import com.danielhw99.cashflow.repository.MonthlyCashFlowRepository;
import com.danielhw99.cashflow.service.CashFlowService;
import com.danielhw99.budget.entity.MonthlyBudget;
import com.danielhw99.budget.entity.BudgetSetupStatus;
import com.danielhw99.budget.repository.MonthlyBudgetRepository;
import com.danielhw99.user.entity.User;
import com.danielhw99.common.error.ApiException;
import com.danielhw99.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CashFlowServiceTest {
    final MonthlyCashFlowRepository records=mock(MonthlyCashFlowRepository.class);
    final MonthlyBudgetRepository budgets=mock(MonthlyBudgetRepository.class);
    final CashFlowService service=new CashFlowService(records,budgets);
    final YearMonth month=YearMonth.of(2026,11);
    CashFlowInput input(long amount,CashFlowKindValue kind) { return new CashFlowInput(month.atDay(2),LocalTime.NOON,"  Salary  ",amount,kind); }
    MonthlyBudget budget(BudgetSetupStatus status) {
        User user=mock(User.class);when(user.getId()).thenReturn(1L);
        var result=new MonthlyBudget(user,month.atDay(1),status,1L,1L,1L);
        when(budgets.findByUser_IdAndMonthStart(1L,month.atDay(1))).thenReturn(Optional.of(result));return result;
    }
    MonthlyCashFlow existing(BudgetSetupStatus status) {
        var result=new MonthlyCashFlow(budget(status),month.atDay(2),LocalTime.NOON,"Original",100L,CashFlowKind.INCOME);
        ReflectionTestUtils.setField(result,"id",91L);when(records.findById(91L)).thenReturn(Optional.of(result));return result;
    }
    void code(ErrorCode expected,Runnable action) { assertThatThrownBy(action::run).isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(expected); }
    @Test void createsBothKindsWithPositiveAmountAndStripsTitle() {
        budget(BudgetSetupStatus.READY);
        when(records.saveAndFlush(any())).thenAnswer(call->{var row=call.getArgument(0);ReflectionTestUtils.setField(row,"id",91L);return row;});
        for(var kind:CashFlowKindValue.values()) {
            var result=service.create(1L,month,input(9_999_999_999L,kind));
            assertThat(result.id().value()).isEqualTo(91);assertThat(result.kind()).isEqualTo(kind);
            assertThat(result.amount()).isEqualTo(9_999_999_999L);assertThat(result.title()).isEqualTo("Salary");
        }
    }
    @Test void requiresExistingReadyBudgetBeforeCreate() {
        code(ErrorCode.BUDGET_NOT_READY,()->service.create(1L,month,input(1,CashFlowKindValue.INCOME)));
        budget(BudgetSetupStatus.DRAFT);code(ErrorCode.BUDGET_NOT_READY,()->service.create(1L,month,input(1,CashFlowKindValue.INCOME)));
        verifyNoInteractions(records);
    }
    @Test void invalidInputNeverSaves() {
        budget(BudgetSetupStatus.READY);
        for(var input:Arrays.asList(null,new CashFlowInput(null,LocalTime.NOON,"x",1L,CashFlowKindValue.INCOME),
                new CashFlowInput(month.plusMonths(1).atDay(1),LocalTime.NOON,"x",1L,CashFlowKindValue.INCOME),
                new CashFlowInput(month.atDay(1),null,"x",1L,CashFlowKindValue.INCOME),
                new CashFlowInput(month.atDay(1),LocalTime.of(1,2,3),"x",1L,CashFlowKindValue.INCOME),
                new CashFlowInput(month.atDay(1),LocalTime.NOON,null,1L,CashFlowKindValue.INCOME),
                new CashFlowInput(month.atDay(1),LocalTime.NOON,"  ",1L,CashFlowKindValue.INCOME),
                new CashFlowInput(month.atDay(1),LocalTime.NOON,"x".repeat(256),1L,CashFlowKindValue.INCOME),
                new CashFlowInput(month.atDay(1),LocalTime.NOON,"x",null,CashFlowKindValue.INCOME),
                input(0,CashFlowKindValue.INCOME),input(-1,CashFlowKindValue.INCOME),input(10_000_000_000L,CashFlowKindValue.INCOME),input(1,null))) {
            code(ErrorCode.VALIDATION,()->service.create(1L,month,input));
        }
        verify(records,never()).saveAndFlush(any());
    }
    @Test void sameKindEditKeepsIdentityAndUpdatesAllEditableFields() {
        var row=existing(BudgetSetupStatus.READY);
        var result=service.update(1L,month,91L,new CashFlowInput(month.atDay(3),LocalTime.of(9,15)," New ",250L,CashFlowKindValue.INCOME));
        assertThat(result.id().value()).isEqualTo(91);assertThat(row.getTitle()).isEqualTo("New");
        assertThat(row.getAmount()).isEqualTo(250);assertThat(row.getOccurredOn()).isEqualTo(month.atDay(3));
    }
    @Test void kindChangeIsRejectedWithoutMutatingTheOriginal() {
        var row=existing(BudgetSetupStatus.READY);
        code(ErrorCode.VALIDATION,()->service.update(1L,month,91L,input(200,CashFlowKindValue.EXTRA_EXPENSE)));
        assertThat(row.getKind()).isEqualTo(CashFlowKind.INCOME);assertThat(row.getAmount()).isEqualTo(100);assertThat(row.getTitle()).isEqualTo("Original");
    }
    @Test void protectsOwnerAndManagementMonthForReadUpdateDelete() {
        existing(BudgetSetupStatus.READY);
        code(ErrorCode.NOT_FOUND,()->service.get(2L,month,91L));
        code(ErrorCode.NOT_FOUND,()->service.update(2L,month,91L,input(1,CashFlowKindValue.INCOME)));
        code(ErrorCode.NOT_FOUND,()->service.delete(2L,month,91L));
        code(ErrorCode.NOT_FOUND,()->service.get(1L,month.plusMonths(1),91L));
        code(ErrorCode.NOT_FOUND,()->service.delete(1L,month.plusMonths(1),91L));
        verify(records,never()).delete(any());
    }
    @Test void draftRecordsCannotBeReadOrChanged() {
        existing(BudgetSetupStatus.DRAFT);
        code(ErrorCode.BUDGET_NOT_READY,()->service.get(1L,month,91L));
        code(ErrorCode.BUDGET_NOT_READY,()->service.update(1L,month,91L,input(1,CashFlowKindValue.INCOME)));
        code(ErrorCode.BUDGET_NOT_READY,()->service.delete(1L,month,91L));
    }
    @Test void deletesOwnedReadyRecordAndValidatesIdentifier() {
        var row=existing(BudgetSetupStatus.READY);service.delete(1L,month,91L);verify(records).delete(row);
        code(ErrorCode.VALIDATION,()->service.get(1L,month,0L));code(ErrorCode.VALIDATION,()->service.get(1L,month,null));
        code(ErrorCode.NOT_FOUND,()->service.get(1L,month,92L));
    }
}
