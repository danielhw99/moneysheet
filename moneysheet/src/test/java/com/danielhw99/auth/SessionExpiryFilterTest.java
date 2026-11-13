package com.danielhw99.auth;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import com.danielhw99.auth.security.AuthPrincipal;
import com.danielhw99.auth.security.SessionExpiryFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SessionExpiryFilterTest {
    private final Instant now=Instant.parse("2026-11-13T00:00:00Z");
    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }
    private void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new AuthPrincipal(1L,"u@example.com","n"),null,List.of()));
    }
    @ParameterizedTest
    @ValueSource(strings={"read","write","invalidate"})
    void concurrentInvalidationClearsAuthenticationAndContinuesOnce(String stage) throws Exception {
        authenticate();
        var session=mock(HttpSession.class);
        when(session.getAttribute(SessionExpiryFilter.LOGIN_AT)).thenReturn(now);
        when(session.getAttribute(SessionExpiryFilter.LAST_SEEN)).thenReturn(now);
        if(stage.equals("read")) when(session.getAttribute(SessionExpiryFilter.LOGIN_AT)).thenThrow(new IllegalStateException());
        if(stage.equals("write")) doThrow(new IllegalStateException()).when(session).setAttribute(SessionExpiryFilter.LAST_SEEN,now);
        if(stage.equals("invalidate")) {
            when(session.getAttribute(SessionExpiryFilter.LOGIN_AT)).thenReturn(now.minusSeconds(43200));
            doThrow(new IllegalStateException()).when(session).invalidate();
        }
        var request=new MockHttpServletRequest();request.setSession(session);
        var response=new MockHttpServletResponse();var chain=mock(FilterChain.class);
        doAnswer(call->{assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();return null;})
                .when(chain).doFilter(request,response);
        new SessionExpiryFilter(Clock.fixed(now,ZoneOffset.UTC)).doFilter(request,response,chain);
        verify(chain,times(1)).doFilter(request,response);
    }
    @Test void activeSessionRefreshesLastSeenAndKeepsAuthentication() throws Exception {
        authenticate();var session=mock(HttpSession.class);
        when(session.getAttribute(SessionExpiryFilter.LOGIN_AT)).thenReturn(now.minusSeconds(100));
        when(session.getAttribute(SessionExpiryFilter.LAST_SEEN)).thenReturn(now.minusSeconds(10));
        var request=new MockHttpServletRequest();request.setSession(session);
        new SessionExpiryFilter(Clock.fixed(now,ZoneOffset.UTC)).doFilter(request,new MockHttpServletResponse(),mock(FilterChain.class));
        verify(session).setAttribute(SessionExpiryFilter.LAST_SEEN,now);verify(session,never()).invalidate();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }
}
