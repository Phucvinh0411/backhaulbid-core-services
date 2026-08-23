package iuh.fit.se.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdentityMigrationLayoutTest {

    @Test
    void addressBookMigrationUsesAUniqueVersionAfterDevSeedMigrations() throws IOException {
        Path migrationRoot = Path.of("src/main/resources/db");

        try (Stream<Path> migrations = Files.walk(migrationRoot)) {
            assertTrue(migrations.anyMatch(path -> path.getFileName().toString().equals("V13__add_address_book.sql")));
        }

        try (Stream<Path> migrations = Files.walk(migrationRoot)) {
            assertFalse(migrations.anyMatch(path -> path.getFileName().toString().equals("V12__add_address_book.sql")));
        }

        assertTrue(Files.exists(migrationRoot.resolve("dev-migration/V12__seed_requested_user_accounts.sql")));
    }
}
