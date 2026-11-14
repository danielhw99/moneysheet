package com.danielhw99.payment.service;

import java.time.YearMonth;
import com.danielhw99.payment.api.PaymentInput;
import com.danielhw99.payment.api.PaymentResponse;
import com.danielhw99.payment.entity.FixedPayment;
import com.danielhw99.payment.repository.FixedPaymentRepository;
import com.danielhw99.budget.entity.FixedBudgetItem;
import com.danielhw99.budget.entity.MonthlyBudget;
import com.danielhw99.budget.entity.BudgetSetupStatus;
import com.danielhw99.budget.repository.FixedBudgetItemRepository;
import com.danielhw99.budget.repository.MonthlyBudgetRepository;
import com.danielhw99.user.repository.UserRepository;
import com.danielhw99.common.api.ApiId;
import com.danielhw99.common.error.ApiException;
import com.danielhw99.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {
    private final FixedPaymentRepository records;
    private final FixedBudgetItemRepository items;
    private final MonthlyBudgetRepository budgets;
    private final UserRepository users;
    public PaymentService(FixedPaymentRepository records, FixedBudgetItemRepository items,
                          MonthlyBudgetRepository budgets, UserRepository users) {
        this.records = records; this.items = items; this.budgets = budgets; this.users = users;
    }

    @Transactional
    public PaymentResponse create(Long owner, YearMonth month, PaymentInput input) {
        lockOwner(owner);
        MonthlyBudget budget = budgets.findByUser_IdAndMonthStart(owner, month.atDay(1))
                .orElseThrow(() -> new ApiException(ErrorCode.BUDGET_NOT_READY));
        requireReady(budget);
        validate(month, input);
        FixedBudgetItem target = target(owner, month, input.budgetItemId().value());
        requireCapacity(target, input.amount(), null);
        return response(records.saveAndFlush(new FixedPayment(target, input.date(), input.time(), input.title().strip(), input.amount())));
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(Long owner, YearMonth month, Long id) { return response(owned(owner, month, id)); }

    @Transactional
    public PaymentResponse update(Long owner, YearMonth month, Long id, PaymentInput input) {
        lockOwner(owner);
        FixedPayment record = owned(owner, month, id);
        validate(month, input);
        FixedBudgetItem target = target(owner, month, input.budgetItemId().value());
        requireCapacity(target, input.amount(), record);
        record.update(target, input.date(), input.time(), input.title().strip(), input.amount());
        return response(record);
    }

    @Transactional
    public void delete(Long owner, YearMonth month, Long id) {
        lockOwner(owner);
        records.delete(owned(owner, month, id));
    }

    private void lockOwner(Long owner) {
        // BudgetService takes this same lock before reads/updates/deletes of a budget item.
        // Reading payment rows only afterwards avoids stale pre-lock snapshots.
        users.findWithLockingById(owner).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
    }

    private FixedBudgetItem target(Long owner, YearMonth month, Long id) {
        FixedBudgetItem item = items.findById(id).filter(row -> row.getMonthlyBudget().getUser().getId().equals(owner)
                        && row.getMonthlyBudget().getMonthStart().equals(month.atDay(1)))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        requireReady(item.getMonthlyBudget());
        return item;
    }

    private FixedPayment owned(Long owner, YearMonth month, Long id) {
        if (id == null || id < 1) throw new ApiException(ErrorCode.VALIDATION);
        FixedPayment record = records.findById(id).filter(row -> row.getFixedBudgetItem().getMonthlyBudget().getUser().getId().equals(owner)
                        && row.getFixedBudgetItem().getMonthlyBudget().getMonthStart().equals(month.atDay(1)))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        requireReady(record.getFixedBudgetItem().getMonthlyBudget());
        return record;
    }

    private void requireReady(MonthlyBudget budget) {
        if (budget.getSetupStatus() != BudgetSetupStatus.READY) throw new ApiException(ErrorCode.BUDGET_NOT_READY);
    }

    private void requireCapacity(FixedBudgetItem target, long amount, FixedPayment previous) {
        long paid = records.sumAmountByItemId(target.getId());
        if (previous != null && previous.getFixedBudgetItem().getId().equals(target.getId())) paid -= previous.getAmount();
        if (paid < 0) throw new ApiException(ErrorCode.AMOUNT_RANGE_EXCEEDED);
        if (paid > target.getAmount() || amount > target.getAmount() - paid)
            throw new ApiException(ErrorCode.PAYMENT_EXCEEDS_BUDGET);
    }

    private void validate(YearMonth month, PaymentInput input) {
        if (input == null || input.date() == null || !YearMonth.from(input.date()).equals(month)
                || input.time() == null || input.time().getSecond() != 0 || input.time().getNano() != 0
                || input.title() == null || input.title().strip().isEmpty()
                || input.title().strip().codePointCount(0, input.title().strip().length()) > 255
                || input.amount() == null || input.amount() < 1 || input.amount() > 9_999_999_999L
                || input.budgetItemId() == null) throw new ApiException(ErrorCode.VALIDATION);
    }

    public static PaymentResponse response(FixedPayment record) {
        return new PaymentResponse(new ApiId(record.getId()), record.getOccurredOn(), record.getOccurredTime(),
                record.getTitle(), record.getAmount(), new ApiId(record.getFixedBudgetItem().getId()));
    }
}
