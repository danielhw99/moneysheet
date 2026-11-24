package com.danielhw99.budget.repository;

import com.danielhw99.budget.entity.MonthlyBudget;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.Optional;

public interface MonthlyBudgetRepository extends JpaRepository<MonthlyBudget, Long> {
    boolean existsByUser_IdAndSetupStatus(Long userId, com.danielhw99.budget.entity.BudgetSetupStatus status);
    Optional<MonthlyBudget> findByUser_IdAndMonthStart(Long userId, LocalDate monthStart);
}
