package com.openrewrite.ui.components;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * Application toolbar with main action buttons.
 * Provides quick access to transform, analyze, refresh, and clear operations.
 */
public class AppToolBar extends javafx.scene.control.ToolBar {

    private final Button transformButton;
    private final Button analyzeButton;
    private final Button refreshButton;
    private final Button clearButton;
    private final Label titleLabel;
    private final Label serverStatusIndicator;

    public AppToolBar() {
        getStyleClass().add("app-toolbar");

        // Title
        titleLabel = new Label("OpenRewrite Code Transformer");
        titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        titleLabel.setPadding(new Insets(0, 15, 0, 5));

        // Transform button
        transformButton = createButton("Apply Recipe", "transform-button");
        transformButton.setTooltip(new Tooltip("Apply the selected recipe to transform code"));
        transformButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-weight: bold;");

        // Analyze button
        analyzeButton = createButton("Analyze", "analyze-button");
        analyzeButton.setTooltip(new Tooltip("Analyze code and suggest applicable recipes"));
        analyzeButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white;");

        // Refresh button
        refreshButton = createButton("Refresh Recipes", "refresh-button");
        refreshButton.setTooltip(new Tooltip("Refresh the list of available recipes"));
        refreshButton.setStyle("-fx-background-color: #95a5a6; -fx-text-fill: white;");

        // Clear button
        clearButton = createButton("Clear", "clear-button");
        clearButton.setTooltip(new Tooltip("Clear all editors"));
        clearButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white;");

        // Spacer to push buttons to the right
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Server status indicator
        serverStatusIndicator = new Label("Server: Stopped");
        serverStatusIndicator.setStyle("-fx-font-size: 11px; -fx-padding: 5px 10px; " +
                "-fx-background-color: #e74c3c; -fx-text-fill: white; " +
                "-fx-background-radius: 3px; -fx-font-weight: bold;");
        serverStatusIndicator.setTooltip(new Tooltip("MCP Server Status"));

        // Add all items to toolbar
        getItems().addAll(
            titleLabel,
            new Separator(),
            transformButton,
            analyzeButton,
            new Separator(),
            refreshButton,
            clearButton,
            spacer,
            serverStatusIndicator
        );

        setPadding(new Insets(8));
        setStyle("-fx-background-color: #ecf0f1; -fx-border-color: #bdc3c7; -fx-border-width: 0 0 1 0;");
    }

    /**
     * Create a styled button.
     */
    private Button createButton(String text, String styleClass) {
        Button button = new Button(text);
        button.getStyleClass().add(styleClass);
        button.setPrefHeight(32);
        button.setMinWidth(100);
        button.setStyle(button.getStyle() + "; -fx-cursor: hand;");

        // Hover effect
        button.setOnMouseEntered(e -> {
            button.setStyle(button.getStyle() + "; -fx-opacity: 0.8;");
        });
        button.setOnMouseExited(e -> {
            button.setStyle(button.getStyle().replace("; -fx-opacity: 0.8;", ""));
        });

        return button;
    }

    /**
     * Add handler for the transform button.
     */
    public void addTransformButton(EventHandler<ActionEvent> handler) {
        transformButton.setOnAction(handler);
    }

    /**
     * Add handler for the analyze button.
     */
    public void addAnalyzeButton(EventHandler<ActionEvent> handler) {
        analyzeButton.setOnAction(handler);
    }

    /**
     * Add handler for the refresh button.
     */
    public void addRefreshButton(EventHandler<ActionEvent> handler) {
        refreshButton.setOnAction(handler);
    }

    /**
     * Add handler for the clear button.
     */
    public void addClearButton(EventHandler<ActionEvent> handler) {
        clearButton.setOnAction(handler);
    }

    /**
     * Enable or disable the transform button.
     */
    public void setTransformEnabled(boolean enabled) {
        transformButton.setDisable(!enabled);
    }

    /**
     * Enable or disable the analyze button.
     */
    public void setAnalyzeEnabled(boolean enabled) {
        analyzeButton.setDisable(!enabled);
    }

    /**
     * Enable or disable the refresh button.
     */
    public void setRefreshEnabled(boolean enabled) {
        refreshButton.setDisable(!enabled);
    }

    /**
     * Enable or disable all action buttons.
     */
    public void setAllButtonsEnabled(boolean enabled) {
        transformButton.setDisable(!enabled);
        analyzeButton.setDisable(!enabled);
        refreshButton.setDisable(!enabled);
        clearButton.setDisable(!enabled);
    }

    /**
     * Set the toolbar title.
     */
    public void setTitle(String title) {
        titleLabel.setText(title);
    }

    /**
     * Update server running status indicator.
     */
    public void setServerRunning(boolean running) {
        if (running) {
            serverStatusIndicator.setText("Server: Running");
            serverStatusIndicator.setStyle("-fx-font-size: 11px; -fx-padding: 5px 10px; " +
                    "-fx-background-color: #27ae60; -fx-text-fill: white; " +
                    "-fx-background-radius: 3px; -fx-font-weight: bold;");
            serverStatusIndicator.setTooltip(new Tooltip("MCP Server is running"));
        } else {
            serverStatusIndicator.setText("Server: Stopped");
            serverStatusIndicator.setStyle("-fx-font-size: 11px; -fx-padding: 5px 10px; " +
                    "-fx-background-color: #e74c3c; -fx-text-fill: white; " +
                    "-fx-background-radius: 3px; -fx-font-weight: bold;");
            serverStatusIndicator.setTooltip(new Tooltip("MCP Server is not running"));
        }
    }
}
