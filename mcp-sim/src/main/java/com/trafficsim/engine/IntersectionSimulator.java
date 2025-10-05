package com.trafficsim.engine;

import com.trafficsim.model.*;

import java.util.*;

/**
 * Core simulation engine for a 4-way intersection.
 * Uses discrete-event simulation with Poisson arrivals.
 */
public class IntersectionSimulator {
    private static final double TIME_STEP = 0.1; // 10 Hz simulation
    private static final double SATURATION_FLOW = 1800.0; // vehicles per hour per lane
    private static final double MIN_HEADWAY = 2.0; // seconds between vehicles

    private final Random random;
    private final Map<Direction, Double> arrivalRates; // vehicles per minute
    private final Map<Direction, Queue<Vehicle>> queues;
    private final List<Vehicle> allVehicles;

    private SignalPlan currentPlan;
    private double simTime;
    private int currentPhaseIndex;
    private double phaseStartTime;
    private String currentSignalState; // "green", "yellow", "red"

    public IntersectionSimulator(long seed) {
        this.random = new Random(seed);
        this.arrivalRates = new EnumMap<>(Direction.class);
        this.queues = new EnumMap<>(Direction.class);
        this.allVehicles = new ArrayList<>();
        this.simTime = 0;
        this.currentPhaseIndex = 0;
        this.phaseStartTime = 0;
        this.currentSignalState = "green";

        // Initialize queues
        for (Direction dir : Direction.values()) {
            queues.put(dir, new LinkedList<>());
            arrivalRates.put(dir, 10.0); // default 10 veh/min
        }
    }

    public void reset(long seed, Map<String, Double> arrivals) {
        this.random.setSeed(seed);
        this.simTime = 0;
        this.currentPhaseIndex = 0;
        this.phaseStartTime = 0;
        this.currentSignalState = "green";

        // Clear queues and vehicles
        for (Queue<Vehicle> queue : queues.values()) {
            queue.clear();
        }
        allVehicles.clear();

        // Set arrival rates
        for (Map.Entry<String, Double> entry : arrivals.entrySet()) {
            Direction dir = Direction.fromCode(entry.getKey());
            arrivalRates.put(dir, entry.getValue());
        }
    }

    public void setSignalPlan(SignalPlan plan) {
        this.currentPlan = plan;
        this.currentPhaseIndex = 0;
        this.phaseStartTime = simTime;
    }

    public void setArrivalRates(Map<String, Double> arrivals) {
        for (Map.Entry<String, Double> entry : arrivals.entrySet()) {
            Direction dir = Direction.fromCode(entry.getKey());
            arrivalRates.put(dir, entry.getValue());
        }
    }

    public SimulationMetrics runSimulation(double durationSeconds) {
        if (currentPlan == null) {
            throw new IllegalStateException("Signal plan must be set before running simulation");
        }

        double endTime = simTime + durationSeconds;

        while (simTime < endTime) {
            // Generate arrivals
            generateArrivals();

            // Update signal state
            updateSignalState();

            // Process queues
            processQueues();

            simTime += TIME_STEP;
        }

        return calculateMetrics();
    }

    private void generateArrivals() {
        for (Direction dir : Direction.values()) {
            double rate = arrivalRates.get(dir) / 60.0; // convert to per second
            double lambda = rate * TIME_STEP;

            // Poisson process: P(arrival) = lambda for small time steps
            if (random.nextDouble() < lambda) {
                Vehicle vehicle = new Vehicle(dir, simTime);
                queues.get(dir).add(vehicle);
                allVehicles.add(vehicle);
            }
        }
    }

    private void updateSignalState() {
        List<Phase> phases = currentPlan.getPhases();
        Phase currentPhase = phases.get(currentPhaseIndex);

        double timeInPhase = simTime - phaseStartTime;
        double greenDuration = currentPhase.getGreenSeconds();
        double yellowDuration = currentPlan.getYellowSeconds();
        double allRedDuration = currentPlan.getAllRedSeconds();

        if (timeInPhase < greenDuration) {
            currentSignalState = "green";
        } else if (timeInPhase < greenDuration + yellowDuration) {
            currentSignalState = "yellow";
        } else if (timeInPhase < greenDuration + yellowDuration + allRedDuration) {
            currentSignalState = "red";
        } else {
            // Move to next phase
            currentPhaseIndex = (currentPhaseIndex + 1) % phases.size();
            phaseStartTime = simTime;
            currentSignalState = "green";
        }
    }

    private void processQueues() {
        if (!"green".equals(currentSignalState)) {
            return;
        }

        Phase currentPhase = currentPlan.getPhases().get(currentPhaseIndex);
        Set<Direction> greenDirections = getGreenDirections(currentPhase);

        for (Direction dir : greenDirections) {
            Queue<Vehicle> queue = queues.get(dir);
            if (!queue.isEmpty()) {
                Vehicle vehicle = queue.peek();

                // Check if enough time has passed (saturation flow constraint)
                double dischargeRate = SATURATION_FLOW / 3600.0; // per second
                double expectedHeadway = 1.0 / dischargeRate;

                if (random.nextDouble() < dischargeRate * TIME_STEP) {
                    queue.poll();
                    vehicle.setDepartureTime(simTime);

                    // Count stop if vehicle had to wait
                    if (vehicle.getDelay() > 1.0) {
                        vehicle.incrementStops();
                    }
                }
            }
        }
    }

    private Set<Direction> getGreenDirections(Phase phase) {
        Set<Direction> green = new HashSet<>();
        String phaseName = phase.getName().toUpperCase();

        if (phaseName.contains("NS") || phaseName.contains("NORTH") || phaseName.contains("SOUTH")) {
            green.add(Direction.NORTH);
            green.add(Direction.SOUTH);
        }
        if (phaseName.contains("EW") || phaseName.contains("EAST") || phaseName.contains("WEST")) {
            green.add(Direction.EAST);
            green.add(Direction.WEST);
        }

        return green;
    }

    private SimulationMetrics calculateMetrics() {
        // Calculate average delay
        double totalDelay = allVehicles.stream()
                .filter(v -> v.getDepartureTime() > 0)
                .mapToDouble(Vehicle::getDelay)
                .sum();
        long departedVehicles = allVehicles.stream()
                .filter(v -> v.getDepartureTime() > 0)
                .count();
        double avgDelay = departedVehicles > 0 ? totalDelay / departedVehicles : 0;

        // Calculate queue P95
        List<Integer> queueSizes = new ArrayList<>();
        for (Queue<Vehicle> queue : queues.values()) {
            queueSizes.add(queue.size());
        }
        double maxQueue = queueSizes.stream().mapToInt(Integer::intValue).max().orElse(0);

        // Calculate throughput (vehicles per hour)
        double throughput = departedVehicles * 3600.0 / (simTime > 0 ? simTime : 1);

        // Calculate stops per vehicle
        double totalStops = allVehicles.stream()
                .filter(v -> v.getDepartureTime() > 0)
                .mapToInt(Vehicle::getStops)
                .sum();
        double stopsPerVeh = departedVehicles > 0 ? totalStops / departedVehicles : 0;

        return new SimulationMetrics(avgDelay, maxQueue, throughput, stopsPerVeh);
    }

    public Map<String, Object> getState() {
        Map<String, Object> state = new HashMap<>();
        state.put("simTime", simTime);
        state.put("currentPhase", currentPhaseIndex);
        state.put("signalState", currentSignalState);

        Map<String, Integer> queueLengths = new HashMap<>();
        for (Map.Entry<Direction, Queue<Vehicle>> entry : queues.entrySet()) {
            queueLengths.put(entry.getKey().getCode(), entry.getValue().size());
        }
        state.put("queueLengths", queueLengths);

        // Add arrival rates for use by DirectToolExecutor
        Map<String, Double> rates = new HashMap<>();
        for (Map.Entry<Direction, Double> entry : arrivalRates.entrySet()) {
            rates.put(entry.getKey().getCode(), entry.getValue());
        }
        state.put("arrivalRates", rates);

        return state;
    }

    public SignalPlan getCurrentPlan() {
        return currentPlan;
    }

    public double getSimTime() {
        return simTime;
    }

    public Map<Direction, Queue<Vehicle>> getQueues() {
        return queues;
    }

    public String getCurrentSignalState() {
        return currentSignalState;
    }

    public int getCurrentPhaseIndex() {
        return currentPhaseIndex;
    }
}
