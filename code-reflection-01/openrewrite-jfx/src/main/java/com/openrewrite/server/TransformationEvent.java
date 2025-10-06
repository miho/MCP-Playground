package com.openrewrite.server;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Event representing a code transformation that occurred via MCP.
 * This event is published when external MCP clients trigger transformations,
 * allowing the UI to synchronize and display the changes.
 */
public class TransformationEvent {

    /**
     * Type of transformation event.
     */
    public enum Type {
        TRANSFORMATION_STARTED,
        TRANSFORMATION_COMPLETED,
        TRANSFORMATION_FAILED
    }

    private final Type type;
    private final String sourceCode;
    private final String transformedCode;
    private final String recipeName;
    private final String recipeDisplayName;
    private final String language;
    private final boolean hasChanges;
    private final String diff;
    private final String errorMessage;
    private final LocalDateTime timestamp;
    private final String requestId;

    private TransformationEvent(Builder builder) {
        this.type = builder.type;
        this.sourceCode = builder.sourceCode;
        this.transformedCode = builder.transformedCode;
        this.recipeName = builder.recipeName;
        this.recipeDisplayName = builder.recipeDisplayName;
        this.language = builder.language;
        this.hasChanges = builder.hasChanges;
        this.diff = builder.diff;
        this.errorMessage = builder.errorMessage;
        this.timestamp = builder.timestamp != null ? builder.timestamp : LocalDateTime.now();
        this.requestId = builder.requestId;
    }

    // Getters

    public Type getType() {
        return type;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public String getTransformedCode() {
        return transformedCode;
    }

    public String getRecipeName() {
        return recipeName;
    }

    public String getRecipeDisplayName() {
        return recipeDisplayName != null ? recipeDisplayName : recipeName;
    }

    public String getLanguage() {
        return language;
    }

    public boolean hasChanges() {
        return hasChanges;
    }

    public String getDiff() {
        return diff;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getRequestId() {
        return requestId;
    }

    public boolean isSuccess() {
        return type == Type.TRANSFORMATION_COMPLETED && errorMessage == null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TransformationEvent that = (TransformationEvent) o;
        return Objects.equals(requestId, that.requestId) &&
               Objects.equals(timestamp, that.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requestId, timestamp);
    }

    @Override
    public String toString() {
        return "TransformationEvent{" +
               "type=" + type +
               ", recipeName='" + recipeName + '\'' +
               ", hasChanges=" + hasChanges +
               ", timestamp=" + timestamp +
               ", requestId='" + requestId + '\'' +
               '}';
    }

    /**
     * Builder for creating TransformationEvent instances.
     */
    public static class Builder {
        private Type type;
        private String sourceCode;
        private String transformedCode;
        private String recipeName;
        private String recipeDisplayName;
        private String language = "java";
        private boolean hasChanges;
        private String diff;
        private String errorMessage;
        private LocalDateTime timestamp;
        private String requestId;

        public Builder type(Type type) {
            this.type = type;
            return this;
        }

        public Builder sourceCode(String sourceCode) {
            this.sourceCode = sourceCode;
            return this;
        }

        public Builder transformedCode(String transformedCode) {
            this.transformedCode = transformedCode;
            return this;
        }

        public Builder recipeName(String recipeName) {
            this.recipeName = recipeName;
            return this;
        }

        public Builder recipeDisplayName(String recipeDisplayName) {
            this.recipeDisplayName = recipeDisplayName;
            return this;
        }

        public Builder language(String language) {
            this.language = language;
            return this;
        }

        public Builder hasChanges(boolean hasChanges) {
            this.hasChanges = hasChanges;
            return this;
        }

        public Builder diff(String diff) {
            this.diff = diff;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public TransformationEvent build() {
            Objects.requireNonNull(type, "Event type must not be null");
            Objects.requireNonNull(recipeName, "Recipe name must not be null");
            return new TransformationEvent(this);
        }
    }

    /**
     * Convenience factory method for transformation started event.
     */
    public static TransformationEvent started(String recipeName, String sourceCode, String language) {
        return new Builder()
                .type(Type.TRANSFORMATION_STARTED)
                .recipeName(recipeName)
                .sourceCode(sourceCode)
                .language(language)
                .requestId(generateRequestId())
                .build();
    }

    /**
     * Convenience factory method for transformation completed event.
     */
    public static TransformationEvent completed(String recipeName, String recipeDisplayName,
                                                  String sourceCode, String transformedCode,
                                                  String language, String diff) {
        boolean hasChanges = !sourceCode.equals(transformedCode);
        return new Builder()
                .type(Type.TRANSFORMATION_COMPLETED)
                .recipeName(recipeName)
                .recipeDisplayName(recipeDisplayName)
                .sourceCode(sourceCode)
                .transformedCode(transformedCode)
                .language(language)
                .hasChanges(hasChanges)
                .diff(diff)
                .requestId(generateRequestId())
                .build();
    }

    /**
     * Convenience factory method for transformation failed event.
     */
    public static TransformationEvent failed(String recipeName, String sourceCode,
                                             String language, String errorMessage) {
        return new Builder()
                .type(Type.TRANSFORMATION_FAILED)
                .recipeName(recipeName)
                .sourceCode(sourceCode)
                .language(language)
                .errorMessage(errorMessage)
                .requestId(generateRequestId())
                .build();
    }

    /**
     * Generate a unique request ID for tracking.
     */
    private static String generateRequestId() {
        return "transform-" + System.nanoTime() + "-" + Thread.currentThread().getId();
    }
}
