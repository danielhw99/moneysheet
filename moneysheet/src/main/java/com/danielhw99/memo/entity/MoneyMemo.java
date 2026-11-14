package com.danielhw99.memo.entity;

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
@Table(name = "money_memos")
public class MoneyMemo extends BaseEntity {

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
    private MemoDirection direction;

    @Column(name = "completed_on", nullable = true)
    private LocalDate completedOn;

    protected MoneyMemo() {
    }

    public MoneyMemo(
            MonthlyBudget monthlyBudget,
            LocalDate occurredOn,
            LocalTime occurredTime,
            String title,
            Long amount,
            MemoDirection direction,
            LocalDate completedOn) {
        this.monthlyBudget = monthlyBudget;
        this.occurredOn = occurredOn;
        this.occurredTime = occurredTime;
        this.title = title;
        this.amount = amount;
        this.direction = direction;
        this.completedOn = completedOn;
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

    public MemoDirection getDirection() {
        return direction;
    }

    public LocalDate getCompletedOn() {
        return completedOn;
    }
    public void update(LocalDate date, LocalTime time, String title, Long amount, MemoDirection direction) {
        this.occurredOn = date;
        this.occurredTime = time;
        this.title = title;
        this.amount = amount;
        this.direction = direction;
    }
    public void complete(LocalDate completedOn) {
        this.completedOn = completedOn;
    }
}
