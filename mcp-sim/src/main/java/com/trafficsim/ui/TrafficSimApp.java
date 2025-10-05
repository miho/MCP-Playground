package com.trafficsim.ui;

import com.trafficsim.engine.IntersectionSimulator;
import com.trafficsim.mcp.DirectToolExecutor;
import com.trafficsim.mcp.McpConfig;
import com.trafficsim.mcp.ServerLauncher;
import com.trafficsim.model.Direction;
import com.trafficsim.model.Phase;
import com.trafficsim.model.SignalPlan;
import com.trafficsim.model.SimulationMetrics;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Main JavaFX application for the traffic intersection simulator.
 */
public class TrafficSimApp extends Application {
    private static final Logger logger = LoggerFactory.getLogger(TrafficSimApp.class);
    private static final double CANVAS_WIDTH = 600;
    private static final double CANVAS_HEIGHT = 600;

    private IntersectionSimulator simulator;
    private IntersectionRenderer renderer;
    private ControlPanel controlPanel;
    private MetricsPanel metricsPanel;
    private StatusBar statusBar;
    private McpLogPanel logPanel;
    private AnimationTimer animationTimer;

    // MCP integration
    private DirectToolExecutor toolExecutor;
    private ServerLauncher serverLauncher;
    private McpConfig mcpConfig;
    private static McpCliOptions cliOptions; // Parsed CLI options

    private boolean isRunning = false;
    private long lastUpdateTime = 0;
    private long lastMetricsUpdateTime = 0;
    private static final long UPDATE_INTERVAL_NS = 100_000_000; // 100ms = 10 Hz
    private static final long METRICS_UPDATE_INTERVAL_NS = 2_000_000_000; // 2 seconds

    @Override
    public void start(Stage primaryStage) {
        // Initialize simulator with default seed
        simulator = new IntersectionSimulator(12345);

        // Build MCP config from CLI options or use default
        boolean mcpEnabled = true;

        if (cliOptions != null) {
            mcpEnabled = cliOptions.isMcpEnabled();
            if (mcpEnabled) {
                mcpConfig = cliOptions.buildMcpConfig();
                System.out.println("MCP Configuration from CLI: " + mcpConfig);
            } else {
                mcpConfig = null; // MCP disabled
                System.out.println("MCP server disabled via CLI");
            }
        } else {
            // Default HTTP configuration
            mcpConfig = McpConfig.defaultHttp();
            System.out.println("Using default MCP configuration: " + mcpConfig);
        }

        // Initialize MCP components
        toolExecutor = new DirectToolExecutor(simulator);
        if (mcpEnabled && mcpConfig != null) {
            serverLauncher = new ServerLauncher(mcpConfig, simulator);
        }

        // Create canvas for rendering
        Canvas canvas = new Canvas(CANVAS_WIDTH, CANVAS_HEIGHT);
        renderer = new IntersectionRenderer(canvas, simulator);

        // Create control panel
        controlPanel = new ControlPanel();
        setupControlHandlers();

        // Listen for arrival rate changes and update simulator in real-time
        controlPanel.setOnArrivalRateChange(() -> {
            Map<Direction, Double> rates = controlPanel.getArrivalRates();
            Map<String, Double> arrivals = new HashMap<>();
            for (Map.Entry<Direction, Double> entry : rates.entrySet()) {
                arrivals.put(entry.getKey().getCode(), entry.getValue());
            }
            simulator.setArrivalRates(arrivals);

            // Also update MCP server's arrival rates
            if (serverLauncher != null) {
                serverLauncher.updateArrivals(arrivals);
            }
        });

        // Create metrics panel
        metricsPanel = new MetricsPanel();

        // Create status bar
        statusBar = new StatusBar();
        statusBar.setStatus("Initializing...");

        // Create log panel
        logPanel = new McpLogPanel();
        logPanel.setPrefHeight(200);
        logPanel.setMinHeight(150);

        // Layout - Main content area
        BorderPane mainContent = new BorderPane();
        mainContent.setCenter(canvas);
        mainContent.setLeft(controlPanel);
        mainContent.setRight(metricsPanel);

        // Split pane for main content and log panel
        SplitPane splitPane = new SplitPane();
        splitPane.setOrientation(javafx.geometry.Orientation.VERTICAL);
        splitPane.getItems().addAll(mainContent, logPanel);
        splitPane.setDividerPositions(0.75);

        // Root layout with status bar at bottom
        BorderPane root = new BorderPane();
        root.setCenter(splitPane);
        root.setBottom(statusBar);
        root.setStyle("-fx-background-color: #1a1a1a;");

        // Scene
        Scene scene = new Scene(root, 1200, 850);
        primaryStage.setTitle("Traffic Intersection Optimizer - MCP Enabled");
        primaryStage.setScene(scene);
        primaryStage.show();

        // Setup animation timer
        setupAnimationTimer();

        // Initialize with baseline plan
        applyBaselinePlan();

        // Start animation
        animationTimer.start();
        isRunning = true;

        // Start MCP server in background (if enabled)
        if (mcpEnabled && mcpConfig != null) {
            startMcpServer();
        } else {
            logPanel.logMessage("MCP server disabled");
            statusBar.setStatus("MCP server disabled");
        }

        // Log initial state
        logPanel.logMessage("Application started - MCP integration enabled");
        statusBar.setStatus("Ready");
    }

    private void setupAnimationTimer() {
        animationTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (now - lastUpdateTime >= UPDATE_INTERVAL_NS) {
                    // Get current speed multiplier
                    double speed = controlPanel.getSimulationSpeed();

                    // Run simulation steps based on speed
                    // At 1x: run 0.1s per frame (10 Hz)
                    // At 10x: run 1.0s per frame
                    double timeStep = 0.1 * speed;

                    if (isRunning && simulator.getCurrentPlan() != null) {
                        simulator.runSimulation(timeStep);
                    }

                    // Update renderer with current speed and render
                    renderer.setSimulationSpeed(speed);
                    renderer.render();

                    lastUpdateTime = now;
                }

                // Update metrics every 2 seconds
                if (now - lastMetricsUpdateTime >= METRICS_UPDATE_INTERVAL_NS) {
                    updateMetrics();
                    lastMetricsUpdateTime = now;
                }
            }
        };
    }

    private void setupControlHandlers() {
        // Reset button
        controlPanel.getResetButton().setOnAction(e -> {
            Map<Direction, Double> rates = controlPanel.getArrivalRates();
            Map<String, Double> arrivals = new HashMap<>();
            for (Map.Entry<Direction, Double> entry : rates.entrySet()) {
                arrivals.put(entry.getKey().getCode(), entry.getValue());
            }

            simulator.reset(System.currentTimeMillis(), arrivals);
            metricsPanel.clear();
            controlPanel.setStatus("Reset completed");
        });

        // Spike button
        controlPanel.getSpikeButton().setOnAction(e -> {
            // Double all arrival rates temporarily
            for (Direction dir : Direction.values()) {
                double current = controlPanel.getArrivalRates().get(dir);
                controlPanel.setArrivalRate(dir, current * 2);
            }
            controlPanel.setStatus("Rush hour spike activated!");

            // Reset after 30 seconds
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    javafx.application.Platform.runLater(() -> {
                        for (Direction dir : Direction.values()) {
                            double current = controlPanel.getArrivalRates().get(dir);
                            controlPanel.setArrivalRate(dir, current / 2);
                        }
                        controlPanel.setStatus("Rush hour ended");
                    });
                }
            }, 30000);
        });

        // Baseline button
        controlPanel.getBaselineButton().setOnAction(e -> {
            applyBaselinePlan();
            evaluateCurrentPlan();
        });

        // Optimize button - triggers MCP-based optimization
        controlPanel.getOptimizeButton().setOnAction(e -> {
            controlPanel.setStatus("Starting LLM optimization via MCP...");
            statusBar.setStatus("Running optimization...");
            logPanel.logMessage("========== STARTING OPTIMIZATION ==========");
            runOptimization();
        });

        // Update Metrics button
        controlPanel.getUpdateMetricsButton().setOnAction(e -> {
            updateMetrics();
            controlPanel.setStatus("Metrics updated");
        });

        // Apply Timing button - manual signal timing adjustment
        controlPanel.getApplyTimingButton().setOnAction(e -> {
            int nsGreen = controlPanel.getNsGreenTime();
            int ewGreen = controlPanel.getEwGreenTime();

            // Use the same logic as the set_green_times MCP tool
            double yellowSeconds = 3.0;
            double allRedSeconds = 1.0;
            double cycleTime = nsGreen + ewGreen + 2 * (yellowSeconds + allRedSeconds);

            List<Phase> phases = Arrays.asList(
                    new Phase("NS_through", nsGreen),
                    new Phase("EW_through", ewGreen)
            );
            SignalPlan customPlan = new SignalPlan(cycleTime, phases, yellowSeconds, allRedSeconds);
            simulator.setSignalPlan(customPlan);

            logPanel.logMessage(String.format("[UI] Signal timing applied: NS=%ds, EW=%ds, Cycle=%ds",
                    nsGreen, ewGreen, (int)cycleTime));
            controlPanel.setStatus(String.format("Signal timing: NS=%ds, EW=%ds", nsGreen, ewGreen));
            statusBar.setStatus(String.format("Signal timing applied: NS=%ds, EW=%ds, Cycle=%ds",
                    nsGreen, ewGreen, (int)cycleTime));

            // Update metrics to show performance with new timing
            updateMetrics();
        });
    }

    private void applyBaselinePlan() {
        // Simple baseline: 60s cycle, equal splits
        List<Phase> phases = Arrays.asList(
                new Phase("NS_through", 25),
                new Phase("EW_through", 25)
        );
        SignalPlan baselinePlan = new SignalPlan(60, phases, 3, 1);
        simulator.setSignalPlan(baselinePlan);
        controlPanel.setStatus("Baseline plan applied");
        syncSlidersWithCurrentPlan();
        updateMetrics();
    }

    private void updateMetrics() {
        if (simulator.getCurrentPlan() == null) {
            return;
        }

        // Sync sliders with current plan
        syncSlidersWithCurrentPlan();

        // Run a quick evaluation in background
        IntersectionSimulator evalSim = new IntersectionSimulator(12345);
        Map<String, Double> arrivals = new HashMap<>();
        for (Map.Entry<Direction, Double> entry : controlPanel.getArrivalRates().entrySet()) {
            arrivals.put(entry.getKey().getCode(), entry.getValue());
        }
        evalSim.reset(12345, arrivals);
        evalSim.setSignalPlan(simulator.getCurrentPlan());

        SimulationMetrics metrics = evalSim.runSimulation(120);
        metricsPanel.updateMetrics(metrics);
    }

    /**
     * Sync UI sliders with current signal plan.
     * Called after MCP tools change the timing.
     */
    private void syncSlidersWithCurrentPlan() {
        SignalPlan plan = simulator.getCurrentPlan();
        if (plan != null && plan.getPhases().size() >= 2) {
            int nsGreen = (int) plan.getPhases().get(0).getGreenSeconds();
            int ewGreen = (int) plan.getPhases().get(1).getGreenSeconds();
            controlPanel.setGreenTimes(nsGreen, ewGreen);
        }
    }

    private void evaluateCurrentPlan() {
        updateMetrics();
    }

    @Override
    public void stop() {
        if (animationTimer != null) {
            animationTimer.stop();
        }
        if (toolExecutor != null) {
            toolExecutor.shutdown();
        }
        if (serverLauncher != null) {
            serverLauncher.shutdown();
        }
    }

    // ==================== MCP INTEGRATION METHODS ====================

    /**
     * Start the MCP server in background.
     */
    private void startMcpServer() {
        statusBar.setStatus("Starting MCP server...");
        logPanel.logMessage("Starting MCP server...");

        // Sync arrival rates before starting
        Map<Direction, Double> rates = controlPanel.getArrivalRates();
        Map<String, Double> arrivals = new HashMap<>();
        for (Map.Entry<Direction, Double> entry : rates.entrySet()) {
            arrivals.put(entry.getKey().getCode(), entry.getValue());
        }
        serverLauncher.updateArrivals(arrivals);

        serverLauncher.startAsync().thenAccept(success -> {
            javafx.application.Platform.runLater(() -> {
                if (success) {
                    statusBar.setServerRunning(true);
                    statusBar.setEndpoint(serverLauncher.getEndpointUrl());
                    statusBar.setStatus("MCP server started successfully");
                    logPanel.logMessage("MCP server started: " + serverLauncher.getEndpointUrl());
                    logger.info("MCP server started");
                } else {
                    statusBar.setServerRunning(false);
                    statusBar.showError("Failed to start MCP server");
                    logPanel.logMessage("ERROR: Failed to start MCP server");
                    logger.error("Failed to start MCP server");
                }
            });
        });
    }

    /**
     * Run optimization workflow using MCP tools.
     * This simulates what an LLM would do:
     * 1. Try multiple signal plans with different parameters
     * 2. Evaluate each plan's performance
     * 3. Select and apply the best plan
     */
    private void runOptimization() {
        // Disable optimize button during optimization
        controlPanel.getOptimizeButton().setDisable(true);

        // Run optimization in background thread
        CompletableFuture.runAsync(() -> {
            try {
                // Get current arrival rates
                Map<Direction, Double> rates = controlPanel.getArrivalRates();
                Map<String, Object> arrivals = new HashMap<>();
                arrivals.put("N", rates.get(Direction.NORTH));
                arrivals.put("S", rates.get(Direction.SOUTH));
                arrivals.put("E", rates.get(Direction.EAST));
                arrivals.put("W", rates.get(Direction.WEST));

                logPanel.logOptimization("Testing multiple signal plan configurations...");

                // Test different plan configurations
                List<TestPlan> testPlans = Arrays.asList(
                        new TestPlan("Short Cycle (60s)", 60, 25, 25),
                        new TestPlan("Medium Cycle (80s)", 80, 35, 35),
                        new TestPlan("Long Cycle (100s)", 100, 45, 45),
                        new TestPlan("NS Priority (60s)", 60, 30, 20),
                        new TestPlan("EW Priority (60s)", 60, 20, 30)
                );

                TestPlan bestPlan = null;
                double bestScore = Double.MAX_VALUE;

                for (int i = 0; i < testPlans.size(); i++) {
                    TestPlan plan = testPlans.get(i);
                    logPanel.logOptimization(String.format("Testing plan %d/%d: %s",
                            i + 1, testPlans.size(), plan.name));

                    // Evaluate the plan using MCP tool
                    Map<String, Object> evalParams = new HashMap<>();

                    Map<String, Object> planDef = new HashMap<>();
                    planDef.put("cycleSeconds", plan.cycleSeconds);
                    planDef.put("yellowSeconds", 3.0);
                    planDef.put("allRedSeconds", 1.0);

                    List<Map<String, Object>> phases = new ArrayList<>();
                    Map<String, Object> phase1 = new HashMap<>();
                    phase1.put("name", "NS_through");
                    phase1.put("greenSeconds", plan.nsGreen);
                    phases.add(phase1);

                    Map<String, Object> phase2 = new HashMap<>();
                    phase2.put("name", "EW_through");
                    phase2.put("greenSeconds", plan.ewGreen);
                    phases.add(phase2);

                    planDef.put("phases", phases);
                    evalParams.put("plan", planDef);
                    evalParams.put("durationSeconds", 120.0);
                    evalParams.put("replications", 3);

                    // Call MCP tool
                    String paramsStr = String.format("cycle=%ds, NS=%ds, EW=%ds",
                            plan.cycleSeconds, plan.nsGreen, plan.ewGreen);
                    logPanel.logToolCall("intersection_evaluate_plan", paramsStr);

                    DirectToolExecutor.ToolResult result = toolExecutor
                            .executeTool("intersection_evaluate_plan", evalParams)
                            .join();

                    if (result.isSuccess()) {
                        logPanel.logToolSuccess("intersection_evaluate_plan", result.getMessage());

                        // Parse metrics from JSON result
                        String jsonResult = result.getMessage();
                        double avgDelay = extractMetric(jsonResult, "avgDelaySec");
                        double queue = extractMetric(jsonResult, "queueP95");

                        // Calculate score (lower is better)
                        double score = avgDelay * 2.0 + queue * 0.5;

                        logPanel.logOptimization(String.format(
                                "  Results: Delay=%.1fs, Queue=%.0f, Score=%.1f",
                                avgDelay, queue, score));

                        if (score < bestScore) {
                            bestScore = score;
                            bestPlan = plan;
                            logPanel.logOptimization("  >>> NEW BEST PLAN! <<<");
                        }
                    } else {
                        logPanel.logToolError("intersection_evaluate_plan", result.getMessage());
                    }

                    // Small delay between evaluations
                    Thread.sleep(100);
                }

                // Apply the best plan
                if (bestPlan != null) {
                    TestPlan finalBestPlan = bestPlan;
                    logPanel.logOptimization(String.format(
                            "Applying best plan: %s (Score: %.1f)",
                            finalBestPlan.name, bestScore));

                    Map<String, Object> applyParams = new HashMap<>();
                    Map<String, Object> planDef = new HashMap<>();
                    planDef.put("cycleSeconds", finalBestPlan.cycleSeconds);
                    planDef.put("yellowSeconds", 3.0);
                    planDef.put("allRedSeconds", 1.0);

                    List<Map<String, Object>> phases = new ArrayList<>();
                    Map<String, Object> phase1 = new HashMap<>();
                    phase1.put("name", "NS_through");
                    phase1.put("greenSeconds", finalBestPlan.nsGreen);
                    phases.add(phase1);

                    Map<String, Object> phase2 = new HashMap<>();
                    phase2.put("name", "EW_through");
                    phase2.put("greenSeconds", finalBestPlan.ewGreen);
                    phases.add(phase2);

                    planDef.put("phases", phases);
                    applyParams.put("plan", planDef);

                    logPanel.logToolCall("intersection_apply_plan", finalBestPlan.name);
                    DirectToolExecutor.ToolResult applyResult = toolExecutor
                            .executeTool("intersection_apply_plan", applyParams)
                            .join();

                    if (applyResult.isSuccess()) {
                        logPanel.logToolSuccess("intersection_apply_plan", applyResult.getMessage());

                        javafx.application.Platform.runLater(() -> {
                            statusBar.showSuccess("Optimization complete! Best plan applied.");
                            controlPanel.setStatus("Optimized: " + finalBestPlan.name);
                            updateMetrics();
                        });
                    } else {
                        logPanel.logToolError("intersection_apply_plan", applyResult.getMessage());
                        javafx.application.Platform.runLater(() -> {
                            statusBar.showError("Failed to apply optimized plan");
                        });
                    }
                } else {
                    javafx.application.Platform.runLater(() -> {
                        statusBar.showError("Optimization failed - no valid plan found");
                    });
                }

                logPanel.logMessage("========== OPTIMIZATION COMPLETE ==========");

            } catch (Exception ex) {
                logger.error("Optimization error", ex);
                logPanel.logMessage("ERROR: " + ex.getMessage());
                javafx.application.Platform.runLater(() -> {
                    statusBar.showError("Optimization failed: " + ex.getMessage());
                });
            } finally {
                javafx.application.Platform.runLater(() -> {
                    controlPanel.getOptimizeButton().setDisable(false);
                });
            }
        });
    }

    /**
     * Extract a metric value from JSON string.
     * Simple parser for our specific JSON format.
     */
    private double extractMetric(String json, String metric) {
        try {
            String search = "\"" + metric + "\": ";
            int start = json.indexOf(search);
            if (start == -1) return 0.0;
            start += search.length();

            int end = start;
            while (end < json.length() && (Character.isDigit(json.charAt(end))
                    || json.charAt(end) == '.' || json.charAt(end) == '-')) {
                end++;
            }

            return Double.parseDouble(json.substring(start, end));
        } catch (Exception e) {
            logger.warn("Failed to extract metric " + metric + " from: " + json, e);
            return 0.0;
        }
    }

    /**
     * Helper class to define test signal plans.
     */
    private static class TestPlan {
        final String name;
        final int cycleSeconds;
        final int nsGreen;
        final int ewGreen;

        TestPlan(String name, int cycleSeconds, int nsGreen, int ewGreen) {
            this.name = name;
            this.cycleSeconds = cycleSeconds;
            this.nsGreen = nsGreen;
            this.ewGreen = ewGreen;
        }
    }

    public static void main(String[] args) {
        // Parse CLI options before launching JavaFX
        cliOptions = McpCliOptions.parse(args);

        // If parsing failed or help was requested, exit
        if (cliOptions == null && args.length > 0) {
            // Help or version was shown, or parsing failed
            return;
        }

        // Launch JavaFX application
        launch(args);
    }
}
