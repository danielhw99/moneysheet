package com.danielhw99.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import com.danielhw99.budget.api.BudgetDtos;
import com.danielhw99.budget.api.BudgetDtos.Food;
import com.danielhw99.budget.api.BudgetDtos.ItemInput;
import com.danielhw99.budget.api.BudgetDtos.Kind;
import com.danielhw99.budget.api.BudgetDtos.SetupInput;
import com.danielhw99.budget.api.BudgetDtos.SetupItem;
import com.danielhw99.budget.entity.BudgetSetupStatus;
import com.danielhw99.budget.entity.FixedBudgetItem;
import com.danielhw99.budget.entity.FixedBudgetKind;
import com.danielhw99.budget.entity.MonthlyBudget;
import com.danielhw99.budget.repository.FixedBudgetItemRepository;
import com.danielhw99.budget.repository.MonthlyBudgetRepository;
import com.danielhw99.budget.service.BudgetService;
import com.danielhw99.category.entity.Category;
import com.danielhw99.category.entity.CategoryDirection;
import com.danielhw99.category.entity.CategoryScope;
import com.danielhw99.category.repository.CategoryRepository;
import com.danielhw99.common.api.ApiId;
import com.danielhw99.common.error.ApiException;
import com.danielhw99.common.error.ErrorCode;
import com.danielhw99.payment.entity.FixedPayment;
import com.danielhw99.payment.repository.FixedPaymentRepository;
import com.danielhw99.user.entity.User;
import com.danielhw99.user.repository.UserRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.mysql.MySQLContainer;

@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=false"})
@ContextConfiguration(initializers = BudgetIntegrationTest.Database.class)
class BudgetIntegrationTest {
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4.13")
            .withDatabaseName("moneysheet_budget_test")
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

    @Autowired BudgetService service;
    @Autowired UserRepository users;
    @Autowired CategoryRepository categories;
    @Autowired MonthlyBudgetRepository budgets;
    @Autowired FixedBudgetItemRepository items;
    @Autowired FixedPaymentRepository payments;

    private YearMonth month() { return YearMonth.now(ZoneId.of("Asia/Seoul")); }

    private User user() {
        String email = UUID.randomUUID() + "@example.com";
        return users.saveAndFlush(new User(email, email, "Budget tester", "hash", "v1", LocalDateTime.now()));
    }

    private Category fixedCategory(User user) {
        return categories.saveAndFlush(new Category(user, CategoryScope.FIXED_BUDGET,
                CategoryDirection.NONE, "Rent", "rent"));
    }

    @Test void firstMonthDraftIsNotPersistedAndSetupBecomesReady() {
        User owner = user();
        YearMonth month = month();
        var first = service.prepare(owner.getId(), month);
        assertThat(first.status()).isEqualTo("draft");
        assertThat(first.items()).singleElement().satisfies(item -> {
            assertThat(item.kind()).isEqualTo(Kind.FREE_ALLOWANCE);
            assertThat(item.id()).isNull();
            assertThat(item.amount()).isNull();
        });
        assertThat(items.findByMonthlyBudget_IdOrderByIdAsc(budgets.findByUser_IdAndMonthStart(owner.getId(), month.atDay(1)).orElseThrow().getId()))
                .isEmpty();
        assertCode(ErrorCode.BUDGET_NOT_READY, () -> service.get(owner.getId(), month));
        var ready = service.setup(owner.getId(), month, new SetupInput(List.of(), new Food(10L, 20L, 30L)));
        assertThat(ready.status()).isEqualTo("ready");
        assertThat(ready.items()).isEmpty();
        assertThat(ready.foodSummary().installments()).hasSize(3);
        assertThat(service.prepare(owner.getId(), month).status()).isEqualTo("ready");
        assertCode(ErrorCode.CONFLICT, () -> service.setup(owner.getId(), month, new SetupInput(List.of(), new Food(1L, 1L, 1L))));
    }

    @Test void previousMonthCopiesOnceWithNewItemIdAndSetupKeepsId() {
        User owner = user();
        Category category = fixedCategory(owner);
        YearMonth month = month();
        MonthlyBudget previous = budgets.saveAndFlush(new MonthlyBudget(owner, month.minusMonths(1).atDay(1),
                BudgetSetupStatus.READY, 10L, 20L, 30L));
        FixedBudgetItem original = items.saveAndFlush(new FixedBudgetItem(previous, category,
                FixedBudgetKind.FIXED_EXPENSE, "Rent", 100L));
        var copy = service.prepare(owner.getId(), month);
        assertThat(copy.copiedFromMonth()).isEqualTo(month.minusMonths(1).toString());
        assertThat(copy.items()).singleElement().satisfies(item -> {
            assertThat(item.id().value()).isNotEqualTo(original.getId());
            assertThat(item.amount()).isEqualTo(100L);
        });
        assertThat(service.prepare(owner.getId(), month).copiedFromMonth()).isNull();
        assertThat(service.prepare(owner.getId(), month).items()).hasSize(1);
        var copied = copy.items().get(0);
        var ready = service.setup(owner.getId(), month, new SetupInput(List.of(new SetupItem(copied.id(),
                Kind.FIXED_EXPENSE, "Rent changed", new ApiId(category.getId()), 120L)), new Food(10L, 20L, 30L)));
        assertThat(ready.items()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(copied.id());
            assertThat(item.name()).isEqualTo("Rent changed");
        });
        assertCode(ErrorCode.BUDGET_READ_ONLY, () -> service.prepare(owner.getId(), month.minusMonths(1)));
        assertCode(ErrorCode.FUTURE_MONTH_NOT_ALLOWED, () -> service.prepare(owner.getId(), month.plusMonths(1)));
    }

    @Test void readyCrudGuardsPaymentsOwnershipAndKind() {
        User owner = user();
        User outsider = user();
        Category category = fixedCategory(owner);
        YearMonth month = month();
        service.prepare(owner.getId(), month);
        service.setup(owner.getId(), month, new SetupInput(List.of(), new Food(10L, 20L, 30L)));
        var input = new ItemInput(Kind.FIXED_EXPENSE, "Rent", new ApiId(category.getId()), 100L);
        var created = service.createItem(owner.getId(), month, input);
        assertCode(ErrorCode.NOT_FOUND, () -> service.updateItem(outsider.getId(), month, created.id().value(), input));
        assertCode(ErrorCode.VALIDATION, () -> service.updateItem(owner.getId(), month, created.id().value(),
                new ItemInput(Kind.FREE_ALLOWANCE, "Rent", new ApiId(category.getId()), 100L)));
        FixedBudgetItem item = items.findById(created.id().value()).orElseThrow();
        payments.saveAndFlush(new FixedPayment(item, month.atDay(1), LocalTime.NOON, "paid", 60L));
        assertCode(ErrorCode.BUDGET_BELOW_PAID, () -> service.updateItem(owner.getId(), month, created.id().value(),
                new ItemInput(Kind.FIXED_EXPENSE, "Rent", new ApiId(category.getId()), 50L)));
        assertCode(ErrorCode.REFERENCED, () -> service.deleteItem(owner.getId(), month, created.id().value()));
        assertThat(service.get(owner.getId(), month).items().get(0).paid()).isEqualTo(60);
        assertThat(service.updateItem(owner.getId(), month, created.id().value(),
                new ItemInput(Kind.FIXED_EXPENSE, "Rent", new ApiId(category.getId()), 80L)).amount()).isEqualTo(80);
        assertThat(service.updateFood(owner.getId(), month, new Food(1L, 1L, 1L)).food().weekday()).isEqualTo(1);
    }

    @Test void setupValidationRollsBackAndOnlyOneAllowanceCanExist() {
        User owner = user();
        Category category = fixedCategory(owner);
        YearMonth month = month();
        service.prepare(owner.getId(), month);
        var allowance = new SetupItem(null, Kind.FREE_ALLOWANCE, "Pocket", new ApiId(category.getId()), 1L);
        assertCode(ErrorCode.VALIDATION, () -> service.setup(owner.getId(), month,
                new SetupInput(List.of(allowance, allowance), new Food(1L, 1L, 1L))));
        assertThat(budgets.findByUser_IdAndMonthStart(owner.getId(), month.atDay(1)).orElseThrow().getSetupStatus())
                .isEqualTo(BudgetSetupStatus.DRAFT);
        assertThat(items.findByMonthlyBudget_IdOrderByIdAsc(budgets.findByUser_IdAndMonthStart(owner.getId(), month.atDay(1)).orElseThrow().getId()))
                .isEmpty();
        service.setup(owner.getId(), month, new SetupInput(List.of(allowance), new Food(1L, 1L, 1L)));
        assertCode(ErrorCode.CONFLICT, () -> service.createItem(owner.getId(), month,
                new ItemInput(Kind.FREE_ALLOWANCE, "Other", new ApiId(category.getId()), 2L)));
    }

    private void assertCode(ErrorCode code, Runnable operation) {
        assertThatThrownBy(operation::run).isInstanceOf(ApiException.class)
                .extracting("errorCode").isEqualTo(code);
    }
}
