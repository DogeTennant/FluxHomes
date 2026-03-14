package com.dogetennant.fluxhomes.listeners;

import com.dogetennant.fluxhomes.FluxHomes;
import com.dogetennant.fluxhomes.models.Home;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

public class PlayerListener implements Listener {

    private final FluxHomes plugin;

    public PlayerListener(FluxHomes plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (!plugin.getConfigUtil().isRespawnAtHomeEnabled()) return;

        Home home = plugin.getHomeManager().getHome(event.getPlayer().getUniqueId(), "home");
        if (home == null) return;

        var loc = home.toLocation();
        if (loc.getWorld() == null) return;

        event.setRespawnLocation(loc);
    }
}