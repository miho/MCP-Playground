package com.imageprocessing.ui;

import com.imageprocessing.server.*;
import com.imageprocessing.execution.DirectToolExecutor;
import com.imageprocessing.ui.components.*;
import com.imageprocessing.ui.model.*;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Main controller coordinating UI components and application logic.
 * Now uses embedded MCP server and direct tool execution.
 */
public class MainController {

    private final WorkflowModel workflowModel;
    private final ToolCollectionPane toolCollectionPane;
    private final ToolPipelinePane toolPipelinePane;
    private final ParameterEditorPane parameterEditorPane;
    private final ControlBar controlBar;
    private final StatusBar statusBar;
    private final ImagePreviewPanel imagePreviewPanel;

    // Embedded server components
    private McpConfig mcpConfig;
    private ServerLauncher serverLauncher;
    private DirectToolExecutor directExecutor;
    private OpenCVImageProcessor processor;
    private IntermediateResultCache cache;
    private TextResultCache textCache;

    private boolean mcpServerRunning = false;
    private boolean mcpServerEnabled = true;
    private boolean isExecuting = false;

    /**
     * Create MainController with default HTTP MCP configuration.
     * @deprecated Use MainController(McpConfig, boolean) instead to specify configuration
     */
    @Deprecated
    public MainController() {
        this(McpConfig.defaultHttp(), true);
    }

    /**
     * Create MainController with specified MCP configuration.
     * @param mcpConfig MCP server configuration (null if MCP disabled)
     * @param mcpEnabled whether to start the embedded MCP server
     */
    public MainController(McpConfig mcpConfig, boolean mcpEnabled) {
        this.mcpConfig = mcpConfig;
        this.mcpServerEnabled = mcpEnabled;
        this.workflowModel = new WorkflowModel();

        // Initialize UI components
        this.toolCollectionPane = new ToolCollectionPane();
        this.toolPipelinePane = new ToolPipelinePane(workflowModel);
        this.parameterEditorPane = new ParameterEditorPane();
        this.controlBar = new ControlBar();
        this.statusBar = new StatusBar();
        this.imagePreviewPanel = new ImagePreviewPanel();

        // Connect parameter editor to workflow model
        this.parameterEditorPane.setWorkflowModel(workflowModel);

        // Setup event handlers
        setupEventHandlers();
    }

    /**
     * Initialize embedded MCP server and shared components.
     * Called by ImageProcessingApp after UI is ready.
     */
    public void initializeEmbeddedServer() {
        try {
            // Initialize OpenCV
            nu.pattern.OpenCV.loadLocally();
            statusBar.setStatus("OpenCV initialized: " + org.opencv.core.Core.VERSION);

            // Initialize shared components
            this.processor = new OpenCVImageProcessor();
            this.cache = new IntermediateResultCache();
            this.textCache = new TextResultCache();
            this.directExecutor = new DirectToolExecutor(processor, cache);

            // Connect WorkflowManager to this controller for MCP access
            WorkflowManager.setMainController(this);
            WorkflowManager.setCache(cache);

            // Connect image preview panel to cache
            this.imagePreviewPanel.setCache(cache);

            // Set callback for display_image tool
            this.directExecutor.setDisplayImageCallback(cacheKey -> {
                Platform.runLater(() -> {
                    imagePreviewPanel.addImageResult(cacheKey, null);
                });
            });

            // Only start MCP server if enabled and config is provided
            if (!mcpServerEnabled) {
                statusBar.setStatus("MCP server disabled");
                controlBar.setServerRunning(false);
                return;
            }

            if (mcpConfig == null) {
                statusBar.showError("MCP config is null, cannot start server");
                controlBar.setServerRunning(false);
                return;
            }

            // Start embedded MCP server in background
            this.serverLauncher = new ServerLauncher(mcpConfig, processor, cache, textCache);
            serverLauncher.startAsync().thenAccept(success -> {
                Platform.runLater(() -> {
                    if (success) {
                        mcpServerRunning = true;
                        controlBar.setServerRunning(true);
                        statusBar.showSuccess("MCP Server running: " + serverLauncher.getEndpointUrl());
                        resetStatusAfterDelay(3000);
                    } else {
                        statusBar.showError("Failed to start embedded MCP server");
                        resetStatusAfterDelay(3000);
                    }
                });
            });

        } catch (Exception e) {
            statusBar.showError("Initialization failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void setupEventHandlers() {
        // Pipeline tool selection -> update parameter editor
        toolPipelinePane.setOnToolSelected(tool -> {
            parameterEditorPane.refreshAvailableKeys();
            parameterEditorPane.editTool(tool);
            statusBar.setStatus("Editing: " + tool.getName());
        });

        // Pipeline changes -> update status
        toolPipelinePane.setOnPipelineChanged(() -> {
            statusBar.updateStats(workflowModel);
            updateUIState();
        });

        // Control bar actions
        controlBar.setOnPlayAction(this::executePipeline);
        controlBar.setOnStopAction(this::stopExecution);
        controlBar.setOnLaunchServerAction(this::showServerInfo);
        controlBar.setOnServerSettingsAction(this::showServerSettings);
        controlBar.setOnSaveWorkflowAction(this::saveWorkflow);
        controlBar.setOnLoadWorkflowAction(this::loadWorkflow);

        // Listen to workflow changes
        workflowModel.getToolInstances().addListener(
            (javafx.collections.ListChangeListener.Change<? extends ToolInstance> c) -> {
                while (c.next()) {
                    statusBar.updateStats(workflowModel);
                }
            }
        );
    }

    /**
     * Execute the entire pipeline using direct execution.
     * Can be called from UI button or MCP.
     * @return CompletableFuture that completes when pipeline execution finishes
     */
    public CompletableFuture<Void> executePipeline() {
        System.out.println("Execute Pipeline button pressed");

        if (workflowModel.isEmpty()) {
            System.out.println("Pipeline is empty, showing warning");
            showWarning("Empty Pipeline", "Please add tools to the pipeline before executing.");
            return CompletableFuture.completedFuture(null);
        }

        if (directExecutor == null) {
            System.out.println("Direct executor is null, showing warning");
            showWarning("System Not Ready", "Direct executor not initialized. Please wait a moment and try again.");
            return CompletableFuture.completedFuture(null);
        }

        if (isExecuting) {
            System.out.println("Already executing, showing warning");
            showWarning("Already Executing", "Pipeline is already executing. Please wait for it to complete.");
            return CompletableFuture.completedFuture(null);
        }

        System.out.println("Starting pipeline execution with " + workflowModel.size() + " tools");

        // Reset all tools to pending
        workflowModel.resetAll();

        // Clear previous image results
        imagePreviewPanel.clear();

        // Start execution
        isExecuting = true;
        controlBar.setExecuting(true);
        statusBar.setStatus("Executing pipeline...");
        statusBar.showProgress(0);

        // Execute pipeline using direct execution
        int total = workflowModel.size();
        System.out.println("[MainController] Calling directExecutor.executePipeline() with " + total + " tools");

        return directExecutor.executePipeline(
            workflowModel.getToolInstances(),
            tool -> {
                // Progress callback - runs on background thread, use Platform.runLater
                Platform.runLater(() -> {
                    int index = workflowModel.getToolInstances().indexOf(tool);
                    if (index >= 0) {
                        System.out.println("[MainController] Progress callback: Tool " + (index + 1) + "/" + total + " - " + tool.getName() + " - Status: " + tool.getStatus());
                        statusBar.setStatus("Executing: " + tool.getName());
                        statusBar.showProgress((double) (index + 1) / total);
                    }

                    // Add image result if tool completed successfully and has output_key
                    if (tool.getStatus() == ToolInstance.Status.COMPLETED) {
                        String outputKey = (String) tool.getParameter("output_key");
                        String outputPath = (String) tool.getParameter("output_path");

                        if (outputKey != null && !outputKey.isBlank()) {
                            System.out.println("[MainController] Adding image result to preview panel: " + outputKey);
                            imagePreviewPanel.addImageResult(outputKey, outputPath);
                        }
                    }
                });
            }
        ).thenAccept(result -> {
            System.out.println("[MainController] Pipeline execution completed callback invoked");
            Platform.runLater(() -> {
                System.out.println("[MainController] Updating UI after pipeline completion");
                isExecuting = false;
                controlBar.setExecuting(false);
                statusBar.hideProgress();

                if (result.hasErrors()) {
                    System.out.println("[MainController] Pipeline completed with errors: " + result.getErrors());
                    statusBar.showError("Pipeline completed with errors");
                    showError("Execution Errors", result.getErrors());
                } else if (result.isCancelled()) {
                    System.out.println("[MainController] Pipeline was cancelled");
                    statusBar.setStatus("Pipeline cancelled");
                } else {
                    System.out.println("[MainController] Pipeline completed successfully");
                    statusBar.showSuccess("Pipeline completed successfully!");
                }

                resetStatusAfterDelay(5000);
            });
        }).exceptionally(throwable -> {
            System.err.println("[MainController] Pipeline execution exception: " + throwable.getMessage());
            throwable.printStackTrace();
            Platform.runLater(() -> {
                isExecuting = false;
                controlBar.setExecuting(false);
                statusBar.hideProgress();
                statusBar.showError("Pipeline execution failed");
                showError("Execution Error", "Pipeline execution failed: " + throwable.getMessage());
                resetStatusAfterDelay(5000);
            });
            return null;
        });
    }

    /**
     * Stop pipeline execution.
     */
    private void stopExecution() {
        isExecuting = false;
        if (directExecutor != null) {
            directExecutor.cancel();
        }
        controlBar.setExecuting(false);
        statusBar.setStatus("Execution stopped");
        statusBar.hideProgress();
    }

    /**
     * Show server information dialog.
     */
    private void showServerInfo() {
        if (serverLauncher != null && serverLauncher.isRunning()) {
            Alert info = new Alert(Alert.AlertType.INFORMATION);
            info.setTitle("MCP Server Status");
            info.setHeaderText("Embedded MCP Server");
            info.setContentText(
                "Status: Running\n" +
                "Endpoint: " + serverLauncher.getEndpointUrl() + "\n\n" +
                "The MCP server is embedded in this application and runs in the background.\n" +
                "External MCP clients can connect to this server for remote access."
            );
            info.showAndWait();
        } else {
            showWarning("Server Not Running", "The embedded MCP server is not currently running.");
        }
    }

    /**
     * Show server configuration dialog.
     */
    private void showServerSettings() {
        Alert info = new Alert(Alert.AlertType.INFORMATION);
        info.setTitle("Server Settings");
        info.setHeaderText("Embedded Server Configuration");

        StringBuilder content = new StringBuilder();
        content.append("Server Status: ").append(mcpServerEnabled ? "Enabled" : "Disabled").append("\n\n");

        if (mcpConfig != null) {
            content.append("Current Configuration:\n");
            content.append("Transport Mode: ").append(mcpConfig.getTransportMode()).append("\n");

            if (mcpConfig.getTransportMode() == McpConfig.TransportMode.HTTP) {
                content.append("HTTP Host: ").append(mcpConfig.getHttpHost()).append("\n");
                content.append("HTTP Port: ").append(mcpConfig.getHttpPort()).append("\n");
                content.append("HTTP Endpoint: ").append(mcpConfig.getHttpEndpoint()).append("\n");
                content.append("Full URL: ").append(mcpConfig.getHttpUrl()).append("\n");
            } else {
                content.append("Mode: Standard input/output\n");
            }

            content.append("\nLogging: ").append(mcpConfig.isCaptureServerLogs() ? "Enabled" : "Disabled").append("\n");
            if (mcpConfig.isCaptureServerLogs()) {
                content.append("Log Directory: ").append(mcpConfig.getLogDirectory()).append("\n");
            }
        } else {
            content.append("No configuration available\n");
        }

        content.append("\nNote: Configuration can be set via CLI arguments.\n");
        content.append("Use --help to see available options.\n");
        content.append("Configuration changes require restarting the application.");

        info.setContentText(content.toString());
        info.showAndWait();
    }

    private void resetStatusAfterDelay(int delayMs) {
        new Thread(() -> {
            try {
                Thread.sleep(delayMs);
                Platform.runLater(() -> {
                    statusBar.resetStatus();
                    statusBar.setStatus("Ready");
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    /**
     * Save current workflow to file.
     */
    private void saveWorkflow() {
        if (workflowModel.isEmpty()) {
            showWarning("Empty Workflow", "No tools to save.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Workflow");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Workflow Files", "*.json")
        );

        // Set default filename
        fileChooser.setInitialFileName("workflow.json");

        File file = fileChooser.showSaveDialog(null);

        if (file != null) {
            try {
                WorkflowSerializer serializer = new WorkflowSerializer();
                serializer.saveWorkflow(workflowModel.getToolInstances(), file);

                statusBar.showSuccess("Workflow saved to: " + file.getName());
                resetStatusAfterDelay(3000);
            } catch (Exception e) {
                statusBar.showError("Failed to save workflow: " + e.getMessage());
                showError("Save Failed", "Could not save workflow to file:\n" + e.getMessage());
                resetStatusAfterDelay(3000);
            }
        }
    }

    /**
     * Load workflow from file.
     */
    private void loadWorkflow() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Load Workflow");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Workflow Files", "*.json")
        );
        File file = fileChooser.showOpenDialog(null);

        if (file != null) {
            try {
                WorkflowSerializer serializer = new WorkflowSerializer();
                List<ToolInstance> loadedTools = serializer.loadWorkflow(file);

                // Clear existing workflow
                workflowModel.clear();

                // Add loaded tools to the workflow
                for (ToolInstance tool : loadedTools) {
                    workflowModel.addTool(tool);
                }

                statusBar.showSuccess("Workflow loaded from: " + file.getName() + " (" + loadedTools.size() + " tools)");
                resetStatusAfterDelay(3000);

            } catch (WorkflowSerializer.WorkflowLoadException e) {
                statusBar.showError("Failed to load workflow: " + e.getMessage());
                showError("Load Failed", "Could not load workflow from file:\n\n" + e.getMessage());
                resetStatusAfterDelay(3000);
            } catch (Exception e) {
                statusBar.showError("Failed to load workflow: " + e.getMessage());
                showError("Load Failed", "An error occurred while loading the workflow:\n\n" + e.getMessage());
                resetStatusAfterDelay(3000);
            }
        }
    }

    /**
     * Update UI state based on application state.
     */
    private void updateUIState() {
        statusBar.updateStats(workflowModel);
    }

    /**
     * Show warning dialog.
     */
    private void showWarning(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Show error dialog.
     */
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Cleanup resources on application shutdown.
     */
    public void shutdown() {
        if (serverLauncher != null) {
            serverLauncher.shutdown();
        }
        if (directExecutor != null) {
            directExecutor.shutdown();
        }
        if (cache != null) {
            cache.clear();
        }
    }

    // Getters for UI components
    public ToolCollectionPane getToolCollectionPane() {
        return toolCollectionPane;
    }

    public ToolPipelinePane getToolPipelinePane() {
        return toolPipelinePane;
    }

    public ParameterEditorPane getParameterEditorPane() {
        return parameterEditorPane;
    }

    public ControlBar getControlBar() {
        return controlBar;
    }

    public StatusBar getStatusBar() {
        return statusBar;
    }

    public WorkflowModel getWorkflowModel() {
        return workflowModel;
    }

    public ImagePreviewPanel getImagePreviewPanel() {
        return imagePreviewPanel;
    }
}
