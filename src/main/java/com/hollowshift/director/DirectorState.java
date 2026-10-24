package com.hollowshift.director;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Everything the Director remembers about one player. Save it with the player
 * (encode() to a single NBT string) so pacing survives logging out.
 */
public final class DirectorState {

    public enum Phase { BUILD_UP, RELAX }

    public double budget;
    public long budgetDay = -1;

    public double intensity;
    public Phase phase = Phase.BUILD_UP;
    public long relaxUntilTick;

    public long lastUpdateTick = -1;
    public long lastEventTick = Long.MIN_VALUE / 4;
    public final Map<Tier, Long> lastTierTick = new EnumMap<>(Tier.class);
    public final Map<String, Double> lastFiredDayClock = new HashMap<>();
    public final Map<String, Integer> fireCount = new HashMap<>();
    public final Deque<String> history = new ArrayDeque<>();

    /** An event waiting for its silence to finish. */
    public String pendingId;
    public long pendingFireTick;

    /** True while a silence is playing out. The adapter should not let the player sleep through it. */
    public boolean isPendingSilence() {
        return pendingId != null;
    }

    // ------------------------------------------------------------------------
    // Persistence: one line per field, "key=value". Store the result in NBT as a string.
    // ------------------------------------------------------------------------

    public String encode() {
        StringBuilder sb = new StringBuilder();
        line(sb, "budget", budget);
        line(sb, "budgetDay", budgetDay);
        line(sb, "intensity", intensity);
        line(sb, "phase", phase.name());
        line(sb, "relaxUntilTick", relaxUntilTick);
        line(sb, "lastUpdateTick", lastUpdateTick);
        line(sb, "lastEventTick", lastEventTick);
        line(sb, "lastTierTick", joinMap(lastTierTick));
        line(sb, "lastFired", joinMap(lastFiredDayClock));
        line(sb, "fireCount", joinMap(fireCount));
        line(sb, "history", String.join(",", history));
        if (pendingId != null) {
            line(sb, "pendingId", pendingId);
            line(sb, "pendingFireTick", pendingFireTick);
        }
        return sb.toString();
    }

    public static DirectorState decode(String s) {
        DirectorState st = new DirectorState();
        if (s == null || s.isBlank()) return st;
        for (String raw : s.split("\n")) {
            int eq = raw.indexOf('=');
            if (eq < 0) continue;
            String k = raw.substring(0, eq), v = raw.substring(eq + 1);
            switch (k) {
                case "budget" -> st.budget = Double.parseDouble(v);
                case "budgetDay" -> st.budgetDay = Long.parseLong(v);
                case "intensity" -> st.intensity = Double.parseDouble(v);
                case "phase" -> st.phase = Phase.valueOf(v);
                case "relaxUntilTick" -> st.relaxUntilTick = Long.parseLong(v);
                case "lastUpdateTick" -> st.lastUpdateTick = Long.parseLong(v);
                case "lastEventTick" -> st.lastEventTick = Long.parseLong(v);
                case "lastTierTick" -> forEachPair(v, (a, b) -> st.lastTierTick.put(Tier.valueOf(a), Long.parseLong(b)));
                case "lastFired" -> forEachPair(v, (a, b) -> st.lastFiredDayClock.put(a, Double.parseDouble(b)));
                case "fireCount" -> forEachPair(v, (a, b) -> st.fireCount.put(a, Integer.parseInt(b)));
                case "history" -> { for (String h : v.split(",")) if (!h.isEmpty()) st.history.addLast(h); }
                case "pendingId" -> st.pendingId = v;
                case "pendingFireTick" -> st.pendingFireTick = Long.parseLong(v);
                default -> { /* unknown keys from newer versions are ignored */ }
            }
        }
        return st;
    }

    private static void line(StringBuilder sb, String k, Object v) {
        sb.append(k).append('=').append(v).append('\n');
    }

    private static String joinMap(Map<?, ?> m) {
        StringBuilder sb = new StringBuilder();
        m.forEach((k, v) -> {
            if (sb.length() > 0) sb.append(',');
            sb.append(k).append(':').append(v);
        });
        return sb.toString();
    }

    private interface PairFn { void accept(String a, String b); }

    private static void forEachPair(String v, PairFn fn) {
        if (v.isEmpty()) return;
        for (String p : v.split(",")) {
            int c = p.lastIndexOf(':');
            if (c > 0) fn.accept(p.substring(0, c), p.substring(c + 1));
        }
    }
}
