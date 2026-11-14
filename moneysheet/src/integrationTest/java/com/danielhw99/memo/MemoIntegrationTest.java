package com.danielhw99.memo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import com.danielhw99.memo.api.MemoInput;
import com.danielhw99.memo.api.MemoDirectionValue;
import com.danielhw99.memo.api.MemoStatus;
import com.danielhw99.memo.repository.MoneyMemoRepository;
import com.danielhw99.memo.service.MemoService;
import com.danielhw99.memo.service.MemoReadService;
import com.danielhw99.budget.entity.MonthlyBudget;
import com.danielhw99.budget.entity.BudgetSetupStatus;
import com.danielhw99.budget.repository.MonthlyBudgetRepository;
import com.danielhw99.common.error.ApiException;
import com.danielhw99.common.error.ErrorCode;
import com.danielhw99.auth.security.AuthPrincipal;
import com.danielhw99.auth.security.SessionExpiryFilter;
import com.danielhw99.user.entity.User;
import com.danielhw99.user.repository.UserRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.mysql.MySQLContainer;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties={"spring.jpa.hibernate.ddl-auto=validate","spring.flyway.enabled=false","spring.profiles.active=local"})
@AutoConfigureMockMvc
@ContextConfiguration(initializers=MemoIntegrationTest.Database.class)
class MemoIntegrationTest {
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4.13")
            .withDatabaseName("moneysheet_memo_test")
            .withCreateContainerCmdModifier(command -> command.getHostConfig().withPortBindings(
                    new com.github.dockerjava.api.model.PortBinding(
                            com.github.dockerjava.api.model.Ports.Binding.bindIp("127.0.0.1"),
                            com.github.dockerjava.api.model.ExposedPort.tcp(3306))));

    static class Database implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override public void initialize(ConfigurableApplicationContext context) {
            MYSQL.start();
            Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                    .cleanDisabled(true).createSchemas(false).baselineOnMigrate(false).load().migrate();
            TestPropertyValues.of("spring.datasource.url=" + MYSQL.getJdbcUrl(),
                    "spring.datasource.username=" + MYSQL.getUsername(),
                    "spring.datasource.password=" + MYSQL.getPassword(),
                    "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver").applyTo(context);
        }
    }


    @Autowired MemoService service;
    @Autowired MemoReadService reads;
    @Autowired MoneyMemoRepository records;
    @Autowired MonthlyBudgetRepository budgets;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired JsonMapper mapper;
    @LocalServerPort int port;
    private final YearMonth month=YearMonth.of(2026,11);
    private User user() {
        String email=UUID.randomUUID()+"@example.com";
        return users.saveAndFlush(new User(email,email,"Memo tester","hash","v1",LocalDateTime.now()));
    }
    private void ready(User user) { ready(user,month); }
    private void ready(User user,YearMonth selected) {
        budgets.saveAndFlush(new MonthlyBudget(user,selected.atDay(1),BudgetSetupStatus.READY,11000L,15000L,10000L));
    }
    private MemoInput input(int day,long amount,MemoDirectionValue direction) {
        return new MemoInput(month.atDay(day),LocalTime.NOON,"정산",amount,direction);
    }
    private void code(ErrorCode expected,Runnable call) {
        assertThatThrownBy(call::run).isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(expected);
    }

    @Test void selectedDateUsesHistoricalActivationCompletionAndOccurrence() {
        User owner=user();ready(owner);
        var expense=service.create(owner.getId(),month,input(2,45000,MemoDirectionValue.EXPENSE));
        service.create(owner.getId(),month,input(1,50000,MemoDirectionValue.INCOME));
        service.create(owner.getId(),month,input(5,100,MemoDirectionValue.EXPENSE));
        service.complete(owner.getId(),month,expense.id().value(),month.atDay(4));
        var earlier=reads.list(owner.getId(),month,month.atDay(3),MemoStatus.PENDING,null,50);
        assertThat(earlier.rows()).hasSize(2);assertThat(earlier.total()).isEqualTo(5000);
        var completed=reads.list(owner.getId(),month,month.atDay(4),MemoStatus.COMPLETED,null,50);
        assertThat(completed.rows()).extracting(row->row.id().value()).containsExactly(expense.id().value());
        assertThat(completed.total()).isEqualTo(-45000);assertThat(completed.pendingMemoTotal()).isEqualTo(50000);
        assertThat(reads.list(owner.getId(),month,month.atDay(2),MemoStatus.ALL,null,50).rows()).hasSize(1);
        var none=reads.list(owner.getId(),month,month.atDay(3),MemoStatus.ALL,null,50);
        assertThat(none.rows()).isEmpty();assertThat(none.total()).isZero();assertThat(none.pendingMemoTotal()).isEqualTo(5000);
        assertThat(reads.list(owner.getId(),month,month.atDay(3),MemoStatus.COMPLETED,null,50).rows()).isEmpty();
        service.cancelCompletion(owner.getId(),month,expense.id().value());
        service.cancelCompletion(owner.getId(),month,expense.id().value());
        assertThat(reads.list(owner.getId(),month,month.atDay(4),MemoStatus.PENDING,null,50).total()).isEqualTo(5000);
        service.complete(owner.getId(),month,expense.id().value(),month.atDay(4));
        service.complete(owner.getId(),month,expense.id().value(),month.atDay(6));
        assertThat(service.get(owner.getId(),month,expense.id().value()).completedOn()).isEqualTo(month.atDay(6));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM main_transactions",Long.class)).isZero();
    }

    @Test void cursorPagingKeepsFullTotalsAndRejectsOtherOwnersFiltersAndMonths() {
        User owner=user();ready(owner);
        var first=service.create(owner.getId(),month,input(2,100,MemoDirectionValue.EXPENSE));
        var second=service.create(owner.getId(),month,input(2,200,MemoDirectionValue.EXPENSE));
        var third=service.create(owner.getId(),month,input(2,500,MemoDirectionValue.INCOME));
        ready(owner,month.minusMonths(1));
        service.create(owner.getId(),month.minusMonths(1),new MemoInput(month.minusMonths(1).atEndOfMonth(),LocalTime.NOON,"지난 월",9000L,MemoDirectionValue.EXPENSE));
        User outsider=user();ready(outsider);service.create(outsider.getId(),month,input(2,9000,MemoDirectionValue.EXPENSE));
        var page1=reads.list(owner.getId(),month,month.atDay(3),MemoStatus.PENDING,null,1);
        var page2=reads.list(owner.getId(),month,month.atDay(3),MemoStatus.PENDING,page1.nextCursor(),1);
        var page3=reads.list(owner.getId(),month,month.atDay(3),MemoStatus.PENDING,page2.nextCursor(),1);
        assertThat(List.of(page1.rows().getFirst().id(),page2.rows().getFirst().id(),page3.rows().getFirst().id())).containsExactly(third.id(),second.id(),first.id());
        assertThat(page1.total()).isEqualTo(200);assertThat(page2.total()).isEqualTo(200);assertThat(page3.total()).isEqualTo(200);
        assertThat(page1.expenseTotal()).isEqualTo(300);assertThat(page1.incomeTotal()).isEqualTo(500);assertThat(page3.nextCursor()).isNull();
        code(ErrorCode.VALIDATION,()->reads.list(outsider.getId(),month,month.atDay(3),MemoStatus.PENDING,page1.nextCursor(),1));
        code(ErrorCode.VALIDATION,()->reads.list(owner.getId(),month,month.atDay(4),MemoStatus.PENDING,page1.nextCursor(),1));
        code(ErrorCode.VALIDATION,()->reads.list(owner.getId(),month,month.atDay(3),MemoStatus.ALL,page1.nextCursor(),1));
        code(ErrorCode.VALIDATION,()->reads.list(owner.getId(),month.minusMonths(1),month.minusMonths(1).atDay(3),MemoStatus.PENDING,page1.nextCursor(),1));
        code(ErrorCode.VALIDATION,()->reads.list(owner.getId(),month,month.atDay(3),MemoStatus.PENDING,page1.nextCursor()+"x",1));
    }

    @Test void ownershipReadinessAndCompletionConstraintsProtectStoredData() {
        User owner=user();ready(owner);User outsider=user();ready(outsider);
        var memo=service.create(owner.getId(),month,input(2,100,MemoDirectionValue.EXPENSE));long id=memo.id().value();
        code(ErrorCode.NOT_FOUND,()->service.get(outsider.getId(),month,id));
        code(ErrorCode.NOT_FOUND,()->service.update(outsider.getId(),month,id,input(2,1,MemoDirectionValue.INCOME)));
        code(ErrorCode.NOT_FOUND,()->service.complete(outsider.getId(),month,id,month.atDay(4)));
        code(ErrorCode.NOT_FOUND,()->service.cancelCompletion(outsider.getId(),month,id));
        code(ErrorCode.NOT_FOUND,()->service.delete(owner.getId(),month.minusMonths(1),id));
        code(ErrorCode.VALIDATION,()->service.complete(owner.getId(),month,id,month.atDay(1)));
        code(ErrorCode.VALIDATION,()->service.complete(owner.getId(),month,id,month.plusMonths(1).atDay(1)));
        service.complete(owner.getId(),month,id,month.atDay(4));
        code(ErrorCode.VALIDATION,()->service.update(owner.getId(),month,id,input(5,1,MemoDirectionValue.EXPENSE)));
        var changed=service.update(owner.getId(),month,id,input(3,500,MemoDirectionValue.INCOME));
        assertThat(changed.completedOn()).isEqualTo(month.atDay(4));assertThat(changed.date()).isEqualTo(month.atDay(3));
        var rejected=org.assertj.core.api.Assertions.catchThrowable(()->jdbc.update("UPDATE money_memos SET completed_on='2026-11-01' WHERE id=?",id));
        assertThat(rejected).isInstanceOf(org.springframework.dao.DataAccessException.class);
        var cause=org.springframework.core.NestedExceptionUtils.getMostSpecificCause(rejected);
        assertThat(cause).isInstanceOf(java.sql.SQLException.class);
        assertThat(((java.sql.SQLException)cause).getErrorCode()).isEqualTo(3819);
        assertThat(service.get(owner.getId(),month,id).completedOn()).isEqualTo(month.atDay(4));
        User draft=user();budgets.saveAndFlush(new MonthlyBudget(draft,month.atDay(1),BudgetSetupStatus.DRAFT,null,null,null));
        code(ErrorCode.BUDGET_NOT_READY,()->service.create(draft.getId(),month,input(2,1,MemoDirectionValue.EXPENSE)));
        code(ErrorCode.BUDGET_NOT_READY,()->reads.list(draft.getId(),month,month.atDay(2),MemoStatus.PENDING,null,50));
        code(ErrorCode.VALIDATION,()->service.create(owner.getId(),month,input(2,0,MemoDirectionValue.EXPENSE)));
        service.delete(owner.getId(),month,id);code(ErrorCode.NOT_FOUND,()->service.get(owner.getId(),month,id));
    }

    @Test void concurrentCompletionAndDateEditCannotBothBreakTheDateInvariant() throws Exception {
        User owner=user();ready(owner);long id=service.create(owner.getId(),month,input(2,1,MemoDirectionValue.EXPENSE)).id().value();
        var start=new CountDownLatch(1);
        Callable<String> update=()->{start.await();try{service.update(owner.getId(),month,id,input(5,2,MemoDirectionValue.EXPENSE));return "updated";}catch(ApiException error){return error.getErrorCode().name();}};
        Callable<String> complete=()->{start.await();try{service.complete(owner.getId(),month,id,month.atDay(4));return "completed";}catch(ApiException error){return error.getErrorCode().name();}};
        try(var executor=Executors.newFixedThreadPool(2)) {
            var a=executor.submit(update);var b=executor.submit(complete);start.countDown();
            var result=List.of(a.get(60,TimeUnit.SECONDS),b.get(60,TimeUnit.SECONDS));
            assertThat(result).contains("VALIDATION");assertThat(result.stream().filter(value->!value.equals("VALIDATION")).count()).isEqualTo(1);
        }
        var finalMemo=service.get(owner.getId(),month,id);
        assertThat(finalMemo.completedOn()==null || !finalMemo.completedOn().isBefore(finalMemo.date())).isTrue();
    }

    @Test void httpLifecycleUsesRealServiceAndDatabaseWithSecurity() throws Exception {
        User owner=user();ready(owner);String path="/api/v1/months/2026-11/memos";
        var session=session(owner);
        String input="{\"date\":\"2026-11-02\",\"time\":\"12:30\",\"title\":\"정산\",\"amount\":100,\"direction\":\"expense\"}";
        mvc.perform(post(path).session(session).contentType("application/json").content(input)).andExpect(status().isForbidden());
        var created=mvc.perform(post(path).session(session).with(SecurityMockMvcRequestPostProcessors.csrf()).contentType("application/json").content(input))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control","no-store")).andReturn();
        String id=mapper.readTree(created.getResponse().getContentAsString()).get("id").asText();
        mvc.perform(put(path+"/"+id+"/completion").session(session).with(SecurityMockMvcRequestPostProcessors.csrf()).contentType("application/json").content("{\"completedOn\":\"2026-11-04\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.completedOn").value("2026-11-04"));
        mvc.perform(get(path).session(session).param("date","2026-11-03")).andExpect(status().isOk()).andExpect(jsonPath("$.rows.length()").value(1));
        mvc.perform(get(path).session(session).param("date","2026-11-04")).andExpect(status().isOk()).andExpect(jsonPath("$.rows").isEmpty());
        mvc.perform(delete(path+"/"+id+"/completion").session(session).with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.completedOn").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(get(path).session(session)).andExpect(status().isBadRequest());
        mvc.perform(get(path+"/"+id)).andExpect(status().isUnauthorized());
        mvc.perform(delete(path+"/"+id).session(session).with(SecurityMockMvcRequestPostProcessors.csrf())).andExpect(status().isNoContent()).andExpect(content().string(""));
        mvc.perform(get(path+"/"+id).session(session)).andExpect(status().isNotFound());
    }

    @Test void swaggerMemoGroupHasOnlyMemoPathsAndCorrectCompletionStatus() throws Exception {
        var client=java.net.http.HttpClient.newHttpClient();
        var response=client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:"+port+"/v3/api-docs/memos")).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        var doc=mapper.readTree(response.body());assertThat(doc.path("paths").size()).isEqualTo(3);
        for(String path:doc.path("paths").propertyNames()) assertThat(path).contains("/memos");
        var completion=doc.path("paths").path("/api/v1/months/{month}/memos/{id}/completion").path("delete");
        assertThat(completion.path("responses").has("200")).isTrue();assertThat(completion.path("responses").has("204")).isFalse();
        assertThat(completion.path("security").toString()).contains("sessionCookie","csrfToken");
        var list=doc.path("paths").path("/api/v1/months/{month}/memos").path("get");
        for(var parameter:list.path("parameters")) {
            if(parameter.path("name").asText().equals("date")) assertThat(parameter.path("required").asBoolean()).isTrue();
            if(parameter.path("name").asText().equals("status")) {
                var schema=parameter.path("schema");
                if(schema.has("$ref")) {
                    String reference=schema.path("$ref").asText();
                    schema=doc.path("components").path("schemas").path(reference.substring(reference.lastIndexOf('/')+1));
                }
                assertThat(schema.path("enum").toString()).isEqualTo("[\"pending\",\"completed\",\"all\"]");
            }
        }
        var config=client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:"+port+"/v3/api-docs/swagger-config")).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(config.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(config.body()).path("urls").toString()).contains("memos");
        assertThat(doc.path("components").path("schemas").path("MemoResponse").path("required").toString()).contains("id","date","time","title","amount","direction","completedOn");
        assertThat(doc.path("components").path("schemas").has("ApiError")).isTrue();
        assertThat(doc.path("components").path("securitySchemes").has("sessionCookie")).isTrue();
    }

    private MockHttpSession session(User user) {
        var principal=new AuthPrincipal(user.getId(),user.getEmail(),"Memo tester");
        var context=SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal,null,List.of()));
        var session=new MockHttpSession();session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,context);
        session.setAttribute(SessionExpiryFilter.LOGIN_AT,Instant.now());session.setAttribute(SessionExpiryFilter.LAST_SEEN,Instant.now());return session;
    }
}
