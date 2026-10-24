package com.hollowshift.registry;

import com.hollowshift.HollowShift;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

/** Every custom sound. Files and subtitles are declared in assets/hollowshift/sounds.json. */
public final class ModSounds {

    public static final SoundEvent DRONE = reg("ambient.drone");
    public static final SoundEvent HEARTBEAT = reg("ambient.heartbeat");
    public static final SoundEvent BREATHING = reg("ambient.breathing");
    public static final SoundEvent WHISPER = reg("ambient.whisper");
    public static final SoundEvent METAL_GRIND = reg("ambient.metal_grind");
    public static final SoundEvent HOLLOW_STEP = reg("entity.hollow.step");
    public static final SoundEvent HOLLOW_CATCH = reg("entity.hollow.catch");
    public static final SoundEvent HOLLOW_VANISH = reg("entity.hollow.vanish");

    // Silent sounds whose only purpose is their subtitle. Lying subtitles.
    public static final SoundEvent LIE_FOOTSTEPS = reg("lie.footsteps_behind");
    public static final SoundEvent LIE_DOOR = reg("lie.door_creaks");
    public static final SoundEvent LIE_BREATHING = reg("lie.breathing_close");
    public static final SoundEvent LIE_WATCHING = reg("lie.something_watches");
    public static final SoundEvent LIE_NAME = reg("lie.your_name");

    public static final SoundEvent[] LIES = {LIE_FOOTSTEPS, LIE_DOOR, LIE_BREATHING, LIE_WATCHING, LIE_NAME};

    private ModSounds() {}

    private static SoundEvent reg(String name) {
        var id = HollowShift.id(name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }

    /** Forces class loading so the static fields above are registered at the right time. */
    public static void register() {}
}
