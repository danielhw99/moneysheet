package com.danielhw99.auth.email;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification,String> {
    // The unique email row serializes concurrent first sends as well as later verification and signup.
    @Modifying
    @Query(value="INSERT INTO email_verifications(email_key,request_count,attempts,consumed) VALUES (:email,0,0,false) ON DUPLICATE KEY UPDATE email_key=email_key",nativeQuery=true)
    void ensure(@Param("email") String email);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from EmailVerification v where v.emailKey=:email")
    Optional<EmailVerification> findLockedByEmailKey(@Param("email") String email);
    @Modifying
    @Query("delete from EmailVerification v where v.sentDate < :cutoff")
    int deleteExpired(@Param("cutoff") LocalDateTime cutoff);
}
