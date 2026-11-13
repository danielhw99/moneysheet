package com.danielhw99.auth.dto;
import java.util.Locale;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
public record LoginInput(
        @NotBlank @Email @Pattern(regexp="^[^\\s@]+@[^\\s@.]+(?:\\.[^\\s@.]+)+$") @Size(max=254) String email,
        @NotNull @Schema(accessMode=Schema.AccessMode.WRITE_ONLY) String password) {
    public LoginInput { email=email==null?null:email.strip().toLowerCase(Locale.ROOT); }
    @Override public String toString() { return "LoginInput[REDACTED]"; }
}
