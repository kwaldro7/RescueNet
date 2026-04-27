package com.rescuenet.core.team;

/**
 * Observer interface for team availability changes.
 *
 * Design Pattern: OBSERVER
 * When a ResponseTeam transitions to AVAILABLE (e.g. returns from a scene),
 * all registered observers are notified. TeamAssignmentService implements
 * this to automatically assign pending queued incidents (Scenario E).
 *
 * This keeps ResponseTeam decoupled from TeamAssignmentService -
 * the team fires the event without knowing who is listening.
 */
public interface TeamAvailabilityObserver {

    /** Called when a team's state transitions to AVAILABLE. */
    void onTeamAvailable(ResponseTeam team);
}
