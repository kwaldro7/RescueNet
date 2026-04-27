package com.rescuenet.core.event;

/**
 * Reliability classification applied to a raw event by the EventFactory
 * before it reaches any domain logic.
 */
public enum EventReliability {
    RELIABLE,
    UNRELIABLE   // e.g. sensor battery < 5%, video confidence < 0.4
}
