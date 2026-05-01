package com.rescuenet.core.priority;

import com.rescuenet.core.event.RawEvent;
import com.rescuenet.core.incident.Incident;

import java.util.ArrayList;
import java.util.List;

/**
 * Default priority strategy.
 *
 * Scoring model:
 *   1. Sum the raw scores of all RELIABLE events on the incident.
 *   2. Add corroboration bonus: +15 per additional reliable source.
 *   3. Map total score to PriorityLevel:
 *        < 20   -> LOW
 *        20-44  -> MEDIUM
 *        45-74  -> HIGH
 *        >= 75  -> CRITICAL
 *
 * Scenario verification:
 *   Scenario B: smoke 450ppm (score=20) -> MEDIUM  (>= 20 threshold) OK
 *   Scenario F: sev2(6) + sev10(30) + corroboration(15) = 51 -> HIGH  OK
 *   Scenario C: smoke(20)+human8(24)+video0.95(24)+2x15 = 98 -> CRITICAL OK
 */
public class CompositePriorityStrategy implements PriorityStrategy {

    private static final int CORROBORATION_BONUS = 15;

    private static final int MEDIUM_THRESHOLD   = 20;
    private static final int HIGH_THRESHOLD     = 45;
    private static final int CRITICAL_THRESHOLD = 75;

    @Override
    public PriorityLevel compute(Incident incident) {
        List<RawEvent> reliableEvents = new ArrayList<>();
        for (RawEvent event : incident.getEvents()) {
            if (event.isReliable()) {
                reliableEvents.add(event);
            }
        }

        if (reliableEvents.isEmpty()) {
            return PriorityLevel.LOW;
        }

        int total = 0;
        for (RawEvent event : reliableEvents) {
            total += event.getRawScore();
        }

        // Corroboration bonus for multiple independent reliable sources
        total += (reliableEvents.size() - 1) * CORROBORATION_BONUS;

        return scoreToLevel(total);
    }

    private PriorityLevel scoreToLevel(int score) {
        if (score >= CRITICAL_THRESHOLD) return PriorityLevel.CRITICAL;
        if (score >= HIGH_THRESHOLD)     return PriorityLevel.HIGH;
        if (score >= MEDIUM_THRESHOLD)   return PriorityLevel.MEDIUM;
        return PriorityLevel.LOW;
    }
}
