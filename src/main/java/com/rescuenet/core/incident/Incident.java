package com.rescuenet.core.incident;

import com.rescuenet.core.event.IncidentCategory;
import com.rescuenet.core.event.Location;
import com.rescuenet.core.event.RawEvent;
import com.rescuenet.core.priority.PriorityLevel;
import com.rescuenet.core.priority.PriorityStrategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Represents a real-world emergency constructed from correlated events.
 * The central aggregate of the domain.
 *
 * An incident:
 *   - Accumulates events as new information arrives
 *   - Maintains a running priority via the injected PriorityStrategy
 *   - Tracks its epicenter (location of first event) for correlation
 *   - Knows when it was last updated for temporal correlation
 */
@SuppressWarnings("PMD.DataClass")
// Domain event classes intentionally carry data — this is not an
// anti-pattern in the context of an event-driven domain model.
public class Incident {

    public enum IncidentStatus {
        OPEN,
        ASSIGNED,
        RESOLVED
    }

    private final String id;
    private final IncidentCategory category;
    private final Location epicenter;
    private final long createdAtMinutes;
    private final List<RawEvent> events;
    private final PriorityStrategy priorityStrategy;

    private PriorityLevel priority;
    private IncidentStatus status;
    private long lastUpdatedMinutes;

    public Incident(RawEvent firstEvent, long currentTimeMinutes,
                    PriorityStrategy priorityStrategy) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.category = firstEvent.getCategory();
        this.epicenter = firstEvent.getLocation();
        this.createdAtMinutes = currentTimeMinutes;
        this.lastUpdatedMinutes = currentTimeMinutes;
        this.events = new ArrayList<>();
        this.status = IncidentStatus.OPEN;
        this.priorityStrategy = priorityStrategy;

        this.events.add(firstEvent);
        this.priority = priorityStrategy.compute(this);
    }

    /**
     * Adds a new event and recalculates priority.
     * Returns true if the priority changed (used to trigger reassignment).
     */
    public boolean addEvent(RawEvent event, long currentTimeMinutes) {
        events.add(event);
        this.lastUpdatedMinutes = currentTimeMinutes;

        PriorityLevel oldPriority = priority;
        priority = priorityStrategy.compute(this);
        return priority != oldPriority;
    }

    public String getId() { return id; }
    public IncidentCategory getCategory() { return category; }
    public Location getEpicenter() { return epicenter; }
    public long getCreatedAtMinutes() { return createdAtMinutes; }
    public long getLastUpdatedMinutes() { return lastUpdatedMinutes; }
    public PriorityLevel getPriority() { return priority; }
    public IncidentStatus getStatus() { return status; }
    public List<RawEvent> getEvents() { return Collections.unmodifiableList(events); }
    public int getEventCount() { return events.size(); }

    public void setStatus(IncidentStatus status) { this.status = status; }

    @Override
    public String toString() {
        return String.format("Incident[%s | %s | %s | events=%d | status=%s]",
                id, category, priority, events.size(), status);
    }
}
