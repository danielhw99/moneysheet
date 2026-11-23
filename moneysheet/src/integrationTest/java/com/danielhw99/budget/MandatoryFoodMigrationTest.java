package com.danielhw99.budget;

import static org.assertj.core.api.Assertions.*;
import java.sql.Connection;
import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.mysql.MySQLContainer;

class MandatoryFoodMigrationTest {
    private MySQLContainer database() {
        return new MySQLContainer("mysql:8.4.13").withDatabaseName("mandatory_food_migration")
            .withCreateContainerCmdModifier(command -> command.getHostConfig().withPortBindings(
                new com.github.dockerjava.api.model.PortBinding(
                    com.github.dockerjava.api.model.Ports.Binding.bindIp("127.0.0.1"),
                    com.github.dockerjava.api.model.ExposedPort.tcp(3306))));
    }
    private Flyway flyway(MySQLContainer db,String target) {
        return Flyway.configure().dataSource(db.getJdbcUrl(),db.getUsername(),db.getPassword())
            .target(target).cleanDisabled(true).createSchemas(false).baselineOnMigrate(false).load();
    }
    private Connection connect(MySQLContainer db) throws Exception {
        return DriverManager.getConnection(db.getJdbcUrl(),db.getUsername(),db.getPassword());
    }
    private void seed(Connection c,boolean invalid) throws Exception {
        try(var s=c.createStatement()) {
            s.executeUpdate("INSERT INTO users (id,email,email_key,nickname,password_hash,privacy_policy_version,privacy_agreed_at,created_at,updated_at) VALUES (1,'migration@example.com','migration@example.com','Migration','hash','v1',NOW(),NOW(),NOW())");
            s.executeUpdate("INSERT INTO monthly_budgets (id,user_id,month_start,setup_status,food_enabled,food_payment_mode,food_weekday,food_saturday,food_sunday,created_at,updated_at) VALUES (1,1,'2026-10-01','DRAFT',NULL,'THREE_INSTALLMENTS',NULL,NULL,NULL,NOW(),NOW())");
            s.executeUpdate("INSERT INTO monthly_budgets (id,user_id,month_start,setup_status,food_enabled,food_payment_mode,food_weekday,food_saturday,food_sunday,created_at,updated_at) VALUES (2,1,'2026-11-01','READY',"+(invalid?"FALSE":"TRUE")+",'MONTH_START',"+(invalid?"NULL,NULL,NULL":"11000,15000,10000")+",NOW(),NOW())");
        }
    }
    private long scalar(Connection c,String query) throws Exception {
        try(var s=c.createStatement();var rows=s.executeQuery(query)){rows.next();return rows.getLong(1);}
    }
    @Test void v10PreservesDraftReadyAmountsAndModeAndIsNotReapplied() throws Exception {
        try(var db=database()) {
            db.start();flyway(db,"9").migrate();
            try(var c=connect(db)){seed(c,false);}
            var latest=flyway(db,"latest");assertThat(latest.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(latest.migrate().migrationsExecuted).isZero();latest.validate();
            try(var c=connect(db)) {
                assertThat(scalar(c,"SELECT COUNT(*) FROM users")).isEqualTo(1);
                assertThat(scalar(c,"SELECT COUNT(*) FROM monthly_budgets")).isEqualTo(2);
                assertThat(scalar(c,"SELECT COUNT(*) FROM monthly_budgets WHERE id=1 AND setup_status='DRAFT' AND food_weekday IS NULL AND food_saturday IS NULL AND food_sunday IS NULL")).isEqualTo(1);
                assertThat(scalar(c,"SELECT COUNT(*) FROM monthly_budgets WHERE id=2 AND setup_status='READY' AND food_payment_mode='MONTH_START' AND food_weekday=11000 AND food_saturday=15000 AND food_sunday=10000")).isEqualTo(1);
                assertThat(scalar(c,"SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='monthly_budgets' AND column_name='food_enabled'")).isZero();
            }
        }
    }
    @Test void incompatibleReadyRowStopsV10WithoutDeletingOrInventingFood() throws Exception {
        try(var db=database()) {
            db.start();flyway(db,"9").migrate();
            try(var c=connect(db)){seed(c,true);}
            assertThatThrownBy(()->flyway(db,"latest").migrate()).isInstanceOf(org.flywaydb.core.api.FlywayException.class);
            try(var c=connect(db)) {
                assertThat(scalar(c,"SELECT COUNT(*) FROM monthly_budgets")).isEqualTo(2);
                assertThat(scalar(c,"SELECT COUNT(*) FROM monthly_budgets WHERE id=2 AND setup_status='READY' AND food_enabled=FALSE AND food_weekday IS NULL")).isEqualTo(1);
            }
        }
    }
}
