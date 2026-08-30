package org.jivesoftware.openfire.fast;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class FastDatabaseSchemaTest {

    private static final List<String> DATABASES = List.of("cockroachdb", "db2", "firebird", "hsqldb",
        "mariadb", "mysql", "oracle", "postgresql", "sqlserver", "sybase");
    private static final Pattern USER_SCRAM_TABLE = Pattern.compile(
        "(?is)CREATE\\s+TABLE\\s+ofUserScram\\s*\\((.*?)\\R\\)\\s*;?");

    @Test
    void baseAndUpgradeSchemasDefineTheSameScramColumnsAndKey() throws Exception {
        for (final String database : DATABASES) {
            final String base = userScramDefinition(databasePath("openfire_" + database + ".sql"));
            final String upgrade = userScramDefinition(databasePath("upgrade/39/openfire_" + database + ".sql"));

            assertEquals(normalize(upgrade), normalize(base), database);
            assertFalse(base.matches("(?is).*\\b(clientID|tokenSlot|replayCounter)\\b.*"), database);
        }
    }

    @Test
    void freshHsqldbSchemaAcceptsTheScramCredentialInsertUsedByAuthProvider() throws Exception {
        final String definition = userScramDefinition(databasePath("openfire_hsqldb.sql"));
        try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:fast-schema-test", "sa", "");
             var create = connection.createStatement()) {
            create.execute("CREATE TABLE ofUserScram (" + definition + ")");
            try (var insert = connection.prepareStatement("INSERT INTO ofUserScram "
                + "(username, mechanism, iterations, salt, storedKey, serverKey) VALUES (?, ?, ?, ?, ?, ?)")) {
                insert.setString(1, "alice");
                insert.setString(2, "SCRAM-SHA-256");
                insert.setInt(3, 4096);
                insert.setString(4, "salt");
                insert.setString(5, "stored-key");
                insert.setString(6, "server-key");
                assertEquals(1, insert.executeUpdate());
            }
        }
    }

    private static Path databasePath(final String relative) {
        return Path.of("..", "distribution", "src", "database").resolve(relative);
    }

    private static String userScramDefinition(final Path script) throws Exception {
        final Matcher matcher = USER_SCRAM_TABLE.matcher(Files.readString(script));
        if (!matcher.find()) {
            throw new AssertionError("No ofUserScram definition in " + script);
        }
        return matcher.group(1);
    }

    private static String normalize(final String definition) {
        return definition.toLowerCase(Locale.ROOT)
            .replace("nvarchar", "varchar")
            .replace("varchar2", "varchar")
            .replaceAll("(?i)constraint\\s+ofuserscram_pk\\s+", "")
            .replaceAll("\\s+", "")
            .replace(";", "");
    }
}
