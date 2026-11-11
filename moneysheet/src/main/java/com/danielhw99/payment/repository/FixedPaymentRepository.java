package com.danielhw99.payment.repository;

import com.danielhw99.payment.entity.FixedPayment;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FixedPaymentRepository extends JpaRepository<FixedPayment, Long> {
}
