package com.danielhw99.auth.email;

import com.danielhw99.common.error.ApiException;
import com.danielhw99.common.error.ErrorCode;
import com.danielhw99.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailVerificationService {
    public record Sent(String challengeId,java.time.Instant expiresAt,int resendAfterSeconds) { }
    public record Verified(String verificationToken,java.time.Instant expiresAt) {
        @Override public String toString(){return "Verified[REDACTED]";}
    }
    private final EmailVerificationRepository rows;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final VerificationMailSender mail;
    private final Clock clock;
    private final SecureRandom random=new SecureRandom();
    public EmailVerificationService(EmailVerificationRepository rows,UserRepository users,PasswordEncoder encoder,
            VerificationMailSender mail,Clock clock) {
        this.rows=rows;this.users=users;this.encoder=encoder;this.mail=mail;this.clock=clock;
    }
    @Transactional
    public Sent request(String rawEmail) {
        String email=normalize(rawEmail);
        rows.ensure(email);
        var row=rows.findLockedByEmailKey(email).orElseThrow();
        if(users.existsByEmailKey(email)) throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS);
        var now=now();
        if(row.sentDate!=null && now.isBefore(row.sentDate.plusSeconds(60)))
            limited(java.time.Duration.between(now,row.sentDate.plusSeconds(60)).toSeconds()+1);
        if(row.windowStartedDate==null || !now.isBefore(row.windowStartedDate.plusHours(1))) {
            row.windowStartedDate=now;row.requestCount=0;
        }
        if(row.requestCount>=10) limited(java.time.Duration.between(now,row.windowStartedDate.plusHours(1)).toSeconds()+1);
        String code=String.format(Locale.ROOT,"%06d",random.nextInt(1_000_000));
        // A slow salted password hash prevents offline enumeration of the short numeric code.
        String codeHash=encoder.encode(code);
        try {mail.send(email,code);}
        catch(RuntimeException failure) {
            // Provider exceptions may contain credentials or message contents. Never propagate or log them.
            throw new ApiException(ErrorCode.INTERNAL_ERROR);
        }
        row.challengeId=UUID.randomUUID().toString();row.codeHash=codeHash;
        row.expiresDate=now.plusMinutes(10);row.sentDate=now;row.requestCount++;
        row.attempts=0;row.consumed=false;row.proofHash=null;row.proofExpiresDate=null;
        return new Sent(row.challengeId,row.expiresDate.toInstant(ZoneOffset.UTC),60);
    }
    // Wrong guesses must commit their attempt count even though the HTTP result is an error.
    @Transactional(noRollbackFor=ApiException.class)
    public Verified confirm(String rawEmail,String challengeId,String code) {
        var row=rows.findLockedByEmailKey(normalize(rawEmail)).orElseThrow(EmailVerificationService::invalidCode);
        if(row.consumed || row.codeHash==null || row.attempts>=5 || row.expiresDate==null
                || !now().isBefore(row.expiresDate) || !java.util.Objects.equals(row.challengeId,challengeId)) throw invalidCode();
        if(code==null || !code.matches("[0-9]{6}") || !encoder.matches(code,row.codeHash)) {
            row.attempts++;throw invalidCode();
        }
        byte[] bytes=new byte[32];random.nextBytes(bytes);
        String proof=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        row.proofHash=hash(proof);row.proofExpiresDate=now().plusMinutes(10);
        return new Verified(proof,row.proofExpiresDate.toInstant(ZoneOffset.UTC));
    }
    @Transactional
    public void consume(String rawEmail,String token) {
        if(token==null || !token.matches("[A-Za-z0-9_-]{43}")) throw invalidProof();
        var row=rows.findLockedByEmailKey(normalize(rawEmail)).orElseThrow(EmailVerificationService::invalidProof);
        if(row.consumed || row.proofHash==null || row.proofExpiresDate==null || !now().isBefore(row.proofExpiresDate)
                || !MessageDigest.isEqual(hash(token).getBytes(StandardCharsets.US_ASCII),row.proofHash.getBytes(StandardCharsets.US_ASCII))) throw invalidProof();
        row.consumed=true;row.codeHash=null;row.proofHash=null;
    }
    @Scheduled(fixedDelay=3_600_000,initialDelay=3_600_000)
    @Transactional
    public void purgeExpired(){rows.deleteExpired(now().minusDays(1));}
    private LocalDateTime now(){return LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC);}
    private static String normalize(String email){return email.strip().toLowerCase(Locale.ROOT);}
    private static void limited(long seconds){throw new ApiException(ErrorCode.RATE_LIMITED,Math.max(1,seconds));}
    private static ApiException invalidCode(){return new ApiException(ErrorCode.VALIDATION,"code","인증번호가 올바르지 않거나 만료됐어요. 새 번호를 요청해 주세요.");}
    private static ApiException invalidProof(){return new ApiException(ErrorCode.VALIDATION,"verificationToken","이메일 인증을 다시 진행해 주세요.");}
    private static String hash(String value) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
}
