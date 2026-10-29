package dev.modernftb.questui.forge.client;

import com.sighs.apricityui.parser.HTML;
import dev.modernftb.questui.forge.QuestUiMod;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import net.minecraft.client.Minecraft;

final class QuestUiResources {
    private static final String[] DEFAULT_FILES = new String[]{"questbook-v8.html", "questbook-v8.css"};
    private static final String MANIFEST_NAME = ".questui-defaults.properties";
    private static final String OBSOLETE_DIRECTORY = "_obsolete";
    private static final String PRE_UPGRADE_SUFFIX = ".pre-upgrade";
    private static String cachedPagePath;
    private static String cachedPageSignature;

    private QuestUiResources() {
    }

    static boolean installDefaults() {
        Path directory = Minecraft.m_91087_().f_91069_.toPath().resolve("apricity").resolve("questui");
        try {
            Files.createDirectories(directory);
            Properties manifest = QuestUiResources.loadManifest(directory);
            Set<String> bundledNames = new LinkedHashSet<>(List.of(DEFAULT_FILES));
            for (String name : DEFAULT_FILES) {
                QuestUiResources.installDefaultFile(directory, manifest, name);
            }
            QuestUiResources.retireObsoleteDefaults(directory, manifest, bundledNames);
            QuestUiResources.saveManifest(directory, manifest);
            return true;
        }
        catch (IOException exception) {
            QuestUiMod.LOGGER.error("Could not install the default Apricity Quests page", (Throwable)exception);
            return false;
        }
    }

    /**
     * Installs one bundled page file. A file that already exists is only replaced when it can be
     * proven to be an unmodified default, so player customizations survive a mod update.
     */
    private static void installDefaultFile(Path directory, Properties manifest, String name) throws IOException {
        Path target = directory.resolve(name);
        byte[] bundled = QuestUiResources.readBundled(name);
        String nextHash = QuestUiResources.sha256(bundled);
        if (!Files.exists(target, new LinkOption[0])) {
            Files.write(target, bundled);
            manifest.setProperty(name, nextHash);
            return;
        }
        String currentHash = QuestUiResources.sha256(Files.readAllBytes(target));
        if (currentHash.equals(nextHash)) {
            manifest.setProperty(name, nextHash);
            return;
        }
        String previousHash = manifest.getProperty(name);
        if (previousHash != null && currentHash.equals(previousHash)) {
            Files.write(target, bundled);
            manifest.setProperty(name, nextHash);
            QuestUiMod.LOGGER.info("QuestUI updated the unmodified default page file {}", (Object)name);
            return;
        }
        if (previousHash != null) {
            QuestUiMod.LOGGER.warn("QuestUI kept your customized page file {}; the bundled default has changed since, so re-apply your edits if the quest book looks outdated",
                    (Object)name);
            manifest.setProperty(name + ".customized", currentHash);
            return;
        }
        // No manifest record: the file predates this scheme, so its provenance is unknown.
        // Preserve it instead of silently discarding it, then install the current default.
        Path backup = directory.resolve(OBSOLETE_DIRECTORY).resolve(name + PRE_UPGRADE_SUFFIX);
        Files.createDirectories(backup.getParent());
        Files.move(target, backup, StandardCopyOption.REPLACE_EXISTING);
        Files.write(target, bundled);
        manifest.setProperty(name, nextHash);
        QuestUiMod.LOGGER.warn("QuestUI replaced the page file {} with the bundled default; your previous file is preserved at {}",
                (Object)name, (Object)backup);
    }

    /**
     * Moves page files that an older QuestUI installed as defaults but that this version no longer
     * ships. Files the player edited are left alone.
     */
    private static void retireObsoleteDefaults(Path directory, Properties manifest, Set<String> bundledNames) throws IOException {
        for (String key : List.copyOf(manifest.stringPropertyNames())) {
            if (key.endsWith(".customized") || bundledNames.contains(key)) {
                continue;
            }
            Path target = directory.resolve(key);
            String recordedHash = manifest.getProperty(key);
            if (Files.exists(target, new LinkOption[0])) {
                String currentHash = QuestUiResources.sha256(Files.readAllBytes(target));
                if (currentHash.equals(recordedHash)) {
                    Path archived = directory.resolve(OBSOLETE_DIRECTORY).resolve(key);
                    Files.createDirectories(archived.getParent());
                    Files.move(target, archived, StandardCopyOption.REPLACE_EXISTING);
                    QuestUiMod.LOGGER.info("QuestUI archived the obsolete page file {} to {}", (Object)key, (Object)archived);
                }
                else {
                    QuestUiMod.LOGGER.warn("QuestUI left your customized obsolete page file {} in place", (Object)key);
                }
            }
            manifest.remove(key);
        }
    }

    private static byte[] readBundled(String name) throws IOException {
        String resource = "/assets/questui/apricity/" + name;
        try (InputStream input = QuestUiResources.class.getResourceAsStream(resource);){
            if (input == null) {
                throw new IOException("Missing bundled AUI page resource: " + resource);
            }
            return input.readAllBytes();
        }
    }

    private static Properties loadManifest(Path directory) {
        Properties manifest = new Properties();
        Path file = directory.resolve(MANIFEST_NAME);
        try {
            if (Files.exists(file, new LinkOption[0])) {
                manifest.load(Files.newInputStream(file));
            }
        }
        catch (IOException exception) {
            QuestUiMod.LOGGER.warn("Could not read the QuestUI page manifest; defaults will be treated as untracked", (Throwable)exception);
        }
        return manifest;
    }

    private static void saveManifest(Path directory, Properties manifest) throws IOException {
        try (java.io.OutputStream output = Files.newOutputStream(directory.resolve(MANIFEST_NAME));){
            manifest.store(output, "QuestUI bundled page defaults; managed automatically, do not edit.");
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder result = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                result.append(String.format(Locale.ROOT, "%02x", b));
            }
            return result.toString();
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    static boolean reloadIfChanged(String pagePath, String... dependentFiles) {
        Path resourceRoot = Minecraft.m_91087_().f_91069_.toPath().resolve("apricity");
        StringBuilder signature = new StringBuilder(pagePath);
        QuestUiResources.appendFileSignature(signature, resourceRoot.resolve(pagePath));
        for (String dependentFile : dependentFiles) {
            QuestUiResources.appendFileSignature(signature, resourceRoot.resolve(dependentFile));
        }
        String nextSignature = signature.toString();
        if (pagePath.equals(cachedPagePath) && nextSignature.equals(cachedPageSignature)) {
            return true;
        }
        if (!HTML.reload(pagePath)) {
            return false;
        }
        cachedPagePath = pagePath;
        cachedPageSignature = nextSignature;
        return true;
    }

    private static void appendFileSignature(StringBuilder signature, Path file) {
        signature.append('|').append(file.getFileName()).append(':');
        try {
            signature.append(Files.size(file)).append(':').append(Files.getLastModifiedTime(file));
        } catch (IOException exception) {
            signature.append("unavailable");
        }
    }
}
