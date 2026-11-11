package com.danielhw99.transaction.repository;

import com.danielhw99.transaction.entity.MainTransaction;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MainTransactionRepository extends JpaRepository<MainTransaction, Long> {
}
