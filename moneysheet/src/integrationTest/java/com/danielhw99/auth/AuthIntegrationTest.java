package com.danielhw99.auth;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import com.danielhw99.auth.dto.SignupInput;
import com.danielhw99.auth.service.SignupService;
import com.danielhw99.common.error.ApiException;
import com.danielhw99.common.error.ErrorCode;
import com.danielhw99.user.repository.UserRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.testcontainers.mysql.MySQLContainer;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={"spring.jpa.hibernate.ddl-auto=validate","spring.flyway.enabled=false","server.servlet.session.cookie.secure=false"})
@AutoConfigureMockMvc
@ContextConfiguration(initializers=AuthIntegrationTest.Database.class)
class AuthIntegrationTest {
 static final MySQLContainer MYSQL=new MySQLContainer("mysql:8.4.13").withDatabaseName("moneysheet_test")
   .withCreateContainerCmdModifier(command -> command.getHostConfig().withPortBindings(
      new com.github.dockerjava.api.model.PortBinding(
       com.github.dockerjava.api.model.Ports.Binding.bindIp("127.0.0.1"),
       com.github.dockerjava.api.model.ExposedPort.tcp(3306))));
 static class Database implements ApplicationContextInitializer<ConfigurableApplicationContext> {
  public void initialize(ConfigurableApplicationContext context) {
   MYSQL.start();
   Flyway.configure().dataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword())
     .cleanDisabled(true).createSchemas(false).baselineOnMigrate(false).load().migrate();
   TestPropertyValues.of("spring.datasource.url="+MYSQL.getJdbcUrl(),"spring.datasource.username="+MYSQL.getUsername(),
    "spring.datasource.password="+MYSQL.getPassword(),"spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver").applyTo(context);
  }
 }
 @org.springframework.beans.factory.annotation.Value("${local.server.port}") int port;
 @Autowired MockMvc mvc;
 @Autowired JsonMapper mapper;
 @Autowired UserRepository users;
 @Autowired SignupService signup;
 @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

 @Test void signupLoginOwnProfileAndLogoutAgainstRealMysql() throws Exception {
  var csrf=mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
  var session=(MockHttpSession)csrf.getRequest().getSession(false);
  String token=mapper.readTree(csrf.getResponse().getContentAsString()).get("token").asText();
  String payload="{\"email\":\" Integration@Example.COM \",\"nickname\":\" Nick \",\"password\":\"a unique long password!\",\"privacyPolicyVersion\":\"v1\",\"privacyAgreed\":true}";
  mvc.perform(post("/api/v1/auth/signup").session(session).header("X-CSRF-TOKEN",token).contentType("application/json").content(payload))
    .andExpect(status().isCreated()).andExpect(jsonPath("$.email").value("integration@example.com"));
  mvc.perform(get("/api/v1/me").session(session)).andExpect(status().isUnauthorized());
  assertThat(users.findByEmailKey("integration@example.com").orElseThrow().getPasswordHash()).startsWith("{argon2id}");
  mvc.perform(post("/api/v1/auth/signup").session(session).header("X-CSRF-TOKEN",token).contentType("application/json").content(payload))
    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
  String before=session.getId();
  mvc.perform(post("/api/v1/auth/login").session(session).header("X-CSRF-TOKEN",token).contentType("application/json")
    .content("{\"email\":\"integration@example.com\",\"password\":\"a unique long password!\"}"))
    .andExpect(status().isOk());
  assertThat(session.getId()).isNotEqualTo(before);
  mvc.perform(get("/api/v1/me").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.nickname").value("Nick"));
  var fresh=mvc.perform(get("/api/v1/auth/csrf").session(session)).andReturn();
  String freshToken=mapper.readTree(fresh.getResponse().getContentAsString()).get("token").asText();
  mvc.perform(post("/api/v1/auth/logout").session(session).header("X-CSRF-TOKEN",freshToken)).andExpect(status().isNoContent());
  mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1",Long.class)).isEqualTo(1);
 }

 @Test void concurrentSignupPreservesUniqueEmail() throws Exception {
  var input=new SignupInput("race@example.com","race","a concurrent password!","v1",true);
  var start=new CountDownLatch(1);
  Callable<String> action=()->{start.await();try{signup.signup(input);return "created";}catch(ApiException e){return e.getErrorCode().name();}};
  try(var executor=Executors.newFixedThreadPool(2)) {
   var one=executor.submit(action);var two=executor.submit(action);start.countDown();
   assertThat(List.of(one.get(30,TimeUnit.SECONDS),two.get(30,TimeUnit.SECONDS))).containsExactlyInAnyOrder("created",ErrorCode.EMAIL_ALREADY_EXISTS.name());
  }
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email_key='race@example.com'",Long.class)).isEqualTo(1);
 }

 @Test void accountLimitIsEnforcedInHttpFlow() throws Exception {
  var result=mvc.perform(get("/api/v1/auth/csrf")).andReturn();
  var session=(MockHttpSession)result.getRequest().getSession(false);
  String token=mapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
  for(int i=0;i<5;i++) mvc.perform(post("/api/v1/auth/login").session(session).header("X-CSRF-TOKEN",token)
    .contentType("application/json").content("{\"email\":\"absent@example.com\",\"password\":\"wrong\"}"))
    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  mvc.perform(post("/api/v1/auth/login").session(session).header("X-CSRF-TOKEN",token)
    .contentType("application/json").content("{\"email\":\"absent@example.com\",\"password\":\"wrong\"}"))
    .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
 }

 @Test void realHttpCookiesRotateAndLogoutClearsAuthentication() throws Exception {
  var cookies=new java.net.CookieManager(null,java.net.CookiePolicy.ACCEPT_ALL);
  try(var client=java.net.http.HttpClient.newBuilder().cookieHandler(cookies).build()) {
   String base="http://127.0.0.1:"+port;
   var csrf=client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+"/api/v1/auth/csrf")).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());
   assertThat(csrf.statusCode()).isEqualTo(200);
   assertThat(csrf.headers().firstValue("set-cookie").orElseThrow()).contains("HttpOnly","SameSite=Lax");
   String before=cookies.getCookieStore().getCookies().stream().filter(c->c.getName().equals("JSESSIONID")).findFirst().orElseThrow().getValue();
   String token=mapper.readTree(csrf.body()).get("token").asText();
   String signupBody="{\"email\":\"http@example.com\",\"nickname\":\"HTTP\",\"password\":\"http integration password!\",\"privacyPolicyVersion\":\"v1\",\"privacyAgreed\":true}";
   var signupResponse=client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+"/api/v1/auth/signup"))
     .header("Content-Type","application/json").header("X-CSRF-TOKEN",token).POST(java.net.http.HttpRequest.BodyPublishers.ofString(signupBody)).build(),java.net.http.HttpResponse.BodyHandlers.ofString());
   assertThat(signupResponse.statusCode()).isEqualTo(201);
   var loginResponse=client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+"/api/v1/auth/login"))
     .header("Content-Type","application/json").header("X-CSRF-TOKEN",token).POST(java.net.http.HttpRequest.BodyPublishers.ofString("{\"email\":\"http@example.com\",\"password\":\"http integration password!\"}")).build(),java.net.http.HttpResponse.BodyHandlers.ofString());
   assertThat(loginResponse.statusCode()).isEqualTo(200);
   String after=cookies.getCookieStore().getCookies().stream().filter(c->c.getName().equals("JSESSIONID")).findFirst().orElseThrow().getValue();
   assertThat(after).isNotEqualTo(before);
   var me=client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+"/api/v1/me")).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());
   assertThat(me.statusCode()).isEqualTo(200);assertThat(mapper.readTree(me.body()).get("email").asText()).isEqualTo("http@example.com");
   var fresh=client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+"/api/v1/auth/csrf")).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());
   String freshToken=mapper.readTree(fresh.body()).get("token").asText();
   var logout=client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+"/api/v1/auth/logout")).header("X-CSRF-TOKEN",freshToken).POST(java.net.http.HttpRequest.BodyPublishers.noBody()).build(),java.net.http.HttpResponse.BodyHandlers.ofString());
   assertThat(logout.statusCode()).isEqualTo(204);assertThat(logout.body()).isEmpty();
   var denied=client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+"/api/v1/me")).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());
   assertThat(denied.statusCode()).isEqualTo(401);
  }
 }
}
