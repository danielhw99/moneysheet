package com.danielhw99.auth.service;
import com.danielhw99.auth.dto.LoginInput;
import com.danielhw99.auth.security.AuthPrincipal;
import com.danielhw99.auth.security.AuthRateLimiter;
import com.danielhw99.common.error.ApiException;
import com.danielhw99.common.error.ErrorCode;
import com.danielhw99.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
@Service
public class LoginService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final String dummyHash;
    private final AuthRateLimiter limiter;
    public LoginService(UserRepository users, PasswordEncoder encoder, AuthRateLimiter limiter) {
        this.users=users; this.encoder=encoder;this.limiter=limiter;
        this.dummyHash=encoder.encode(java.util.UUID.randomUUID().toString());
    }
    public AuthPrincipal authenticate(LoginInput input) {
        if (input.password()==null || input.password().isEmpty()
                || input.password().codePointCount(0,input.password().length())>128) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        try (var attempt = limiter.beginAttempt(input.email())) {
            var user = users.findByEmailKey(input.email());
            boolean matches = encoder.matches(input.password(), user.map(u -> u.getPasswordHash()).orElse(dummyHash));
            if (!matches || user.isEmpty()) {
                attempt.failed();
                throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
            }
            AuthPrincipal principal = AuthPrincipal.from(user.orElseThrow());
            attempt.succeeded();
            return principal;
        }
    }
}
