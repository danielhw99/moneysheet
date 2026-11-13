package com.danielhw99.auth.dto;

import java.util.Locale;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

public record SignupInput(
        @NotBlank @Email @Pattern(regexp = "^[^\\s@]+@[^\\s@.]+(?:\\.[^\\s@.]+)+$") @Size(max=254) String email,
        @NotBlank @Size(max=50) String nickname,
        @NotNull @Schema(accessMode=Schema.AccessMode.WRITE_ONLY,minLength=15,maxLength=128) String password,
        @NotBlank @Size(max=50) String privacyPolicyVersion,
        @NotNull @AssertTrue Boolean privacyAgreed) {
    public SignupInput {
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
        nickname = nickname == null ? null : nickname.strip();
    }
    @Override public String toString() { return "SignupInput[REDACTED]"; }
}
