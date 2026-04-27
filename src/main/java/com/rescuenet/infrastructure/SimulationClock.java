package com.rescuenet.infrastructure;

/**
 * A deterministic tick-based simulation clock.
 * All domain objects use this instead of System.currentTimeMillis(),
 * making the system 100% reproducible and testable without threads.
 */
public class SimulationClock {

    private long currentMinutes = 0;

    public long now() { return currentMinutes; }

    public void advance(long minutes) {
        if (minutes < 0) {
            throw new IllegalArgumentException("Cannot advance time backwards");
        }
        currentMinutes += minutes;
    }

    public void reset() { currentMinutes = 0; }

    @Override
    public String toString() {
        return String.format("SimulationClock[t=%d min]", currentMinutes);
    }
}
