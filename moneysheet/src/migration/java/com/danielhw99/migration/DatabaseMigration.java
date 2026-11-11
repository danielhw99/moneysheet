package com.danielhw99.migration;

import java.util.Arrays;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.CoreMigrationType;
import org.flywaydb.core.api.MigrationInfo;

public final class DatabaseMigration {

    private DatabaseMigration() {
    }

    public static void main(String[] args) {
        try {
            if (args.length != 1 || !(args[0].equals("info")
                    || args[0].equals("validate") || args[0].equals("migrate"))) {
                throw new IllegalArgumentException("Only info, validate and migrate are supported.");
            }

            String url = requireEnvironment("MIGRATION_DB_URL");
            String user = requireEnvironment("MIGRATION_DB_USERNAME");
            String password = requireEnvironment("MIGRATION_DB_PASSWORD");
            if (!url.startsWith("jdbc:mysql://")) {
                throw new IllegalArgumentException("MIGRATION_DB_URL must be a MySQL JDBC URL.");
            }
            if (!user.equals("moneysheet_migration")) {
                throw new IllegalArgumentException("Use the moneysheet_migration account.");
            }

            Flyway flyway = Flyway.configure()
                    .dataSource(url, user, password)
                    .locations("classpath:db/migration")
                    .cleanDisabled(true)
                    .createSchemas(false)
                    .baselineOnMigrate(false)
                    .validateOnMigrate(true)
                    .validateMigrationNaming(true)
                    .load();

            switch (args[0]) {
                case "info" -> {
                    for (MigrationInfo migration : flyway.info().all()) {
                        System.out.printf("%s | %s | %s%n", migration.getVersion(),
                                migration.getDescription(), migration.getState());
                    }
                }
                case "validate" -> flyway.validate();
                case "migrate" -> {
                    boolean hasLocalSql = Arrays.stream(flyway.info().all())
                            .anyMatch(migration -> migration.getState().isResolved()
                                    && migration.getType() == CoreMigrationType.SQL);
                    if (!hasLocalSql) {
                        throw new IllegalStateException("No migration scripts found; migrate refused.");
                    }
                    flyway.migrate();
                }
                default -> throw new IllegalArgumentException("Unsupported operation.");
            }
            System.out.println("Flyway " + args[0] + " completed.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            System.err.println(exception.getMessage());
            System.exit(1);
        } catch (Exception exception) {
            // Connection exceptions may contain credentials or connection properties.
            System.err.println("Flyway operation failed (" + exception.getClass().getSimpleName()
                    + "). Check the connection and migration state; do not retry blindly.");
            System.exit(1);
        }
    }

    private static String requireEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Required environment variable is missing: " + name);
        }
        return value;
    }
}
