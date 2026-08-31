package com.betterbees.client;

import com.betterbees.BetterBees;
import com.betterbees.platform.LoaderHooks;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import java.nio.file.Files;
import java.nio.file.Path;

/** Identical client-only TOML semantics on both loaders; read once per game launch. */
public final class ClientAudioConfig {
    private static boolean loaded;
    private static boolean adaptive = true;
    private static int maxLoops = 8;

    private ClientAudioConfig() {}

    public static boolean adaptive() { load(); return adaptive; }
    public static int maxLoops() { load(); return maxLoops; }

    private static void load() {
        if (loaded) return;
        loaded = true;
        Path path = LoaderHooks.configDirectory().resolve("betterbees-client.toml");
        try {
            Files.createDirectories(path.getParent());
            try (CommentedFileConfig config = CommentedFileConfig.builder(path).sync().build()) {
                config.load();
                Object enabled = config.get("audio.adaptive_bee_sounds");
                Object limit = config.get("audio.max_bee_loops");
                adaptive = enabled instanceof Boolean value ? value : true;
                maxLoops = limit instanceof Number value ? Math.max(1, Math.min(64, value.intValue())) : 8;
                if (enabled == null) config.set("audio.adaptive_bee_sounds", true);
                if (limit == null) config.set("audio.max_bee_loops", 8);
                config.setComment("audio.adaptive_bee_sounds", "Limit overlapping bee buzz loops. Restart the client after changing audio settings.");
                config.setComment("audio.max_bee_loops", "Maximum simultaneous bee buzz loops per listener (1-64). Angry bees take priority.");
                config.save();
            }
        } catch (Exception exception) {
            adaptive = true;
            maxLoops = 8;
            BetterBees.LOGGER.error("Could not load {}; using default client audio settings.", path, exception);
        }
    }
}
