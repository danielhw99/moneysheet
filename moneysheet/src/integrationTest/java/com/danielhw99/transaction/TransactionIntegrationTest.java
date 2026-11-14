package com.danielhw99.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import com.danielhw99.budget.api.BudgetDtos;
import com.danielhw99.budget.entity.BudgetSetupStatus;
import com.danielhw99.budget.entity.MonthlyBudget;
import com.danielhw99.budget.repository.MonthlyBudgetRepository;
import com.danielhw99.category.entity.Category;
import com.danielhw99.category.entity.CategoryDirection;
import com.danielhw99.category.entity.CategoryScope;
import com.danielhw99.category.repository.CategoryRepository;
import com.danielhw99.common.api.ApiId;
import com.danielhw99.common.error.ApiException;
import com.danielhw99.common.error.ErrorCode;
import com.danielhw99.user.entity.User;
import com.danielhw99.user.repository.UserRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import com.danielhw99.transaction.api.TransactionInput;
import com.danielhw99.transaction.api.TransactionDirectionValue;
import com.danielhw99.transaction.service.TransactionService;
import com.danielhw99.transaction.service.TransactionReadService;
import com.danielhw99.transaction.repository.MainTransactionRepository;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.mysql.MySQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=false", "spring.profiles.active=local"})
@ContextConfiguration(initializers = TransactionIntegrationTest.Database.class)
class TransactionIntegrationTest {
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4.13")
            .withDatabaseName("moneysheet_transaction_test")
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

    @Autowired TransactionService service;
    @Autowired TransactionReadService reads;
    @Autowired UserRepository users;
    @Autowired CategoryRepository categories;
    @Autowired MonthlyBudgetRepository budgets;
    @Autowired MainTransactionRepository records;
    @LocalServerPort int port;
    private final YearMonth month = YearMonth.of(2026,10);
    private User user() {
        String email=UUID.randomUUID()+"@example.com";
        return users.saveAndFlush(new User(email,email,"Transactions tester","hash","v1",LocalDateTime.now()));
    }
    private void ready(User user) {
        budgets.saveAndFlush(new MonthlyBudget(user,month.atDay(1),BudgetSetupStatus.READY,11000L,15000L,10000L));
    }
    private TransactionInput input(int day,long amount,TransactionDirectionValue direction,ApiId category) {
        return new TransactionInput(month.atDay(day),LocalTime.NOON,"기록",amount,direction,category);
    }
    @Test void crudPastReadyMonthOwnershipAndCategoryValidation() {
        User owner=user(), other=user(); ready(owner); ready(other);
        var created=service.create(owner.getId(),month,input(1,9000,TransactionDirectionValue.EXPENSE,null));
        assertThat(service.get(owner.getId(),month,created.id().value()).categoryId()).isNull();
        assertCode(ErrorCode.NOT_FOUND,()->service.update(other.getId(),month,created.id().value(),input(1,1,TransactionDirectionValue.EXPENSE,null)));
        assertCode(ErrorCode.NOT_FOUND,()->service.get(owner.getId(),month.plusMonths(1),created.id().value()));
        var incomeCategory=categories.saveAndFlush(new Category(owner,CategoryScope.MAIN_TRANSACTION,CategoryDirection.INCOME,"용돈","용돈"));
        assertCode(ErrorCode.VALIDATION,()->service.update(owner.getId(),month,created.id().value(),input(1,1,TransactionDirectionValue.EXPENSE,new ApiId(incomeCategory.getId()))));
        assertCode(ErrorCode.NOT_FOUND,()->service.create(other.getId(),month,input(1,1,TransactionDirectionValue.INCOME,new ApiId(incomeCategory.getId()))));
        var changed=service.update(owner.getId(),month,created.id().value(),input(2,20000,TransactionDirectionValue.INCOME,new ApiId(incomeCategory.getId())));
        assertThat(changed.amount()).isEqualTo(20000);
        assertThat(service.get(owner.getId(),month,changed.id().value()).date()).isEqualTo(month.atDay(2));
        service.delete(owner.getId(),month,changed.id().value());
        assertCode(ErrorCode.NOT_FOUND,()->service.delete(owner.getId(),month,changed.id().value()));
    }
    @Test void keysetPagesAndWholeFilterTotalsStayIndependent() {
        User owner=user();ready(owner);
        var first=service.create(owner.getId(),month,input(1,100,TransactionDirectionValue.EXPENSE,null));
        var second=service.create(owner.getId(),month,input(1,200,TransactionDirectionValue.EXPENSE,null));
        var third=service.create(owner.getId(),month,input(2,1000,TransactionDirectionValue.INCOME,null));
        var page1=reads.list(owner.getId(),month,null,null,null,1);
        assertThat(page1.rows()).singleElement().satisfies(row->assertThat(row.id()).isEqualTo(third.id()));
        assertThat(page1.expenseTotal()).isEqualTo(300);
        assertThat(page1.incomeTotal()).isEqualTo(1000);
        assertThat(page1.total()).isEqualTo(700);
        var page2=reads.list(owner.getId(),month,null,null,page1.nextCursor(),1);
        assertThat(page2.rows().getFirst().id()).isEqualTo(second.id());
        assertThat(page2.total()).isEqualTo(700);
        var page3=reads.list(owner.getId(),month,null,null,page2.nextCursor(),1);
        assertThat(page3.rows().getFirst().id()).isEqualTo(first.id());
        assertThat(page3.nextCursor()).isNull();
        var expenses=reads.list(owner.getId(),month,month.atDay(1),TransactionDirectionValue.EXPENSE,null,100);
        assertThat(expenses.rows()).hasSize(2);
        assertThat(expenses.total()).isEqualTo(-300);
        assertThat(expenses.incomeTotal()).isZero();
        assertCode(ErrorCode.VALIDATION,()->reads.list(owner.getId(),month,null,TransactionDirectionValue.INCOME,page1.nextCursor(),1));
        assertCode(ErrorCode.VALIDATION,()->reads.list(owner.getId(),month,month.plusMonths(1).atDay(1),null,null,1));
        assertCode(ErrorCode.VALIDATION,()->reads.list(owner.getId(),month,null,null,null,101));
        User empty=user();ready(empty);
        var result=reads.list(empty.getId(),month,null,null,null,50);
        assertThat(result.rows()).isEmpty();assertThat(result.total()).isZero();assertThat(result.nextCursor()).isNull();
    }
    @Test void groupDocumentsContainOnlyTheirOwnPathsAndSecurity() throws Exception {
        var client=java.net.http.HttpClient.newHttpClient();
        var mapper=JsonMapper.builder().build();
        for(String group:List.of("auth","categories","budgets","transactions")) {
            var response=client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:"+port+"/v3/api-docs/"+group)).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            var doc=mapper.readTree(response.body());
            assertThat(doc.path("paths").size()).isGreaterThan(0);
            assertThat(doc.path("components").path("securitySchemes").has("sessionCookie")).isTrue();
            assertThat(doc.path("components").path("schemas").has("ApiError")).isTrue();
            var names=doc.path("paths").propertyNames();
            for(String path:names) {
                if(group.equals("auth")) assertThat(path.startsWith("/api/v1/auth/") || path.equals("/api/v1/me")).isTrue();
                if(group.equals("categories")) assertThat(path).isEqualTo("/api/v1/categories");
                if(group.equals("transactions")) assertThat(path).contains("/transactions");
                if(group.equals("budgets")) assertThat(path).doesNotContain("/transactions").contains("/api/v1/months/");
            }
            if(group.equals("auth")) assertThat(doc.path("paths").path("/api/v1/auth/csrf").path("get").path("parameters").size()).isZero();
            if(group.equals("transactions")) {
                var post=doc.path("paths").path("/api/v1/months/{month}/transactions").path("post");
                assertThat(post.path("security").toString()).contains("sessionCookie","csrfToken");
                assertThat(post.path("parameters").toString()).contains("X-CSRF-TOKEN");
            }
        }
    }
    private void assertCode(ErrorCode code,Runnable call) {
        assertThatThrownBy(call::run).isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo(code);
    }
}
