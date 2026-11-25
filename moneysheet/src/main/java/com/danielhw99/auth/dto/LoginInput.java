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
        @NotNull @Schema(accessMode=Schema.AccessMode.WRITE_ONLY,minLength=1,maxLength=128) String password,
        @Schema(defaultValue="false") boolean rememberMe) {
    @com.fasterxml.jackson.annotation.JsonCreator
    public static LoginInput fromJson(@com.fasterxml.jackson.annotation.JsonProperty("email") String email,
            @com.fasterxml.jackson.annotation.JsonProperty("password") String password,
            @com.fasterxml.jackson.annotation.JsonProperty("rememberMe") tools.jackson.databind.JsonNode rememberMe) {
        if(rememberMe!=null && !rememberMe.isBoolean())throw new IllegalArgumentException("rememberMe must be a boolean");
        return new LoginInput(email,password,rememberMe!=null && rememberMe.booleanValue());
    }
    public LoginInput(String email,String password){this(email,password,false);}
    public LoginInput { email=email==null?null:email.strip().toLowerCase(Locale.ROOT); }
    @Override public String toString() { return "LoginInput[REDACTED]"; }
}
