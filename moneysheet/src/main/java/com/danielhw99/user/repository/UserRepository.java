package com.danielhw99.user.repository;

import com.danielhw99.user.entity.User;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<User> findWithLockingById(Long id);
    boolean existsByEmailKey(String emailKey);
    java.util.Optional<User> findByEmailKey(String emailKey);
}
