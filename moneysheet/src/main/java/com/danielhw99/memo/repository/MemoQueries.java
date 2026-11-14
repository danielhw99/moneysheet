package com.danielhw99.memo.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import com.danielhw99.memo.api.MemoStatus;
import com.danielhw99.memo.api.MemoDirectionValue;
import com.danielhw99.memo.entity.MoneyMemo;
import com.danielhw99.memo.service.MemoCursor.Anchor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.stereotype.Repository;

@Repository
public class MemoQueries {
    private final EntityManager em;
    public MemoQueries(EntityManager em) { this.em = em; }

    private List<Predicate> filters(CriteriaBuilder cb, Root<MoneyMemo> root, Long budget, LocalDate date, MemoStatus status) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(root.get("monthlyBudget").get("id"), budget));
        switch (status) {
            case PENDING -> {
                predicates.add(cb.lessThanOrEqualTo(root.get("occurredOn"), date));
                predicates.add(cb.or(cb.isNull(root.get("completedOn")), cb.greaterThan(root.get("completedOn"), date)));
            }
            case COMPLETED -> predicates.add(cb.equal(root.get("completedOn"), date));
            case ALL -> predicates.add(cb.equal(root.get("occurredOn"), date));
        }
        return predicates;
    }

    public List<MoneyMemo> page(Long budget, LocalDate date, MemoStatus status, Anchor cursor, int limit) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        var query = cb.createQuery(MoneyMemo.class);
        Root<MoneyMemo> root = query.from(MoneyMemo.class);
        List<Predicate> predicates = filters(cb, root, budget, date, status);
        if (cursor != null) predicates.add(cb.or(
                cb.lessThan(root.get("occurredOn"), cursor.date()),
                cb.and(cb.equal(root.get("occurredOn"), cursor.date()), cb.lessThan(root.get("occurredTime"), cursor.time())),
                cb.and(cb.equal(root.get("occurredOn"), cursor.date()), cb.equal(root.get("occurredTime"), cursor.time()), cb.lessThan(root.get("id"), cursor.id()))));
        query.select(root).where(predicates.toArray(Predicate[]::new))
                .orderBy(cb.desc(root.get("occurredOn")), cb.desc(root.get("occurredTime")), cb.desc(root.get("id")));
        return em.createQuery(query).setMaxResults(limit + 1).getResultList();
    }

    public BigDecimal total(Long budget, LocalDate date, MemoStatus status, MemoDirectionValue direction) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        var query = cb.createQuery(BigDecimal.class);
        Root<MoneyMemo> root = query.from(MoneyMemo.class);
        List<Predicate> predicates = filters(cb, root, budget, date, status);
        predicates.add(cb.equal(root.get("direction"), direction.entityValue()));
        query.select(cb.sum(root.<Long>get("amount").as(BigDecimal.class))).where(predicates.toArray(Predicate[]::new));
        BigDecimal result = em.createQuery(query).getSingleResult();
        return result == null ? BigDecimal.ZERO : result;
    }
}
