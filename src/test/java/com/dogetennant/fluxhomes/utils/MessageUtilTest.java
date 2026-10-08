package com.dogetennant.fluxhomes.utils;

import com.dogetennant.fluxhomes.FluxHomes;
import com.dogetennant.fluxhomes.TestPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Messages from the translation file, with names filled in. */
class MessageUtilTest {

    @TempDir
    Path dataFolder;
    @TempDir
    Path pluginsFolder;

    private FluxHomes plugin;
    private final Player player = mock(Player.class);

    @BeforeEach
    void setUp() {
        plugin = TestPlugin.mockPlugin(dataFolder, pluginsFolder);
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        when(plugin.getResource(anyString())).thenAnswer(call ->
                MessageUtil.class.getResourceAsStream("/" + call.getArgument(0, String.class)));
    }

    private Component sent(MessageUtil messages, String key, String... replacements) {
        messages.send(player, key, replacements);
        ArgumentCaptor<Component> message = ArgumentCaptor.forClass(Component.class);
        verify(player).sendMessage(message.capture());
        return message.getValue();
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    /** The component and everything inside it. */
    private static List<Component> parts(Component component) {
        List<Component> parts = new ArrayList<>();
        parts.add(component);
        for (Component child : component.children()) parts.addAll(parts(child));
        return parts;
    }

    @Test
    void aMessageIsTakenFromTheLanguageFileWithTheNameFilledIn() {
        Component message = sent(new MessageUtil(plugin), "home-set", "{home}", "base");

        assertThat(plain(message)).isEqualTo("[FluxHomes] Home base has been set.");
    }

    @Test
    void aHomeNameIsShownAsTypedNotAsFormatting() {
        // a player's own home name, later shown to an admin by /ha list
        Component message = sent(new MessageUtil(plugin), "home-list-entry",
                "{home}", "<click:run_command:/stop>x&c");

        assertThat(plain(message)).isEqualTo(" - <click:run_command:/stop>x&c");
        assertThat(parts(message)).allSatisfy(part -> assertThat(part.clickEvent()).isNull());
    }

    @Test
    void legacyColourCodesInTheLanguageFileStillWork() throws Exception {
        Path translations = Files.createDirectories(dataFolder.resolve("translations"));
        Files.writeString(translations.resolve("en_us.yml"), "home-set: \"&aHome {home} is set\"\n");

        Component message = sent(new MessageUtil(plugin), "home-set", "{home}", "base");

        assertThat(plain(message)).isEqualTo("Home base is set");
        assertThat(parts(message)).anySatisfy(part -> assertThat(part.color()).isEqualTo(NamedTextColor.GREEN));
    }

    @Test
    void aMissingMessageSaysWhichKeyIsMissing() {
        Component message = sent(new MessageUtil(plugin), "no-such-message");

        assertThat(plain(message)).isEqualTo("Missing message: no-such-message");
    }
}
