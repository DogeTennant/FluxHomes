package com.dogetennant.fluxhomes.database;

import com.dogetennant.fluxhomes.TestPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The contract against the real SQLite file {@code data.db}, plus what only SQLite does. */
class SQLiteManagerTest extends DatabaseManagerContractTest {

    @TempDir
    Path dataFolder;

    @Override
    protected DatabaseManager open(String prefix) throws Exception {
        SQLiteManager manager = new SQLiteManager(TestPlugin.mockPlugin(dataFolder, dataFolder, prefix));
        manager.initialize();
        return manager;
    }

    @Override
    protected List<String> tables(DatabaseManager manager) throws Exception {
        List<String> names = new ArrayList<>();
        try (Connection con = manager.dataSource.getConnection();
             ResultSet rs = con.getMetaData().getTables(null, null, "%", new String[] {"TABLE"})) {
            while (rs.next()) names.add(rs.getString("TABLE_NAME"));
        }
        return names;
    }

    @Test
    void homesSurviveARestart() throws Exception {
        db.saveHome(home(ALEX, "base", "world", 7));
        db.shutdown();

        DatabaseManager reopened = open("");
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
    void theJournalIsInWalMode() throws Exception {
        try (Connection con = db.dataSource.getConnection();
             Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA journal_mode;")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).isEqualToIgnoringCase("wal");
        }
    }

    @Test
    void anExistingTableFromVersion1IsUsedAsItIs() throws Exception {
        // 1.0.x created exactly this table; 1.1 with the default prefix must keep using it
        db.saveHome(home(ALEX, "base", "world", 3));
        db.shutdown();

        DatabaseManager upgraded = open("");
        try {
            assertThat(tables(upgraded)).containsOnlyOnce("homes");
            assertThat(upgraded.getHome(ALEX, "base")).isNotNull();
        } finally {
            upgraded.shutdown();
        }
    }
}
