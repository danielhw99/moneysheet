package com.danielhw99.memo.repository;

import com.danielhw99.memo.entity.MoneyMemo;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MoneyMemoRepository extends JpaRepository<MoneyMemo, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select memo from MoneyMemo memo where memo.id = :id")
    Optional<MoneyMemo> findLockedById(@Param("id") Long id);
}
