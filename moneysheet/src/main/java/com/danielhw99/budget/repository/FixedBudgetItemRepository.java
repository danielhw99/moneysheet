package com.danielhw99.budget.repository;

import com.danielhw99.budget.entity.FixedBudgetItem;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FixedBudgetItemRepository extends JpaRepository<FixedBudgetItem, Long> {
    List<FixedBudgetItem> findByMonthlyBudget_IdOrderByIdAsc(Long monthlyBudgetId);
}
