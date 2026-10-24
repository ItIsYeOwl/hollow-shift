package com.hollowshift.director;

/** Situational requirements an event can declare. All declared conditions must hold. */
public enum Condition {
    NIGHT, DAY, SURFACE, UNDERGROUND, IN_BASE, AWAY_FROM_BASE, DARK, LIT;

    public boolean test(DirectorContext c) {
        return switch (this) {
            case NIGHT -> c.isNight();
            case DAY -> !c.isNight();
            case SURFACE -> !c.underground();
            case UNDERGROUND -> c.underground();
            case IN_BASE -> c.inBase();
            case AWAY_FROM_BASE -> !c.inBase();
            case DARK -> !c.nearLight();
            case LIT -> c.nearLight();
        };
    }
}
