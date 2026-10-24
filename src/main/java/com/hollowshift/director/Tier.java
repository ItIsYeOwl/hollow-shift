package com.hollowshift.director;

/**
 * Scare tiers, from background unease to direct encounters.
 * Each tier has default gates and costs (see {@link EventDef.Builder}).
 */
public enum Tier {
    /** Barely noticeable: a twig snap, a distant figure, peripheral decay. */
    AMBIENT,
    /** The world was touched while you weren't looking: torches out, chests sorted. */
    TAMPERING,
    /** The game itself lies: fake chat, lying subtitles, inventory edits. */
    META,
    /** The entity, close. Always preceded by silence. */
    CONFRONTATION
}
