package com.hollowshift.director;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * Plays a fake player through N in-game days, with no Minecraft required.
 *
 * Use it to tune pacing: change DirectorConfig or DefaultEvents, re-run, and read
 * the report. Run:  java DirectorSimulation [days] [seeds]
 *
 * The fake player wanders between their base, the surface and mines, sleeps on
 * roughly half the nights, and has a simple tension model (dark and underground
 * raise it, base and daylight lower it, and the world gets worse each day).
 */
public final class DirectorSimulation {

    enum Activity { IN_BASE, SURFACE, MINING }

    /** One thing the Director did. selection=true means this is when it was chosen (and paid for). */
    record Rec(long day, int tod, long gameTick, double dayClock, EventDef ev,
               Decision.Kind kind, boolean selection, double tension, Trigger trigger) {}

    static List<Rec> run(long seed, int days, DirectorConfig cfg) {
        Random world = new Random(seed);
        Director director = new Director(cfg, DefaultEvents.all(), new Random(seed * 31 + 7));
        DirectorState state = new DirectorState();
        List<Rec> out = new ArrayList<>();

        long gameTick = 0, dayTime = 0;
        double tension = 5;
        Activity act = Activity.IN_BASE;
        boolean lit = true;
        long nextActChange = 0;
        long sleepDecidedDay = 0;
        int step = cfg.evalIntervalTicks;

        while (dayTime < days * 24000L) {
            long day = dayTime / 24000 + 1;
            int tod = (int) (dayTime % 24000);
            boolean night = tod >= 13000 && tod < 23000;

            // Bedtime: on about half the nights the player sleeps and skips to morning.
            if (tod >= 12600 && sleepDecidedDay != day) {
                sleepDecidedDay = day;
                if (world.nextDouble() < 0.5 && !state.isPendingSilence()) {
                    dayTime = day * 24000L;
                    if (dayTime >= days * 24000L) break;
                    gameTick += 100;
                    act = Activity.IN_BASE;
                    lit = true;
                    tension = Math.max(0, tension - 15); // a night's rest helps
                    DirectorContext wake = ctx(gameTick, dayTime, tension, act, lit);
                    tension = handle(director.onTrigger(Trigger.ON_WAKE, wake, state), wake, tension,
                            true, Trigger.ON_WAKE, out);
                    nextActChange = gameTick + 1200;
                    continue;
                }
            }

            // Every in-game minute, the player may change what they're doing.
            if (gameTick >= nextActChange) {
                Activity prev = act;
                double r = world.nextDouble();
                act = night
                        ? (r < 0.5 ? Activity.IN_BASE : r < 0.8 ? Activity.MINING : Activity.SURFACE)
                        : (r < 0.2 ? Activity.IN_BASE : r < 0.55 ? Activity.MINING : Activity.SURFACE);
                lit = switch (act) {
                    case IN_BASE -> true;
                    case SURFACE -> !night || world.nextDouble() < 0.2;
                    case MINING -> world.nextDouble() < 0.4;
                };
                nextActChange = gameTick + 1200;
                if (prev != Activity.IN_BASE && act == Activity.IN_BASE) {
                    DirectorContext c = ctx(gameTick, dayTime, tension, act, lit);
                    tension = handle(director.onTrigger(Trigger.ON_RETURN_TO_BASE, c, state), c, tension,
                            true, Trigger.ON_RETURN_TO_BASE, out);
                }
            }

            // Tension drifts toward a target set by the situation, and the world worsens daily.
            double target = switch (act) {
                case IN_BASE -> 8;
                case SURFACE -> night ? (lit ? 35 : 52) : 15;
                case MINING -> lit ? 40 : 65;
            };
            double escalation = Math.min(25, 1.6 * (day - 1));
            target += act == Activity.IN_BASE ? escalation * 0.4 : escalation;
            tension += (target - tension) * 0.03;
            tension = clamp(tension);

            DirectorContext c = ctx(gameTick, dayTime, tension, act, lit);
            boolean wasPending = state.isPendingSilence();
            Optional<Decision> d = director.tick(c, state);
            tension = handle(d, c, tension, !wasPending, Trigger.TICK, out);

            gameTick += step;
            dayTime += step;
        }
        return out;
    }

    private static DirectorContext ctx(long gameTick, long dayTime, double tension, Activity act, boolean lit) {
        return new DirectorContext(gameTick, dayTime / 24000 + 1, (int) (dayTime % 24000), tension,
                act == Activity.MINING, act == Activity.IN_BASE, lit);
    }

    private static double handle(Optional<Decision> d, DirectorContext c, double tension, boolean selection,
                                 Trigger trig, List<Rec> out) {
        if (d.isEmpty()) return tension;
        Decision dec = d.get();
        out.add(new Rec(c.day(), c.timeOfDay(), c.gameTick(), c.dayClock(), dec.event(), dec.kind(),
                selection, tension, trig));
        if (dec.kind() == Decision.Kind.FIRE) tension = clamp(tension + dec.event().tensionDelta);
        return tension;
    }

    private static double clamp(double t) {
        return Math.max(0, Math.min(100, t));
    }

    /** Minecraft clock: tick 0 is 06:00. */
    static String clock(int tod) {
        int minutes = (int) ((tod / 1000.0 + 6) * 60) % (24 * 60);
        return String.format("%02d:%02d", minutes / 60, minutes % 60);
    }

    // ------------------------------------------------------------------------

    public static void main(String[] args) {
        int days = args.length > 0 ? Integer.parseInt(args[0]) : 20;
        int seeds = args.length > 1 ? Integer.parseInt(args[1]) : 500;
        DirectorConfig cfg = DirectorConfig.defaults();

        // ---- 1. One world, told as a story ----
        System.out.println("=== Sample world (seed 1), " + days + " days ===");
        List<Rec> sample = run(1, days, cfg);
        for (long day = 1; day <= days; day++) {
            StringBuilder line = new StringBuilder();
            int spent = 0;
            for (Rec r : sample) {
                if (r.day() != day) continue;
                if (r.selection()) spent += r.ev().cost;
                String tag = r.kind() == Decision.Kind.BEGIN_SILENCE ? "[silence] "
                        : r.trigger() != Trigger.TICK ? "[" + r.trigger().name().toLowerCase() + "] " : "";
                if (r.kind() == Decision.Kind.FIRE && !r.selection()) tag = "  -> ";
                line.append(String.format("%n    %s %s%s (%s, T%.0f)", clock(r.tod()), tag, r.ev().id,
                        r.ev().tier.name().toLowerCase(), r.tension()));
            }
            System.out.printf("Day %2d  budget %4.1f  spent %2d%s%n", day, cfg.dailyBudget(day), spent,
                    line.length() == 0 ? "\n    (nothing)" : line);
        }

        // ---- 2. Many worlds: averages and invariant checks ----
        System.out.println("\n=== " + seeds + " worlds: average events per day, by tier ===");
        double[][] perDayTier = new double[days + 1][Tier.values().length];
        int[] confrontationByDay = new int[days + 1]; // worlds whose first confrontation is by day N
        List<String> failures = new ArrayList<>();
        List<Long> gapsAfterConfrontation = new ArrayList<>();
        int silences = 0, silencesDelivered = 0;
        Map<String, Integer> totalById = new HashMap<>();

        for (long seed = 1; seed <= seeds; seed++) {
            List<Rec> recs = run(seed, days, cfg);
            Map<Long, Integer> spentByDay = new HashMap<>();
            Map<String, Double> lastSel = new HashMap<>();
            long firstConf = Long.MAX_VALUE;
            Rec pendingSilence = null;

            for (int i = 0; i < recs.size(); i++) {
                Rec r = recs.get(i);
                EventDef ev = r.ev();

                if (r.selection()) {
                    perDayTier[(int) r.day()][ev.tier.ordinal()]++;
                    totalById.merge(ev.id, 1, Integer::sum);
                    spentByDay.merge(r.day(), ev.cost, Integer::sum);

                    if (r.day() < ev.minDay) failures.add(seed + ": " + ev.id + " before day " + ev.minDay);
                    if (r.tension() < ev.minTension) failures.add(seed + ": " + ev.id + " below tension gate");
                    Double prev = lastSel.put(ev.id, r.dayClock());
                    if (prev != null && r.dayClock() - prev < ev.cooldownDays - 1e-9)
                        failures.add(seed + ": " + ev.id + " broke cooldown");
                    if (r.day() <= 3 && ev.tier != Tier.AMBIENT)
                        failures.add(seed + ": non-ambient on day " + r.day());
                }

                if (r.kind() == Decision.Kind.BEGIN_SILENCE) {
                    silences++;
                    pendingSilence = r;
                }
                if (r.kind() == Decision.Kind.FIRE && !r.selection()) {
                    if (pendingSilence == null || pendingSilence.ev() != ev)
                        failures.add(seed + ": " + ev.id + " fired without its silence");
                    else {
                        long wait = r.gameTick() - pendingSilence.gameTick();
                        if (wait < cfg.silenceMinTicks) failures.add(seed + ": silence too short");
                    }
                    silencesDelivered++;
                    pendingSilence = null;
                }
                if (ev.tier == Tier.CONFRONTATION && ev.needsSilence && r.kind() == Decision.Kind.FIRE
                        && r.selection()) {
                    failures.add(seed + ": " + ev.id + " skipped its silence");
                }

                boolean confrontationLanded = ev.tier == Tier.CONFRONTATION && r.kind() == Decision.Kind.FIRE;
                if (confrontationLanded) {
                    firstConf = Math.min(firstConf, r.day());
                    for (int j = i + 1; j < recs.size(); j++) {
                        Rec n = recs.get(j);
                        if (n.selection() && n.trigger() == Trigger.TICK) {
                            gapsAfterConfrontation.add(n.gameTick() - r.gameTick());
                            break;
                        }
                    }
                }
            }
            for (var e : spentByDay.entrySet()) {
                if (e.getValue() > cfg.dailyBudget(e.getKey()) + 1e-9)
                    failures.add(seed + ": overspent on day " + e.getKey());
            }
            for (long dd = firstConf; dd <= days; dd++) confrontationByDay[(int) dd]++;
        }

        System.out.println("Day   ambient  tamper   meta   confront   total");
        for (int day = 1; day <= days; day++) {
            double total = 0;
            StringBuilder sb = new StringBuilder(String.format("%3d ", day));
            for (Tier t : Tier.values()) {
                double v = perDayTier[day][t.ordinal()] / seeds;
                total += v;
                sb.append(String.format("  %6.2f ", v));
            }
            sb.append(String.format("  %6.2f", total));
            System.out.println(sb);
        }

        System.out.println("\nFirst confrontation has happened by:");
        for (int day : new int[]{10, 11, 12, 14, 16, 20}) {
            if (day <= days)
                System.out.printf("  day %2d: %5.1f%% of worlds%n", day, 100.0 * confrontationByDay[day] / seeds);
        }

        gapsAfterConfrontation.sort(Long::compare);
        if (!gapsAfterConfrontation.isEmpty()) {
            long min = gapsAfterConfrontation.get(0);
            long med = gapsAfterConfrontation.get(gapsAfterConfrontation.size() / 2);
            System.out.printf("%nCalm after a confrontation (until next periodic event): min %.1f min, median %.1f min%n",
                    min / 1200.0, med / 1200.0);
        }
        System.out.printf("Silences: %d started, %d led to an event, %d ended in nothing (the player moved, so the silence was the scare)%n",
                silences, silencesDelivered, silences - silencesDelivered);

        System.out.println("\nMost / least common events (per world, over " + days + " days):");
        totalById.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .forEach(e -> System.out.printf("  %-22s %5.2f%n", e.getKey(), e.getValue() / (double) seeds));
        List<String> never = new ArrayList<>();
        for (EventDef e : DefaultEvents.all()) if (!totalById.containsKey(e.id)) never.add(e.id);
        if (!never.isEmpty()) System.out.println("  NEVER FIRED: " + never);

        // ---- 3. Persistence round trip ----
        DirectorState st = new DirectorState();
        Director d = new Director(cfg, DefaultEvents.all(), new Random(3));
        for (int i = 0; i < 3000; i++) {
            d.tick(new DirectorContext(i * 40L, 1 + i * 40L / 24000, (int) ((i * 40L) % 24000), 80, true, false, false), st);
        }
        String enc = st.encode();
        if (!DirectorState.decode(enc).encode().equals(enc)) failures.add("persistence round trip mismatch");

        System.out.println("\n=== Invariants ===");
        if (failures.isEmpty()) {
            System.out.println("PASS  budgets respected, tier/day/tension gates held, cooldowns held,");
            System.out.println("      every silenced event had its silence, days 1-3 ambient only, save/load round trip");
        } else {
            System.out.println("FAIL  " + failures.size() + " problems, first few:");
            failures.stream().limit(15).forEach(f -> System.out.println("  " + f));
        }
    }
}
