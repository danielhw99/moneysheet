package com.danielhw99.auth.security;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
public class SessionExpiryFilter extends OncePerRequestFilter {
    public static final String LOGIN_AT=SessionExpiryFilter.class.getName()+".loginAt";
    public static final String LAST_SEEN=SessionExpiryFilter.class.getName()+".lastSeen";
    private final Clock clock;
    public SessionExpiryFilter(Clock clock) { this.clock=clock; }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        var session=request.getSession(false);
        if(session!=null && auth!=null && auth.getPrincipal() instanceof AuthPrincipal) {
            try {
                Instant now=clock.instant();
                Object login=session.getAttribute(LOGIN_AT), seen=session.getAttribute(LAST_SEEN);
                if(SessionWindow.expired(login instanceof Instant i?i:null,seen instanceof Instant i?i:null,now)) {
                    session.invalidate();SecurityContextHolder.clearContext();
                } else session.setAttribute(LAST_SEEN,now);
            } catch (IllegalStateException invalidated) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request,response);
    }
}
