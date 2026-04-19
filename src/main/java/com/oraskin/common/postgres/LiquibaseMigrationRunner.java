package com.oraskin.common.postgres;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

public final class LiquibaseMigrationRunner {

    private static final String MASTER_CHANGELOG = "db/changelog/db.changelog-master.xml";

    private final PostgresConnectionFactory connectionFactory;

    public LiquibaseMigrationRunner(PostgresConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory);
    }

    public void runMigrations() {
        try (Connection connection = connectionFactory.openConnection()) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            Liquibase liquibase = new Liquibase(
                    MASTER_CHANGELOG,
                    new ClassLoaderResourceAccessor(),
                    database
            );
            liquibase.update(new Contexts(), new LabelExpression());
        } catch (SQLException | LiquibaseException e) {
            throw new IllegalStateException("Failed to apply Liquibase changelog", e);
        }
    }
}
