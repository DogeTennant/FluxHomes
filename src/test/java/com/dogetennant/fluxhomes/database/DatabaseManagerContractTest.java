package com.dogetennant.fluxhomes.database;

import com.dogetennant.fluxhomes.models.Home;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What both storage backends must do the same way. Runs once per dialect
 * ({@link SQLiteManagerTest}, {@link MySQLManagerTest}).
 */
abstract class DatabaseManagerContractTest {

    protected static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000a1e7");
    protected static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-0000000057e7");

    protected DatabaseManager db;

    protected abstract DatabaseManager open() throws Exception;

    @BeforeEach
    void openDatabase() throws Exception {
        db = open();
    }

    @AfterEach
    void closeDatabase() {
        db.shutdown();
    }

    protected static Home home(UUID owner, String name, String world, double x) {
        return new Home(owner, name, world, x, 64.5, -12.25, 90.5f, -15.25f);
    }

    /** Homes have no equals(); compare field by field. */
    protected static void assertSameHome(Home actual, Home expected) {
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void aSavedHomeIsReadBackExactly() {
        Home base = new Home(ALEX, "base", "world", 123.456789, -59.5, 1_000_000.125, 179.9f, -89.5f);

        db.saveHome(base);

        assertSameHome(db.getHome(ALEX, "base"), base);
    }

    @Test
    void savingTheSameNameAgainMovesTheHome() {
        db.saveHome(home(ALEX, "base", "world", 1));

        db.saveHome(home(ALEX, "base", "world_nether", 2));

        assertSameHome(db.getHome(ALEX, "base"), home(ALEX, "base", "world_nether", 2));
        assertThat(db.getHomeCount(ALEX)).isEqualTo(1);
    }

    @Test
    void namesBelongToTheirOwner() {
        db.saveHome(home(ALEX, "base", "world", 1));
        db.saveHome(home(STEVE, "base", "world", 2));

        assertThat(db.getHome(ALEX, "base").getX()).isEqualTo(1);
        assertThat(db.getHome(STEVE, "base").getX()).isEqualTo(2);
    }

    @Test
    void homesAndCountsArePerOwner() {
        db.saveHome(home(ALEX, "base", "world", 1));
        db.saveHome(home(ALEX, "farm", "world", 2));
        db.saveHome(home(STEVE, "base", "world", 3));

        assertThat(db.getHomes(ALEX)).extracting(Home::getName).containsExactlyInAnyOrder("base", "farm");
        assertThat(db.getHomeCount(ALEX)).isEqualTo(2);
        assertThat(db.getHomeCount(STEVE)).isEqualTo(1);
    }

    @Test
    void aPlayerWithoutHomesHasNone() {
        assertThat(db.getHome(ALEX, "base")).isNull();
        assertThat(db.getHomes(ALEX)).isEmpty();
        assertThat(db.getHomeCount(ALEX)).isZero();
    }

    @Test
    void deleteHomeRemovesOnlyThatHome() {
        db.saveHome(home(ALEX, "base", "world", 1));
        db.saveHome(home(ALEX, "farm", "world", 2));
        db.saveHome(home(STEVE, "base", "world", 3));

        db.deleteHome(ALEX, "base");

        assertThat(db.getHome(ALEX, "base")).isNull();
        assertThat(db.getHome(ALEX, "farm")).isNotNull();
        assertThat(db.getHome(STEVE, "base")).isNotNull();
    }

    @Test
    void deleteAllHomesRemovesOnlyThatOwnersHomes() {
        db.saveHome(home(ALEX, "base", "world", 1));
        db.saveHome(home(ALEX, "farm", "world", 2));
        db.saveHome(home(STEVE, "base", "world", 3));

        db.deleteAllHomes(ALEX);

        assertThat(db.getHomes(ALEX)).isEmpty();
        assertThat(db.getHomeCount(STEVE)).isEqualTo(1);
    }

    @Test
    void deletingSomethingThatDoesNotExistIsHarmless() {
        db.deleteHome(ALEX, "nothing");
        db.deleteAllHomes(ALEX);

        assertThat(db.getHomeCount(ALEX)).isZero();
    }
}
