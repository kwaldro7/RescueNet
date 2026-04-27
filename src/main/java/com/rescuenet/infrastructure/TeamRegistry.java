package com.rescuenet.infrastructure;

import com.rescuenet.core.event.Location;
import com.rescuenet.core.team.ResponseTeam;
import com.rescuenet.core.team.TeamAvailabilityObserver;
import com.rescuenet.core.team.TeamSkill;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Registry of all response teams in the city. */
public class TeamRegistry {

    private final List<ResponseTeam> teams = new ArrayList<>();

    public void register(ResponseTeam team) {
        teams.add(team);
    }

    /**
     * Registers an observer on every team currently in the registry.
     * Called once by TeamAssignmentService at startup.
     */
    public void addObserverToAll(TeamAvailabilityObserver observer) {
        for (ResponseTeam team : teams) {
            team.addObserver(observer);
        }
    }

    /**
     * Finds the closest available team that has all required skills.
     * Returns null if none found.
     */
    public ResponseTeam findBestAvailable(Set<TeamSkill> requiredSkills,
                                          Location incidentLocation) {
        ResponseTeam best = null;
        double bestDist = Double.MAX_VALUE;

        for (ResponseTeam team : teams) {
            if (team.isAvailable() && team.hasSkills(requiredSkills)) {
                double dist = team.getLocation().distanceTo(incidentLocation);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = team;
                }
            }
        }
        return best;
    }

    public List<ResponseTeam> getAll() {
        return Collections.unmodifiableList(teams);
    }

    public List<ResponseTeam> getAvailable() {
        List<ResponseTeam> available = new ArrayList<>();
        for (ResponseTeam team : teams) {
            if (team.isAvailable()) {
                available.add(team);
            }
        }
        return available;
    }
}
