package com.rescuenet.core.assignment;

import com.rescuenet.core.incident.Incident;
import com.rescuenet.core.team.TeamSkill;

import java.util.Set;

/**
 * Represents an incident that could not be immediately assigned
 * because no suitable team was available. Held in the pending queue.
 */
public class PendingAssignment {

    private final Incident incident;
    private final Set<TeamSkill> requiredSkills;
    private final long queuedAtMinutes;

    public PendingAssignment(Incident incident, Set<TeamSkill> requiredSkills,
                             long queuedAtMinutes) {
        this.incident = incident;
        this.requiredSkills = requiredSkills;
        this.queuedAtMinutes = queuedAtMinutes;
    }

    public Incident getIncident() { return incident; }
    public Set<TeamSkill> getRequiredSkills() { return requiredSkills; }
    public long getQueuedAtMinutes() { return queuedAtMinutes; }

    @Override
    public String toString() {
        return String.format("Pending[incident=%s | skills=%s | queued_at=%d]",
                incident.getId(), requiredSkills, queuedAtMinutes);
    }
}
