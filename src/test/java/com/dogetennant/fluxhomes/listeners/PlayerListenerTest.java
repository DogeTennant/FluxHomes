package com.dogetennant.fluxhomes.listeners;

import com.dogetennant.fluxhomes.FluxHomes;
import com.dogetennant.fluxhomes.TestPlugin;
import com.dogetennant.fluxhomes.managers.HomeManager;
import com.dogetennant.fluxhomes.models.Home;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@code respawn-at-home}: dying sends the player to their home named "home". */
class PlayerListenerTest {

    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000a1e7");

    @TempDir
    Path dataFolder;
    @TempDir
    Path pluginsFolder;

    private final Server server = TestPlugin.bukkitServer();
    private final HomeManager homes = mock(HomeManager.class);
    private final PlayerRespawnEvent respawn = mock(PlayerRespawnEvent.class);
    private final World world = mock(World.class);
    private FluxHomes plugin;
    private PlayerListener listener;

    @BeforeEach
    void setUp() {
        plugin = TestPlugin.mockPlugin(dataFolder, pluginsFolder);
        when(plugin.getHomeManager()).thenReturn(homes);
        when(plugin.getConfigUtil().isRespawnAtHomeEnabled()).thenReturn(true);
        when(server.getWorld("world")).thenReturn(world);
        when(server.getWorld("gone")).thenReturn(null);
        Player alex = mock(Player.class);
        when(alex.getUniqueId()).thenReturn(ALEX);
        when(respawn.getPlayer()).thenReturn(alex);
        when(homes.cachedHome(any(), any())).thenReturn(Optional.empty());
        listener = new PlayerListener(plugin);
    }

    private void hasHome(String name, String world) {
        when(homes.cachedHome(ALEX, name)).thenReturn(Optional.of(new Home(ALEX, name, world, 10, 70, -4, 0f, 0f)));
    }

    @Test
    void thePlayerRespawnsAtTheHomeNamedHome() {
        hasHome("home", "world");

        listener.onPlayerRespawn(respawn);

        verify(respawn).setRespawnLocation(new Location(world, 10, 70, -4, 0f, 0f));
    }

    @Test
    void otherHomesAreNotUsed() {
        hasHome("base", "world");

        listener.onPlayerRespawn(respawn);

        verify(respawn, never()).setRespawnLocation(any());
    }

    @Test
    void aHomeInAMissingWorldLeavesTheNormalRespawn() {
        hasHome("home", "gone");

        listener.onPlayerRespawn(respawn);

        verify(respawn, never()).setRespawnLocation(any());
    }

    @Test
    void nothingChangesWhenTheOptionIsOff() {
        when(plugin.getConfigUtil().isRespawnAtHomeEnabled()).thenReturn(false);
        hasHome("home", "world");

        listener.onPlayerRespawn(respawn);

        verify(respawn, never()).setRespawnLocation(any());
    }
}
