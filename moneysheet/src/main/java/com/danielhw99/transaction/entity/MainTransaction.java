package com.danielhw99.transaction.entity;

import java.time.LocalDate;
import java.time.LocalTime;

import com.danielhw99.budget.entity.MonthlyBudget;
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
@Table(name = "main_transactions")
public class MainTransaction extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "monthly_budget_id", nullable = false)
    private MonthlyBudget monthlyBudget;

    @Column(name = "occurred_on", nullable = false)
    private LocalDate occurredOn;

    @Column(name = "occurred_time", nullable = false, columnDefinition = "time")
    private LocalTime occurredTime;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "direction", nullable = false, length = 10)
    private TransactionDirection direction;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "category_id", nullable = true)
    private Category category;

    protected MainTransaction() {
    }

    public MainTransaction(
            MonthlyBudget monthlyBudget,
            LocalDate occurredOn,
            LocalTime occurredTime,
            String title,
            Long amount,
            TransactionDirection direction,
            Category category) {
        this.monthlyBudget = monthlyBudget;
        this.occurredOn = occurredOn;
        this.occurredTime = occurredTime;
        this.title = title;
        this.amount = amount;
        this.direction = direction;
        this.category = category;
    }

    public MonthlyBudget getMonthlyBudget() {
        return monthlyBudget;
    }

    public LocalDate getOccurredOn() {
        return occurredOn;
    }

    public LocalTime getOccurredTime() {
        return occurredTime;
    }

    public String getTitle() {
        return title;
    }

    public Long getAmount() {
        return amount;
    }

    public TransactionDirection getDirection() {
        return direction;
    }

    public Category getCategory() {
        return category;
    }
}
