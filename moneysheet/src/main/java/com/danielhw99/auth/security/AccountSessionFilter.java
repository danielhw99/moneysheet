package com.danielhw99.auth.security;
import java.io.IOException;
import com.danielhw99.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
public class AccountSessionFilter extends OncePerRequestFilter {
 private final UserRepository users;
 public AccountSessionFilter(UserRepository users) { this.users=users; }
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
  var auth=SecurityContextHolder.getContext().getAuthentication();
  if(auth!=null && auth.getPrincipal() instanceof AuthPrincipal principal) {
   var user=users.findById(principal.userId());
   if(user.isEmpty() || user.get().getSessionVersion()!=principal.sessionVersion()) {
    SecurityContextHolder.clearContext();
    var session=request.getSession(false);
    if(session!=null) try {session.invalidate();} catch(IllegalStateException alreadyInvalidated) { /* concurrent request */ }
   }
  }
  chain.doFilter(request,response);
 }
}
