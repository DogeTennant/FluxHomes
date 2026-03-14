package com.dogetennant.fluxhomes.gui;

import com.dogetennant.fluxhomes.FluxHomes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class SettingsGUI {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    public static final String TITLE = "FluxHomes Settings";

    public static void open(Player player, FluxHomes plugin) {
        Inventory inv = Bukkit.createInventory(null, 54, MM.deserialize("<dark_gray>" + TITLE));

        // Fill borders
        ItemStack border = buildItem(Material.GRAY_STAINED_GLASS_PANE, "<gray>", List.of());
        for (int i = 0; i < 9; i++) inv.setItem(i, border);
        for (int i = 45; i < 54; i++) inv.setItem(i, border);
        for (int i = 1; i < 6; i++) {
            inv.setItem(i * 9, border);
            inv.setItem(i * 9 + 8, border);
        }

// Max Homes - slot 40 (second row from bottom, middle)
        inv.setItem(40, buildItem(Material.CHEST,
                "<aqua>Max Homes",
                List.of(
                        "<gray>Current: <white>" + plugin.getConfigUtil().getMaxHomes("default"),
                        "<gray>Left-click: <green>+1",
                        "<gray>Right-click: <red>-1"
                )));

// Cooldown toggle - slot 11
        inv.setItem(11, buildToggleItem(
                "<aqua>Cooldown",
                plugin.getConfigUtil().isCooldownEnabled(),
                List.of(
                        "<gray>Prevents spam teleporting.",
                        "<gray>Click to toggle."
                )));

// Cooldown seconds - slot 12
        inv.setItem(12, buildItem(Material.CLOCK,
                "<aqua>Cooldown Duration",
                List.of(
                        "<gray>Current: <white>" + plugin.getConfigUtil().getCooldownSeconds() + "s",
                        "<gray>Left-click: <green>+1s",
                        "<gray>Right-click: <red>-1s"
                )));

// Warmup toggle - slot 15
        inv.setItem(15, buildToggleItem(
                "<aqua>Warmup",
                plugin.getConfigUtil().isWarmupEnabled(),
                List.of(
                        "<gray>Player must stand still before teleporting.",
                        "<gray>Click to toggle."
                )));

// Warmup seconds - slot 16
        inv.setItem(16, buildItem(Material.SAND,
                "<aqua>Warmup Duration",
                List.of(
                        "<gray>Current: <white>" + plugin.getConfigUtil().getWarmupSeconds() + "s",
                        "<gray>Left-click: <green>+1s",
                        "<gray>Right-click: <red>-1s"
                )));

// Teleport sound - slot 20
        inv.setItem(20, buildToggleItem(
                "<aqua>Teleport Sound",
                plugin.getConfigUtil().isTeleportSoundEnabled(),
                List.of(
                        "<gray>Plays enderman sound on teleport.",
                        "<gray>Click to toggle."
                )));

// Respawn at home - slot 22
        inv.setItem(22, buildToggleItem(
                "<aqua>Respawn at Home",
                plugin.getConfigUtil().isRespawnAtHomeEnabled(),
                List.of(
                        "<gray>Respawn at home named 'home' on death.",
                        "<gray>Click to toggle."
                )));

// Confirm deletion toggle - slot 24
        inv.setItem(24, buildToggleItem(
                "<aqua>Confirm Deletion",
                plugin.getConfigUtil().isConfirmDeletionEnabled(),
                List.of(
                        "<gray>Require confirmation before deleting homes.",
                        "<gray>Click to toggle."
                )));

// Confirm deletion timeout - slot 25
        inv.setItem(25, buildItem(Material.BARRIER,
                "<aqua>Deletion Timeout",
                List.of(
                        "<gray>Current: <white>" + plugin.getConfigUtil().getConfirmDeletionTimeout() + "s",
                        "<gray>Left-click: <green>+5s",
                        "<gray>Right-click: <red>-5s"
                )));

// Blocked worlds toggle - slot 29
        List<String> blockedWorlds = plugin.getConfigUtil().getBlockedWorlds();
        List<String> blockedLore = new ArrayList<>();
        blockedLore.add("<gray>Prevent homes in certain worlds.");
        blockedLore.add("<gray>Blocked: <white>" + (blockedWorlds.isEmpty() ? "none" : String.join(", ", blockedWorlds)));
        blockedLore.add("<gray>Click to toggle.");
        inv.setItem(29, buildToggleItem(
                "<aqua>Block Worlds",
                plugin.getConfigUtil().isWorldBlockingEnabled(),
                blockedLore));

// Blocked worlds editor - slot 30
        inv.setItem(30, buildItem(Material.PAPER,
                "<aqua>Edit Blocked Worlds",
                List.of(
                        "<gray>Left-click: <green>add a world",
                        "<gray>Right-click: <red>remove a world",
                        "<gray>Blocked: <white>" + (blockedWorlds.isEmpty() ? "none" : String.join(", ", blockedWorlds))
                )));

// Language - slot 33
        String currentLang = plugin.getConfig().getString("language", "en_us");
        List<String> languages = plugin.getMessageUtil().getAvailableLanguages();
        inv.setItem(33, buildItem(Material.BOOK,
                "<aqua>Language",
                List.of(
                        "<gray>Current: <white>" + currentLang,
                        "<gray>Available: <white>" + String.join(", ", languages),
                        "<gray>Left-click: <green>next",
                        "<gray>Right-click: <red>previous"
                )));

        player.openInventory(inv);
    }

    public static ItemStack buildToggleItem(String name, boolean enabled, List<String> extraLore) {
        Material mat = enabled ? Material.GREEN_CONCRETE : Material.RED_CONCRETE;
        String status = enabled ? "<green>Enabled" : "<red>Disabled";
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Status: " + status);
        lore.addAll(extraLore);
        return buildItem(mat, name, lore);
    }

    public static ItemStack buildItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(name));
        meta.lore(lore.stream().map(MM::deserialize).toList());
        item.setItemMeta(meta);
        return item;
    }
}