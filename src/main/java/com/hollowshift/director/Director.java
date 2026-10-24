package com.hollowshift.director;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * The Hollow Shift's horror director.
 *
 * Decides WHEN a scare happens and WHICH one, so the mod stays frightening on day 20
 * instead of only day 2. It works like a heartbeat:
 *
 *   BUILD_UP  small events spend a daily budget; each one raises "intensity".
 *   RELAX     once intensity peaks, everything stops for a few minutes.
 *             A relaxed player startles harder than one already on guard.
 *
 * Intensity (the Director's pacing memory) is NOT the same as the player's
 * TensionLevel. Tension is how scared the player should feel and drives the shaders.
 * Intensity is how recently the Director scared them and stops it from overdoing it.
 *
 * Pure Java, no Minecraft imports: call tick() every config.evalIntervalTicks from a
 * server tick hook, and onTrigger() when the player wakes up or returns to base.
 */
public final class Director {

    private final DirectorConfig cfg;
    private final List<EventDef> events;
    private final Random rng;

    public Director(DirectorConfig cfg, List<EventDef> events, Random rng) {
        this.cfg = cfg;
        this.events = List.copyOf(events);
        this.rng = rng;
    }

    public DirectorConfig config() {
        return cfg;
    }

    /** Periodic evaluation. Call every cfg.evalIntervalTicks for each online player. */
    public Optional<Decision> tick(DirectorContext ctx, DirectorState s) {
        update(ctx, s);

        // A silence is in progress: either wait, or deliver what it was building to.
        if (s.pendingId != null) {
            if (ctx.gameTick() < s.pendingFireTick) return Optional.empty();
            EventDef ev = byId(s.pendingId);
            s.pendingId = null;
            // If the situation changed (player ran into a lit base), the silence was the scare.
            if (ev == null || !ev.conditionsHold(ctx)) return Optional.empty();
            // The calm after a big moment is measured from when it lands, not when the silence began.
            if (s.phase == DirectorState.Phase.RELAX) {
                s.relaxUntilTick = Math.max(s.relaxUntilTick,
                        ctx.gameTick() + randBetween(cfg.relaxMinTicks, cfg.relaxMaxTicks));
            }
            return Optional.of(Decision.fire(ev)); // already paid for when the silence began
        }

        if (s.phase == DirectorState.Phase.RELAX) return Optional.empty();
        if (ctx.gameTick() - s.lastEventTick < cfg.minGapTicks) return Optional.empty();
        if (rng.nextDouble() >= fireChance(ctx, s)) return Optional.empty();

        return choose(Trigger.TICK, ctx, s);
    }

    /** A discovery moment: the player just woke up, or just walked back into their base. */
    public Optional<Decision> onTrigger(Trigger trigger, DirectorContext ctx, DirectorState s) {
        if (trigger == Trigger.TICK) throw new IllegalArgumentException("use tick()");
        update(ctx, s);
        if (s.pendingId != null) return Optional.empty();
        if (rng.nextDouble() >= cfg.triggerChance * tensionFactor(ctx)) return Optional.empty();
        // Discoveries are allowed during RELAX: finding a sorted chest is quiet dread, not a jolt.
        return choose(trigger, ctx, s);
    }

    // ------------------------------------------------------------------------

    private void update(DirectorContext ctx, DirectorState s) {
        if (ctx.day() != s.budgetDay) {
            s.budgetDay = ctx.day();
            s.budget = cfg.dailyBudget(ctx.day()); // unspent budget does not carry over
        }

        if (s.lastUpdateTick >= 0) {
            long dt = ctx.gameTick() - s.lastUpdateTick;
            if (dt > 0) s.intensity *= Math.pow(0.5, dt / (cfg.intensityHalfLifeSeconds * 20.0));
        }
        s.lastUpdateTick = ctx.gameTick();

        if (s.phase == DirectorState.Phase.BUILD_UP && s.intensity >= cfg.peakIntensity) {
            s.phase = DirectorState.Phase.RELAX;
            s.relaxUntilTick = ctx.gameTick() + randBetween(cfg.relaxMinTicks, cfg.relaxMaxTicks);
        } else if (s.phase == DirectorState.Phase.RELAX
                && ctx.gameTick() >= s.relaxUntilTick
                && s.intensity <= cfg.resumeIntensity) {
            s.phase = DirectorState.Phase.BUILD_UP;
        }
    }

    double fireChance(DirectorContext ctx, DirectorState s) {
        double p = cfg.baseChancePerEval * tensionFactor(ctx);
        if (ctx.isNight()) p *= cfg.nightMultiplier;
        if (ctx.underground()) p *= cfg.undergroundMultiplier;
        if (ctx.nearLight()) p *= cfg.litMultiplier;
        double since = Math.min(ctx.gameTick() - s.lastEventTick, cfg.droughtCapTicks);
        p *= 1.0 + since / cfg.droughtRampTicks;
        return Math.min(p, cfg.maxChancePerEval);
    }

    private static double tensionFactor(DirectorContext ctx) {
        return 0.5 + ctx.tension() / 100.0; // 0.5x when calm, 1.5x when terrified
    }

    private Optional<Decision> choose(Trigger trigger, DirectorContext ctx, DirectorState s) {
        List<EventDef> candidates = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        String lastId = s.history.peekLast();
        EventDef last = lastId == null ? null : byId(lastId);

        for (EventDef ev : events) {
            if (ev.trigger != trigger || !eligible(ev, ctx, s)) continue;
            double w = ev.weight * tierBias(ev.tier, ctx.tension()) * overdueBoost(ev, ctx, s);
            if (s.history.contains(ev.id)) w *= cfg.recentRepeatPenalty;
            if (last != null && last.tier == ev.tier) w *= cfg.sameTierPenalty;
            if (w <= 0) continue;
            candidates.add(ev);
            weights.add(w);
        }
        if (candidates.isEmpty()) return Optional.empty();

        EventDef pick = weightedPick(candidates, weights);
        commit(pick, ctx, s);

        if (pick.needsSilence) {
            int dur = (int) randBetween(cfg.silenceMinTicks, cfg.silenceMaxTicks);
            s.pendingId = pick.id;
            s.pendingFireTick = ctx.gameTick() + dur;
            return Optional.of(Decision.silence(pick, dur));
        }
        return Optional.of(Decision.fire(pick));
    }

    boolean eligible(EventDef ev, DirectorContext ctx, DirectorState s) {
        if (ctx.day() < ev.minDay) return false;
        if (ctx.tension() < ev.minTension) return false;
        if (ev.cost > s.budget) return false;
        if (ev.maxFires > 0 && s.fireCount.getOrDefault(ev.id, 0) >= ev.maxFires) return false;

        Double lastFired = s.lastFiredDayClock.get(ev.id);
        if (lastFired != null && ctx.dayClock() - lastFired < ev.cooldownDays) return false;

        Long lastTier = s.lastTierTick.get(ev.tier);
        if (lastTier != null && ctx.gameTick() - lastTier < cfg.tierGapTicks[ev.tier.ordinal()]) return false;

        return ev.conditionsHold(ctx);
    }

    /** Higher tension shifts the odds toward higher tiers; low tension keeps things subtle. */
    private static double tierBias(Tier tier, double tension) {
        double b = 1.0 + tier.ordinal() * ((tension - 50.0) / 50.0) * 0.5;
        return Math.max(0.2, b);
    }

    /**
     * Bigger tiers get likelier the longer it's been since one happened, so a world
     * never goes a week without anything real. Ambient events don't need this.
     */
    private double overdueBoost(EventDef ev, DirectorContext ctx, DirectorState s) {
        if (ev.tier == Tier.AMBIENT) return 1.0;
        Long last = s.lastTierTick.get(ev.tier);
        double daysSince = last == null
                ? ctx.day() - ev.minDay + 1
                : (ctx.gameTick() - last) / 24000.0;
        return 1.0 + Math.min(cfg.maxOverdueBoost, daysSince / cfg.overdueDays);
    }

    private void commit(EventDef ev, DirectorContext ctx, DirectorState s) {
        s.budget -= ev.cost;
        s.fireCount.merge(ev.id, 1, Integer::sum);
        s.lastFiredDayClock.put(ev.id, ctx.dayClock());
        s.lastTierTick.put(ev.tier, ctx.gameTick());
        s.lastEventTick = ctx.gameTick();
        s.history.addLast(ev.id);
        while (s.history.size() > cfg.historySize) s.history.removeFirst();
        s.intensity += ev.intensity;
    }

    private EventDef weightedPick(List<EventDef> evs, List<Double> ws) {
        double total = 0;
        for (double w : ws) total += w;
        double r = rng.nextDouble() * total;
        for (int i = 0; i < evs.size(); i++) {
            r -= ws.get(i);
            if (r <= 0) return evs.get(i);
        }
        return evs.get(evs.size() - 1);
    }

    private long randBetween(long lo, long hi) {
        return lo + (long) (rng.nextDouble() * (hi - lo + 1));
    }

    public EventDef byId(String id) {
        for (EventDef e : events) if (e.id.equals(id)) return e;
        return null;
    }

    public List<EventDef> events() {
        return events;
    }
}
