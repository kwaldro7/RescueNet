package com.rescuenet.core.event;

/**
 * An event produced by the city's video analytics system.
 * Carries a label (e.g. "fire", "intrusion") and a confidence score 0.0-1.0.
 */
public class VideoAnalyticsEvent extends RawEvent {

    public static final double LOW_CONFIDENCE_THRESHOLD  = 0.4;
    public static final double HIGH_CONFIDENCE_THRESHOLD = 0.8;

    private final String label;
    private final double confidence;

    public VideoAnalyticsEvent(Location location, long timestampMinutes,
                               String label, double confidence) {
        super(location, timestampMinutes, labelToCategory(label));
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException(
                    "Confidence must be in [0.0, 1.0], got: " + confidence);
        }
        this.label = label;
        this.confidence = confidence;
    }

    private static IncidentCategory labelToCategory(String label) {
        if (label == null) {
            return IncidentCategory.UNKNOWN;
        }
        switch (label.toLowerCase()) {
            case "fire":
                return IncidentCategory.FIRE;
            case "intrusion":
                return IncidentCategory.INTRUSION;
            case "crowd anomaly":
            case "crowd_anomaly":
                return IncidentCategory.CROWD_ANOMALY;
            default:
                return IncidentCategory.UNKNOWN;
        }
    }

    public String getLabel() { return label; }
    public double getConfidence() { return confidence; }

    /** Score = 25 * confidence. High confidence (>= 0.8) gives full 25 pts. */
    @Override
    public int getRawScore() {
        return (int) Math.round(25 * confidence);
    }
}
