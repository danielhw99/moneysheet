package com.danielhw99.cashflow.entity;

import java.time.LocalDate;
import java.time.LocalTime;

import com.danielhw99.budget.entity.MonthlyBudget;
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
@Table(name = "monthly_cash_flows")
public class MonthlyCashFlow extends BaseEntity {

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
    @Column(name = "kind", nullable = false, length = 20)
    private CashFlowKind kind;

    protected MonthlyCashFlow() {
    }

    public MonthlyCashFlow(
            MonthlyBudget monthlyBudget,
            LocalDate occurredOn,
            LocalTime occurredTime,
            String title,
            Long amount,
            CashFlowKind kind) {
        this.monthlyBudget = monthlyBudget;
        this.occurredOn = occurredOn;
        this.occurredTime = occurredTime;
        this.title = title;
        this.amount = amount;
        this.kind = kind;
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

    public CashFlowKind getKind() {
        return kind;
    }
}
