package com.danielhw99.summary;
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
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import com.danielhw99.summary.service.SummaryService;
import com.danielhw99.summary.api.SummaryDtos;
import com.danielhw99.transaction.service.TransactionService;
import com.danielhw99.transaction.api.TransactionInput;
import com.danielhw99.transaction.api.TransactionDirectionValue;
import com.danielhw99.memo.service.MemoService;
import com.danielhw99.memo.api.MemoInput;
import com.danielhw99.memo.api.MemoDirectionValue;
import com.danielhw99.memo.entity.MoneyMemo;
import com.danielhw99.memo.entity.MemoDirection;
import com.danielhw99.memo.repository.MoneyMemoRepository;
import com.danielhw99.payment.service.PaymentService;
import com.danielhw99.payment.api.PaymentInput;
import com.danielhw99.payment.entity.FixedPayment;
import com.danielhw99.payment.repository.FixedPaymentRepository;
import com.danielhw99.budget.entity.FixedBudgetItem;
import com.danielhw99.budget.repository.FixedBudgetItemRepository;
import com.danielhw99.budget.service.BudgetService;
import com.danielhw99.budget.api.BudgetDtos;
import com.danielhw99.category.entity.Category;
import com.danielhw99.category.entity.CategoryScope;
import com.danielhw99.category.entity.CategoryDirection;
import com.danielhw99.category.repository.CategoryRepository;
import com.danielhw99.common.api.ApiId;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
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
@Import(SummaryIntegrationTest.TestClock.class)
@ContextConfiguration(initializers=SummaryIntegrationTest.Database.class)
class SummaryIntegrationTest {
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4.13")
            .withDatabaseName("moneysheet_summaries_test")
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



    @TestConfiguration(proxyBeanMethods=false)
    static class TestClock {
        @Bean @Primary Clock summaryClock() { return Clock.fixed(Instant.parse("2026-11-15T16:00:00Z"),ZoneOffset.UTC); }
    }
    @Autowired SummaryService service;
    @Autowired com.danielhw99.balance.service.DailyBalanceCheckService balanceChecks;
    @Test void actualBalanceHttpUsesSessionCsrfAndStrictValidation() throws Exception {
        User owner=user(); var session=session(owner); String path="/api/v1/daily-balance-checks/2026-11-02";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(put(path).session(session).contentType("application/json").content("{\"actualBalance\":0,\"locked\":true}")).andExpect(status().isForbidden());
        mvc.perform(put(path).session(session).with(SecurityMockMvcRequestPostProcessors.csrf()).contentType("application/json").content("{\"actualBalance\":0,\"locked\":true}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.actualBalance").value(0));
        mvc.perform(get(path).session(session)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.locked").value(true));
        for(String input:List.of("{\"actualBalance\":-1,\"locked\":true}","{\"actualBalance\":1,\"locked\":null}","{\"actualBalance\":1.5,\"locked\":true}"))
            mvc.perform(put(path).session(session).with(SecurityMockMvcRequestPostProcessors.csrf()).contentType("application/json").content(input)).andExpect(status().isBadRequest());
    }
    @Test void actualBalancePersistsPerOwnerAndDateWithoutChangingSummary() {
        User owner=user(); ready(owner); User other=user();
        var day=month.atDay(2); var before=service.daily(owner.getId(),day);
        balanceChecks.save(owner.getId(),day,0L,true);
        assertThat(balanceChecks.get(owner.getId(),day).actualBalance()).isZero();
        assertThat(balanceChecks.get(other.getId(),day).actualBalance()).isNull();
        assertThat(balanceChecks.get(owner.getId(),day.plusDays(1)).actualBalance()).isNull();
        balanceChecks.save(owner.getId(),day,9999999999L,false);
        assertThat(balanceChecks.get(owner.getId(),day).locked()).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM daily_balance_checks WHERE user_id=? AND balance_date=?",Long.class,owner.getId(),day)).isEqualTo(1);
        balanceChecks.save(owner.getId(),day,null,true);
        assertThat(balanceChecks.get(owner.getId(),day).actualBalance()).isNull();
        assertThat(service.daily(owner.getId(),day)).isEqualTo(before);
    }
    @Autowired TransactionService transactions;
    @Autowired MemoService memos;
    @Autowired PaymentService payments;
    @Autowired CashFlowService flows;
    @Autowired BudgetService budgetService;
    @Autowired MonthlyBudgetRepository budgets;
    @Autowired MainTransactionRepository mainRecords;
    @Autowired MoneyMemoRepository memoRecords;
    @Autowired FixedPaymentRepository paymentRecords;
    @Autowired FixedBudgetItemRepository items;
    @Autowired CategoryRepository categories;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired JsonMapper mapper;
    @LocalServerPort int port;
    final YearMonth month=YearMonth.of(2026,11);
    User user() { String email=UUID.randomUUID()+"@example.com";return users.saveAndFlush(new User(email,email,"Summary tester","hash","v1",LocalDateTime.now())); }
    MonthlyBudget budget(User owner,YearMonth selected,BudgetSetupStatus status) { return budgets.saveAndFlush(new MonthlyBudget(owner,selected.atDay(1),status,11000L,11000L,11000L)); }
    MonthlyBudget ready(User owner) { return budget(owner,month,BudgetSetupStatus.READY); }
    FixedBudgetItem item(User owner,MonthlyBudget budget,long amount) {
        String key=UUID.randomUUID().toString();var category=categories.saveAndFlush(new Category(owner,CategoryScope.FIXED_BUDGET,CategoryDirection.NONE,key,key));
        return items.saveAndFlush(new FixedBudgetItem(budget,category,"Ordinary fixed cost",amount));
    }
    TransactionInput transaction(int day,long amount,TransactionDirectionValue direction) { return new TransactionInput(month.atDay(day),LocalTime.NOON,"Main",amount,direction,null); }
    MemoInput memo(int day,long amount,MemoDirectionValue direction) { return new MemoInput(month.atDay(day),LocalTime.NOON,"Temporary",amount,direction); }
    PaymentInput payment(int day,long amount,Long item) { return new PaymentInput(month.atDay(day),LocalTime.NOON,"Payment",amount,new ApiId(item)); }
    void code(ErrorCode expected,Runnable call) { assertThatThrownBy(call::run).isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(expected); }

    @Test void dailyIncludesGeneralIncomeInFreeBalanceOnceAndExcludesFuturePayments() {
        User owner=user();var budget=ready(owner);var item=item(owner,budget,100000);
        transactions.create(owner.getId(),month,transaction(1,9000,TransactionDirectionValue.EXPENSE));
        transactions.create(owner.getId(),month,transaction(1,50000,TransactionDirectionValue.INCOME));
        transactions.create(owner.getId(),month,transaction(2,22000,TransactionDirectionValue.EXPENSE));
        payments.create(owner.getId(),month,payment(2,60000,item.getId()));payments.create(owner.getId(),month,payment(4,40000,item.getId()));
        var out=memos.create(owner.getId(),month,memo(2,45000,MemoDirectionValue.EXPENSE));
        var in=memos.create(owner.getId(),month,memo(2,50000,MemoDirectionValue.INCOME));
        var pending=memos.create(owner.getId(),month,memo(2,14900,MemoDirectionValue.EXPENSE));
        var first=service.daily(owner.getId(),month.atDay(1));assertThat(first.carryoverFreeBalance()).isZero();assertThat(first.freeBalance()).isEqualTo(52000);
        var daily=service.daily(owner.getId(),month.atDay(2));
        assertThat(daily.carryoverFreeBalance()).isEqualTo(52000);assertThat(daily.freeBalance()).isEqualTo(41000);assertThat(daily.remainingFood()).isEqualTo(99000);assertThat(daily.mainBalance()).isEqualTo(130100);
        assertThat(daily.expenseTotal()).isEqualTo(22000);assertThat(daily.incomeTotal()).isZero();assertThat(daily.transactions()).hasSize(1);assertThat(daily.activeMemos()).hasSize(3);
        assertThat(daily.payments()).hasSize(1);assertThat(daily.payments().getFirst().paid()).isEqualTo(60000);assertThat(daily.payments().getFirst().remaining()).isEqualTo(40000);assertThat(daily.payments().getFirst().complete()).isFalse();
        for(var value:List.of(out,in,pending)) memos.complete(owner.getId(),month,value.id().value(),month.atDay(4));
        assertThat(service.daily(owner.getId(),month.atDay(3)).activeMemos()).hasSize(3);
        var completed=service.daily(owner.getId(),month.atDay(4));assertThat(completed.activeMemos()).isEmpty();assertThat(completed.mainBalance()).isEqualTo(140000);assertThat(completed.payments().getFirst().complete()).isTrue();
    }

    @Test void monthlyUsesFullBudgetAndMonthlyFlowsNotMainOrActualPayments() {
        User owner=user();var budget=ready(owner);var item=item(owner,budget,650000);
        payments.create(owner.getId(),month,payment(1,100000,item.getId()));
        transactions.create(owner.getId(),month,transaction(1,999999,TransactionDirectionValue.INCOME));
        transactions.create(owner.getId(),month,transaction(1,99999,TransactionDirectionValue.EXPENSE));
        memos.create(owner.getId(),month,memo(1,10000,MemoDirectionValue.INCOME));
        flows.create(owner.getId(),month,new CashFlowInput(month.atDay(1),LocalTime.NOON,"Salary",4000000L,CashFlowKindValue.INCOME));
        flows.create(owner.getId(),month,new CashFlowInput(month.atDay(2),LocalTime.NOON,"Savings withdrawal",100000L,CashFlowKindValue.EXTRA_EXPENSE));
        var summary=service.monthly(owner.getId(),month);
        assertThat(summary.fixedBudget()).isEqualTo(650000);assertThat(summary.foodBudget()).isEqualTo(330000);assertThat(summary.income()).isEqualTo(4000000);
        assertThat(summary.extra()).isEqualTo(100000);assertThat(summary.totalExpense()).isEqualTo(1080000);assertThat(summary.net()).isEqualTo(2920000);
        assertThat(service.home(owner.getId(),month).expense()).isEqualTo(summary.totalExpense());
    }

    @Test void homeReturnsEveryDateAndExplicitFutureRecordsWithoutFutureFreeBalance() {
        User owner=user();var budget=ready(owner);var item=item(owner,budget,100000);
        transactions.create(owner.getId(),month,transaction(1,1000,TransactionDirectionValue.EXPENSE));
        transactions.create(owner.getId(),month,transaction(20,100,TransactionDirectionValue.EXPENSE));
        payments.create(owner.getId(),month,payment(20,200,item.getId()));
        var future=memos.create(owner.getId(),month,memo(20,300,MemoDirectionValue.EXPENSE));memos.complete(owner.getId(),month,future.id().value(),month.atDay(22));
        var home=service.home(owner.getId(),month);assertThat(home.asOfDate()).isEqualTo(month.atDay(16));assertThat(home.days()).hasSize(30);assertThat(home.freeBalance()).isEqualTo(175000);
        var day20=home.days().get(19).entries();
        assertThat(day20).anyMatch(row->row instanceof SummaryDtos.AmountEntry a && a.kind().equals("fixed") && a.amount()==-200);
        assertThat(day20).anyMatch(row->row instanceof SummaryDtos.AmountEntry a && a.kind().equals("transaction") && a.amount()==-100);
        assertThat(day20).anyMatch(row->row instanceof SummaryDtos.MemoEntry m && m.label().equals("미완료 메모") && m.count()==1);
        assertThat(day20).noneMatch(row->row instanceof SummaryDtos.AmountEntry a && a.kind().equals("free"));
        assertThat(home.days().get(21).entries()).anyMatch(row->row instanceof SummaryDtos.MemoEntry m && m.label().equals("완료 메모") && m.count()==1);
        assertThat(home.days().getFirst().entries()).noneMatch(row->row instanceof SummaryDtos.AmountEntry a && a.kind().equals("fixed"));
    }

    @Test void recordsAreIndependentAcrossUsersAndMonthsAndReadsNeverCreateBudgets() {
        User owner=user();ready(owner);User other=user();ready(other);
        transactions.create(other.getId(),month,transaction(1,999999,TransactionDirectionValue.EXPENSE));
        var past=month.minusMonths(1);budget(owner,past,BudgetSetupStatus.READY);
        transactions.create(owner.getId(),past,new TransactionInput(past.atDay(1),LocalTime.NOON,"Past",999999L,TransactionDirectionValue.EXPENSE,null));
        assertThat(service.daily(owner.getId(),month.atDay(1)).freeBalance()).isEqualTo(11000);
        var historical=service.home(owner.getId(),past);assertThat(historical.asOfDate()).isEqualTo(past.atEndOfMonth());assertThat(historical.days()).hasSize(31);assertThat(historical.freeBalance()).isEqualTo(341000-999999);
        User missing=user();long before=budgets.count();
        code(ErrorCode.BUDGET_NOT_READY,()->service.daily(missing.getId(),month.atDay(1)));code(ErrorCode.BUDGET_NOT_READY,()->service.monthly(missing.getId(),month));code(ErrorCode.BUDGET_NOT_READY,()->service.home(missing.getId(),month));
        assertThat(budgets.count()).isEqualTo(before);
        budget(missing,month,BudgetSetupStatus.DRAFT);code(ErrorCode.BUDGET_NOT_READY,()->service.daily(missing.getId(),month.atDay(1)));
        code(ErrorCode.FUTURE_MONTH_NOT_ALLOWED,()->service.home(owner.getId(),month.plusMonths(1)));
    }

    @Test void editsDeletesAndRetroactiveFoodChangesAreReflectedOnTheNextRead() {
        User owner=user();var budget=ready(owner);var item=item(owner,budget,100000);
        var transaction=transactions.create(owner.getId(),month,transaction(1,9000,TransactionDirectionValue.EXPENSE));
        var payment=payments.create(owner.getId(),month,payment(3,20000,item.getId()));
        assertThat(service.daily(owner.getId(),month.atDay(3)).freeBalance()).isEqualTo(24000);
        budgetService.updateFood(owner.getId(),month,new BudgetDtos.Food(20000L,20000L,20000L));
        assertThat(service.daily(owner.getId(),month.atDay(3)).freeBalance()).isEqualTo(51000);assertThat(service.monthly(owner.getId(),month).foodBudget()).isEqualTo(600000);
        transactions.update(owner.getId(),month,transaction.id().value(),transaction(1,14000,TransactionDirectionValue.EXPENSE));assertThat(service.daily(owner.getId(),month.atDay(3)).freeBalance()).isEqualTo(46000);
        transactions.delete(owner.getId(),month,transaction.id().value());payments.delete(owner.getId(),month,payment.id().value());
        var daily=service.daily(owner.getId(),month.atDay(3));assertThat(daily.freeBalance()).isEqualTo(60000);assertThat(daily.payments()).isEmpty();assertThat(service.monthly(owner.getId(),month).fixedBudget()).isEqualTo(100000);
    }

    @Test void dailyReturnsAllRowsBeyondListPageSizeWithoutSilentTruncation() {
        User owner=user();var budget=ready(owner);var item=item(owner,budget,100000);
        var tx=new ArrayList<MainTransaction>();var memo=new ArrayList<MoneyMemo>();var pay=new ArrayList<FixedPayment>();
        for(int i=0;i<103;i++) {
            tx.add(new MainTransaction(budget,month.atDay(2),LocalTime.NOON,"Transaction "+i,1L,TransactionDirection.EXPENSE,null));
            memo.add(new MoneyMemo(budget,month.atDay(2),LocalTime.NOON,"Memo "+i,1L,MemoDirection.EXPENSE,null));
            pay.add(new FixedPayment(item,month.atDay(2),LocalTime.NOON,"Payment "+i,1L));
        }
        mainRecords.saveAllAndFlush(tx);memoRecords.saveAllAndFlush(memo);paymentRecords.saveAllAndFlush(pay);
        var daily=service.daily(owner.getId(),month.atDay(2));assertThat(daily.transactions()).hasSize(103);assertThat(daily.activeMemos()).hasSize(103);assertThat(daily.payments()).hasSize(103);
        assertThat(daily.expenseTotal()).isEqualTo(103);assertThat(daily.payments()).allMatch(row->row.paid()==103 && row.remaining()==99897);
        assertThat(daily.transactions().getFirst().id().value()).isGreaterThan(daily.transactions().getLast().id().value());
    }

    @Test void allInstallmentEndsAndLeapMonthAreCorrectWithPersistedBudgets() {
        User owner=user();ready(owner);
        for(int day:new int[]{11,21,30}) assertThat(service.daily(owner.getId(),month.atDay(day)).remainingFood()).isZero();
        assertThat(service.daily(owner.getId(),month.atDay(12)).remainingFood()).isEqualTo(99000);assertThat(service.daily(owner.getId(),month.atDay(22)).remainingFood()).isEqualTo(88000);
        var leap=YearMonth.of(2024,2);budget(owner,leap,BudgetSetupStatus.READY);
        assertThat(service.daily(owner.getId(),leap.atEndOfMonth()).freeBalance()).isEqualTo(319000);assertThat(service.daily(owner.getId(),leap.atEndOfMonth()).remainingFood()).isZero();
        assertThat(service.home(owner.getId(),leap).days()).hasSize(29);
    }

    @Test void realHttpRoutesAndSwaggerUseTheSummaryContractAndDoNotWrite() throws Exception {
        User owner=user();ready(owner);var session=session(owner);long before=budgets.count();
        mvc.perform(get("/api/v1/summaries/daily").param("date","2026-11-01").session(session)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.freeBalance").value(11000)).andExpect(jsonPath("$.payments").isEmpty());
        mvc.perform(get("/api/v1/summaries/monthly").param("month","2026-11").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.foodBudget").value(330000));
        mvc.perform(get("/api/v1/summaries/home").param("month","2026-11").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.asOfDate").value("2026-11-16")).andExpect(jsonPath("$.days.length()").value(30));
        mvc.perform(get("/api/v1/summaries/daily").param("date","2026-11-31").session(session)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/summaries/daily").param("date","2026-11-01")).andExpect(status().isUnauthorized());
        assertThat(budgets.count()).isEqualTo(before);
        var client=java.net.http.HttpClient.newHttpClient();var response=client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:"+port+"/v3/api-docs/summaries")).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);var doc=mapper.readTree(response.body());assertThat(doc.path("paths").size()).isEqualTo(3);for(String key:doc.path("paths").propertyNames()) assertThat(key).startsWith("/api/v1/summaries/");
        var schemas=doc.path("components").path("schemas");assertThat(schemas.path("DailySummary").path("required").toString()).contains("freeBalance","mainBalance","transactions","activeMemos","payments");
        assertThat(schemas.path("HomeSummary").path("required").toString()).contains("month","asOfDate","days","expense","freeBalance");
        assertThat(schemas.path("CalendarEntry").path("oneOf").size()).isEqualTo(2);
        assertThat(doc.path("paths").path("/api/v1/summaries/daily").path("get").path("security").toString()).contains("sessionCookie");
        var config=client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:"+port+"/v3/api-docs/swagger-config")).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());assertThat(mapper.readTree(config.body()).path("urls").toString()).contains("summaries");
    }
    MockHttpSession session(User user) {
        var principal=new AuthPrincipal(user.getId(),user.getEmail(),"Summary tester");var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal,null,List.of()));
        var session=new MockHttpSession();session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,context);session.setAttribute(SessionExpiryFilter.LOGIN_AT,Instant.parse("2026-11-15T16:00:00Z"));session.setAttribute(SessionExpiryFilter.LAST_SEEN,Instant.parse("2026-11-15T16:00:00Z"));return session;
    }
}
