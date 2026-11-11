package com.danielhw99.cashflow.repository;

import com.danielhw99.cashflow.entity.MonthlyCashFlow;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MonthlyCashFlowRepository extends JpaRepository<MonthlyCashFlow, Long> {
}
