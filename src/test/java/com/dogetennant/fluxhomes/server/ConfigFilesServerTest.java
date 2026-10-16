package com.dogetennant.fluxhomes.server;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.dogetennant.fluxhomes.server.TestServer.messages;
import static org.assertj.core.api.Assertions.assertThat;

/** config.yml and the translations as admins edit them by hand, in the settings menu and by command. */
class ConfigFilesServerTest {

    private static final String TYPO = "\nbroken: [no closing bracket\n";

    private TestServer mc;

    @BeforeEach
    void setUp() {
        mc = TestServer.start();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private YamlConfiguration configOnDisk() {
        return YamlConfiguration.loadConfiguration(mc.file("config.yml").toFile());
    }

    @Test
    void startsWithTheBundledConfigAndTranslations() {
        assertThat(mc.plugin.isEnabled()).isTrue();
        assertThat(mc.file("config.yml")).exists();
        assertThat(mc.file("translations/cs_cz.yml")).exists();
        assertThat(mc.plugin.getConfigUtil().getMaxHomes("default")).isEqualTo(3);
    }

    /** docs/problems/fluxhomes-settings-files-replaced-after-typo.md */
    @Test
    void aTypoInConfigYmlAtStartIsNotReplacedByTheDefaultsAndFluxHomesStaysOff() throws IOException {
        mc.replace("config.yml", "storage-type: sqlite", "storage-type: mysql");
        mc.replace("config.yml", "table-prefix: \"\"", "table-prefix: \"fh_\"");
        mc.append("config.yml", TYPO);
        String broken = Files.readString(mc.file("config.yml"));

        mc.restart();

        assertThat(Files.readString(mc.file("config.yml"))).isEqualTo(broken);
        assertThat(mc.plugin.isEnabled()).isFalse(); // not on SQLite with no table prefix
    }

    /** docs/problems/fluxhomes-settings-files-replaced-after-typo.md */
    @Test
    void aReloadWithATypoInConfigYmlKeepsTheSettingsFromBefore() throws IOException {
        PlayerMock admin = mc.admin();
        mc.replace("config.yml", "  default: 3", "  default: 5");
        admin.performCommand("ha reload");
        mc.append("config.yml", TYPO);
        String broken = Files.readString(mc.file("config.yml"));
        messages(admin);

        admin.performCommand("ha reload");

        assertThat(messages(admin)).singleElement().asString()
                .contains("config.yml has a mistake (YAML error)");
        assertThat(mc.plugin.getConfigUtil().getMaxHomes("default")).isEqualTo(5);
        assertThat(Files.readString(mc.file("config.yml"))).isEqualTo(broken);
    }

    /** docs/problems/fluxhomes-settings-files-replaced-after-typo.md */
    @Test
    void switchingLanguageByCommandKeepsAHandEditOfConfigYml() throws IOException {
        PlayerMock admin = mc.admin();
        mc.replace("config.yml", "  default: 3", "  default: 7"); // not reloaded yet

        admin.performCommand("ha language cs_cz");

        assertThat(configOnDisk().getInt("max-homes.default")).isEqualTo(7);
        assertThat(configOnDisk().getString("language")).isEqualTo("cs_cz");
    }

    /** docs/problems/fluxhomes-settings-files-replaced-after-typo.md */
    @Test
    void aChangeInTheSettingsMenuKeepsAHandEditOfConfigYml() throws IOException {
        PlayerMock admin = mc.admin();
        admin.performCommand("ha settings");
        mc.replace("config.yml", "  seconds: 5", "  seconds: 9"); // the cooldown, not reloaded yet

        mc.click(admin, 15); // warmup on/off

        assertThat(configOnDisk().getInt("cooldown.seconds")).isEqualTo(9);
        assertThat(configOnDisk().getBoolean("warmup.enabled")).isFalse();
        assertThat(mc.plugin.getConfigUtil().isWarmupEnabled()).isFalse();
    }

    /** docs/problems/fluxhomes-settings-files-replaced-after-typo.md */
    @Test
    void aChangeAfterATypoInConfigYmlLeavesTheFileAloneAndTellsTheAdmin() throws IOException {
        PlayerMock admin = mc.admin();
        admin.performCommand("ha settings");
        mc.append("config.yml", TYPO);
        String broken = Files.readString(mc.file("config.yml"));
        messages(admin);

        mc.click(admin, 15);
        admin.performCommand("ha language cs_cz");

        assertThat(Files.readString(mc.file("config.yml"))).isEqualTo(broken);
        assertThat(messages(admin)).anyMatch(message -> message.contains("config.yml has a mistake (YAML error)"));
    }

    /** docs/problems/fluxhomes-settings-files-replaced-after-typo.md */
    @Test
    void aTypoInATranslationIsNotReplacedByTheEnglishTexts() throws IOException {
        PlayerMock admin = mc.admin();
        admin.performCommand("ha language cs_cz");
        mc.append("translations/cs_cz.yml", TYPO);
        String broken = Files.readString(mc.file("translations/cs_cz.yml"));
        messages(admin);

        admin.performCommand("ha reload");

        assertThat(Files.readString(mc.file("translations/cs_cz.yml"))).isEqualTo(broken);
        assertThat(messages(admin)).singleElement().asString()
                .contains("translations/cs_cz.yml").contains("YAML");
    }
}
