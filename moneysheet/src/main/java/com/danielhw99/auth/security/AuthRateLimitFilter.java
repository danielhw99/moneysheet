package com.danielhw99.auth.security;
import java.io.IOException;
import com.danielhw99.common.error.ApiException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
public class AuthRateLimitFilter extends OncePerRequestFilter {
    private final AuthRateLimiter limiter;
    private final SecurityErrorWriter errors;
    public AuthRateLimitFilter(AuthRateLimiter limiter,SecurityErrorWriter errors) { this.limiter=limiter;this.errors=errors; }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        String path=request.getRequestURI().substring(request.getContextPath().length());
        if("POST".equals(request.getMethod()) && (path.equals("/api/v1/auth/login") || path.equals("/api/v1/auth/signup"))) {
            try { limiter.request(request.getRemoteAddr(),path.endsWith("signup")); }
            catch(ApiException limited) {
                response.setHeader("Retry-After",limited.getRetryAfterSeconds().toString());
                errors.write(request,response,limited.getErrorCode());return;
            }
        }
        chain.doFilter(request,response);
    }
}
