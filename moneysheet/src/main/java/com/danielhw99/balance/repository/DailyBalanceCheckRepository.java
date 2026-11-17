package com.danielhw99.balance.repository;
import java.time.LocalDate;
import java.util.Optional;
import com.danielhw99.balance.entity.DailyBalanceCheck;
import org.springframework.data.jpa.repository.JpaRepository;
public interface DailyBalanceCheckRepository extends JpaRepository<DailyBalanceCheck,Long> {
 Optional<DailyBalanceCheck> findByUserIdAndDate(Long userId,LocalDate date);
}
