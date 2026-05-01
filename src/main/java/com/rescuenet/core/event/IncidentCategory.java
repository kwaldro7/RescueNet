package com.rescuenet.core.event;

/**
 * High-level category of an emergency incident.
 * Used by CorrelationEngine to check type compatibility between events.
 */
public enum IncidentCategory {
    FIRE,
    MEDICAL,
    INTRUSION,
    ENVIRONMENTAL_HAZARD,
    CROWD_ANOMALY,
    UNKNOWN
}
