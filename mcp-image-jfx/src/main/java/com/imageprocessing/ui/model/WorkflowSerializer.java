package com.imageprocessing.ui.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import javafx.collections.ObservableList;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Handles JSON serialization and deserialization of workflow pipelines.
 * Uses Jackson for robust JSON processing.
 */
public class WorkflowSerializer {

    private static final String WORKFLOW_VERSION = "1.0";
    private final ObjectMapper objectMapper;

    public WorkflowSerializer() {
        this.objectMapper = new ObjectMapper();
        // Enable pretty printing for better readability
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Save a workflow to a JSON file.
     *
     * @param tools List of tool instances in the pipeline
     * @param file Target file to save to
     * @throws IOException if file I/O fails
     */
    public void saveWorkflow(ObservableList<ToolInstance> tools, File file) throws IOException {
        WorkflowData workflowData = new WorkflowData();
        workflowData.version = WORKFLOW_VERSION;
        workflowData.tools = new ArrayList<>();

        for (ToolInstance tool : tools) {
            ToolData toolData = new ToolData();
            toolData.name = tool.getName();
            toolData.parameters = new HashMap<>(tool.getParameterValues());
            workflowData.tools.add(toolData);
        }

        objectMapper.writeValue(file, workflowData);
    }

    /**
     * Load a workflow from a JSON file.
     *
     * @param file Source file to load from
     * @return List of tool instances representing the loaded workflow
     * @throws IOException if file I/O fails
     * @throws WorkflowLoadException if workflow format is invalid or tools are not found
     */
    public List<ToolInstance> loadWorkflow(File file) throws IOException, WorkflowLoadException {
        WorkflowData workflowData = objectMapper.readValue(file, WorkflowData.class);

        // Validate version (future compatibility)
        if (workflowData.version == null || workflowData.version.isEmpty()) {
            throw new WorkflowLoadException("Workflow version is missing");
        }

        if (workflowData.tools == null || workflowData.tools.isEmpty()) {
            throw new WorkflowLoadException("Workflow contains no tools");
        }

        List<ToolInstance> loadedTools = new ArrayList<>();

        for (int i = 0; i < workflowData.tools.size(); i++) {
            ToolData toolData = workflowData.tools.get(i);

            // Validate tool data
            if (toolData.name == null || toolData.name.isEmpty()) {
                throw new WorkflowLoadException("Tool at index " + i + " has no name");
            }

            // Find tool metadata from registry
            Optional<ToolMetadata> toolMetadataOpt = ToolRegistry.getToolByName(toolData.name);
            if (toolMetadataOpt.isEmpty()) {
                throw new WorkflowLoadException(
                    "Tool '" + toolData.name + "' not found in registry. " +
                    "This workflow may have been created with a different version."
                );
            }

            ToolMetadata metadata = toolMetadataOpt.get();
            ToolInstance instance = new ToolInstance(metadata);

            // Restore parameters
            if (toolData.parameters != null) {
                for (Map.Entry<String, Object> entry : toolData.parameters.entrySet()) {
                    String paramName = entry.getKey();
                    Object paramValue = entry.getValue();

                    // Validate parameter exists in tool metadata
                    boolean paramExists = metadata.getParameters().stream()
                        .anyMatch(p -> p.getName().equals(paramName));

                    if (paramExists && paramValue != null) {
                        instance.setParameter(paramName, paramValue);
                    }
                    // Silently skip parameters that don't exist (forward compatibility)
                }
            }

            loadedTools.add(instance);
        }

        return loadedTools;
    }

    /**
     * JSON structure for workflow data.
     */
    private static class WorkflowData {
        @JsonProperty("version")
        public String version;

        @JsonProperty("tools")
        public List<ToolData> tools;
    }

    /**
     * JSON structure for individual tool data.
     */
    private static class ToolData {
        @JsonProperty("name")
        public String name;

        @JsonProperty("parameters")
        public Map<String, Object> parameters;
    }

    /**
     * Exception thrown when workflow loading fails.
     */
    public static class WorkflowLoadException extends Exception {
        public WorkflowLoadException(String message) {
            super(message);
        }

        public WorkflowLoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
