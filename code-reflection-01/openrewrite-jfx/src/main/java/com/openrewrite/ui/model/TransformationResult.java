package com.openrewrite.ui.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Model class representing the result of applying an OpenRewrite recipe.
 * Contains the original code, transformed code, and metadata about the transformation.
 */
public class TransformationResult {

    private String originalCode;
    private String transformedCode;
    private String recipeName;
    private String recipeDisplayName;
    private boolean hasChanges;
    private String diff;
    private LocalDateTime timestamp;
    private List<ChangeDetail> changes;
    private String errorMessage;
    private boolean success;

    public TransformationResult() {
        this.timestamp = LocalDateTime.now();
        this.changes = new ArrayList<>();
        this.success = true;
    }

    public TransformationResult(String originalCode, String transformedCode, String recipeName) {
        this();
        this.originalCode = originalCode;
        this.transformedCode = transformedCode;
        this.recipeName = recipeName;
        this.hasChanges = !originalCode.equals(transformedCode);
    }

    // Getters and setters

    public String getOriginalCode() {
        return originalCode;
    }

    public void setOriginalCode(String originalCode) {
        this.originalCode = originalCode;
    }

    public String getTransformedCode() {
        return transformedCode;
    }

    public void setTransformedCode(String transformedCode) {
        this.transformedCode = transformedCode;
        if (this.originalCode != null) {
            this.hasChanges = !this.originalCode.equals(transformedCode);
        }
    }

    public String getRecipeName() {
        return recipeName;
    }

    public void setRecipeName(String recipeName) {
        this.recipeName = recipeName;
    }

    public String getRecipeDisplayName() {
        return recipeDisplayName != null ? recipeDisplayName : recipeName;
    }

    public void setRecipeDisplayName(String recipeDisplayName) {
        this.recipeDisplayName = recipeDisplayName;
    }

    public boolean hasChanges() {
        return hasChanges;
    }

    public void setHasChanges(boolean hasChanges) {
        this.hasChanges = hasChanges;
    }

    public String getDiff() {
        return diff;
    }

    public void setDiff(String diff) {
        this.diff = diff;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public List<ChangeDetail> getChanges() {
        return changes;
    }

    public void setChanges(List<ChangeDetail> changes) {
        this.changes = changes != null ? changes : new ArrayList<>();
    }

    public void addChange(ChangeDetail change) {
        if (this.changes == null) {
            this.changes = new ArrayList<>();
        }
        this.changes.add(change);
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        this.success = false;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public int getChangeCount() {
        return changes != null ? changes.size() : 0;
    }

    public String getSummary() {
        if (!success) {
            return "Transformation failed: " + errorMessage;
        }
        if (!hasChanges) {
            return "No changes were made by " + getRecipeDisplayName();
        }
        return String.format("%s applied successfully (%d change%s)",
            getRecipeDisplayName(),
            getChangeCount(),
            getChangeCount() == 1 ? "" : "s");
    }

    @Override
    public String toString() {
        return "TransformationResult{" +
            "recipeName='" + recipeName + '\'' +
            ", hasChanges=" + hasChanges +
            ", success=" + success +
            ", timestamp=" + timestamp +
            '}';
    }

    /**
     * Nested class representing a single change detail.
     */
    public static class ChangeDetail {
        private String type;
        private String description;
        private int lineNumber;
        private String beforeSnippet;
        private String afterSnippet;

        public ChangeDetail() {
        }

        public ChangeDetail(String type, String description, int lineNumber) {
            this.type = type;
            this.description = description;
            this.lineNumber = lineNumber;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public int getLineNumber() {
            return lineNumber;
        }

        public void setLineNumber(int lineNumber) {
            this.lineNumber = lineNumber;
        }

        public String getBeforeSnippet() {
            return beforeSnippet;
        }

        public void setBeforeSnippet(String beforeSnippet) {
            this.beforeSnippet = beforeSnippet;
        }

        public String getAfterSnippet() {
            return afterSnippet;
        }

        public void setAfterSnippet(String afterSnippet) {
            this.afterSnippet = afterSnippet;
        }

        @Override
        public String toString() {
            return String.format("[%s] Line %d: %s", type, lineNumber, description);
        }
    }

    /**
     * Builder for convenient TransformationResult creation.
     */
    public static class Builder {
        private final TransformationResult result;

        public Builder() {
            this.result = new TransformationResult();
        }

        public Builder originalCode(String originalCode) {
            result.originalCode = originalCode;
            return this;
        }

        public Builder transformedCode(String transformedCode) {
            result.setTransformedCode(transformedCode);
            return this;
        }

        public Builder recipeName(String recipeName) {
            result.recipeName = recipeName;
            return this;
        }

        public Builder recipeDisplayName(String recipeDisplayName) {
            result.recipeDisplayName = recipeDisplayName;
            return this;
        }

        public Builder diff(String diff) {
            result.diff = diff;
            return this;
        }

        public Builder addChange(ChangeDetail change) {
            result.addChange(change);
            return this;
        }

        public Builder changes(List<ChangeDetail> changes) {
            result.changes = changes;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            result.setErrorMessage(errorMessage);
            return this;
        }

        public Builder success(boolean success) {
            result.success = success;
            return this;
        }

        public TransformationResult build() {
            if (result.originalCode != null && result.transformedCode != null) {
                result.hasChanges = !result.originalCode.equals(result.transformedCode);
            }
            return result;
        }
    }
}
