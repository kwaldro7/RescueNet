package com.rescuenet.core.team;

import com.rescuenet.core.event.Location;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Represents an operational emergency response team.
 * Fires observer notifications when transitioning to AVAILABLE.
 */
public class ResponseTeam {

    private final String id;
    private final String name;
    private final Set<TeamSkill> skills;
    private final int capacity;
    private Location location;
    private TeamState state;

    private final List<TeamAvailabilityObserver> observers = new ArrayList<>();

    public ResponseTeam(String name, Location location,
                        Set<TeamSkill> skills, int capacity) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.name = name;
        this.location = location;
        this.skills = Collections.unmodifiableSet(skills);
        this.capacity = capacity;
        this.state = TeamState.AVAILABLE;
    }

    // --- Observer registration ---

    public void addObserver(TeamAvailabilityObserver observer) {
        observers.add(observer);
    }

    public void removeObserver(TeamAvailabilityObserver observer) {
        observers.remove(observer);
    }

    // --- State machine ---

    /**
     * Transitions to the target state.
     * Notifies observers when target is AVAILABLE.
     *
     * @throws IllegalStateException if the transition is not valid
     */
    public void setState(TeamState target) {
        if (!state.canTransitionTo(target)) {
            throw new IllegalStateException(String.format(
                    "Team %s cannot transition from %s to %s", name, state, target));
        }
        this.state = target;
        if (target == TeamState.AVAILABLE) {
            notifyObservers();
        }
    }

    private void notifyObservers() {
        for (TeamAvailabilityObserver observer : observers) {
            observer.onTeamAvailable(this);
        }
    }

    // --- Capability ---

    /** Returns true if this team has all of the required skills. */
    public boolean hasSkills(Set<TeamSkill> required) {
        return skills.containsAll(required);
    }

    public boolean isAvailable() {
        return state == TeamState.AVAILABLE;
    }

    // --- Getters ---

    public String getId() { return id; }
    public String getName() { return name; }
    public Set<TeamSkill> getSkills() { return skills; }
    public int getCapacity() { return capacity; }
    public Location getLocation() { return location; }
    public TeamState getState() { return state; }

    public void setLocation(Location location) { this.location = location; }

    @Override
    public String toString() {
        return String.format("Team[%s | %s | %s | skills=%s]",
                id, name, state, skills);
    }
}
