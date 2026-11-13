package com.danielhw99.auth;
import com.danielhw99.auth.controller.AuthController;
import com.danielhw99.auth.service.SignupService;
import com.danielhw99.auth.security.SecurityConfiguration;
import com.danielhw99.auth.security.SecurityErrorWriter;
import com.danielhw99.common.config.ApiJsonConfiguration;
import com.danielhw99.common.web.TraceIdFilter;
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
@WebMvcTest(AuthController.class)
@Import({SecurityConfiguration.class,SecurityErrorWriter.class,ApiJsonConfiguration.class,TraceIdFilter.class})
class AuthMvcTest {
 @Autowired MockMvc mvc;
 @MockitoBean SignupService signup;
 @Test void suppliesCsrfAndCommonUnauthorizedResponse() throws Exception {
  mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty()).andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"));
  mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED")).andExpect(jsonPath("$.fieldErrors").isArray()).andExpect(jsonPath("$.traceId").isNotEmpty());
 }
 @Test void rejectsMissingAndInvalidCsrf() throws Exception {
  mvc.perform(post("/api/v1/auth/signup").contentType("application/json").content("{}"))
   .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
  mvc.perform(post("/api/v1/auth/signup").with(csrf().useInvalidToken()).contentType("application/json").content("{}"))
   .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
  verifyNoInteractions(signup);
 }
 @Test void rejectsInvalidSignupAndUnknownProperty() throws Exception {
  mvc.perform(post("/api/v1/auth/signup").with(csrf()).contentType("application/json").content("{}"))
   .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION"));
  mvc.perform(post("/api/v1/auth/signup").with(csrf()).contentType("application/json").content("{\"extra\":1}"))
   .andExpect(status().isBadRequest());
  verifyNoInteractions(signup);
 }
}
