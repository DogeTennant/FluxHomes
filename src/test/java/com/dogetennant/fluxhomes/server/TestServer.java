package com.dogetennant.fluxhomes.server;

import com.dogetennant.fluxhomes.FluxHomes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.inventory.PlayerInventoryViewMock;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * The whole plugin on a MockBukkit server: its own config, translations and SQLite database in a
 * temporary folder, every listener and command registered. Stop it with {@code MockBukkit.unmock()}.
 */
final class TestServer {

    final ServerMock server;
    final FluxHomes plugin;

    private TestServer(ServerMock server, FluxHomes plugin) {
        this.server = server;
        this.plugin = plugin;
    }

    static TestServer start() {
        removeMockitoServer();
        ServerMock server = MockBukkit.mock();
        server.addSimpleWorld("world");
        return new TestServer(server, MockBukkit.load(FluxHomes.class));
    }

    /** The logic tests leave a Mockito server in {@code Bukkit}; MockBukkit refuses to start next to it. */
    private static void removeMockitoServer() {
        if (Bukkit.getServer() == null || Bukkit.getServer() instanceof ServerMock) return;
        try {
            Field field = Bukkit.class.getDeclaredField("server");
            field.setAccessible(true);
            field.set(null, null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot remove the other tests' server", e);
        }
    }

    /**
     * What a server start does with the plugin: stop it, then start it again. A real start reads
     * config.yml from disk, so the copy Bukkit kept from the run before is thrown away first.
     */
    void restart() {
        server.getPluginManager().disablePlugin(plugin);
        plugin.reloadConfig();
        server.getPluginManager().enablePlugin(plugin);
    }

    PlayerMock admin() {
        PlayerMock admin = server.addPlayer("Admin");
        admin.setOp(true);
        return admin;
    }

    /** A player without op who joins now (their homes are loaded while they log in). */
    PlayerMock player(String name) {
        return server.addPlayer(name);
    }

    /**
     * Lets the database thread finish what is queued so far, then hands its answers to the main
     * thread - a few rounds, for work handed back and forth (a marker queued behind it; no sleeps).
     */
    void settle() {
        for (int round = 0; round < 3; round++) {
            CountDownLatch marker = new CountDownLatch(1);
            plugin.getHomeManager().inBackground(() -> {
                marker.countDown();
                return null;
            }, done -> { });
            try {
                if (!marker.await(30, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("the database thread is stuck");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            server.getScheduler().performOneTick();
        }
    }

    /** The default warmup (3 seconds) and one tick more. */
    void waitOutTheWarmup() {
        server.getScheduler().performTicks(3 * 20 + 1);
    }

    Path file(String name) {
        return plugin.getDataFolder().toPath().resolve(name);
    }

    /** Adds {@code text} at the end of a file in the plugin folder. */
    void append(String name, String text) throws IOException {
        Files.writeString(file(name), Files.readString(file(name)) + text);
    }

    /** Replaces {@code from} with {@code to} in a file in the plugin folder (it must be there). */
    void replace(String name, String from, String to) throws IOException {
        String text = Files.readString(file(name));
        if (!text.contains(from)) throw new IllegalArgumentException(name + " has no '" + from + "'");
        Files.writeString(file(name), text.replace(from, to));
    }

    /**
     * A left click on raw slot {@code slot} of the screen the player has open, as the server reports
     * it: the screen's slots first, then the player's 27 storage slots, then the hotbar.
     */
    InventoryClickEvent click(PlayerMock player, int slot) {
        InventoryClickEvent click = new InventoryClickEvent(
                new ClickableView(player, player.getOpenInventory().getTopInventory()),
                InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(click);
        return click;
    }

    /**
     * The player's view of a screen. MockBukkit 4.116 reads the item of a slot below the screen
     * from the wrong inventory (a click there sees an empty slot); the server reads the player's.
     */
    private static final class ClickableView extends PlayerInventoryViewMock {

        ClickableView(HumanEntity player, Inventory top) {
            super(player, top);
        }

        @Override
        public ItemStack getItem(int rawSlot) {
            Inventory inventory = getInventory(rawSlot);
            return inventory == null ? null : inventory.getItem(convertSlot(rawSlot));
        }
    }

    /** What the player was told since the last call, as plain text. */
    static List<String> messages(PlayerMock player) {
        List<String> messages = new ArrayList<>();
        Component message;
        while ((message = player.nextComponentMessage()) != null) {
            messages.add(PlainTextComponentSerializer.plainText().serialize(message));
        }
        return messages;
    }
}
