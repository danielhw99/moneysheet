package com.danielhw99.summary.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import com.danielhw99.summary.api.SummaryDtos;
import com.danielhw99.summary.repository.SummaryQueries;
import com.danielhw99.summary.service.SummaryFacts.Food;
import com.danielhw99.summary.service.SummaryFacts.Amounts;
import com.danielhw99.budget.entity.MonthlyBudget;
import com.danielhw99.budget.entity.BudgetSetupStatus;
import com.danielhw99.budget.repository.MonthlyBudgetRepository;
import com.danielhw99.transaction.service.TransactionService;
import com.danielhw99.memo.service.MemoService;
import com.danielhw99.payment.service.PaymentService;
import com.danielhw99.common.error.ApiException;
import com.danielhw99.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
public class SummaryService {
    private final MonthlyBudgetRepository budgets;
    private final SummaryQueries queries;
    private final SummaryCalculator calculator;
    private final Clock clock;
    public SummaryService(MonthlyBudgetRepository budgets,SummaryQueries queries,SummaryCalculator calculator,Clock clock) {
        this.budgets=budgets;this.queries=queries;this.calculator=calculator;this.clock=clock;
    }
    public SummaryDtos.Daily daily(Long owner,LocalDate date) {
        var budget=ready(owner,YearMonth.from(date));
        var numbers=calculator.daily(date,food(budget),queries.ledger(budget.getId()));
        var rows=queries.dailyRows(budget.getId(),date);
        var payments=rows.payments().stream().map(row->{
            var item=row.getFixedBudgetItem();long paid=calculator.positive(rows.paidByItem().getOrDefault(item.getId(),BigDecimal.ZERO));
            long remaining=calculator.add(item.getAmount(),-paid);
            if(remaining<0) throw new ApiException(ErrorCode.AMOUNT_RANGE_EXCEEDED);
            return new SummaryDtos.DailyPayment(PaymentService.response(row),item.getName(),paid,remaining,remaining==0);
        }).toList();
        return new SummaryDtos.Daily(date,numbers.mainBalance(),numbers.freeBalance(),numbers.foodBudget(),numbers.carryoverFreeBalance(),
                numbers.availableBeforeSpending(),numbers.remainingFood(),numbers.expenseTotal(),numbers.incomeTotal(),
                rows.transactions().stream().map(TransactionService::response).toList(),rows.memos().stream().map(MemoService::response).toList(),payments);
    }
    public SummaryDtos.Monthly monthly(Long owner,YearMonth month) { return monthly(month,ready(owner,month)); }
    private SummaryDtos.Monthly monthly(YearMonth month,MonthlyBudget budget) {
        var totals=queries.totals(budget.getId());var value=calculator.monthly(month,food(budget),totals.fixed(),totals.income(),totals.extra());
        return new SummaryDtos.Monthly(month,value.income(),value.extra(),value.fixedBudget(),value.foodBudget(),value.basicTotal(),value.totalExpense(),value.net());
    }
    public SummaryDtos.Home home(Long owner,YearMonth month) {
        LocalDate today=LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul")));
        if(month.isAfter(YearMonth.from(today))) throw new ApiException(ErrorCode.FUTURE_MONTH_NOT_ALLOWED);
        LocalDate asOf=month.equals(YearMonth.from(today))?today:month.atEndOfMonth();
        var budget=ready(owner,month);var food=food(budget);var ledger=queries.ledger(budget.getId());
        var days=new ArrayList<SummaryDtos.CalendarDay>();
        for(int day=1;day<=month.lengthOfMonth();day++) {
            LocalDate date=month.atDay(day);var entries=new ArrayList<SummaryDtos.CalendarEntry>();
            var amounts=ledger.transactions().getOrDefault(date,Amounts.zero());
            long expense=calculator.positive(amounts.expense()),income=calculator.positive(amounts.income());
            if(expense>0) entries.add(new SummaryDtos.AmountEntry("transaction","내역 지출",-expense));
            if(income>0) entries.add(new SummaryDtos.AmountEntry("transaction","내역 입금",income));
            long paid=calculator.positive(ledger.payments().getOrDefault(date,BigDecimal.ZERO));
            if(paid>0) entries.add(new SummaryDtos.AmountEntry("fixed","고정지출",-paid));
            var memos=calculator.memos(date,ledger);
            if(memos.active()>0) entries.add(new SummaryDtos.MemoEntry("memo","미완료 메모",memos.active()));
            if(memos.completed()>0) entries.add(new SummaryDtos.MemoEntry("memo","완료 메모",memos.completed()));
            if(!date.isAfter(asOf)) entries.add(new SummaryDtos.AmountEntry("free","여유비",calculator.daily(date,food,ledger).freeBalance()));
            days.add(new SummaryDtos.CalendarDay(date,entries));
        }
        return new SummaryDtos.Home(month,asOf,days,monthly(month,budget).totalExpense(),calculator.daily(asOf,food,ledger).freeBalance());
    }
    private MonthlyBudget ready(Long owner,YearMonth month) {
        var budget=budgets.findByUser_IdAndMonthStart(owner,month.atDay(1)).orElseThrow(()->new ApiException(ErrorCode.BUDGET_NOT_READY));
        if(budget.getSetupStatus()!=BudgetSetupStatus.READY) throw new ApiException(ErrorCode.BUDGET_NOT_READY);
        return budget;
    }
    private Food food(MonthlyBudget budget) { return new Food(budget.getFoodWeekday(),budget.getFoodSaturday(),budget.getFoodSunday()); }
}
