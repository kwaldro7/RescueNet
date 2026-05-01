package com.rescuenet.infrastructure;

import com.rescuenet.core.assignment.PendingAssignment;
import com.rescuenet.core.assignment.SkillMapper;
import com.rescuenet.core.incident.Incident;
import com.rescuenet.core.priority.PriorityLevel;
import com.rescuenet.core.team.ResponseTeam;
import com.rescuenet.core.team.TeamAvailabilityObserver;
import com.rescuenet.core.team.TeamSkill;
import com.rescuenet.core.team.TeamState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Handles assigning response teams to incidents.
 *
 * Design Pattern: OBSERVER (implements TeamAvailabilityObserver)
 * When a team becomes AVAILABLE, this service is notified and
 * automatically assigns it to a queued pending incident (Scenario E).
 *
 * Assignment logic:
 *   1. Determine required skills from incident category.
 *   2. Find closest available team with matching skills.
 *   3. If found: team -> EN_ROUTE, incident -> ASSIGNED.
 *   4. If not found: incident added to pending queue.
 */
public class TeamAssignmentService implements TeamAvailabilityObserver {

    // Only incidents at MEDIUM or above trigger automatic assignment
    private static final PriorityLevel ASSIGNMENT_THRESHOLD = PriorityLevel.MEDIUM;

    private final TeamRegistry teamRegistry;
    private final List<PendingAssignment> pendingQueue = new ArrayList<>();

    public TeamAssignmentService(TeamRegistry teamRegistry) {
        this.teamRegistry = teamRegistry;
        teamRegistry.addObserverToAll(this);
    }

    /**
     * Attempts to assign a team to the given incident.
     * If priority is below threshold, does nothing.
     * If no team is available, queues the incident.
     *
     * @return the assigned team, or null if queued/skipped
     */
    public ResponseTeam assignTeam(Incident incident, long currentTimeMinutes) {
        if (incident.getPriority().ordinal() < ASSIGNMENT_THRESHOLD.ordinal()) {
            return null;
        }

        Set<TeamSkill> required = SkillMapper.requiredSkillsFor(incident.getCategory());
        ResponseTeam candidate = teamRegistry.findBestAvailable(
                required, incident.getEpicenter());

        if (candidate != null) {
            candidate.setState(TeamState.EN_ROUTE);
            incident.setStatus(Incident.IncidentStatus.ASSIGNED);
            return candidate;
        } else {
            // No team available - queue the incident (Scenario E)
            boolean alreadyQueued = false;
            for (PendingAssignment pending : pendingQueue) {
                if (pending.getIncident().getId().equals(incident.getId())) {
                    alreadyQueued = true;
                    break;
                }
            }
            if (!alreadyQueued) {
                pendingQueue.add(
                        new PendingAssignment(incident, required, currentTimeMinutes));
            }
            return null;
        }
    }

    /**
     * Observer callback - fired when a team transitions to AVAILABLE.
     * Scans the pending queue and assigns this team to the first match.
     */
    @Override
    public void onTeamAvailable(ResponseTeam team) {
        for (int i = 0; i < pendingQueue.size(); i++) {
            PendingAssignment pending = pendingQueue.get(i);
            if (team.hasSkills(pending.getRequiredSkills())) {
                pendingQueue.remove(i);
                team.setState(TeamState.EN_ROUTE);
                pending.getIncident().setStatus(Incident.IncidentStatus.ASSIGNED);
                return;
            }
        }
    }

    /**
     * Re-evaluates assignment after a priority change (Scenario F).
     * Does nothing if the incident is already assigned.
     */
    public ResponseTeam reassignIfNeeded(Incident incident, long currentTimeMinutes) {
        if (incident.getStatus() == Incident.IncidentStatus.ASSIGNED) {
            return null;
        }
        return assignTeam(incident, currentTimeMinutes);
    }

    public List<PendingAssignment> getPendingQueue() {
        return Collections.unmodifiableList(pendingQueue);
    }
}
