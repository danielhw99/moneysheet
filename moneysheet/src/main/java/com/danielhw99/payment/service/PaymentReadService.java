package com.danielhw99.payment.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import com.danielhw99.payment.api.PaymentList;
import com.danielhw99.payment.entity.FixedPayment;
import com.danielhw99.payment.repository.PaymentQueries;
import com.danielhw99.budget.entity.BudgetSetupStatus;
import com.danielhw99.budget.repository.MonthlyBudgetRepository;
import com.danielhw99.budget.repository.FixedBudgetItemRepository;
import com.danielhw99.common.error.ApiException;
import com.danielhw99.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentReadService {
    private final MonthlyBudgetRepository budgets;
    private final FixedBudgetItemRepository items;
    private final PaymentQueries queries;
    private final PaymentCursor cursors;
    public PaymentReadService(MonthlyBudgetRepository budgets, FixedBudgetItemRepository items,
                              PaymentQueries queries, PaymentCursor cursors) {
        this.budgets = budgets; this.items = items; this.queries = queries; this.cursors = cursors;
    }
    @Transactional(readOnly = true)
    public PaymentList list(Long owner, YearMonth month, LocalDate date, Long item, String cursor, int limit) {
        if (limit < 1 || limit > 100 || (date != null && !YearMonth.from(date).equals(month)) || (item != null && item < 1))
            throw new ApiException(ErrorCode.VALIDATION);
        var position = cursors.decode(cursor, owner, month, date, item);
        var budget = budgets.findByUser_IdAndMonthStart(owner, month.atDay(1))
                .orElseThrow(() -> new ApiException(ErrorCode.BUDGET_NOT_READY));
        if (budget.getSetupStatus() != BudgetSetupStatus.READY) throw new ApiException(ErrorCode.BUDGET_NOT_READY);
        if (item != null) items.findById(item).filter(row -> row.getMonthlyBudget().getId().equals(budget.getId()))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        List<FixedPayment> fetched = queries.page(budget.getId(), date, item, position, limit);
        boolean more = fetched.size() > limit;
        List<FixedPayment> page = more ? fetched.subList(0, limit) : fetched;
        String next = null;
        if (more) {
            var last = page.getLast();
            next = cursors.encode(owner, month, date, item, new PaymentCursor.Anchor(last.getOccurredOn(), last.getOccurredTime(), last.getId()));
        }
        long expense = safeTotal(queries.total(budget.getId(), date, item));
        // Payments are expenses only; this API's total is the positive paid amount.
        return new PaymentList(page.stream().map(PaymentService::response).toList(), next, expense, 0, expense);
    }
    private long safeTotal(BigDecimal value) {
        if (value.signum() < 0 || value.compareTo(BigDecimal.valueOf(9_007_199_254_740_991L)) > 0)
            throw new ApiException(ErrorCode.AMOUNT_RANGE_EXCEEDED);
        try { return value.longValueExact(); }
        catch (ArithmeticException invalid) { throw new ApiException(ErrorCode.AMOUNT_RANGE_EXCEEDED); }
    }
}
