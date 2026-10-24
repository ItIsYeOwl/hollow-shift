package com.hollowshift.director;

/**
 * A snapshot of one player's situation, built by the game adapter each evaluation.
 * The Director never touches Minecraft classes directly, which keeps it testable.
 *
 * @param gameTick   total elapsed game ticks (world.getTime()); does NOT jump when sleeping
 * @param day        1-based in-game day number (timeOfDay / 24000 + 1)
 * @param timeOfDay  0..23999 within the current day (6000 = noon, 18000 = midnight)
 * @param tension    the player's TensionLevel, 0..100
 * @param underground player is below the surface (e.g. no sky access and y below sea level)
 * @param inBase     player is inside their base area
 * @param nearLight  block light at the player is high (torches, fire nearby)
 */
public record DirectorContext(
        long gameTick,
        long day,
        int timeOfDay,
        double tension,
        boolean underground,
        boolean inBase,
        boolean nearLight) {

    public boolean isNight() {
        return timeOfDay >= 13000 && timeOfDay < 23000;
    }

    /**
     * Continuous "day clock": 3.5 means halfway through day 4.
     * Used for day-scale cooldowns. It jumps forward when the player sleeps,
     * which is what we want (a skipped night still counts as time passing).
     */
    public double dayClock() {
        return (day - 1) + (timeOfDay % 24000) / 24000.0;
    }
}
