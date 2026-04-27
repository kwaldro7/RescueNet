package com.rescuenet.core.event;

import java.util.UUID;

/**
 * Base class for all raw events entering the system.
 * Each subtype carries source-specific data; the common contract
 * exposes location, timestamp, category, and reliability.
 */
public abstract class RawEvent {

    private final String id;
    private final Location location;
    private final long timestampMinutes;
    private final IncidentCategory category;
    private EventReliability reliability;

    protected RawEvent(Location location, long timestampMinutes, IncidentCategory category) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.location = location;
        this.timestampMinutes = timestampMinutes;
        this.category = category;
        this.reliability = EventReliability.RELIABLE;
    }

    public String getId() { return id; }
    public Location getLocation() { return location; }
    public long getTimestampMinutes() { return timestampMinutes; }
    public IncidentCategory getCategory() { return category; }
    public EventReliability getReliability() { return reliability; }

    public void markUnreliable() { this.reliability = EventReliability.UNRELIABLE; }
    public boolean isReliable() { return reliability == EventReliability.RELIABLE; }

    /**
     * Returns the raw priority contribution score of this event (0-100).
     * Used by PriorityStrategy implementations.
     */
    public abstract int getRawScore();

    @Override
    public String toString() {
        return String.format("[%s | %s | t=%d | %s | score=%d]",
                getClass().getSimpleName(), category, timestampMinutes,
                reliability, getRawScore());
    }
}
