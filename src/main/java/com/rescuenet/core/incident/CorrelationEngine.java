package com.rescuenet.core.incident;

import com.rescuenet.core.event.IncidentCategory;
import com.rescuenet.core.event.RawEvent;

import java.util.List;

/**
 * Decides whether a new event should merge into an existing incident
 * or trigger the creation of a new one.
 *
 * Three criteria must ALL pass:
 *   1. Spatial proximity  - distance <= DISTANCE_THRESHOLD (200 grid units)
 *   2. Temporal proximity - time delta <= TIME_WINDOW_MINUTES (15 min)
 *   3. Category compatibility - same category (FIRE != INTRUSION)
 *
 * Uses lastUpdatedMinutes (not createdAtMinutes) so slow-developing
 * incidents still accept new corroborating events.
 */
public class CorrelationEngine {

    public static final double DISTANCE_THRESHOLD  = 200.0;
    public static final long   TIME_WINDOW_MINUTES = 15;

    /**
     * Returns the first open incident that matches the given event,
     * or null if none match.
     */
    public Incident findMatch(RawEvent event, List<Incident> openIncidents) {
        for (Incident incident : openIncidents) {
            if (incident.getStatus() == Incident.IncidentStatus.RESOLVED) {
                continue;
            }
            if (isCompatible(event.getCategory(), incident.getCategory())
                    && isSpatiallyClose(event, incident)
                    && isTemporallyClose(event, incident)) {
                return incident;
            }
        }
        return null;
    }

    private boolean isCompatible(IncidentCategory eventCat, IncidentCategory incidentCat) {
        return eventCat == incidentCat
                || eventCat == IncidentCategory.UNKNOWN
                || incidentCat == IncidentCategory.UNKNOWN;
    }

    private boolean isSpatiallyClose(RawEvent event, Incident incident) {
        return event.getLocation().distanceTo(incident.getEpicenter()) <= DISTANCE_THRESHOLD;
    }

    private boolean isTemporallyClose(RawEvent event, Incident incident) {
        long timeDiff = Math.abs(
                event.getTimestampMinutes() - incident.getLastUpdatedMinutes());
        return timeDiff <= TIME_WINDOW_MINUTES;
    }
}
