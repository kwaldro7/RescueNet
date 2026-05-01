package com.rescuenet.ui;

import com.rescuenet.core.event.*;
import com.rescuenet.core.incident.Incident;
import com.rescuenet.core.team.ResponseTeam;
import com.rescuenet.core.team.TeamSkill;
import com.rescuenet.core.team.TeamState;
import com.rescuenet.infrastructure.RescueNetSystem;

import java.util.HashSet;
import java.util.Set;

/**
 * Command-line entry point.
 * Runs all 7 mandatory validation scenarios deterministically.
 * No manual input required.
 */
public final class Main {
    private Main() { }


    public static void main(String[] args) {
        System.out.println("==========================================");
        System.out.println("  RescueNet - Scenario Runner");
        System.out.println("==========================================\n");

        runScenarioA();
        runScenarioB();
        runScenarioC();
        runScenarioD();
        runScenarioE();
        runScenarioF();
        runScenarioG();
    }

    // Scenario A - Human Report Baseline
    private static void runScenarioA() {
        header("SCENARIO A - Human Report Baseline");
        RescueNetSystem system = new RescueNetSystem();

        RawEvent report = EventFactory.humanReport(
                new Location(100, 100), 0,
                IncidentCategory.FIRE, 6, "Smoke from building");

        Incident result = system.submitEvent(report);
        if (result != null) {
            System.out.println("  OK Incident created: " + result.getId()
                    + "  Priority: " + result.getPriority());
        }
        system.printStatus();
    }

    // Scenario B - Sensor Event Thresholds
    private static void runScenarioB() {
        header("SCENARIO B - Sensor Event (High Smoke)");
        RescueNetSystem system = new RescueNetSystem();

        RawEvent sensor = EventFactory.sensorEvent(
                "SMOKE-01", new Location(200, 150), 0,
                SensorEvent.SensorType.SMOKE, 450.0,
                SensorEvent.SensorStatus.ALERT, 80.0);

        Incident result = system.submitEvent(sensor);
        if (result != null) {
            System.out.println("  OK Incident created: " + result.getId()
                    + "  Priority: " + result.getPriority()
                    + " (expected >= MEDIUM)");
        }
        system.printStatus();
    }

    // Scenario C - Correlation
    private static void runScenarioC() {
        header("SCENARIO C - Correlation (3 sources -> 1 incident)");
        RescueNetSystem system = new RescueNetSystem();
        Location loc = new Location(300, 300);

        system.submitEvent(EventFactory.sensorEvent(
                "SMOKE-02", loc, 0,
                SensorEvent.SensorType.SMOKE, 500.0,
                SensorEvent.SensorStatus.ALERT, 90.0));

        system.advanceTime(3);
        system.submitEvent(EventFactory.humanReport(
                new Location(310, 295), 3,
                IncidentCategory.FIRE, 8, "Large fire"));

        system.advanceTime(2);
        system.submitEvent(EventFactory.videoEvent(
                new Location(305, 305), 5, "fire", 0.95));

        System.out.println("  Total incidents: " + system.getIncidents().size()
                + " (expected: 1)");
        for (Incident i : system.getIncidents()) {
            System.out.println("  OK " + i.getId()
                    + "  events=" + i.getEventCount()
                    + "  priority=" + i.getPriority());
        }
        system.printStatus();
    }

    // Scenario D - Team Assignment Happy Path
    private static void runScenarioD() {
        header("SCENARIO D - Team Assignment Happy Path");
        RescueNetSystem system = new RescueNetSystem();

        Set<TeamSkill> skills = new HashSet<>();
        skills.add(TeamSkill.FIRE);
        ResponseTeam fireTeam = new ResponseTeam(
                "Alpha Fire Unit", new Location(280, 280), skills, 4);
        system.registerTeam(fireTeam);

        system.submitEvent(EventFactory.sensorEvent(
                "SMOKE-03", new Location(300, 300), 0,
                SensorEvent.SensorType.SMOKE, 800.0,
                SensorEvent.SensorStatus.ALERT, 85.0));
        system.advanceTime(2);
        system.submitEvent(EventFactory.humanReport(
                new Location(305, 298), 2,
                IncidentCategory.FIRE, 9, "Building on fire"));

        System.out.println("  Fire team state: " + fireTeam.getState()
                + " (expected: EN_ROUTE)");
        system.printStatus();
    }

    // Scenario E - Resource Exhaustion / Queuing
    private static void runScenarioE() {
        header("SCENARIO E - Resource Exhaustion and Auto-Assignment");
        RescueNetSystem system = new RescueNetSystem();

        Set<TeamSkill> skills = new HashSet<>();
        skills.add(TeamSkill.FIRE);
        ResponseTeam fireTeam = new ResponseTeam(
                "Bravo Fire Unit", new Location(50, 50), skills, 4);
        fireTeam.setState(TeamState.UNAVAILABLE);
        system.registerTeam(fireTeam);

        system.submitEvent(EventFactory.sensorEvent(
                "SMOKE-04", new Location(400, 400), 0,
                SensorEvent.SensorType.SMOKE, 800.0,
                SensorEvent.SensorStatus.ALERT, 75.0));
        system.advanceTime(1);
        system.submitEvent(EventFactory.humanReport(
                new Location(405, 398), 1,
                IncidentCategory.FIRE, 9, "Warehouse fire"));

        System.out.println("  Pending queue: " + system.getPendingQueue().size()
                + " (expected: 1)");
        System.out.println("  Incident status: "
                + system.getIncidents().get(0).getStatus()
                + " (expected: OPEN)");

        System.out.println("  -> Team transitions to AVAILABLE...");
        fireTeam.setState(TeamState.AVAILABLE);

        System.out.println("  Pending queue: " + system.getPendingQueue().size()
                + " (expected: 0)");
        System.out.println("  Incident status: "
                + system.getIncidents().get(0).getStatus()
                + " (expected: ASSIGNED)");
        System.out.println("  Team state: " + fireTeam.getState()
                + " (expected: EN_ROUTE)");
        system.printStatus();
    }

    // Scenario F - Priority Escalation
    private static void runScenarioF() {
        header("SCENARIO F - Priority Escalation");
        RescueNetSystem system = new RescueNetSystem();

        Set<TeamSkill> skills = new HashSet<>();
        skills.add(TeamSkill.FIRE);
        ResponseTeam fireTeam = new ResponseTeam(
                "Charlie Fire Unit", new Location(500, 490), skills, 4);
        system.registerTeam(fireTeam);

        system.submitEvent(EventFactory.humanReport(
                new Location(500, 500), 0,
                IncidentCategory.FIRE, 2, "Possible smoke smell"));

        Incident incident = system.getIncidents().get(0);
        System.out.println("  Initial priority: " + incident.getPriority()
                + " (expected: LOW)");

        system.advanceTime(5);
        system.submitEvent(EventFactory.humanReport(
                new Location(502, 498), 5,
                IncidentCategory.FIRE, 10, "EXPLOSION - building engulfed"));

        System.out.println("  Escalated priority: " + incident.getPriority()
                + " (expected: HIGH or CRITICAL)");
        System.out.println("  Team state: " + fireTeam.getState()
                + " (expected: EN_ROUTE)");
        system.printStatus();
    }

    // Scenario G - Fault Tolerance
    private static void runScenarioG() {
        header("SCENARIO G - Fault Tolerance (Low Battery Sensor)");
        RescueNetSystem system = new RescueNetSystem();

        RawEvent badSensor = EventFactory.sensorEvent(
                "DEAD-S1", new Location(600, 600), 0,
                SensorEvent.SensorType.SMOKE, 999.0,
                SensorEvent.SensorStatus.ALERT, 3.0);

        System.out.println("  Event reliability: " + badSensor.getReliability()
                + " (expected: UNRELIABLE)");

        Incident result = system.submitEvent(badSensor);
        System.out.println("  Incident created: " + (result != null)
                + " (expected: false)");
        System.out.println("  Total incidents: " + system.getIncidents().size()
                + " (expected: 0)");
        system.printStatus();
    }

    private static void header(String title) {
        System.out.println("------------------------------------------");
        System.out.println(">> " + title);
        System.out.println("------------------------------------------");
    }
}
