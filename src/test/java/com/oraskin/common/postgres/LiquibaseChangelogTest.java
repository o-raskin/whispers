package com.oraskin.common.postgres;

import liquibase.changelog.ChangeLogParameters;
import liquibase.exception.ChangeLogParseException;
import liquibase.parser.ChangeLogParserFactory;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class LiquibaseChangelogTest {

    @Test
    void masterChangelogParsesSuccessfully() {
        assertDoesNotThrow(() -> ChangeLogParserFactory.getInstance()
                .getParser("db/changelog/db.changelog-master.xml", new ClassLoaderResourceAccessor())
                .parse(
                        "db/changelog/db.changelog-master.xml",
                        new ChangeLogParameters(),
                        new ClassLoaderResourceAccessor()
                ));
    }
}
