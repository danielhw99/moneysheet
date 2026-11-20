package com.danielhw99.auth;

import com.danielhw99.auth.email.*;
import com.danielhw99.common.error.ApiException;
import com.danielhw99.common.error.ErrorCode;
import com.danielhw99.user.repository.UserRepository;
import com.danielhw99.auth.security.SecurityConfiguration;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmailVerificationServiceTest {
    final EmailVerificationRepository rows=mock(EmailVerificationRepository.class);
    final UserRepository users=mock(UserRepository.class);
    final VerificationMailSender mail=mock(VerificationMailSender.class);
    final MutableClock clock=new MutableClock();
    final String email="user@example.com";
    EmailVerification row;
    EmailVerificationService service;
    String code;
    @BeforeEach void setup() {
        row=new EmailVerification(email);
        when(rows.findLockedByEmailKey(email)).thenReturn(Optional.of(row));
        doAnswer(call -> {code=call.getArgument(1); return null;}).when(mail).send(eq(email),anyString());
        service=new EmailVerificationService(rows,users,new SecurityConfiguration().passwordEncoder(),mail,clock);
    }
    @Test void hashesCodeAndProofAndConsumesOnlyOnce() {
        var sent=service.request(email);
        assertThat(code).matches("[0-9]{6}");
        assertThat(row.getCodeHash()).doesNotContain(code).startsWith("{argon2id}");
        var proof=service.confirm(email,sent.challengeId(),code);
        assertThat(proof.verificationToken()).matches("[A-Za-z0-9_-]{43}");
        assertThat(row.getProofHash()).doesNotContain(proof.verificationToken());
        service.consume(email,proof.verificationToken());
        invalid(() -> service.consume(email,proof.verificationToken()));
    }
    @Test void rejectsUnverifiedMissingWrongAndExpiredProof() {
        invalid(() -> service.consume(email,null));
        var sent=service.request(email);
        invalid(() -> service.consume(email,"A".repeat(43)));
        var proof=service.confirm(email,sent.challengeId(),code);
        invalid(() -> service.consume(email,"A".repeat(43)));
        clock.advance(600);
        invalid(() -> service.consume(email,proof.verificationToken()));
    }
    @Test void fiveWrongCodesPersistentlyExhaustTheChallenge() {
        var sent=service.request(email);
        String wrong=code.equals("000000")?"000001":"000000";
        for(int i=0;i<5;i++) invalid(() -> service.confirm(email,sent.challengeId(),wrong));
        assertThat(row.getAttempts()).isEqualTo(5);
        invalid(() -> service.confirm(email,sent.challengeId(),code));
    }
    @Test void resendEnforcesCooldownAndInvalidatesOldChallengeAndProof() {
        var first=service.request(email);
        var proof=service.confirm(email,first.challengeId(),code);
        assertThatThrownBy(() -> service.request(email)).isInstanceOfSatisfying(ApiException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RATE_LIMITED));
        clock.advance(60);
        var second=service.request(email);
        assertThat(second.challengeId()).isNotEqualTo(first.challengeId());
        invalid(() -> service.confirm(email,first.challengeId(),code));
        invalid(() -> service.consume(email,proof.verificationToken()));
    }
    @Test void codeExpiresAtExactBoundaryAndEmailBudgetIsBounded() {
        var sent=service.request(email); clock.advance(600);
        invalid(() -> service.confirm(email,sent.challengeId(),code));
        for(int i=1;i<10;i++){ service.request(email); clock.advance(60); }
        assertThatThrownBy(() -> service.request(email)).isInstanceOfSatisfying(ApiException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RATE_LIMITED));
        clock.advance(3600); service.request(email);
    }
    @Test void neverSendsForRegisteredEmailOrTreatsMailFailureAsSuccess() {
        when(users.existsByEmailKey(email)).thenReturn(true);
        assertThatThrownBy(() -> service.request(email)).isInstanceOfSatisfying(ApiException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS));
        verifyNoInteractions(mail);
        when(users.existsByEmailKey(email)).thenReturn(false);
        doThrow(new IllegalStateException("provider credentials must never escape")).when(mail).send(anyString(),anyString());
        assertThatThrownBy(() -> service.request(email)).isInstanceOfSatisfying(ApiException.class,
            e -> {assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR);assertThat(e.getCause()).isNull();});
    }
    void invalid(Runnable action) { assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class,
        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION)); }
    static class MutableClock extends Clock {
        Instant now=Instant.parse("2026-11-20T00:00:00Z");
        void advance(long seconds){now=now.plusSeconds(seconds);}
        public Instant instant(){return now;}
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return this;}
    }
}
