package com.imageprocessing.execution;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of pipeline execution, including errors and cancellation state.
 */
public class PipelineResult {
    private final List<String> errors = new ArrayList<>();
    private boolean cancelled = false;

    public void addError(String error) {
        errors.add(error);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public String getErrors() {
        return String.join("\n", errors);
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public boolean isCancelled() {
        return cancelled;
    }
}
