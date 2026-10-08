package com.dogetennant.fluxhomes.managers;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Teleport cooldowns, on a clock the test moves. */
class CooldownManagerTest {

    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000a1e7");

    private long now = 1_000_000;
    private final CooldownManager cooldowns = new CooldownManager(() -> now);

    @Test
    void nobodyIsOnCooldownAtFirst() {
        assertThat(cooldowns.isOnCooldown(ALEX)).isFalse();
        assertThat(cooldowns.getRemainingSeconds(ALEX)).isZero();
    }

    @Test
    void aCooldownLastsItsSeconds() {
        cooldowns.setCooldown(ALEX, 5);

        now += 4_999;
        assertThat(cooldowns.isOnCooldown(ALEX)).isTrue();
        now += 1;
        assertThat(cooldowns.isOnCooldown(ALEX)).isFalse();
    }

    @Test
    void theWaitIsShownInWholeSecondsRoundedUp() {
        cooldowns.setCooldown(ALEX, 5);
        assertThat(cooldowns.getRemainingSeconds(ALEX)).isEqualTo(5);

        now += 10;                                       // 4.99 s left
        assertThat(cooldowns.getRemainingSeconds(ALEX)).isEqualTo(5);

        now += 4_000;                                    // 0.99 s left: still "wait 1 second", never 0
        assertThat(cooldowns.isOnCooldown(ALEX)).isTrue();
        assertThat(cooldowns.getRemainingSeconds(ALEX)).isEqualTo(1);
    }

    @Test
    void aZeroSecondCooldownIsOverAtOnce() {
        cooldowns.setCooldown(ALEX, 0);

        assertThat(cooldowns.isOnCooldown(ALEX)).isFalse();
    }

    @Test
    void clearingEndsTheCooldown() {
        cooldowns.setCooldown(ALEX, 60);

        cooldowns.clearCooldown(ALEX);

        assertThat(cooldowns.isOnCooldown(ALEX)).isFalse();
    }

    @Test
    void cleanupDropsOnlyFinishedCooldowns() {
        UUID steve = UUID.randomUUID();
        cooldowns.setCooldown(ALEX, 1);
        cooldowns.setCooldown(steve, 60);
        now += 1_000;

        cooldowns.cleanup();

        assertThat(cooldowns.getRemainingSeconds(ALEX)).isZero();
        assertThat(cooldowns.isOnCooldown(steve)).isTrue();
    }
}
