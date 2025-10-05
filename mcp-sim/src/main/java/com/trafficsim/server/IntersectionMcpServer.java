package com.trafficsim.server;

import com.trafficsim.engine.IntersectionSimulator;
import com.trafficsim.model.Phase;
import com.trafficsim.model.SignalPlan;
import com.trafficsim.model.SimulationMetrics;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpAsyncServer;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * MCP Server for traffic intersection optimization.
 * Exposes tools for resetting, evaluating, and applying signal plans.
 */
public class IntersectionMcpServer {

    private static IntersectionSimulator simulator;
    private static long currentSeed = 12345;
    private static Map<String, Double> currentArrivals;

    public static void main(String[] args) {
        try {
            simulator = new IntersectionSimulator(currentSeed);
            currentArrivals = getDefaultArrivals();

            startStdioServer();
        } catch (Exception e) {
            System.err.println("Server error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Create an async stdio server with a shared simulator.
     * Used by UI when running in stdio mode.
     */
    public static McpAsyncServer createStdioServer(IntersectionSimulator sharedSimulator,
                                                     Map<String, Double> arrivals,
                                                     long seed) {
        // Use the shared simulator instead of creating a new one
        simulator = sharedSimulator;
        currentArrivals = arrivals;
        currentSeed = seed;

        StdioServerTransportProvider transportProvider =
                new StdioServerTransportProvider(McpJsonMapper.createDefault());

        return McpServer.async(transportProvider)
                .serverInfo("intersection-optimizer-server", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .tools(
                        createGetStatusTool(),
                        createSetGreenTimesTool(),
                        createResetTool(),
                        createEvaluatePlanTool(),
                        createApplyPlanTool(),
                        createGetStateTool()
                )
                .build();
    }

    /**
     * Update the current arrival rates.
     * Used by UI to keep MCP server in sync with slider changes.
     */
    public static void updateCurrentArrivals(Map<String, Double> arrivals) {
        if (currentArrivals != null) {
            currentArrivals.putAll(arrivals);
        }
    }

    private static void startStdioServer() throws InterruptedException {
        McpAsyncServer server = createStdioServer(simulator, currentArrivals, currentSeed);

        System.err.println("Intersection Optimizer MCP Server started (stdio mode)");
        System.err.println("Version: 1.0.0");
        System.err.println("Ready for connections...");

        CountDownLatch latch = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.err.println("Shutting down server...");
            server.close();
            latch.countDown();
        }));

        latch.await();
    }

    private static McpServerFeatures.AsyncToolSpecification createGetStatusTool() {
        String schema = """
            {
              "type": "object",
              "properties": {}
            }
            """;

        return new McpServerFeatures.AsyncToolSpecification.Builder()
                .tool(McpSchema.Tool.builder()
                        .name("get_status")
                        .description("Get current intersection status: arrival rates, performance metrics, and signal timing")
                        .inputSchema(McpJsonMapper.createDefault(), schema)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        // Get current signal plan
                        SignalPlan plan = simulator.getCurrentPlan();
                        double nsGreen = 0, ewGreen = 0;
                        if (plan != null && plan.getPhases().size() >= 2) {
                            nsGreen = plan.getPhases().get(0).getGreenSeconds();
                            ewGreen = plan.getPhases().get(1).getGreenSeconds();
                        }

                        // Run quick evaluation to get current metrics
                        SimulationMetrics metrics = null;
                        if (plan != null) {
                            IntersectionSimulator evalSim = new IntersectionSimulator(currentSeed);
                            evalSim.reset(currentSeed, currentArrivals);
                            evalSim.setSignalPlan(plan);
                            metrics = evalSim.runSimulation(120);
                        }

                        String message;
                        if (metrics != null) {
                            message = String.format(
                                    "Current Status:\\n\\n" +
                                    "Arrival Rates (veh/min):\\n" +
                                    "  N=%.1f, S=%.1f, E=%.1f, W=%.1f\\n\\n" +
                                    "Current Signal Timing:\\n" +
                                    "  NS Green: %.0f sec\\n" +
                                    "  EW Green: %.0f sec\\n" +
                                    "  Cycle: %.0f sec\\n\\n" +
                                    "Performance Metrics:\\n" +
                                    "  Average Delay: %.1f sec\\n" +
                                    "  Queue P95: %.1f vehicles\\n" +
                                    "  Throughput: %.0f veh/hr\\n" +
                                    "  Stops/Vehicle: %.2f",
                                    currentArrivals.get("N"), currentArrivals.get("S"),
                                    currentArrivals.get("E"), currentArrivals.get("W"),
                                    nsGreen, ewGreen, plan.getCycleSeconds(),
                                    metrics.getAvgDelaySec(),
                                    metrics.getQueueP95(),
                                    metrics.getThroughputVph(),
                                    metrics.getStopsPerVeh());
                        } else {
                            message = String.format(
                                    "Current Status:\\n\\n" +
                                    "Arrival Rates (veh/min):\\n" +
                                    "  N=%.1f, S=%.1f, E=%.1f, W=%.1f\\n\\n" +
                                    "No signal plan currently set",
                                    currentArrivals.get("N"), currentArrivals.get("S"),
                                    currentArrivals.get("E"), currentArrivals.get("W"));
                        }

                        return Mono.just(new McpSchema.CallToolResult.Builder()
                                .content(List.of(new McpSchema.TextContent(message)))
                                .isError(false)
                                .build());
                    } catch (Exception e) {
                        return Mono.just(new McpSchema.CallToolResult.Builder()
                                .content(List.of(new McpSchema.TextContent("Error: " + e.getMessage())))
                                .isError(true)
                                .build());
                    }
                })
                .build();
    }

    private static McpServerFeatures.AsyncToolSpecification createSetGreenTimesTool() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "nsGreenSeconds": {
                  "type": "number",
                  "description": "Green time for North-South direction (seconds)",
                  "minimum": 10,
                  "maximum": 120
                },
                "ewGreenSeconds": {
                  "type": "number",
                  "description": "Green time for East-West direction (seconds)",
                  "minimum": 10,
                  "maximum": 120
                }
              },
              "required": ["nsGreenSeconds", "ewGreenSeconds"]
            }
            """;

        return new McpServerFeatures.AsyncToolSpecification.Builder()
                .tool(McpSchema.Tool.builder()
                        .name("set_green_times")
                        .description("Set signal green times directly (simpler than creating a full signal plan)")
                        .inputSchema(McpJsonMapper.createDefault(), schema)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        Map<String, Object> args = request.arguments();
                        double nsGreen = getDoubleArg(args, "nsGreenSeconds");
                        double ewGreen = getDoubleArg(args, "ewGreenSeconds");

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

                        String message = String.format(
                                "Signal timing applied successfully!\\n\\n" +
                                "NS Green: %.0f sec\\n" +
                                "EW Green: %.0f sec\\n" +
                                "Total Cycle: %.0f sec (includes yellow and all-red transitions)",
                                nsGreen, ewGreen, cycleSeconds);

                        return Mono.just(new McpSchema.CallToolResult.Builder()
                                .content(List.of(new McpSchema.TextContent(message)))
                                .isError(false)
                                .build());
                    } catch (Exception e) {
                        return Mono.just(new McpSchema.CallToolResult.Builder()
                                .content(List.of(new McpSchema.TextContent("Error: " + e.getMessage())))
                                .isError(true)
                                .build());
                    }
                })
                .build();
    }

    private static McpServerFeatures.AsyncToolSpecification createResetTool() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "seed": {"type": "integer", "description": "Random seed for reproducibility"},
                "arrivals": {
                  "type": "object",
                  "properties": {
                    "N": {"type": "number", "description": "North arrival rate (veh/min)"},
                    "S": {"type": "number", "description": "South arrival rate (veh/min)"},
                    "E": {"type": "number", "description": "East arrival rate (veh/min)"},
                    "W": {"type": "number", "description": "West arrival rate (veh/min)"}
                  },
                  "required": ["N", "S", "E", "W"]
                }
              },
              "required": ["seed", "arrivals"]
            }
            """;

        return new McpServerFeatures.AsyncToolSpecification.Builder()
                .tool(McpSchema.Tool.builder()
                        .name("intersection_reset")
                        .description("Reset the intersection simulation with specified seed and arrival rates")
                        .inputSchema(McpJsonMapper.createDefault(), schema)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        Map<String, Object> args = request.arguments();
                        long seed = getLongArg(args, "seed");

                        @SuppressWarnings("unchecked")
                        Map<String, Object> arrivalsObj = (Map<String, Object>) args.get("arrivals");
                        Map<String, Double> arrivals = new HashMap<>();
                        arrivals.put("N", getDoubleArg(arrivalsObj, "N"));
                        arrivals.put("S", getDoubleArg(arrivalsObj, "S"));
                        arrivals.put("E", getDoubleArg(arrivalsObj, "E"));
                        arrivals.put("W", getDoubleArg(arrivalsObj, "W"));

                        currentSeed = seed;
                        currentArrivals = arrivals;
                        simulator.reset(seed, arrivals);

                        String message = String.format(
                                "Intersection reset successfully!\\nSeed: %d\\nArrivals: N=%.1f, S=%.1f, E=%.1f, W=%.1f veh/min",
                                seed, arrivals.get("N"), arrivals.get("S"), arrivals.get("E"), arrivals.get("W"));

                        return Mono.just(new McpSchema.CallToolResult.Builder()
                                .content(List.of(new McpSchema.TextContent(message)))
                                .isError(false)
                                .build());
                    } catch (Exception e) {
                        return Mono.just(new McpSchema.CallToolResult.Builder()
                                .content(List.of(new McpSchema.TextContent("Error: " + e.getMessage())))
                                .isError(true)
                                .build());
                    }
                })
                .build();
    }

    private static McpServerFeatures.AsyncToolSpecification createEvaluatePlanTool() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "plan": {
                  "type": "object",
                  "properties": {
                    "cycleSeconds": {"type": "number", "minimum": 30, "maximum": 180},
                    "phases": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "properties": {
                          "name": {"type": "string"},
                          "greenSeconds": {"type": "number", "minimum": 6, "maximum": 120}
                        },
                        "required": ["name", "greenSeconds"]
                      }
                    },
                    "yellowSeconds": {"type": "number", "default": 3},
                    "allRedSeconds": {"type": "number", "default": 1}
                  },
                  "required": ["cycleSeconds", "phases"]
                },
                "durationSeconds": {"type": "number", "default": 120},
                "replications": {"type": "integer", "default": 3}
              },
              "required": ["plan"]
            }
            """;

        return new McpServerFeatures.AsyncToolSpecification.Builder()
                .tool(McpSchema.Tool.builder()
                        .name("intersection_evaluate_plan")
                        .description("Evaluate a signal plan and return performance metrics")
                        .inputSchema(McpJsonMapper.createDefault(), schema)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        Map<String, Object> args = request.arguments();

                        @SuppressWarnings("unchecked")
                        Map<String, Object> planObj = (Map<String, Object>) args.get("plan");
                        SignalPlan plan = parsePlan(planObj);

                        double duration = getDoubleArg(args, "durationSeconds", 120.0);
                        int replications = getIntArg(args, "replications", 3);

                        // Run multiple replications and average results
                        double totalDelay = 0, totalQueue = 0, totalThroughput = 0, totalStops = 0;

                        for (int i = 0; i < replications; i++) {
                            simulator.reset(currentSeed + i, currentArrivals);
                            simulator.setSignalPlan(plan);
                            SimulationMetrics metrics = simulator.runSimulation(duration);

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

                        String jsonMetrics = String.format(
                                "{\"metrics\": {\"avgDelaySec\": %.2f, \"queueP95\": %.1f, \"throughputVph\": %.0f, \"stopsPerVeh\": %.2f}}",
                                avgMetrics.get("avgDelaySec"),
                                avgMetrics.get("queueP95"),
                                avgMetrics.get("throughputVph"),
                                avgMetrics.get("stopsPerVeh"));

                        return Mono.just(new McpSchema.CallToolResult.Builder()
                                .content(List.of(new McpSchema.TextContent(jsonMetrics)))
                                .isError(false)
                                .build());
                    } catch (Exception e) {
                        return Mono.just(new McpSchema.CallToolResult.Builder()
                                .content(List.of(new McpSchema.TextContent("Error: " + e.getMessage())))
                                .isError(true)
                                .build());
                    }
                })
                .build();
    }

    private static McpServerFeatures.AsyncToolSpecification createApplyPlanTool() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "plan": {
                  "type": "object",
                  "properties": {
                    "cycleSeconds": {"type": "number", "minimum": 30, "maximum": 180},
                    "phases": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "properties": {
                          "name": {"type": "string"},
                          "greenSeconds": {"type": "number", "minimum": 6, "maximum": 120}
                        },
                        "required": ["name", "greenSeconds"]
                      }
                    },
                    "yellowSeconds": {"type": "number", "default": 3},
                    "allRedSeconds": {"type": "number", "default": 1}
                  },
                  "required": ["cycleSeconds", "phases"]
                }
              },
              "required": ["plan"]
            }
            """;

        return new McpServerFeatures.AsyncToolSpecification.Builder()
                .tool(McpSchema.Tool.builder()
                        .name("intersection_apply_plan")
                        .description("Apply a signal plan to the running simulation")
                        .inputSchema(McpJsonMapper.createDefault(), schema)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        Map<String, Object> args = request.arguments();

                        @SuppressWarnings("unchecked")
                        Map<String, Object> planObj = (Map<String, Object>) args.get("plan");
                        SignalPlan plan = parsePlan(planObj);

                        simulator.setSignalPlan(plan);

                        String message = String.format(
                                "Signal plan applied successfully!\\nCycle: %.0fs, Phases: %d",
                                plan.getCycleSeconds(), plan.getPhases().size());

                        return Mono.just(new McpSchema.CallToolResult.Builder()
                                .content(List.of(new McpSchema.TextContent(message)))
                                .isError(false)
                                .build());
                    } catch (Exception e) {
                        return Mono.just(new McpSchema.CallToolResult.Builder()
                                .content(List.of(new McpSchema.TextContent("Error: " + e.getMessage())))
                                .isError(true)
                                .build());
                    }
                })
                .build();
    }

    private static McpServerFeatures.AsyncToolSpecification createGetStateTool() {
        String schema = """
            {
              "type": "object",
              "properties": {}
            }
            """;

        return new McpServerFeatures.AsyncToolSpecification.Builder()
                .tool(McpSchema.Tool.builder()
                        .name("intersection_get_state")
                        .description("Get current intersection state (queue lengths, signal phase, etc.)")
                        .inputSchema(McpJsonMapper.createDefault(), schema)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        Map<String, Object> state = simulator.getState();

                        @SuppressWarnings("unchecked")
                        Map<String, Integer> queueLengths = (Map<String, Integer>) state.get("queueLengths");

                        String message = String.format(
                                "Intersection State:\\nTime: %.1fs\\nPhase: %d\\nSignal: %s\\nQueues: N=%d, S=%d, E=%d, W=%d",
                                (Double) state.get("simTime"),
                                (Integer) state.get("currentPhase"),
                                (String) state.get("signalState"),
                                queueLengths.get("N"),
                                queueLengths.get("S"),
                                queueLengths.get("E"),
                                queueLengths.get("W"));

                        return Mono.just(new McpSchema.CallToolResult.Builder()
                                .content(List.of(new McpSchema.TextContent(message)))
                                .isError(false)
                                .build());
                    } catch (Exception e) {
                        return Mono.just(new McpSchema.CallToolResult.Builder()
                                .content(List.of(new McpSchema.TextContent("Error: " + e.getMessage())))
                                .isError(true)
                                .build());
                    }
                })
                .build();
    }

    private static SignalPlan parsePlan(Map<String, Object> planObj) {
        double cycleSeconds = getDoubleArg(planObj, "cycleSeconds");
        double yellowSeconds = getDoubleArg(planObj, "yellowSeconds", 3.0);
        double allRedSeconds = getDoubleArg(planObj, "allRedSeconds", 1.0);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> phasesObj = (List<Map<String, Object>>) planObj.get("phases");
        List<Phase> phases = new ArrayList<>();

        for (Map<String, Object> phaseObj : phasesObj) {
            String name = (String) phaseObj.get("name");
            double greenSeconds = getDoubleArg(phaseObj, "greenSeconds");
            phases.add(new Phase(name, greenSeconds));
        }

        return new SignalPlan(cycleSeconds, phases, yellowSeconds, allRedSeconds);
    }

    private static Map<String, Double> getDefaultArrivals() {
        Map<String, Double> arrivals = new HashMap<>();
        arrivals.put("N", 10.0);
        arrivals.put("S", 10.0);
        arrivals.put("E", 10.0);
        arrivals.put("W", 10.0);
        return arrivals;
    }

    private static long getLongArg(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.parseLong(value.toString());
    }

    private static int getIntArg(Map<String, Object> args, String key, int defaultValue) {
        Object value = args.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.parseInt(value.toString());
    }

    private static double getDoubleArg(Map<String, Object> args, String key) {
        return getDoubleArg(args, key, 0.0);
    }

    private static double getDoubleArg(Map<String, Object> args, String key, double defaultValue) {
        Object value = args.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return Double.parseDouble(value.toString());
    }

    /**
     * Factory for creating stateless sync tools for HTTP transport.
     * These tools work with a shared simulator instance passed from the UI.
     */
    public static class ToolFactory {

        public static java.util.List<io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification> createAllStatelessTools(
                IntersectionSimulator sharedSimulator) {
            return java.util.List.of(
                    createGetStatusToolStateless(sharedSimulator),
                    createSetGreenTimesToolStateless(sharedSimulator),
                    createResetToolStateless(sharedSimulator),
                    createEvaluatePlanToolStateless(sharedSimulator),
                    createApplyPlanToolStateless(sharedSimulator),
                    createGetStateToolStateless(sharedSimulator)
            );
        }

        private static io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification createGetStatusToolStateless(
                IntersectionSimulator sharedSimulator) {
            String schema = """
                {
                  "type": "object",
                  "properties": {}
                }
                """;

            return new io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification.Builder()
                    .tool(McpSchema.Tool.builder()
                            .name("get_status")
                            .description("Get current intersection status: arrival rates, performance metrics, and signal timing")
                            .inputSchema(McpJsonMapper.createDefault(), schema)
                            .build())
                    .callHandler((transportContext, request) -> {
                        try {
                            // Get current signal plan
                            SignalPlan plan = sharedSimulator.getCurrentPlan();
                            double nsGreen = 0, ewGreen = 0;
                            if (plan != null && plan.getPhases().size() >= 2) {
                                nsGreen = plan.getPhases().get(0).getGreenSeconds();
                                ewGreen = plan.getPhases().get(1).getGreenSeconds();
                            }

                            // Run quick evaluation to get current metrics
                            SimulationMetrics metrics = null;
                            if (plan != null) {
                                IntersectionSimulator evalSim = new IntersectionSimulator(currentSeed);
                                evalSim.reset(currentSeed, currentArrivals);
                                evalSim.setSignalPlan(plan);
                                metrics = evalSim.runSimulation(120);
                            }

                            String message;
                            if (metrics != null) {
                                message = String.format(
                                        "Current Status:\\n\\n" +
                                        "Arrival Rates (veh/min):\\n" +
                                        "  N=%.1f, S=%.1f, E=%.1f, W=%.1f\\n\\n" +
                                        "Current Signal Timing:\\n" +
                                        "  NS Green: %.0f sec\\n" +
                                        "  EW Green: %.0f sec\\n" +
                                        "  Cycle: %.0f sec\\n\\n" +
                                        "Performance Metrics:\\n" +
                                        "  Average Delay: %.1f sec\\n" +
                                        "  Queue P95: %.1f vehicles\\n" +
                                        "  Throughput: %.0f veh/hr\\n" +
                                        "  Stops/Vehicle: %.2f",
                                        currentArrivals.get("N"), currentArrivals.get("S"),
                                        currentArrivals.get("E"), currentArrivals.get("W"),
                                        nsGreen, ewGreen, plan.getCycleSeconds(),
                                        metrics.getAvgDelaySec(),
                                        metrics.getQueueP95(),
                                        metrics.getThroughputVph(),
                                        metrics.getStopsPerVeh());
                            } else {
                                message = String.format(
                                        "Current Status:\\n\\n" +
                                        "Arrival Rates (veh/min):\\n" +
                                        "  N=%.1f, S=%.1f, E=%.1f, W=%.1f\\n\\n" +
                                        "No signal plan currently set",
                                        currentArrivals.get("N"), currentArrivals.get("S"),
                                        currentArrivals.get("E"), currentArrivals.get("W"));
                            }

                            return new McpSchema.CallToolResult.Builder()
                                    .content(List.of(new McpSchema.TextContent(message)))
                                    .isError(false)
                                    .build();
                        } catch (Exception e) {
                            return new McpSchema.CallToolResult.Builder()
                                    .content(List.of(new McpSchema.TextContent("Error: " + e.getMessage())))
                                    .isError(true)
                                    .build();
                        }
                    })
                    .build();
        }

        private static io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification createSetGreenTimesToolStateless(
                IntersectionSimulator sharedSimulator) {
            String schema = """
                {
                  "type": "object",
                  "properties": {
                    "nsGreenSeconds": {
                      "type": "number",
                      "description": "Green time for North-South direction (seconds)",
                      "minimum": 10,
                      "maximum": 120
                    },
                    "ewGreenSeconds": {
                      "type": "number",
                      "description": "Green time for East-West direction (seconds)",
                      "minimum": 10,
                      "maximum": 120
                    }
                  },
                  "required": ["nsGreenSeconds", "ewGreenSeconds"]
                }
                """;

            return new io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification.Builder()
                    .tool(McpSchema.Tool.builder()
                            .name("set_green_times")
                            .description("Set signal green times directly (simpler than creating a full signal plan)")
                            .inputSchema(McpJsonMapper.createDefault(), schema)
                            .build())
                    .callHandler((transportContext, request) -> {
                        try {
                            Map<String, Object> args = request.arguments();
                            double nsGreen = getDoubleArg(args, "nsGreenSeconds");
                            double ewGreen = getDoubleArg(args, "ewGreenSeconds");

                            // Create signal plan with standard yellow (3s) and all-red (1s)
                            double yellowSeconds = 3.0;
                            double allRedSeconds = 1.0;
                            double cycleSeconds = nsGreen + ewGreen + 2 * (yellowSeconds + allRedSeconds);

                            List<Phase> phases = List.of(
                                    new Phase("NS_through", nsGreen),
                                    new Phase("EW_through", ewGreen)
                            );

                            SignalPlan plan = new SignalPlan(cycleSeconds, phases, yellowSeconds, allRedSeconds);
                            sharedSimulator.setSignalPlan(plan);

                            String message = String.format(
                                    "Signal timing applied successfully!\\n\\n" +
                                    "NS Green: %.0f sec\\n" +
                                    "EW Green: %.0f sec\\n" +
                                    "Total Cycle: %.0f sec (includes yellow and all-red transitions)",
                                    nsGreen, ewGreen, cycleSeconds);

                            return new McpSchema.CallToolResult.Builder()
                                    .content(List.of(new McpSchema.TextContent(message)))
                                    .isError(false)
                                    .build();
                        } catch (Exception e) {
                            return new McpSchema.CallToolResult.Builder()
                                    .content(List.of(new McpSchema.TextContent("Error: " + e.getMessage())))
                                    .isError(true)
                                    .build();
                        }
                    })
                    .build();
        }

        private static io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification createResetToolStateless(
                IntersectionSimulator sharedSimulator) {
            String schema = """
                {
                  "type": "object",
                  "properties": {
                    "seed": {"type": "integer", "description": "Random seed for reproducibility"},
                    "arrivals": {
                      "type": "object",
                      "properties": {
                        "N": {"type": "number", "description": "North arrival rate (veh/min)"},
                        "S": {"type": "number", "description": "South arrival rate (veh/min)"},
                        "E": {"type": "number", "description": "East arrival rate (veh/min)"},
                        "W": {"type": "number", "description": "West arrival rate (veh/min)"}
                      },
                      "required": ["N", "S", "E", "W"]
                    }
                  },
                  "required": ["seed", "arrivals"]
                }
                """;

            return new io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification.Builder()
                    .tool(McpSchema.Tool.builder()
                            .name("intersection_reset")
                            .description("Reset the intersection simulation with specified seed and arrival rates")
                            .inputSchema(McpJsonMapper.createDefault(), schema)
                            .build())
                    .callHandler((transportContext, request) -> {
                        try {
                            Map<String, Object> args = request.arguments();
                            long seed = getLongArg(args, "seed");

                            @SuppressWarnings("unchecked")
                            Map<String, Object> arrivalsObj = (Map<String, Object>) args.get("arrivals");
                            Map<String, Double> arrivals = new HashMap<>();
                            arrivals.put("N", getDoubleArg(arrivalsObj, "N"));
                            arrivals.put("S", getDoubleArg(arrivalsObj, "S"));
                            arrivals.put("E", getDoubleArg(arrivalsObj, "E"));
                            arrivals.put("W", getDoubleArg(arrivalsObj, "W"));

                            sharedSimulator.reset(seed, arrivals);

                            String message = String.format(
                                    "Intersection reset successfully!\\nSeed: %d\\nArrivals: N=%.1f, S=%.1f, E=%.1f, W=%.1f veh/min",
                                    seed, arrivals.get("N"), arrivals.get("S"), arrivals.get("E"), arrivals.get("W"));

                            return new McpSchema.CallToolResult.Builder()
                                    .content(List.of(new McpSchema.TextContent(message)))
                                    .isError(false)
                                    .build();
                        } catch (Exception e) {
                            return new McpSchema.CallToolResult.Builder()
                                    .content(List.of(new McpSchema.TextContent("Error: " + e.getMessage())))
                                    .isError(true)
                                    .build();
                        }
                    })
                    .build();
        }

        private static io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification createEvaluatePlanToolStateless(
                IntersectionSimulator sharedSimulator) {
            String schema = """
                {
                  "type": "object",
                  "properties": {
                    "plan": {
                      "type": "object",
                      "properties": {
                        "cycleSeconds": {"type": "number", "minimum": 30, "maximum": 180},
                        "phases": {
                          "type": "array",
                          "items": {
                            "type": "object",
                            "properties": {
                              "name": {"type": "string"},
                              "greenSeconds": {"type": "number", "minimum": 6, "maximum": 120}
                            },
                            "required": ["name", "greenSeconds"]
                          }
                        },
                        "yellowSeconds": {"type": "number", "default": 3},
                        "allRedSeconds": {"type": "number", "default": 1}
                      },
                      "required": ["cycleSeconds", "phases"]
                    },
                    "durationSeconds": {"type": "number", "default": 120},
                    "replications": {"type": "integer", "default": 3}
                  },
                  "required": ["plan"]
                }
                """;

            return new io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification.Builder()
                    .tool(McpSchema.Tool.builder()
                            .name("intersection_evaluate_plan")
                            .description("Evaluate a signal plan and return performance metrics")
                            .inputSchema(McpJsonMapper.createDefault(), schema)
                            .build())
                    .callHandler((transportContext, request) -> {
                        try {
                            Map<String, Object> args = request.arguments();

                            @SuppressWarnings("unchecked")
                            Map<String, Object> planObj = (Map<String, Object>) args.get("plan");
                            SignalPlan plan = parsePlan(planObj);

                            double duration = getDoubleArg(args, "durationSeconds", 120.0);
                            int replications = getIntArg(args, "replications", 3);

                            // Create a temporary simulator for evaluation
                            IntersectionSimulator evalSim = new IntersectionSimulator(12345);
                            Map<String, Object> state = sharedSimulator.getState();

                            // Get current arrival rates from shared simulator
                            Map<String, Double> currentArrivals = new HashMap<>();
                            currentArrivals.put("N", 10.0);
                            currentArrivals.put("S", 10.0);
                            currentArrivals.put("E", 10.0);
                            currentArrivals.put("W", 10.0);

                            // Run multiple replications and average results
                            double totalDelay = 0, totalQueue = 0, totalThroughput = 0, totalStops = 0;

                            for (int i = 0; i < replications; i++) {
                                evalSim.reset(12345 + i, currentArrivals);
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

                            String jsonMetrics = String.format(
                                    "{\"metrics\": {\"avgDelaySec\": %.2f, \"queueP95\": %.1f, \"throughputVph\": %.0f, \"stopsPerVeh\": %.2f}}",
                                    avgMetrics.get("avgDelaySec"),
                                    avgMetrics.get("queueP95"),
                                    avgMetrics.get("throughputVph"),
                                    avgMetrics.get("stopsPerVeh"));

                            return new McpSchema.CallToolResult.Builder()
                                    .content(List.of(new McpSchema.TextContent(jsonMetrics)))
                                    .isError(false)
                                    .build();
                        } catch (Exception e) {
                            return new McpSchema.CallToolResult.Builder()
                                    .content(List.of(new McpSchema.TextContent("Error: " + e.getMessage())))
                                    .isError(true)
                                    .build();
                        }
                    })
                    .build();
        }

        private static io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification createApplyPlanToolStateless(
                IntersectionSimulator sharedSimulator) {
            String schema = """
                {
                  "type": "object",
                  "properties": {
                    "plan": {
                      "type": "object",
                      "properties": {
                        "cycleSeconds": {"type": "number", "minimum": 30, "maximum": 180},
                        "phases": {
                          "type": "array",
                          "items": {
                            "type": "object",
                            "properties": {
                              "name": {"type": "string"},
                              "greenSeconds": {"type": "number", "minimum": 6, "maximum": 120}
                            },
                            "required": ["name", "greenSeconds"]
                          }
                        },
                        "yellowSeconds": {"type": "number", "default": 3},
                        "allRedSeconds": {"type": "number", "default": 1}
                      },
                      "required": ["cycleSeconds", "phases"]
                    }
                  },
                  "required": ["plan"]
                }
                """;

            return new io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification.Builder()
                    .tool(McpSchema.Tool.builder()
                            .name("intersection_apply_plan")
                            .description("Apply a signal plan to the running simulation")
                            .inputSchema(McpJsonMapper.createDefault(), schema)
                            .build())
                    .callHandler((transportContext, request) -> {
                        try {
                            Map<String, Object> args = request.arguments();

                            @SuppressWarnings("unchecked")
                            Map<String, Object> planObj = (Map<String, Object>) args.get("plan");
                            SignalPlan plan = parsePlan(planObj);

                            sharedSimulator.setSignalPlan(plan);

                            String message = String.format(
                                    "Signal plan applied successfully!\\nCycle: %.0fs, Phases: %d",
                                    plan.getCycleSeconds(), plan.getPhases().size());

                            return new McpSchema.CallToolResult.Builder()
                                    .content(List.of(new McpSchema.TextContent(message)))
                                    .isError(false)
                                    .build();
                        } catch (Exception e) {
                            return new McpSchema.CallToolResult.Builder()
                                    .content(List.of(new McpSchema.TextContent("Error: " + e.getMessage())))
                                    .isError(true)
                                    .build();
                        }
                    })
                    .build();
        }

        private static io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification createGetStateToolStateless(
                IntersectionSimulator sharedSimulator) {
            String schema = """
                {
                  "type": "object",
                  "properties": {}
                }
                """;

            return new io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification.Builder()
                    .tool(McpSchema.Tool.builder()
                            .name("intersection_get_state")
                            .description("Get current intersection state (queue lengths, signal phase, etc.)")
                            .inputSchema(McpJsonMapper.createDefault(), schema)
                            .build())
                    .callHandler((transportContext, request) -> {
                        try {
                            Map<String, Object> state = sharedSimulator.getState();

                            @SuppressWarnings("unchecked")
                            Map<String, Integer> queueLengths = (Map<String, Integer>) state.get("queueLengths");

                            String message = String.format(
                                    "Intersection State:\\nTime: %.1fs\\nPhase: %d\\nSignal: %s\\nQueues: N=%d, S=%d, E=%d, W=%d",
                                    (Double) state.get("simTime"),
                                    (Integer) state.get("currentPhase"),
                                    (String) state.get("signalState"),
                                    queueLengths.get("N"),
                                    queueLengths.get("S"),
                                    queueLengths.get("E"),
                                    queueLengths.get("W"));

                            return new McpSchema.CallToolResult.Builder()
                                    .content(List.of(new McpSchema.TextContent(message)))
                                    .isError(false)
                                    .build();
                        } catch (Exception e) {
                            return new McpSchema.CallToolResult.Builder()
                                    .content(List.of(new McpSchema.TextContent("Error: " + e.getMessage())))
                                    .isError(true)
                                    .build();
                        }
                    })
                    .build();
        }
    }
}
