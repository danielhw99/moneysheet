package com.danielhw99.payment;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import com.danielhw99.payment.api.PaymentInput;
import com.danielhw99.payment.repository.FixedPaymentRepository;
import com.danielhw99.payment.service.PaymentService;
import com.danielhw99.payment.service.PaymentReadService;
import com.danielhw99.budget.api.BudgetDtos;
import com.danielhw99.budget.entity.MonthlyBudget;
import com.danielhw99.budget.entity.FixedBudgetItem;
import com.danielhw99.budget.entity.BudgetSetupStatus;
import com.danielhw99.budget.repository.MonthlyBudgetRepository;
import com.danielhw99.budget.repository.FixedBudgetItemRepository;
import com.danielhw99.budget.service.BudgetService;
import com.danielhw99.category.entity.Category;
import com.danielhw99.category.entity.CategoryScope;
import com.danielhw99.category.entity.CategoryDirection;
import com.danielhw99.category.repository.CategoryRepository;
import com.danielhw99.common.api.ApiId;
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
@ContextConfiguration(initializers=PaymentIntegrationTest.Database.class)
class PaymentIntegrationTest {
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4.13")
            .withDatabaseName("moneysheet_payments_test")
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



    @Autowired PaymentService service;
    @Autowired PaymentReadService reads;
    @Autowired FixedPaymentRepository records;
    @Autowired FixedBudgetItemRepository items;
    @Autowired MonthlyBudgetRepository budgets;
    @Autowired BudgetService budgetService;
    @Autowired CategoryRepository categories;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired JsonMapper mapper;
    @LocalServerPort int port;
    final YearMonth month=YearMonth.now(ZoneId.of("Asia/Seoul"));
    User user() { String email=UUID.randomUUID()+"@example.com";return users.saveAndFlush(new User(email,email,"Payment tester","hash","v1",LocalDateTime.now())); }
    FixedBudgetItem item(User user,YearMonth selected,long amount,String name) {
        var budget=budgets.findByUser_IdAndMonthStart(user.getId(),selected.atDay(1)).orElseGet(()->budgets.saveAndFlush(new MonthlyBudget(user,selected.atDay(1),BudgetSetupStatus.READY,11000L,15000L,10000L)));
        String key=UUID.randomUUID().toString();var category=categories.saveAndFlush(new Category(user,CategoryScope.FIXED_BUDGET,CategoryDirection.NONE,key,key));
        return items.saveAndFlush(new FixedBudgetItem(budget,category,name,amount));
    }
    PaymentInput input(YearMonth selected,int day,long amount,Long item) { return new PaymentInput(selected.atDay(day),LocalTime.NOON,"지급",amount,new ApiId(item)); }
    PaymentInput input(int day,long amount,Long item) { return input(month,day,amount,item); }
    BudgetDtos.ItemSummary summary(User owner,Long id) { return budgetService.get(owner.getId(),month).items().stream().filter(row->row.id().value()==id.longValue()).findFirst().orElseThrow(); }
    void code(ErrorCode expected,Runnable call) { assertThatThrownBy(call::run).isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(expected); }

    @Test void splitEditDeleteRecalculateRemainingAndCompletionForEveryOrdinaryItem() {
        User owner=user();var item=item(owner,month,100000,"고정여유비");
        var first=service.create(owner.getId(),month,input(2,60000,item.getId()));
        var second=service.create(owner.getId(),month,input(2,40000,item.getId()));
        var full=summary(owner,item.getId());assertThat(full.paid()).isEqualTo(100000);assertThat(full.remaining()).isZero();assertThat(full.complete()).isTrue();
        code(ErrorCode.PAYMENT_EXCEEDS_BUDGET,()->service.create(owner.getId(),month,input(2,1,item.getId())));
        code(ErrorCode.PAYMENT_EXCEEDS_BUDGET,()->service.update(owner.getId(),month,first.id().value(),input(2,60001,item.getId())));
        code(ErrorCode.REFERENCED,()->budgetService.deleteItem(owner.getId(),month,item.getId()));
        code(ErrorCode.BUDGET_BELOW_PAID,()->budgetService.updateItem(owner.getId(),month,item.getId(),new BudgetDtos.ItemInput("고정비",new ApiId(item.getCategory().getId()),99999L)));
        service.update(owner.getId(),month,first.id().value(),input(3,50000,item.getId()));
        assertThat(summary(owner,item.getId()).remaining()).isEqualTo(10000);assertThat(summary(owner,item.getId()).complete()).isFalse();
        service.delete(owner.getId(),month,second.id().value());assertThat(summary(owner,item.getId()).paid()).isEqualTo(50000);
        service.delete(owner.getId(),month,first.id().value());assertThat(summary(owner,item.getId()).paid()).isZero();
        budgetService.deleteItem(owner.getId(),month,item.getId());assertThat(items.findById(item.getId())).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM main_transactions",Long.class)).isZero();
    }
    @Test void movingPaymentChecksTargetCapacityAtomicallyAndFreesSourceBudget() {
        User owner=user();var source=item(owner,month,100000,"원본");var target=item(owner,month,50000,"대상");
        var payment=service.create(owner.getId(),month,input(2,60000,source.getId()));service.create(owner.getId(),month,input(2,20000,target.getId()));
        code(ErrorCode.PAYMENT_EXCEEDS_BUDGET,()->service.update(owner.getId(),month,payment.id().value(),input(3,30001,target.getId())));
        assertThat(service.get(owner.getId(),month,payment.id().value()).budgetItemId().value()).isEqualTo(source.getId());
        assertThat(summary(owner,source.getId()).paid()).isEqualTo(60000);
        service.update(owner.getId(),month,payment.id().value(),input(3,30000,target.getId()));
        assertThat(summary(owner,source.getId()).paid()).isZero();assertThat(summary(owner,target.getId()).paid()).isEqualTo(50000);assertThat(summary(owner,target.getId()).complete()).isTrue();
    }
    @Test void optionalDateAndItemFiltersHaveFullTotalsAndScopedCursor() {
        User owner=user();var a=item(owner,month,100000,"A");var b=item(owner,month,100000,"B");
        var first=service.create(owner.getId(),month,input(2,10000,a.getId()));var second=service.create(owner.getId(),month,input(2,20000,a.getId()));var third=service.create(owner.getId(),month,input(2,50000,b.getId()));
        User outsider=user();var foreign=item(outsider,month,100000,"외부");service.create(outsider.getId(),month,input(2,90000,foreign.getId()));
        var past=item(owner,month.minusMonths(1),100000,"지난 월");service.create(owner.getId(),month.minusMonths(1),input(month.minusMonths(1),2,70000,past.getId()));
        var one=reads.list(owner.getId(),month,null,null,null,1);var two=reads.list(owner.getId(),month,null,null,one.nextCursor(),1);var three=reads.list(owner.getId(),month,null,null,two.nextCursor(),1);
        assertThat(List.of(one.rows().getFirst().id(),two.rows().getFirst().id(),three.rows().getFirst().id())).containsExactly(third.id(),second.id(),first.id());
        assertThat(one.total()).isEqualTo(80000);assertThat(two.total()).isEqualTo(80000);assertThat(three.total()).isEqualTo(80000);assertThat(one.incomeTotal()).isZero();assertThat(three.nextCursor()).isNull();
        assertThat(reads.list(owner.getId(),month,month.atDay(2),a.getId(),null,50).total()).isEqualTo(30000);
        assertThat(reads.list(owner.getId(),month,month.atDay(1),null,null,50).total()).isZero();
        code(ErrorCode.VALIDATION,()->reads.list(owner.getId(),month,month.atDay(2),null,one.nextCursor(),1));
        code(ErrorCode.VALIDATION,()->reads.list(owner.getId(),month,null,a.getId(),one.nextCursor(),1));
        code(ErrorCode.VALIDATION,()->reads.list(outsider.getId(),month,null,null,one.nextCursor(),1));
        code(ErrorCode.VALIDATION,()->reads.list(owner.getId(),month.minusMonths(1),null,null,one.nextCursor(),1));
        code(ErrorCode.VALIDATION,()->reads.list(owner.getId(),month,null,null,one.nextCursor()+"x",1));
        code(ErrorCode.NOT_FOUND,()->reads.list(owner.getId(),month,null,foreign.getId(),null,50));
        code(ErrorCode.NOT_FOUND,()->reads.list(owner.getId(),month,null,past.getId(),null,50));
    }
    @Test void ownerReadinessMonthAndHistoricalRecordRulesAreEnforced() {
        User owner=user();var a=item(owner,month,100000,"A");var payment=service.create(owner.getId(),month,input(2,100,a.getId()));
        User other=user();var foreign=item(other,month,100000,"외부");
        code(ErrorCode.NOT_FOUND,()->service.get(other.getId(),month,payment.id().value()));
        code(ErrorCode.NOT_FOUND,()->service.update(other.getId(),month,payment.id().value(),input(2,1,foreign.getId())));
        code(ErrorCode.NOT_FOUND,()->service.delete(owner.getId(),month.minusMonths(1),payment.id().value()));
        code(ErrorCode.NOT_FOUND,()->service.create(owner.getId(),month,input(2,1,foreign.getId())));
        code(ErrorCode.VALIDATION,()->service.create(owner.getId(),month,input(2,0,a.getId())));
        code(ErrorCode.VALIDATION,()->service.create(owner.getId(),month,input(month.minusMonths(1),2,1,a.getId())));
        User draft=user();budgets.saveAndFlush(new MonthlyBudget(draft,month.atDay(1),BudgetSetupStatus.DRAFT,null,null,null));
        code(ErrorCode.BUDGET_NOT_READY,()->service.create(draft.getId(),month,input(2,1,a.getId())));code(ErrorCode.BUDGET_NOT_READY,()->reads.list(draft.getId(),month,null,null,null,50));
        var past=item(owner,month.minusMonths(1),100000,"과거");
        var old=service.create(owner.getId(),month.minusMonths(1),input(month.minusMonths(1),2,100,past.getId()));
        service.update(owner.getId(),month.minusMonths(1),old.id().value(),input(month.minusMonths(1),3,200,past.getId()));
        assertThat(service.get(owner.getId(),month.minusMonths(1),old.id().value()).amount()).isEqualTo(200);
        service.delete(owner.getId(),month.minusMonths(1),old.id().value());assertThat(records.findById(old.id().value())).isEmpty();
    }
    @Test void concurrentPaymentsCannotExceedTheBudget() throws Exception {
        User owner=user();var a=item(owner,month,100000,"경합");var start=new CountDownLatch(1);
        Callable<String> pay=()->{start.await();try{service.create(owner.getId(),month,input(2,60000,a.getId()));return "paid";}catch(ApiException error){return error.getErrorCode().name();}};
        try(var executor=Executors.newFixedThreadPool(2)) { var x=executor.submit(pay);var y=executor.submit(pay);start.countDown();assertThat(List.of(x.get(60,TimeUnit.SECONDS),y.get(60,TimeUnit.SECONDS))).containsExactlyInAnyOrder("paid","PAYMENT_EXCEEDS_BUDGET"); }
        assertThat(summary(owner,a.getId()).paid()).isEqualTo(60000);assertThat(summary(owner,a.getId()).remaining()).isEqualTo(40000);
    }
    @Test void budgetShrinkAndPaymentUseTheSameOwnerLock() throws Exception {
        User owner=user();var a=item(owner,month,100000,"경합");var start=new CountDownLatch(1);
        Callable<String> pay=()->{start.await();try{service.create(owner.getId(),month,input(2,60000,a.getId()));return "paid";}catch(ApiException error){return error.getErrorCode().name();}};
        Callable<String> shrink=()->{start.await();try{budgetService.updateItem(owner.getId(),month,a.getId(),new BudgetDtos.ItemInput("경합",new ApiId(a.getCategory().getId()),50000L));return "resized";}catch(ApiException error){return error.getErrorCode().name();}};
        try(var executor=Executors.newFixedThreadPool(2)) { var x=executor.submit(pay);var y=executor.submit(shrink);start.countDown();var results=List.of(x.get(60,TimeUnit.SECONDS),y.get(60,TimeUnit.SECONDS));assertThat(results).containsAnyOf("PAYMENT_EXCEEDS_BUDGET","BUDGET_BELOW_PAID");assertThat(results.stream().filter(value->value.equals("paid")||value.equals("resized")).count()).isEqualTo(1); }
        var result=summary(owner,a.getId());assertThat(result.paid()).isLessThanOrEqualTo(result.amount());assertThat(result.remaining()).isGreaterThanOrEqualTo(0);
    }
    @Test void itemDeletionAndPaymentCannotLeaveDanglingReferences() throws Exception {
        User owner=user();var a=item(owner,month,100000,"삭제 경합");var start=new CountDownLatch(1);
        Callable<String> pay=()->{start.await();try{service.create(owner.getId(),month,input(2,60000,a.getId()));return "paid";}catch(ApiException error){return error.getErrorCode().name();}};
        Callable<String> remove=()->{start.await();try{budgetService.deleteItem(owner.getId(),month,a.getId());return "deleted";}catch(ApiException error){return error.getErrorCode().name();}};
        try(var executor=Executors.newFixedThreadPool(2)) { var x=executor.submit(pay);var y=executor.submit(remove);start.countDown();var results=List.of(x.get(60,TimeUnit.SECONDS),y.get(60,TimeUnit.SECONDS));assertThat(results).containsAnyOf("REFERENCED","NOT_FOUND");assertThat(results.stream().filter(value->value.equals("paid")||value.equals("deleted")).count()).isEqualTo(1); }
        if(items.findById(a.getId()).isPresent()) assertThat(records.sumAmountByItemId(a.getId())).isEqualTo(60000);else assertThat(records.sumAmountByItemId(a.getId())).isZero();
    }
    @Test void httpCrudAndSwaggerGroupUseTheApprovedContract() throws Exception {
        User owner=user();var a=item(owner,month,100000,"HTTP");var session=session(owner);String path="/api/v1/months/"+month+"/fixed-payments";
        String body="{\"date\":\""+month+"-02\",\"time\":\"12:30\",\"title\":\"지급\",\"amount\":100,\"budgetItemId\":\""+a.getId()+"\"}";
        mvc.perform(post(path).session(session).contentType("application/json").content(body)).andExpect(status().isForbidden());
        var created=mvc.perform(post(path).session(session).with(SecurityMockMvcRequestPostProcessors.csrf()).contentType("application/json").content(body)).andExpect(status().isCreated()).andExpect(header().string("Cache-Control","no-store")).andReturn();
        String id=mapper.readTree(created.getResponse().getContentAsString()).path("id").asText();
        mvc.perform(get(path).session(session).param("budgetItemId",a.getId().toString())).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(100)).andExpect(jsonPath("$.incomeTotal").value(0));
        mvc.perform(put(path+"/"+id).session(session).with(SecurityMockMvcRequestPostProcessors.csrf()).contentType("application/json").content(body.replace("100,","150,"))).andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(150));
        mvc.perform(get(path+"/"+id)).andExpect(status().isUnauthorized());
        mvc.perform(delete(path+"/"+id).session(session).with(SecurityMockMvcRequestPostProcessors.csrf())).andExpect(status().isNoContent()).andExpect(content().string(""));mvc.perform(get(path+"/"+id).session(session)).andExpect(status().isNotFound());
        var client=java.net.http.HttpClient.newHttpClient();var document=client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:"+port+"/v3/api-docs/payments")).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(document.statusCode()).isEqualTo(200);var doc=mapper.readTree(document.body());assertThat(doc.path("paths").size()).isEqualTo(2);for(String key:doc.path("paths").propertyNames())assertThat(key).contains("/fixed-payments");
        assertThat(doc.path("paths").path("/api/v1/months/{month}/fixed-payments").path("get").path("parameters").toString()).contains("budgetItemId");
        assertThat(doc.path("paths").path("/api/v1/months/{month}/fixed-payments/{id}").path("delete").path("responses").has("204")).isTrue();
        assertThat(doc.path("paths").path("/api/v1/months/{month}/fixed-payments").path("post").path("security").toString()).contains("sessionCookie","csrfToken");
        assertThat(doc.path("components").path("schemas").path("PaymentResponse").path("required").toString()).contains("id","date","time","title","amount","budgetItemId");
        var config=client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:"+port+"/v3/api-docs/swagger-config")).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());assertThat(mapper.readTree(config.body()).path("urls").toString()).contains("payments");
    }
    MockHttpSession session(User user) {
        var principal=new AuthPrincipal(user.getId(),user.getEmail(),"Payment tester");var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal,null,List.of()));
        var session=new MockHttpSession();session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,context);session.setAttribute(SessionExpiryFilter.LOGIN_AT,Instant.now());session.setAttribute(SessionExpiryFilter.LAST_SEEN,Instant.now());return session;
    }
}
