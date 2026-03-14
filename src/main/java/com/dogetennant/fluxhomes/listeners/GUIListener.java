package com.dogetennant.fluxhomes.listeners;

import com.dogetennant.fluxhomes.FluxHomes;
import com.dogetennant.fluxhomes.gui.SettingsGUI;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GUIListener implements Listener {

    private final FluxHomes plugin;
    private final Map<UUID, String> pendingChatInput = new HashMap<>(); // "add" or "remove"

    public GUIListener(FluxHomes plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = PlainTextComponentSerializer.plainText()
                .serialize(event.getView().title());
        if (!title.equals(SettingsGUI.TITLE)) return;

        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;

        boolean left = event.isLeftClick();
        int slot = event.getSlot();

        switch (slot) {
            case 40 -> handleMaxHomes(left);
            case 11 -> handleToggle("cooldown.enabled");
            case 12 -> handleInt("cooldown.seconds", left, 1, 1, 3600);
            case 15 -> handleToggle("warmup.enabled");
            case 16 -> handleInt("warmup.seconds", left, 1, 1, 300);
            case 20 -> handleToggle("teleport-sound.enabled");
            case 22 -> handleToggle("respawn-at-home.enabled");
            case 24 -> handleToggle("confirm-deletion.enabled");
            case 25 -> handleInt("confirm-deletion.timeout", left, 5, 5, 300);
            case 29 -> handleToggle("blocked-worlds.enabled");
            case 30 -> {
                player.closeInventory();
                String mode = left ? "add" : "remove";
                pendingChatInput.put(player.getUniqueId(), mode);
                if (left) {
                    plugin.getMessageUtil().send(player, "gui-blocked-worlds-add-prompt");
                } else {
                    plugin.getMessageUtil().send(player, "gui-blocked-worlds-remove-prompt");
                }
                plugin.getMessageUtil().send(player, "gui-blocked-worlds-cancel-hint");
                return; // Don't play sound or reopen GUI yet
            }
            case 33 -> handleLanguage(player, left);
            default -> { return; }
        }

        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
        SettingsGUI.open(player, plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!pendingChatInput.containsKey(uuid)) return;

        event.setCancelled(true); // Don't broadcast the message to other players
        String mode = pendingChatInput.remove(uuid);
        String input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();

        // Handle cancellation
        if (input.equalsIgnoreCase("cancel")) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                plugin.getMessageUtil().send(player, "gui-blocked-worlds-cancelled");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                SettingsGUI.open(player, plugin);
            });
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            List<String> worlds = new ArrayList<>(plugin.getConfigUtil().getBlockedWorlds());

            if (mode.equals("add")) {
                if (worlds.contains(input)) {
                    plugin.getMessageUtil().send(player, "gui-blocked-worlds-already-added", "{world}", input);
                } else {
                    worlds.add(input);
                    plugin.getConfig().set("blocked-worlds.worlds", worlds);
                    plugin.saveConfig();
                    plugin.getMessageUtil().send(player, "gui-blocked-worlds-added", "{world}", input);
                }
            } else {
                if (!worlds.contains(input)) {
                    plugin.getMessageUtil().send(player, "gui-blocked-worlds-not-found", "{world}", input);
                } else {
                    worlds.remove(input);
                    plugin.getConfig().set("blocked-worlds.worlds", worlds);
                    plugin.saveConfig();
                    plugin.getMessageUtil().send(player, "gui-blocked-worlds-removed", "{world}", input);
                }
            }

            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
            SettingsGUI.open(player, plugin);
        });
    }

    private void handleMaxHomes(boolean increase) {
        int current = plugin.getConfigUtil().getMaxHomes("default");
        int newValue = increase ? current + 1 : Math.max(1, current - 1);
        plugin.getConfig().set("max-homes.default", newValue);
        plugin.saveConfig();
    }

    private void handleToggle(String configKey) {
        boolean current = plugin.getConfig().getBoolean(configKey);
        plugin.getConfig().set(configKey, !current);
        plugin.saveConfig();
    }

    private void handleInt(String configKey, boolean increase, int step, int min, int max) {
        int current = plugin.getConfig().getInt(configKey);
        int newValue = increase ? Math.min(max, current + step) : Math.max(min, current - step);
        plugin.getConfig().set(configKey, newValue);
        plugin.saveConfig();
    }

    private void handleLanguage(Player player, boolean next) {
        List<String> languages = plugin.getMessageUtil().getAvailableLanguages();
        if (languages.isEmpty()) return;

        String current = plugin.getConfig().getString("language", "en_us");
        int index = languages.indexOf(current);

        index = next
                ? (index + 1) % languages.size()
                : (index - 1 + languages.size()) % languages.size();

        String newLang = languages.get(index);
        plugin.getConfig().set("language", newLang);
        plugin.saveConfig();
        plugin.getMessageUtil().loadLang();
    }
}