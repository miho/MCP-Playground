package com.imageprocessing.ui.model;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Model representing a workflow pipeline of tool instances.
 * Manages the execution sequence and state of multiple tools.
 */
public class WorkflowModel {

    private final ObservableList<ToolInstance> toolInstances;

    public WorkflowModel() {
        this.toolInstances = FXCollections.observableArrayList();
    }

    public ObservableList<ToolInstance> getToolInstances() {
        return toolInstances;
    }

    /**
     * Add a tool to the pipeline.
     */
    public void addTool(ToolMetadata metadata) {
        ToolInstance instance = new ToolInstance(metadata);
        toolInstances.add(instance);
    }

    /**
     * Add a tool instance directly to the pipeline.
     * Used for loading workflows from files.
     */
    public void addTool(ToolInstance instance) {
        toolInstances.add(instance);
    }

    /**
     * Insert a tool at a specific position.
     */
    public void insertTool(int index, ToolMetadata metadata) {
        ToolInstance instance = new ToolInstance(metadata);
        if (index >= 0 && index <= toolInstances.size()) {
            toolInstances.add(index, instance);
        } else {
            toolInstances.add(instance);
        }
    }

    /**
     * Remove a tool from the pipeline.
     */
    public void removeTool(ToolInstance instance) {
        toolInstances.remove(instance);
    }

    /**
     * Move a tool to a new position.
     */
    public void moveTool(int fromIndex, int toIndex) {
        if (fromIndex >= 0 && fromIndex < toolInstances.size() &&
            toIndex >= 0 && toIndex < toolInstances.size()) {
            ToolInstance instance = toolInstances.remove(fromIndex);
            toolInstances.add(toIndex, instance);
        }
    }

    /**
     * Clear all tools from the pipeline.
     */
    public void clear() {
        toolInstances.clear();
    }

    /**
     * Reset all tools to pending state.
     */
    public void resetAll() {
        for (ToolInstance instance : toolInstances) {
            instance.reset();
        }
    }

    /**
     * Check if the pipeline is empty.
     */
    public boolean isEmpty() {
        return toolInstances.isEmpty();
    }

    /**
     * Get the number of tools in the pipeline.
     */
    public int size() {
        return toolInstances.size();
    }

    /**
     * Get execution statistics.
     */
    public ExecutionStats getStats() {
        int pending = 0, running = 0, completed = 0, error = 0;

        for (ToolInstance instance : toolInstances) {
            switch (instance.getStatus()) {
                case PENDING: pending++; break;
                case RUNNING: running++; break;
                case COMPLETED: completed++; break;
                case ERROR: error++; break;
            }
        }

        return new ExecutionStats(pending, running, completed, error);
    }

    public static class ExecutionStats {
        private final int pending;
        private final int running;
        private final int completed;
        private final int error;

        public ExecutionStats(int pending, int running, int completed, int error) {
            this.pending = pending;
            this.running = running;
            this.completed = completed;
            this.error = error;
        }

        public int getPending() { return pending; }
        public int getRunning() { return running; }
        public int getCompleted() { return completed; }
        public int getError() { return error; }
        public int getTotal() { return pending + running + completed + error; }
    }
}
