package com.danielhw99.auth.security;
import java.io.IOException;
import com.danielhw99.common.error.ApiException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
public class AuthRateLimitFilter extends OncePerRequestFilter {
    private static final RequestMatcher LOGIN = PathPatternRequestMatcher.withDefaults()
            .matcher(HttpMethod.POST,"/api/v1/auth/login");
    private static final RequestMatcher SIGNUP = PathPatternRequestMatcher.withDefaults()
            .matcher(HttpMethod.POST,"/api/v1/auth/signup");
    private final AuthRateLimiter limiter;
    private final SecurityErrorWriter errors;
    public AuthRateLimitFilter(AuthRateLimiter limiter,SecurityErrorWriter errors) { this.limiter=limiter;this.errors=errors; }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        boolean signup=SIGNUP.matches(request);
        if(signup || LOGIN.matches(request)) {
            try { limiter.request(request.getRemoteAddr(),signup); }
            catch(ApiException limited) {
                response.setHeader("Retry-After",limited.getRetryAfterSeconds().toString());
                errors.write(request,response,limited.getErrorCode());return;
            }
        }
        chain.doFilter(request,response);
    }
}
