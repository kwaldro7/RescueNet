package com.rescuenet.core.priority;

import com.rescuenet.core.incident.Incident;

/**
 * Strategy interface for computing an Incident's priority.
 *
 * Design Pattern: STRATEGY
 * Decouples the scoring algorithm from the Incident class.
 * Different implementations can be swapped in without touching Incident.
 * The default implementation is CompositePriorityStrategy.
 */
public interface PriorityStrategy {

    /**
     * Computes and returns the priority for the given incident.
     * Called every time a new event is added to the incident.
     */
    PriorityLevel compute(Incident incident);
}
