package com.danielhw99.user.repository;

import com.danielhw99.user.entity.User;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByEmailKey(String emailKey);
    java.util.Optional<User> findByEmailKey(String emailKey);
}
