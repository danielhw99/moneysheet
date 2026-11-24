package com.danielhw99.budget.entity;

import java.time.LocalDate;

import com.danielhw99.common.persistence.BaseEntity;
import com.danielhw99.user.entity.User;

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
@Table(name = "monthly_budgets")
public class MonthlyBudget extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "month_start", nullable = false)
    private LocalDate monthStart;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "setup_status", nullable = false, length = 10)
    private BudgetSetupStatus setupStatus;

    @Column(name = "food_weekday", nullable = true)
    private Long foodWeekday;

    @Column(name = "food_saturday", nullable = true)
    private Long foodSaturday;

    @Column(name = "food_sunday", nullable = true)
    private Long foodSunday;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name="food_payment_mode",nullable=false,length=20)
    private FoodPaymentMode foodPaymentMode=FoodPaymentMode.THREE_INSTALLMENTS;

    public FoodPaymentMode getFoodPaymentMode() { return foodPaymentMode; }
    public void copyFoodSettings(MonthlyBudget source) {
        foodPaymentMode=source.foodPaymentMode;
    }
    public void configureFood(FoodPaymentMode mode, Long weekday, Long saturday, Long sunday) {
        foodPaymentMode=mode;
        foodWeekday=weekday;foodSaturday=saturday;foodSunday=sunday;
    }
    public void finishSetup() { if(foodStartDate==null)foodStartDate=monthStart; setupStatus=BudgetSetupStatus.READY; }

    @Column(name="food_start_date")
    private LocalDate foodStartDate;
    public LocalDate getFoodStartDate() { return foodStartDate; }
    public void selectFoodStartDate(LocalDate date) {
        if(setupStatus!=BudgetSetupStatus.DRAFT) throw new com.danielhw99.common.error.ApiException(com.danielhw99.common.error.ErrorCode.CONFLICT);
        foodStartDate=date;
    }

    protected MonthlyBudget() {
    }

    public MonthlyBudget(
            User user,
            LocalDate monthStart,
            BudgetSetupStatus setupStatus,
            Long foodWeekday,
            Long foodSaturday,
            Long foodSunday) {
        this.user = user;
        this.monthStart = monthStart;
        this.setupStatus = setupStatus;
        this.foodStartDate = setupStatus==BudgetSetupStatus.READY?monthStart:null;
        this.foodWeekday = foodWeekday;
        this.foodSaturday = foodSaturday;
        this.foodSunday = foodSunday;
    }

    public User getUser() {
        return user;
    }

    public LocalDate getMonthStart() {
        return monthStart;
    }

    public BudgetSetupStatus getSetupStatus() {
        return setupStatus;
    }

    public Long getFoodWeekday() {
        return foodWeekday;
    }

    public Long getFoodSaturday() {
        return foodSaturday;
    }

    public Long getFoodSunday() {
        return foodSunday;
    }

    public void finishSetup(long weekday, long saturday, long sunday) {
        updateFood(weekday, saturday, sunday);
        if(foodStartDate==null)foodStartDate=monthStart;
        setupStatus = BudgetSetupStatus.READY;
    }

    public void updateFood(long weekday, long saturday, long sunday) {
        foodWeekday = weekday;
        foodSaturday = saturday;
        foodSunday = sunday;
    }
}
