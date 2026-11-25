package com.danielhw99.auth;

import com.danielhw99.auth.email.EmailVerificationController;
import com.danielhw99.auth.email.EmailVerificationService;
import com.danielhw99.auth.security.SecurityConfiguration;
import com.danielhw99.auth.security.SecurityErrorWriter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.mockito.Mockito.*;

@WebMvcTest(EmailVerificationController.class)
@Import({SecurityConfiguration.class,com.danielhw99.auth.remember.RememberCookies.class,SecurityErrorWriter.class})
class EmailVerificationMvcTest {
 @org.springframework.test.context.bean.override.mockito.MockitoBean com.danielhw99.auth.remember.RememberLoginService remember;

    @Autowired MockMvc mvc;
    @MockitoBean EmailVerificationService service;
    @MockitoBean com.danielhw99.auth.security.AuthRateLimiter limiter;
    @MockitoBean com.danielhw99.user.repository.UserRepository users;
    @Test void publicButCsrfProtectedAndStrictlyValidated() throws Exception {
        mvc.perform(post("/api/v1/auth/email-verifications").contentType("application/json").content("{\"email\":\"u@example.com\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/email-verifications").with(csrf()).contentType("application/json").content("{\"email\":\"u@b..com\"}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
        when(service.request("u@example.com")).thenReturn(new EmailVerificationService.Sent(java.util.UUID.randomUUID().toString(),java.time.Instant.now().plusSeconds(600),60));
        mvc.perform(post("/api/v1/auth/email-verifications").with(csrf()).contentType("application/json").content("{\"email\":\"u@example.com\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.resendAfterSeconds").value(60));
    }
}
