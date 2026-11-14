package com.danielhw99.cashflow.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import com.danielhw99.budget.entity.BudgetSetupStatus;
import com.danielhw99.budget.repository.MonthlyBudgetRepository;
import com.danielhw99.common.error.ApiException;
import com.danielhw99.common.error.ErrorCode;
import com.danielhw99.cashflow.api.CashFlowKindValue;
import com.danielhw99.cashflow.api.CashFlowList;
import com.danielhw99.cashflow.entity.MonthlyCashFlow;
import com.danielhw99.cashflow.repository.CashFlowQueries;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CashFlowReadService {
    private final MonthlyBudgetRepository budgets;
    private final CashFlowQueries queries;
    private final CashFlowCursor cursors;
    public CashFlowReadService(MonthlyBudgetRepository budgets, CashFlowQueries queries, CashFlowCursor cursors) {
        this.budgets = budgets; this.queries = queries; this.cursors = cursors;
    }
    @Transactional(readOnly = true)
    public CashFlowList list(Long owner, YearMonth month, LocalDate date, CashFlowKindValue kind, String cursor, int limit) {
        if (limit < 1 || limit > 100 || (date != null && !YearMonth.from(date).equals(month))) throw new ApiException(ErrorCode.VALIDATION);
        var position = cursors.decode(cursor,owner,month,date,kind);
        var budget = budgets.findByUser_IdAndMonthStart(owner,month.atDay(1)).orElseThrow(() -> new ApiException(ErrorCode.BUDGET_NOT_READY));
        if (budget.getSetupStatus() != BudgetSetupStatus.READY) throw new ApiException(ErrorCode.BUDGET_NOT_READY);
        List<MonthlyCashFlow> fetched = queries.page(budget.getId(),date,kind,position,limit);
        boolean more = fetched.size() > limit;
        List<MonthlyCashFlow> page = more ? fetched.subList(0,limit) : fetched;
        String next = null;
        if (more) {
            MonthlyCashFlow last = page.getLast();
            next = cursors.encode(owner,month,date,kind,new CashFlowCursor.Anchor(last.getOccurredOn(),last.getOccurredTime(),last.getId()));
        }
        long expense = safeTotal(queries.total(budget.getId(),date,kind,CashFlowKindValue.EXTRA_EXPENSE));
        long income = safeTotal(queries.total(budget.getId(),date,kind,CashFlowKindValue.INCOME));
        return new CashFlowList(page.stream().map(CashFlowService::response).toList(),next,expense,income,income-expense);
    }
    static long safeTotal(BigDecimal value) {
        if (value.signum() < 0 || value.compareTo(BigDecimal.valueOf(9_007_199_254_740_991L)) > 0) throw new ApiException(ErrorCode.AMOUNT_RANGE_EXCEEDED);
        try { return value.longValueExact(); } catch (ArithmeticException invalid) { throw new ApiException(ErrorCode.AMOUNT_RANGE_EXCEEDED); }
    }
}
