package com.rescuenet.core.event;

/**
 * An emergency alert from a wearable device worn by a vulnerable citizen.
 * May include optional biometric data (heart rate).
 */
public class WearableAlert extends RawEvent {

    private final String deviceId;
    private final boolean manualTrigger;
    private final Integer heartRate;   // nullable - optional biometric

    public WearableAlert(String deviceId, Location location, long timestampMinutes,
                         boolean manualTrigger, Integer heartRate) {
        super(location, timestampMinutes, IncidentCategory.MEDICAL);
        this.deviceId = deviceId;
        this.manualTrigger = manualTrigger;
        this.heartRate = heartRate;
    }

    public String getDeviceId() { return deviceId; }
    public boolean isManualTrigger() { return manualTrigger; }
    public Integer getHeartRate() { return heartRate; }

    /** Manual trigger = 15 pts; biometric-only = 8 pts; abnormal HR adds 5. */
    @Override
    public int getRawScore() {
        int score = manualTrigger ? 15 : 8;
        if (heartRate != null && (heartRate > 150 || heartRate < 40)) {
            score += 5;
        }
        return score;
    }
}
