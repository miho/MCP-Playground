package com.openrewrite.ui.components;

import com.openrewrite.ui.model.Recipe;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Panel to display detailed information about a selected recipe.
 * Shows recipe name, description, tags, and configuration options.
 * Provides interactive input controls for recipe options.
 */
public class RecipeDetailsPane extends VBox {

    private final Label nameLabel;
    private final Label displayNameLabel;
    private final TextArea descriptionArea;
    private final Label tagsLabel;
    private final VBox optionsContainer;
    private final ScrollPane scrollPane;

    private Recipe currentRecipe;
    private final Map<String, Control> optionControls;

    public RecipeDetailsPane() {
        getStyleClass().add("recipe-details-pane");
        setPadding(new Insets(10));
        setSpacing(10);
        setPrefHeight(300);
        setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #dee2e6; -fx-border-width: 1;");
        this.optionControls = new HashMap<>();

        // Recipe name (technical name)
        Label nameHeaderLabel = new Label("Recipe ID:");
        nameHeaderLabel.setFont(Font.font("System", FontWeight.BOLD, 11));
        nameHeaderLabel.setStyle("-fx-text-fill: #6c757d;");

        nameLabel = new Label("No recipe selected");
        nameLabel.setWrapText(true);
        nameLabel.setFont(Font.font("Consolas", 11));
        nameLabel.setStyle("-fx-text-fill: #495057;");

        // Display name
        displayNameLabel = new Label();
        displayNameLabel.setWrapText(true);
        displayNameLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        displayNameLabel.setStyle("-fx-text-fill: #212529;");

        // Description
        Label descriptionHeaderLabel = new Label("Description:");
        descriptionHeaderLabel.setFont(Font.font("System", FontWeight.BOLD, 11));
        descriptionHeaderLabel.setStyle("-fx-text-fill: #6c757d;");

        descriptionArea = new TextArea();
        descriptionArea.setWrapText(true);
        descriptionArea.setEditable(false);
        descriptionArea.setPrefRowCount(4);
        descriptionArea.setStyle("-fx-background-color: white; -fx-border-color: #ced4da; -fx-border-radius: 3;");
        VBox.setVgrow(descriptionArea, Priority.ALWAYS);

        // Tags
        Label tagsHeaderLabel = new Label("Tags:");
        tagsHeaderLabel.setFont(Font.font("System", FontWeight.BOLD, 11));
        tagsHeaderLabel.setStyle("-fx-text-fill: #6c757d;");

        tagsLabel = new Label();
        tagsLabel.setWrapText(true);
        tagsLabel.setStyle("-fx-text-fill: #495057; -fx-padding: 5; -fx-background-color: white; -fx-border-color: #ced4da; -fx-border-radius: 3;");

        // Options container
        Label optionsHeaderLabel = new Label("Options:");
        optionsHeaderLabel.setFont(Font.font("System", FontWeight.BOLD, 11));
        optionsHeaderLabel.setStyle("-fx-text-fill: #6c757d;");

        optionsContainer = new VBox(5);
        optionsContainer.setPadding(new Insets(5));
        optionsContainer.setStyle("-fx-background-color: white; -fx-border-color: #ced4da; -fx-border-radius: 3;");

        scrollPane = new ScrollPane();
        scrollPane.setContent(this);
        scrollPane.setFitToWidth(true);

        getChildren().addAll(
            displayNameLabel,
            new Separator(),
            nameHeaderLabel,
            nameLabel,
            new Separator(),
            descriptionHeaderLabel,
            descriptionArea,
            new Separator(),
            tagsHeaderLabel,
            tagsLabel,
            new Separator(),
            optionsHeaderLabel,
            optionsContainer
        );

        showEmptyState();
    }

    /**
     * Show details for the selected recipe.
     */
    public void showRecipe(Recipe recipe) {
        this.currentRecipe = recipe;

        if (recipe == null) {
            showEmptyState();
            return;
        }

        // Update name
        nameLabel.setText(recipe.getName());
        nameLabel.setStyle("-fx-text-fill: #495057; -fx-font-family: 'Consolas';");

        // Update display name
        String displayName = recipe.getDisplayName();
        if (displayName == null || displayName.isEmpty()) {
            displayName = recipe.getName();
        }
        displayNameLabel.setText(displayName);

        // Update description
        String description = recipe.getDescription();
        if (description == null || description.isEmpty()) {
            description = "No description available.";
        }
        descriptionArea.setText(description);

        // Update tags
        Set<String> tags = recipe.getTags();
        if (tags == null || tags.isEmpty()) {
            tagsLabel.setText("No tags");
            tagsLabel.setStyle(tagsLabel.getStyle() + "; -fx-text-fill: #adb5bd;");
        } else {
            tagsLabel.setText(String.join(", ", tags));
            tagsLabel.setStyle("-fx-text-fill: #495057; -fx-padding: 5; -fx-background-color: white; -fx-border-color: #ced4da; -fx-border-radius: 3;");
        }

        // Update options
        optionsContainer.getChildren().clear();
        optionControls.clear();
        List<Recipe.RecipeOption> options = recipe.getOptions();
        if (options == null || options.isEmpty()) {
            Label noOptions = new Label("No configuration options");
            noOptions.setStyle("-fx-text-fill: #adb5bd; -fx-font-style: italic;");
            optionsContainer.getChildren().add(noOptions);
        } else {
            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(8);
            grid.setPadding(new Insets(5));

            for (int i = 0; i < options.size(); i++) {
                Recipe.RecipeOption option = options.get(i);

                // Create option label with required indicator
                String labelText = option.getName();
                if (option.isRequired()) {
                    labelText += " *";
                }
                Label optionName = new Label(labelText + ":");
                optionName.setFont(Font.font("System", FontWeight.BOLD, 11));
                optionName.setStyle("-fx-text-fill: #495057;");

                // Create type label
                Label optionType = new Label("(" + option.getType() + ")");
                optionType.setStyle("-fx-text-fill: #adb5bd; -fx-font-size: 9px; -fx-font-style: italic;");

                // Create description label
                Label optionDesc = new Label(option.getDescription());
                optionDesc.setWrapText(true);
                optionDesc.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 10px;");

                // Create appropriate input control based on type
                Control inputControl = createInputControl(option);
                if (inputControl != null) {
                    optionControls.put(option.getName(), inputControl);
                    GridPane.setHgrow(inputControl, Priority.ALWAYS);
                }

                // Add to grid
                grid.add(optionName, 0, i * 3);
                grid.add(optionType, 1, i * 3);
                grid.add(optionDesc, 0, i * 3 + 1, 2, 1);
                if (inputControl != null) {
                    grid.add(inputControl, 0, i * 3 + 2, 2, 1);
                }
            }

            optionsContainer.getChildren().add(grid);
        }
    }

    /**
     * Create an appropriate input control based on the option type.
     */
    private Control createInputControl(Recipe.RecipeOption option) {
        String type = option.getType() != null ? option.getType().toLowerCase() : "string";
        Control control = null;

        switch (type) {
            case "boolean":
            case "java.lang.boolean":
                CheckBox checkBox = new CheckBox();
                if (option.getDefaultValue() != null) {
                    checkBox.setSelected(Boolean.parseBoolean(option.getDefaultValue().toString()));
                }
                checkBox.setStyle("-fx-font-size: 11px;");
                control = checkBox;
                break;

            case "integer":
            case "int":
            case "java.lang.integer":
                TextField intField = new TextField();
                if (option.getDefaultValue() != null) {
                    intField.setText(option.getDefaultValue().toString());
                }
                intField.setPromptText("Enter integer value");
                intField.setStyle("-fx-font-size: 11px;");
                // Add validation
                intField.textProperty().addListener((obs, oldVal, newVal) -> {
                    if (!newVal.isEmpty() && !newVal.matches("-?\\d*")) {
                        intField.setText(oldVal);
                    }
                });
                control = intField;
                break;

            case "long":
            case "java.lang.long":
                TextField longField = new TextField();
                if (option.getDefaultValue() != null) {
                    longField.setText(option.getDefaultValue().toString());
                }
                longField.setPromptText("Enter long value");
                longField.setStyle("-fx-font-size: 11px;");
                longField.textProperty().addListener((obs, oldVal, newVal) -> {
                    if (!newVal.isEmpty() && !newVal.matches("-?\\d*")) {
                        longField.setText(oldVal);
                    }
                });
                control = longField;
                break;

            case "double":
            case "float":
            case "java.lang.double":
            case "java.lang.float":
                TextField doubleField = new TextField();
                if (option.getDefaultValue() != null) {
                    doubleField.setText(option.getDefaultValue().toString());
                }
                doubleField.setPromptText("Enter numeric value");
                doubleField.setStyle("-fx-font-size: 11px;");
                doubleField.textProperty().addListener((obs, oldVal, newVal) -> {
                    if (!newVal.isEmpty() && !newVal.matches("-?\\d*\\.?\\d*")) {
                        doubleField.setText(oldVal);
                    }
                });
                control = doubleField;
                break;

            case "list":
            case "java.util.list":
            case "array":
                TextArea listArea = new TextArea();
                if (option.getDefaultValue() != null) {
                    listArea.setText(option.getDefaultValue().toString());
                }
                listArea.setPromptText("Enter values, one per line");
                listArea.setPrefRowCount(3);
                listArea.setWrapText(false);
                listArea.setStyle("-fx-font-size: 11px; -fx-font-family: 'Consolas';");
                control = listArea;
                break;

            case "string":
            case "java.lang.string":
            default:
                TextField textField = new TextField();
                if (option.getDefaultValue() != null) {
                    textField.setText(option.getDefaultValue().toString());
                }
                textField.setPromptText("Enter value");
                textField.setStyle("-fx-font-size: 11px;");
                control = textField;
                break;
        }

        return control;
    }

    /**
     * Show empty state when no recipe is selected.
     */
    private void showEmptyState() {
        nameLabel.setText("No recipe selected");
        nameLabel.setStyle("-fx-text-fill: #adb5bd; -fx-font-style: italic;");
        displayNameLabel.setText("Select a recipe to view details");
        displayNameLabel.setStyle("-fx-text-fill: #adb5bd; -fx-font-size: 14px; -fx-font-style: italic;");
        descriptionArea.setText("");
        tagsLabel.setText("");
        optionsContainer.getChildren().clear();
    }

    /**
     * Clear the recipe details.
     */
    public void clear() {
        currentRecipe = null;
        optionControls.clear();
        showEmptyState();
    }

    /**
     * Get the currently displayed recipe.
     */
    public Recipe getCurrentRecipe() {
        return currentRecipe;
    }

    /**
     * Get the current values of all recipe options as a Map.
     * Converts the control values to appropriate types based on the option definition.
     *
     * @return Map of option name to value, or empty map if no options
     * @throws IllegalArgumentException if a value cannot be converted to the expected type
     */
    public Map<String, Object> getOptionValues() {
        Map<String, Object> values = new HashMap<>();

        if (currentRecipe == null || currentRecipe.getOptions() == null) {
            return values;
        }

        List<String> errors = new ArrayList<>();

        for (Recipe.RecipeOption option : currentRecipe.getOptions()) {
            String optionName = option.getName();
            Control control = optionControls.get(optionName);

            if (control == null) {
                continue;
            }

            try {
                Object value = extractValueFromControl(control, option.getType());

                // Only add non-null values
                if (value != null) {
                    values.put(optionName, value);
                }
            } catch (IllegalArgumentException e) {
                // Collect all errors to report them together
                errors.add(String.format("%s: %s", optionName, e.getMessage()));
            }
        }

        // If there were errors, throw an exception with all of them
        if (!errors.isEmpty()) {
            String errorMessage = "Invalid option values:\n" + String.join("\n", errors);
            throw new IllegalArgumentException(errorMessage);
        }

        return values;
    }

    /**
     * Extract the value from a control based on its type.
     * Throws IllegalArgumentException for invalid numeric input.
     */
    private Object extractValueFromControl(Control control, String type) {
        if (control instanceof CheckBox) {
            return ((CheckBox) control).isSelected();
        } else if (control instanceof TextField) {
            String text = ((TextField) control).getText();
            if (text == null || text.trim().isEmpty()) {
                return null;
            }

            // Parse based on type
            String lowerType = type != null ? type.toLowerCase() : "";
            if (lowerType.isEmpty()) {
                // No type specified, return as string
                return text.trim();
            }

            try {
                switch (lowerType) {
                    case "integer":
                    case "int":
                    case "java.lang.integer":
                        return Integer.parseInt(text.trim());
                    case "long":
                    case "java.lang.long":
                        return Long.parseLong(text.trim());
                    case "double":
                    case "java.lang.double":
                        return Double.parseDouble(text.trim());
                    case "float":
                    case "java.lang.float":
                        return Float.parseFloat(text.trim());
                    case "pattern":
                    case "java.util.regex.pattern":
                        // Validate regex pattern
                        try {
                            java.util.regex.Pattern.compile(text.trim());
                        } catch (java.util.regex.PatternSyntaxException e) {
                            throw new IllegalArgumentException("Invalid regex pattern: " + e.getMessage());
                        }
                        return text.trim();
                    case "duration":
                    case "java.time.duration":
                        // Validate duration format
                        try {
                            if (text.trim().matches("\\d+")) {
                                // Simple seconds
                                Long.parseLong(text.trim());
                            } else {
                                // ISO-8601 format
                                java.time.Duration.parse(text.trim());
                            }
                        } catch (Exception e) {
                            throw new IllegalArgumentException("Invalid duration format: " + e.getMessage());
                        }
                        return text.trim();
                    default:
                        return text.trim();
                }
            } catch (NumberFormatException e) {
                // Provide clear error message for invalid numbers
                String errorMsg = String.format("Invalid %s value: '%s'",
                    type != null ? type : "numeric", text);
                throw new IllegalArgumentException(errorMsg, e);
            }
        } else if (control instanceof TextArea) {
            String text = ((TextArea) control).getText();
            if (text == null || text.trim().isEmpty()) {
                // Return empty list instead of null for TextArea
                // Some recipes may expect empty list rather than null
                return new java.util.ArrayList<>();
            }
            // Split by lines and create a list
            String[] lines = text.split("\n");
            List<String> list = new java.util.ArrayList<>();
            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    list.add(trimmed);
                }
            }
            return list;
        }

        return null;
    }

    /**
     * Validate that all required options have values.
     *
     * @return ValidationResult containing whether validation passed and any error messages
     */
    public ValidationResult validateOptions() {
        if (currentRecipe == null || currentRecipe.getOptions() == null) {
            return new ValidationResult(true, null);
        }

        List<String> missingOptions = new java.util.ArrayList<>();

        for (Recipe.RecipeOption option : currentRecipe.getOptions()) {
            if (option.isRequired()) {
                Control control = optionControls.get(option.getName());
                if (control == null) {
                    continue;
                }

                Object value = extractValueFromControl(control, option.getType());
                if (value == null) {
                    missingOptions.add(option.getName());
                }
            }
        }

        if (missingOptions.isEmpty()) {
            return new ValidationResult(true, null);
        } else {
            String message = "Required options missing: " + String.join(", ", missingOptions);
            return new ValidationResult(false, message);
        }
    }

    /**
     * Result of option validation.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;

        public ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
