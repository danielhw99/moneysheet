package com.danielhw99.summary;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import com.danielhw99.auth.security.AuthPrincipal;
import com.danielhw99.auth.security.SessionExpiryFilter;
import com.danielhw99.budget.api.BudgetDtos;
import com.danielhw99.budget.service.BudgetService;
import com.danielhw99.idempotency.IdempotentWrites;
import com.danielhw99.idempotency.WriteRequestRepository;
import com.danielhw99.transaction.api.TransactionInput;
import com.danielhw99.transaction.api.TransactionDirectionValue;
import com.danielhw99.transaction.service.TransactionService;
import com.danielhw99.transaction.repository.MainTransactionRepository;
import com.danielhw99.user.entity.User;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.testcontainers.mysql.MySQLContainer;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"spring.jpa.hibernate.ddl-auto=validate","spring.flyway.enabled=false","spring.profiles.active=local"})
@AutoConfigureMockMvc
@ContextConfiguration(initializers=IdempotencyIntegrationTest.Database.class)
class IdempotencyIntegrationTest {
 static final MySQLContainer MYSQL=new MySQLContainer("mysql:8.4.13").withDatabaseName("moneysheet_idempotency_test")
  .withCreateContainerCmdModifier(c->c.getHostConfig().withPortBindings(new com.github.dockerjava.api.model.PortBinding(com.github.dockerjava.api.model.Ports.Binding.bindIp("127.0.0.1"),new com.github.dockerjava.api.model.ExposedPort(3306))));
 static class Database implements ApplicationContextInitializer<ConfigurableApplicationContext> {
  public void initialize(ConfigurableApplicationContext context) {
   MYSQL.start();Flyway.configure().dataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword()).cleanDisabled(true).createSchemas(false).baselineOnMigrate(false).load().migrate();
   TestPropertyValues.of("spring.datasource.url="+MYSQL.getJdbcUrl(),"spring.datasource.username="+MYSQL.getUsername(),"spring.datasource.password="+MYSQL.getPassword(),"spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver").applyTo(context);
  }
 }
 @Autowired MockMvc mvc;
 @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
 @Autowired JsonMapper mapper;
 @Autowired UserRepository users;
 @Autowired BudgetService budgets;
 @Autowired TransactionService transactions;
 @Autowired MainTransactionRepository records;
 @Autowired WriteRequestRepository requests;
 @Autowired IdempotentWrites writes;
 final YearMonth month=YearMonth.now(ZoneId.of("Asia/Seoul"));
 User owner() {
  String email=UUID.randomUUID()+"@example.com";
  var user=users.saveAndFlush(new User(email,email,"Idempotency","hash","v1",LocalDateTime.now()));
  budgets.prepare(user.getId(),month);budgets.setup(user.getId(),month,new BudgetDtos.SetupInput(List.of(),new BudgetDtos.Food(1L,1L,1L),month.atDay(1)));
  return user;
 }
 MockHttpSession session(User owner) {
  var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(new AuthPrincipal(owner.getId(),owner.getEmail(),"Tester"),null,List.of()));
  var s=new MockHttpSession();s.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,context);s.setAttribute(SessionExpiryFilter.LOGIN_AT,Instant.now());s.setAttribute(SessionExpiryFilter.LAST_SEEN,Instant.now());return s;
 }
 String base() { return "/api/v1/months/"+month; }
 String body(long amount) { return "{\"date\":\""+month+"-01\",\"time\":\"12:00\",\"title\":\"Lunch\",\"amount\":"+amount+",\"direction\":\"expense\",\"categoryId\":null}"; }
 String createHttp(MockHttpSession session,String path,String key,String input) throws Exception {
  return mvc.perform(post(path).session(session).with(csrf()).header("Idempotency-Key",key).contentType("application/json").content(input)).andExpect(status().isCreated()).andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse().getContentAsString();
 }
 @Test void fiveHttpCreateOperationsReplayTheSameResource() throws Exception {
  var user=owner();var session=session(user);
  var fixed=budgets.createItem(user.getId(),month,new BudgetDtos.ItemInput("Rent",null,100L));
  var cases=List.of(new String[]{"transactions",body(10)},
   new String[]{"memos",body(10).replace(",\"categoryId\":null","")},
   new String[]{"fixed-payments",body(10).replace(",\"direction\":\"expense\",\"categoryId\":null",",\"budgetItemId\":\""+fixed.id().value()+"\"")},
   new String[]{"cash-flows",body(10).replace(",\"direction\":\"expense\",\"categoryId\":null",",\"kind\":\"income\"")},
   new String[]{"budget-items","{\"name\":\"New item\",\"categoryId\":null,\"amount\":10}"});
  for(var example:cases) {
   String key=UUID.randomUUID().toString(),path=base()+"/"+example[0];
   var first=mapper.readTree(createHttp(session,path,key,example[1]));
   var again=mapper.readTree(createHttp(session,path,key,example[1]));
   assertThat(again.path("id").asText()).as(example[0]).isEqualTo(first.path("id").asText());
  }
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM write_requests WHERE user_id=?",Long.class,user.getId())).isEqualTo(5);
 }
 @Test void changedInputConflictsCurrentEditsAreReturnedAndDeletionDoesNotRecreate() throws Exception {
  var user=owner();var s=session(user);String key=UUID.randomUUID().toString(),path=base()+"/transactions";
  String id=mapper.readTree(createHttp(s,path,key,body(10))).path("id").asText();
  mvc.perform(post(path).session(s).with(csrf()).header("Idempotency-Key",key).contentType("application/json").content(body(11))).andExpect(status().isConflict());
  mvc.perform(put(path+"/"+id).session(s).with(csrf()).contentType("application/json").content(body(20))).andExpect(status().isOk());
  assertThat(mapper.readTree(createHttp(s,path,key,body(10))).path("amount").asLong()).isEqualTo(20);
  mvc.perform(delete(path+"/"+id).session(s).with(csrf())).andExpect(status().isNoContent());
  mvc.perform(post(path).session(s).with(csrf()).header("Idempotency-Key",key).contentType("application/json").content(body(10))).andExpect(status().isNotFound());
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM main_transactions t JOIN monthly_budgets b ON b.id=t.monthly_budget_id WHERE b.user_id=?",Long.class,user.getId())).isZero();
 }
 @Test void concurrentRequestsSerializeAndDistinctKeysRemainDistinct() throws Exception {
  var user=owner();String key=UUID.randomUUID().toString();var input=new TransactionInput(month.atDay(1),LocalTime.NOON,"Race",1L,TransactionDirectionValue.EXPENSE,null);
  var start=new CountDownLatch(1);
  java.util.concurrent.Callable<Long> operation=()->{start.await();return writes.execute(user.getId(),key,"transactions",month,input,()->transactions.create(user.getId(),month,input),id->transactions.get(user.getId(),month,id),r->r.id().value()).id().value();};
  try(var executor=Executors.newFixedThreadPool(2)) {
   var first=executor.submit(operation);var second=executor.submit(operation);start.countDown();assertThat(first.get(20,TimeUnit.SECONDS)).isEqualTo(second.get(20,TimeUnit.SECONDS));
  }
  String one=createHttp(session(user),base()+"/transactions",UUID.randomUUID().toString(),body(1));
  String two=createHttp(session(user),base()+"/transactions",UUID.randomUUID().toString(),body(1));
  assertThat(mapper.readTree(one).path("id").asText()).isNotEqualTo(mapper.readTree(two).path("id").asText());
 }
 @Test void transactionRollsBackIfReceiptFailsAndAccountDeletionCascadesReceipts() {
  var user=owner();var input=new TransactionInput(month.atDay(1),LocalTime.NOON,"Rollback",1L,TransactionDirectionValue.EXPENSE,null);
  assertThatThrownBy(()->writes.execute(user.getId(),UUID.randomUUID().toString(),"invalid-operation",month,input,()->transactions.create(user.getId(),month,input),id->transactions.get(user.getId(),month,id),r->r.id().value())).isInstanceOf(RuntimeException.class);
  assertThat(records.findAll().stream().filter(r->r.getTitle().equals("Rollback")).count()).isZero();
  String key=UUID.randomUUID().toString();var row=writes.execute(user.getId(),key,"transactions",month,input,()->transactions.create(user.getId(),month,input),id->transactions.get(user.getId(),month,id),r->r.id().value());
  transactions.delete(user.getId(),month,row.id().value());assertThat(requests.findByUser_IdAndRequestKey(user.getId(),key)).isPresent();
  jdbc.update("DELETE FROM monthly_budgets WHERE user_id=?",user.getId());
  users.deleteById(user.getId());assertThat(requests.findByUser_IdAndRequestKey(user.getId(),key)).isEmpty();
 }
 @Test void malformedHeaderAuthCsrfAndDifferentUsersRemainProtected() throws Exception {
  var one=owner();var two=owner();String path=base()+"/transactions",key=UUID.randomUUID().toString();
  var a=mapper.readTree(createHttp(session(one),path,key,body(1)));var b=mapper.readTree(createHttp(session(two),path,key,body(1)));assertThat(a.path("id").asText()).isNotEqualTo(b.path("id").asText());
  mvc.perform(post(path).session(session(one)).with(csrf()).header("Idempotency-Key","bad").contentType("application/json").content(body(1))).andExpect(status().isBadRequest());
  mvc.perform(post(path).session(session(one)).header("Idempotency-Key",key).contentType("application/json").content(body(1))).andExpect(status().isForbidden());
  mvc.perform(post(path).with(csrf()).header("Idempotency-Key",key).contentType("application/json").content(body(1))).andExpect(status().isUnauthorized());
 }
}
