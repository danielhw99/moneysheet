package com.danielhw99.memo.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import com.danielhw99.memo.api.MemoStatus;
import com.danielhw99.memo.api.MemoList;
import com.danielhw99.memo.api.MemoDirectionValue;
import com.danielhw99.memo.entity.MoneyMemo;
import com.danielhw99.memo.repository.MemoQueries;
import com.danielhw99.budget.repository.MonthlyBudgetRepository;
import com.danielhw99.budget.entity.BudgetSetupStatus;
import com.danielhw99.common.error.ApiException;
import com.danielhw99.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemoReadService {
    private final MonthlyBudgetRepository budgets;
    private final MemoQueries queries;
    private final MemoCursor cursors;
    public MemoReadService(MonthlyBudgetRepository budgets, MemoQueries queries, MemoCursor cursors) {
        this.budgets = budgets;
        this.queries = queries;
        this.cursors = cursors;
    }

    @Transactional(readOnly = true)
    public MemoList list(Long owner, YearMonth month, LocalDate date, MemoStatus status, String cursor, int limit) {
        if (date == null || status == null || !YearMonth.from(date).equals(month) || limit < 1 || limit > 100)
            throw new ApiException(ErrorCode.VALIDATION);
        var position = cursors.decode(cursor, owner, month, date, status);
        var budget = budgets.findByUser_IdAndMonthStart(owner, month.atDay(1))
                .orElseThrow(() -> new ApiException(ErrorCode.BUDGET_NOT_READY));
        if (budget.getSetupStatus() != BudgetSetupStatus.READY) throw new ApiException(ErrorCode.BUDGET_NOT_READY);
        List<MoneyMemo> fetched = queries.page(budget.getId(), date, status, position, limit);
        boolean more = fetched.size() > limit;
        List<MoneyMemo> page = more ? fetched.subList(0, limit) : fetched;
        String next = null;
        if (more) {
            MoneyMemo last = page.getLast();
            next = cursors.encode(owner, month, date, status, new MemoCursor.Anchor(last.getOccurredOn(), last.getOccurredTime(), last.getId()));
        }
        long expense = safeTotal(queries.total(budget.getId(), date, status, MemoDirectionValue.EXPENSE));
        long income = safeTotal(queries.total(budget.getId(), date, status, MemoDirectionValue.INCOME));
        // Pending adjustment is independent of both the page cursor and chosen status.
        long pendingExpense = status == MemoStatus.PENDING ? expense : safeTotal(queries.total(budget.getId(), date, MemoStatus.PENDING, MemoDirectionValue.EXPENSE));
        long pendingIncome = status == MemoStatus.PENDING ? income : safeTotal(queries.total(budget.getId(), date, MemoStatus.PENDING, MemoDirectionValue.INCOME));
        return new MemoList(page.stream().map(MemoService::response).toList(), next, expense, income, income - expense, pendingIncome - pendingExpense);
    }

    private long safeTotal(BigDecimal value) {
        if (value.signum() < 0 || value.compareTo(BigDecimal.valueOf(9_007_199_254_740_991L)) > 0)
            throw new ApiException(ErrorCode.AMOUNT_RANGE_EXCEEDED);
        try { return value.longValueExact(); }
        catch (ArithmeticException invalid) { throw new ApiException(ErrorCode.AMOUNT_RANGE_EXCEEDED); }
    }
}
