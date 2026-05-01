package com.rescuenet.infrastructure;

import com.rescuenet.core.event.RawEvent;
import com.rescuenet.core.incident.CorrelationEngine;
import com.rescuenet.core.incident.Incident;
import com.rescuenet.core.priority.CompositePriorityStrategy;
import com.rescuenet.core.priority.PriorityStrategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Orchestrates the full lifecycle of incidents:
 *   1. Receives a raw (reliable) event
 *   2. Uses CorrelationEngine to merge or create
 *   3. Recalculates priority
 *   4. Triggers assignment via TeamAssignmentService
 */
public class IncidentManager {

    private final List<Incident> incidents = new ArrayList<>();
    private final CorrelationEngine correlationEngine;
    private final PriorityStrategy priorityStrategy;
    private final TeamAssignmentService assignmentService;

    public IncidentManager(TeamAssignmentService assignmentService) {
        this.correlationEngine = new CorrelationEngine();
        this.priorityStrategy = new CompositePriorityStrategy();
        this.assignmentService = assignmentService;
    }

    /**
     * Processes one incoming event.
     * Unreliable events are silently dropped (Scenario G).
     *
     * @return the incident that was created or updated, or null if dropped
     */
    public Incident processEvent(RawEvent event, long currentTimeMinutes) {
        if (!event.isReliable()) {
            return null;
        }

        Incident match = correlationEngine.findMatch(event, incidents);

        boolean priorityChanged;
        Incident incident;

        if (match != null) {
            incident = match;
            priorityChanged = incident.addEvent(event, currentTimeMinutes);
        } else {
            incident = new Incident(event, currentTimeMinutes, priorityStrategy);
            incidents.add(incident);
            priorityChanged = true;
        }

        if (priorityChanged) {
            assignmentService.reassignIfNeeded(incident, currentTimeMinutes);
        }

        return incident;
    }

    public List<Incident> getIncidents() {
        return Collections.unmodifiableList(incidents);
    }

    public List<Incident> getOpenIncidents() {
        List<Incident> open = new ArrayList<>();
        for (Incident i : incidents) {
            if (i.getStatus() != Incident.IncidentStatus.RESOLVED) {
                open.add(i);
            }
        }
        return open;
    }
}
