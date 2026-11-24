package com.danielhw99.summary.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import com.danielhw99.budget.service.BudgetCalendar;
import com.danielhw99.common.error.ApiException;
import com.danielhw99.common.error.ErrorCode;
import com.danielhw99.summary.service.SummaryFacts.Food;
import com.danielhw99.summary.service.SummaryFacts.Ledger;
import com.danielhw99.summary.service.SummaryFacts.Amounts;
import org.springframework.stereotype.Component;

@Component
public class SummaryCalculator {
    private static final BigDecimal MAX_SAFE=BigDecimal.valueOf(9_007_199_254_740_991L);
    private final BudgetCalendar calendar;
    public record Day(long carryoverFreeBalance,long availableBeforeSpending,long freeBalance,long foodBudget,
            long remainingFood,long expenseTotal,long incomeTotal,long mainBalance,int activeMemos,int completedMemos) {}
    public record Month(long income,long extra,long fixedBudget,long foodBudget,long basicTotal,long totalExpense,long net) {}
    public SummaryCalculator(BudgetCalendar calendar) { this.calendar=calendar; }

    public Day daily(LocalDate date,Food food,Ledger ledger) {
        YearMonth month=YearMonth.from(date);
        long carry=0,available=0,free=0,todayFood=0,expense=0,income=0,cumulativeIncome=0,cumulativeExpense=0;
        for(int day=1;day<=date.getDayOfMonth();day++) {
            LocalDate current=month.atDay(day);carry=free;todayFood=foodForDate(current,food);
            Amounts totals=ledger.transactions().getOrDefault(current,Amounts.zero());
            expense=positive(totals.expense());income=positive(totals.income());
            // Validate the raw running aggregate even when food would offset it.
            cumulativeExpense=add(cumulativeExpense,expense);
            cumulativeIncome=add(cumulativeIncome,income);
            available=add(carry,todayFood);free=add(add(available,-expense),income);
        }
        int end=date.getDayOfMonth()<=10?10:date.getDayOfMonth()<=20?20:month.lengthOfMonth();
        if(food.paymentMode()==com.danielhw99.budget.entity.FoodPaymentMode.MONTH_START) end=month.lengthOfMonth();
        long remaining=0;
        for(int day=date.getDayOfMonth()+1;day<=end && (food.foodStartDate()==null || !date.isBefore(food.foodStartDate()));day++) remaining=add(remaining,foodForDate(month.atDay(day),food));
        MemoState memos=memos(date,ledger);
        long main=add(add(free,remaining),memos.adjustment());
        return new Day(carry,available,free,todayFood,remaining,expense,income,main,memos.active(),memos.completed());
    }

    public record MemoState(long adjustment,int active,int completed) {}
    public MemoState memos(LocalDate date,Ledger ledger) {
        YearMonth month=YearMonth.from(date);
        long expense=0,income=0,active=0,completed=0;
        for(var memo:ledger.memos()) {
            if(!YearMonth.from(memo.date()).equals(month)) continue;
            if(memo.count()<0) throw range();
            if(!memo.date().isAfter(date) && (memo.completedOn()==null || memo.completedOn().isAfter(date))) {
                expense=add(expense,positive(memo.expense()));income=add(income,positive(memo.income()));active=add(active,memo.count());
            }
            if(date.equals(memo.completedOn())) completed=add(completed,memo.count());
        }
        try { return new MemoState(add(income,-expense),Math.toIntExact(active),Math.toIntExact(completed)); }
        catch(ArithmeticException overflow) { throw range(); }
    }

    public Month monthly(YearMonth month,Food food,BigDecimal fixed,BigDecimal income,BigDecimal extra) {
        long fixedTotal=positive(fixed),in=positive(income),out=positive(extra);
        var installments=calendar.foodInstallments(month,food.paymentMode(),food.weekday(),food.saturday(),food.sunday(),food.foodStartDate()==null?month.atDay(1):food.foodStartDate());
        long foodTotal=add(add(installments.get(0),installments.get(1)),installments.get(2));
        long basic=add(fixedTotal,foodTotal),expense=add(basic,out);
        return new Month(in,out,fixedTotal,foodTotal,basic,expense,add(in,-expense));
    }

    public long foodForDate(LocalDate date,Food food) {
        if(food.foodStartDate()!=null && date.isBefore(food.foodStartDate()))return 0;
        long amount=switch(date.getDayOfWeek()) { case SATURDAY -> food.saturday(); case SUNDAY -> food.sunday(); default -> food.weekday(); };
        if(amount<1) throw range();
        return positive(BigDecimal.valueOf(amount));
    }

    public long positive(BigDecimal value) {
        if(value==null || value.signum()<0 || value.compareTo(MAX_SAFE)>0) throw range();
        try { return value.longValueExact(); } catch(ArithmeticException overflow) { throw range(); }
    }
    public long add(long left,long right) { return calendar.safeAdd(left,right); }
    private ApiException range() { return new ApiException(ErrorCode.AMOUNT_RANGE_EXCEEDED); }
}
