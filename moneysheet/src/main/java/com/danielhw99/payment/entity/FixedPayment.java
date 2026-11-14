package com.danielhw99.payment.entity;

import java.time.LocalDate;
import java.time.LocalTime;

import com.danielhw99.budget.entity.FixedBudgetItem;
import com.danielhw99.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "fixed_payments")
public class FixedPayment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fixed_budget_item_id", nullable = false)
    private FixedBudgetItem fixedBudgetItem;

    @Column(name = "occurred_on", nullable = false)
    private LocalDate occurredOn;

    @Column(name = "occurred_time", nullable = false, columnDefinition = "time")
    private LocalTime occurredTime;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "amount", nullable = false)
    private Long amount;

    protected FixedPayment() {
    }

    public FixedPayment(
            FixedBudgetItem fixedBudgetItem,
            LocalDate occurredOn,
            LocalTime occurredTime,
            String title,
            Long amount) {
        this.fixedBudgetItem = fixedBudgetItem;
        this.occurredOn = occurredOn;
        this.occurredTime = occurredTime;
        this.title = title;
        this.amount = amount;
    }

    public FixedBudgetItem getFixedBudgetItem() {
        return fixedBudgetItem;
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
    public void update(FixedBudgetItem item, LocalDate date, LocalTime time, String title, Long amount) {
        this.fixedBudgetItem = item;
        this.occurredOn = date;
        this.occurredTime = time;
        this.title = title;
        this.amount = amount;
    }
}
