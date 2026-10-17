package com.dogetennant.fluxhomes.server;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.dogetennant.fluxhomes.server.TestServer.messages;
import static org.assertj.core.api.Assertions.assertThat;

/** /homesadmin settings: the menu, its clicks and the blocked-worlds chat prompt. */
class SettingsMenuServerTest {

    private static final int MAX_HOMES = 40;
    private static final int COOLDOWN = 11;
    private static final int EDIT_BLOCKED_WORLDS = 30;

    private TestServer mc;
    /** Chat messages that went out to the other players. */
    private final List<String> chatShown = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        mc = TestServer.start();
        mc.server.getPluginManager().registerEvents(new Listener() {
            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            public void onChat(AsyncChatEvent event) {
                chatShown.add(PlainTextComponentSerializer.plainText().serialize(event.message()));
            }
        }, MockBukkit.createMockPlugin("Chat"));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private YamlConfiguration configOnDisk() {
        return YamlConfiguration.loadConfiguration(mc.file("config.yml").toFile());
    }

    private static List<String> lore(ItemStack item) {
        return item.getItemMeta().lore().stream()
                .map(line -> PlainTextComponentSerializer.plainText().serialize(line))
                .toList();
    }

    @Test
    void theMenuShowsTheSettingsAndAClickChangesThem() {
        PlayerMock admin = mc.admin();
        admin.performCommand("ha settings");
        assertThat(lore(admin.getOpenInventory().getTopInventory().getItem(MAX_HOMES))).contains("Current: 3");

        mc.click(admin, MAX_HOMES);
        mc.click(admin, COOLDOWN);

        assertThat(configOnDisk().getInt("max-homes.default")).isEqualTo(4);
        assertThat(configOnDisk().getBoolean("cooldown.enabled")).isFalse();
        assertThat(lore(admin.getOpenInventory().getTopInventory().getItem(MAX_HOMES))).contains("Current: 4");
        assertThat(mc.plugin.getConfigUtil().isCooldownEnabled()).isFalse();
    }

    @Test
    void aBlockedWorldIsAddedThroughTheChatPrompt() {
        PlayerMock admin = mc.admin();
        admin.performCommand("ha settings");
        mc.click(admin, EDIT_BLOCKED_WORLDS);
        messages(admin);

        chat(admin, "world_nether");

        assertThat(configOnDisk().getStringList("blocked-worlds.worlds"))
                .containsExactly("world_example", "resource_world", "world_nether");
        assertThat(messages(admin)).containsExactly("[FluxHomes] Added world_nether to the blocked worlds list.");
    }

    /** docs/problems/fluxhomes-settings-changed-from-any-chest.md */
    @Test
    void aChestNamedLikeTheMenuIsJustAChest() {
        PlayerMock alex = mc.player("Alex");
        // an anvil-renamed chest: on Paper its name is the title of the screen it opens
        Inventory chest = mc.server.createInventory(null, 54, Component.text("FluxHomes Settings"));
        chest.setItem(MAX_HOMES, new ItemStack(Material.DIRT));
        chest.setItem(COOLDOWN, new ItemStack(Material.DIRT));
        alex.openInventory(chest);

        boolean cancelled = mc.click(alex, MAX_HOMES).isCancelled();
        mc.click(alex, COOLDOWN);

        assertThat(cancelled).isFalse(); // the player takes the dirt out
        assertThat(configOnDisk().getInt("max-homes.default")).isEqualTo(3);
        assertThat(configOnDisk().getBoolean("cooldown.enabled")).isTrue();
    }

    /** docs/problems/fluxhomes-settings-changed-from-any-chest.md */
    @Test
    void aClickInTheAdminsOwnInventoryBelowTheMenuChangesNothing() {
        PlayerMock admin = mc.admin();
        admin.getInventory().setItem(COOLDOWN, new ItemStack(Material.COBBLESTONE)); // second row of the backpack
        admin.performCommand("ha settings");

        // the menu has 54 slots; the backpack's slot 11 is raw slot 54 + 2 below it
        boolean cancelled = mc.click(admin, 54 + (COOLDOWN - 9)).isCancelled();

        assertThat(cancelled).isTrue(); // nothing is moved into the menu either
        assertThat(configOnDisk().getBoolean("cooldown.enabled")).isTrue();
    }

    /** docs/problems/fluxhomes-settings-changed-from-any-chest.md */
    @Test
    void anAdminWhoLostThePermissionWithTheMenuOpenChangesNothing() {
        PlayerMock admin = mc.admin();
        admin.performCommand("ha settings");
        admin.setOp(false);

        mc.click(admin, MAX_HOMES);

        assertThat(configOnDisk().getInt("max-homes.default")).isEqualTo(3);
    }

    /** docs/problems/fluxhomes-blocked-worlds-prompt-never-ends.md */
    @Test
    void aPromptLeftOpenEndsWhenTheAdminLeaves() {
        PlayerMock admin = mc.admin();
        admin.performCommand("ha settings");
        mc.click(admin, EDIT_BLOCKED_WORLDS);
        admin.disconnect();
        admin.reconnect();
        messages(admin);

        boolean shown = chat(admin, "hi everyone");

        assertThat(shown).isTrue();
        assertThat(configOnDisk().getStringList("blocked-worlds.worlds"))
                .containsExactly("world_example", "resource_world");
    }

    /** The player says {@code text}; returns whether the message went out to the chat. */
    private boolean chat(PlayerMock player, String text) {
        player.chat(text);
        mc.server.getScheduler().waitAsyncEventsFinished();
        mc.server.getScheduler().performOneTick(); // the answer is handled on the main thread
        return chatShown.contains(text);
    }
}
