package com.hollowshift;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hollowshift.director.DirectorConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * config/hollowshift.json. Created with defaults on first launch; edit it and restart.
 */
public final class HollowConfig {

    // ---- Gameplay ----
    /** Fake chat, lying subtitles, fake save screens, inventory edits. Some players find these too confusing. */
    public boolean metaScares = true;
    /** Whether a confrontation can take your held item and torches. */
    public boolean stealItems = true;
    /** Damage (in half-hearts) when the Hollow catches you. 0 = harmless scares only. */
    public float catchDamage = 6.0f;

    // ---- Visuals (client) ----
    public boolean tensionFog = true;
    public boolean postEffects = true;
    public boolean chromaticAberration = true;
    /** Turns off flicker, colour fringing and fast pulses. */
    public boolean photosensitiveMode = false;
    /**
     * What to do when an Iris shader pack is active:
     * "auto" = skip the mod's own fog and screen effects (the pack handles atmosphere),
     * "always" = apply them on top of the pack anyway.
     */
    public String withIrisShaders = "auto";

    // ---- Audio (client) ----
    /** The low drone and heartbeat that rise with tension. */
    public boolean tensionAmbience = true;

    // ---- Pacing ----
    public DirectorConfig director = DirectorConfig.defaults();

    // ------------------------------------------------------------------------

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static HollowConfig instance;

    public static HollowConfig get() {
        if (instance == null) instance = load();
        return instance;
    }

    private static HollowConfig load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("hollowshift.json");
        HollowConfig cfg = null;
        try {
            if (Files.exists(file)) cfg = GSON.fromJson(Files.readString(file), HollowConfig.class);
        } catch (Exception e) {
            HollowShift.LOG.warn("Couldn't read {}, using defaults: {}", file, e.getMessage());
        }
        if (cfg == null) cfg = new HollowConfig();
        if (cfg.director == null) cfg.director = DirectorConfig.defaults();
        try {
            Files.writeString(file, GSON.toJson(cfg)); // writes any new options added since last version
        } catch (IOException e) {
            HollowShift.LOG.warn("Couldn't write {}: {}", file, e.getMessage());
        }
        return cfg;
    }
}
