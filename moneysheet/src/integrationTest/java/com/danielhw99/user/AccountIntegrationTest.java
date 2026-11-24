package com.danielhw99.user;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import java.util.*;
import java.time.*;
import java.util.concurrent.*;
import com.danielhw99.auth.dto.SignupInput;
import com.danielhw99.auth.service.SignupService;
import com.danielhw99.user.service.AccountService;
import com.danielhw99.user.repository.UserRepository;
import com.danielhw99.category.service.CategoryService;
import com.danielhw99.category.api.*;
import com.danielhw99.transaction.service.TransactionService;
import com.danielhw99.transaction.api.*;
import com.danielhw99.common.error.ApiException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.mysql.MySQLContainer;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"spring.jpa.hibernate.ddl-auto=validate","spring.flyway.enabled=false","server.servlet.session.cookie.secure=false"})
@AutoConfigureMockMvc
@org.springframework.context.annotation.Import(com.danielhw99.auth.VerificationTestSupport.class)
@ContextConfiguration(initializers=AccountIntegrationTest.Database.class)
class AccountIntegrationTest {
 static final MySQLContainer MYSQL=new MySQLContainer("mysql:8.4.13").withDatabaseName("account_test")
 .withCreateContainerCmdModifier(command->command.getHostConfig().withPortBindings(new com.github.dockerjava.api.model.PortBinding(com.github.dockerjava.api.model.Ports.Binding.bindIp("127.0.0.1"),com.github.dockerjava.api.model.ExposedPort.tcp(3306))));
 static class Database implements ApplicationContextInitializer<ConfigurableApplicationContext>{public void initialize(ConfigurableApplicationContext c){MYSQL.start();Flyway.configure().dataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword()).cleanDisabled(true).createSchemas(false).load().migrate();TestPropertyValues.of("spring.datasource.url="+MYSQL.getJdbcUrl(),"spring.datasource.username="+MYSQL.getUsername(),"spring.datasource.password="+MYSQL.getPassword(),"spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver").applyTo(c);}}
 @Autowired MockMvc mvc;@Autowired SignupService signup;@Autowired AccountService accounts;@Autowired UserRepository users;@Autowired JdbcTemplate jdbc;@Autowired PlatformTransactionManager tm;@Autowired TransactionService transactions;@Autowired CategoryService categories;
 static final String PASSWORD="account secret pass!";
 @Autowired com.danielhw99.auth.email.EmailVerificationService verification;
 @Autowired com.danielhw99.auth.VerificationTestSupport.Mailbox mailbox;
 long account(){String email=UUID.randomUUID()+"@example.com";String proof=com.danielhw99.auth.VerificationTestSupport.proof(verification,mailbox,email);return Long.parseLong(signup.signup(new SignupInput(email,UUID.randomUUID().toString(),PASSWORD,"v1",true,proof)).id().jsonValue());}
 MockHttpSession login(long id,String password) throws Exception {
  var result=mvc.perform(get("/api/v1/auth/csrf")).andReturn();var session=(MockHttpSession)result.getRequest().getSession(false);
  String email=users.findById(id).orElseThrow().getEmail();
  mvc.perform(post("/api/v1/auth/login").session(session).with(csrf()).contentType("application/json").content("{\"email\":\""+email+"\",\"password\":\""+password+"\"}")).andExpect(status().isOk());return session;
 }
 @Test void nicknameKeepsBothSessionsAndRejectsExtraFields() throws Exception {
  long id=account();var a=login(id,PASSWORD);var b=login(id,PASSWORD);
  mvc.perform(patch("/api/v1/me").session(a).with(csrf()).contentType("application/json").content("{\"nickname\":\" New \"}")).andExpect(status().isOk()).andExpect(jsonPath("$.nickname").value("New"));
  mvc.perform(get("/api/v1/me").session(b)).andExpect(status().isOk()).andExpect(jsonPath("$.nickname").value("New"));
  mvc.perform(patch("/api/v1/me").session(a).with(csrf()).contentType("application/json").content("{\"nickname\":\"Bad\",\"email\":\"x@example.com\"}")).andExpect(status().isBadRequest());
  mvc.perform(patch("/api/v1/me").session(a).contentType("application/json").content("{\"nickname\":\"Bad\"}")).andExpect(status().isForbidden());
 }
 @Test void passwordChangeRevokesBothSessionsAndOnlyNewPasswordWorks() throws Exception {
  long id=account();var a=login(id,PASSWORD);var b=login(id,PASSWORD);
  mvc.perform(put("/api/v1/me/password").session(a).with(csrf()).contentType("application/json").content("{\"currentPassword\":\""+PASSWORD+"\",\"newPassword\":\"different passphrase\"}")).andExpect(status().isNoContent()).andExpect(header().string("Set-Cookie",org.hamcrest.Matchers.containsString("Max-Age=0")));
  assertThat(a.isInvalid()).isTrue();mvc.perform(get("/api/v1/me").session(b)).andExpect(status().isUnauthorized());assertThat(b.isInvalid()).isTrue();
  assertThatThrownBy(()->login(id,PASSWORD)).isInstanceOf(AssertionError.class);mvc.perform(get("/api/v1/me").session(login(id,"different passphrase"))).andExpect(status().isOk());
 }
 @Test void incorrectCurrentPasswordKeepsSessionAndAccount() throws Exception {
  long id=account();var a=login(id,PASSWORD);
  for(String path:new String[]{"/api/v1/me/password","/api/v1/me/deletion"}){
   var builder=path.endsWith("password")?put(path):post(path);
   mvc.perform(builder.session(a).with(csrf()).contentType("application/json").content(path.endsWith("password")?"{\"currentPassword\":\"wrong\",\"newPassword\":\"different passphrase\"}":"{\"currentPassword\":\"wrong\"}")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
   mvc.perform(get("/api/v1/me").session(a)).andExpect(status().isOk());
  }
 }
 long seed(long id){
  jdbc.update("INSERT INTO categories(user_id,scope,direction,name,name_key,created_at,updated_at) VALUES (?,'FIXED_BUDGET','NONE','Rent','rent',NOW(6),NOW(6))",id);
  long cat=jdbc.queryForObject("SELECT id FROM categories WHERE user_id=?",Long.class,id);
  jdbc.update("INSERT INTO monthly_budgets(user_id,month_start,food_start_date,setup_status,food_weekday,food_saturday,food_sunday,created_at,updated_at) VALUES (?,'2026-11-01','2026-11-01','READY',11000,15000,10000,NOW(6),NOW(6))",id);
  long budget=jdbc.queryForObject("SELECT id FROM monthly_budgets WHERE user_id=?",Long.class,id);
  jdbc.update("INSERT INTO fixed_budget_items(monthly_budget_id,category_id,name,amount,created_at,updated_at) VALUES (?,?,'Rent',10000,NOW(6),NOW(6))",budget,cat);
  long item=jdbc.queryForObject("SELECT id FROM fixed_budget_items WHERE monthly_budget_id=?",Long.class,budget);
  jdbc.update("INSERT INTO fixed_payments(fixed_budget_item_id,occurred_on,occurred_time,title,amount,created_at,updated_at) VALUES (?,'2026-11-01','12:00','Rent',100,NOW(6),NOW(6))",item);
  jdbc.update("INSERT INTO main_transactions(monthly_budget_id,occurred_on,occurred_time,title,amount,direction,created_at,updated_at) VALUES (?,'2026-11-01','12:00','Food',100,'EXPENSE',NOW(6),NOW(6))",budget);
  jdbc.update("INSERT INTO money_memos(monthly_budget_id,occurred_on,occurred_time,title,amount,direction,created_at,updated_at) VALUES (?,'2026-11-01','12:00','Memo',100,'EXPENSE',NOW(6),NOW(6))",budget);
  jdbc.update("INSERT INTO monthly_cash_flows(monthly_budget_id,occurred_on,occurred_time,title,amount,kind,created_at,updated_at) VALUES (?,'2026-11-01','12:00','Income',100,'INCOME',NOW(6),NOW(6))",budget);
  return budget;
 }
 @Test void deletionRemovesAllOwnDataAndKeepsOtherUserAndSessions() throws Exception {
  long id=account(),other=account();long budget=seed(id),otherBudget=seed(other);var a=login(id,PASSWORD);var b=login(id,PASSWORD);var theirs=login(other,PASSWORD);
  jdbc.update("INSERT INTO daily_balance_checks(user_id,balance_date,actual_balance,locked,created_at,updated_at) VALUES (?, '2026-11-01', 100, 1, NOW(), NOW())",id);
  mvc.perform(post("/api/v1/me/deletion").session(a).with(csrf()).contentType("application/json").content("{\"currentPassword\":\""+PASSWORD+"\"}")).andExpect(status().isNoContent());
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM daily_balance_checks WHERE user_id=?",Long.class,id)).isZero();
  assertThat(users.findById(id)).isEmpty();assertThat(a.isInvalid()).isTrue();mvc.perform(get("/api/v1/me").session(b)).andExpect(status().isUnauthorized());mvc.perform(get("/api/v1/me").session(theirs)).andExpect(status().isOk());
  for(String table:new String[]{"main_transactions","money_memos","monthly_cash_flows","fixed_budget_items"}){
   assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE monthly_budget_id=?",Long.class,budget)).isZero();
   assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE monthly_budget_id=?",Long.class,otherBudget)).isEqualTo(1);
  }
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM categories WHERE user_id=?",Long.class,id)).isZero();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM monthly_budgets WHERE user_id=?",Long.class,id)).isZero();
 }
 @Test void failureAtUserDeletionRollsBackEarlierChildDeletes(){
  long id=account();long budget=seed(id);
  jdbc.execute("CREATE TABLE deletion_blocker(user_id BIGINT PRIMARY KEY, FOREIGN KEY(user_id) REFERENCES users(id)) ENGINE=InnoDB");
  try{jdbc.update("INSERT INTO deletion_blocker VALUES (?)",id);assertThatThrownBy(()->accounts.deleteAccount(id,PASSWORD)).isInstanceOf(RuntimeException.class);assertThat(users.findById(id)).isPresent();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM main_transactions WHERE monthly_budget_id=?",Long.class,budget)).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fixed_payments p JOIN fixed_budget_items i ON p.fixed_budget_item_id=i.id WHERE i.monthly_budget_id=?",Long.class,budget)).isEqualTo(1);}finally{jdbc.execute("DROP TABLE deletion_blocker");}
 }
 @Test void queuedWriteCannotRecreateDataAfterDeletion() throws Exception {
  long id=account();seed(id);var queued=new CountDownLatch(1);
  try(var executor=Executors.newSingleThreadExecutor()){
   final Future<?>[] task=new Future<?>[1];
   new TransactionTemplate(tm).executeWithoutResult(status->{users.findWithLockingById(id).orElseThrow();task[0]=executor.submit(()->{queued.countDown();return transactions.create(id,YearMonth.of(2026,11),new TransactionInput(LocalDate.of(2026,11,1),LocalTime.NOON,"Late",100L,TransactionDirectionValue.EXPENSE,null));});awaitUserLockWait(id);accounts.deleteAccount(id,PASSWORD);});
   assertThatThrownBy(()->task[0].get(20,TimeUnit.SECONDS)).hasCauseInstanceOf(ApiException.class);assertThat(users.findById(id)).isEmpty();
  }
 }
 @Test void queuedOldSessionWriteIsRejectedAfterPasswordChange() throws Exception {
  long id=account();long budget=seed(id);var queued=new CountDownLatch(1);
  try(var executor=Executors.newSingleThreadExecutor()){
   final Future<?>[] task=new Future<?>[1];
   new TransactionTemplate(tm).executeWithoutResult(status->{users.findWithLockingById(id).orElseThrow();task[0]=executor.submit(()->{
    var context=org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();context.setAuthentication(org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(new com.danielhw99.auth.security.AuthPrincipal(id,"old@example.com","Old",0),null,List.of()));org.springframework.security.core.context.SecurityContextHolder.setContext(context);
    try{queued.countDown();return transactions.create(id,YearMonth.of(2026,11),new TransactionInput(LocalDate.of(2026,11,1),LocalTime.NOON,"Late",100L,TransactionDirectionValue.EXPENSE,null));}finally{org.springframework.security.core.context.SecurityContextHolder.clearContext();}
   });awaitUserLockWait(id);accounts.changePassword(id,PASSWORD,"different passphrase");});
   assertThatThrownBy(()->task[0].get(20,TimeUnit.SECONDS)).hasCauseInstanceOf(ApiException.class);
   assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM main_transactions WHERE monthly_budget_id=?",Long.class,budget)).isEqualTo(1);
  }
 }
 private void awaitUserLockWait(long owner) {
  // Observe the actual InnoDB wait using only this ephemeral Testcontainers root account.
  String sql="SELECT COUNT(*) FROM performance_schema.data_lock_waits w JOIN performance_schema.data_locks l ON l.ENGINE=w.ENGINE AND l.ENGINE_LOCK_ID=w.BLOCKING_ENGINE_LOCK_ID WHERE l.OBJECT_SCHEMA=? AND l.OBJECT_NAME='users' AND l.INDEX_NAME='PRIMARY' AND l.LOCK_DATA=?";
  try(var connection=java.sql.DriverManager.getConnection(MYSQL.getJdbcUrl(),"root",MYSQL.getPassword());var statement=connection.prepareStatement(sql)){
   statement.setString(1,MYSQL.getDatabaseName());statement.setString(2,Long.toString(owner));
   long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
   while(System.nanoTime()<deadline){try(var rows=statement.executeQuery()){rows.next();if(rows.getLong(1)>0)return;}Thread.sleep(20);}
   throw new AssertionError("Queued write never waited on the owned user row");
  }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new AssertionError("Interrupted while observing user lock wait",interrupted);}
   catch(java.sql.SQLException failure){throw new AssertionError("Cannot observe isolated MySQL lock wait",failure);}
 }
 @Test void concurrentCategoryReuseStillReturnsOneRow() throws Exception {
  long id=account();var start=new CountDownLatch(1);var input=new CategoryInput("Food",CategoryScopeValue.MAIN_TRANSACTION,CategoryDirectionValue.EXPENSE);
  try(var executor=Executors.newFixedThreadPool(2)){
   Callable<String> action=()->{start.await();return categories.createOrReuse(id,input).id().jsonValue();};var a=executor.submit(action);var b=executor.submit(action);start.countDown();assertThat(a.get(20,TimeUnit.SECONDS)).isEqualTo(b.get(20,TimeUnit.SECONDS));
  }
 }

 @Test void caseInsensitiveNicknameUniquenessAndSelfRename() throws Exception {
  long first=account(),second=account();
  accounts.updateNickname(first,"CaseSensitiveName");
  assertThat(accounts.updateNickname(first," casesensitivename ").nickname()).isEqualTo("casesensitivename");
  assertThat(users.findById(first).orElseThrow().getSessionVersion()).isZero();
  var session=login(second,PASSWORD);
  mvc.perform(patch("/api/v1/me").session(session).with(csrf()).contentType("application/json").content("{\"nickname\":\"CASESENSITIVENAME\"}"))
   .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFLICT")).andExpect(jsonPath("$.fieldErrors[0].field").value("nickname"));
  String email=UUID.randomUUID()+"@example.com";
  String proof=com.danielhw99.auth.VerificationTestSupport.proof(verification,mailbox,email);
  assertThatThrownBy(()->signup.signup(new SignupInput(email,"CASESENSITIVENAME",PASSWORD,"v1",true,proof)))
   .isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getErrorCode()).isEqualTo(com.danielhw99.common.error.ErrorCode.CONFLICT));
  assertThat(jdbc.queryForObject("SELECT consumed FROM email_verifications WHERE email_key=?",Boolean.class,email)).isFalse();
  signup.signup(new SignupInput(email,"DifferentAvailableName",PASSWORD,"v1",true,proof));
 }
 @Test void concurrentRenamesCannotTakeSameNickname() throws Exception {
  long first=account(),second=account();var start=new CountDownLatch(1);
  try(var executor=Executors.newFixedThreadPool(2)) {
   java.util.function.LongFunction<String> rename=id->{try{start.await();accounts.updateNickname(id,"ConcurrentNick");return "ok";}catch(ApiException e){return e.getErrorCode().name();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}};
   var a=executor.submit(()->rename.apply(first));var b=executor.submit(()->rename.apply(second));start.countDown();
   assertThat(List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS))).containsExactlyInAnyOrder("ok","CONFLICT");
  }
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE nickname_key=LOWER('CONCURRENTNICK')",Long.class)).isEqualTo(1);
 }
 @Test void concurrentSignupsWithDistinctEmailsCannotTakeSameNickname() throws Exception {
  var inputs=new ArrayList<SignupInput>();
  for(int i=0;i<2;i++){String email=UUID.randomUUID()+"@example.com";String proof=com.danielhw99.auth.VerificationTestSupport.proof(verification,mailbox,email);inputs.add(new SignupInput(email,i==0?"SignupRace":"SIGNUPRACE",PASSWORD,"v1",true,proof));}
  var start=new CountDownLatch(1);
  try(var executor=Executors.newFixedThreadPool(2)) {
   java.util.function.Function<SignupInput,String> create=input->{try{start.await();signup.signup(input);return "ok";}catch(ApiException e){return e.getErrorCode().name();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}};
   var a=executor.submit(()->create.apply(inputs.get(0)));var b=executor.submit(()->create.apply(inputs.get(1)));start.countDown();
   assertThat(List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS))).containsExactlyInAnyOrder("ok","CONFLICT");
  }
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE nickname_key=LOWER('SignupRace')",Long.class)).isEqualTo(1);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM email_verifications WHERE email_key IN (?,?) AND consumed=1",Long.class,inputs.get(0).email(),inputs.get(1).email())).isEqualTo(1);
 }
 @Test void databaseKeyPreservesAccentsWidthAndInternalSpaces() {
  for(String nickname:new String[]{"Cafe","Café","Ｃａｆｅ","Ca fe"}) accounts.updateNickname(account(),nickname);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE nickname IN ('Cafe','Café','Ｃａｆｅ','Ca fe')",Long.class)).isEqualTo(4);
 }
 @Test void v12RejectsLegacyDuplicatesWithoutChangingUsers() throws Exception {
  String schema="nickname_migration_test";
  String url=MYSQL.getJdbcUrl().replace(MYSQL.getDatabaseName(),schema);
  try(var connection=java.sql.DriverManager.getConnection(MYSQL.getJdbcUrl(),"root",MYSQL.getPassword());var statement=connection.createStatement()){
   statement.execute("CREATE DATABASE "+schema+" CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin");
  }
  try {
   Flyway.configure().dataSource(url,"root",MYSQL.getPassword()).target("11").load().migrate();
   try(var connection=java.sql.DriverManager.getConnection(url,"root",MYSQL.getPassword());var statement=connection.createStatement()){
    statement.executeUpdate("INSERT INTO users(email,email_key,nickname,password_hash,privacy_policy_version,privacy_agreed_at,created_at,updated_at) VALUES ('one@example.com','one@example.com','OldNick','hash','v1',NOW(),NOW(),NOW()),('two@example.com','two@example.com','OLDNICK','hash','v1',NOW(),NOW(),NOW())");
   }
   var flyway=Flyway.configure().dataSource(url,"root",MYSQL.getPassword()).load();
   assertThatThrownBy(flyway::migrate).isInstanceOf(org.flywaydb.core.api.FlywayException.class);
   try(var connection=java.sql.DriverManager.getConnection(url,"root",MYSQL.getPassword());var statement=connection.createStatement()){
    try(var rows=statement.executeQuery("SELECT nickname FROM users ORDER BY id")){var names=new ArrayList<String>();while(rows.next())names.add(rows.getString(1));assertThat(names).containsExactly("OldNick","OLDNICK");}
    try(var rows=statement.executeQuery("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='"+schema+"' AND table_name='users' AND column_name='nickname_key'")){rows.next();assertThat(rows.getInt(1)).isZero();}
    statement.executeUpdate("UPDATE users SET nickname='ResolvedNick' WHERE email_key='two@example.com'");
   }
   flyway.repair();flyway.migrate();flyway.validate();
  } finally {
   try(var connection=java.sql.DriverManager.getConnection(MYSQL.getJdbcUrl(),"root",MYSQL.getPassword());var statement=connection.createStatement()){statement.execute("DROP DATABASE "+schema);}
  }
 }
}
