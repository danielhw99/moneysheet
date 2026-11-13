package com.danielhw99.auth;

import com.danielhw99.auth.service.PasswordPolicy;
import com.danielhw99.common.error.ApiException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PasswordPolicyTest {
    private final PasswordPolicy policy = new PasswordPolicy();

    @Test void acceptsUnicodeAndSpacesWithoutChangingPassword() {
        assertThatCode(() -> policy.validate("  correct horse battery staple  ")).doesNotThrowAnyException();
        assertThatCode(() -> policy.validate("\uD83D\uDE00".repeat(15))).doesNotThrowAnyException();
    }
    @Test void rejectsShortLongAndCommonPasswords() {
        assertThatThrownBy(() -> policy.validate("x".repeat(14))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> policy.validate("\uD83D\uDE00".repeat(129))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> policy.validate("passwordpassword")).isInstanceOf(ApiException.class);
    }
    @Test void acceptsBothLengthBoundaries() {
        assertThatCode(() -> policy.validate("x".repeat(15))).doesNotThrowAnyException();
        assertThatCode(() -> policy.validate("\uD83D\uDE00".repeat(128))).doesNotThrowAnyException();
    }
}
