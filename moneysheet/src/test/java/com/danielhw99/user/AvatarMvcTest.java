package com.danielhw99.user;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import java.util.List;
import java.util.Optional;
import com.danielhw99.auth.dto.Profile;
import com.danielhw99.auth.security.*;
import com.danielhw99.common.config.ApiJsonConfiguration;
import com.danielhw99.common.web.TraceIdFilter;
import com.danielhw99.common.api.ApiId;
import com.danielhw99.user.api.AvatarController;
import com.danielhw99.user.repository.UserRepository;
import com.danielhw99.user.service.AvatarService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AvatarController.class)
@Import({SecurityConfiguration.class,com.danielhw99.auth.remember.RememberCookies.class, SecurityErrorWriter.class, ApiJsonConfiguration.class, TraceIdFilter.class})
class AvatarMvcTest {
 @org.springframework.test.context.bean.override.mockito.MockitoBean com.danielhw99.auth.remember.RememberLoginService remember;

    @Autowired MockMvc mvc;
    @MockitoBean AvatarService service;
    @MockitoBean UserRepository users;
    @MockitoBean AuthRateLimiter limiter;
    @BeforeEach void activeUser() {
        when(users.findById(7L)).thenAnswer(call -> {
            var user = mock(com.danielhw99.user.entity.User.class);
            when(user.getId()).thenReturn(7L);
            return Optional.of(user);
        });
    }
    @Test void servesOnlyTheAuthenticatedUsersImageWithPrivateHeaders() throws Exception {
        when(service.read(7L)).thenReturn(new byte[]{1, 2, 3});
        mvc.perform(get("/api/v1/me/avatar").session(session())).andExpect(status().isOk())
            .andExpect(content().contentType("image/jpeg")).andExpect(content().bytes(new byte[]{1,2,3}))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        mvc.perform(get("/api/v1/me/avatar")).andExpect(status().isUnauthorized());
    }
    @Test void writesRequireCsrfAndReturnTheUpdatedProfile() throws Exception {
        var file = new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1});
        var profile = new Profile(new ApiId(7L), "user@example.com", "User", "/api/v1/me/avatar?v=" + "a".repeat(32));
        when(service.save(eq(7L), any())).thenReturn(profile);
        when(service.delete(7L)).thenReturn(new Profile(new ApiId(7L), "user@example.com", "User"));
        mvc.perform(multipart("/api/v1/me/avatar").file(file).with(r -> {r.setMethod("PUT");return r;}).session(session()))
            .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/v1/me/avatar").file(file).with(r -> {r.setMethod("PUT");return r;}).session(session()).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.avatarUrl").value(profile.avatarUrl()));
        mvc.perform(delete("/api/v1/me/avatar").session(session()).with(csrf())).andExpect(status().isOk())
            .andExpect(jsonPath("$.avatarUrl").isEmpty());
    }
    @Test void missingFileHasTheFileFieldError() throws Exception {
        mvc.perform(multipart("/api/v1/me/avatar").with(r -> {r.setMethod("PUT");return r;}).session(session()).with(csrf()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("file"));
        verifyNoInteractions(service);
    }
    MockHttpSession session() {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(new AuthPrincipal(7L,"user@example.com","User"),null,List.of()));
        var session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,context);
        session.setAttribute(SessionExpiryFilter.LOGIN_AT, java.time.Instant.now());
        session.setAttribute(SessionExpiryFilter.LAST_SEEN, java.time.Instant.now());
        return session;
    }
}
