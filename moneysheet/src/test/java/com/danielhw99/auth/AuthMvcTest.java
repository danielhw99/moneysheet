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
@WebMvcTest({AuthController.class,com.danielhw99.auth.controller.MeController.class})
@Import({SecurityConfiguration.class,SecurityErrorWriter.class,ApiJsonConfiguration.class,TraceIdFilter.class})
class AuthMvcTest {
 @Autowired MockMvc mvc;
 @MockitoBean SignupService signup;
 @MockitoBean com.danielhw99.auth.security.AuthRateLimiter limiter;
 @MockitoBean com.danielhw99.user.repository.UserRepository users;
 @MockitoBean com.danielhw99.auth.service.LoginService login;
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

 @Test void loginRotatesSessionAndCsrfThenLogoutInvalidatesIt() throws Exception {
  var principal=new com.danielhw99.auth.security.AuthPrincipal(1L,"user@example.com","Nick");
  when(login.authenticate(any())).thenReturn(principal);
  var user=new com.danielhw99.user.entity.User("user@example.com","user@example.com","Nick","hash","v1",java.time.LocalDateTime.now());
  org.springframework.test.util.ReflectionTestUtils.setField(user,"id",1L);
  when(users.findById(1L)).thenReturn(java.util.Optional.of(user));
  var csrfResult=mvc.perform(get("/api/v1/auth/csrf")).andReturn();
  var session=(org.springframework.mock.web.MockHttpSession)csrfResult.getRequest().getSession(false);
  String oldId=session.getId();
  var oldToken=tools.jackson.databind.json.JsonMapper.builder().build().readTree(csrfResult.getResponse().getContentAsString()).get("token").asText();
  mvc.perform(post("/api/v1/auth/login").session(session).header("X-CSRF-TOKEN",oldToken)
    .contentType("application/json").content("{\"email\":\"user@example.com\",\"password\":\"valid long password!\"}"))
    .andExpect(status().isOk()).andExpect(jsonPath("$.id").value("1"));
  org.assertj.core.api.Assertions.assertThat(session.getId()).isNotEqualTo(oldId);
  mvc.perform(get("/api/v1/me").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.email").value("user@example.com"));
  mvc.perform(post("/api/v1/auth/logout").session(session).header("X-CSRF-TOKEN",oldToken))
    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
  var fresh=mvc.perform(get("/api/v1/auth/csrf").session(session)).andReturn();
  String token=tools.jackson.databind.json.JsonMapper.builder().build().readTree(fresh.getResponse().getContentAsString()).get("token").asText();
  mvc.perform(post("/api/v1/auth/logout").session(session).header("X-CSRF-TOKEN",token))
    .andExpect(status().isNoContent()).andExpect(content().string(""))
    .andExpect(header().string("Set-Cookie",org.hamcrest.Matchers.containsString("Max-Age=0")));
  org.assertj.core.api.Assertions.assertThat(session.isInvalid()).isTrue();
  mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
 }
 @Test void expiredAuthenticatedSessionIsRejected() throws Exception {
  var principal=new com.danielhw99.auth.security.AuthPrincipal(1L,"user@example.com","Nick");
  var context=org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
  context.setAuthentication(org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(principal,null,java.util.List.of()));
  var session=new org.springframework.mock.web.MockHttpSession();
  session.setAttribute(org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,context);
  session.setAttribute(com.danielhw99.auth.security.SessionExpiryFilter.LOGIN_AT,java.time.Instant.now().minusSeconds(43201));
  session.setAttribute(com.danielhw99.auth.security.SessionExpiryFilter.LAST_SEEN,java.time.Instant.now());
  mvc.perform(get("/api/v1/me").session(session)).andExpect(status().isUnauthorized());
  org.assertj.core.api.Assertions.assertThat(session.isInvalid()).isTrue();
 }
 @Test void loginFailureKeepsGenericErrorContract() throws Exception {
  when(login.authenticate(any())).thenThrow(new com.danielhw99.common.error.ApiException(com.danielhw99.common.error.ErrorCode.INVALID_CREDENTIALS));
  mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType("application/json")
   .content("{\"email\":\"user@example.com\",\"password\":\"incorrect\"}"))
   .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
 }

 @Test void rateFilterReturnsContractAndRetryAfter() throws Exception {
  doThrow(new com.danielhw99.common.error.ApiException(com.danielhw99.common.error.ErrorCode.RATE_LIMITED,60L)).when(limiter).request(anyString(),eq(false));
  mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType("application/json").content("{}"))
    .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After","60"))
    .andExpect(jsonPath("$.code").value("RATE_LIMITED")).andExpect(jsonPath("$.traceId").isNotEmpty());
 }
}
