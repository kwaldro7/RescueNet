package com.rescuenet.core.event;

/**
 * Factory for creating RawEvent instances.
 *
 * Design Pattern: FACTORY
 * Centralizes event construction and applies reliability rules before
 * any event reaches domain logic:
 *   - SensorEvent with battery < 5%  -> marked UNRELIABLE  (Scenario G)
 *   - VideoAnalyticsEvent confidence < 0.4 -> marked UNRELIABLE
 *
 * This means IncidentManager only needs to check event.isReliable()
 * and never needs to know about battery levels or confidence thresholds.
 */
public class EventFactory {

    private EventFactory() {
        // Utility class - not instantiable
    }

    /** Creates a HumanReport. Always considered RELIABLE. */
    public static HumanReport humanReport(Location location, long timestampMinutes,
                                          IncidentCategory category, int severity,
                                          String description) {
        return new HumanReport(location, timestampMinutes, category, severity, description);
    }

    /**
     * Creates a SensorEvent and applies reliability rules.
     * Battery below LOW_BATTERY_THRESHOLD -> marked UNRELIABLE.
     */
    public static SensorEvent sensorEvent(String sensorId, Location location,
                                          long timestampMinutes,
                                          SensorEvent.SensorType sensorType,
                                          double measuredValue,
                                          SensorEvent.SensorStatus status,
                                          double batteryLevel) {
        SensorEvent event = new SensorEvent(
                sensorId, location, timestampMinutes,
                sensorType, measuredValue, status, batteryLevel);
        if (event.hasCriticallyLowBattery()) {
            event.markUnreliable();
        }
        return event;
    }

    /**
     * Creates a VideoAnalyticsEvent and applies reliability rules.
     * Confidence below LOW_CONFIDENCE_THRESHOLD -> marked UNRELIABLE.
     */
    public static VideoAnalyticsEvent videoEvent(Location location, long timestampMinutes,
                                                  String label, double confidence) {
        VideoAnalyticsEvent event = new VideoAnalyticsEvent(
                location, timestampMinutes, label, confidence);
        if (confidence < VideoAnalyticsEvent.LOW_CONFIDENCE_THRESHOLD) {
            event.markUnreliable();
        }
        return event;
    }

    /** Creates a WearableAlert. Always considered RELIABLE. */
    public static WearableAlert wearableAlert(String deviceId, Location location,
                                               long timestampMinutes,
                                               boolean manualTrigger, Integer heartRate) {
        return new WearableAlert(deviceId, location, timestampMinutes, manualTrigger, heartRate);
    }
}
