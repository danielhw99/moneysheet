package com.danielhw99.budget.repository;

import com.danielhw99.budget.entity.MonthlyBudget;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MonthlyBudgetRepository extends JpaRepository<MonthlyBudget, Long> {
}
