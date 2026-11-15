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
            cumulativeExpense=add(cumulativeExpense,expense);
            cumulativeIncome=add(cumulativeIncome,income);
            available=add(carry,todayFood);free=add(available,-expense);
        }
        int end=date.getDayOfMonth()<=11?11:date.getDayOfMonth()<=21?21:month.lengthOfMonth();
        long remaining=0;
        for(int day=date.getDayOfMonth()+1;day<=end;day++) remaining=add(remaining,foodForDate(month.atDay(day),food));
        long memoExpense=0,memoIncome=0,active=0,completed=0;
        for(var memo:ledger.memos()) {
            if(!YearMonth.from(memo.date()).equals(month)) continue;
            if(memo.count()<0) throw range();
            if(memo.date().compareTo(date)<=0 && (memo.completedOn()==null || memo.completedOn().isAfter(date))) {
                memoExpense=add(memoExpense,positive(memo.expense()));memoIncome=add(memoIncome,positive(memo.income()));active=add(active,memo.count());
            }
            if(date.equals(memo.completedOn())) completed=add(completed,memo.count());
        }
        long memoAdjustment=add(memoIncome,-memoExpense);
        long main=add(add(add(free,remaining),cumulativeIncome),memoAdjustment);
        try { return new Day(carry,available,free,todayFood,remaining,expense,income,main,Math.toIntExact(active),Math.toIntExact(completed)); }
        catch(ArithmeticException overflow) { throw range(); }
    }

    public Month monthly(YearMonth month,Food food,BigDecimal fixed,BigDecimal income,BigDecimal extra) {
        long fixedTotal=positive(fixed),in=positive(income),out=positive(extra);
        var installments=calendar.foodInstallments(month,food.weekday(),food.saturday(),food.sunday());
        long foodTotal=add(add(installments.get(0),installments.get(1)),installments.get(2));
        long basic=add(fixedTotal,foodTotal),expense=add(basic,out);
        return new Month(in,out,fixedTotal,foodTotal,basic,expense,add(in,-expense));
    }

    public long foodForDate(LocalDate date,Food food) {
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
