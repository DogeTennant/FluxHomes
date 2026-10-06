package com.dogetennant.fluxhomes.database;

import com.dogetennant.fluxhomes.TestPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The contract against the MySQL dialect, run on H2 in MySQL mode. That checks the SQL the
 * plugin sends ({@code ON DUPLICATE KEY UPDATE}, column types), not MySQL itself.
 */
class MySQLManagerTest extends DatabaseManagerContractTest {

    @TempDir
    Path dataFolder;

    @Override
    protected DatabaseManager open() throws Exception {
        return TestPlugin.h2MySql(TestPlugin.mockPlugin(dataFolder, dataFolder));
    }

    /** Documents current behaviour: see docs/problems/fluxhomes-long-home-names-lost-on-mysql.md. */
    @Test
    void homeNamesLongerThan32CharactersAreNotSaved() {
        db.saveHome(home(ALEX, "a".repeat(32), "world", 1));
        db.saveHome(home(ALEX, "a".repeat(33), "world", 1));

        assertThat(db.getHome(ALEX, "a".repeat(32))).isNotNull();
        assertThat(db.getHome(ALEX, "a".repeat(33))).isNull();
    }
}
