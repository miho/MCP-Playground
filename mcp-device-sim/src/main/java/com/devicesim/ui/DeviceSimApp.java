package com.devicesim.ui;

import com.devicesim.data.CsvDataReader;
import com.devicesim.engine.DeviceSimulator;
import com.devicesim.mcp.DirectToolExecutor;
import com.devicesim.mcp.McpConfig;
import com.devicesim.mcp.ServerLauncher;
import com.devicesim.model.DeviceState;
import com.devicesim.model.Location;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Main JavaFX application for the device simulator with MCP integration.
 *
 * @since 1.0.0
 */
public class DeviceSimApp extends Application {

    private static final Logger logger = LoggerFactory.getLogger(DeviceSimApp.class);
    private static final double CANVAS_WIDTH = 800;
    private static final double CANVAS_HEIGHT = 600;
    private static final long UPDATE_INTERVAL_NS = 16_666_667; // ~60 FPS

    // Core components
    private DeviceSimulator simulator;
    private CsvDataReader csvReader;
    private DirectToolExecutor toolExecutor;
    private ServerLauncher serverLauncher;
    private McpConfig mcpConfig;

    // UI components
    private DeviceCanvas canvas;
    private ControlPanel controlPanel;
    private LocationListPanel locationListPanel;
    private StatusBar statusBar;
    private McpLogPanel logPanel;
    private AnimationTimer animationTimer;

    // State
    private static McpCliOptions cliOptions;
    private long lastUpdateTime = 0;
    private boolean isRunning = false;
    private String currentCsvPath = null;

    @Override
    public void start(Stage primaryStage) {
        logger.info("Starting Device Simulator Application");

        // Initialize core components
        simulator = new DeviceSimulator(0.0, 0.0);
        csvReader = new CsvDataReader();
        toolExecutor = new DirectToolExecutor(simulator, csvReader);

        // Build MCP config from CLI options or use default
        boolean mcpEnabled = true;

        if (cliOptions != null) {
            mcpEnabled = cliOptions.isMcpEnabled();
            if (mcpEnabled) {
                mcpConfig = cliOptions.buildMcpConfig();
                logger.info("MCP Configuration from CLI: {}", mcpConfig);
            } else {
                mcpConfig = null;
                logger.info("MCP server disabled via CLI");
            }
        } else {
            // Default HTTP configuration
            mcpConfig = McpConfig.defaultHttp();
            logger.info("Using default MCP configuration: {}", mcpConfig);
        }

        // Initialize MCP components
        if (mcpEnabled && mcpConfig != null) {
            serverLauncher = new ServerLauncher(mcpConfig, simulator);
        }

        // Create UI components
        canvas = new DeviceCanvas(simulator, CANVAS_WIDTH, CANVAS_HEIGHT);
        controlPanel = new ControlPanel();
        locationListPanel = new LocationListPanel();
        statusBar = new StatusBar();
        logPanel = new McpLogPanel();

        // Setup control handlers
        setupControlHandlers();

        // Layout - Main content area
        SplitPane horizontalSplit = new SplitPane();
        horizontalSplit.setOrientation(javafx.geometry.Orientation.HORIZONTAL);
        horizontalSplit.getItems().addAll(controlPanel, canvas, locationListPanel);
        horizontalSplit.setDividerPositions(0.2, 0.8);

        // Split pane for main content and log panel
        SplitPane verticalSplit = new SplitPane();
        verticalSplit.setOrientation(javafx.geometry.Orientation.VERTICAL);
        verticalSplit.getItems().addAll(horizontalSplit, logPanel);
        verticalSplit.setDividerPositions(0.75);

        // Root layout with status bar at bottom
        BorderPane root = new BorderPane();
        root.setCenter(verticalSplit);
        root.setBottom(statusBar);
        root.setStyle("-fx-background-color: #1a1a1a;");

        // Scene
        Scene scene = new Scene(root, 1400, 900);
        primaryStage.setTitle("Device Simulator - MCP Enabled");
        primaryStage.setScene(scene);
        primaryStage.show();

        // Setup animation timer
        setupAnimationTimer();
        animationTimer.start();

        // Start MCP server in background (if enabled)
        if (mcpEnabled && mcpConfig != null) {
            startMcpServer();
        } else {
            logPanel.logMessage("MCP server disabled");
            statusBar.setStatus("MCP server disabled");
        }

        // Log initial state
        logPanel.logMessage("Application started - Ready to load CSV data");
        statusBar.setStatus("Ready - Load CSV file to begin");

        // Initial render
        canvas.render();
        updateStatusBar();
    }

    /**
     * Setup animation timer for 60 FPS updates.
     */
    private void setupAnimationTimer() {
        animationTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (now - lastUpdateTime >= UPDATE_INTERVAL_NS) {
                    double deltaTime = (now - lastUpdateTime) / 1_000_000_000.0; // Convert to seconds

                    // Update simulator
                    if (isRunning) {
                        simulator.update(deltaTime);
                    }

                    // Render canvas
                    canvas.render();

                    // Update UI
                    updateStatusBar();
                    updateLocationList();

                    lastUpdateTime = now;
                }
            }
        };
    }

    /**
     * Setup control panel event handlers.
     */
    private void setupControlHandlers() {
        // Browse CSV file
        controlPanel.setOnBrowseFile(() -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select CSV File");
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("CSV Files", "*.csv")
            );

            File file = fileChooser.showOpenDialog(null);
            if (file != null) {
                currentCsvPath = file.getAbsolutePath();
                controlPanel.setCsvPath(currentCsvPath);

                // Load headers
                try {
                    List<String> headers = csvReader.getHeaders(currentCsvPath);
                    controlPanel.updateHeaders(headers);
                    logPanel.logMessage("CSV file loaded: " + file.getName() + " (" + headers.size() + " columns)");
                    controlPanel.setStatus("CSV loaded - " + headers.size() + " columns");

                    // Update MCP server's CSV path
                    if (serverLauncher != null) {
                        serverLauncher.updateCsvPath(currentCsvPath);
                    }
                } catch (Exception e) {
                    logger.error("Failed to load CSV headers", e);
                    showError("Failed to load CSV file: " + e.getMessage());
                }
            }
        });

        // Load locations
        controlPanel.setOnLoadLocations(() -> {
            if (currentCsvPath == null) {
                showError("Please select a CSV file first");
                return;
            }

            String xColumn = controlPanel.getXColumn();
            String yColumn = controlPanel.getYColumn();

            if (xColumn == null || yColumn == null) {
                showError("Please select X and Y columns");
                return;
            }

            try {
                Map<String, CsvDataReader.FilterCriteria> filters = controlPanel.getFilters();
                List<Location> locations = csvReader.readLocations(currentCsvPath, xColumn, yColumn, filters);

                if (locations.isEmpty()) {
                    showError("No locations found matching the criteria");
                    return;
                }

                simulator.setTargetLocations(locations);
                simulator.setAutoAdvance(true); // Enable auto-advance for sequential visiting
                logPanel.logMessage(String.format("Loaded %d locations from CSV (auto-advance enabled)", locations.size()));
                controlPanel.setStatus(String.format("Loaded %d locations", locations.size()));
                statusBar.showSuccess(String.format("Loaded %d locations successfully", locations.size()));

                updateLocationList();
                canvas.render();

            } catch (Exception e) {
                logger.error("Failed to load locations", e);
                showError("Failed to load locations: " + e.getMessage());
            }
        });

        // Start/Pause
        controlPanel.setOnStartPause(() -> {
            isRunning = !isRunning;
            controlPanel.setRunning(isRunning);

            if (isRunning) {
                simulator.startMovement();
                logPanel.logDeviceAction("Movement started");
                statusBar.setStatus("Device moving...");
            } else {
                simulator.stopMovement();
                logPanel.logDeviceAction("Movement paused");
                statusBar.setStatus("Movement paused");
            }
        });

        // Mark visited
        controlPanel.setOnMarkVisited(() -> {
            Location currentTarget = simulator.getCurrentTarget();
            if (currentTarget != null) {
                simulator.markCurrentAsVisited();
                logPanel.logDeviceAction("Marked location as visited: " + currentTarget.getId());
                updateLocationList();

                Location newTarget = simulator.getCurrentTarget();
                if (newTarget != null) {
                    statusBar.setStatus("Moved to next target: " + newTarget.getId());
                } else {
                    statusBar.showSuccess("All locations visited!");
                    isRunning = false;
                    controlPanel.setRunning(false);
                }
            }
        });

        // Reset
        controlPanel.setOnReset(() -> {
            simulator.reset();
            isRunning = false;
            controlPanel.setRunning(false);
            logPanel.logDeviceAction("Simulator reset");
            statusBar.setStatus("Simulator reset");
            updateLocationList();
            canvas.render();
        });

        // Speed change
        controlPanel.setOnSpeedChange(() -> {
            double speed = controlPanel.getSpeed();
            simulator.setSpeed(speed);
            logPanel.logDeviceAction(String.format("Speed set to %.1f u/s", speed));
        });

        // Acceleration change
        controlPanel.setOnAccelerationChange(() -> {
            double acceleration = controlPanel.getAcceleration();
            simulator.setAcceleration(acceleration);
            logPanel.logDeviceAction(String.format("Acceleration set to %.1f u/s²", acceleration));
        });
    }

    /**
     * Update status bar with current device state.
     */
    private void updateStatusBar() {
        Platform.runLater(() -> {
            DeviceState state = simulator.getState();
            Location currentTarget = simulator.getCurrentTarget();

            statusBar.setPosition(state.getX(), state.getY());
            statusBar.setSpeed(state.getSpeed());

            if (currentTarget != null) {
                statusBar.setTarget(currentTarget.getId(), currentTarget.getX(), currentTarget.getY());
            } else {
                statusBar.setTarget(null, 0, 0);
            }
        });
    }

    /**
     * Update location list panel.
     */
    private void updateLocationList() {
        List<Location> locations = simulator.getAllLocations();
        int currentIndex = simulator.getCurrentTargetIndex();
        locationListPanel.updateLocations(locations, currentIndex);
    }

    /**
     * Start MCP server asynchronously.
     */
    private void startMcpServer() {
        statusBar.setStatus("Starting MCP server...");
        logPanel.logMessage("Starting MCP server...");

        serverLauncher.startAsync().thenAccept(success -> {
            Platform.runLater(() -> {
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
     * Show error dialog.
     */
    private void showError(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    @Override
    public void stop() {
        logger.info("Stopping application");

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

    /**
     * Main entry point.
     */
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
