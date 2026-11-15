package com.danielhw99.cashflow;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import com.danielhw99.budget.entity.MonthlyBudget;
import com.danielhw99.budget.entity.BudgetSetupStatus;
import com.danielhw99.budget.repository.MonthlyBudgetRepository;
import com.danielhw99.common.error.ApiException;
import com.danielhw99.common.error.ErrorCode;
import com.danielhw99.auth.security.AuthPrincipal;
import com.danielhw99.auth.security.SessionExpiryFilter;
import com.danielhw99.user.entity.User;
import com.danielhw99.user.repository.UserRepository;
import com.danielhw99.cashflow.api.CashFlowInput;
import com.danielhw99.cashflow.api.CashFlowKindValue;
import com.danielhw99.cashflow.api.CashFlowResponse;
import com.danielhw99.cashflow.service.CashFlowService;
import com.danielhw99.cashflow.service.CashFlowReadService;
import com.danielhw99.cashflow.repository.MonthlyCashFlowRepository;
import com.danielhw99.transaction.entity.MainTransaction;
import com.danielhw99.transaction.entity.TransactionDirection;
import com.danielhw99.transaction.repository.MainTransactionRepository;
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
@ContextConfiguration(initializers=CashFlowIntegrationTest.Database.class)
class CashFlowIntegrationTest {
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4.13")
            .withDatabaseName("moneysheet_cashflows_test")
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



    @Autowired CashFlowService service;
    @Autowired CashFlowReadService reads;
    @Autowired MonthlyCashFlowRepository records;
    @Autowired MonthlyBudgetRepository budgets;
    @Autowired MainTransactionRepository mainRecords;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired JsonMapper mapper;
    @LocalServerPort int port;
    final YearMonth month=YearMonth.of(2026,11);

    User user() {
        String email=UUID.randomUUID()+"@example.com";
        return users.saveAndFlush(new User(email,email,"Cash flow tester","hash","v1",LocalDateTime.now()));
    }
    MonthlyBudget budget(User owner,YearMonth selected,BudgetSetupStatus status) {
        return budgets.saveAndFlush(new MonthlyBudget(owner,selected.atDay(1),status,11000L,15000L,10000L));
    }
    CashFlowInput input(YearMonth selected,int day,long amount,CashFlowKindValue kind) {
        return new CashFlowInput(selected.atDay(day),LocalTime.NOON,"  Monthly record  ",amount,kind);
    }
    CashFlowResponse create(User owner,int day,long amount,CashFlowKindValue kind) {
        return service.create(owner.getId(),month,input(month,day,amount,kind));
    }
    void code(ErrorCode expected,Runnable call) {
        assertThatThrownBy(call::run).isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(expected);
    }

    @Test void incomeAndExtraExpenseCrudPersistsAndRecomputesTotals() {
        User owner=user();budget(owner,month,BudgetSetupStatus.READY);
        var income=create(owner,1,100000,CashFlowKindValue.INCOME);
        var expense=create(owner,2,20000,CashFlowKindValue.EXTRA_EXPENSE);
        assertThat(service.get(owner.getId(),month,income.id().value()).title()).isEqualTo("Monthly record");
        var all=reads.list(owner.getId(),month,null,null,null,50);
        assertThat(all.incomeTotal()).isEqualTo(100000);assertThat(all.expenseTotal()).isEqualTo(20000);assertThat(all.total()).isEqualTo(80000);
        service.update(owner.getId(),month,expense.id().value(),new CashFlowInput(month.atDay(3),LocalTime.of(9,30),"Updated",30000L,CashFlowKindValue.EXTRA_EXPENSE));
        assertThat(service.get(owner.getId(),month,expense.id().value()).time()).isEqualTo(LocalTime.of(9,30));
        assertThat(reads.list(owner.getId(),month,null,null,null,50).total()).isEqualTo(70000);
        service.delete(owner.getId(),month,income.id().value());
        assertThat(reads.list(owner.getId(),month,null,null,null,50).total()).isEqualTo(-30000);
        code(ErrorCode.NOT_FOUND,()->service.delete(owner.getId(),month,income.id().value()));
        service.delete(owner.getId(),month,expense.id().value());
        var empty=reads.list(owner.getId(),month,null,null,null,50);assertThat(empty.rows()).isEmpty();assertThat(empty.nextCursor()).isNull();assertThat(empty.total()).isZero();
    }

    @Test void filteredTotalsCoverAllPagesAndStableTiesUseDescendingId() {
        User owner=user();budget(owner,month,BudgetSetupStatus.READY);
        var first=create(owner,1,100000,CashFlowKindValue.INCOME);
        var second=create(owner,1,20000,CashFlowKindValue.EXTRA_EXPENSE);
        var third=create(owner,2,40000,CashFlowKindValue.INCOME);
        var fourth=create(owner,2,10000,CashFlowKindValue.EXTRA_EXPENSE);
        var earlier=service.create(owner.getId(),month,new CashFlowInput(month.atDay(2),LocalTime.of(11,0),"Earlier",5000L,CashFlowKindValue.EXTRA_EXPENSE));
        var seen=new java.util.ArrayList<com.danielhw99.common.api.ApiId>();String cursor=null;
        do {
            var page=reads.list(owner.getId(),month,null,null,cursor,1);assertThat(page.rows()).hasSize(1);
            assertThat(page.incomeTotal()).isEqualTo(140000);assertThat(page.expenseTotal()).isEqualTo(35000);assertThat(page.total()).isEqualTo(105000);
            seen.add(page.rows().getFirst().id());cursor=page.nextCursor();
            assertThat(seen).hasSizeLessThanOrEqualTo(5);
        } while(cursor!=null);
        assertThat(seen).containsExactly(fourth.id(),third.id(),earlier.id(),second.id(),first.id());
        var income=reads.list(owner.getId(),month,null,CashFlowKindValue.INCOME,null,1);
        assertThat(income.rows()).hasSize(1);assertThat(income.incomeTotal()).isEqualTo(140000);assertThat(income.expenseTotal()).isZero();
        var rest=reads.list(owner.getId(),month,null,CashFlowKindValue.INCOME,income.nextCursor(),1);
        assertThat(rest.rows().getFirst().id()).isEqualTo(first.id());assertThat(rest.nextCursor()).isNull();assertThat(rest.total()).isEqualTo(140000);
        var expenses=reads.list(owner.getId(),month,month.atDay(2),CashFlowKindValue.EXTRA_EXPENSE,null,50);
        assertThat(expenses.rows()).hasSize(2);assertThat(expenses.expenseTotal()).isEqualTo(15000);assertThat(expenses.incomeTotal()).isZero();assertThat(expenses.total()).isEqualTo(-15000);
        assertThat(reads.list(owner.getId(),month,month.atDay(3),null,null,50).rows()).isEmpty();
    }

    @Test void signedCursorsRejectTamperingAndAnyChangedScope() {
        User owner=user();budget(owner,month,BudgetSetupStatus.READY);create(owner,1,10,CashFlowKindValue.INCOME);create(owner,1,20,CashFlowKindValue.INCOME);
        String cursor=reads.list(owner.getId(),month,null,null,null,1).nextCursor();assertThat(cursor).isNotBlank();
        User other=user();budget(other,month,BudgetSetupStatus.READY);budget(owner,month.minusMonths(1),BudgetSetupStatus.READY);
        code(ErrorCode.VALIDATION,()->reads.list(other.getId(),month,null,null,cursor,1));
        code(ErrorCode.VALIDATION,()->reads.list(owner.getId(),month.minusMonths(1),null,null,cursor,1));
        code(ErrorCode.VALIDATION,()->reads.list(owner.getId(),month,month.atDay(1),null,cursor,1));
        code(ErrorCode.VALIDATION,()->reads.list(owner.getId(),month,null,CashFlowKindValue.INCOME,cursor,1));
        code(ErrorCode.VALIDATION,()->reads.list(owner.getId(),month,null,null,cursor+"x",1));
    }

    @Test void usersAndMonthsStayIndependentAndForeignRecordsAreNotFound() {
        User owner=user();budget(owner,month,BudgetSetupStatus.READY);var mine=create(owner,1,100,CashFlowKindValue.INCOME);
        User other=user();budget(other,month,BudgetSetupStatus.READY);create(other,1,9000,CashFlowKindValue.EXTRA_EXPENSE);
        var past=month.minusMonths(1);budget(owner,past,BudgetSetupStatus.READY);service.create(owner.getId(),past,input(past,1,5000,CashFlowKindValue.INCOME));
        assertThat(reads.list(owner.getId(),month,null,null,null,50).total()).isEqualTo(100);
        code(ErrorCode.NOT_FOUND,()->service.get(other.getId(),month,mine.id().value()));
        code(ErrorCode.NOT_FOUND,()->service.update(other.getId(),month,mine.id().value(),input(month,2,1,CashFlowKindValue.INCOME)));
        code(ErrorCode.NOT_FOUND,()->service.delete(other.getId(),month,mine.id().value()));
        code(ErrorCode.NOT_FOUND,()->service.get(owner.getId(),past,mine.id().value()));
        code(ErrorCode.NOT_FOUND,()->service.delete(owner.getId(),past,mine.id().value()));
        assertThat(records.findById(mine.id().value())).isPresent();
    }

    @Test void monthKindAndValidationFailuresPreserveTheOriginalButPastRecordsAreEditable() {
        User owner=user();var past=month.minusMonths(1);budget(owner,past,BudgetSetupStatus.READY);
        var row=service.create(owner.getId(),past,input(past,1,100,CashFlowKindValue.INCOME));
        code(ErrorCode.VALIDATION,()->service.update(owner.getId(),past,row.id().value(),input(month,1,200,CashFlowKindValue.INCOME)));
        code(ErrorCode.VALIDATION,()->service.update(owner.getId(),past,row.id().value(),input(past,2,200,CashFlowKindValue.EXTRA_EXPENSE)));
        code(ErrorCode.VALIDATION,()->service.update(owner.getId(),past,row.id().value(),input(past,2,0,CashFlowKindValue.INCOME)));
        assertThat(service.get(owner.getId(),past,row.id().value()).amount()).isEqualTo(100);
        service.update(owner.getId(),past,row.id().value(),input(past,2,200,CashFlowKindValue.INCOME));
        assertThat(service.get(owner.getId(),past,row.id().value()).amount()).isEqualTo(200);
        service.delete(owner.getId(),past,row.id().value());assertThat(records.findById(row.id().value())).isEmpty();
    }

    @Test void missingOrDraftBudgetsCannotAcceptOrExposeCashFlows() {
        User owner=user();
        code(ErrorCode.BUDGET_NOT_READY,()->create(owner,1,10,CashFlowKindValue.INCOME));
        code(ErrorCode.BUDGET_NOT_READY,()->reads.list(owner.getId(),month,null,null,null,50));
        budget(owner,month,BudgetSetupStatus.DRAFT);
        code(ErrorCode.BUDGET_NOT_READY,()->create(owner,1,10,CashFlowKindValue.EXTRA_EXPENSE));
        code(ErrorCode.BUDGET_NOT_READY,()->reads.list(owner.getId(),month,null,null,null,50));
    }

    @Test void monthlyRecordsNeverCreateMainMemoOrPaymentAndExcludeMainIncome() {
        User owner=user();var budget=budget(owner,month,BudgetSetupStatus.READY);
        var income=create(owner,1,100000,CashFlowKindValue.INCOME);create(owner,2,20000,CashFlowKindValue.EXTRA_EXPENSE);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM main_transactions WHERE monthly_budget_id=?",Long.class,budget.getId())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM money_memos WHERE monthly_budget_id=?",Long.class,budget.getId())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fixed_payments p JOIN fixed_budget_items i ON i.id=p.fixed_budget_item_id WHERE i.monthly_budget_id=?",Long.class,budget.getId())).isZero();
        var main=mainRecords.saveAndFlush(new MainTransaction(budget,month.atDay(1),LocalTime.NOON,"Main deposit",900L,TransactionDirection.INCOME,null));
        service.update(owner.getId(),month,income.id().value(),input(month,1,110000,CashFlowKindValue.INCOME));
        assertThat(reads.list(owner.getId(),month,null,null,null,50).total()).isEqualTo(90000);
        service.delete(owner.getId(),month,income.id().value());assertThat(mainRecords.findById(main.getId())).isPresent();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM main_transactions WHERE monthly_budget_id=?",Long.class,budget.getId())).isEqualTo(1);
    }

    @Test void actualHttpCrudAndSwaggerDocumentMatchTheContract() throws Exception {
        User owner=user();budget(owner,month,BudgetSetupStatus.READY);var session=session(owner);String path="/api/v1/months/"+month+"/cash-flows";
        String body="{\"date\":\"2026-11-01\",\"time\":\"12:30\",\"title\":\"Salary\",\"amount\":100,\"kind\":\"income\"}";
        mvc.perform(post(path).session(session).contentType("application/json").content(body)).andExpect(status().isForbidden());
        var created=mvc.perform(post(path).session(session).with(SecurityMockMvcRequestPostProcessors.csrf()).contentType("application/json").content(body)).andExpect(status().isCreated()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.kind").value("income")).andReturn();
        String id=mapper.readTree(created.getResponse().getContentAsString()).path("id").asText();
        mvc.perform(get(path).session(session).param("kind","income")).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(100)).andExpect(jsonPath("$.expenseTotal").value(0));
        mvc.perform(get(path).session(session).param("kind","INCOME")).andExpect(status().isBadRequest());
        mvc.perform(put(path+"/"+id).session(session).with(SecurityMockMvcRequestPostProcessors.csrf()).contentType("application/json").content(body.replace("100,","150,"))).andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(150));
        mvc.perform(get(path+"/"+id)).andExpect(status().isUnauthorized());
        mvc.perform(delete(path+"/"+id).session(session).with(SecurityMockMvcRequestPostProcessors.csrf())).andExpect(status().isNoContent()).andExpect(content().string(""));
        mvc.perform(get(path+"/"+id).session(session)).andExpect(status().isNotFound());
        var client=java.net.http.HttpClient.newHttpClient();
        var document=client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:"+port+"/v3/api-docs/cash-flows")).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(document.statusCode()).isEqualTo(200);var doc=mapper.readTree(document.body());assertThat(doc.path("paths").size()).isEqualTo(2);
        for(String key:doc.path("paths").propertyNames()) assertThat(key).contains("/cash-flows");
        var post=doc.path("paths").path("/api/v1/months/{month}/cash-flows").path("post");
        assertThat(post.path("security").toString()).contains("sessionCookie","csrfToken");
        assertThat(post.path("responses").has("201")).isTrue();
        var schema=doc.path("components").path("schemas");
        assertThat(schema.path("MonthlyCashFlow").path("required").toString()).contains("id","date","time","title","amount","kind");
        assertThat(schema.path("MonthlyCashFlowList").path("required").toString()).contains("rows","nextCursor","expenseTotal","incomeTotal","total");
        assertThat(doc.path("paths").path("/api/v1/months/{month}/cash-flows/{id}").path("delete").path("responses").has("204")).isTrue();
        var config=client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:"+port+"/v3/api-docs/swagger-config")).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(mapper.readTree(config.body()).path("urls").toString()).contains("cash-flows");
    }

    MockHttpSession session(User user) {
        var principal=new AuthPrincipal(user.getId(),user.getEmail(),"Cash flow tester");
        var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal,null,List.of()));
        var session=new MockHttpSession();session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,context);
        session.setAttribute(SessionExpiryFilter.LOGIN_AT,Instant.now());session.setAttribute(SessionExpiryFilter.LAST_SEEN,Instant.now());return session;
    }
}
