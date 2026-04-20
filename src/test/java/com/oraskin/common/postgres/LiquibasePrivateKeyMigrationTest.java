package com.oraskin.common.postgres;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiquibasePrivateKeyMigrationTest {

    @Test
    void privateKeyTableCreationMigrationIsIdempotent() throws IOException {
        String changelog = Files.readString(Path.of("src/main/resources/db/changelog/ddl/0004_ddl_private_chat_support.xml"));

        assertAll(
                () -> assertTrue(changelog.contains("<validCheckSum>ANY</validCheckSum>")),
                () -> assertTrue(changelog.contains("CREATE TABLE IF NOT EXISTS private_chat_client_keys")),
                () -> assertTrue(changelog.contains("<sql splitStatements=\"false\" endDelimiter=\"$$;\">")),
                () -> assertTrue(changelog.contains("NOT EXISTS (\n                       SELECT 1\n                       FROM pg_constraint")),
                () -> assertTrue(changelog.contains("CREATE INDEX IF NOT EXISTS idx_private_chat_client_keys_user_status_updated"))
        );
    }

    @Test
    void privateKeyTableRenameMigrationIsIdempotent() throws IOException {
        String changelog = Files.readString(Path.of("src/main/resources/db/changelog/ddl/0005_ddl_rename_private_chat_client_keys_to_public_keys.xml"));

        assertAll(
                () -> assertTrue(changelog.contains("<validCheckSum>ANY</validCheckSum>")),
                () -> assertTrue(changelog.contains("<sql splitStatements=\"false\" endDelimiter=\"$$;\">")),
                () -> assertTrue(changelog.contains("to_regclass('public.private_chat_client_keys') IS NOT NULL")),
                () -> assertTrue(changelog.contains("to_regclass('public.public_keys') IS NULL")),
                () -> assertTrue(changelog.contains("to_regclass('public.idx_private_chat_client_keys_user_status_updated') IS NOT NULL")),
                () -> assertTrue(changelog.contains("to_regclass('public.idx_public_keys_user_status_updated') IS NULL"))
        );
    }
}
