package com.rescuenet;

import com.rescuenet.core.event.*;
import com.rescuenet.core.incident.Incident;
import com.rescuenet.core.priority.PriorityLevel;
import com.rescuenet.core.team.ResponseTeam;
import com.rescuenet.core.team.TeamSkill;
import com.rescuenet.core.team.TeamState;
import com.rescuenet.infrastructure.RescueNetSystem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Full test suite covering all 7 mandatory validation scenarios
 * plus unit-level edge cases. Fully automated - no manual input.
 */
class RescueNetSystemTest {

    private static final Location BASE_LOC = new Location(100, 100);

    private static ResponseTeam makeFireTeam(String name, Location loc) {
        Set<TeamSkill> skills = new HashSet<>();
        skills.add(TeamSkill.FIRE);
        return new ResponseTeam(name, loc, skills, 4);
    }

    private static ResponseTeam makeMedicalTeam(String name, Location loc) {
        Set<TeamSkill> skills = new HashSet<>();
        skills.add(TeamSkill.MEDICAL);
        return new ResponseTeam(name, loc, skills, 4);
    }

    // =========================================================================
    // Scenario A - Human Report Baseline
    // =========================================================================

    @Nested
    @DisplayName("Scenario A - Human report baseline")
    class ScenarioA {

        @Test
        @DisplayName("FIRE report severity=6 creates incident with assigned priority")
        void humanReportCreatesIncident() {
            RescueNetSystem system = new RescueNetSystem();

            Incident result = system.submitEvent(EventFactory.humanReport(
                    BASE_LOC, 0, IncidentCategory.FIRE, 6, "Smoke from building"));

            assertThat(result).isNotNull();
            assertThat(system.getIncidents()).hasSize(1);
            assertThat(result.getPriority()).isNotNull();
        }

        @Test
        @DisplayName("Severity 10 produces priority >= MEDIUM")
        void highSeverityIsAtLeastMedium() {
            RescueNetSystem system = new RescueNetSystem();
            system.submitEvent(EventFactory.humanReport(
                    BASE_LOC, 0, IncidentCategory.FIRE, 10, "Massive fire"));

            assertThat(system.getIncidents().get(0).getPriority().ordinal())
                    .isGreaterThanOrEqualTo(PriorityLevel.MEDIUM.ordinal());
        }

        @Test
        @DisplayName("Severity 1 produces LOW priority")
        void lowSeverityIsLow() {
            RescueNetSystem system = new RescueNetSystem();
            system.submitEvent(EventFactory.humanReport(
                    BASE_LOC, 0, IncidentCategory.FIRE, 1, "Faint smell"));

            assertThat(system.getIncidents().get(0).getPriority())
                    .isEqualTo(PriorityLevel.LOW);
        }

        @Test
        @DisplayName("Severity out of range throws IllegalArgumentException")
        void invalidSeverityThrows() {
            assertThatThrownBy(() -> EventFactory.humanReport(
                    BASE_LOC, 0, IncidentCategory.FIRE, 11, "Bad"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // =========================================================================
    // Scenario B - Sensor Event Thresholds
    // =========================================================================

    @Nested
    @DisplayName("Scenario B - Sensor event thresholds")
    class ScenarioB {

        @Test
        @DisplayName("Smoke sensor at 450ppm creates incident with priority >= MEDIUM")
        void highSmokeSensorIsAtLeastMedium() {
            RescueNetSystem system = new RescueNetSystem();

            system.submitEvent(EventFactory.sensorEvent(
                    "S1", BASE_LOC, 0,
                    SensorEvent.SensorType.SMOKE, 450.0,
                    SensorEvent.SensorStatus.ALERT, 80.0));

            assertThat(system.getIncidents()).hasSize(1);
            assertThat(system.getIncidents().get(0).getPriority().ordinal())
                    .isGreaterThanOrEqualTo(PriorityLevel.MEDIUM.ordinal());
        }

        @Test
        @DisplayName("Sensor with OK status creates LOW priority incident")
        void okStatusSensorIsLow() {
            RescueNetSystem system = new RescueNetSystem();

            system.submitEvent(EventFactory.sensorEvent(
                    "S2", BASE_LOC, 0,
                    SensorEvent.SensorType.SMOKE, 50.0,
                    SensorEvent.SensorStatus.OK, 90.0));

            assertThat(system.getIncidents().get(0).getPriority())
                    .isEqualTo(PriorityLevel.LOW);
        }
    }

    // =========================================================================
    // Scenario C - Correlation
    // =========================================================================

    @Nested
    @DisplayName("Scenario C - Event correlation")
    class ScenarioC {

        @Test
        @DisplayName("3 corroborating events merge into one HIGH/CRITICAL incident")
        void threeSourcesMerge() {
            RescueNetSystem system = new RescueNetSystem();
            Location loc = new Location(300, 300);

            system.submitEvent(EventFactory.sensorEvent(
                    "S-C1", loc, 0,
                    SensorEvent.SensorType.SMOKE, 500.0,
                    SensorEvent.SensorStatus.ALERT, 90.0));

            system.advanceTime(3);
            system.submitEvent(EventFactory.humanReport(
                    new Location(310, 295), 3,
                    IncidentCategory.FIRE, 8, "Large fire"));

            system.advanceTime(2);
            system.submitEvent(EventFactory.videoEvent(
                    new Location(305, 305), 5, "fire", 0.95));

            assertThat(system.getIncidents()).hasSize(1);
            Incident incident = system.getIncidents().get(0);
            assertThat(incident.getEventCount()).isEqualTo(3);
            assertThat(incident.getPriority())
                    .isIn(PriorityLevel.HIGH, PriorityLevel.CRITICAL);
        }

        @Test
        @DisplayName("Events far apart in space create separate incidents")
        void distantEventsDontCorrelate() {
            RescueNetSystem system = new RescueNetSystem();

            system.submitEvent(EventFactory.sensorEvent(
                    "S-D1", new Location(0, 0), 0,
                    SensorEvent.SensorType.SMOKE, 500.0,
                    SensorEvent.SensorStatus.ALERT, 90.0));

            system.submitEvent(EventFactory.sensorEvent(
                    "S-D2", new Location(1000, 1000), 0,
                    SensorEvent.SensorType.SMOKE, 500.0,
                    SensorEvent.SensorStatus.ALERT, 90.0));

            assertThat(system.getIncidents()).hasSize(2);
        }

        @Test
        @DisplayName("Events outside time window create separate incidents")
        void temporallyDistantEventsDontCorrelate() {
            RescueNetSystem system = new RescueNetSystem();
            Location loc = new Location(100, 100);

            system.submitEvent(EventFactory.sensorEvent(
                    "S-T1", loc, 0,
                    SensorEvent.SensorType.SMOKE, 500.0,
                    SensorEvent.SensorStatus.ALERT, 90.0));

            system.advanceTime(30);
            system.submitEvent(EventFactory.sensorEvent(
                    "S-T2", loc, 30,
                    SensorEvent.SensorType.SMOKE, 500.0,
                    SensorEvent.SensorStatus.ALERT, 90.0));

            assertThat(system.getIncidents()).hasSize(2);
        }

        @Test
        @DisplayName("Incompatible categories do not merge")
        void incompatibleCategoriesDontMerge() {
            RescueNetSystem system = new RescueNetSystem();
            Location loc = new Location(100, 100);

            system.submitEvent(EventFactory.sensorEvent(
                    "S-F1", loc, 0,
                    SensorEvent.SensorType.SMOKE, 500.0,
                    SensorEvent.SensorStatus.ALERT, 90.0));

            system.submitEvent(EventFactory.sensorEvent(
                    "S-M1", new Location(105, 100), 0,
                    SensorEvent.SensorType.MOTION, 1.0,
                    SensorEvent.SensorStatus.ALERT, 90.0));

            assertThat(system.getIncidents()).hasSize(2);
        }
    }

    // =========================================================================
    // Scenario D - Team Assignment Happy Path
    // =========================================================================

    @Nested
    @DisplayName("Scenario D - Team assignment happy path")
    class ScenarioD {

        @Test
        @DisplayName("High-priority incident with available fire team -> EN_ROUTE")
        void highPriorityAssignsTeam() {
            RescueNetSystem system = new RescueNetSystem();
            ResponseTeam fireTeam = makeFireTeam("Alpha", new Location(290, 290));
            system.registerTeam(fireTeam);

            system.submitEvent(EventFactory.sensorEvent(
                    "S-H1", new Location(300, 300), 0,
                    SensorEvent.SensorType.SMOKE, 800.0,
                    SensorEvent.SensorStatus.ALERT, 85.0));
            system.advanceTime(2);
            system.submitEvent(EventFactory.humanReport(
                    new Location(305, 298), 2,
                    IncidentCategory.FIRE, 9, "Building fire"));

            assertThat(fireTeam.getState()).isEqualTo(TeamState.EN_ROUTE);
            assertThat(system.getIncidents().get(0).getStatus())
                    .isEqualTo(Incident.IncidentStatus.ASSIGNED);
        }

        @Test
        @DisplayName("Medical incident assigns medical team, not fire team")
        void medicalIncidentAssignsMedicalTeam() {
            RescueNetSystem system = new RescueNetSystem();
            ResponseTeam fireTeam   = makeFireTeam("Fire",   BASE_LOC);
            ResponseTeam medTeam    = makeMedicalTeam("Med", BASE_LOC);
            system.registerTeam(fireTeam);
            system.registerTeam(medTeam);

            system.submitEvent(EventFactory.wearableAlert(
                    "W-01", BASE_LOC, 0, true, 180));

            assertThat(medTeam.getState()).isEqualTo(TeamState.EN_ROUTE);
            assertThat(fireTeam.getState()).isEqualTo(TeamState.AVAILABLE);
        }
    }

    // =========================================================================
    // Scenario E - Resource Exhaustion / Queuing
    // =========================================================================

    @Nested
    @DisplayName("Scenario E - Resource exhaustion and queuing")
    class ScenarioE {

        @Test
        @DisplayName("No team available -> queued; team available -> auto-assigned")
        void noTeamQueuesAndAutoAssigns() {
            RescueNetSystem system = new RescueNetSystem();
            ResponseTeam fireTeam = makeFireTeam("Bravo", new Location(50, 50));
            fireTeam.setState(TeamState.UNAVAILABLE);
            system.registerTeam(fireTeam);

            system.submitEvent(EventFactory.sensorEvent(
                    "S-E1", new Location(400, 400), 0,
                    SensorEvent.SensorType.SMOKE, 800.0,
                    SensorEvent.SensorStatus.ALERT, 75.0));
            system.advanceTime(1);
            system.submitEvent(EventFactory.humanReport(
                    new Location(405, 398), 1,
                    IncidentCategory.FIRE, 9, "Warehouse fire"));

            assertThat(system.getPendingQueue()).hasSize(1);
            assertThat(system.getIncidents().get(0).getStatus())
                    .isEqualTo(Incident.IncidentStatus.OPEN);

            fireTeam.setState(TeamState.AVAILABLE);

            assertThat(system.getPendingQueue()).isEmpty();
            assertThat(system.getIncidents().get(0).getStatus())
                    .isEqualTo(Incident.IncidentStatus.ASSIGNED);
            assertThat(fireTeam.getState()).isEqualTo(TeamState.EN_ROUTE);
        }

        @Test
        @DisplayName("Incident is not queued twice on repeated events")
        void incidentNotQueuedTwice() {
            RescueNetSystem system = new RescueNetSystem();
            // No teams registered

            system.submitEvent(EventFactory.sensorEvent(
                    "S-E2", new Location(200, 200), 0,
                    SensorEvent.SensorType.SMOKE, 800.0,
                    SensorEvent.SensorStatus.ALERT, 90.0));
            system.advanceTime(2);
            system.submitEvent(EventFactory.humanReport(
                    new Location(202, 200), 2,
                    IncidentCategory.FIRE, 8, "More smoke"));

            assertThat(system.getPendingQueue()).hasSize(1);
        }
    }

    // =========================================================================
    // Scenario F - Priority Escalation
    // =========================================================================

    @Nested
    @DisplayName("Scenario F - Priority escalation")
    class ScenarioF {

        @Test
        @DisplayName("LOW incident escalates to HIGH/CRITICAL and dispatches team")
        void priorityEscalatesAndDispatches() {
            RescueNetSystem system = new RescueNetSystem();
            ResponseTeam fireTeam = makeFireTeam("Charlie", new Location(490, 490));
            system.registerTeam(fireTeam);

            system.submitEvent(EventFactory.humanReport(
                    new Location(500, 500), 0,
                    IncidentCategory.FIRE, 2, "Faint smell"));

            Incident incident = system.getIncidents().get(0);
            assertThat(incident.getPriority()).isEqualTo(PriorityLevel.LOW);
            assertThat(fireTeam.getState()).isEqualTo(TeamState.AVAILABLE);

            system.advanceTime(5);
            system.submitEvent(EventFactory.humanReport(
                    new Location(502, 498), 5,
                    IncidentCategory.FIRE, 10, "EXPLOSION"));

            assertThat(incident.getPriority())
                    .isIn(PriorityLevel.HIGH, PriorityLevel.CRITICAL);
            assertThat(fireTeam.getState()).isEqualTo(TeamState.EN_ROUTE);
        }
    }

    // =========================================================================
    // Scenario G - Fault Tolerance
    // =========================================================================

    @Nested
    @DisplayName("Scenario G - Fault tolerance")
    class ScenarioG {

        @Test
        @DisplayName("Sensor battery < 5% is UNRELIABLE and no incident created")
        void lowBatterySensorIgnored() {
            RescueNetSystem system = new RescueNetSystem();

            RawEvent badSensor = EventFactory.sensorEvent(
                    "DEAD-S1", BASE_LOC, 0,
                    SensorEvent.SensorType.SMOKE, 999.0,
                    SensorEvent.SensorStatus.ALERT, 3.0);

            assertThat(badSensor.getReliability()).isEqualTo(EventReliability.UNRELIABLE);
            assertThat(system.submitEvent(badSensor)).isNull();
            assertThat(system.getIncidents()).isEmpty();
        }

        @Test
        @DisplayName("Sensor at exactly 5% battery is RELIABLE")
        void boundaryBatteryIsReliable() {
            RawEvent sensor = EventFactory.sensorEvent(
                    "BORDER-S1", BASE_LOC, 0,
                    SensorEvent.SensorType.SMOKE, 500.0,
                    SensorEvent.SensorStatus.ALERT, 5.0);

            assertThat(sensor.getReliability()).isEqualTo(EventReliability.RELIABLE);
        }

        @Test
        @DisplayName("Video confidence < 0.4 is UNRELIABLE and no incident created")
        void lowConfidenceVideoIgnored() {
            RescueNetSystem system = new RescueNetSystem();

            RawEvent lowConf = EventFactory.videoEvent(BASE_LOC, 0, "fire", 0.2);

            assertThat(lowConf.getReliability()).isEqualTo(EventReliability.UNRELIABLE);
            assertThat(system.submitEvent(lowConf)).isNull();
            assertThat(system.getIncidents()).isEmpty();
        }
    }

    // =========================================================================
    // Unit - TeamState machine
    // =========================================================================

    @Nested
    @DisplayName("Unit - TeamState machine")
    class TeamStateMachineTests {

        @Test
        @DisplayName("Valid transition chain succeeds")
        void validTransitions() {
            ResponseTeam team = makeFireTeam("T1", BASE_LOC);
            team.setState(TeamState.EN_ROUTE);
            assertThat(team.getState()).isEqualTo(TeamState.EN_ROUTE);
            team.setState(TeamState.ON_SCENE);
            assertThat(team.getState()).isEqualTo(TeamState.ON_SCENE);
            team.setState(TeamState.AVAILABLE);
            assertThat(team.getState()).isEqualTo(TeamState.AVAILABLE);
        }

        @Test
        @DisplayName("Invalid transition throws IllegalStateException")
        void invalidTransitionThrows() {
            ResponseTeam team = makeFireTeam("T2", BASE_LOC);
            assertThatThrownBy(() -> team.setState(TeamState.ON_SCENE))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}
