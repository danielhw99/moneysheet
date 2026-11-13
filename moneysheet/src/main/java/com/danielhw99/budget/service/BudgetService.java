package com.danielhw99.budget.service;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import com.danielhw99.budget.api.BudgetDtos;
import com.danielhw99.budget.api.BudgetDtos.DraftItem;
import com.danielhw99.budget.api.BudgetDtos.Food;
import com.danielhw99.budget.api.BudgetDtos.Kind;
import com.danielhw99.budget.api.BudgetDtos.Preparation;
import com.danielhw99.budget.entity.BudgetSetupStatus;
import com.danielhw99.budget.entity.FixedBudgetItem;
import com.danielhw99.budget.entity.FixedBudgetKind;
import com.danielhw99.budget.entity.MonthlyBudget;
import com.danielhw99.budget.repository.FixedBudgetItemRepository;
import com.danielhw99.budget.repository.MonthlyBudgetRepository;
import com.danielhw99.category.repository.CategoryRepository;
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
        var existing = budgets.findByUser_IdAndMonthStart(userId, month.atDay(1));
        if (existing.isPresent()) return preparation(existing.get(), month, null);

        var previous = budgets.findByUser_IdAndMonthStart(userId, month.minusMonths(1).atDay(1));
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
        return preparation(budget, month, previous.isPresent() ? ApiFormats.formatMonth(month.minusMonths(1)) : null);
    }

    private Preparation preparation(MonthlyBudget budget, YearMonth month, String source) {
        List<DraftItem> draftItems = new ArrayList<>();
        for (var item : items.findByMonthlyBudget_IdOrderByIdAsc(budget.getId())) {
            draftItems.add(new DraftItem(new ApiId(item.getId()), kind(item.getKind()), item.getName(),
                    new ApiId(item.getCategory().getId()), item.getAmount()));
        }
        if (source == null && draftItems.isEmpty() && budget.getFoodWeekday() == null) {
            draftItems.add(new DraftItem(null, Kind.FREE_ALLOWANCE, "고정자유비", null, null));
        }
        return new Preparation(ApiFormats.formatMonth(month), budget.getSetupStatus() == BudgetSetupStatus.READY ? "ready" : "draft",
                source, draftItems, new Food(budget.getFoodWeekday(), budget.getFoodSaturday(), budget.getFoodSunday()));
    }

    private Kind kind(FixedBudgetKind value) {
        return value == FixedBudgetKind.FREE_ALLOWANCE ? Kind.FREE_ALLOWANCE : Kind.FIXED_EXPENSE;
    }
}
