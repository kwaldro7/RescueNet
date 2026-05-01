package com.rescuenet.core.event;

/**
 * An event produced by an environmental sensor.
 * Supports: smoke (ppm), temperature (C), CO2 (ppm), motion.
 * Reliability degrades when battery is critically low.
 */
@SuppressWarnings("PMD.DataClass")
// Domain event classes intentionally carry data — this is not an
// anti-pattern in the context of an event-driven domain model.
public class SensorEvent extends RawEvent {

    public enum SensorType { SMOKE, TEMPERATURE, CO2, MOTION }
    public enum SensorStatus { OK, ALERT }

    /** Battery threshold below which the event is unreliable (Scenario G). */
    public static final double LOW_BATTERY_THRESHOLD = 5.0;

    private static final double SMOKE_HIGH_PPM    = 700.0;
    private static final double SMOKE_MEDIUM_PPM  = 300.0;
    private static final double TEMP_HIGH_CELSIUS = 60.0;
    private static final double TEMP_MED_CELSIUS  = 40.0;
    private static final double CO2_HIGH_PPM      = 2000.0;
    private static final double CO2_MED_PPM       = 1000.0;

    private final String sensorId;
    private final SensorType sensorType;
    private final double measuredValue;
    private final SensorStatus status;
    private final double batteryLevel;

    public SensorEvent(String sensorId, Location location, long timestampMinutes,
                       SensorType sensorType, double measuredValue,
                       SensorStatus status, double batteryLevel) {
        super(location, timestampMinutes, deriveCategory(sensorType));
        this.sensorId = sensorId;
        this.sensorType = sensorType;
        this.measuredValue = measuredValue;
        this.status = status;
        this.batteryLevel = batteryLevel;
    }

    private static IncidentCategory deriveCategory(SensorType type) {
        switch (type) {
            case SMOKE:
            case TEMPERATURE:
                return IncidentCategory.FIRE;
            case CO2:
                return IncidentCategory.ENVIRONMENTAL_HAZARD;
            case MOTION:
                return IncidentCategory.INTRUSION;
            default:
                return IncidentCategory.UNKNOWN;
        }
    }

    public String getSensorId() { return sensorId; }
    public SensorType getSensorType() { return sensorType; }
    public double getMeasuredValue() { return measuredValue; }
    public SensorStatus getStatus() { return status; }
    public double getBatteryLevel() { return batteryLevel; }

    public boolean hasCriticallyLowBattery() {
        return batteryLevel < LOW_BATTERY_THRESHOLD;
    }

    @Override
    public int getRawScore() {
        if (status == SensorStatus.OK) {
            return 0;
        }
        switch (sensorType) {
            case SMOKE:       return scoreBanded(measuredValue, SMOKE_MEDIUM_PPM, SMOKE_HIGH_PPM);
            case TEMPERATURE: return scoreBanded(measuredValue, TEMP_MED_CELSIUS, TEMP_HIGH_CELSIUS);
            case CO2:         return scoreBanded(measuredValue, CO2_MED_PPM, CO2_HIGH_PPM);
            case MOTION:      return 20;
            default:          return 0;
        }
    }

    /** Returns 20 for medium threshold, 30 for high threshold. */
    private int scoreBanded(double value, double medThreshold, double highThreshold) {
        if (value >= highThreshold) return 30;
        if (value >= medThreshold)  return 20;
        return 10;
    }
}
