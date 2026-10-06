package com.dogetennant.fluxhomes.database;

import com.dogetennant.fluxhomes.TestPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** The contract against the real SQLite file {@code data.db}, plus what only SQLite does. */
class SQLiteManagerTest extends DatabaseManagerContractTest {

    @TempDir
    Path dataFolder;

    @Override
    protected DatabaseManager open() {
        SQLiteManager manager = new SQLiteManager(TestPlugin.mockPlugin(dataFolder, dataFolder));
        manager.initialize();
        return manager;
    }

    @Test
    void homesSurviveARestart() {
        db.saveHome(home(ALEX, "base", "world", 7));
        db.shutdown();

        DatabaseManager reopened = open();
        try {
            assertSameHome(reopened.getHome(ALEX, "base"), home(ALEX, "base", "world", 7));
        } finally {
            reopened.shutdown();
        }
    }

    @Test
    void theDatabaseIsDataDbInThePluginFolder() {
        assertThat(dataFolder.resolve("data.db")).isRegularFile();
    }

    @Test
    void longHomeNamesAreKept() {
        String name = "a".repeat(40);

        db.saveHome(home(ALEX, name, "world", 1));

        assertThat(db.getHome(ALEX, name)).isNotNull();
    }
}
