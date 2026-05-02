# AI Usage Log — RescueNet Project

This file documents all AI assistance used during development.
Tool used: Ollama (local) with `llama3` and `codellama` models.

---

## Entry 1 — Initial Architecture Design

**Task:** Architecture design

**Prompt sent to Ollama:**
```
I am building a Java system called RescueNet. It receives emergency events from
four sources: sensors, citizens, cameras, and wearables. It must correlate events
into incidents, compute priority, and assign response teams. What package structure
and design patterns would you recommend?
```

**AI Output (summary):**
- Suggested separating domain logic from infrastructure using layered architecture
- Recommended Factory pattern for event creation
- Recommended Strategy pattern for priority calculation
- Recommended Observer pattern for team notifications
- Suggested a SimulationClock to avoid threading complexity in tests

**Modifications made by team:**
- AI suggested `domain/`, `service/`, `api/` packages. We changed to `core/`,
  `infrastructure/`, `ui/` to better reflect the project brief's emphasis on
  domain modelling
- AI suggested putting `TeamAssignmentService` in the domain layer. We moved it
  to `infrastructure/` because it orchestrates multiple domain objects and
  references the `TeamRegistry` — making it an infrastructure concern
- AI did not suggest the `SkillMapper` class; we added it to decouple
  `IncidentCategory → TeamSkill` mapping from `TeamAssignmentService`

**Motivation:**
The pattern suggestions were sound and matched the project spec. The package
structure adjustment reflects a more careful reading of what belongs in the
domain core vs infrastructure.

---

## Entry 2 — EventFactory and Reliability Rules

**Task:** Code generation

**Prompt sent to Ollama:**
```
Generate a Java EventFactory class with static factory methods for:
HumanReport, SensorEvent, VideoAnalyticsEvent, WearableAlert.
Sensors with battery below 5% should be marked UNRELIABLE.
Video events with confidence below 0.4 should be marked UNRELIABLE.
The class should be a utility class (not instantiable).
```

**AI Output (summary):**
- Generated `EventFactory` with four static methods
- Applied `markUnreliable()` inline in each factory method
- Added `private EventFactory() { }` constructor

**Modifications made by team:**
- AI placed battery and confidence threshold values as inline literals (e.g. `< 5.0`).
  We moved these to named constants on the respective event classes
  (`SensorEvent.LOW_BATTERY_THRESHOLD`, `VideoAnalyticsEvent.LOW_CONFIDENCE_THRESHOLD`)
  to avoid duplication and satisfy the PMD `AvoidLiteralsInIfCondition` rule
- AI did not add `final` to the class declaration. After running PMD, we added
  `public final class EventFactory` to fix the
  `ClassWithOnlyPrivateConstructorsShouldBeFinal` violation
- AI generated javadoc comments that referenced internal implementation details.
  We rewrote the javadoc to focus on behaviour and spec references

**Motivation:**
Keeping threshold constants on the event classes that own those concepts improves
cohesion. If the battery threshold changes, only `SensorEvent` must be updated —
not the factory and every other reference.

---

## Entry 3 — CorrelationEngine Logic

**Task:** Code generation

**Prompt sent to Ollama:**
```
Implement a Java CorrelationEngine class. It should decide whether a new event
belongs to an existing open incident or creates a new one. Use three criteria:
1. Euclidean distance <= 200 units (2D Cartesian coordinates)
2. Time delta <= 15 minutes
3. Same IncidentCategory

Return the first matching incident or null if none found.
```

**AI Output (summary):**
- Generated `findMatch()` using an enhanced for loop with three boolean conditions
- Used `incident.getCreatedAtMinutes()` for the temporal filter

**Modifications made by team:**
- Changed `getCreatedAtMinutes()` to `getLastUpdatedMinutes()` in the temporal
  filter. The AI used creation time, which would incorrectly reject events for
  incidents that were created long ago but recently updated (e.g. a fire that
  has been burning for 30 minutes still needs to accept new corroborating events).
  This was a logical error in the AI output that we caught during scenario testing.
- Added a check to also match `ASSIGNED` incidents (not just `OPEN`). The AI's
  version would create a new incident even when a matching assigned incident exists,
  splitting what should be one incident into two.
- Added the `UNKNOWN` category exception — events categorised as UNKNOWN should
  match any incident to avoid creating orphan incidents.

**Motivation:**
The `lastUpdated` vs `createdAt` bug is the most important fix. It directly affects
Scenario F (escalation): a LOW incident created at t=0 receiving a new event at t=5
must still correlate. Using creation time would have caused Scenario F to create two
incidents instead of merging.

---

## Entry 4 — Priority Scoring Model

**Task:** Design assistance

**Prompt sent to Ollama:**
```
I need a scoring model for emergency incident priority in Java.
Inputs:
- HumanReport: severity 1-10 (subjective)
- SensorEvent: ppm or celsius with thresholds
- VideoAnalyticsEvent: confidence 0.0-1.0
- WearableAlert: manual trigger or biometric

Output: LOW, MEDIUM, HIGH, or CRITICAL

Requirements:
- Multiple corroborating sources should escalate priority
- Low-reliability data should contribute less
- Single sensor alert should reach at least MEDIUM
```

**AI Output (summary):**
- Proposed per-event `getRawScore()` method returning 0-100
- Human reports: severity/10 * 30 (max 30)
- Sensors: banded scoring 10/20/30 based on thresholds
- Video: 25 * confidence (max 25)
- Corroboration bonus: +10 per extra source
- Thresholds: LOW < 25, MEDIUM 25-49, HIGH 50-74, CRITICAL >= 75

**Modifications made by team:**
- Increased corroboration bonus from +10 to +15. With +10, Scenario F
  (severity-2 + severity-10 + corroboration) scored 44, landing at MEDIUM
  instead of HIGH. With +15 the score is 51 → HIGH, correctly satisfying the spec.
- Lowered MEDIUM threshold from 25 to 20. With threshold 25, a single smoke sensor
  at 450ppm (score=20) would be LOW, failing Scenario B which requires >= MEDIUM.
  Threshold 20 makes score 20 exactly hit MEDIUM.
- Verified all thresholds manually against every scenario before finalising.

**Motivation:**
The AI's initial thresholds were not calibrated against the mandatory scenarios.
We derived the correct thresholds by working backwards from the spec requirements:
Scenario B forces MEDIUM threshold ≤ 20, Scenario F forces corroboration bonus ≥ 15.

---

## Entry 5 — Observer Pattern Implementation

**Task:** Code generation

**Prompt sent to Ollama:**
```
Implement Scenario E in Java using the Observer pattern:
When a ResponseTeam transitions to AVAILABLE, any pending queued incidents should
be automatically assigned to that team if it has the required skills.
The ResponseTeam class must not directly depend on TeamAssignmentService.
```

**AI Output (summary):**
- Generated `TeamAvailabilityObserver` interface with `onTeamAvailable()`
- `ResponseTeam.setState(AVAILABLE)` calls `notifyObservers()`
- `TeamAssignmentService implements TeamAvailabilityObserver`
- `onTeamAvailable()` iterates the pending queue and assigns the first match

**Modifications made by team:**
- Added a duplicate-check guard in `assignTeam()`. The AI's version would add
  the same incident to `pendingQueue` multiple times if `processEvent()` was
  called for multiple merged events on the same incident. This would cause
  Scenario E's `pendingQueue.size() == 1` assertion to fail.
- Changed observer registration: in the AI's version, observers were registered
  inside `TeamRegistry.register()`. We moved registration to `RescueNetSystem
  .registerTeam()` so that teams added at any time (before or after service
  creation) always get the observer attached correctly.
- The AI's `onTeamAvailable()` used an iterator with `remove()`. We changed to
  index-based removal to be clearer and avoid `ConcurrentModificationException`.

**Motivation:**
The duplicate-queue bug would have been invisible in simple tests but would have
caused Scenario E to fail in the automated test suite. The registration timing fix
ensures correctness regardless of the order in which teams and services are created.

---

## Entry 6 — JUnit Test Generation

**Task:** Test generation

**Prompt sent to Ollama:**
```
Generate JUnit 5 tests for the RescueNet system covering all 7 mandatory scenarios
(A through G). Use @Nested classes, @DisplayName, and AssertJ assertions.
Tests must be fully automated with no manual input.
Include edge cases for:
- Invalid severity values
- Events outside the correlation time window
- Incompatible incident categories
- Team state machine invalid transitions
```

**AI Output (summary):**
- Generated 7 nested test classes
- Used `assertThat(x).isIn(HIGH, CRITICAL)` for multi-value expectations
- Included basic state machine and correlation boundary tests

**Modifications made by team:**
- Added `sensorAtBoundaryBatteryIsReliable` test (battery exactly 5.0% → RELIABLE).
  The AI only tested values clearly below the threshold, missing the boundary case.
- Added `incidentIsNotQueuedTwice` test for Scenario E. The AI did not test that
  the pending queue deduplicates, which is a critical correctness requirement.
- Added `incompatibleCategoriesDontMerge` test (FIRE vs INTRUSION in same location).
  The AI did not include category-mismatch correlation tests.
- Fixed several tests where the AI used `system.getIncidents().get(0)` without
  first asserting that the list is non-empty, which would throw an uncaught
  `IndexOutOfBoundsException` rather than a meaningful test failure.
- Added `@BeforeEach` reset logic to ensure test isolation.

**Motivation:**
Boundary tests and negative tests are where AI generation is consistently weakest.
All AI-generated tests were reviewed line by line and supplemented with cases that
probe the boundaries of each requirement.

---

## Entry 7 — PMD Refactoring

**Task:** Refactoring support

**Prompt sent to Ollama:**
```
I ran PMD 6.55.0 on my Java project and got these violations.
Explain each one and recommend a fix:

1. ClassWithOnlyPrivateConstructorsShouldBeFinal in EventFactory and SkillMapper
2. ConstructorCallsOverridableMethod in Incident constructor
3. UseLocaleWithCaseConversions in VideoAnalyticsEvent.labelToCategory()
4. DataClass in SensorEvent, VideoAnalyticsEvent, Incident
5. ShortVariable for x, y, id, dx, dy in Location, RawEvent, Incident, ResponseTeam
```

**AI Output (summary):**
- Add `final` to classes with only private constructors
- Replace `addEvent()` call in constructor with inlined logic
- Add `Locale.ROOT` to `toLowerCase()` call
- `DataClass` can be suppressed with `@SuppressWarnings` for domain models
- `ShortVariable` for mathematical notation like `x/y` is a false positive

**Modifications made by team:**
- Applied all five fixes exactly as suggested for violations 1-3
- Added `@SuppressWarnings("PMD.DataClass")` to all three flagged classes with
  inline comments explaining why the data-carrying pattern is intentional
- Documented violations 4 and 5 as justified suppressions in DESIGN.md rather
  than just suppressing them silently — the justification is part of the deliverable
- The AI suggested renaming `id` to `identifier` to fix `ShortVariable`. We
  disagreed: `id` is the universally accepted Java abbreviation (used in Spring,
  JPA, Hibernate, etc.) and renaming it would introduce non-standard naming.

**Motivation:**
The AI's suggestions were technically correct for violations 1-4. For violation 5
(ShortVariable), we applied engineering judgment over mechanical rule-following —
renaming `x` to `xCoordinate` in a Euclidean distance formula actively harms
readability.
