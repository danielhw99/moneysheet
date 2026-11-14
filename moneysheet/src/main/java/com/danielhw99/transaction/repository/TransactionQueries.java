package com.danielhw99.transaction.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import com.danielhw99.transaction.entity.MainTransaction;
import com.danielhw99.transaction.api.TransactionDirectionValue;
import com.danielhw99.transaction.service.TransactionCursor.Anchor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.stereotype.Repository;

@Repository
public class TransactionQueries {
    private final EntityManager em;
    public TransactionQueries(EntityManager em) { this.em = em; }
    private List<Predicate> filters(CriteriaBuilder cb, Root<MainTransaction> root, Long budget,
            LocalDate date, TransactionDirectionValue direction) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(root.get("monthlyBudget").get("id"), budget));
        if (date != null) predicates.add(cb.equal(root.get("occurredOn"),date));
        if (direction != null) predicates.add(cb.equal(root.get("direction"),direction.entityValue()));
        return predicates;
    }
    public List<MainTransaction> page(Long budget, LocalDate date, TransactionDirectionValue direction, Anchor cursor, int limit) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        var query = cb.createQuery(MainTransaction.class);
        Root<MainTransaction> root = query.from(MainTransaction.class);
        List<Predicate> predicates = filters(cb,root,budget,date,direction);
        if (cursor != null) predicates.add(cb.or(
                cb.lessThan(root.get("occurredOn"),cursor.date()),
                cb.and(cb.equal(root.get("occurredOn"),cursor.date()),cb.lessThan(root.get("occurredTime"),cursor.time())),
                cb.and(cb.equal(root.get("occurredOn"),cursor.date()),cb.equal(root.get("occurredTime"),cursor.time()),cb.lessThan(root.get("id"),cursor.id()))));
        query.select(root).where(predicates.toArray(Predicate[]::new))
                .orderBy(cb.desc(root.get("occurredOn")),cb.desc(root.get("occurredTime")),cb.desc(root.get("id")));
        return em.createQuery(query).setMaxResults(limit + 1).getResultList();
    }
    public BigDecimal total(Long budget, LocalDate date, TransactionDirectionValue filter, TransactionDirectionValue summed) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        var query = cb.createQuery(BigDecimal.class);
        Root<MainTransaction> root = query.from(MainTransaction.class);
        List<Predicate> predicates = filters(cb,root,budget,date,filter);
        predicates.add(cb.equal(root.get("direction"),summed.entityValue()));
        query.select(cb.sum(root.<Long>get("amount").as(BigDecimal.class))).where(predicates.toArray(Predicate[]::new));
        BigDecimal result = em.createQuery(query).getSingleResult();
        return result == null ? BigDecimal.ZERO : result;
    }
}
