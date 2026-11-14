package com.danielhw99.payment.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import com.danielhw99.payment.entity.FixedPayment;
import com.danielhw99.payment.service.PaymentCursor.Anchor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentQueries {
    private final EntityManager em;
    public PaymentQueries(EntityManager em) { this.em = em; }
    private List<Predicate> filters(CriteriaBuilder cb, Root<FixedPayment> root, Long budget, LocalDate date, Long item) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(root.get("fixedBudgetItem").get("monthlyBudget").get("id"), budget));
        if (date != null) predicates.add(cb.equal(root.get("occurredOn"), date));
        if (item != null) predicates.add(cb.equal(root.get("fixedBudgetItem").get("id"), item));
        return predicates;
    }
    public List<FixedPayment> page(Long budget, LocalDate date, Long item, Anchor cursor, int limit) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        var query = cb.createQuery(FixedPayment.class);
        Root<FixedPayment> root = query.from(FixedPayment.class);
        List<Predicate> predicates = filters(cb, root, budget, date, item);
        if (cursor != null) predicates.add(cb.or(
                cb.lessThan(root.get("occurredOn"), cursor.date()),
                cb.and(cb.equal(root.get("occurredOn"), cursor.date()), cb.lessThan(root.get("occurredTime"), cursor.time())),
                cb.and(cb.equal(root.get("occurredOn"), cursor.date()), cb.equal(root.get("occurredTime"), cursor.time()), cb.lessThan(root.get("id"), cursor.id()))));
        query.select(root).where(predicates.toArray(Predicate[]::new))
                .orderBy(cb.desc(root.get("occurredOn")), cb.desc(root.get("occurredTime")), cb.desc(root.get("id")));
        return em.createQuery(query).setMaxResults(limit + 1).getResultList();
    }
    public BigDecimal total(Long budget, LocalDate date, Long item) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        var query = cb.createQuery(BigDecimal.class);
        Root<FixedPayment> root = query.from(FixedPayment.class);
        query.select(cb.sum(root.<Long>get("amount").as(BigDecimal.class)))
                .where(filters(cb, root, budget, date, item).toArray(Predicate[]::new));
        BigDecimal result = em.createQuery(query).getSingleResult();
        return result == null ? BigDecimal.ZERO : result;
    }
}
