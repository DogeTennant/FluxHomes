package com.dogetennant.fluxhomes.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import com.dogetennant.fluxhomes.FluxHomes;
import java.util.Arrays;
import java.util.List;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class MessageUtil {

    private final FluxHomes plugin;
    private FileConfiguration langConfig;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private static final String[] BUNDLED_LANGUAGES = {"en_us", "cs_cz"}; // Add new languages here

    public MessageUtil(FluxHomes plugin) {
        this.plugin = plugin;
        loadLang();
    }

    public void loadLang() {
        String language = plugin.getConfig().getString("language", "en_us");
        File translationsFolder = new File(plugin.getDataFolder(), "translations");

        if (!translationsFolder.exists()) {
            translationsFolder.mkdirs();
        }

        // Copy all bundled languages if they don't exist yet
        for (String bundled : BUNDLED_LANGUAGES) {
            File bundledFile = new File(translationsFolder, bundled + ".yml");
            if (!bundledFile.exists()) {
                copyDefaultLang(bundledFile, bundled);
            }
        }

        File langFile = new File(translationsFolder, language + ".yml");

        if (!langFile.exists()) {
            plugin.getLogger().warning("Translation file '" + language + ".yml' not found. Falling back to en_us.");
            langFile = new File(translationsFolder, "en_us.yml");
        }

        langConfig = YamlConfiguration.loadConfiguration(langFile);

        // Add missing keys from default without overwriting
        InputStream defaultStream = plugin.getResource("translations/en_us.yml");
        if (defaultStream != null) {
            YamlConfiguration defaultLang = YamlConfiguration.loadConfiguration(
                    new java.io.InputStreamReader(defaultStream));
            boolean changed = false;
            for (String key : defaultLang.getKeys(true)) {
                if (!langConfig.contains(key)) {
                    langConfig.set(key, defaultLang.get(key));
                    changed = true;
                }
            }
            if (changed) {
                try {
                    langConfig.save(langFile);
                    plugin.getLogger().info("Added missing keys to translation file.");
                } catch (IOException e) {
                    plugin.getLogger().severe("Failed to update translation file: " + e.getMessage());
                }
            }
        }
    }

    private void copyDefaultLang(File destination, String language) {
        try (InputStream in = plugin.getResource("translations/" + language + ".yml");
             OutputStream out = new FileOutputStream(destination)) {
            if (in == null) {
                plugin.getLogger().severe("Bundled translation file '" + language + ".yml' not found in plugin resources!");
                return;
            }
            in.transferTo(out);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to copy translation file '" + language + "': " + e.getMessage());
        }
    }

    public List<String> getAvailableLanguages() {
        File translationsFolder = new File(plugin.getDataFolder(), "translations");
        File[] files = translationsFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return List.of();
        return Arrays.stream(files)
                .map(f -> f.getName().replace(".yml", ""))
                .sorted()
                .toList();
    }

    public void send(Player player, String key, String... replacements) {
        String raw = langConfig.getString(key, "<red>Missing message: " + key + "</red>");
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            raw = raw.replace(replacements[i], replacements[i + 1]);
        }
        raw = convertLegacy(raw);
        Component message = miniMessage.deserialize(raw);
        player.sendMessage(message);
    }

    private String convertLegacy(String input) {
        input = input
                .replace("&0", "<black>")
                .replace("&1", "<dark_blue>")
                .replace("&2", "<dark_green>")
                .replace("&3", "<dark_aqua>")
                .replace("&4", "<dark_red>")
                .replace("&5", "<dark_purple>")
                .replace("&6", "<gold>")
                .replace("&7", "<gray>")
                .replace("&8", "<dark_gray>")
                .replace("&9", "<blue>")
                .replace("&a", "<green>")
                .replace("&b", "<aqua>")
                .replace("&c", "<red>")
                .replace("&d", "<light_purple>")
                .replace("&e", "<yellow>")
                .replace("&f", "<white>")
                .replace("&l", "<bold>")
                .replace("&o", "<italic>")
                .replace("&n", "<underlined>")
                .replace("&m", "<strikethrough>")
                .replace("&k", "<obfuscated>")
                .replace("&r", "<reset>");

        // Also handle uppercase variants like &A, &B, etc.
        input = input
                .replace("&A", "<green>")
                .replace("&B", "<aqua>")
                .replace("&C", "<red>")
                .replace("&D", "<light_purple>")
                .replace("&E", "<yellow>")
                .replace("&F", "<white>")
                .replace("&L", "<bold>")
                .replace("&O", "<italic>")
                .replace("&N", "<underlined>")
                .replace("&M", "<strikethrough>")
                .replace("&K", "<obfuscated>")
                .replace("&R", "<reset>");

        return input;
    }
}