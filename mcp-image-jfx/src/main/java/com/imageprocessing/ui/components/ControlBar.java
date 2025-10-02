package com.imageprocessing.ui.components;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

/**
 * Top control bar with play/stop, MCP status, and quick actions.
 */
public class ControlBar extends HBox {

    private final Button playButton;
    private final Button stopButton;
    private final Button launchServerButton;
    private final Button settingsButton;
    private final Button themeToggleButton;
    private final Circle statusIndicator;
    private final Label statusLabel;
    private final BooleanProperty serverRunning;
    private final BooleanProperty executing;

    private Runnable onPlayAction;
    private Runnable onStopAction;
    private Runnable onLaunchServerAction;
    private Runnable onServerSettingsAction;
    private Runnable onSaveWorkflowAction;
    private Runnable onLoadWorkflowAction;
    private Runnable onThemeToggleAction;

    public ControlBar() {
        this.serverRunning = new SimpleBooleanProperty(false);
        this.executing = new SimpleBooleanProperty(false);

        // Play button
        playButton = new Button("Play");
        playButton.getStyleClass().add("play-button");
        playButton.setTooltip(new Tooltip("Execute pipeline"));
        playButton.setOnAction(e -> {
            if (onPlayAction != null) {
                onPlayAction.run();
            }
        });

        // Stop button
        stopButton = new Button("Stop");
        stopButton.getStyleClass().add("stop-button");
        stopButton.setTooltip(new Tooltip("Stop execution"));
        stopButton.setDisable(true);
        stopButton.setOnAction(e -> {
            if (onStopAction != null) {
                onStopAction.run();
            }
        });

        // Separator
        Separator sep1 = new Separator();
        sep1.setOrientation(javafx.geometry.Orientation.VERTICAL);

        // MCP Status indicator
        statusIndicator = new Circle(8);
        statusIndicator.setStroke(Color.DARKGRAY);
        statusIndicator.setFill(Color.RED);

        statusLabel = new Label("Disconnected");
        statusLabel.getStyleClass().add("status-label");

        // Settings button
        settingsButton = new Button("⚙");
        settingsButton.getStyleClass().add("secondary-button");
        settingsButton.setTooltip(new Tooltip("MCP Server Settings"));
        settingsButton.setOnAction(e -> {
            if (onServerSettingsAction != null) {
                onServerSettingsAction.run();
            }
        });

        // Launch server button
        launchServerButton = new Button("Launch MCP Server");
        launchServerButton.getStyleClass().add("launch-button");
        launchServerButton.setTooltip(new Tooltip("Start the MCP server"));
        launchServerButton.setOnAction(e -> {
            if (onLaunchServerAction != null) {
                onLaunchServerAction.run();
            }
        });

        HBox statusBox = new HBox(8, statusIndicator, statusLabel, settingsButton, launchServerButton);
        statusBox.setAlignment(Pos.CENTER_LEFT);

        // Separator
        Separator sep2 = new Separator();
        sep2.setOrientation(javafx.geometry.Orientation.VERTICAL);

        // Workflow buttons
        Button saveButton = new Button("Save Workflow");
        saveButton.getStyleClass().add("secondary-button");
        saveButton.setTooltip(new Tooltip("Save current workflow"));
        saveButton.setOnAction(e -> {
            if (onSaveWorkflowAction != null) {
                onSaveWorkflowAction.run();
            }
        });

        Button loadButton = new Button("Load Workflow");
        loadButton.getStyleClass().add("secondary-button");
        loadButton.setTooltip(new Tooltip("Load saved workflow"));
        loadButton.setOnAction(e -> {
            if (onLoadWorkflowAction != null) {
                onLoadWorkflowAction.run();
            }
        });

        HBox workflowButtons = new HBox(5, saveButton, loadButton);
        workflowButtons.setAlignment(Pos.CENTER_LEFT);

        // Spacer
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Theme toggle button
        themeToggleButton = new Button("\u263C"); // Sun/moon icon
        themeToggleButton.getStyleClass().add("theme-toggle");
        themeToggleButton.setTooltip(new Tooltip("Toggle Dark/Light Theme"));
        themeToggleButton.setOnAction(e -> {
            if (onThemeToggleAction != null) {
                onThemeToggleAction.run();
            }
        });

        // Layout
        this.setPadding(new Insets(10));
        this.setSpacing(10);
        this.setAlignment(Pos.CENTER_LEFT);
        this.getChildren().addAll(
            playButton,
            stopButton,
            sep1,
            statusBox,
            sep2,
            workflowButtons,
            spacer,
            themeToggleButton
        );
        this.getStyleClass().add("control-bar");

        // Bind properties
        setupBindings();
    }

    private void setupBindings() {
        // Update status indicator based on server state
        serverRunning.addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                statusIndicator.setFill(Color.GREEN);
                statusLabel.setText("Connected");
                launchServerButton.setDisable(true);
            } else {
                statusIndicator.setFill(Color.RED);
                statusLabel.setText("Disconnected");
                launchServerButton.setDisable(false);
            }
        });

        // Update button states based on execution
        executing.addListener((obs, oldVal, newVal) -> {
            playButton.setDisable(newVal);
            stopButton.setDisable(!newVal);
        });
    }

    // Action handlers
    public void setOnPlayAction(Runnable action) {
        this.onPlayAction = action;
    }

    public void setOnStopAction(Runnable action) {
        this.onStopAction = action;
    }

    public void setOnLaunchServerAction(Runnable action) {
        this.onLaunchServerAction = action;
    }

    public void setOnServerSettingsAction(Runnable action) {
        this.onServerSettingsAction = action;
    }

    public void setOnSaveWorkflowAction(Runnable action) {
        this.onSaveWorkflowAction = action;
    }

    public void setOnLoadWorkflowAction(Runnable action) {
        this.onLoadWorkflowAction = action;
    }

    public void setOnThemeToggleAction(Runnable action) {
        this.onThemeToggleAction = action;
    }

    // Property getters
    public BooleanProperty serverRunningProperty() {
        return serverRunning;
    }

    public BooleanProperty executingProperty() {
        return executing;
    }

    public void setServerRunning(boolean running) {
        serverRunning.set(running);
    }

    public void setExecuting(boolean executing) {
        this.executing.set(executing);
    }

    /**
     * Update the theme toggle button icon based on current theme.
     * @param isDarkTheme true if dark theme is active, false for light theme
     */
    public void updateThemeIcon(boolean isDarkTheme) {
        // Dark theme shows sun icon (to switch to light)
        // Light theme shows moon icon (to switch to dark)
        themeToggleButton.setText(isDarkTheme ? "\u2600" : "\u263D");
    }
}
