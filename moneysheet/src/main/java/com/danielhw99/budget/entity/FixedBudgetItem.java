package com.danielhw99.budget.entity;

import com.danielhw99.category.entity.Category;
import com.danielhw99.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "fixed_budget_items")
public class FixedBudgetItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "monthly_budget_id", nullable = false)
    private MonthlyBudget monthlyBudget;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "kind", nullable = false, length = 20)
    private FixedBudgetKind kind;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "amount", nullable = false)
    private Long amount;

    protected FixedBudgetItem() {
    }

    public FixedBudgetItem(MonthlyBudget monthlyBudget, Category category, FixedBudgetKind kind, String name, Long amount) {
        this.monthlyBudget = monthlyBudget;
        this.category = category;
        this.kind = kind;
        this.name = name;
        this.amount = amount;
    }

    public MonthlyBudget getMonthlyBudget() {
        return monthlyBudget;
    }

    public Category getCategory() {
        return category;
    }

    public FixedBudgetKind getKind() {
        return kind;
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
