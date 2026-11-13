package com.danielhw99.budget.repository;

import com.danielhw99.budget.entity.MonthlyBudget;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.Optional;

public interface MonthlyBudgetRepository extends JpaRepository<MonthlyBudget, Long> {
    Optional<MonthlyBudget> findByUser_IdAndMonthStart(Long userId, LocalDate monthStart);
}
