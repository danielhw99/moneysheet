package com.danielhw99.auth.email;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name="email_verifications")
public class EmailVerification {
    @Id @Column(name="email_key",length=254) private String emailKey;
    @Column(name="challenge_id",length=36) String challengeId;
    @Column(name="code_hash",length=255) String codeHash;
    @Column(name="proof_hash",length=64) String proofHash;
    @Column(name="expires_at",columnDefinition="datetime(6)") LocalDateTime expiresDate;
    @Column(name="proof_expires_at",columnDefinition="datetime(6)") LocalDateTime proofExpiresDate;
    @Column(name="sent_at",columnDefinition="datetime(6)") LocalDateTime sentDate;
    @Column(name="window_started_at",columnDefinition="datetime(6)") LocalDateTime windowStartedDate;
    @Column(name="request_count",nullable=false) int requestCount;
    @Column(name="attempts",nullable=false) int attempts;
    @Column(name="consumed",nullable=false) boolean consumed;
    protected EmailVerification() { }
    public EmailVerification(String email) {emailKey=email;}
    public String getCodeHash(){return codeHash;}
    public String getProofHash(){return proofHash;}
    public int getAttempts(){return attempts;}
}
