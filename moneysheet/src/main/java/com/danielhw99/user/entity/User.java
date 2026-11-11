package com.danielhw99.user.entity;

import java.time.LocalDateTime;

import com.danielhw99.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "email_key", nullable = false, length = 254)
    private String emailKey;

    @Column(name = "nickname", nullable = false, length = 50)
    private String nickname;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "privacy_policy_version", nullable = false, length = 50)
    private String privacyPolicyVersion;

    @Column(name = "privacy_agreed_at", nullable = false, columnDefinition = "datetime(6)")
    private LocalDateTime privacyAgreedDate;

    protected User() {
    }

    public User(
            String email,
            String emailKey,
            String nickname,
            String passwordHash,
            String privacyPolicyVersion,
            LocalDateTime privacyAgreedDate) {
        this.email = email;
        this.emailKey = emailKey;
        this.nickname = nickname;
        this.passwordHash = passwordHash;
        this.privacyPolicyVersion = privacyPolicyVersion;
        this.privacyAgreedDate = privacyAgreedDate;
    }

    public String getEmail() {
        return email;
    }

    public String getEmailKey() {
        return emailKey;
    }

    public String getNickname() {
        return nickname;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getPrivacyPolicyVersion() {
        return privacyPolicyVersion;
    }

    public LocalDateTime getPrivacyAgreedDate() {
        return privacyAgreedDate;
    }
}
