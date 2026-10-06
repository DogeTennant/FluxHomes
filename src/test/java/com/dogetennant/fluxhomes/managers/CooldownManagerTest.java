package com.dogetennant.fluxhomes.managers;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Teleport cooldowns. Uses whole minutes or zero, so the real clock cannot make it flaky. */
class CooldownManagerTest {

    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000a1e7");

    private final CooldownManager cooldowns = new CooldownManager();

    @Test
    void nobodyIsOnCooldownAtFirst() {
        assertThat(cooldowns.isOnCooldown(ALEX)).isFalse();
        assertThat(cooldowns.getRemainingSeconds(ALEX)).isZero();
    }

    @Test
    void aCooldownLastsItsSeconds() {
        cooldowns.setCooldown(ALEX, 60);

        assertThat(cooldowns.isOnCooldown(ALEX)).isTrue();
        assertThat(cooldowns.getRemainingSeconds(ALEX)).isBetween(58, 60);
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
        cooldowns.setCooldown(ALEX, 0);
        cooldowns.setCooldown(steve, 60);

        cooldowns.cleanup();

        assertThat(cooldowns.getRemainingSeconds(ALEX)).isZero();
        assertThat(cooldowns.isOnCooldown(steve)).isTrue();
    }
}
