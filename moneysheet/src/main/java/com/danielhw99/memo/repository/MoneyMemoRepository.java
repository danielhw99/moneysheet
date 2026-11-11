package com.danielhw99.memo.repository;

import com.danielhw99.memo.entity.MoneyMemo;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MoneyMemoRepository extends JpaRepository<MoneyMemo, Long> {
}
