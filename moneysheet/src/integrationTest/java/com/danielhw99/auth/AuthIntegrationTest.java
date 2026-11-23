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
@org.springframework.context.annotation.Import(VerificationTestSupport.class)
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
     .cleanDisabled(true).createSchemas(false).baselineOnMigrate(false).target("6").load().migrate();
   try(var connection=java.sql.DriverManager.getConnection(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword());var statement=connection.createStatement()) {
    statement.executeUpdate("INSERT INTO users(email,email_key,nickname,password_hash,privacy_policy_version,privacy_agreed_at,created_at,updated_at) VALUES ('legacy@example.com','legacy@example.com','Legacy','hash','v1',NOW(6),NOW(6),NOW(6))");
   } catch(java.sql.SQLException e){throw new IllegalStateException(e);}
   Flyway.configure().dataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword()).load().migrate();
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
 @Autowired com.danielhw99.auth.email.EmailVerificationService verification;
 @Autowired VerificationTestSupport.Mailbox mailbox;
 String proof(String email){return VerificationTestSupport.proof(verification,mailbox,email);}

 @Test void v7VerifiesOnlyExistingAccountsAndUnverifiedSignupCannotBypass() throws Exception {
  assertThat(jdbc.queryForObject("SELECT email_verified FROM users WHERE email_key='legacy@example.com'",Boolean.class)).isTrue();
  jdbc.update("INSERT INTO users(email,email_key,nickname,password_hash,privacy_policy_version,privacy_agreed_at,created_at,updated_at) VALUES ('after-v7@example.com','after-v7@example.com','New','hash','v1',NOW(6),NOW(6),NOW(6))");
  assertThat(jdbc.queryForObject("SELECT email_verified FROM users WHERE email_key='after-v7@example.com'",Boolean.class)).isFalse();
  mvc.perform(post("/api/v1/auth/signup").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
    .contentType("application/json").content("{\"email\":\"bypass@example.com\",\"nickname\":\"N\",\"password\":\"a unique long password!\",\"privacyPolicyVersion\":\"v1\",\"privacyAgreed\":true}"))
    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION"));
  assertThat(users.existsByEmailKey("bypass@example.com")).isFalse();
 }

 @Test void verificationHttpFlowIsPublicCsrfProtectedAndBindsEmail() throws Exception {
  String email="verify-http@example.com";
  var result=mvc.perform(get("/api/v1/auth/csrf")).andReturn();
  var session=(MockHttpSession)result.getRequest().getSession(false);
  String csrf=mapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
  mvc.perform(post("/api/v1/auth/email-verifications").session(session).contentType("application/json").content("{\"email\":\""+email+"\"}"))
   .andExpect(status().isForbidden());
  var sent=mvc.perform(post("/api/v1/auth/email-verifications").session(session).header("X-CSRF-TOKEN",csrf).contentType("application/json").content("{\"email\":\""+email+"\"}"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.code").doesNotExist()).andReturn();
  String challenge=mapper.readTree(sent.getResponse().getContentAsString()).get("challengeId").asText();
  String code=mailbox.codes.get(email);
  assertThat(jdbc.queryForObject("SELECT code_hash FROM email_verifications WHERE email_key=?",String.class,email)).doesNotContain(code).startsWith("{argon2id}");
  var verified=mvc.perform(post("/api/v1/auth/email-verifications/confirmation").session(session).header("X-CSRF-TOKEN",csrf).contentType("application/json")
   .content("{\"email\":\""+email+"\",\"challengeId\":\""+challenge+"\",\"code\":\""+code+"\"}"))
   .andExpect(status().isOk()).andReturn();
  String proof=mapper.readTree(verified.getResponse().getContentAsString()).get("verificationToken").asText();
  assertThatThrownBy(()->signup.signup(new SignupInput("other-email@example.com","N","a unique long password!","v1",true,proof))).isInstanceOf(ApiException.class);
  var saved=signup.signup(new SignupInput(email,"N","a unique long password!","v1",true,proof));
  assertThat(users.findById(Long.parseLong(saved.id().jsonValue())).orElseThrow().isEmailVerified()).isTrue();
  assertThat(jdbc.queryForObject("SELECT code_hash FROM email_verifications WHERE email_key=?",String.class,email)).isNull();
 }

 @Test void wrongGuessesCommitDespiteExceptionsAndResendInvalidatesProof() {
  String email="wrong-guesses@example.com";
  var sent=verification.request(email);String correct=mailbox.codes.get(email),wrong=correct.equals("000000")?"000001":"000000";
  for(int i=0;i<5;i++) assertThatThrownBy(()->verification.confirm(email,sent.challengeId(),wrong)).isInstanceOf(ApiException.class);
  assertThat(jdbc.queryForObject("SELECT attempts FROM email_verifications WHERE email_key=?",Integer.class,email)).isEqualTo(5);
  assertThatThrownBy(()->verification.confirm(email,sent.challengeId(),correct)).isInstanceOf(ApiException.class);
  jdbc.update("UPDATE email_verifications SET sent_at=DATE_SUB(UTC_TIMESTAMP(),INTERVAL 61 SECOND) WHERE email_key=?",email);
  var resent=verification.request(email);
  assertThat(resent.challengeId()).isNotEqualTo(sent.challengeId());
  var proof=verification.confirm(email,resent.challengeId(),mailbox.codes.get(email)).verificationToken();
  jdbc.update("UPDATE email_verifications SET sent_at=DATE_SUB(UTC_TIMESTAMP(),INTERVAL 61 SECOND) WHERE email_key=?",email);
  verification.request(email);
  assertThatThrownBy(()->verification.consume(email,proof)).isInstanceOf(ApiException.class);
 }

 @Test void mailFailureRollsBackAndFailedSignupDoesNotConsumeProof() {
  String email="rollback-email@example.com";
  mailbox.fail=true;
  try {assertThatThrownBy(()->verification.request(email)).isInstanceOf(ApiException.class);}
  finally {mailbox.fail=false;}
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM email_verifications WHERE email_key=?",Integer.class,email)).isZero();
  String proof=proof(email);
  assertThatThrownBy(()->signup.signup(new SignupInput(email,null,"a unique long password!","v1",true,proof))).isInstanceOf(RuntimeException.class);
  assertThat(jdbc.queryForObject("SELECT consumed FROM email_verifications WHERE email_key=?",Boolean.class,email)).isFalse();
  signup.signup(new SignupInput(email,"Recovered","a unique long password!","v1",true,proof));
  assertThat(jdbc.queryForObject("SELECT consumed FROM email_verifications WHERE email_key=?",Boolean.class,email)).isTrue();
 }

 @Test void distinctEmailKeysNeverShareAProofThroughDatabaseCollation() {
  String first="cafe@example.com",second="caf\u00e9@example.com";
  String token=proof(first);
  verification.request(second);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM email_verifications WHERE email_key IN (?,?)",Integer.class,first,second)).isEqualTo(2);
  assertThatThrownBy(()->verification.consume(second,token)).isInstanceOf(ApiException.class);
  signup.signup(new SignupInput(first,"N","a unique long password!","v1",true,token));
 }

 @Test void reconfirmationCommitsProofReplacementBeforeSignup() {
  String email="reconfirm@example.com";
  var sent=verification.request(email);
  var first=verification.confirm(email,sent.challengeId(),mailbox.codes.get(email));
  var latest=verification.confirm(email,sent.challengeId(),mailbox.codes.get(email));
  assertThat(latest.verificationToken()).isNotEqualTo(first.verificationToken());
  assertThatThrownBy(()->signup.signup(new SignupInput(email,"N","a unique long password!","v1",true,first.verificationToken())))
   .isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION));
  assertThat(users.existsByEmailKey(email)).isFalse();
  signup.signup(new SignupInput(email,"N","a unique long password!","v1",true,latest.verificationToken()));
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email_key=?",Integer.class,email)).isEqualTo(1);
  assertThatThrownBy(()->verification.consume(email,latest.verificationToken())).isInstanceOf(ApiException.class);
 }

 @Test void signupLoginOwnProfileAndLogoutAgainstRealMysql() throws Exception {
  var csrf=mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
  var session=(MockHttpSession)csrf.getRequest().getSession(false);
  String token=mapper.readTree(csrf.getResponse().getContentAsString()).get("token").asText();
  String payload="{\"email\":\" Integration@Example.COM \",\"nickname\":\" Nick \",\"password\":\"a unique long password!\",\"privacyPolicyVersion\":\"v1\",\"privacyAgreed\":true}";
  payload=payload.substring(0,payload.length()-1)+",\"verificationToken\":\""+proof("integration@example.com")+"\"}";
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
  assertThat(jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank", String.class)).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10");
 }

 @Test void concurrentSignupPreservesUniqueEmail() throws Exception {
  var input=new SignupInput("race@example.com","race","a concurrent password!","v1",true,proof("race@example.com"));
  var start=new CountDownLatch(1);
  Callable<String> action=()->{start.await();try{signup.signup(input);return "created";}catch(ApiException e){return e.getErrorCode().name();}};
  try(var executor=Executors.newFixedThreadPool(2)) {
   var one=executor.submit(action);var two=executor.submit(action);start.countDown();
   var outcomes=List.of(one.get(30,TimeUnit.SECONDS),two.get(30,TimeUnit.SECONDS));
   assertThat(outcomes.stream().filter("created"::equals).count()).isEqualTo(1);
   assertThat(outcomes.stream().filter(value->!value.equals("created")).toList()).singleElement()
    .isIn(ErrorCode.EMAIL_ALREADY_EXISTS.name(),ErrorCode.VALIDATION.name());
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
   signupBody=signupBody.substring(0,signupBody.length()-1)+",\"verificationToken\":\""+proof("http@example.com")+"\"}";
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
