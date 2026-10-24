package com.hollowshift.director;

/**
 * What the Director wants the game to do right now.
 *
 * FIRE           run the event's effect and apply event.tensionDelta to the player.
 * BEGIN_SILENCE  fade out ambient sound and music for silenceTicks. The event itself
 *                will come back as a FIRE decision once the silence is over.
 */
public record Decision(Kind kind, EventDef event, int silenceTicks) {

    public enum Kind { FIRE, BEGIN_SILENCE }

    public static Decision fire(EventDef e) {
        return new Decision(Kind.FIRE, e, 0);
    }

    public static Decision silence(EventDef e, int ticks) {
        return new Decision(Kind.BEGIN_SILENCE, e, ticks);
    }
}
