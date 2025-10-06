package com.openrewrite.ui.components;

import com.openrewrite.ui.model.Recipe;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.List;
import java.util.Set;

/**
 * Panel to display detailed information about a selected recipe.
 * Shows recipe name, description, tags, and configuration options.
 */
public class RecipeDetailsPane extends VBox {

    private final Label nameLabel;
    private final Label displayNameLabel;
    private final TextArea descriptionArea;
    private final Label tagsLabel;
    private final VBox optionsContainer;
    private final ScrollPane scrollPane;

    private Recipe currentRecipe;

    public RecipeDetailsPane() {
        getStyleClass().add("recipe-details-pane");
        setPadding(new Insets(10));
        setSpacing(10);
        setPrefHeight(300);
        setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #dee2e6; -fx-border-width: 1;");

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
        List<Recipe.RecipeOption> options = recipe.getOptions();
        if (options == null || options.isEmpty()) {
            Label noOptions = new Label("No configuration options");
            noOptions.setStyle("-fx-text-fill: #adb5bd; -fx-font-style: italic;");
            optionsContainer.getChildren().add(noOptions);
        } else {
            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(5);
            grid.setPadding(new Insets(5));

            for (int i = 0; i < options.size(); i++) {
                Recipe.RecipeOption option = options.get(i);

                Label optionName = new Label(option.getName() + ":");
                optionName.setFont(Font.font("System", FontWeight.BOLD, 11));
                optionName.setStyle("-fx-text-fill: #495057;");

                Label optionDesc = new Label(option.getDescription());
                optionDesc.setWrapText(true);
                optionDesc.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 10px;");

                Label optionType = new Label("(" + option.getType() + ")");
                optionType.setStyle("-fx-text-fill: #adb5bd; -fx-font-size: 9px; -fx-font-style: italic;");

                grid.add(optionName, 0, i * 2);
                grid.add(optionType, 1, i * 2);
                grid.add(optionDesc, 0, i * 2 + 1, 2, 1);
            }

            optionsContainer.getChildren().add(grid);
        }
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
        showEmptyState();
    }

    /**
     * Get the currently displayed recipe.
     */
    public Recipe getCurrentRecipe() {
        return currentRecipe;
    }
}
