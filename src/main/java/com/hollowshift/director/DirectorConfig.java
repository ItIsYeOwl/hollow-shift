package com.hollowshift.director;

/**
 * Every pacing knob in one place. Field names map 1:1 to a "director" section in
 * config.json, so players (and you, while playtesting) can tune without recompiling.
 *
 * Time units: 20 ticks = 1 second, 1200 ticks = 1 minute, 24000 ticks = 1 in-game day (20 min).
 */
public final class DirectorConfig {

    // --- Evaluation ---------------------------------------------------------
    /** How often the adapter calls Director.tick() (ticks). 40 = every 2 seconds. */
    public int evalIntervalTicks = 40;

    // --- Daily scare budget -------------------------------------------------
    /** Budget on day 1. Ambient events cost 1, so day 1 gets one tiny thing at most. */
    public double baseBudget = 1.0;
    /** Extra budget per day survived. This is the main escalation lever. */
    public double budgetPerDay = 1.5;
    /** Cap so day 60 isn't non-stop. */
    public double maxBudget = 28.0;

    // --- Chance to fire on a periodic evaluation ----------------------------
    public double baseChancePerEval = 0.006;
    public double nightMultiplier = 2.0;
    public double undergroundMultiplier = 1.4;
    /** Standing near light makes the world calmer. Torches matter. */
    public double litMultiplier = 0.6;
    /** The longer nothing happens, the likelier something will (ticks to double the chance). */
    public double droughtRampTicks = 6000;
    /** Drought pressure stops growing after this long. */
    public double droughtCapTicks = 18000;
    public double maxChancePerEval = 0.25;

    // --- Spacing ------------------------------------------------------------
    /** Absolute minimum gap between any two events (30 s). */
    public long minGapTicks = 600;
    /** Minimum gap between two events of the same tier, indexed by Tier.ordinal(). */
    public long[] tierGapTicks = {
            1200,   // AMBIENT: 1 min
            3600,   // TAMPERING: 3 min
            6000,   // META: 5 min
            14400   // CONFRONTATION: 12 min
    };
    /** Recent events are heavily down-weighted so the pool feels varied. */
    public int historySize = 6;
    public double recentRepeatPenalty = 0.15;
    public double sameTierPenalty = 0.6;
    /** Non-ambient tiers gain +1x weight per this many days without one... */
    public double overdueDays = 2.0;
    /** ...up to this much extra (3x total). */
    public double maxOverdueBoost = 2.0;

    // --- Intensity & relax (the "breathing" of the horror) -------------------
    /** Intensity at which the Director stops everything and lets the player relax. */
    public double peakIntensity = 70;
    /** Intensity must decay below this before scares resume. */
    public double resumeIntensity = 25;
    /** Intensity halves every N seconds. */
    public double intensityHalfLifeSeconds = 90;
    /** Forced calm after a peak, random within this range (1.5 to 3.5 min). */
    public long relaxMinTicks = 1800;
    public long relaxMaxTicks = 4200;

    // --- Silence lead-in before confrontations ------------------------------
    public int silenceMinTicks = 200;   // 10 s
    public int silenceMaxTicks = 400;   // 20 s

    // --- Discovery triggers (waking up, returning to base) -------------------
    public double triggerChance = 0.45;

    public static DirectorConfig defaults() {
        return new DirectorConfig();
    }

    public double dailyBudget(long day) {
        return Math.min(maxBudget, baseBudget + budgetPerDay * (day - 1));
    }
}
