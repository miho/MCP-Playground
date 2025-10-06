package com.openrewrite.ui.model;

import java.util.*;

/**
 * Model class representing an OpenRewrite recipe.
 * Contains metadata about the recipe including name, description, tags, and configuration options.
 */
public class Recipe {

    private String name;
    private String displayName;
    private String description;
    private Set<String> tags;
    private List<RecipeOption> options;
    private List<String> examples;

    public Recipe() {
        this.tags = new HashSet<>();
        this.options = new ArrayList<>();
        this.examples = new ArrayList<>();
    }

    public Recipe(String name, String displayName, String description) {
        this();
        this.name = name;
        this.displayName = displayName;
        this.description = description;
    }

    // Getters and setters

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return displayName != null && !displayName.isEmpty() ? displayName : name;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Set<String> getTags() {
        return tags;
    }

    public void setTags(Set<String> tags) {
        this.tags = tags != null ? tags : new HashSet<>();
    }

    public void addTag(String tag) {
        if (this.tags == null) {
            this.tags = new HashSet<>();
        }
        this.tags.add(tag);
    }

    public List<RecipeOption> getOptions() {
        return options;
    }

    public void setOptions(List<RecipeOption> options) {
        this.options = options != null ? options : new ArrayList<>();
    }

    public void addOption(RecipeOption option) {
        if (this.options == null) {
            this.options = new ArrayList<>();
        }
        this.options.add(option);
    }

    public List<String> getExamples() {
        return examples;
    }

    public void setExamples(List<String> examples) {
        this.examples = examples != null ? examples : new ArrayList<>();
    }

    public void addExample(String example) {
        if (this.examples == null) {
            this.examples = new ArrayList<>();
        }
        this.examples.add(example);
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Recipe recipe = (Recipe) o;
        return Objects.equals(name, recipe.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    /**
     * Nested class representing a recipe configuration option.
     */
    public static class RecipeOption {
        private String name;
        private String description;
        private String type;
        private Object defaultValue;
        private boolean required;

        public RecipeOption() {
        }

        public RecipeOption(String name, String description, String type) {
            this.name = name;
            this.description = description;
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Object getDefaultValue() {
            return defaultValue;
        }

        public void setDefaultValue(Object defaultValue) {
            this.defaultValue = defaultValue;
        }

        public boolean isRequired() {
            return required;
        }

        public void setRequired(boolean required) {
            this.required = required;
        }

        @Override
        public String toString() {
            return name + " (" + type + ")";
        }
    }

    /**
     * Builder for convenient Recipe creation.
     */
    public static class Builder {
        private final Recipe recipe;

        public Builder(String name) {
            this.recipe = new Recipe();
            this.recipe.name = name;
        }

        public Builder displayName(String displayName) {
            recipe.displayName = displayName;
            return this;
        }

        public Builder description(String description) {
            recipe.description = description;
            return this;
        }

        public Builder addTag(String tag) {
            recipe.addTag(tag);
            return this;
        }

        public Builder tags(Set<String> tags) {
            recipe.tags = tags;
            return this;
        }

        public Builder addOption(RecipeOption option) {
            recipe.addOption(option);
            return this;
        }

        public Builder options(List<RecipeOption> options) {
            recipe.options = options;
            return this;
        }

        public Builder addExample(String example) {
            recipe.addExample(example);
            return this;
        }

        public Builder examples(List<String> examples) {
            recipe.examples = examples;
            return this;
        }

        public Recipe build() {
            return recipe;
        }
    }
}
