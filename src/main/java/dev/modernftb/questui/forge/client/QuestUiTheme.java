package dev.modernftb.questui.forge.client;

import dev.modernftb.questui.forge.QuestUiMod;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;

final class QuestUiTheme {
    private static final String SETTINGS_PATH = "config/questui-client.properties";
    private static final Set<String> THEMES = Set.of("green", "yellow", "blue", "cyan", "purple", "pink");
    private static String current = "green";
    private static boolean loaded;

    private QuestUiTheme() {
    }

    static void loadIfNeeded(Path gameDirectory) {
        if (loaded) {
            return;
        }
        loaded = true;
        Path settings = gameDirectory.resolve(SETTINGS_PATH);
        if (!Files.isRegularFile(settings)) {
            return;
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(settings)) {
            properties.load(input);
            current = normalize(properties.getProperty("theme", "green"));
        } catch (IOException exception) {
            QuestUiMod.LOGGER.warn("Could not read QuestUI client settings", exception);
        }
    }

    static String currentId() {
        return current;
    }

    static void select(String id, Path gameDirectory) {
        String next = normalize(id);
        if (next.equals(current)) {
            return;
        }
        current = next;
        loaded = true;
        write(gameDirectory);
    }

    static int completedDependencyArgb() {
        return 0xff000000 | QuestUiTheme.palette().bright();
    }

    private static Palette palette() {
        return switch (current) {
            case "yellow" -> new Palette(0x987317, 0xf0c34e, 0xc99c2d, 0x44391e, 0xf7d875, 0xf1e4c4);
            case "blue" -> new Palette(0x1d68b5, 0x4ea2ef, 0x3785d3, 0x213750, 0x82c5ff, 0xd4e7fa);
            case "cyan" -> new Palette(0x08777b, 0x43c7c2, 0x299da0, 0x1d3c3f, 0x74e0da, 0xceeae7);
            case "purple" -> new Palette(0x5143a2, 0x9285f2, 0x7567d7, 0x302c50, 0xb5aaff, 0xe0ddfa);
            case "pink" -> new Palette(0xa13e68, 0xf071a5, 0xcf5689, 0x482c3b, 0xffa0c2, 0xf3dbe5);
            default -> new Palette(0x3c8527, 0x65b742, 0x4b9a32, 0x29422a, 0x8bd46a, 0xd9e8d2);
        };
    }

    private record Palette(int primary, int bright, int hover, int selected, int light, int accentText) {
    }

    private static void write(Path gameDirectory) {
        Path settings = gameDirectory.resolve(SETTINGS_PATH);
        Properties properties = new Properties();
        properties.setProperty("theme", current);
        try {
            Files.createDirectories(settings.getParent());
            try (OutputStream output = Files.newOutputStream(settings)) {
                properties.store(output, "QuestUI client preferences");
            }
        } catch (IOException exception) {
            QuestUiMod.LOGGER.warn("Could not save QuestUI client settings", exception);
        }
    }

    private static String normalize(String id) {
        if (id == null) {
            return "green";
        }
        String candidate = id.trim().toLowerCase(java.util.Locale.ROOT);
        return THEMES.contains(candidate) ? candidate : "green";
    }
}
