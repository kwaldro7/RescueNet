package com.rescuenet.core.priority;

/** Incident priority levels, ordered from lowest to highest. */
public enum PriorityLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    public boolean isHigherThan(PriorityLevel other) {
        return this.ordinal() > other.ordinal();
    }
}
