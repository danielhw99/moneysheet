package com.danielhw99.budget.service;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.danielhw99.budget.api.BudgetDtos;
import com.danielhw99.budget.api.BudgetDtos.DraftItem;
import com.danielhw99.budget.api.BudgetDtos.Food;
import com.danielhw99.budget.api.BudgetDtos.Kind;
import com.danielhw99.budget.api.BudgetDtos.Preparation;
import com.danielhw99.budget.api.BudgetDtos.SetupInput;
import com.danielhw99.budget.api.BudgetDtos.Summary;
import com.danielhw99.budget.api.BudgetDtos.ItemSummary;
import com.danielhw99.budget.api.BudgetDtos.FoodSummary;
import com.danielhw99.budget.api.BudgetDtos.Item;
import com.danielhw99.budget.api.BudgetDtos.ItemInput;
import com.danielhw99.budget.entity.BudgetSetupStatus;
import com.danielhw99.budget.entity.FixedBudgetItem;
import com.danielhw99.budget.entity.FixedBudgetKind;
import com.danielhw99.budget.entity.MonthlyBudget;
import com.danielhw99.budget.repository.FixedBudgetItemRepository;
import com.danielhw99.budget.repository.MonthlyBudgetRepository;
import com.danielhw99.category.repository.CategoryRepository;
import com.danielhw99.category.entity.Category;
import com.danielhw99.category.entity.CategoryScope;
import com.danielhw99.common.api.ApiId;
import com.danielhw99.common.api.ApiFormats;
import com.danielhw99.common.error.ApiException;
import com.danielhw99.common.error.ErrorCode;
import com.danielhw99.payment.repository.FixedPaymentRepository;
import com.danielhw99.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BudgetService {
    private final MonthlyBudgetRepository budgets;
    private final FixedBudgetItemRepository items;
    private final CategoryRepository categories;
    private final FixedPaymentRepository payments;
    private final UserRepository users;
    private final BudgetCalendar calendar;

    public BudgetService(MonthlyBudgetRepository budgets, FixedBudgetItemRepository items,
                         CategoryRepository categories, FixedPaymentRepository payments,
                         UserRepository users, BudgetCalendar calendar) {
        this.budgets = budgets;
        this.items = items;
        this.categories = categories;
        this.payments = payments;
        this.users = users;
        this.calendar = calendar;
    }

    @Transactional
    public Preparation prepare(Long userId, YearMonth month) {
        calendar.requireWritable(month);
        var owner = users.findWithLockingById(userId).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
        calendar.requireWritable(month);
        var existing = budgets.findByUser_IdAndMonthStart(userId, month.atDay(1));
        var previous = budgets.findByUser_IdAndMonthStart(userId, month.minusMonths(1).atDay(1));
        if (existing.isPresent()) return preparation(existing.get(), month, null, previous.isEmpty());
        var budget = budgets.saveAndFlush(new MonthlyBudget(owner, month.atDay(1), BudgetSetupStatus.DRAFT,
                previous.map(MonthlyBudget::getFoodWeekday).orElse(null),
                previous.map(MonthlyBudget::getFoodSaturday).orElse(null),
                previous.map(MonthlyBudget::getFoodSunday).orElse(null)));
        if (previous.isPresent()) {
            for (var item : items.findByMonthlyBudget_IdOrderByIdAsc(previous.get().getId())) {
                items.save(new FixedBudgetItem(budget, item.getCategory(), item.getKind(), item.getName(), item.getAmount()));
            }
            items.flush();
        }
        return preparation(budget, month, previous.isPresent() ? ApiFormats.formatMonth(month.minusMonths(1)) : null,
                previous.isEmpty());
    }

    private Preparation preparation(MonthlyBudget budget, YearMonth month, String source, boolean firstMonth) {
        List<DraftItem> draftItems = new ArrayList<>();
        for (var item : items.findByMonthlyBudget_IdOrderByIdAsc(budget.getId())) {
            draftItems.add(new DraftItem(new ApiId(item.getId()), kind(item.getKind()), item.getName(),
                    new ApiId(item.getCategory().getId()), item.getAmount()));
        }
        if (firstMonth && draftItems.isEmpty() && budget.getFoodWeekday() == null) {
            draftItems.add(new DraftItem(null, Kind.FREE_ALLOWANCE, "고정여유비", null, null));
        }
        return new Preparation(ApiFormats.formatMonth(month), budget.getSetupStatus() == BudgetSetupStatus.READY ? "ready" : "draft",
                source, draftItems, new Food(budget.getFoodWeekday(), budget.getFoodSaturday(), budget.getFoodSunday()));
    }

    private Kind kind(FixedBudgetKind value) {
        return value == FixedBudgetKind.FREE_ALLOWANCE ? Kind.FREE_ALLOWANCE : Kind.FIXED_EXPENSE;
    }

    @Transactional(readOnly = true)
    public Summary get(Long userId, YearMonth month) {
        MonthlyBudget budget = budgets.findByUser_IdAndMonthStart(userId, month.atDay(1))
                .orElseThrow(() -> new ApiException(ErrorCode.BUDGET_NOT_READY));
        requireReady(budget);
        return summary(budget, month);
    }

    @Transactional
    public Summary setup(Long userId, YearMonth month, SetupInput input) {
        calendar.requireWritable(month);
        users.findWithLockingById(userId).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
        calendar.requireWritable(month);
        MonthlyBudget budget = budgets.findByUser_IdAndMonthStart(userId, month.atDay(1))
                .orElseThrow(() -> new ApiException(ErrorCode.BUDGET_NOT_READY));
        if (budget.getSetupStatus() != BudgetSetupStatus.DRAFT) throw new ApiException(ErrorCode.CONFLICT);
        if (input == null || input.items() == null) throw new ApiException(ErrorCode.VALIDATION);
        validateFood(input.food());
        Map<Long, FixedBudgetItem> existing = items.findByMonthlyBudget_IdOrderByIdAsc(budget.getId())
                .stream().collect(Collectors.toMap(FixedBudgetItem::getId, Function.identity()));
        var seenIds = new HashSet<Long>();
        int allowanceCount = 0;
        for (var item : input.items()) {
            if (item == null) throw new ApiException(ErrorCode.VALIDATION);
            validateItem(item.kind(), item.name(), item.categoryId(), item.amount());
            if (item.kind() == Kind.FREE_ALLOWANCE) allowanceCount++;
            if (allowanceCount > 1) throw new ApiException(ErrorCode.VALIDATION);
            if (item.id() != null && !seenIds.add(item.id().value())) throw new ApiException(ErrorCode.VALIDATION);
            if (item.id() != null) {
                FixedBudgetItem current = existing.get(item.id().value());
                if (current == null) throw new ApiException(ErrorCode.NOT_FOUND);
                if (kind(current.getKind()) != item.kind()) throw new ApiException(ErrorCode.VALIDATION);
            }
        }
        for (var current : existing.values()) {
            if (!seenIds.contains(current.getId())) {
                if (payments.existsByFixedBudgetItem_Id(current.getId())) throw new ApiException(ErrorCode.REFERENCED);
                items.delete(current);
            }
        }
        items.flush(); // Release the one-allowance unique slot before a replacement insert.
        for (var item : input.items()) {
            Category category = fixedCategory(userId, item.categoryId());
            if (item.id() == null) {
                items.save(new FixedBudgetItem(budget, category, entityKind(item.kind()),
                        item.name().strip(), item.amount()));
            } else {
                existing.get(item.id().value()).update(category, item.name().strip(), item.amount());
            }
        }
        budget.finishSetup(input.food().weekday(), input.food().saturday(), input.food().sunday());
        items.flush();
        return summary(budget, month);
    }

    private Summary summary(MonthlyBudget budget, YearMonth month) {
        var result = new ArrayList<ItemSummary>();
        long fixedTotal = 0;
        for (var item : items.findByMonthlyBudget_IdOrderByIdAsc(budget.getId())) {
            boolean automatic = item.getKind() == FixedBudgetKind.FREE_ALLOWANCE;
            long paid = automatic ? item.getAmount() : payments.sumAmountByItemId(item.getId());
            if (paid > item.getAmount()) throw new ApiException(ErrorCode.AMOUNT_RANGE_EXCEEDED);
            fixedTotal = calendar.safeAdd(fixedTotal, item.getAmount());
            result.add(new ItemSummary(new ApiId(item.getId()), kind(item.getKind()), item.getName(),
                    new ApiId(item.getCategory().getId()), item.getAmount(), paid,
                    item.getAmount() - paid, paid == item.getAmount(), automatic));
        }
        Food food = new Food(budget.getFoodWeekday(), budget.getFoodSaturday(), budget.getFoodSunday());
        var installments = calendar.foodInstallments(month, food.weekday(), food.saturday(), food.sunday());
        long total = calendar.safeAdd(calendar.safeAdd(installments.get(0), installments.get(1)), installments.get(2));
        return new Summary(ApiFormats.formatMonth(month), "ready", result, food,
                fixedTotal, new FoodSummary(total, installments));
    }

    private void requireReady(MonthlyBudget budget) {
        if (budget.getSetupStatus() != BudgetSetupStatus.READY) throw new ApiException(ErrorCode.BUDGET_NOT_READY);
    }

    private void validateFood(Food food) {
        if (food == null || !validAmount(food.weekday()) || !validAmount(food.saturday()) || !validAmount(food.sunday()))
            throw new ApiException(ErrorCode.VALIDATION);
    }

    private void validateItem(Kind kind, String name, ApiId categoryId, Long amount) {
        if (kind == null || categoryId == null || !validAmount(amount) || name == null
                || name.strip().isEmpty() || name.strip().codePointCount(0, name.strip().length()) > 100)
            throw new ApiException(ErrorCode.VALIDATION);
    }

    private boolean validAmount(Long amount) { return amount != null && amount >= 1 && amount <= 9_999_999_999L; }

    private Category fixedCategory(Long userId, ApiId categoryId) {
        return categories.findById(categoryId.value())
                .filter(c -> c.getUser().getId().equals(userId) && c.getScope() == CategoryScope.FIXED_BUDGET)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    private FixedBudgetKind entityKind(Kind kind) {
        return kind == Kind.FREE_ALLOWANCE ? FixedBudgetKind.FREE_ALLOWANCE : FixedBudgetKind.FIXED_EXPENSE;
    }

    @Transactional
    public Summary updateFood(Long userId, YearMonth month, Food food) {
        MonthlyBudget budget = writableReady(userId, month);
        validateFood(food);
        budget.updateFood(food.weekday(), food.saturday(), food.sunday());
        return summary(budget, month);
    }

    @Transactional
    public Item createItem(Long userId, YearMonth month, ItemInput input) {
        MonthlyBudget budget = writableReady(userId, month);
        if (input == null) throw new ApiException(ErrorCode.VALIDATION);
        validateItem(input.kind(), input.name(), input.categoryId(), input.amount());
        if (input.kind() == Kind.FREE_ALLOWANCE && items.findByMonthlyBudget_IdOrderByIdAsc(budget.getId())
                .stream().anyMatch(item -> item.getKind() == FixedBudgetKind.FREE_ALLOWANCE))
            throw new ApiException(ErrorCode.CONFLICT);
        Category category = fixedCategory(userId, input.categoryId());
        FixedBudgetItem saved = items.saveAndFlush(new FixedBudgetItem(budget, category, entityKind(input.kind()),
                input.name().strip(), input.amount()));
        return itemResponse(saved);
    }

    @Transactional
    public Item updateItem(Long userId, YearMonth month, Long id, ItemInput input) {
        FixedBudgetItem item = writableItem(userId, month, id);
        if (input == null) throw new ApiException(ErrorCode.VALIDATION);
        validateItem(input.kind(), input.name(), input.categoryId(), input.amount());
        if (kind(item.getKind()) != input.kind()) throw new ApiException(ErrorCode.VALIDATION);
        if (item.getKind() == FixedBudgetKind.FIXED_EXPENSE
                && input.amount() < payments.sumAmountByItemId(id)) throw new ApiException(ErrorCode.BUDGET_BELOW_PAID);
        item.update(fixedCategory(userId, input.categoryId()), input.name().strip(), input.amount());
        return itemResponse(item);
    }

    @Transactional
    public void deleteItem(Long userId, YearMonth month, Long id) {
        FixedBudgetItem item = writableItem(userId, month, id);
        if (payments.existsByFixedBudgetItem_Id(id)) throw new ApiException(ErrorCode.REFERENCED);
        items.delete(item);
    }

    private MonthlyBudget writableReady(Long userId, YearMonth month) {
        lockWritableMonth(userId, month);
        MonthlyBudget budget = budgets.findByUser_IdAndMonthStart(userId, month.atDay(1))
                .orElseThrow(() -> new ApiException(ErrorCode.BUDGET_NOT_READY));
        requireReady(budget);
        return budget;
    }

    private void lockWritableMonth(Long userId, YearMonth month) {
        calendar.requireWritable(month);
        users.findWithLockingById(userId).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
        calendar.requireWritable(month);
    }

    private FixedBudgetItem writableItem(Long userId, YearMonth month, Long id) {
        lockWritableMonth(userId, month);
        if (id == null || id < 1) throw new ApiException(ErrorCode.VALIDATION);
        FixedBudgetItem item = items.findById(id)
                .filter(candidate -> candidate.getMonthlyBudget().getUser().getId().equals(userId)
                        && candidate.getMonthlyBudget().getMonthStart().equals(month.atDay(1)))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        requireReady(item.getMonthlyBudget());
        return item;
    }

    private Item itemResponse(FixedBudgetItem item) {
        return new Item(new ApiId(item.getId()), kind(item.getKind()), item.getName(),
                new ApiId(item.getCategory().getId()), item.getAmount());
    }
}
