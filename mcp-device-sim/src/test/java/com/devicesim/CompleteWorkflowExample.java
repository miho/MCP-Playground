package com.devicesim;

import com.devicesim.data.CsvDataReader;
import com.devicesim.engine.DeviceSimulator;
import com.devicesim.model.DeviceState;
import com.devicesim.model.Location;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Complete workflow example demonstrating all components working together.
 * This is not a JUnit test but a runnable example showing typical usage.
 */
public class CompleteWorkflowExample {

    public static void main(String[] args) {
        try {
            runExample();
        } catch (Exception e) {
            System.err.println("Error running example: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void runExample() throws IOException {
        System.out.println("=== Device Simulation Complete Workflow Example ===\n");

        // Step 1: Read and filter locations from CSV
        System.out.println("Step 1: Loading locations from CSV file...");
        CsvDataReader csvReader = new CsvDataReader();

        // Check if sample file exists
        Path sampleFile = Paths.get("data/sample_locations.csv");
        if (!Files.exists(sampleFile)) {
            System.out.println("Sample CSV file not found. Creating temporary test data...");
            sampleFile = createTempCsvFile();
        }

        // Read headers
        List<String> headers = csvReader.getHeaders(sampleFile.toString());
        System.out.println("CSV Headers: " + headers);

        // Set up filters - only locations with area between 150-200 and circularity > 0.85
        Map<String, CsvDataReader.FilterCriteria> filters = new HashMap<>();
        filters.put("area", new CsvDataReader.FilterCriteria("area", 150.0, 200.0));
        filters.put("circularity", new CsvDataReader.FilterCriteria("circularity", 0.85, 1.0));

        List<Location> locations = csvReader.readLocations(
            sampleFile.toString(), "x", "y", filters
        );

        System.out.println("Loaded " + locations.size() + " locations (after filtering)");
        locations.forEach(loc -> System.out.printf(
            "  - %s at (%.2f, %.2f) - area: %.1f, circularity: %.2f%n",
            loc.getId(), loc.getX(), loc.getY(),
            loc.getProperty("area"), loc.getProperty("circularity")
        ));
        System.out.println();

        // Step 2: Create and configure the simulator
        System.out.println("Step 2: Setting up device simulator...");
        DeviceSimulator simulator = new DeviceSimulator(0, 0);
        simulator.setTargetLocations(locations);
        simulator.setSpeed(15.0); // 15 units per second
        simulator.setAcceleration(8.0); // 8 units per second²
        simulator.setAutoAdvance(true); // Automatically move to next target

        DeviceState initialState = simulator.getState();
        System.out.printf("Initial position: (%.2f, %.2f)%n", initialState.getX(), initialState.getY());
        System.out.printf("Max speed: %.2f units/sec, Acceleration: %.2f units/sec²%n",
            initialState.getMaxSpeed(), initialState.getAcceleration());
        System.out.println("Auto-advance: ENABLED");
        System.out.println();

        // Step 3: Simulate movement through all locations
        System.out.println("Step 3: Simulating device movement...");
        simulator.startMovement();

        int iteration = 0;
        int maxIterations = 1000;
        double timeStep = 0.05; // 50ms per iteration
        double totalTime = 0;

        while (iteration < maxIterations) {
            DeviceState currentState = simulator.getState();
            Location currentTarget = simulator.getCurrentTarget();

            // Print status every second
            if (iteration % 20 == 0) {
                System.out.printf("[%.2fs] Pos: (%.2f, %.2f), Speed: %.2f, Target: %s%n",
                    totalTime,
                    currentState.getX(), currentState.getY(),
                    currentState.getSpeed(),
                    currentTarget != null ? currentTarget.getId() : "NONE"
                );
            }

            // Update simulation
            simulator.update(timeStep);
            totalTime += timeStep;

            // Check if simulation is complete (all locations visited or no target)
            Location afterUpdateTarget = simulator.getCurrentTarget();
            if (afterUpdateTarget == null ||
                (simulator.getAllLocations().stream().allMatch(Location::isVisited))) {
                if (!currentState.isMoving()) {
                    System.out.println("\n>>> All targets visited! Simulation complete.");
                    break;
                }
            }

            iteration++;
        }

        // Step 4: Summary
        System.out.println("\nStep 4: Simulation Summary");
        System.out.println("=".repeat(50));
        System.out.printf("Total simulation time: %.2f seconds%n", totalTime);
        System.out.printf("Total iterations: %d%n", iteration);

        DeviceState finalState = simulator.getState();
        System.out.printf("Final position: (%.2f, %.2f)%n", finalState.getX(), finalState.getY());

        long visitedCount = simulator.getAllLocations().stream()
            .filter(Location::isVisited)
            .count();
        System.out.printf("Locations visited: %d / %d%n", visitedCount, locations.size());

        System.out.println("\nVisited locations:");
        simulator.getAllLocations().stream()
            .filter(Location::isVisited)
            .forEach(loc -> System.out.printf("  ✓ %s at (%.2f, %.2f)%n",
                loc.getId(), loc.getX(), loc.getY()));

        System.out.println("\n=== Example Complete ===");
    }

    private static Path createTempCsvFile() throws IOException {
        Path tempFile = Files.createTempFile("device_sim_", ".csv");
        String csvContent = """
            x,y,area,circularity,intensity,label
            10.5,20.3,150.2,0.85,128.5,cell_1
            25.7,30.1,180.8,0.92,145.3,cell_2
            40.2,15.6,120.5,0.78,110.2,cell_3
            55.8,45.2,175.3,0.88,135.7,cell_4
            70.1,25.9,165.4,0.81,120.8,cell_5
            85.3,50.7,190.7,0.90,142.1,cell_6
            """;
        Files.writeString(tempFile, csvContent);
        tempFile.toFile().deleteOnExit();
        return tempFile;
    }
}
