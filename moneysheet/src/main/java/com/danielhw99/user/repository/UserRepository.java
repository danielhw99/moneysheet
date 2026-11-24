package com.danielhw99.user.repository;

import com.danielhw99.user.entity.User;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<User> findWithLockingById(Long id);
    // Use MySQL LOWER for both the generated unique key and lookup (no Java/DB case-fold mismatch).
    @org.springframework.data.jpa.repository.Query(value = "SELECT COUNT(*) FROM users WHERE nickname_key = LOWER(:nickname) AND id <> :excludedId", nativeQuery = true)
    long countNicknameConflicts(@org.springframework.data.repository.query.Param("nickname") String nickname,
            @org.springframework.data.repository.query.Param("excludedId") Long excludedId);
    boolean existsByEmailKey(String emailKey);
    java.util.Optional<User> findByEmailKey(String emailKey);
}
