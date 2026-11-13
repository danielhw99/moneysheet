package com.danielhw99.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.Optional;

import com.danielhw99.category.api.CategoryDirectionValue;
import com.danielhw99.category.api.CategoryInput;
import com.danielhw99.category.api.CategoryScopeValue;
import com.danielhw99.category.entity.CategoryDirection;
import com.danielhw99.category.entity.CategoryScope;
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
    void staleLookupRecoversFromRealMysqlUniqueViolation() {
        Long userId = user("category-stale@example.com");
        var input = new CategoryInput("Rent", CategoryScopeValue.FIXED_BUDGET, null);
        var first = service.createOrReuse(userId, input);
        CategoryRepository staleLookup = spy(categories);
        doReturn(Optional.empty()).doAnswer(invocation -> categories.findByUser_IdAndScopeAndDirectionAndNameKey(
                invocation.getArgument(0), invocation.getArgument(1),
                invocation.getArgument(2), invocation.getArgument(3)))
                .when(staleLookup).findByUser_IdAndScopeAndDirectionAndNameKey(
                        userId, CategoryScope.FIXED_BUDGET, CategoryDirection.NONE, "rent");

        var recovered = new CategoryService(staleLookup, users).createOrReuse(userId, input);

        assertThat(recovered.id()).isEqualTo(first.id());
        assertThat(categories.findByUser_IdOrderByIdAsc(userId)).hasSize(1);
    }

    @Test
    void foreignKeyFailureIsNotReportedAsReuse() {
        Long absentUserId = Long.MAX_VALUE;
        User phantom = new User("phantom@example.com", "phantom@example.com", "Test", "hash", "v1",
                LocalDateTime.now());
        ReflectionTestUtils.setField(phantom, "id", absentUserId);
        UserRepository fakeUsers = mock(UserRepository.class);
        when(fakeUsers.findById(absentUserId)).thenReturn(Optional.of(phantom));

        assertThatThrownBy(() -> new CategoryService(categories, fakeUsers).createOrReuse(absentUserId,
                new CategoryInput("Ghost", CategoryScopeValue.FIXED_BUDGET, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(categories.findByUser_IdOrderByIdAsc(absentUserId)).isEmpty();
    }

    private Long user(String email) {
        return users.saveAndFlush(new User(email, email, "Test", "hash", "v1", LocalDateTime.now())).getId();
    }
}
