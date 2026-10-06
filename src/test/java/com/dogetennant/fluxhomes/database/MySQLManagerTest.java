package com.dogetennant.fluxhomes.database;

import com.dogetennant.fluxhomes.TestPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The contract against the MySQL dialect, run on H2 in MySQL mode. That checks the SQL the
 * plugin sends ({@code ON DUPLICATE KEY UPDATE}, column types), not MySQL itself.
 */
class MySQLManagerTest extends DatabaseManagerContractTest {

    @TempDir
    Path dataFolder;

    @Override
    protected DatabaseManager open(String prefix) throws Exception {
        return TestPlugin.h2MySql(TestPlugin.mockPlugin(dataFolder, dataFolder, prefix));
    }

    @Override
    protected List<String> tables(DatabaseManager manager) throws Exception {
        List<String> names = new ArrayList<>();
        try (Connection con = manager.dataSource.getConnection();
             ResultSet rs = con.getMetaData().getTables(null, null, "%", new String[] {"TABLE", "BASE TABLE"})) {
            while (rs.next()) names.add(rs.getString("TABLE_NAME").toLowerCase(Locale.ROOT));
        }
        return names;
    }

    /**
     * The column holds 32 characters, which is why {@code /sethome} refuses longer names
     * ({@code HomeManager#MAX_NAME_LENGTH}).
     */
    @Test
    void theNameColumnHolds32Characters() {
        db.saveHome(home(ALEX, "a".repeat(32), "world", 1));
        db.saveHome(home(ALEX, "a".repeat(33), "world", 1));

        assertThat(db.getHome(ALEX, "a".repeat(32))).isNotNull();
        assertThat(db.getHome(ALEX, "a".repeat(33))).isNull();
    }
}
