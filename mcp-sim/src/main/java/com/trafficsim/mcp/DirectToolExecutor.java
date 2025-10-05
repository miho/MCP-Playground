package com.trafficsim.mcp;

import com.trafficsim.engine.IntersectionSimulator;
import com.trafficsim.model.Phase;
import com.trafficsim.model.SignalPlan;
import com.trafficsim.model.SimulationMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Execute MCP tools directly by calling IntersectionSimulator (no HTTP/RPC overhead).
 * This is used by the JavaFX UI for internal tool execution.
 * External MCP clients can still use the embedded MCP server for remote access.
 */
public class DirectToolExecutor {

    private static final Logger logger = LoggerFactory.getLogger(DirectToolExecutor.class);

    private final IntersectionSimulator simulator;
    private final ExecutorService executor;
    private volatile boolean cancelled = false;

    public DirectToolExecutor(IntersectionSimulator simulator) {
        this.simulator = simulator;
        this.executor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "DirectToolExecutor");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Execute a single tool directly.
     */
    public CompletableFuture<ToolResult> executeTool(String toolName, Map<String, Object> params) {
        return CompletableFuture.supplyAsync(() -> {
            if (cancelled) {
                logger.debug("Tool execution cancelled before start: {}", toolName);
                return ToolResult.cancelled();
            }

            try {
                logger.info("Executing tool: {} with params: {}", toolName, params);

                Object result = executeToolInternal(toolName, params);

                if (cancelled) {
                    logger.info("Tool execution cancelled after completion: {}", toolName);
                    return ToolResult.cancelled();
                }

                String resultMessage = result != null ? result.toString() : "Success";
                logger.info("Tool completed successfully: {} - {}", toolName, resultMessage);
                return ToolResult.success(resultMessage);

            } catch (Exception e) {
                logger.error("Tool execution failed: " + toolName, e);
                String errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                return ToolResult.error(errorMessage);
            }
        }, executor);
    }

    /**
     * Cancel ongoing execution.
     */
    public void cancel() {
        cancelled = true;
    }

    /**
     * Shutdown the executor service.
     */
    public void shutdown() {
        executor.shutdown();
    }

    /**
     * Route tool execution to the appropriate method.
     */
    private Object executeToolInternal(String toolName, Map<String, Object> params) throws Exception {
        return switch (toolName) {
            case "get_status" -> executeGetStatus(params);
            case "set_green_times" -> executeSetGreenTimes(params);
            case "intersection_reset" -> executeReset(params);
            case "intersection_evaluate_plan" -> executeEvaluatePlan(params);
            case "intersection_apply_plan" -> executeApplyPlan(params);
            case "intersection_get_state" -> executeGetState(params);
            default -> throw new IllegalArgumentException("Unknown tool: " + toolName);
        };
    }

    // ==================== TOOL IMPLEMENTATIONS ====================

    private Object executeReset(Map<String, Object> params) {
        long seed = getLongParam(params, "seed");

        @SuppressWarnings("unchecked")
        Map<String, Object> arrivalsObj = (Map<String, Object>) params.get("arrivals");
        Map<String, Double> arrivals = new HashMap<>();
        arrivals.put("N", getDoubleParam(arrivalsObj, "N"));
        arrivals.put("S", getDoubleParam(arrivalsObj, "S"));
        arrivals.put("E", getDoubleParam(arrivalsObj, "E"));
        arrivals.put("W", getDoubleParam(arrivalsObj, "W"));

        simulator.reset(seed, arrivals);

        return String.format(
                "Intersection reset successfully!\nSeed: %d\nArrivals: N=%.1f, S=%.1f, E=%.1f, W=%.1f veh/min",
                seed, arrivals.get("N"), arrivals.get("S"), arrivals.get("E"), arrivals.get("W"));
    }

    private Object executeEvaluatePlan(Map<String, Object> params) {
        @SuppressWarnings("unchecked")
        Map<String, Object> planObj = (Map<String, Object>) params.get("plan");
        SignalPlan plan = parsePlan(planObj);

        double duration = getDoubleParam(params, "durationSeconds", 120.0);
        int replications = getIntParam(params, "replications", 3);

        // Run multiple replications and average results
        double totalDelay = 0, totalQueue = 0, totalThroughput = 0, totalStops = 0;

        // Get current arrivals from simulator state
        Map<String, Object> state = simulator.getState();
        @SuppressWarnings("unchecked")
        Map<String, Double> currentArrivals = (Map<String, Double>) state.get("arrivalRates");

        long currentSeed = (long) (Math.random() * 100000);

        for (int i = 0; i < replications; i++) {
            IntersectionSimulator evalSim = new IntersectionSimulator(currentSeed + i);
            evalSim.reset(currentSeed + i, currentArrivals);
            evalSim.setSignalPlan(plan);
            SimulationMetrics metrics = evalSim.runSimulation(duration);

            totalDelay += metrics.getAvgDelaySec();
            totalQueue += metrics.getQueueP95();
            totalThroughput += metrics.getThroughputVph();
            totalStops += metrics.getStopsPerVeh();
        }

        Map<String, Double> avgMetrics = new HashMap<>();
        avgMetrics.put("avgDelaySec", totalDelay / replications);
        avgMetrics.put("queueP95", totalQueue / replications);
        avgMetrics.put("throughputVph", totalThroughput / replications);
        avgMetrics.put("stopsPerVeh", totalStops / replications);

        return String.format(
                "{\"metrics\": {\"avgDelaySec\": %.2f, \"queueP95\": %.1f, \"throughputVph\": %.0f, \"stopsPerVeh\": %.2f}}",
                avgMetrics.get("avgDelaySec"),
                avgMetrics.get("queueP95"),
                avgMetrics.get("throughputVph"),
                avgMetrics.get("stopsPerVeh"));
    }

    private Object executeApplyPlan(Map<String, Object> params) {
        @SuppressWarnings("unchecked")
        Map<String, Object> planObj = (Map<String, Object>) params.get("plan");
        SignalPlan plan = parsePlan(planObj);

        simulator.setSignalPlan(plan);

        return String.format(
                "Signal plan applied successfully!\nCycle: %.0fs, Phases: %d",
                plan.getCycleSeconds(), plan.getPhases().size());
    }

    private Object executeGetState(Map<String, Object> params) {
        Map<String, Object> state = simulator.getState();

        @SuppressWarnings("unchecked")
        Map<String, Integer> queueLengths = (Map<String, Integer>) state.get("queueLengths");

        return String.format(
                "Intersection State:\nTime: %.1fs\nPhase: %d\nSignal: %s\nQueues: N=%d, S=%d, E=%d, W=%d",
                (Double) state.get("simTime"),
                (Integer) state.get("currentPhase"),
                (String) state.get("signalState"),
                queueLengths.get("N"),
                queueLengths.get("S"),
                queueLengths.get("E"),
                queueLengths.get("W"));
    }

    private Object executeGetStatus(Map<String, Object> params) {
        // Get current signal plan
        SignalPlan plan = simulator.getCurrentPlan();
        double nsGreen = 0, ewGreen = 0;
        if (plan != null && plan.getPhases().size() >= 2) {
            nsGreen = plan.getPhases().get(0).getGreenSeconds();
            ewGreen = plan.getPhases().get(1).getGreenSeconds();
        }

        // Get current arrivals
        Map<String, Object> state = simulator.getState();
        @SuppressWarnings("unchecked")
        Map<String, Double> currentArrivals = (Map<String, Double>) state.get("arrivalRates");

        // Run quick evaluation to get metrics
        SimulationMetrics metrics = null;
        if (plan != null) {
            long seed = (long) (Math.random() * 100000);
            IntersectionSimulator evalSim = new IntersectionSimulator(seed);
            evalSim.reset(seed, currentArrivals);
            evalSim.setSignalPlan(plan);
            metrics = evalSim.runSimulation(120);
        }

        if (metrics != null) {
            return String.format(
                    "Current Status:\n\n" +
                    "Arrival Rates (veh/min):\n" +
                    "  N=%.1f, S=%.1f, E=%.1f, W=%.1f\n\n" +
                    "Current Signal Timing:\n" +
                    "  NS Green: %.0f sec\n" +
                    "  EW Green: %.0f sec\n" +
                    "  Cycle: %.0f sec\n\n" +
                    "Performance Metrics:\n" +
                    "  Average Delay: %.1f sec\n" +
                    "  Queue P95: %.1f vehicles\n" +
                    "  Throughput: %.0f veh/hr\n" +
                    "  Stops/Vehicle: %.2f",
                    currentArrivals.get("N"), currentArrivals.get("S"),
                    currentArrivals.get("E"), currentArrivals.get("W"),
                    nsGreen, ewGreen, plan.getCycleSeconds(),
                    metrics.getAvgDelaySec(),
                    metrics.getQueueP95(),
                    metrics.getThroughputVph(),
                    metrics.getStopsPerVeh());
        } else {
            return String.format(
                    "Current Status:\n\n" +
                    "Arrival Rates (veh/min):\n" +
                    "  N=%.1f, S=%.1f, E=%.1f, W=%.1f\n\n" +
                    "No signal plan currently set",
                    currentArrivals.get("N"), currentArrivals.get("S"),
                    currentArrivals.get("E"), currentArrivals.get("W"));
        }
    }

    private Object executeSetGreenTimes(Map<String, Object> params) {
        double nsGreen = getDoubleParam(params, "nsGreenSeconds");
        double ewGreen = getDoubleParam(params, "ewGreenSeconds");

        // Create signal plan with standard yellow (3s) and all-red (1s)
        double yellowSeconds = 3.0;
        double allRedSeconds = 1.0;
        double cycleSeconds = nsGreen + ewGreen + 2 * (yellowSeconds + allRedSeconds);

        List<Phase> phases = List.of(
                new Phase("NS_through", nsGreen),
                new Phase("EW_through", ewGreen)
        );

        SignalPlan plan = new SignalPlan(cycleSeconds, phases, yellowSeconds, allRedSeconds);
        simulator.setSignalPlan(plan);

        return String.format(
                "Signal timing applied successfully!\n\n" +
                "NS Green: %.0f sec\n" +
                "EW Green: %.0f sec\n" +
                "Total Cycle: %.0f sec (includes yellow and all-red transitions)",
                nsGreen, ewGreen, cycleSeconds);
    }

    // ==================== HELPER METHODS ====================

    private SignalPlan parsePlan(Map<String, Object> planObj) {
        double cycleSeconds = getDoubleParam(planObj, "cycleSeconds");
        double yellowSeconds = getDoubleParam(planObj, "yellowSeconds", 3.0);
        double allRedSeconds = getDoubleParam(planObj, "allRedSeconds", 1.0);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> phasesObj = (List<Map<String, Object>>) planObj.get("phases");
        List<Phase> phases = new ArrayList<>();

        for (Map<String, Object> phaseObj : phasesObj) {
            String name = (String) phaseObj.get("name");
            double greenSeconds = getDoubleParam(phaseObj, "greenSeconds");
            phases.add(new Phase(name, greenSeconds));
        }

        return new SignalPlan(cycleSeconds, phases, yellowSeconds, allRedSeconds);
    }

    private long getLongParam(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.parseLong(value.toString());
    }

    private int getIntParam(Map<String, Object> params, String key, int defaultValue) {
        Object value = params.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private double getDoubleParam(Map<String, Object> params, String key) {
        return getDoubleParam(params, key, 0.0);
    }

    private double getDoubleParam(Map<String, Object> params, String key, double defaultValue) {
        Object value = params.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    // ==================== RESULT CLASSES ====================

    /**
     * Result of executing a single tool.
     */
    public static class ToolResult {
        private final boolean success;
        private final boolean cancelled;
        private final String message;

        private ToolResult(boolean success, boolean cancelled, String message) {
            this.success = success;
            this.cancelled = cancelled;
            this.message = message;
        }

        public static ToolResult success(String message) {
            return new ToolResult(true, false, message);
        }

        public static ToolResult error(String message) {
            return new ToolResult(false, false, message);
        }

        public static ToolResult cancelled() {
            return new ToolResult(false, true, "Cancelled");
        }

        public boolean isSuccess() {
            return success;
        }

        public boolean isCancelled() {
            return cancelled;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public String toString() {
            return message;
        }
    }
}
