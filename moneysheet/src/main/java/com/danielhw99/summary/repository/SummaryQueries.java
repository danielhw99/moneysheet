package com.danielhw99.summary.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.danielhw99.summary.service.SummaryFacts.Amounts;
import com.danielhw99.summary.service.SummaryFacts.Ledger;
import com.danielhw99.summary.service.SummaryFacts.MemoWindow;
import com.danielhw99.transaction.entity.MainTransaction;
import com.danielhw99.transaction.entity.TransactionDirection;
import com.danielhw99.memo.entity.MoneyMemo;
import com.danielhw99.memo.entity.MemoDirection;
import com.danielhw99.payment.entity.FixedPayment;
import com.danielhw99.cashflow.entity.CashFlowKind;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

@Repository
public class SummaryQueries {
    private final EntityManager em;
    public record Totals(BigDecimal fixed,BigDecimal income,BigDecimal extra) {}
    public record DailyRows(List<MainTransaction> transactions,List<MoneyMemo> memos,List<FixedPayment> payments,Map<Long,BigDecimal> paidByItem) {}
    public SummaryQueries(EntityManager em) { this.em=em; }

    public Ledger ledger(Long budget) {
        Map<LocalDate,Amounts> transactions=new HashMap<>();
        for(Object[] row:em.createQuery("select t.occurredOn,t.direction,sum(cast(t.amount as BigDecimal)) from MainTransaction t where t.monthlyBudget.id=:budget group by t.occurredOn,t.direction",Object[].class).setParameter("budget",budget).getResultList()) {
            LocalDate date=(LocalDate)row[0];Amounts previous=transactions.getOrDefault(date,Amounts.zero());
            transactions.put(date,row[1]==TransactionDirection.EXPENSE?new Amounts((BigDecimal)row[2],previous.income()):new Amounts(previous.expense(),(BigDecimal)row[2]));
        }
        Map<LocalDate,BigDecimal> payments=new HashMap<>();
        for(Object[] row:em.createQuery("select p.occurredOn,sum(cast(p.amount as BigDecimal)) from FixedPayment p where p.fixedBudgetItem.monthlyBudget.id=:budget group by p.occurredOn",Object[].class).setParameter("budget",budget).getResultList()) payments.put((LocalDate)row[0],(BigDecimal)row[1]);
        List<MemoWindow> memos=new ArrayList<>();
        for(Object[] row:em.createQuery("select m.occurredOn,m.completedOn,m.direction,sum(cast(m.amount as BigDecimal)),count(m) from MoneyMemo m where m.monthlyBudget.id=:budget group by m.occurredOn,m.completedOn,m.direction",Object[].class).setParameter("budget",budget).getResultList()) {
            boolean expense=row[2]==MemoDirection.EXPENSE;
            memos.add(new MemoWindow((LocalDate)row[0],(LocalDate)row[1],expense?(BigDecimal)row[3]:BigDecimal.ZERO,expense?BigDecimal.ZERO:(BigDecimal)row[3],(Long)row[4]));
        }
        return new Ledger(transactions,payments,memos);
    }

    public Totals totals(Long budget) {
        BigDecimal fixed=em.createQuery("select sum(cast(i.amount as BigDecimal)) from FixedBudgetItem i where i.monthlyBudget.id=:budget",BigDecimal.class).setParameter("budget",budget).getSingleResult();
        BigDecimal income=cash(budget,CashFlowKind.INCOME),extra=cash(budget,CashFlowKind.EXTRA_EXPENSE);
        return new Totals(zero(fixed),income,extra);
    }
    private BigDecimal cash(Long budget,CashFlowKind kind) {
        return zero(em.createQuery("select sum(cast(c.amount as BigDecimal)) from MonthlyCashFlow c where c.monthlyBudget.id=:budget and c.kind=:kind",BigDecimal.class).setParameter("budget",budget).setParameter("kind",kind).getSingleResult());
    }
    private BigDecimal zero(BigDecimal value) { return value==null?BigDecimal.ZERO:value; }

    public DailyRows dailyRows(Long budget,LocalDate date) {
        var transactions=em.createQuery("select t from MainTransaction t left join fetch t.category where t.monthlyBudget.id=:budget and t.occurredOn=:date order by t.occurredOn desc,t.occurredTime desc,t.id desc",MainTransaction.class).setParameter("budget",budget).setParameter("date",date).getResultList();
        var memos=em.createQuery("select m from MoneyMemo m where m.monthlyBudget.id=:budget and m.occurredOn<=:date and (m.completedOn is null or m.completedOn>:date) order by m.occurredOn desc,m.occurredTime desc,m.id desc",MoneyMemo.class).setParameter("budget",budget).setParameter("date",date).getResultList();
        var payments=em.createQuery("select p from FixedPayment p join fetch p.fixedBudgetItem i where i.monthlyBudget.id=:budget and p.occurredOn=:date order by p.occurredOn desc,p.occurredTime desc,p.id desc",FixedPayment.class).setParameter("budget",budget).setParameter("date",date).getResultList();
        Map<Long,BigDecimal> paid=new HashMap<>();
        for(Object[] row:em.createQuery("select p.fixedBudgetItem.id,sum(cast(p.amount as BigDecimal)) from FixedPayment p where p.fixedBudgetItem.monthlyBudget.id=:budget and p.occurredOn<=:date group by p.fixedBudgetItem.id",Object[].class).setParameter("budget",budget).setParameter("date",date).getResultList()) paid.put((Long)row[0],(BigDecimal)row[1]);
        // Daily's arrays intentionally contain every matching row; no list cursor/limit applies.
        return new DailyRows(transactions,memos,payments,Map.copyOf(paid));
    }
}
