package com.danielhw99.auth.service;

import java.util.Locale;
import java.util.Set;
import com.danielhw99.common.error.ApiException;
import com.danielhw99.common.error.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class PasswordPolicy {
    // Initial local blocklist; never send passwords to a third-party service.
    private static final Set<String> COMMON = Set.of("passwordpassword", "123456789012345",
            "1234567890123456", "qwertyuiopasdfgh", "correct horse battery staple",
            "letmeinletmeinletmein", "password123456789");

    public void validate(String password) {
        if (password == null || password.codePointCount(0, password.length()) < 15
                || password.codePointCount(0, password.length()) > 128
                || COMMON.contains(password.toLowerCase(Locale.ROOT))) {
            throw new ApiException(ErrorCode.VALIDATION);
        }
    }
}
