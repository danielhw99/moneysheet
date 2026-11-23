package com.danielhw99.payment.repository;

import com.danielhw99.payment.entity.FixedPayment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FixedPaymentRepository extends JpaRepository<FixedPayment, Long> {
    @Query("select p.fixedBudgetItem.id, sum(p.amount) from FixedPayment p where p.fixedBudgetItem.monthlyBudget.id = :budgetId group by p.fixedBudgetItem.id")
    java.util.List<Object[]> sumAmountsByBudgetId(@Param("budgetId") Long budgetId);

    boolean existsByFixedBudgetItem_Id(Long itemId);

    @Query("select coalesce(sum(p.amount), 0) from FixedPayment p where p.fixedBudgetItem.id = :itemId")
    long sumAmountByItemId(@Param("itemId") Long itemId);
}
