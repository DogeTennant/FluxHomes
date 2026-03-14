package com.dogetennant.fluxhomes.commands;

import com.dogetennant.fluxhomes.FluxHomes;
import com.dogetennant.fluxhomes.models.Home;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import com.dogetennant.fluxhomes.gui.SettingsGUI;

import java.util.Arrays;
import java.util.List;

public class AdminHomeCommand implements CommandExecutor, TabCompleter {

    private final FluxHomes plugin;

    public AdminHomeCommand(FluxHomes plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("fluxhomes.admin")) {
            if (sender instanceof Player player) {
                plugin.getMessageUtil().send(player, "no-permission");
            } else {
                sender.sendMessage("You don't have permission to do that.");
            }
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "settings" -> {
                if (sender instanceof Player player) {
                    SettingsGUI.open(player, plugin);
                } else {
                    sender.sendMessage("This command can only be used by players.");
                }
            }
            case "delhome" -> handleAdminDelHome(sender, args);
            case "listhomes" -> handleAdminListHomes(sender, args);
            case "clearhomes" -> handleAdminClearHomes(sender, args);
            case "language" -> handleAdminLanguage(sender, args);
            case "import" -> handleAdminImport(sender, args);
            case "reload" -> handleAdminReload(sender);
            default -> sendUsage(sender);
        }

        return true;
    }

    private void handleAdminDelHome(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendUsage(sender);
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        String homeName = args[2].toLowerCase();
        boolean success = plugin.getHomeManager().deleteHome(target.getUniqueId(), homeName);

        if (sender instanceof Player player) {
            if (success) {
                plugin.getMessageUtil().send(player, "admin-home-deleted",
                        "{home}", homeName, "{player}", args[1]);
            } else {
                plugin.getMessageUtil().send(player, "home-not-found", "{home}", homeName);
            }
        } else {
            sender.sendMessage(success
                    ? "Deleted home " + homeName + " for " + args[1]
                    : "Home not found.");
        }
    }

    private void handleAdminListHomes(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendUsage(sender);
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        List<Home> homes = plugin.getHomeManager().getHomes(target.getUniqueId());

        if (sender instanceof Player player) {
            if (homes.isEmpty()) {
                plugin.getMessageUtil().send(player, "admin-no-homes", "{player}", args[1]);
                return;
            }
            plugin.getMessageUtil().send(player, "admin-home-list-header", "{player}", args[1]);
            for (Home home : homes) {
                plugin.getMessageUtil().send(player, "home-list-entry", "{home}", home.getName());
            }
        } else {
            if (homes.isEmpty()) {
                sender.sendMessage(args[1] + " has no homes.");
                return;
            }
            sender.sendMessage("Homes for " + args[1] + ":");
            homes.forEach(home -> sender.sendMessage(" - " + home.getName()));
        }
    }

    private void handleAdminClearHomes(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendUsage(sender);
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        plugin.getHomeManager().deleteAllHomes(target.getUniqueId());

        if (sender instanceof Player player) {
            plugin.getMessageUtil().send(player, "admin-homes-cleared", "{player}", args[1]);
        } else {
            sender.sendMessage("Cleared all homes for " + args[1] + ".");
        }
    }

    private void handleAdminReload(CommandSender sender) {
        plugin.reloadConfig();
        plugin.getMessageUtil().loadLang();

        if (sender instanceof Player player) {
            plugin.getMessageUtil().send(player, "admin-reloaded");
        } else {
            sender.sendMessage("FluxHomes configuration reloaded.");
        }
    }

    private void sendUsage(CommandSender sender) {
        if (sender instanceof Player player) {
            plugin.getMessageUtil().send(player, "admin-help-header");
            plugin.getMessageUtil().send(player, "admin-help-delhome");
            plugin.getMessageUtil().send(player, "admin-help-listhomes");
            plugin.getMessageUtil().send(player, "admin-help-clearhomes");
            plugin.getMessageUtil().send(player, "admin-help-settings");
            plugin.getMessageUtil().send(player, "admin-help-import");
            plugin.getMessageUtil().send(player, "admin-help-language");
            plugin.getMessageUtil().send(player, "admin-help-reload");
        } else {
            sender.sendMessage("FluxHomes Admin Commands:");
            sender.sendMessage(" /homesadmin delhome <player> <home> - Delete a player's home");
            sender.sendMessage(" /homesadmin listhomes <player> - List a player's homes");
            sender.sendMessage(" /homesadmin clearhomes <player> - Clear all homes for a player");
            sender.sendMessage(" /homesadmin settings - Open the settings GUI");
            sender.sendMessage(" /homesadmin language [name] - Change the language");
            sender.sendMessage(" /homesadmin reload - Reload config and translations");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("fluxhomes.admin")) return List.of();

        if (args.length == 1) {
            return Arrays.asList("delhome", "listhomes", "clearhomes", "reload", "import", "language", "settings")
                    .stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }

        if (args.length == 2 && !args[0].equalsIgnoreCase("reload")
                && !args[0].equalsIgnoreCase("import")
                && !args[0].equalsIgnoreCase("language")) {
            String partial = args[1].toLowerCase();
            return Bukkit.getOnlinePlayers()
                    .stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(partial))
                    .toList();
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("delhome")) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            String partial = args[2].toLowerCase();
            return plugin.getHomeManager().getHomes(target.getUniqueId())
                    .stream()
                    .map(Home::getName)
                    .filter(name -> name.startsWith(partial))
                    .toList();
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("import")) {
            return List.of("essentialsx");
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("language")) {
            return plugin.getMessageUtil().getAvailableLanguages()
                    .stream()
                    .filter(lang -> lang.startsWith(args[1].toLowerCase()))
                    .toList();
        }

        return List.of();
    }

    private void handleAdminLanguage(CommandSender sender, String[] args) {
        if (args.length < 2) {
            // List available languages
            List<String> languages = plugin.getMessageUtil().getAvailableLanguages();
            String current = plugin.getConfig().getString("language", "en_us");
            if (sender instanceof Player player) {
                plugin.getMessageUtil().send(player, "admin-language-list",
                        "{languages}", String.join(", ", languages),
                        "{current}", current);
            } else {
                sender.sendMessage("Available languages: " + String.join(", ", languages));
                sender.sendMessage("Current: " + current);
            }
            return;
        }

        String newLang = args[1].toLowerCase();
        List<String> available = plugin.getMessageUtil().getAvailableLanguages();

        if (!available.contains(newLang)) {
            if (sender instanceof Player player) {
                plugin.getMessageUtil().send(player, "admin-language-not-found", "{language}", newLang);
            } else {
                sender.sendMessage("Language '" + newLang + "' not found in translations folder.");
            }
            return;
        }

        plugin.getConfig().set("language", newLang);
        plugin.saveConfig();
        plugin.getMessageUtil().loadLang();

        if (sender instanceof Player player) {
            plugin.getMessageUtil().send(player, "admin-language-changed", "{language}", newLang);
        } else {
            sender.sendMessage("Language changed to " + newLang + ".");
        }
    }

    private void handleAdminImport(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /homesadmin import essentialsx");
            return;
        }

        if (!args[1].equalsIgnoreCase("essentialsx")) {
            sender.sendMessage("Unknown import source. Available: essentialsx");
            return;
        }

        if (sender instanceof Player player) {
            plugin.getMessageUtil().send(player, "admin-import-started");
        } else {
            sender.sendMessage("Starting EssentialsX import, please wait...");
        }

        // Run async since it could be reading hundreds of files
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                int count = plugin.getHomeManager().importFromEssentialsX();

                new org.bukkit.scheduler.BukkitRunnable() {
                    @Override
                    public void run() {
                        if (count == -1) {
                            if (sender instanceof Player player) {
                                plugin.getMessageUtil().send(player, "admin-import-not-found");
                            } else {
                                sender.sendMessage("EssentialsX userdata folder not found.");
                            }
                        } else {
                            if (sender instanceof Player player) {
                                plugin.getMessageUtil().send(player, "admin-import-complete",
                                        "{count}", String.valueOf(count));
                            } else {
                                sender.sendMessage("Import complete. Imported " + count + " homes.");
                            }
                        }
                    }
                }.runTask(plugin);
            }
        }.runTaskAsynchronously(plugin);
    }
}