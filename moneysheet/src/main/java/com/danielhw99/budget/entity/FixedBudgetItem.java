package com.danielhw99.budget.entity;

import com.danielhw99.category.entity.Category;
import com.danielhw99.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;


@Entity
@Table(name = "fixed_budget_items")
public class FixedBudgetItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "monthly_budget_id", nullable = false)
    private MonthlyBudget monthlyBudget;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "amount", nullable = false)
    private Long amount;

    protected FixedBudgetItem() {
    }

    public FixedBudgetItem(MonthlyBudget monthlyBudget, Category category, String name, Long amount) {
        this.monthlyBudget = monthlyBudget;
        this.category = category;
        this.name = name;
        this.amount = amount;
    }

    public MonthlyBudget getMonthlyBudget() {
        return monthlyBudget;
    }

    public Category getCategory() {
        return category;
    }

    public String getName() {
        return name;
    }

    public Long getAmount() {
        return amount;
    }

    public void update(Category category, String name, long amount) {
        this.category = category;
        this.name = name;
        this.amount = amount;
    }
}
