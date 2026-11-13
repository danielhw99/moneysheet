package com.danielhw99.auth;

import com.danielhw99.auth.security.AuthRateLimitFilter;
import com.danielhw99.auth.security.AuthRateLimiter;
import com.danielhw99.auth.security.SecurityErrorWriter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.mockito.Mockito.*;

class AuthRateLimitFilterTest {
    @Test void encodedAuthPathsUseTheSameBucketsAsPlainPaths() throws Exception {
        for (String path : new String[]{"/api/v1/auth/login", "/api/v1/auth/%6cogin",
                "/api/v1/auth/signup", "/api/v1/auth/%73ignup"}) {
            var limiter=mock(AuthRateLimiter.class);
            var filter=new AuthRateLimitFilter(limiter,mock(SecurityErrorWriter.class));
            var request=new MockHttpServletRequest("POST",path);
            var response=new MockHttpServletResponse();
            var chain=mock(FilterChain.class);
            filter.doFilter(request,response,chain);
            verify(limiter).request(request.getRemoteAddr(),path.endsWith("ignup"));
            verify(chain).doFilter(request,response);
        }
    }
    @Test void supportsContextPathAndIgnoresUnrelatedRequests() throws Exception {
        var limiter=mock(AuthRateLimiter.class);
        var filter=new AuthRateLimitFilter(limiter,mock(SecurityErrorWriter.class));
        var request=new MockHttpServletRequest("POST","/app/api/v1/auth/%73ignup");
        request.setContextPath("/app");
        filter.doFilter(request,new MockHttpServletResponse(),mock(FilterChain.class));
        verify(limiter).request(request.getRemoteAddr(),true);
        clearInvocations(limiter);
        filter.doFilter(new MockHttpServletRequest("GET","/api/v1/auth/signup"),new MockHttpServletResponse(),mock(FilterChain.class));
        filter.doFilter(new MockHttpServletRequest("POST","/api/v1/auth/other"),new MockHttpServletResponse(),mock(FilterChain.class));
        verifyNoInteractions(limiter);
    }
}
