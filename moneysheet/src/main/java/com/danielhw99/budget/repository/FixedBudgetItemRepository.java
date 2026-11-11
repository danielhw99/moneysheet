package com.danielhw99.budget.repository;

import com.danielhw99.budget.entity.FixedBudgetItem;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FixedBudgetItemRepository extends JpaRepository<FixedBudgetItem, Long> {
}
