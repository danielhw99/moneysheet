package com.danielhw99.transaction.service;

import java.time.YearMonth;
import com.danielhw99.transaction.api.TransactionInput;
import com.danielhw99.transaction.api.TransactionResponse;
import com.danielhw99.transaction.api.TransactionDirectionValue;
import com.danielhw99.transaction.entity.MainTransaction;
import com.danielhw99.transaction.repository.MainTransactionRepository;
import com.danielhw99.budget.entity.MonthlyBudget;
import com.danielhw99.budget.entity.BudgetSetupStatus;
import com.danielhw99.budget.repository.MonthlyBudgetRepository;
import com.danielhw99.category.entity.Category;
import com.danielhw99.category.entity.CategoryScope;
import com.danielhw99.category.repository.CategoryRepository;
import com.danielhw99.common.api.ApiId;
import com.danielhw99.common.error.ApiException;
import com.danielhw99.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {
    private final MainTransactionRepository records;
    private final MonthlyBudgetRepository budgets;
    private final CategoryRepository categories;
    public TransactionService(MainTransactionRepository records, MonthlyBudgetRepository budgets, CategoryRepository categories) {
        this.records = records; this.budgets = budgets; this.categories = categories;
    }
    @Transactional
    public TransactionResponse create(Long owner, YearMonth month, TransactionInput input) {
        MonthlyBudget budget = readyBudget(owner, month);
        validate(month, input);
        return response(records.saveAndFlush(new MainTransaction(budget, input.date(), input.time(),
                input.title().strip(), input.amount(), input.direction().entityValue(), category(owner, input))));
    }
    @Transactional(readOnly = true)
    public TransactionResponse get(Long owner, YearMonth month, Long id) { return response(owned(owner, month, id)); }
    @Transactional
    public TransactionResponse update(Long owner, YearMonth month, Long id, TransactionInput input) {
        MainTransaction record = owned(owner, month, id);
        validate(month, input);
        record.update(input.date(), input.time(), input.title().strip(), input.amount(),
                input.direction().entityValue(), category(owner, input));
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
    private MainTransaction owned(Long owner, YearMonth month, Long id) {
        if (id == null || id < 1) throw new ApiException(ErrorCode.VALIDATION);
        MainTransaction record = records.findById(id)
                .filter(row -> row.getMonthlyBudget().getUser().getId().equals(owner)
                        && row.getMonthlyBudget().getMonthStart().equals(month.atDay(1)))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        requireReady(record.getMonthlyBudget());
        return record;
    }
    private void requireReady(MonthlyBudget budget) {
        if (budget.getSetupStatus() != BudgetSetupStatus.READY) throw new ApiException(ErrorCode.BUDGET_NOT_READY);
    }
    private void validate(YearMonth month, TransactionInput input) {
        if (input == null || input.date() == null || !YearMonth.from(input.date()).equals(month)
                || input.time() == null || input.time().getSecond() != 0 || input.time().getNano() != 0
                || input.title() == null || input.title().strip().isEmpty()
                || input.title().strip().codePointCount(0, input.title().strip().length()) > 255
                || input.amount() == null || input.amount() < 1 || input.amount() > 9_999_999_999L
                || input.direction() == null) throw new ApiException(ErrorCode.VALIDATION);
    }
    private Category category(Long owner, TransactionInput input) {
        if (input.categoryId() == null) return null;
        Category category = categories.findById(input.categoryId().value())
                .filter(value -> value.getUser().getId().equals(owner))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (category.getScope() != CategoryScope.MAIN_TRANSACTION
                || !category.getDirection().name().equals(input.direction().name())) throw new ApiException(ErrorCode.VALIDATION);
        return category;
    }
    public static TransactionResponse response(MainTransaction record) {
        return new TransactionResponse(new ApiId(record.getId()), record.getOccurredOn(), record.getOccurredTime(),
                record.getTitle(), record.getAmount(), TransactionDirectionValue.fromEntity(record.getDirection()),
                record.getCategory() == null ? null : new ApiId(record.getCategory().getId()));
    }
}
