package com.danielhw99.auth;

import com.danielhw99.auth.service.PasswordPolicy;
import com.danielhw99.common.error.ApiException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PasswordPolicyTest {
    private final PasswordPolicy policy = new PasswordPolicy();

    @Test void acceptsUnicodeAndSpacesWithoutChangingPassword() {
        assertThatCode(() -> policy.validate("  spaced phrase!  ")).doesNotThrowAnyException();
        assertThatCode(() -> policy.validate("\uD83D\uDE00".repeat(10))).doesNotThrowAnyException();
    }
    @Test void rejectsShortLongAndCommonPasswords() {
        assertThatThrownBy(() -> policy.validate("x".repeat(9))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> policy.validate("\uD83D\uDE00".repeat(21))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> policy.validate("passwordpassword")).isInstanceOf(ApiException.class);
    }
    @Test void acceptsBothLengthBoundaries() {
        assertThatCode(() -> policy.validate("x".repeat(10))).doesNotThrowAnyException();
        assertThatCode(() -> policy.validate("\uD83D\uDE00".repeat(20))).doesNotThrowAnyException();
    }
}
