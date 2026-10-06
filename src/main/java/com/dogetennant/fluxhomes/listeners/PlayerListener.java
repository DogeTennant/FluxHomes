package com.dogetennant.fluxhomes.listeners;

import com.dogetennant.fluxhomes.FluxHomes;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class PlayerListener implements Listener {

    private final FluxHomes plugin;

    public PlayerListener(FluxHomes plugin) {
        this.plugin = plugin;
    }

    /** Loads the homes before the player is in the game (this event runs off the main thread). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        plugin.getHomeManager().loadBlocking(event.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.getHomeManager().unload(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (!plugin.getConfigUtil().isRespawnAtHomeEnabled()) return;

        // the respawn location is needed right now: only the homes loaded at login are used
        plugin.getHomeManager().cachedHome(event.getPlayer().getUniqueId(), "home").ifPresent(home -> {
            var loc = home.toLocation();
            if (loc.getWorld() != null) {
                event.setRespawnLocation(loc);
            }
        });
    }
}
