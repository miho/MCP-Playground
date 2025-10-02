package com.imageprocessing.ui.model;

import java.util.*;

/**
 * Metadata describing an available MCP tool.
 * Contains information about the tool's name, description, category, and parameters.
 */
public class ToolMetadata {

    public enum Category {
        LOAD("Load", "Input operations"),
        TRANSFORM("Transform", "Image transformations"),
        FILTER("Filter", "Image filtering"),
        ANALYSIS("Analysis", "Image analysis"),
        OUTPUT("Output", "Output operations");

        private final String displayName;
        private final String description;

        Category(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    private final String name;
    private final String description;
    private final Category category;
    private final List<ParameterDefinition> parameters;

    public ToolMetadata(String name, String description, Category category,
                        List<ParameterDefinition> parameters) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.parameters = new ArrayList<>(parameters);
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public Category getCategory() { return category; }
    public List<ParameterDefinition> getParameters() { return Collections.unmodifiableList(parameters); }

    @Override
    public String toString() {
        return name;
    }

    /**
     * Definition of a tool parameter.
     */
    public static class ParameterDefinition {

        public enum Type {
            STRING,
            INTEGER,
            DOUBLE,
            FLOAT,
            BOOLEAN,
            IMAGE_PATH,      // File path with browse button
            RESULT_KEY,      // ComboBox of cached results
            OUTPUT_PATH,     // File path with save dialog
            OUTPUT_KEY,      // TextField for cache storage
            ENUM             // ComboBox with predefined values
        }

        private final String name;
        private final String description;
        private final Type type;
        private final boolean required;
        private final Object defaultValue;
        private final List<String> enumValues;  // For ENUM type

        public ParameterDefinition(String name, String description, Type type,
                                   boolean required, Object defaultValue) {
            this(name, description, type, required, defaultValue, null);
        }

        public ParameterDefinition(String name, String description, Type type,
                                   boolean required, Object defaultValue, List<String> enumValues) {
            this.name = name;
            this.description = description;
            this.type = type;
            this.required = required;
            this.defaultValue = defaultValue;
            this.enumValues = enumValues != null ? new ArrayList<>(enumValues) : new ArrayList<>();
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
        public Type getType() { return type; }
        public boolean isRequired() { return required; }
        public Object getDefaultValue() { return defaultValue; }
        public List<String> getEnumValues() { return Collections.unmodifiableList(enumValues); }
    }
}
