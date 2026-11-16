package com.danielhw99.auth.security;
import com.danielhw99.user.entity.User;
import com.danielhw99.common.error.ApiException;
import com.danielhw99.common.error.ErrorCode;
import org.springframework.security.core.context.SecurityContextHolder;
public final class ActiveAccount {
 private ActiveAccount() { }
 // Call after acquiring the user lock, before reading any mutable business rows.
 public static User requireCurrent(User user) {
  var authentication=SecurityContextHolder.getContext().getAuthentication();
  if(authentication!=null && authentication.getPrincipal() instanceof AuthPrincipal principal
    && (!principal.userId().equals(user.getId()) || principal.sessionVersion()!=user.getSessionVersion()))
   throw new ApiException(ErrorCode.UNAUTHENTICATED);
  return user;
 }
}
