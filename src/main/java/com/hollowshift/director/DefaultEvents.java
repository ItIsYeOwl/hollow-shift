package com.hollowshift.director;

import static com.hollowshift.director.Condition.*;
import static com.hollowshift.director.Tier.*;
import static com.hollowshift.director.Trigger.*;

import java.util.List;

/**
 * The starter event pool. Tier defaults (cost, earliest day, tension gate, cooldown)
 * come from EventDef.Builder; only the differences are written here.
 *
 *   tier           cost  from day  tension  cooldown
 *   AMBIENT          1      1         0      0.5 day
 *   TAMPERING        3      4        30      2 days
 *   META             5      6        50      3 days
 *   CONFRONTATION   10     10        65      3 days   (preceded by silence, then a forced calm)
 */
public final class DefaultEvents {

    private DefaultEvents() {}

    public static List<EventDef> all() {
        return List.of(
                // ---------------- AMBIENT: "did I hear something?" ----------------
                EventDef.of("twig_snap", AMBIENT).requires(SURFACE, AWAY_FROM_BASE).weight(1.2).build(),
                EventDef.of("leaves_rustle", AMBIENT).requires(SURFACE).build(),
                EventDef.of("peripheral_decay", AMBIENT).minDay(2).build(),
                EventDef.of("cave_breathing", AMBIENT).requires(UNDERGROUND, DARK).weight(1.3).build(),
                EventDef.of("delayed_footstep", AMBIENT).minDay(3).cooldownDays(1).build(),
                // The first sighting: far away, once every couple of days, never in daylight early on.
                EventDef.of("distant_figure", AMBIENT).minDay(2).requires(SURFACE, NIGHT)
                        .cooldownDays(2).intensity(25).tensionDelta(8).weight(1.5).build(),

                // ---------------- TAMPERING: things changed while you were away ----------------
                EventDef.of("extinguish_torch", TAMPERING).requires(NIGHT, AWAY_FROM_BASE).build(),
                EventDef.of("fake_cow", TAMPERING).requires(SURFACE).cooldownDays(4).build(),
                EventDef.of("mimic_mining", TAMPERING).requires(UNDERGROUND).minDay(5).build(),
                EventDef.of("breadcrumb_reverse", TAMPERING).requires(UNDERGROUND).minDay(6).cooldownDays(4).build(),
                // Discoveries: only revealed when you come home or wake up.
                EventDef.of("door_left_open", TAMPERING).trigger(ON_RETURN_TO_BASE).minTension(20).build(),
                EventDef.of("chest_sorted", TAMPERING).trigger(ON_RETURN_TO_BASE).minTension(20).cooldownDays(3).build(),
                EventDef.of("sign_rewritten", TAMPERING).trigger(ON_RETURN_TO_BASE).minDay(7).cooldownDays(4).build(),
                EventDef.of("bed_moved", TAMPERING).trigger(ON_WAKE).minDay(5).minTension(15).cooldownDays(4).build(),

                // ---------------- META: the game itself lies ----------------
                EventDef.of("lying_subtitle", META).cost(3).weight(1.3).build(),
                EventDef.of("inventory_unlit_torch", META).cost(3).requires(DARK).build(),
                EventDef.of("fake_join_message", META).cooldownDays(5).build(),
                EventDef.of("fake_saving_screen", META).minDay(8).cooldownDays(6).maxFires(3).build(),
                EventDef.of("name_whisper", META).minDay(9).minTension(60).cooldownDays(5).maxFires(3).build(),
                // A silence where nothing comes. Keeps "silence = attack" from becoming a safe rule.
                EventDef.of("the_quiet", META).cost(2).minDay(10).silence(true).weight(0.5)
                        .intensity(15).cooldownDays(4).build(),

                // ---------------- CONFRONTATION: it's here ----------------
                // The signature scare. Its conditions are narrow, so it gets extra weight when they hold.
                EventDef.of("close_encounter", CONFRONTATION).requires(SURFACE, NIGHT, AWAY_FROM_BASE).weight(1.8).build(),
                EventDef.of("tunnel_stalk", CONFRONTATION).requires(UNDERGROUND, DARK).build(),
                // Low tension gate on purpose: the base is where the player feels safe, so tension is low there.
                EventDef.of("base_presence", CONFRONTATION).requires(IN_BASE, NIGHT).minDay(12).minTension(25)
                        .cooldownDays(5).build(),
                // Breaks the "day is safe" rule. Rare on purpose.
                EventDef.of("daylight_visitor", CONFRONTATION).requires(SURFACE, DAY).minDay(13).minTension(35)
                        .maxFires(2).cooldownDays(6).silence(false).weight(0.7).build(),
                EventDef.of("takes_something", CONFRONTATION).minDay(14).requires(DARK).cooldownDays(5)
                        .weight(0.8).build()
        );
    }
}
