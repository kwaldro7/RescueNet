# RescueNet — Requirements and Design Document

## Table of Contents

1. [Domain Analysis](#1-domain-analysis)
2. [Requirements](#2-requirements)
3. [Architecture](#3-architecture)
4. [Design Patterns](#4-design-patterns)
5. [UML Diagrams](#5-uml-diagrams)
6. [PMD Report and Refactoring](#6-pmd-report-and-refactoring)
7. [Testing Strategy](#7-testing-strategy)

---

## 1. Domain Analysis

### 1.1 Problem Statement

The city of Novaterra operates four independent emergency data sources: environmental
sensors, citizen mobile reports, video analytics cameras, and wearable devices. Each
source is independently unreliable. The central coordination centre receives a
continuous, heterogeneous stream of data that operators must interpret in real time.

The fundamental problem is not a lack of data — it is the need to interpret a
continuous and potentially unreliable flow of information in order to support timely
and effective decision-making.

### 1.2 Key Domain Concepts

**RawEvent** — A single input signal from one source. Has a location, timestamp,
incident category, and reliability status. Events are the system's only inputs.

**Incident** — A real-world emergency constructed from one or more correlated events.
An incident has a priority, a status, and an evolving event list. It is the central
aggregate of the domain.

**Correlation** — The process of deciding whether a new event belongs to an existing
incident or starts a new one. Based on spatial proximity, temporal proximity, and
category compatibility.

**Priority** — A four-level classification (LOW / MEDIUM / HIGH / CRITICAL) computed
from the incident's events and recalculated on every new event.

**ResponseTeam** — An operational unit with skills, a location, and a state. Transitions
through AVAILABLE → EN_ROUTE → ON_SCENE → AVAILABLE.

**Assignment** — The act of dispatching a team to an incident. May be immediate or
deferred (queued) if no suitable team is available.

### 1.3 Design Constraints Applied

- **2D Cartesian grid** for location. Distance = Euclidean. No GPS or Haversine (spec 4).
- **Tick-based simulation clock** (`SimulationClock`). No real-time threads (spec 4).
- **Interface layer = CLI + JUnit tests**. No GUI or REST API required (spec 4).
- **Java 11**, modular Maven project (spec 2.3).

---

## 2. Requirements

### 2.1 Functional Requirements

| ID  | Requirement | Spec Ref |
|-----|-------------|----------|
| FR1 | System receives events from HumanReport, SensorEvent, VideoAnalyticsEvent, WearableAlert | 1.1 |
| FR2 | System creates an Incident from one or more correlated events | 1.2 |
| FR3 | System correlates events by spatial proximity (≤200 units), temporal proximity (≤15 min), and category compatibility | 1.2 |
| FR4 | Each incident is assigned a priority: LOW, MEDIUM, HIGH, or CRITICAL | 1.3 |
| FR5 | Priority depends on event types, sensor values, severity, source count, and reliability | 1.3 |
| FR6 | System handles priority escalation when new events arrive | 1.3, 1.5 |
| FR7 | System manages response teams with location, skills, capacity, and state | 1.4 |
| FR8 | System assigns the nearest qualified available team when a high-priority incident is created | 1.4 |
| FR9 | When no team is available, the incident is queued and auto-assigned when a team becomes available | 1.4 |
| FR10 | System flags sensor events with battery < 5% as UNRELIABLE and ignores them | 2.2-G |
| FR11 | System flags video events with confidence < 0.4 as UNRELIABLE and ignores them | 2.2-G |

### 2.2 Non-Functional Requirements

| ID   | Requirement | Spec Ref |
|------|-------------|----------|
| NFR1 | System must be executable without manual input | 2.1 |
| NFR2 | All scenarios must be reproducible | 2.1 |
| NFR3 | Architecture must separate domain logic from interface layer | 2.3 |
| NFR4 | At least 3 design patterns must be implemented and documented | 2.3 |
| NFR5 | PMD must be used for code quality analysis | 2.5 |
| NFR6 | Refactoring must be documented (5+ improvements) | 2.5 |

### 2.3 Mandatory Scenarios

| Scenario | Input | Expected Output |
|----------|-------|-----------------|
| A — Human Report | FIRE report, severity 6 | Incident created; priority assigned |
| B — Sensor Threshold | Smoke sensor, high ppm, ALERT | Incident created; priority >= MEDIUM |
| C — Correlation | Smoke + human + video (conf 0.95), same area/time | Single incident; priority HIGH or CRITICAL |
| D — Team Assignment | High-priority incident; available teams | Team assigned; status EN_ROUTE |
| E — Resource Exhaustion | HIGH incident; no teams available | Queued; auto-assigned when team available |
| F — Priority Escalation | LOW incident receives severity-10 event | Priority increases; team dispatched |
| G — Fault Tolerance | Sensor ALERT; battery < 5% | Event UNRELIABLE; no incident created |

---

## 3. Architecture

### 3.1 Layer Structure

The system is divided into three strict layers. The domain core has zero dependency
on the interface layer — it never imports from `ui/` or test classes.

```
┌─────────────────────────────────────────────────────────────┐
│  Interface Layer                                             │
│  com.rescuenet.ui.Main  (CLI)   ·   JUnit test suite        │
└──────────────────────────┬──────────────────────────────────┘
                           │  uses only
┌──────────────────────────▼──────────────────────────────────┐
│  Infrastructure Layer                                        │
│  RescueNetSystem (façade)  ·  SimulationClock                │
│  IncidentManager  ·  TeamRegistry  ·  TeamAssignmentService  │
└──────────────────────────┬──────────────────────────────────┘
                           │  uses
┌──────────────────────────▼──────────────────────────────────┐
│  Domain Core                                                 │
│  core/event      RawEvent hierarchy  ·  EventFactory         │
│  core/incident   Incident  ·  CorrelationEngine              │
│  core/priority   PriorityStrategy  ·  CompositePriority      │
│  core/team       ResponseTeam  ·  TeamState  ·  Observer     │
│  core/assignment PendingAssignment  ·  SkillMapper           │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 Key Design Decisions

**Tick-based simulation clock**
Real-time threading causes non-deterministic test behaviour and race conditions.
`SimulationClock` maintains a `long currentMinutes` counter. All domain objects
receive time via `clock.now()`. Tests call `system.advanceTime(n)` to move time
forward deterministically. This satisfies spec 2.1 (reproducible scenarios) and
spec 2.5 (tests executable without manual intervention).

**RescueNetSystem as façade**
The façade exposes a single, clean API (`submitEvent`, `advanceTime`, `registerTeam`,
`getIncidents`, etc.). Tests and the CLI interact exclusively through this class.
Adding or changing an output format never requires touching domain logic.

**lastUpdatedMinutes in CorrelationEngine**
The temporal filter uses `incident.getLastUpdatedMinutes()`, not `createdAtMinutes`.
A fire that was created 30 minutes ago but last updated 2 minutes ago should still
accept a new corroborating event. Using creation time would incorrectly reject it.

---

## 4. Design Patterns

### 4.1 Pattern 1 — Factory: `EventFactory`

**Location:** `com.rescuenet.core.event.EventFactory`

**Intent:**
Centralise construction of all four `RawEvent` subtypes and apply reliability
rules before events reach any domain logic.

**Problem it solves:**
Each event type has different constructor parameters and different reliability
criteria. Without a factory, the battery < 5% check for sensors and the
confidence < 0.4 check for video events would be duplicated in every caller.
If the threshold changes, every caller must be updated — a maintenance hazard.

**Implementation:**
```java
public final class EventFactory {
    private EventFactory() { }

    public static SensorEvent sensorEvent(..., double batteryLevel) {
        SensorEvent event = new SensorEvent(...);
        if (event.hasCriticallyLowBattery()) {
            event.markUnreliable();   // Scenario G handled here
        }
        return event;
    }

    public static VideoAnalyticsEvent videoEvent(..., double confidence) {
        VideoAnalyticsEvent event = new VideoAnalyticsEvent(...);
        if (confidence < VideoAnalyticsEvent.LOW_CONFIDENCE_THRESHOLD) {
            event.markUnreliable();
        }
        return event;
    }
    // humanReport() and wearableAlert() always RELIABLE
}
```

**Why this pattern was chosen:**
`IncidentManager.processEvent()` only needs to call `event.isReliable()`.
It never needs to know about battery levels, confidence thresholds, or any
other source-specific reliability rule. Scenario G (fault tolerance) is handled
entirely within the factory before domain logic is ever involved.

---

### 4.2 Pattern 2 — Strategy: `PriorityStrategy`

**Location:** `com.rescuenet.core.priority`

**Intent:**
Decouple the priority computation algorithm from the `Incident` class, allowing
different scoring strategies to be swapped without modifying the aggregate.

**Problem it solves:**
Priority computation is complex, combines multiple signals, and will need to evolve
(e.g. adding weather data, time-of-day weighting). Embedding this logic in `Incident`
violates Single Responsibility and makes each change risky.

**Class diagram:**
```
«interface»
PriorityStrategy
  + compute(Incident): PriorityLevel
        ▲
        │ implements
CompositePriorityStrategy
  + compute(Incident): PriorityLevel
  - scoreToLevel(int): PriorityLevel
```

**Scoring model:**

| Source | Score formula | Max |
|--------|---------------|-----|
| HumanReport severity s | (s / 10) × 30 | 30 |
| SensorEvent smoke ≥ 700 ppm | 30 pts | 30 |
| SensorEvent smoke ≥ 300 ppm | 20 pts | 20 |
| SensorEvent MOTION (ALERT) | 20 pts | 20 |
| VideoAnalyticsEvent confidence c | 25 × c | 25 |
| WearableAlert manual trigger | 15 pts | 15 |
| Each extra reliable source | +15 corroboration bonus | — |

| Score | Priority |
|-------|----------|
| < 20 | LOW |
| 20–44 | MEDIUM |
| 45–74 | HIGH |
| ≥ 75 | CRITICAL |

**Scenario C verification:**
smoke(20) + human_sev8(24) + video_0.95(24) + 2 × corroboration(30) = 98 → CRITICAL ✓

**Why this pattern was chosen:**
A new `WeatherAwarePriorityStrategy` could be injected into `Incident` at
construction with zero changes to any other class. Tests can also inject a
deterministic stub strategy to isolate priority-unrelated behaviour.

---

### 4.3 Pattern 3 — Observer: `TeamAvailabilityObserver`

**Location:** `com.rescuenet.core.team`

**Intent:**
Decouple `ResponseTeam` from `TeamAssignmentService`. When a team becomes AVAILABLE,
interested parties are notified without the team knowing who they are.

**Problem it solves:**
When a team returns from a scene and becomes AVAILABLE, queued incidents must be
auto-assigned (Scenario E). If `ResponseTeam` called `TeamAssignmentService`
directly, we would have a downward dependency from a domain object to an
infrastructure service — a serious architectural violation. The Observer pattern
inverts this: the team publishes a state change, the service subscribes.

**Implementation:**
```java
// Interface in domain layer
public interface TeamAvailabilityObserver {
    void onTeamAvailable(ResponseTeam team);
}

// ResponseTeam (domain) — fires the event
public void setState(TeamState target) {
    // ... validate transition ...
    this.state = target;
    if (target == TeamState.AVAILABLE) {
        notifyObservers();   // fires for all registered observers
    }
}

// TeamAssignmentService (infrastructure) — handles the event
@Override
public void onTeamAvailable(ResponseTeam team) {
    for (PendingAssignment pending : pendingQueue) {
        if (team.hasSkills(pending.getRequiredSkills())) {
            pendingQueue.remove(pending);
            team.setState(TeamState.EN_ROUTE);
            pending.getIncident().setStatus(ASSIGNED);
            return;
        }
    }
}
```

**Scenario E flow:**
1. HIGH incident created; all FIRE teams UNAVAILABLE
2. `TeamAssignmentService.assignTeam()` adds incident to `pendingQueue`
3. Team completes its previous assignment → `setState(AVAILABLE)`
4. `ResponseTeam.notifyObservers()` fires
5. `TeamAssignmentService.onTeamAvailable()` drains the queue automatically
6. Team → EN_ROUTE, incident → ASSIGNED

**Why this pattern was chosen:**
Zero polling, zero threads. The queue drains purely through the event mechanism.
Additional observers (e.g. a logging service, a notification service) can be
registered with zero changes to `ResponseTeam` or `TeamAssignmentService`.

---

## 5. UML Diagrams

### 5.1 Class Diagram (Domain Core)

```
 ┌──────────────────────────────────────────────────────────────┐
 │                      «abstract»                              │
 │                       RawEvent                               │
 │  - id: String                                                │
 │  - location: Location                                        │
 │  - timestampMinutes: long                                    │
 │  - category: IncidentCategory                                │
 │  - reliability: EventReliability                             │
 │  + isReliable(): boolean                                     │
 │  + markUnreliable(): void                                    │
 │  + getRawScore(): int  {abstract}                            │
 └──────┬──────────┬──────────┬──────────┬───────────────────-─┘
        │          │          │          │
   ┌────▼────┐ ┌───▼────┐ ┌──▼──────────▼──────┐ ┌────────────┐
   │ Human   │ │Sensor  │ │VideoAnalyticsEvent  │ │ Wearable   │
   │ Report  │ │ Event  │ │- label: String      │ │  Alert     │
   │-severity│ │-sensorId│ │- confidence: double│ │-deviceId   │
   │ 1-10    │ │-value  │ └─────────────────────┘ │-manualTrig │
   └─────────┘ │-battery│                          └────────────┘
               │-status │
               └────────┘

 ┌────────────────────────────────┐
 │           Incident              │
 │  - id: String                  │    ┌──────────────────────┐
 │  - category: IncidentCategory  │    │  «interface»         │
 │  - epicenter: Location         │◄───│  PriorityStrategy    │
 │  - events: List<RawEvent>      │    │  + compute(Incident) │
 │  - priority: PriorityLevel     │    └──────────┬───────────┘
 │  - status: IncidentStatus      │               │ implements
 │  - lastUpdatedMinutes: long    │    ┌──────────▼───────────┐
 │  + addEvent(): boolean         │    │CompositePriority     │
 └────────────────────────────────┘    │Strategy              │
                                       └──────────────────────┘

 ┌────────────────────────────────┐
 │         ResponseTeam           │    ┌──────────────────────┐
 │  - id: String                  │    │  «interface»         │
 │  - name: String                │───►│  TeamAvailability    │
 │  - skills: Set<TeamSkill>      │    │  Observer            │
 │  - state: TeamState            │    │  + onTeamAvailable() │
 │  - observers: List<Observer>   │    └──────────┬───────────┘
 │  + setState(TeamState): void   │               │ implements
 └────────────────────────────────┘    ┌──────────▼───────────┐
                                       │TeamAssignmentService │
                                       │- pendingQueue: List  │
                                       │+ assignTeam()        │
                                       │+ onTeamAvailable()   │
                                       └──────────────────────┘
```

### 5.2 Component Diagram

```
 ┌─────────────────────────────────────────────────────────────┐
 │  «component» Interface Layer                                 │
 │  ┌──────────────┐    ┌──────────────────────────────────┐   │
 │  │  Main (CLI)  │    │  RescueNetSystemTest (JUnit 5)   │   │
 │  └──────┬───────┘    └──────────────────┬───────────────┘   │
 └─────────│──────────────────────────────-│───────────────────┘
           │  «uses»                        │  «uses»
 ┌─────────▼──────────────────────────────-▼───────────────────┐
 │  «component» Infrastructure                                  │
 │  ┌────────────────────┐  ┌──────────────────────────────┐   │
 │  │  RescueNetSystem   │  │      SimulationClock         │   │
 │  │  (façade)          │  └──────────────────────────────┘   │
 │  └────────────────────┘                                      │
 │  ┌────────────────────┐  ┌──────────────────────────────┐   │
 │  │  IncidentManager   │  │   TeamAssignmentService      │   │
 │  └────────────────────┘  └──────────────────────────────┘   │
 │  ┌────────────────────┐                                      │
 │  │   TeamRegistry     │                                      │
 │  └────────────────────┘                                      │
 └──────────────────────────────┬──────────────────────────────┘
                                │  «uses»
 ┌──────────────────────────────▼──────────────────────────────┐
 │  «component» Domain Core                                     │
 │  ┌─────────────────┐  ┌──────────────────────────────────┐  │
 │  │  core/event     │  │  core/incident                   │  │
 │  │  RawEvent hier. │  │  Incident · CorrelationEngine    │  │
 │  │  EventFactory   │  └──────────────────────────────────┘  │
 │  └─────────────────┘                                         │
 │  ┌─────────────────┐  ┌──────────────────────────────────┐  │
 │  │  core/priority  │  │  core/team                       │  │
 │  │  PriorityStrat. │  │  ResponseTeam · Observer         │  │
 │  └─────────────────┘  └──────────────────────────────────┘  │
 │  ┌─────────────────┐                                         │
 │  │  core/assignment│                                         │
 │  │  PendingAssign. │                                         │
 │  │  SkillMapper    │                                         │
 │  └─────────────────┘                                         │
 └─────────────────────────────────────────────────────────────┘
```

### 5.3 Sequence Diagram 1 — Scenario C (Event Correlation)

```
Caller      RescueNetSystem   IncidentManager   CorrelationEngine   Incident

  │──submitEvent(smoke,t=0)──►│
  │                           │──processEvent(smoke,0)──►│
  │                           │                          │──findMatch(smoke,[])──►│
  │                           │                          │◄──null (no incidents)──│
  │                           │                          │  new Incident(smoke)
  │                           │                          │  incidents.add(i1)
  │◄──i1──────────────────────│◄──i1─────────────────────│

  │──advanceTime(3)───────────►│  clock = 3 min

  │──submitEvent(humanReport,t=3)──►│
  │                           │──processEvent(report,3)─►│
  │                           │                          │──findMatch(report,[i1])──►│
  │                           │                          │   dist((310,295)→(300,300)) ≈ 11 ≤ 200 ✓
  │                           │                          │   timeDiff(3-0=3) ≤ 15 ✓
  │                           │                          │   FIRE == FIRE ✓
  │                           │                          │◄──i1──────────────────────│
  │                           │                          │  i1.addEvent(report,3)
  │                           │                          │  priority → MEDIUM
  │◄──i1──────────────────────│◄──i1─────────────────────│

  │──advanceTime(2)───────────►│  clock = 5 min

  │──submitEvent(videoEvent,t=5)──►│
  │                           │──processEvent(video,5)──►│
  │                           │                          │──findMatch(video,[i1])──►│
  │                           │                          │   all 3 checks pass ✓
  │                           │                          │◄──i1──────────────────────│
  │                           │                          │  i1.addEvent(video,5)
  │                           │                          │  score=98 → CRITICAL
  │◄──i1──────────────────────│◄──i1─────────────────────│

  assert: incidents.size()==1 · i1.eventCount==3 · i1.priority==CRITICAL ✓
```

### 5.4 Sequence Diagram 2 — Scenario E (Observer / Queuing)

```
Caller       RescueNetSystem   TeamAssignmentService   ResponseTeam(Bravo)

  │  team state = UNAVAILABLE
  │──registerTeam(bravo)──────►│
  │                            │──team.addObserver(assignmentService)──►│

  │──submitEvent(smoke,t=0)───►│  [creates HIGH incident i1]
  │                            │──assignTeam(i1,0)──►│
  │                            │                     │  findBestAvailable(FIRE) → null
  │                            │                     │  pendingQueue.add(Pending(i1))
  │                            │◄─null───────────────│
  │  assert: pendingQueue.size()==1
  │  assert: i1.status==OPEN

  │──bravo.setState(AVAILABLE)───────────────────────────────────────────►│
  │                                                                        │ notifyObservers()
  │                            │◄──onTeamAvailable(bravo)─────────────────│
  │                            │  pendingQueue matches FIRE skills
  │                            │  pendingQueue.remove(pending)
  │                            │──bravo.setState(EN_ROUTE)────────────────►│
  │                            │  i1.setStatus(ASSIGNED)

  │  assert: pendingQueue.size()==0
  │  assert: i1.status==ASSIGNED
  │  assert: bravo.state==EN_ROUTE ✓
```

---

## 6. PMD Report and Refactoring

PMD 6.55.0 was run using `mvn pmd:pmd` with the following rulesets:
`maven-pmd-plugin-default.xml`, `design.xml`, `unnecessary.xml`, `basic.xml`, `naming.xml`.

### 6.1 Violations Fixed (Code Changed)

**Refactor 1 — ClassWithOnlyPrivateConstructorsShouldBeFinal (Priority 1)**
Files: `EventFactory.java`, `SkillMapper.java`

Utility classes with only private constructors cannot be subclassed. PMD correctly
flags them — adding `final` documents this intent and prevents accidental extension.

```java
// Before
public class EventFactory { private EventFactory() { } ... }

// After
public final class EventFactory { private EventFactory() { } ... }
```

**Refactor 2 — ConstructorCallsOverridableMethod (Priority 1)**
File: `Incident.java`

The original constructor called `addEvent()` — a public, overridable method. If a
subclass overrides `addEvent()`, it runs on a partially-constructed object, which is
a well-known source of subtle bugs. The fix inlines the constructor logic directly.

```java
// Before (in constructor)
addEvent(firstEvent, currentTimeMinutes);

// After (inlined — no overridable method call)
this.events.add(firstEvent);
this.priority = priorityStrategy.compute(this);
this.lastUpdatedMinutes = currentTimeMinutes;
```

**Refactor 3 — UseUtilityClass (Priority 3)**
File: `Main.java`

All methods in `Main` are static. PMD flags classes where all methods are static but
no private constructor exists. Added `private Main() { }` to signal intent clearly.

**Refactor 4 — UseLocaleWithCaseConversions (Priority 3)**
File: `VideoAnalyticsEvent.java`

`String.toLowerCase()` uses the default locale, which can produce incorrect results
in some locales (e.g. Turkish: `"I".toLowerCase()` gives `"ı"`, not `"i"`).

```java
// Before
switch (label.toLowerCase()) {

// After
switch (label.toLowerCase(java.util.Locale.ROOT)) {
```

**Refactor 5 — UnnecessaryImport (Priority 4)**
Files: `SkillMapper.java` (unused `java.util.HashSet`), `Main.java` (unused `java.util.Collections`).
Both unused imports were removed.

### 6.2 Violations Suppressed (Justified — Not Fixed)

**DataClass** (`SensorEvent`, `VideoAnalyticsEvent`, `Incident`)
Suppressed with `@SuppressWarnings("PMD.DataClass")`. These are intentional domain
model classes. In an event-driven architecture, event objects are designed to carry
data. Having many getters relative to behaviour is correct by design, not an
anti-pattern. The alternative (hiding data inside the class) would make the scoring
strategy impossible to implement without coupling.

**ShortVariable** (`x`, `y`, `dx`, `dy`, `id`)
Not fixed. `x` and `y` are universally accepted names for 2D Cartesian coordinates.
`dx`/`dy` are standard delta notation in geometry. Renaming to `xCoordinate` and
`deltaX` would make the Euclidean distance formula harder to read without any
correctness benefit. `id` is the accepted abbreviation for identifier across all
Java frameworks.

**LongVariable** (`DISTANCE_THRESHOLD`, `TIME_WINDOW_MINUTES`, `currentTimeMinutes`, etc.)
Not fixed. These are named constants and parameter names where the full descriptive
name is essential for clarity. Shortening `DISTANCE_THRESHOLD` to `DIST_THRESH` or
`TIME_WINDOW_MINUTES` to `TIME_WIN` would reduce readability with no technical
benefit. Descriptive names at the cost of length is the modern convention.

**AbstractNaming** (`RawEvent`)
Not fixed. The `AbstractXXX` prefix is a legacy PMD convention. The Google Java Style
Guide and the majority of modern Java codebases do not require this prefix.
`RawEvent` is a clearer, more domain-aligned name than `AbstractRawEvent`.

**ShortClassName** (`Main`)
Not fixed. `Main` is the universally accepted name for a Java application entry point.
Renaming it to `RescueNetApplication` or similar would violate convention without
any benefit.

---

## 7. Testing Strategy

### 7.1 Test Structure

All tests are in `RescueNetSystemTest` using JUnit 5 `@Nested` classes. Each nested
class corresponds to one mandatory scenario plus additional unit-level edge cases.

### 7.2 Coverage Summary

| Area | Tests | Notes |
|------|-------|-------|
| Scenario A — human report | 4 | baseline, high/low severity, invalid input |
| Scenario B — sensor threshold | 2 | medium ppm, OK status |
| Scenario C — correlation | 4 | merge, spatial separation, temporal separation, category mismatch |
| Scenario D — team assignment | 2 | happy path, skill matching |
| Scenario E — queuing | 2 | queue + auto-assign, no duplicate queuing |
| Scenario F — escalation | 1 | priority escalation + dispatch |
| Scenario G — fault tolerance | 3 | low battery, boundary battery (5.0%), low confidence video |
| Unit — TeamState machine | 2 | valid chain, invalid jump |
| **Total** | **20+** | |

### 7.3 Key Testing Decisions

- `advanceTime()` called between events to simulate realistic temporal spacing
  without `Thread.sleep()` or real-time dependencies
- `assertThat(priority).isIn(HIGH, CRITICAL)` used where the spec allows either
  value — avoids over-specifying the scoring arithmetic
- Boundary test: battery exactly at 5.0% → RELIABLE (condition is strictly `< 5%`)
- All tests are `@Nested` for clear structure matching the spec's scenario IDs
- AssertJ used for fluent, readable assertions
