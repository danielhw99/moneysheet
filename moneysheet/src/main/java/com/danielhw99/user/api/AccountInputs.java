package com.danielhw99.user.api;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
public final class AccountInputs {
 private AccountInputs() { }
 public record Nickname(@NotNull @Schema(minLength=1,maxLength=50) String nickname) { }
 public record Password(@NotNull @Schema(accessMode=Schema.AccessMode.WRITE_ONLY,minLength=1,maxLength=128) String currentPassword,
   @NotNull @Schema(accessMode=Schema.AccessMode.WRITE_ONLY,minLength=15,maxLength=128) String newPassword) {
  @Override public String toString(){return "Password[REDACTED]";}
 }
 public record Deletion(@NotNull @Schema(accessMode=Schema.AccessMode.WRITE_ONLY,minLength=1,maxLength=128) String currentPassword) {
  @Override public String toString(){return "Deletion[REDACTED]";}
 }
}
