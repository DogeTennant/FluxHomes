package com.dogetennant.fluxhomes.commands;

import com.dogetennant.fluxhomes.FluxHomes;
import com.dogetennant.fluxhomes.models.Home;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import com.dogetennant.fluxhomes.models.SetHomeResult;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

import java.util.List;

public class HomeCommand implements CommandExecutor, TabCompleter {

    private final FluxHomes plugin;

    public HomeCommand(FluxHomes plugin) {
        this.plugin = plugin;
    }

    private final Map<UUID, String> pendingDeletions = new HashMap<>();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        switch (command.getName().toLowerCase()) {
            case "sethome" -> handleSetHome(player, args);
            case "home" -> handleHome(player, args);
            case "delhome" -> handleDelHome(player, args);
            case "homes" -> {
                if (args.length > 0 && args[0].equalsIgnoreCase("help")) {
                    handleHelp(player);
                } else {
                    handleHomes(player);
                }
            }
        }

        return true;
    }

    private void handleSetHome(Player player, String[] args) {
        if (!player.hasPermission("fluxhomes.sethome")) {
            plugin.getMessageUtil().send(player, "no-permission");
            return;
        }

        String name = args.length > 0 ? args[0].toLowerCase() : "home";

        switch (plugin.getHomeManager().setHome(player, name)) {
            case SUCCESS -> plugin.getMessageUtil().send(player, "home-set", "{home}", name);
            case LIMIT_REACHED -> {
                int limit = plugin.getHomeManager().getMaxHomes(player);
                plugin.getMessageUtil().send(player, "home-limit-reached", "{limit}", String.valueOf(limit));
            }
            case WORLD_BLOCKED -> plugin.getMessageUtil().send(player, "home-world-blocked");
        }
    }

    private void handleHome(Player player, String[] args) {
        if (!player.hasPermission("fluxhomes.home")) {
            plugin.getMessageUtil().send(player, "no-permission");
            return;
        }

        String name = args.length > 0 ? args[0].toLowerCase() : "home";
        Home home = plugin.getHomeManager().getHome(player.getUniqueId(), name);

        if (home == null) {
            plugin.getMessageUtil().send(player, "home-not-found", "{home}", name);
            return;
        }

        plugin.getHomeManager().teleportHome(player, home);
    }

    private void handleDelHome(Player player, String[] args) {
        if (!player.hasPermission("fluxhomes.delhome")) {
            plugin.getMessageUtil().send(player, "no-permission");
            return;
        }

        if (args.length == 0) {
            plugin.getMessageUtil().send(player, "home-not-found", "{home}", "");
            return;
        }

        String name = args[0].toLowerCase();

        // Check home exists first
        if (plugin.getHomeManager().getHome(player.getUniqueId(), name) == null) {
            plugin.getMessageUtil().send(player, "home-not-found", "{home}", name);
            return;
        }

        if (plugin.getConfigUtil().isConfirmDeletionEnabled()) {
            UUID uuid = player.getUniqueId();

            // If they already have a pending deletion for this home, confirm it
            if (name.equals(pendingDeletions.get(uuid))) {
                pendingDeletions.remove(uuid);
                plugin.getHomeManager().deleteHome(uuid, name);
                plugin.getMessageUtil().send(player, "home-deleted", "{home}", name);
                return;
            }

            // Otherwise store it as pending and ask for confirmation
            pendingDeletions.put(uuid, name);
            plugin.getMessageUtil().send(player, "home-delete-confirm", "{home}", name);

            // Auto-expire the confirmation after timeout
            int timeout = plugin.getConfigUtil().getConfirmDeletionTimeout();
            new org.bukkit.scheduler.BukkitRunnable() {
                @Override
                public void run() {
                    if (name.equals(pendingDeletions.get(uuid))) {
                        pendingDeletions.remove(uuid);
                        if (player.isOnline()) {
                            plugin.getMessageUtil().send(player, "home-delete-cancelled");
                        }
                    }
                }
            }.runTaskLater(plugin, timeout * 20L);

        } else {
            boolean success = plugin.getHomeManager().deleteHome(player.getUniqueId(), name);
            if (success) {
                plugin.getMessageUtil().send(player, "home-deleted", "{home}", name);
            } else {
                plugin.getMessageUtil().send(player, "home-not-found", "{home}", name);
            }
        }
    }

    private void handleHomes(Player player) {
        if (!player.hasPermission("fluxhomes.homes")) {
            plugin.getMessageUtil().send(player, "no-permission");
            return;
        }

        List<Home> homes = plugin.getHomeManager().getHomes(player.getUniqueId());

        if (homes.isEmpty()) {
            plugin.getMessageUtil().send(player, "home-list-empty");
            return;
        }

        plugin.getMessageUtil().send(player, "home-list-header");
        for (Home home : homes) {
            plugin.getMessageUtil().send(player, "home-list-entry", "{home}", home.getName());
        }
    }

    private void handleHelp(Player player) {
        plugin.getMessageUtil().send(player, "help-header");
        plugin.getMessageUtil().send(player, "help-sethome");
        plugin.getMessageUtil().send(player, "help-home");
        plugin.getMessageUtil().send(player, "help-delhome");
        plugin.getMessageUtil().send(player, "help-homes");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return List.of();
        if (args.length != 1) return List.of();

        String partial = args[0].toLowerCase();

        if (command.getName().equalsIgnoreCase("home") ||
                command.getName().equalsIgnoreCase("delhome")) {
            return plugin.getHomeManager().getHomes(player.getUniqueId())
                    .stream()
                    .map(Home::getName)
                    .filter(name -> name.startsWith(partial))
                    .toList();
        }

        return List.of();
    }
}