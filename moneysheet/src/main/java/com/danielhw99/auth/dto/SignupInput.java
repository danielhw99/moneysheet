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
        @NotBlank @Size(max=50) @Schema(description="앞뒤 공백 제거, 대소문자를 구분하지 않고 중복 거부. 중복 시 409 CONFLICT와 nickname 필드 오류") String nickname,
        @NotNull @Schema(accessMode=Schema.AccessMode.WRITE_ONLY,minLength=10,maxLength=20) String password,
        @NotBlank @Size(max=50) String privacyPolicyVersion,
        @NotNull @AssertTrue Boolean privacyAgreed,
        @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}") @Schema(accessMode=Schema.AccessMode.WRITE_ONLY) String verificationToken) {
    public SignupInput(String email,String nickname,String password,String privacyPolicyVersion,Boolean privacyAgreed) {
        this(email,nickname,password,privacyPolicyVersion,privacyAgreed,null);
    }
    public SignupInput {
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
        nickname = nickname == null ? null : nickname.strip();
    }
    @Override public String toString() { return "SignupInput[REDACTED]"; }
}
