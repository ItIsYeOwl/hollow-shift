package com.hollowshift.director;

/**
 * When an event is allowed to be considered.
 *
 * TICK events are rolled for periodically. The other triggers are moments of
 * discovery: tampering "happened while you were away" and is only revealed when
 * the player wakes up or walks back into their base.
 */
public enum Trigger {
    TICK,
    ON_WAKE,
    ON_RETURN_TO_BASE
}
