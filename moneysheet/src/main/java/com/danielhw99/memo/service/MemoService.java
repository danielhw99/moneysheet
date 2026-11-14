package com.danielhw99.memo.service;

import java.time.LocalDate;
import java.time.YearMonth;
import com.danielhw99.memo.api.MemoInput;
import com.danielhw99.memo.api.MemoResponse;
import com.danielhw99.memo.api.MemoDirectionValue;
import com.danielhw99.memo.entity.MoneyMemo;
import com.danielhw99.memo.repository.MoneyMemoRepository;
import com.danielhw99.budget.entity.MonthlyBudget;
import com.danielhw99.budget.entity.BudgetSetupStatus;
import com.danielhw99.budget.repository.MonthlyBudgetRepository;
import com.danielhw99.common.api.ApiId;
import com.danielhw99.common.error.ApiException;
import com.danielhw99.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemoService {
    private final MoneyMemoRepository records;
    private final MonthlyBudgetRepository budgets;
    public MemoService(MoneyMemoRepository records, MonthlyBudgetRepository budgets) {
        this.records = records;
        this.budgets = budgets;
    }

    @Transactional
    public MemoResponse create(Long owner, YearMonth month, MemoInput input) {
        MonthlyBudget budget = budgets.findByUser_IdAndMonthStart(owner, month.atDay(1))
                .orElseThrow(() -> new ApiException(ErrorCode.BUDGET_NOT_READY));
        requireReady(budget);
        validate(month, input);
        return response(records.saveAndFlush(new MoneyMemo(budget, input.date(), input.time(),
                input.title().strip(), input.amount(), input.direction().entityValue(), null)));
    }

    @Transactional(readOnly = true)
    public MemoResponse get(Long owner, YearMonth month, Long id) {
        return response(owned(owner, month, id, false));
    }

    @Transactional
    public MemoResponse update(Long owner, YearMonth month, Long id, MemoInput input) {
        MoneyMemo record = owned(owner, month, id, true);
        validate(month, input);
        validateCompletion(month, input.date(), record.getCompletedOn());
        record.update(input.date(), input.time(), input.title().strip(), input.amount(), input.direction().entityValue());
        return response(record);
    }

    @Transactional
    public void delete(Long owner, YearMonth month, Long id) {
        records.delete(owned(owner, month, id, true));
    }

    private MoneyMemo owned(Long owner, YearMonth month, Long id, boolean write) {
        if (id == null || id < 1) throw new ApiException(ErrorCode.VALIDATION);
        var found = write ? records.findLockedById(id) : records.findById(id);
        MoneyMemo record = found.filter(row -> row.getMonthlyBudget().getUser().getId().equals(owner)
                        && row.getMonthlyBudget().getMonthStart().equals(month.atDay(1)))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        requireReady(record.getMonthlyBudget());
        return record;
    }

    private void requireReady(MonthlyBudget budget) {
        if (budget.getSetupStatus() != BudgetSetupStatus.READY) throw new ApiException(ErrorCode.BUDGET_NOT_READY);
    }

    private void validate(YearMonth month, MemoInput input) {
        if (input == null || input.date() == null || !YearMonth.from(input.date()).equals(month)
                || input.time() == null || input.time().getSecond() != 0 || input.time().getNano() != 0
                || input.title() == null || input.title().strip().isEmpty()
                || input.title().strip().codePointCount(0, input.title().strip().length()) > 255
                || input.amount() == null || input.amount() < 1 || input.amount() > 9_999_999_999L
                || input.direction() == null) throw new ApiException(ErrorCode.VALIDATION);
    }

    private void validateCompletion(YearMonth month, LocalDate occurredOn, LocalDate completedOn) {
        if (completedOn != null && (completedOn.isBefore(occurredOn) || !YearMonth.from(completedOn).equals(month)))
            throw new ApiException(ErrorCode.VALIDATION);
    }

    public static MemoResponse response(MoneyMemo record) {
        return new MemoResponse(new ApiId(record.getId()), record.getOccurredOn(), record.getOccurredTime(),
                record.getTitle(), record.getAmount(), MemoDirectionValue.fromEntity(record.getDirection()), record.getCompletedOn());
    }
}
