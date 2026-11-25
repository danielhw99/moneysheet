package com.danielhw99.auth.remember;
import java.time.Instant;
import com.danielhw99.auth.security.AuthPrincipal;
public record RememberGrant(AuthPrincipal principal,String cookieValue,Instant expiresAt){
 @Override public String toString(){return "RememberGrant[REDACTED]";}
}
