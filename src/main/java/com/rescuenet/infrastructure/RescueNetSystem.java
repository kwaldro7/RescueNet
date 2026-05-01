package com.rescuenet.infrastructure;

import com.rescuenet.core.assignment.PendingAssignment;
import com.rescuenet.core.event.RawEvent;
import com.rescuenet.core.incident.Incident;
import com.rescuenet.core.team.ResponseTeam;

import java.util.List;

/**
 * Top-level facade for the RescueNet system.
 *
 * Wires together SimulationClock, TeamRegistry, TeamAssignmentService,
 * and IncidentManager. All scenarios (tests and CLI) interact only
 * through this class - strict separation between interface and domain.
 *
 * Typical usage:
 *   RescueNetSystem system = new RescueNetSystem();
 *   system.registerTeam(team);
 *   system.submitEvent(EventFactory.humanReport(...));
 *   system.advanceTime(5);
 *   system.submitEvent(EventFactory.sensorEvent(...));
 */
public class RescueNetSystem {

    private final SimulationClock clock;
    private final TeamRegistry teamRegistry;
    private final TeamAssignmentService assignmentService;
    private final IncidentManager incidentManager;

    public RescueNetSystem() {
        this.clock = new SimulationClock();
        this.teamRegistry = new TeamRegistry();
        this.assignmentService = new TeamAssignmentService(teamRegistry);
        this.incidentManager = new IncidentManager(assignmentService);
    }

    // --- Team management ---

    /** Registers a team and attaches the assignment observer to it. */
    public void registerTeam(ResponseTeam team) {
        teamRegistry.register(team);
        team.addObserver(assignmentService);
    }

    // --- Event submission ---

    /**
     * Submits an event at the current simulation time.
     * Returns the affected incident, or null if the event was unreliable.
     */
    public Incident submitEvent(RawEvent event) {
        return incidentManager.processEvent(event, clock.now());
    }

    // --- Time control ---

    public void advanceTime(long minutes) { clock.advance(minutes); }
    public long getCurrentTime() { return clock.now(); }

    // --- Queries ---

    public List<Incident> getIncidents() { return incidentManager.getIncidents(); }
    public List<Incident> getOpenIncidents() { return incidentManager.getOpenIncidents(); }
    public List<ResponseTeam> getTeams() { return teamRegistry.getAll(); }
    public List<ResponseTeam> getAvailableTeams() { return teamRegistry.getAvailable(); }
    public List<PendingAssignment> getPendingQueue() { return assignmentService.getPendingQueue(); }

    // --- Reporting ---

    public void printStatus() {
        System.out.println("=== RescueNet [t=" + clock.now() + " min] ===");
        System.out.println("Incidents (" + getIncidents().size() + "):");
        for (Incident i : getIncidents()) {
            System.out.println("  " + i);
        }
        System.out.println("Teams (" + getTeams().size() + "):");
        for (ResponseTeam t : getTeams()) {
            System.out.println("  " + t);
        }
        System.out.println("Pending queue: " + getPendingQueue().size());
        System.out.println();
    }
}
