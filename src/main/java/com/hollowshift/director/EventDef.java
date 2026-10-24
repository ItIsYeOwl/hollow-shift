package com.hollowshift.director;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Data describing one scare. The Director decides WHEN; the game adapter decides HOW
 * (it maps {@link #id} to code that actually extinguishes a torch, plays a sound, etc).
 */
public final class EventDef {
    public final String id;
    public final Tier tier;
    public final Trigger trigger;
    /** Budget points spent when fired. */
    public final int cost;
    /** Earliest in-game day this can happen. */
    public final int minDay;
    /** Player tension required. */
    public final int minTension;
    /** Minimum in-game days between two firings of this same event. */
    public final double cooldownDays;
    /** Max firings per world; 0 = unlimited. Keeps special moments special. */
    public final int maxFires;
    /** Relative pick weight among eligible events. */
    public final double weight;
    /** How much this adds to the Director's pacing intensity. */
    public final int intensity;
    /** Suggested change to the player's tension; the adapter applies it. */
    public final int tensionDelta;
    /** If true, the Director first orders a silence, then fires this a few seconds later. */
    public final boolean needsSilence;
    public final Set<Condition> requires;

    private EventDef(Builder b) {
        this.id = b.id;
        this.tier = b.tier;
        this.trigger = b.trigger;
        this.cost = b.cost;
        this.minDay = b.minDay;
        this.minTension = b.minTension;
        this.cooldownDays = b.cooldownDays;
        this.maxFires = b.maxFires;
        this.weight = b.weight;
        this.intensity = b.intensity;
        this.tensionDelta = b.tensionDelta;
        this.needsSilence = b.needsSilence;
        this.requires = Collections.unmodifiableSet(EnumSet.copyOf(b.requires));
    }

    public boolean conditionsHold(DirectorContext ctx) {
        for (Condition c : requires) {
            if (!c.test(ctx)) return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return id;
    }

    public static Builder of(String id, Tier tier) {
        return new Builder(id, tier);
    }

    public static final class Builder {
        private final String id;
        private final Tier tier;
        private Trigger trigger = Trigger.TICK;
        private int cost, minDay, minTension, maxFires, intensity, tensionDelta;
        private double cooldownDays, weight = 1.0;
        private boolean needsSilence;
        private final EnumSet<Condition> requires = EnumSet.noneOf(Condition.class);

        private Builder(String id, Tier tier) {
            this.id = id;
            this.tier = tier;
            // Tier defaults; override per event where it matters.
            switch (tier) {
                case AMBIENT -> { cost = 1; minDay = 1; minTension = 0; cooldownDays = 0.5; intensity = 10; tensionDelta = 3; }
                case TAMPERING -> { cost = 3; minDay = 4; minTension = 30; cooldownDays = 2; intensity = 20; tensionDelta = 6; }
                case META -> { cost = 5; minDay = 6; minTension = 50; cooldownDays = 3; intensity = 30; tensionDelta = 8; }
                // intensity 75 > peakIntensity, so every confrontation is followed by a forced calm
                case CONFRONTATION -> { cost = 10; minDay = 10; minTension = 65; cooldownDays = 3; intensity = 75; tensionDelta = 15; needsSilence = true; }
            }
        }

        public Builder trigger(Trigger t) { this.trigger = t; return this; }
        public Builder cost(int v) { this.cost = v; return this; }
        public Builder minDay(int v) { this.minDay = v; return this; }
        public Builder minTension(int v) { this.minTension = v; return this; }
        public Builder cooldownDays(double v) { this.cooldownDays = v; return this; }
        public Builder maxFires(int v) { this.maxFires = v; return this; }
        public Builder weight(double v) { this.weight = v; return this; }
        public Builder intensity(int v) { this.intensity = v; return this; }
        public Builder tensionDelta(int v) { this.tensionDelta = v; return this; }
        public Builder silence(boolean v) { this.needsSilence = v; return this; }
        public Builder requires(Condition... cs) { for (Condition c : cs) requires.add(c); return this; }

        public EventDef build() {
            return new EventDef(this);
        }
    }
}
