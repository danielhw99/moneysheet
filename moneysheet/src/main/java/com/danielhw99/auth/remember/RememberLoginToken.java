package com.danielhw99.auth.remember;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.security.MessageDigest;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import com.danielhw99.user.entity.User;
@Entity @Table(name="remember_login_tokens")
public class RememberLoginToken {
 @Id @Column(columnDefinition="binary(16)") private byte[] selector;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="user_id",nullable=false) private User user;
 @Column(name="validator_hash",nullable=false,columnDefinition="binary(32)") private byte[] validatorHash;
 @Column(name="previous_validator_hash",columnDefinition="binary(32)") private byte[] previousValidatorHash;
 @Column(name="previous_valid_until",columnDefinition="datetime(6)") private LocalDateTime previousValidUntil;
 @Column(name="session_version",nullable=false) private long sessionVersion;
 @Column(name="issued_at",nullable=false,columnDefinition="datetime(6)") private LocalDateTime issuedAt;
 @Column(name="rotated_at",columnDefinition="datetime(6)") private LocalDateTime rotatedAt;
 @Column(name="expires_at",nullable=false,columnDefinition="datetime(6)") private LocalDateTime expiresAt;
 @Column(name="revoked_at",columnDefinition="datetime(6)") private LocalDateTime revokedAt;
 protected RememberLoginToken(){}
 public RememberLoginToken(User user,RememberProof proof,Instant now){this.user=user;selector=proof.selector();validatorHash=proof.hash();sessionVersion=user.getSessionVersion();issuedAt=utc(now);expiresAt=utc(now.plusSeconds(30L*86400));}
 private static LocalDateTime utc(Instant value){return LocalDateTime.ofInstant(value,ZoneOffset.UTC);}
 public byte[] getSelector(){return selector.clone();}
 public User getUser(){return user;}
 public long getSessionVersion(){return sessionVersion;}
 public Instant expiresAt(){return expiresAt.toInstant(ZoneOffset.UTC);}
 public boolean active(Instant now,long version){return revokedAt==null && sessionVersion==version && utc(now).isBefore(expiresAt);}
 public boolean matches(RememberProof proof){return MessageDigest.isEqual(selector,proof.selector())&&MessageDigest.isEqual(validatorHash,proof.hash());}
 public boolean previousMatches(RememberProof proof){return MessageDigest.isEqual(selector,proof.selector())&&previousValidatorHash!=null&&MessageDigest.isEqual(previousValidatorHash,proof.hash());}
 public boolean previousWithinGrace(Instant now){return previousValidUntil!=null&&utc(now).isBefore(previousValidUntil);}
 public void rotate(RememberProof next,Instant now){previousValidatorHash=validatorHash;previousValidUntil=utc(now.plusSeconds(10));validatorHash=next.hash();rotatedAt=utc(now);}
 public void revoke(Instant now){revokedAt=utc(now);}
 @Override public String toString(){return "RememberLoginToken[REDACTED]";}
}
