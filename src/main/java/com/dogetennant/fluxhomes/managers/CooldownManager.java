package com.dogetennant.fluxhomes.managers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CooldownManager {

    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public boolean isOnCooldown(UUID playerUUID) {
        if (!cooldowns.containsKey(playerUUID)) return false;
        if (cooldowns.get(playerUUID) <= System.currentTimeMillis()) {
            cooldowns.remove(playerUUID);
            return false;
        }
        return true;
    }

    public int getRemainingSeconds(UUID playerUUID) {
        if (!cooldowns.containsKey(playerUUID)) return 0;
        long remaining = (cooldowns.get(playerUUID) - System.currentTimeMillis()) / 1000;
        return (int) Math.max(0, remaining);
    }

    public void setCooldown(UUID playerUUID, int seconds) {
        cooldowns.put(playerUUID, System.currentTimeMillis() + (seconds * 1000L));
    }

    public void clearCooldown(UUID playerUUID) {
        cooldowns.remove(playerUUID);
    }

    public void cleanup() {
        cooldowns.entrySet().removeIf(entry -> entry.getValue() <= System.currentTimeMillis());
    }
}