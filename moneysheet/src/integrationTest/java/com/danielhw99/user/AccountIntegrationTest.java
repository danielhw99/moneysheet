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
@ContextConfiguration(initializers=AccountIntegrationTest.Database.class)
class AccountIntegrationTest {
 static final MySQLContainer MYSQL=new MySQLContainer("mysql:8.4.13").withDatabaseName("account_test")
 .withCreateContainerCmdModifier(command->command.getHostConfig().withPortBindings(new com.github.dockerjava.api.model.PortBinding(com.github.dockerjava.api.model.Ports.Binding.bindIp("127.0.0.1"),com.github.dockerjava.api.model.ExposedPort.tcp(3306))));
 static class Database implements ApplicationContextInitializer<ConfigurableApplicationContext>{public void initialize(ConfigurableApplicationContext c){MYSQL.start();Flyway.configure().dataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword()).cleanDisabled(true).createSchemas(false).load().migrate();TestPropertyValues.of("spring.datasource.url="+MYSQL.getJdbcUrl(),"spring.datasource.username="+MYSQL.getUsername(),"spring.datasource.password="+MYSQL.getPassword(),"spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver").applyTo(c);}}
 @Autowired MockMvc mvc;@Autowired SignupService signup;@Autowired AccountService accounts;@Autowired UserRepository users;@Autowired JdbcTemplate jdbc;@Autowired PlatformTransactionManager tm;@Autowired TransactionService transactions;@Autowired CategoryService categories;
 static final String PASSWORD="a unique account password!";
 long account(){String email=UUID.randomUUID()+"@example.com";return Long.parseLong(signup.signup(new SignupInput(email,"Nick",PASSWORD,"v1",true)).id().jsonValue());}
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
  mvc.perform(put("/api/v1/me/password").session(a).with(csrf()).contentType("application/json").content("{\"currentPassword\":\""+PASSWORD+"\",\"newPassword\":\"a different unique password!\"}")).andExpect(status().isNoContent()).andExpect(header().string("Set-Cookie",org.hamcrest.Matchers.containsString("Max-Age=0")));
  assertThat(a.isInvalid()).isTrue();mvc.perform(get("/api/v1/me").session(b)).andExpect(status().isUnauthorized());assertThat(b.isInvalid()).isTrue();
  assertThatThrownBy(()->login(id,PASSWORD)).isInstanceOf(AssertionError.class);mvc.perform(get("/api/v1/me").session(login(id,"a different unique password!"))).andExpect(status().isOk());
 }
 @Test void incorrectCurrentPasswordKeepsSessionAndAccount() throws Exception {
  long id=account();var a=login(id,PASSWORD);
  for(String path:new String[]{"/api/v1/me/password","/api/v1/me/deletion"}){
   var builder=path.endsWith("password")?put(path):post(path);
   mvc.perform(builder.session(a).with(csrf()).contentType("application/json").content(path.endsWith("password")?"{\"currentPassword\":\"wrong\",\"newPassword\":\"a different unique password!\"}":"{\"currentPassword\":\"wrong\"}")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
   mvc.perform(get("/api/v1/me").session(a)).andExpect(status().isOk());
  }
 }
 long seed(long id){
  jdbc.update("INSERT INTO categories(user_id,scope,direction,name,name_key,created_at,updated_at) VALUES (?,'FIXED_BUDGET','NONE','Rent','rent',NOW(6),NOW(6))",id);
  long cat=jdbc.queryForObject("SELECT id FROM categories WHERE user_id=?",Long.class,id);
  jdbc.update("INSERT INTO monthly_budgets(user_id,month_start,setup_status,food_weekday,food_saturday,food_sunday,created_at,updated_at) VALUES (?,'2026-11-01','READY',11000,15000,10000,NOW(6),NOW(6))",id);
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
  mvc.perform(post("/api/v1/me/deletion").session(a).with(csrf()).contentType("application/json").content("{\"currentPassword\":\""+PASSWORD+"\"}")).andExpect(status().isNoContent());
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
   });awaitUserLockWait(id);accounts.changePassword(id,PASSWORD,"a different unique password!");});
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
}
