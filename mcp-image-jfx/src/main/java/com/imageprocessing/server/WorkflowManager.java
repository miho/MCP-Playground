package com.imageprocessing.server;

import com.imageprocessing.ui.MainController;
import com.imageprocessing.ui.model.ToolInstance;
import com.imageprocessing.ui.model.ToolMetadata;
import com.imageprocessing.ui.model.ToolRegistry;
import javafx.application.Platform;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Bridge between MCP server and JavaFX UI workflow.
 * Allows external MCP clients to manipulate the UI's workflow.
 */
public class WorkflowManager {

    private static MainController mainController;
    private static IntermediateResultCache cache;

    /**
     * Set the main controller reference (called during app initialization).
     */
    public static void setMainController(MainController controller) {
        mainController = controller;
    }

    /**
     * Set the cache reference for accessing image results.
     */
    public static void setCache(IntermediateResultCache resultCache) {
        cache = resultCache;
    }

    /**
     * Add a tool to the UI workflow.
     * This is called from MCP tools to populate the UI's workflow list.
     */
    public static CompletableFuture<String> addToolToWorkflow(String toolName, Map<String, Object> parameters) {
        if (mainController == null) {
            return CompletableFuture.completedFuture("Error: UI not initialized");
        }

        CompletableFuture<String> future = new CompletableFuture<>();

        Platform.runLater(() -> {
            try {
                // Find tool metadata
                Optional<ToolMetadata> metadataOpt = ToolRegistry.getToolByName(toolName);
                if (metadataOpt.isEmpty()) {
                    future.complete("Error: Unknown tool: " + toolName);
                    return;
                }

                ToolMetadata metadata = metadataOpt.get();
                ToolInstance instance = new ToolInstance(metadata);

                // Set parameters
                for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                    instance.setParameter(entry.getKey(), entry.getValue());
                }

                // Add to workflow
                mainController.getWorkflowModel().addTool(instance);

                int position = mainController.getWorkflowModel().size();
                future.complete(String.format("Added '%s' to workflow (position %d)", toolName, position));

            } catch (Exception e) {
                future.complete("Error: " + e.getMessage());
            }
        });

        return future;
    }

    /**
     * Clear the entire workflow.
     */
    public static CompletableFuture<String> clearWorkflow() {
        if (mainController == null) {
            return CompletableFuture.completedFuture("Error: UI not initialized");
        }

        CompletableFuture<String> future = new CompletableFuture<>();

        Platform.runLater(() -> {
            try {
                int count = mainController.getWorkflowModel().size();
                mainController.getWorkflowModel().clear();
                future.complete(String.format("Cleared workflow (%d tools removed)", count));
            } catch (Exception e) {
                future.complete("Error: " + e.getMessage());
            }
        });

        return future;
    }

    /**
     * Execute the workflow (trigger UI execution) and return all image results.
     */
    public static CompletableFuture<Map<String, Object>> executeWorkflow() {
        if (mainController == null) {
            Map<String, Object> errorResult = new java.util.HashMap<>();
            errorResult.put("error", "UI not initialized");
            return CompletableFuture.completedFuture(errorResult);
        }

        CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();

        Platform.runLater(() -> {
            try {
                if (mainController.getWorkflowModel().isEmpty()) {
                    Map<String, Object> errorResult = new java.util.HashMap<>();
                    errorResult.put("error", "Workflow is empty");
                    future.complete(errorResult);
                    return;
                }

                int toolCount = mainController.getWorkflowModel().size();

                // Trigger execution and wait for completion
                mainController.executePipeline().thenAccept(v -> {
                    try {
                        // Collect all output images from completed tools
                        List<Map<String, String>> images = new ArrayList<>();
                        var workflow = mainController.getWorkflowModel();

                        for (ToolInstance tool : workflow.getToolInstances()) {
                            if (tool.getStatus() == ToolInstance.Status.COMPLETED) {
                                // Check for output_key
                                String outputKey = (String) tool.getParameter("output_key");
                                if (outputKey != null && !outputKey.isBlank() && cache != null && cache.containsKey(outputKey)) {
                                    try {
                                        var mat = cache.get(outputKey);
                                        String base64 = OpenCVImageProcessor.matToBase64Png(mat);

                                        Map<String, String> imageInfo = new java.util.HashMap<>();
                                        imageInfo.put("key", outputKey);
                                        imageInfo.put("tool", tool.getName());
                                        imageInfo.put("data", "data:image/png;base64," + base64);
                                        images.add(imageInfo);
                                    } catch (Exception e) {
                                        System.err.println("Error converting image " + outputKey + ": " + e.getMessage());
                                    }
                                }

                                // Special case: load_image uses result_key
                                if ("load_image".equals(tool.getName())) {
                                    String resultKey = (String) tool.getParameter("result_key");
                                    if (resultKey != null && !resultKey.isBlank() && cache != null && cache.containsKey(resultKey)) {
                                        try {
                                            var mat = cache.get(resultKey);
                                            String base64 = OpenCVImageProcessor.matToBase64Png(mat);

                                            Map<String, String> imageInfo = new java.util.HashMap<>();
                                            imageInfo.put("key", resultKey);
                                            imageInfo.put("tool", tool.getName());
                                            imageInfo.put("data", "data:image/png;base64," + base64);
                                            images.add(imageInfo);
                                        } catch (Exception e) {
                                            System.err.println("Error converting image " + resultKey + ": " + e.getMessage());
                                        }
                                    }
                                }
                            }
                        }

                        var stats = workflow.getStats();
                        Map<String, Object> result = new java.util.HashMap<>();
                        result.put("status", "completed");
                        result.put("totalTools", toolCount);
                        result.put("completed", stats.getCompleted());
                        result.put("errors", stats.getError());
                        result.put("imageCount", images.size());
                        result.put("images", images);

                        future.complete(result);

                    } catch (Exception e) {
                        Map<String, Object> errorResult = new java.util.HashMap<>();
                        errorResult.put("error", "Failed to collect results: " + e.getMessage());
                        future.complete(errorResult);
                    }
                }).exceptionally(throwable -> {
                    Map<String, Object> errorResult = new java.util.HashMap<>();
                    errorResult.put("error", "Execution failed: " + throwable.getMessage());
                    future.complete(errorResult);
                    return null;
                });

            } catch (Exception e) {
                Map<String, Object> errorResult = new java.util.HashMap<>();
                errorResult.put("error", e.getMessage());
                future.complete(errorResult);
            }
        });

        return future;
    }

    /**
     * Get current workflow status.
     */
    public static CompletableFuture<String> getWorkflowStatus() {
        if (mainController == null) {
            return CompletableFuture.completedFuture("Error: UI not initialized");
        }

        CompletableFuture<String> future = new CompletableFuture<>();

        Platform.runLater(() -> {
            try {
                var workflow = mainController.getWorkflowModel();
                int total = workflow.size();

                if (total == 0) {
                    future.complete("Workflow is empty");
                    return;
                }

                var stats = workflow.getStats();
                StringBuilder status = new StringBuilder();
                status.append(String.format("Workflow: %d tools total\n", total));
                status.append(String.format("- Pending: %d\n", stats.getPending()));
                status.append(String.format("- Running: %d\n", stats.getRunning()));
                status.append(String.format("- Completed: %d\n", stats.getCompleted()));
                status.append(String.format("- Error: %d\n", stats.getError()));

                status.append("\nTools:\n");
                int i = 1;
                for (ToolInstance tool : workflow.getToolInstances()) {
                    status.append(String.format("%d. %s [%s]\n", i++, tool.getName(), tool.getStatus()));
                }

                future.complete(status.toString());

            } catch (Exception e) {
                future.complete("Error: " + e.getMessage());
            }
        });

        return future;
    }
}
