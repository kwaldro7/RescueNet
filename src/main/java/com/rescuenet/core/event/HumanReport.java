package com.rescuenet.core.event;

/**
 * A manual report submitted by a citizen via mobile app.
 * Subjective and potentially imprecise; severity ranges 1-10.
 */
public class HumanReport extends RawEvent {

    private final int severity;
    private final String description;

    public HumanReport(Location location, long timestampMinutes,
                       IncidentCategory category, int severity, String description) {
        super(location, timestampMinutes, category);
        if (severity < 1 || severity > 10) {
            throw new IllegalArgumentException(
                    "Severity must be between 1 and 10, got: " + severity);
        }
        this.severity = severity;
        this.description = description;
    }

    public int getSeverity() { return severity; }
    public String getDescription() { return description; }

    /**
     * Score formula: severity maps linearly to 0-30 points.
     * Human reports are weighted lower than sensors because they are subjective.
     */
    @Override
    public int getRawScore() {
        return (int) Math.round(severity / 10.0 * 30);
    }
}
