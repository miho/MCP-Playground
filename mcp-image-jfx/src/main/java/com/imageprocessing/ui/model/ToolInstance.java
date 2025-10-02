package com.imageprocessing.ui.model;

import javafx.beans.property.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a tool instance in the execution pipeline.
 * Tracks parameter values, execution status, and results.
 */
public class ToolInstance {

    public enum Status {
        PENDING("Pending", "#3498db"),
        RUNNING("Running", "#f39c12"),
        COMPLETED("Completed", "#27ae60"),
        ERROR("Error", "#e74c3c");

        private final String displayName;
        private final String color;

        Status(String displayName, String color) {
            this.displayName = displayName;
            this.color = color;
        }

        public String getDisplayName() { return displayName; }
        public String getColor() { return color; }
    }

    private final ToolMetadata metadata;
    private final Map<String, Object> parameterValues;
    private final ObjectProperty<Status> status;
    private final StringProperty errorMessage;
    private final StringProperty result;

    public ToolInstance(ToolMetadata metadata) {
        this.metadata = metadata;
        this.parameterValues = new HashMap<>();
        this.status = new SimpleObjectProperty<>(Status.PENDING);
        this.errorMessage = new SimpleStringProperty("");
        this.result = new SimpleStringProperty("");

        // Initialize parameters with default values
        for (ToolMetadata.ParameterDefinition param : metadata.getParameters()) {
            if (param.getDefaultValue() != null) {
                parameterValues.put(param.getName(), param.getDefaultValue());
            }
        }
    }

    public ToolMetadata getMetadata() {
        return metadata;
    }

    public String getName() {
        return metadata.getName();
    }

    public String getDescription() {
        return metadata.getDescription();
    }

    // Parameter management
    public void setParameter(String name, Object value) {
        parameterValues.put(name, value);
    }

    public Object getParameter(String name) {
        return parameterValues.get(name);
    }

    public Map<String, Object> getParameterValues() {
        return new HashMap<>(parameterValues);
    }

    // Status management
    public Status getStatus() {
        return status.get();
    }

    public void setStatus(Status status) {
        this.status.set(status);
    }

    public ObjectProperty<Status> statusProperty() {
        return status;
    }

    // Error message
    public String getErrorMessage() {
        return errorMessage.get();
    }

    public void setErrorMessage(String message) {
        this.errorMessage.set(message);
    }

    public StringProperty errorMessageProperty() {
        return errorMessage;
    }

    // Result
    public String getResult() {
        return result.get();
    }

    public void setResult(String result) {
        this.result.set(result);
    }

    public StringProperty resultProperty() {
        return result;
    }

    /**
     * Reset the tool instance to pending state.
     */
    public void reset() {
        status.set(Status.PENDING);
        errorMessage.set("");
        result.set("");
    }

    /**
     * Get a summary of parameter values for display.
     */
    public String getParameterSummary() {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (Map.Entry<String, Object> entry : parameterValues.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().toString().isEmpty()) {
                if (count > 0) sb.append(", ");
                sb.append(entry.getKey()).append("=").append(entry.getValue());
                count++;
                if (count >= 3) {
                    sb.append("...");
                    break;
                }
            }
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return String.format("%s [%s]", getName(), getStatus().getDisplayName());
    }
}
