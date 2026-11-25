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
    .contentType("application/json").content("{\"email\":\"bypass@example.com\",\"nickname\":\"N\",\"password\":\"unique secure pass!\",\"privacyPolicyVersion\":\"v1\",\"privacyAgreed\":true}"))
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
  assertThatThrownBy(()->signup.signup(new SignupInput("other-email@example.com","N","unique secure pass!","v1",true,proof))).isInstanceOf(ApiException.class);
  var saved=signup.signup(new SignupInput(email,email.split("@")[0],"unique secure pass!","v1",true,proof));
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
  assertThatThrownBy(()->signup.signup(new SignupInput(email,null,"unique secure pass!","v1",true,proof))).isInstanceOf(RuntimeException.class);
  assertThat(jdbc.queryForObject("SELECT consumed FROM email_verifications WHERE email_key=?",Boolean.class,email)).isFalse();
  signup.signup(new SignupInput(email,"Recovered","unique secure pass!","v1",true,proof));
  assertThat(jdbc.queryForObject("SELECT consumed FROM email_verifications WHERE email_key=?",Boolean.class,email)).isTrue();
 }

 @Test void distinctEmailKeysNeverShareAProofThroughDatabaseCollation() {
  String first="cafe@example.com",second="caf\u00e9@example.com";
  String token=proof(first);
  verification.request(second);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM email_verifications WHERE email_key IN (?,?)",Integer.class,first,second)).isEqualTo(2);
  assertThatThrownBy(()->verification.consume(second,token)).isInstanceOf(ApiException.class);
  signup.signup(new SignupInput(first,"Accent proof","unique secure pass!","v1",true,token));
 }

 @Test void reconfirmationCommitsProofReplacementBeforeSignup() {
  String email="reconfirm@example.com";
  var sent=verification.request(email);
  var first=verification.confirm(email,sent.challengeId(),mailbox.codes.get(email));
  var latest=verification.confirm(email,sent.challengeId(),mailbox.codes.get(email));
  assertThat(latest.verificationToken()).isNotEqualTo(first.verificationToken());
  assertThatThrownBy(()->signup.signup(new SignupInput(email,email.split("@")[0],"unique secure pass!","v1",true,first.verificationToken())))
   .isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION));
  assertThat(users.existsByEmailKey(email)).isFalse();
  signup.signup(new SignupInput(email,email.split("@")[0],"unique secure pass!","v1",true,latest.verificationToken()));
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email_key=?",Integer.class,email)).isEqualTo(1);
  assertThatThrownBy(()->verification.consume(email,latest.verificationToken())).isInstanceOf(ApiException.class);
 }

 @Test void signupLoginOwnProfileAndLogoutAgainstRealMysql() throws Exception {
  var csrf=mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
  var session=(MockHttpSession)csrf.getRequest().getSession(false);
  String token=mapper.readTree(csrf.getResponse().getContentAsString()).get("token").asText();
  String payload="{\"email\":\" Integration@Example.COM \",\"nickname\":\" Nick \",\"password\":\"unique secure pass!\",\"privacyPolicyVersion\":\"v1\",\"privacyAgreed\":true}";
  payload=payload.substring(0,payload.length()-1)+",\"verificationToken\":\""+proof("integration@example.com")+"\"}";
  mvc.perform(post("/api/v1/auth/signup").session(session).header("X-CSRF-TOKEN",token).contentType("application/json").content(payload))
    .andExpect(status().isCreated()).andExpect(jsonPath("$.email").value("integration@example.com"));
  mvc.perform(get("/api/v1/me").session(session)).andExpect(status().isUnauthorized());
  assertThat(users.findByEmailKey("integration@example.com").orElseThrow().getPasswordHash()).startsWith("{argon2id}");
  mvc.perform(post("/api/v1/auth/signup").session(session).header("X-CSRF-TOKEN",token).contentType("application/json").content(payload))
    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
  String before=session.getId();
  mvc.perform(post("/api/v1/auth/login").session(session).header("X-CSRF-TOKEN",token).contentType("application/json")
    .content("{\"email\":\"integration@example.com\",\"password\":\"unique secure pass!\"}"))
    .andExpect(status().isOk());
  assertThat(session.getId()).isNotEqualTo(before);
  mvc.perform(get("/api/v1/me").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.nickname").value("Nick"));
  var fresh=mvc.perform(get("/api/v1/auth/csrf").session(session)).andReturn();
  String freshToken=mapper.readTree(fresh.getResponse().getContentAsString()).get("token").asText();
  mvc.perform(post("/api/v1/auth/logout").session(session).header("X-CSRF-TOKEN",freshToken)).andExpect(status().isNoContent());
  mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
  assertThat(jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank", String.class)).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13");
 }

 @Test void concurrentSignupPreservesUniqueEmail() throws Exception {
  var input=new SignupInput("race@example.com","race","concurrent password!","v1",true,proof("race@example.com"));
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
   String signupBody="{\"email\":\"http@example.com\",\"nickname\":\"HTTP\",\"password\":\"http secure pass!\",\"privacyPolicyVersion\":\"v1\",\"privacyAgreed\":true}";
   signupBody=signupBody.substring(0,signupBody.length()-1)+",\"verificationToken\":\""+proof("http@example.com")+"\"}";
   var signupResponse=client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+"/api/v1/auth/signup"))
     .header("Content-Type","application/json").header("X-CSRF-TOKEN",token).POST(java.net.http.HttpRequest.BodyPublishers.ofString(signupBody)).build(),java.net.http.HttpResponse.BodyHandlers.ofString());
   assertThat(signupResponse.statusCode()).isEqualTo(201);
   var loginResponse=client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+"/api/v1/auth/login"))
     .header("Content-Type","application/json").header("X-CSRF-TOKEN",token).POST(java.net.http.HttpRequest.BodyPublishers.ofString("{\"email\":\"http@example.com\",\"password\":\"http secure pass!\"}")).build(),java.net.http.HttpResponse.BodyHandlers.ofString());
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

 @Autowired com.danielhw99.auth.remember.RememberLoginService remember;
 @Autowired com.danielhw99.auth.remember.RememberTokenRepository rememberTokens;
 @Autowired com.danielhw99.auth.remember.RememberCleanup rememberCleanup;
 @Autowired com.danielhw99.user.service.AccountService accounts;
 @Autowired org.springframework.security.crypto.password.PasswordEncoder passwords;
 @Autowired org.springframework.transaction.PlatformTransactionManager tm;
 static final String REMEMBER_PASSWORD="remember passphrase!";
 com.danielhw99.user.entity.User rememberUser(){
  String key=java.util.UUID.randomUUID().toString();
  var u=new com.danielhw99.user.entity.User(key+"@example.com",key+"@example.com",key,passwords.encode(REMEMBER_PASSWORD),"v1",java.time.LocalDateTime.now());
  u.verifyEmail();return users.saveAndFlush(u);
 }
 com.danielhw99.auth.remember.RememberGrant grant(com.danielhw99.user.entity.User user){
  return remember.login(com.danielhw99.auth.security.AuthPrincipal.from(user),true,null);
 }
 jakarta.servlet.http.Cookie rememberCookie(String value){return new jakarta.servlet.http.Cookie("MS_REMEMBER",value);}
 org.springframework.test.web.servlet.ResultActions restore(String value) throws Exception {
  return mvc.perform(post("/api/v1/auth/session").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).cookie(rememberCookie(value)));
 }
 @Test void rememberHttpIssuanceRotationExistingSessionAndExpiredFamily() throws Exception {
  var user=rememberUser();
  var login=mvc.perform(post("/api/v1/auth/login").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
   .contentType("application/json").content("{\"email\":\""+user.getEmail()+"\",\"password\":\""+REMEMBER_PASSWORD+"\",\"rememberMe\":true}"))
   .andExpect(status().isOk()).andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("no-store")))
   .andExpect(jsonPath("$.rememberSelector").doesNotExist()).andReturn();
  var cookie=login.getResponse().getCookie("MS_REMEMBER");assertThat(cookie).isNotNull();
  var proof=com.danielhw99.auth.remember.RememberProof.parse(cookie.getValue()).orElseThrow();
  assertThat(jdbc.queryForObject("SELECT validator_hash FROM remember_login_tokens WHERE selector=?",byte[].class,proof.selector())).containsExactly(proof.hash());
  var expiry=rememberTokens.findById(proof.selector()).orElseThrow().expiresAt();
  var session=(MockHttpSession)login.getRequest().getSession(false);
  mvc.perform(post("/api/v1/auth/session").session(session).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).cookie(rememberCookie("malformed")))
   .andExpect(status().isOk()).andExpect(header().doesNotExist("Set-Cookie"));
  // /me never restores merely because a remember cookie exists.
  mvc.perform(get("/api/v1/me").cookie(cookie)).andExpect(status().isUnauthorized());
  var restored=restore(cookie.getValue()).andExpect(status().isOk()).andReturn();
  var replacement=restored.getResponse().getCookie("MS_REMEMBER");assertThat(replacement.getValue()).isNotEqualTo(cookie.getValue());
  assertThat(rememberTokens.findById(proof.selector()).orElseThrow().expiresAt()).isEqualTo(expiry);
  restore(cookie.getValue()).andExpect(status().isConflict()).andExpect(header().doesNotExist("Set-Cookie"));
  restore(replacement.getValue()).andExpect(status().isOk());
  jdbc.update("UPDATE remember_login_tokens SET issued_at=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 31 DAY), expires_at=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 1 SECOND) WHERE selector=?",proof.selector());
  mvc.perform(get("/api/v1/me").session((MockHttpSession)restored.getRequest().getSession(false))).andExpect(status().isUnauthorized());
  restore(replacement.getValue()).andExpect(status().isUnauthorized());
 }
 @Test void oldCookieLogoutRevokesRotatedFamilyAndIsAnonymousIdempotent() throws Exception {
  var grant=grant(rememberUser());var restored=restore(grant.cookieValue()).andExpect(status().isOk()).andReturn();
  var session=(MockHttpSession)restored.getRequest().getSession(false);
  mvc.perform(post("/api/v1/auth/logout").cookie(rememberCookie(grant.cookieValue()))).andExpect(status().isForbidden());
  for(int i=0;i<2;i++) mvc.perform(post("/api/v1/auth/logout").cookie(rememberCookie(grant.cookieValue()))
   .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
   .andExpect(status().isNoContent()).andExpect(header().stringValues("Set-Cookie",org.hamcrest.Matchers.hasItems(org.hamcrest.Matchers.containsString("MS_REMEMBER=;"),org.hamcrest.Matchers.containsString("JSESSIONID=;"))));
  mvc.perform(get("/api/v1/me").session(session)).andExpect(status().isUnauthorized());
  restore(restored.getResponse().getCookie("MS_REMEMBER").getValue()).andExpect(status().isUnauthorized());
 }
 @Test void parallelRestoresProduceOnlyOneNewProofAndLogoutCannotResurrectIt() throws Exception {
  var user=rememberUser();var initial=grant(user);var start=new CountDownLatch(1);
  try(var executor=Executors.newFixedThreadPool(2)){
   Callable<String> action=()->{start.await();try{remember.restore(initial.cookieValue());return "ok";}catch(ApiException e){return e.getErrorCode().name();}};
   var a=executor.submit(action);var b=executor.submit(action);start.countDown();
   assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder("ok","CONFLICT");
   var current=grant(user);var race=new CountDownLatch(1);
   var restored=executor.submit(()->{race.await();try{return remember.restore(current.cookieValue());}catch(ApiException e){assertThat(e.getErrorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED);return null;}});
   var loggedOut=executor.submit(()->{race.await();remember.logout(null,current.cookieValue());return true;});race.countDown();
   var late=restored.get(20,TimeUnit.SECONDS);assertThat(loggedOut.get(20,TimeUnit.SECONDS)).isTrue();
   if(late!=null)assertThat(remember.sessionValid(late.principal())).isFalse();
   assertThatThrownBy(()->remember.restore(current.cookieValue())).isInstanceOf(ApiException.class);
  }
 }
 @Test void passwordChangeDeletionRollbackAndCleanupPreserveProofRules(){
  var user=rememberUser();var first=grant(user);var second=grant(user);
  accounts.changePassword(user.getId(),REMEMBER_PASSWORD,"new remember secret!");
  assertThat(remember.sessionValid(first.principal())).isFalse();
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM remember_login_tokens WHERE user_id=? AND revoked_at IS NULL",Long.class,user.getId())).isZero();
  assertThatThrownBy(()->remember.restore(second.cookieValue())).isInstanceOf(ApiException.class);
  var fresh=users.findById(user.getId()).orElseThrow();grant(fresh);accounts.deleteAccount(user.getId(),"new remember secret!");
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM remember_login_tokens WHERE user_id=?",Long.class,user.getId())).isZero();
  var rollbackUser=rememberUser();
  var rolled=new org.springframework.transaction.support.TransactionTemplate(tm).execute(status->{var g=grant(rollbackUser);status.setRollbackOnly();return g;});
  assertThat(rememberTokens.findById(com.danielhw99.auth.remember.RememberProof.parse(rolled.cookieValue()).orElseThrow().selector())).isEmpty();
  var active=grant(rollbackUser);var revoked=grant(rollbackUser);remember.logout(null,revoked.cookieValue());rememberCleanup.removeInactive();
  assertThat(rememberTokens.findById(com.danielhw99.auth.remember.RememberProof.parse(revoked.cookieValue()).orElseThrow().selector())).isEmpty();
  assertThat(remember.sessionValid(active.principal())).isTrue();
 }
 @Test void queuedWriteRechecksRevokedRememberFamilyAfterAcquiringOwnerLock() throws Exception {
  var user=rememberUser();var grant=grant(user);
  try(var executor=Executors.newSingleThreadExecutor()){
   final java.util.concurrent.Future<?>[] pending=new java.util.concurrent.Future<?>[1];
   new org.springframework.transaction.support.TransactionTemplate(tm).executeWithoutResult(status->{
    users.findWithLockingById(user.getId()).orElseThrow();
    pending[0]=executor.submit(()->{
     var context=org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
     context.setAuthentication(org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(grant.principal(),null,List.of()));
     org.springframework.security.core.context.SecurityContextHolder.setContext(context);
     try{return accounts.updateNickname(user.getId(),"must-not-save");}finally{org.springframework.security.core.context.SecurityContextHolder.clearContext();}
    });
    awaitRememberOwnerLockWait(user.getId());remember.logout(null,grant.cookieValue());
   });
   assertThatThrownBy(()->pending[0].get(20,TimeUnit.SECONDS)).hasCauseInstanceOf(ApiException.class);
   assertThat(users.findById(user.getId()).orElseThrow().getNickname()).isEqualTo(user.getNickname());
  }
 }
 private void awaitRememberOwnerLockWait(long owner){
  String sql="SELECT COUNT(*) FROM performance_schema.data_lock_waits w JOIN performance_schema.data_locks l ON l.ENGINE=w.ENGINE AND l.ENGINE_LOCK_ID=w.BLOCKING_ENGINE_LOCK_ID WHERE l.OBJECT_SCHEMA=? AND l.OBJECT_NAME='users' AND l.INDEX_NAME='PRIMARY' AND l.LOCK_DATA=?";
  try(var connection=java.sql.DriverManager.getConnection(MYSQL.getJdbcUrl(),"root",MYSQL.getPassword());var statement=connection.prepareStatement(sql)){
   statement.setString(1,MYSQL.getDatabaseName());statement.setString(2,Long.toString(owner));long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
   while(System.nanoTime()<deadline){try(var rs=statement.executeQuery()){rs.next();if(rs.getInt(1)>0)return;}Thread.sleep(20);}
   throw new AssertionError("queued write did not wait for the owner lock");
  }catch(Exception e){throw new IllegalStateException(e);}
 }
}
