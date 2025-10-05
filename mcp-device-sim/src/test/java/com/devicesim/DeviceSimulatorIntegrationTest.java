package com.devicesim;

import com.devicesim.data.CsvDataReader;
import com.devicesim.engine.DeviceSimulator;
import com.devicesim.model.DeviceState;
import com.devicesim.model.Location;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test demonstrating the complete device simulation workflow.
 */
class DeviceSimulatorIntegrationTest {

    private DeviceSimulator simulator;
    private List<Location> testLocations;

    @BeforeEach
    void setUp() {
        simulator = new DeviceSimulator(0, 0);

        // Create test locations (starting from 5,0 so we can see movement)
        testLocations = Arrays.asList(
            new Location("loc1", 5, 0, createProperties(100.0, 0.8)),
            new Location("loc2", 10, 0, createProperties(150.0, 0.9)),
            new Location("loc3", 10, 10, createProperties(200.0, 0.85)),
            new Location("loc4", 0, 10, createProperties(120.0, 0.75))
        );
    }

    @Test
    void testBasicMovement() {
        // Set up locations
        simulator.setTargetLocations(testLocations);
        simulator.setSpeed(10.0);
        simulator.setAcceleration(5.0);

        // Start movement
        simulator.startMovement();

        // Verify initial state
        DeviceState initialState = simulator.getState();
        assertEquals(0.0, initialState.getX(), 0.01);
        assertEquals(0.0, initialState.getY(), 0.01);
        assertTrue(initialState.isMoving());

        // Simulate for 2 seconds
        for (int i = 0; i < 20; i++) {
            simulator.update(0.1); // 100ms steps
        }

        // Device should have moved
        DeviceState afterMovement = simulator.getState();
        assertTrue(afterMovement.getX() > 0 || afterMovement.getY() > 0,
                "Device should have moved from origin");
    }

    @Test
    void testTargetReaching() {
        List<Location> singleTarget = Collections.singletonList(
            new Location("target", 5, 0)
        );

        simulator.setTargetLocations(singleTarget);
        simulator.setSpeed(10.0);
        simulator.setAcceleration(10.0);
        simulator.startMovement();

        // Simulate until target is reached
        int maxIterations = 200;
        int iterations = 0;
        while (simulator.getState().isMoving() && iterations < maxIterations) {
            simulator.update(0.01);
            iterations++;
        }

        // Verify target was reached
        DeviceState finalState = simulator.getState();
        assertFalse(finalState.isMoving(), "Device should have stopped");
        assertEquals(5.0, finalState.getX(), 0.6, "Should be at target X");
        assertEquals(0.0, finalState.getY(), 0.6, "Should be at target Y");
        assertTrue(singleTarget.get(0).isVisited(), "Target should be marked as visited");
    }

    @Test
    void testLocationProperties() {
        Location loc = testLocations.get(0);

        assertEquals(100.0, loc.getProperty("area"));
        assertEquals(0.8, loc.getProperty("circularity"));

        loc.setProperty("newProp", "testValue");
        assertEquals("testValue", loc.getProperty("newProp"));
    }

    @Test
    void testCsvFilterCriteria() {
        CsvDataReader.FilterCriteria rangeFilter =
            new CsvDataReader.FilterCriteria("area", 100.0, 200.0);

        Map<String, Object> properties1 = createProperties(150.0, 0.8);
        assertTrue(rangeFilter.passes(properties1), "150 should be in range [100, 200]");

        Map<String, Object> properties2 = createProperties(250.0, 0.8);
        assertFalse(rangeFilter.passes(properties2), "250 should be outside range [100, 200]");

        CsvDataReader.FilterCriteria equalsFilter =
            new CsvDataReader.FilterCriteria("circularity", 0.8);

        assertTrue(equalsFilter.passes(properties1), "Should match exact value 0.8");

        Map<String, Object> properties3 = createProperties(250.0, 0.9);
        assertFalse(equalsFilter.passes(properties3), "Should not match different value (0.9 != 0.8)");
    }

    @Test
    void testDeviceStateImmutability() {
        DeviceState state1 = new DeviceState(0, 0);
        DeviceState state2 = state1.withPosition(10, 10);

        // Original should be unchanged
        assertEquals(0.0, state1.getX(), 0.01);
        assertEquals(0.0, state1.getY(), 0.01);

        // New state should have new values
        assertEquals(10.0, state2.getX(), 0.01);
        assertEquals(10.0, state2.getY(), 0.01);
    }

    @Test
    void testMultipleTargets() {
        simulator.setTargetLocations(testLocations);
        simulator.setAutoAdvance(true);
        simulator.setSpeed(20.0);
        simulator.setAcceleration(10.0);
        simulator.startMovement();

        // Simulate for a while
        for (int i = 0; i < 500; i++) {
            simulator.update(0.05);
        }

        // Check that at least one target was visited
        long visitedCount = testLocations.stream()
            .filter(Location::isVisited)
            .count();

        assertTrue(visitedCount > 0, "At least one target should be visited");
    }

    @Test
    void testAccelerationAndDeceleration() {
        List<Location> farTarget = Collections.singletonList(
            new Location("far", 100, 0)
        );

        simulator.setTargetLocations(farTarget);
        simulator.setSpeed(10.0);
        simulator.setAcceleration(5.0);
        simulator.startMovement();

        double previousSpeed = 0;
        boolean accelerated = false;
        boolean decelerated = false;

        for (int i = 0; i < 500; i++) {
            simulator.update(0.05);
            DeviceState state = simulator.getState();

            if (state.getSpeed() > previousSpeed) {
                accelerated = true;
            }
            if (state.getSpeed() < previousSpeed && previousSpeed > 0) {
                decelerated = true;
            }

            previousSpeed = state.getSpeed();

            if (!state.isMoving()) {
                break;
            }
        }

        assertTrue(accelerated, "Device should have accelerated");
        assertTrue(decelerated || !simulator.getState().isMoving(),
                "Device should have decelerated or stopped");
    }

    private Map<String, Object> createProperties(double area, double circularity) {
        Map<String, Object> props = new HashMap<>();
        props.put("area", area);
        props.put("circularity", circularity);
        return props;
    }
}
