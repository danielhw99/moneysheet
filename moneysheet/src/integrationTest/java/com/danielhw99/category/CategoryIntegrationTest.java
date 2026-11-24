package com.danielhw99.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.Optional;

import com.danielhw99.category.api.CategoryDirectionValue;
import com.danielhw99.category.api.CategoryInput;
import com.danielhw99.category.api.CategoryScopeValue;
import com.danielhw99.category.repository.CategoryRepository;
import com.danielhw99.category.service.CategoryService;
import com.danielhw99.user.entity.User;
import com.danielhw99.user.repository.UserRepository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.mysql.MySQLContainer;

@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=false"})
@ContextConfiguration(initializers = CategoryIntegrationTest.Database.class)
class CategoryIntegrationTest {
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;

    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4.13")
            .withDatabaseName("moneysheet_category_test")
            .withCreateContainerCmdModifier(command -> command.getHostConfig().withPortBindings(
                    new com.github.dockerjava.api.model.PortBinding(
                            com.github.dockerjava.api.model.Ports.Binding.bindIp("127.0.0.1"),
                            com.github.dockerjava.api.model.ExposedPort.tcp(3306))));

    static class Database implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            MYSQL.start();
            Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                    .cleanDisabled(true).createSchemas(false).baselineOnMigrate(false).load().migrate();
            TestPropertyValues.of("spring.datasource.url=" + MYSQL.getJdbcUrl(),
                    "spring.datasource.username=" + MYSQL.getUsername(),
                    "spring.datasource.password=" + MYSQL.getPassword(),
                    "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver").applyTo(context);
        }
    }

    @Autowired CategoryService service;
    @Autowired CategoryRepository categories;
    @Autowired UserRepository users;

    @Test
    void concurrentSameKeyReturnsOneCategory() throws Exception {
        Long userId = user("category-race@example.com");
        var start = new CountDownLatch(1);
        var input = new CategoryInput(" Food ", CategoryScopeValue.MAIN_TRANSACTION,
                CategoryDirectionValue.EXPENSE);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); return service.createOrReuse(userId, input); });
            var second = executor.submit(() -> { start.await(); return service.createOrReuse(userId,
                    new CategoryInput("FOOD", CategoryScopeValue.MAIN_TRANSACTION,
                            CategoryDirectionValue.EXPENSE)); });
            start.countDown();
            assertThat(first.get(30, TimeUnit.SECONDS).id())
                    .isEqualTo(second.get(30, TimeUnit.SECONDS).id());
        }
        assertThat(categories.findByUser_IdOrderByIdAsc(userId)).hasSize(1);
    }

    @Test
    void sameNameInOtherUserScopeOrDirectionIsSeparate() {
        Long first = user("category-one@example.com");
        Long second = user("category-two@example.com");
        var expense = new CategoryInput("Shared", CategoryScopeValue.MAIN_TRANSACTION,
                CategoryDirectionValue.EXPENSE);
        var firstId = service.createOrReuse(first, expense).id();
        assertThat(service.createOrReuse(second, expense).id()).isNotEqualTo(firstId);
        assertThat(service.createOrReuse(first, new CategoryInput("Shared", CategoryScopeValue.MAIN_TRANSACTION,
                CategoryDirectionValue.INCOME)).id()).isNotEqualTo(firstId);
        assertThat(service.createOrReuse(first, new CategoryInput("Shared", CategoryScopeValue.FIXED_BUDGET,
                null)).id()).isNotEqualTo(firstId);
        assertThat(categories.findByUser_IdOrderByIdAsc(first)).hasSize(3);
    }

    @Test
    void unexpectedStaleLookupDoesNotHideIntegrityFailure() {
        Long userId = user("category-stale@example.com");
        var input = new CategoryInput("Rent", CategoryScopeValue.FIXED_BUDGET, null);
        var first = service.createOrReuse(userId, input);
        AtomicBoolean firstLookup = new AtomicBoolean(true);
        CategoryRepository staleLookup = (CategoryRepository) Proxy.newProxyInstance(
                CategoryRepository.class.getClassLoader(), new Class<?>[] {CategoryRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("findByUser_IdAndScopeAndDirectionAndNameKey")
                            && firstLookup.getAndSet(false)) return Optional.empty();
                    try {
                        return method.invoke(categories, args);
                    } catch (InvocationTargetException failure) {
                        throw failure.getCause();
                    }
                });

        assertThatThrownBy(() -> new org.springframework.transaction.support.TransactionTemplate(transactionManager)
                .execute(status -> new CategoryService(staleLookup, users).createOrReuse(userId, input)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(service.createOrReuse(userId,input).id()).isEqualTo(first.id());
        assertThat(categories.findByUser_IdOrderByIdAsc(userId)).hasSize(1);
    }

    @Test
    void foreignKeyFailureIsNotReportedAsReuse() {
        Long absentUserId = Long.MAX_VALUE;
        User phantom = new User("phantom@example.com", "phantom@example.com", "Test", "hash", "v1",
                LocalDateTime.now());
        ReflectionTestUtils.setField(phantom, "id", absentUserId);
        UserRepository fakeUsers = mock(UserRepository.class);
        when(fakeUsers.findWithLockingById(absentUserId)).thenReturn(Optional.of(phantom));

        assertThatThrownBy(() -> new org.springframework.transaction.support.TransactionTemplate(transactionManager)
                .execute(status -> new CategoryService(categories, fakeUsers).createOrReuse(absentUserId,
                new CategoryInput("Ghost", CategoryScopeValue.FIXED_BUDGET, null))))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(categories.findByUser_IdOrderByIdAsc(absentUserId)).isEmpty();
    }

    @Autowired com.danielhw99.budget.service.BudgetService budgetService;
    @Autowired com.danielhw99.budget.repository.FixedBudgetItemRepository budgetItems;
    @Autowired com.danielhw99.payment.repository.FixedPaymentRepository payments;
    @Autowired com.danielhw99.transaction.service.TransactionService transactions;

    @Test void deletionPreservesBudgetPaymentsAndTransactionsAsUncategorized() {
        Long owner = user("category-delete@example.com");
        var month = java.time.YearMonth.now(java.time.ZoneId.of("Asia/Seoul"));
        var fixed = service.createOrReuse(owner, new CategoryInput("Fixed", CategoryScopeValue.FIXED_BUDGET, null));
        var expense = service.createOrReuse(owner, new CategoryInput("Expense", CategoryScopeValue.MAIN_TRANSACTION, CategoryDirectionValue.EXPENSE));
        budgetService.prepare(owner, month);
        budgetService.setup(owner, month, new com.danielhw99.budget.api.BudgetDtos.SetupInput(java.util.List.of(),
                new com.danielhw99.budget.api.BudgetDtos.Food(11L, 15L, 10L),month.atDay(1)));
        var item = budgetService.createItem(owner, month, new com.danielhw99.budget.api.BudgetDtos.ItemInput("Rent", fixed.id(), 100L));
        var payment = payments.saveAndFlush(new com.danielhw99.payment.entity.FixedPayment(
                budgetItems.findById(item.id().value()).orElseThrow(), month.atDay(1), java.time.LocalTime.NOON, "partial", 40L));
        var transaction = transactions.create(owner, month, new com.danielhw99.transaction.api.TransactionInput(
                month.atDay(1), java.time.LocalTime.NOON, "lunch", 9L,
                com.danielhw99.transaction.api.TransactionDirectionValue.EXPENSE, expense.id()));

        service.update(owner, expense.id().value(), "Renamed");
        assertThat(transactions.get(owner, month, transaction.id().value()).categoryId()).isEqualTo(expense.id());
        assertThat(service.list(owner, null, null)).anyMatch(value -> value.name().equals("Renamed"));
        service.delete(owner, fixed.id().value());
        service.delete(owner, expense.id().value());
        var summary = budgetService.get(owner, month).items().getFirst();
        assertThat(summary.categoryId()).isNull();
        assertThat(summary.name()).isEqualTo("Rent");
        assertThat(summary.amount()).isEqualTo(100L);
        assertThat(summary.paid()).isEqualTo(40L);
        assertThat(payments.findById(payment.getId())).isPresent();
        assertThat(transactions.get(owner, month, transaction.id().value()).categoryId()).isNull();
        assertThat(transactions.get(owner, month, transaction.id().value()).amount()).isEqualTo(9L);
        assertThatThrownBy(() -> service.delete(owner, fixed.id().value()))
                .isInstanceOf(com.danielhw99.common.error.ApiException.class)
                .extracting("errorCode").isEqualTo(com.danielhw99.common.error.ErrorCode.NOT_FOUND);
    }

    @Test void duplicateRenameRollsBackAndOtherScopesStillAllowSameName() {
        Long owner = user("category-rename@example.com");
        var first = service.createOrReuse(owner, new CategoryInput("Food", CategoryScopeValue.MAIN_TRANSACTION, CategoryDirectionValue.EXPENSE));
        var second = service.createOrReuse(owner, new CategoryInput("Other", CategoryScopeValue.MAIN_TRANSACTION, CategoryDirectionValue.EXPENSE));
        service.createOrReuse(owner, new CategoryInput("Food", CategoryScopeValue.MAIN_TRANSACTION, CategoryDirectionValue.INCOME));
        assertThatThrownBy(() -> service.update(owner, second.id().value(), " FOOD "))
                .isInstanceOf(com.danielhw99.common.error.ApiException.class)
                .extracting("errorCode").isEqualTo(com.danielhw99.common.error.ErrorCode.CONFLICT);
        assertThat(categories.findById(second.id().value()).orElseThrow().getName()).isEqualTo("Other");
        assertThat(service.update(owner, first.id().value(), " FOOD ").name()).isEqualTo("FOOD");
        Long stranger = user("category-stranger@example.com");
        assertThatThrownBy(() -> service.delete(stranger, first.id().value()))
                .isInstanceOf(com.danielhw99.common.error.ApiException.class)
                .extracting("errorCode").isEqualTo(com.danielhw99.common.error.ErrorCode.NOT_FOUND);
        assertThat(categories.findById(first.id().value())).isPresent();
    }

    @Test void deletionSerializesAgainstBudgetWriteWithoutDanglingReference() throws Exception {
        Long owner = user("category-delete-race@example.com");
        var month = java.time.YearMonth.now(java.time.ZoneId.of("Asia/Seoul"));
        var fixed = service.createOrReuse(owner, new CategoryInput("Fixed", CategoryScopeValue.FIXED_BUDGET, null));
        budgetService.prepare(owner, month);
        budgetService.setup(owner, month, new com.danielhw99.budget.api.BudgetDtos.SetupInput(java.util.List.of(),
                new com.danielhw99.budget.api.BudgetDtos.Food(1L, 1L, 1L),month.atDay(1)));
        var held = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var tx = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var deleting = executor.submit(() -> tx.execute(status -> {
                service.delete(owner, fixed.id().value());
                held.countDown();
                try { if (!release.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("release timeout"); }
                catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
                return null;
            }));
            try {
                assertThat(held.await(20, TimeUnit.SECONDS)).isTrue();
                var writing = executor.submit(() -> {
                    try {
                        budgetService.createItem(owner, month, new com.danielhw99.budget.api.BudgetDtos.ItemInput("late", fixed.id(), 10L));
                        return "created";
                    } catch (com.danielhw99.common.error.ApiException failure) { return failure.getErrorCode().name(); }
                });
                awaitOwnerLockWait(owner);
                assertThat(writing.isDone()).isFalse();
                release.countDown();
                deleting.get(20, TimeUnit.SECONDS);
                assertThat(writing.get(20, TimeUnit.SECONDS)).isEqualTo("NOT_FOUND");
                assertThat(budgetService.get(owner, month).items()).isEmpty();
            } finally { release.countDown(); }
        }
    }

    private void awaitOwnerLockWait(Long owner) throws Exception {
        // Only this disposable Testcontainers root account can inspect performance_schema.
        String sql = "SELECT COUNT(*) FROM performance_schema.data_lock_waits w "
                + "JOIN performance_schema.data_locks l ON l.ENGINE=w.ENGINE AND l.ENGINE_LOCK_ID=w.BLOCKING_ENGINE_LOCK_ID "
                + "WHERE l.OBJECT_SCHEMA=? AND l.OBJECT_NAME='users' AND l.INDEX_NAME='PRIMARY' AND l.LOCK_DATA=?";
        try (var connection = java.sql.DriverManager.getConnection(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword());
             var query = connection.prepareStatement(sql)) {
            query.setString(1, MYSQL.getDatabaseName());
            query.setString(2, owner.toString());
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (System.nanoTime() < deadline) {
                try (var rows = query.executeQuery()) {
                    rows.next();
                    if (rows.getLong(1) > 0) return;
                }
                Thread.sleep(25);
            }
        }
        throw new AssertionError("Budget write never waited on the owned user row");
    }

    private Long user(String email) {
        return users.saveAndFlush(new User(email, email, "Test", "hash", "v1", LocalDateTime.now())).getId();
    }
}
