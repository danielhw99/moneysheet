package com.danielhw99.transaction.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import com.danielhw99.budget.entity.BudgetSetupStatus;
import com.danielhw99.budget.repository.MonthlyBudgetRepository;
import com.danielhw99.common.error.ApiException;
import com.danielhw99.common.error.ErrorCode;
import com.danielhw99.transaction.api.TransactionDirectionValue;
import com.danielhw99.transaction.api.TransactionList;
import com.danielhw99.transaction.entity.MainTransaction;
import com.danielhw99.transaction.repository.TransactionQueries;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionReadService {
    private final MonthlyBudgetRepository budgets;
    private final TransactionQueries queries;
    private final TransactionCursor cursors;
    public TransactionReadService(MonthlyBudgetRepository budgets, TransactionQueries queries, TransactionCursor cursors) {
        this.budgets = budgets; this.queries = queries; this.cursors = cursors;
    }
    @Transactional(readOnly = true)
    public TransactionList list(Long owner, YearMonth month, LocalDate date, TransactionDirectionValue direction, String cursor, int limit) {
        if (limit < 1 || limit > 100 || (date != null && !YearMonth.from(date).equals(month))) throw new ApiException(ErrorCode.VALIDATION);
        var position = cursors.decode(cursor,owner,month,date,direction);
        var budget = budgets.findByUser_IdAndMonthStart(owner,month.atDay(1)).orElseThrow(() -> new ApiException(ErrorCode.BUDGET_NOT_READY));
        if (budget.getSetupStatus() != BudgetSetupStatus.READY) throw new ApiException(ErrorCode.BUDGET_NOT_READY);
        List<MainTransaction> fetched = queries.page(budget.getId(),date,direction,position,limit);
        boolean more = fetched.size() > limit;
        List<MainTransaction> page = more ? fetched.subList(0,limit) : fetched;
        String next = null;
        if (more) {
            MainTransaction last = page.getLast();
            next = cursors.encode(owner,month,date,direction,new TransactionCursor.Anchor(last.getOccurredOn(),last.getOccurredTime(),last.getId()));
        }
        long expense = safeTotal(queries.total(budget.getId(),date,direction,TransactionDirectionValue.EXPENSE));
        long income = safeTotal(queries.total(budget.getId(),date,direction,TransactionDirectionValue.INCOME));
        return new TransactionList(page.stream().map(TransactionService::response).toList(),next,expense,income,income-expense);
    }
    static long safeTotal(BigDecimal value) {
        if (value.signum() < 0 || value.compareTo(BigDecimal.valueOf(9_007_199_254_740_991L)) > 0) throw new ApiException(ErrorCode.AMOUNT_RANGE_EXCEEDED);
        try { return value.longValueExact(); } catch (ArithmeticException invalid) { throw new ApiException(ErrorCode.AMOUNT_RANGE_EXCEEDED); }
    }
}
