package com.rescuenet.core.assignment;

import com.rescuenet.core.event.IncidentCategory;
import com.rescuenet.core.team.TeamSkill;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Maps an IncidentCategory to the set of TeamSkills required to respond. */
public class SkillMapper {

    private SkillMapper() { }

    public static Set<TeamSkill> requiredSkillsFor(IncidentCategory category) {
        switch (category) {
            case FIRE:
                return Collections.singleton(TeamSkill.FIRE);
            case MEDICAL:
                return Collections.singleton(TeamSkill.MEDICAL);
            case INTRUSION:
            case CROWD_ANOMALY:
                return Collections.singleton(TeamSkill.SECURITY);
            case ENVIRONMENTAL_HAZARD:
                return Collections.singleton(TeamSkill.ENVIRONMENTAL);
            default:
                return Collections.emptySet();
        }
    }
}
