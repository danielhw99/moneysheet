package com.danielhw99.cashflow.service;

import java.time.YearMonth;
import com.danielhw99.cashflow.api.CashFlowInput;
import com.danielhw99.cashflow.api.CashFlowResponse;
import com.danielhw99.cashflow.api.CashFlowKindValue;
import com.danielhw99.cashflow.entity.MonthlyCashFlow;
import com.danielhw99.cashflow.repository.MonthlyCashFlowRepository;
import com.danielhw99.budget.entity.MonthlyBudget;
import com.danielhw99.budget.entity.BudgetSetupStatus;
import com.danielhw99.budget.repository.MonthlyBudgetRepository;
import com.danielhw99.common.api.ApiId;
import com.danielhw99.common.error.ApiException;
import com.danielhw99.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CashFlowService {
    private final MonthlyCashFlowRepository records;
    private final MonthlyBudgetRepository budgets;
    public CashFlowService(MonthlyCashFlowRepository records, MonthlyBudgetRepository budgets) {
        this.records = records; this.budgets = budgets;
    }
    @Transactional
    public CashFlowResponse create(Long owner, YearMonth month, CashFlowInput input) {
        MonthlyBudget budget = readyBudget(owner, month);
        validate(month, input);
        return response(records.saveAndFlush(new MonthlyCashFlow(budget, input.date(), input.time(),
                input.title().strip(), input.amount(), input.kind().entityValue())));
    }
    @Transactional(readOnly = true)
    public CashFlowResponse get(Long owner, YearMonth month, Long id) { return response(owned(owner, month, id)); }
    @Transactional
    public CashFlowResponse update(Long owner, YearMonth month, Long id, CashFlowInput input) {
        MonthlyCashFlow record = owned(owner, month, id);
        validate(month, input);
        if (record.getKind() != input.kind().entityValue()) throw new ApiException(ErrorCode.VALIDATION);
        record.update(input.date(), input.time(), input.title().strip(), input.amount());
        return response(record);
    }
    @Transactional
    public void delete(Long owner, YearMonth month, Long id) { records.delete(owned(owner, month, id)); }
    private MonthlyBudget readyBudget(Long owner, YearMonth month) {
        MonthlyBudget budget = budgets.findByUser_IdAndMonthStart(owner, month.atDay(1))
                .orElseThrow(() -> new ApiException(ErrorCode.BUDGET_NOT_READY));
        requireReady(budget);
        return budget;
    }
    private MonthlyCashFlow owned(Long owner, YearMonth month, Long id) {
        if (id == null || id < 1) throw new ApiException(ErrorCode.VALIDATION);
        MonthlyCashFlow record = records.findById(id)
                .filter(row -> row.getMonthlyBudget().getUser().getId().equals(owner)
                        && row.getMonthlyBudget().getMonthStart().equals(month.atDay(1)))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        requireReady(record.getMonthlyBudget());
        return record;
    }
    private void requireReady(MonthlyBudget budget) {
        if (budget.getSetupStatus() != BudgetSetupStatus.READY) throw new ApiException(ErrorCode.BUDGET_NOT_READY);
    }
    private void validate(YearMonth month, CashFlowInput input) {
        if (input == null || input.date() == null || !YearMonth.from(input.date()).equals(month)
                || input.time() == null || input.time().getSecond() != 0 || input.time().getNano() != 0
                || input.title() == null || input.title().strip().isEmpty()
                || input.title().strip().codePointCount(0, input.title().strip().length()) > 255
                || input.amount() == null || input.amount() < 1 || input.amount() > 9_999_999_999L
                || input.kind() == null) throw new ApiException(ErrorCode.VALIDATION);
    }
    public static CashFlowResponse response(MonthlyCashFlow record) {
        return new CashFlowResponse(new ApiId(record.getId()), record.getOccurredOn(), record.getOccurredTime(),
                record.getTitle(), record.getAmount(), CashFlowKindValue.fromEntity(record.getKind()));
    }
}
