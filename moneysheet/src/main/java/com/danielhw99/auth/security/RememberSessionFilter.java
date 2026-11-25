package com.danielhw99.auth.security;
import java.io.IOException;
import com.danielhw99.auth.remember.RememberLoginService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
/** Revoked/expired families cannot keep an otherwise current session alive. */
public class RememberSessionFilter extends OncePerRequestFilter {
 private final RememberLoginService remember;
 public RememberSessionFilter(RememberLoginService remember){this.remember=remember;}
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
  var auth=SecurityContextHolder.getContext().getAuthentication();
  if(auth!=null&&auth.getPrincipal() instanceof AuthPrincipal principal&&principal.rememberSelector()!=null&&!remember.sessionValid(principal)){
   SecurityContextHolder.clearContext();
   var session=request.getSession(false);
   if(session!=null)try{session.invalidate();}catch(IllegalStateException concurrentInvalidation){/* Another request already ended it. */}
  }
  chain.doFilter(request,response);
 }
}
