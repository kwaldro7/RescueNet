# RescueNet — Execution Instructions

Emergency Smart Coordination System — Java 11 · Maven 3.6+

---

## Prerequisites
| Java (JDK) | 11 or higher | `java -version` |
| Maven | 3.6 or higher | `mvn -version` |

---

## Project Structure

```
RescueNet/
├── pom.xml
├── README.md
├── DESIGN.md
├── ai_log.md
└── src/
    ├── main/java/com/rescuenet/
    │   ├── core/
    │   │   ├── event/          RawEvent, HumanReport, SensorEvent,
    │   │   │                   VideoAnalyticsEvent, WearableAlert,
    │   │   │                   EventFactory, Location, enums
    │   │   ├── incident/       Incident, CorrelationEngine
    │   │   ├── priority/       PriorityLevel, PriorityStrategy,
    │   │   │                   CompositePriorityStrategy
    │   │   ├── team/           ResponseTeam, TeamSkill, TeamState,
    │   │   │                   TeamAvailabilityObserver
    │   │   └── assignment/     PendingAssignment, SkillMapper
    │   ├── infrastructure/     RescueNetSystem, IncidentManager,
    │   │                       TeamRegistry, TeamAssignmentService,
    │   │                       SimulationClock
    │   └── ui/
    │       └── Main.java       CLI scenario runner
    └── test/java/com/rescuenet/
        └── RescueNetSystemTest.java
```

---

## Build

```bash
# Compile all source files
mvn compile
```

Expected output: `BUILD SUCCESS`

---

## Run All Tests (20 JUnit 5 tests covering all 7 scenarios)

```bash
mvn test
```

Expected output:
```
Tests run: 20, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

All 7 mandatory scenarios (A through G) are covered by the automated test suite.
No manual input is required at any step.

---

## Run the CLI Scenario Runner

```bash
# Build the executable JAR
mvn package -DskipTests

# Run the CLI (Windows)
java -jar target\rescuenet-1.0.0.jar


The CLI runs all 7 scenarios in sequence and prints results to stdout.
No manual input is required.

---

## Run PMD Code Analysis

```bash
# Generate PMD XML report
mvn pmd:pmd

Reports are written to:
- `target/site/pmd.html`

---

## Sample Input Data for Each Scenario

The following code shows the programmatic inputs used for each mandatory scenario.
These are also executed automatically by `mvn test` and the CLI runner.

### Scenario A — Human Report Baseline

```java
RescueNetSystem system = new RescueNetSystem();

RawEvent report = EventFactory.humanReport(
    new Location(100, 100),       // location on 2D grid
    0,                            // timestamp (simulation minutes)
    IncidentCategory.FIRE,        // type of incident
    6,                            // severity 1-10
    "Smoke coming from building"  // description
);

Incident result = system.submitEvent(report);
// Expected: result != null, incident created, priority assigned
```

### Scenario B — Sensor Event (Threshold)

```java
RescueNetSystem system = new RescueNetSystem();

RawEvent sensor = EventFactory.sensorEvent(
    "SMOKE-01",                   // sensor ID
    new Location(200, 150),       // location
    0,                            // timestamp
    SensorEvent.SensorType.SMOKE, // sensor type
    450.0,                        // measured value (ppm) — above medium threshold
    SensorEvent.SensorStatus.ALERT,
    80.0                          // battery level % — healthy
);

Incident result = system.submitEvent(sensor);
// Expected: priority >= MEDIUM (score = 20, threshold = 20)
```

### Scenario C — Correlation (3 sources → 1 incident)

```java
RescueNetSystem system = new RescueNetSystem();
Location loc = new Location(300, 300);

// Event 1: smoke sensor
system.submitEvent(EventFactory.sensorEvent(
    "SMOKE-02", loc, 0,
    SensorEvent.SensorType.SMOKE, 500.0,
    SensorEvent.SensorStatus.ALERT, 90.0));

system.advanceTime(3);  // move clock forward 3 minutes

// Event 2: citizen report at nearby location
system.submitEvent(EventFactory.humanReport(
    new Location(310, 295), 3,    // 11 units from epicenter — within 200
    IncidentCategory.FIRE, 8, "Large fire visible"));

system.advanceTime(2);  // clock now at 5 minutes

// Event 3: video analytics
system.submitEvent(EventFactory.videoEvent(
    new Location(305, 305), 5, "fire", 0.95));

// Expected: 1 incident, 3 events, priority HIGH or CRITICAL
// Score: smoke(20) + human8(24) + video0.95(24) + 2*corroboration(30) = 98 → CRITICAL
```

### Scenario D — Team Assignment (Happy Path)

```java
RescueNetSystem system = new RescueNetSystem();

// Register a FIRE team near the incident location
Set<TeamSkill> skills = new HashSet<>();
skills.add(TeamSkill.FIRE);
ResponseTeam fireTeam = new ResponseTeam(
    "Alpha Fire Unit",
    new Location(290, 290),  // close to incident
    skills, 4);
system.registerTeam(fireTeam);

// Create HIGH priority incident
system.submitEvent(EventFactory.sensorEvent(
    "SMOKE-03", new Location(300, 300), 0,
    SensorEvent.SensorType.SMOKE, 800.0,
    SensorEvent.SensorStatus.ALERT, 85.0));
system.advanceTime(2);
system.submitEvent(EventFactory.humanReport(
    new Location(305, 298), 2, IncidentCategory.FIRE, 9, "Building on fire"));

// Expected: fireTeam.getState() == EN_ROUTE
//           incident.getStatus() == ASSIGNED
```

### Scenario E — Resource Exhaustion (Queuing)

```java
RescueNetSystem system = new RescueNetSystem();

// Register a FIRE team that is currently UNAVAILABLE
Set<TeamSkill> skills = new HashSet<>();
skills.add(TeamSkill.FIRE);
ResponseTeam fireTeam = new ResponseTeam(
    "Bravo Fire Unit", new Location(50, 50), skills, 4);
fireTeam.setState(TeamState.UNAVAILABLE);  // mark unavailable BEFORE registering
system.registerTeam(fireTeam);

// Create HIGH priority incident — no team available
system.submitEvent(EventFactory.sensorEvent(
    "SMOKE-04", new Location(400, 400), 0,
    SensorEvent.SensorType.SMOKE, 800.0,
    SensorEvent.SensorStatus.ALERT, 75.0));
system.advanceTime(1);
system.submitEvent(EventFactory.humanReport(
    new Location(405, 398), 1, IncidentCategory.FIRE, 9, "Warehouse fire"));

// At this point: pendingQueue.size() == 1, incident.status == OPEN

// Simulate team becoming available (e.g. returns from another scene)
fireTeam.setState(TeamState.AVAILABLE);

// Observer fires automatically:
// Expected: pendingQueue.size() == 0
//           incident.status == ASSIGNED
//           fireTeam.getState() == EN_ROUTE
```

### Scenario F — Priority Escalation

```java
RescueNetSystem system = new RescueNetSystem();

// Register a fire team
Set<TeamSkill> skills = new HashSet<>();
skills.add(TeamSkill.FIRE);
ResponseTeam fireTeam = new ResponseTeam(
    "Charlie Fire Unit", new Location(490, 490), skills, 4);
system.registerTeam(fireTeam);

// LOW priority start: severity 2 = 6 pts → LOW, no dispatch
system.submitEvent(EventFactory.humanReport(
    new Location(500, 500), 0,
    IncidentCategory.FIRE, 2, "Possible faint smell"));

// At this point: priority == LOW, team == AVAILABLE

system.advanceTime(5);

// Escalation: severity 10 at same location within time window
// Score: 6 + 30 + 15(corroboration) = 51 → HIGH
system.submitEvent(EventFactory.humanReport(
    new Location(502, 498), 5,
    IncidentCategory.FIRE, 10, "EXPLOSION — building engulfed"));

// Expected: incident.priority == HIGH or CRITICAL
//           fireTeam.getState() == EN_ROUTE
```

### Scenario G — Fault Tolerance (Unreliable Sensor)

```java
RescueNetSystem system = new RescueNetSystem();

// Sensor with critically high reading but critically low battery (3%)
RawEvent badSensor = EventFactory.sensorEvent(
    "DEAD-S1",
    new Location(600, 600),
    0,
    SensorEvent.SensorType.SMOKE,
    999.0,                          // extreme reading
    SensorEvent.SensorStatus.ALERT,
    3.0                             // battery = 3% — below 5% threshold
);

// EventFactory marks it UNRELIABLE immediately
// badSensor.getReliability() == UNRELIABLE

Incident result = system.submitEvent(badSensor);
// Expected: result == null (event dropped)
//           system.getIncidents().isEmpty() == true
//           No incident created
```

---

## Run a Single Test Class

```bash
# Run only the full scenario test suite
mvn test -Dtest=RescueNetSystemTest

# Run a specific scenario nested class
mvn test -Dtest="RescueNetSystemTest\$ScenarioC"
```

---

## Clean Build Artifacts

```bash
mvn clean
```

---

## Full Build + Test + Package (one command)

```bash
mvn clean package
```

This compiles, runs all 20 tests, and builds the executable JAR.
The JAR is written to `target/rescuenet-1.0.0.jar`.
