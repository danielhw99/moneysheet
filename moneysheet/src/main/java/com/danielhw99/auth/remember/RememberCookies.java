package com.danielhw99.auth.remember;
import java.time.Clock;
import java.time.Duration;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
@Component
public class RememberCookies {
 public static final String NAME="MS_REMEMBER";
 private final boolean secure;private final Clock clock;
 public RememberCookies(@Value("${server.servlet.session.cookie.secure:true}") boolean secure,Clock clock){this.secure=secure;this.clock=clock;}
 public String read(HttpServletRequest request){String found=null;boolean seen=false;if(request.getCookies()!=null)for(Cookie cookie:request.getCookies())if(NAME.equals(cookie.getName())){if(seen)return null;seen=true;found=cookie.getValue();}return found;}
 public void write(HttpServletResponse response,RememberGrant grant){
  long seconds=grant.cookieValue()==null?0:Math.max(0,Duration.between(clock.instant(),grant.expiresAt()).getSeconds());
  response.addHeader(HttpHeaders.SET_COOKIE,ResponseCookie.from(NAME,grant.cookieValue()==null?"":grant.cookieValue()).path("/").httpOnly(true).secure(secure).sameSite("Lax").maxAge(seconds).build().toString());
 }
}
