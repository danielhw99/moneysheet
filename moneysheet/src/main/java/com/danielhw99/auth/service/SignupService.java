package com.danielhw99.auth.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import com.danielhw99.auth.dto.Profile;
import com.danielhw99.auth.dto.SignupInput;
import com.danielhw99.common.error.ApiException;
import com.danielhw99.common.error.ErrorCode;
import com.danielhw99.user.entity.User;
import com.danielhw99.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SignupService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final PasswordPolicy policy;
    private final Clock clock;
    private final String policyVersion;
    private final com.danielhw99.auth.email.EmailVerificationService verification;
    public SignupService(UserRepository users, PasswordEncoder encoder, PasswordPolicy policy, Clock clock,
            @Value("${moneysheet.auth.privacy-policy-version:v1}") String policyVersion,
            com.danielhw99.auth.email.EmailVerificationService verification) {
        this.users=users; this.encoder=encoder; this.policy=policy; this.clock=clock; this.policyVersion=policyVersion;
        this.verification=verification;
    }
    @Transactional
    public Profile signup(SignupInput input) {
        policy.validate(input.password());
        if (!Boolean.TRUE.equals(input.privacyAgreed()) || !policyVersion.equals(input.privacyPolicyVersion())) {
            throw new ApiException(ErrorCode.VALIDATION);
        }
        if (users.existsByEmailKey(input.email())) throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS);
        if (input.nickname() == null || input.nickname().isBlank()
                || input.nickname().codePointCount(0, input.nickname().length()) > 50) throw new ApiException(ErrorCode.VALIDATION);
        if (users.countNicknameConflicts(input.nickname(), 0L) > 0) throw com.danielhw99.user.service.UserUniqueConflict.nickname();
        verification.consume(input.email(),input.verificationToken());
        User user = new User(input.email(), input.email(), input.nickname(), encoder.encode(input.password()),
                policyVersion, LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        user.verifyEmail();
        try {
            return Profile.from(users.saveAndFlush(user));
        } catch (DataIntegrityViolationException duplicate) {
            throw com.danielhw99.user.service.UserUniqueConflict.translate(duplicate);
        }
    }
}
