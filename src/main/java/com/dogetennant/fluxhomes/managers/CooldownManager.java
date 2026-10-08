package com.dogetennant.fluxhomes.managers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

public class CooldownManager {

    private final Map<UUID, Long> cooldowns = new HashMap<>();
    /** The current time in milliseconds. */
    private final LongSupplier clock;

    public CooldownManager() {
        this(System::currentTimeMillis);
    }

    /** With {@code clock} as the current time in milliseconds (tests). */
    CooldownManager(LongSupplier clock) {
        this.clock = clock;
    }

    public boolean isOnCooldown(UUID playerUUID) {
        if (!cooldowns.containsKey(playerUUID)) return false;
        if (cooldowns.get(playerUUID) <= clock.getAsLong()) {
            cooldowns.remove(playerUUID);
            return false;
        }
        return true;
    }

    /** Whole seconds left, rounded up: while on cooldown it is at least 1. */
    public int getRemainingSeconds(UUID playerUUID) {
        if (!cooldowns.containsKey(playerUUID)) return 0;
        long remainingMs = cooldowns.get(playerUUID) - clock.getAsLong();
        return (int) Math.max(0, (remainingMs + 999) / 1000);
    }

    public void setCooldown(UUID playerUUID, int seconds) {
        cooldowns.put(playerUUID, clock.getAsLong() + (seconds * 1000L));
    }

    public void clearCooldown(UUID playerUUID) {
        cooldowns.remove(playerUUID);
    }

    public void cleanup() {
        long now = clock.getAsLong();
        cooldowns.entrySet().removeIf(entry -> entry.getValue() <= now);
    }
}
