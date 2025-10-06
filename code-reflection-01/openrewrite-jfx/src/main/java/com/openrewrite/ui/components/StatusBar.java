package com.openrewrite.ui.components;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * Bottom status bar displaying application status, progress, and connection info.
 */
public class StatusBar extends HBox {

    private final Label statusLabel;
    private final ProgressBar progressBar;
    private final Label connectionLabel;
    private final Label recipeCountLabel;
    private final StringProperty statusMessage;
    private final StringProperty connectionStatus;

    public StatusBar() {
        this.statusMessage = new SimpleStringProperty("Ready");
        this.connectionStatus = new SimpleStringProperty("Disconnected");

        getStyleClass().add("status-bar");
        setPadding(new Insets(6, 10, 6, 10));
        setSpacing(10);
        setAlignment(Pos.CENTER_LEFT);
        setStyle("-fx-background-color: #2c3e50; -fx-border-color: #34495e; -fx-border-width: 1 0 0 0;");

        // Status message
        statusLabel = new Label();
        statusLabel.textProperty().bind(statusMessage);
        statusLabel.getStyleClass().add("status-message");
        statusLabel.setStyle("-fx-text-fill: #ecf0f1; -fx-font-size: 12px;");
        HBox.setHgrow(statusLabel, Priority.ALWAYS);

        // Progress bar
        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(150);
        progressBar.setVisible(false);
        progressBar.setStyle("-fx-accent: #3498db;");

        // Spacer
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.SOMETIMES);

        // Recipe count
        recipeCountLabel = new Label("0 recipes");
        recipeCountLabel.getStyleClass().add("recipe-count-label");
        recipeCountLabel.setStyle("-fx-text-fill: #95a5a6; -fx-font-size: 11px;");

        // Connection status
        connectionLabel = new Label();
        connectionLabel.textProperty().bind(connectionStatus);
        connectionLabel.getStyleClass().add("connection-label");
        connectionLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 11px; -fx-font-weight: bold;");

        getChildren().addAll(
            statusLabel,
            progressBar,
            spacer,
            recipeCountLabel,
            new Separator(),
            connectionLabel
        );
    }

    /**
     * Set the status message.
     */
    public void setStatus(String message) {
        statusMessage.set(message);
        resetStatusStyle();
    }

    /**
     * Show progress with a value between 0.0 and 1.0.
     */
    public void showProgress(double progress) {
        progressBar.setVisible(true);
        progressBar.setProgress(progress);
    }

    /**
     * Show indeterminate progress (spinning).
     */
    public void showIndeterminateProgress() {
        progressBar.setVisible(true);
        progressBar.setProgress(-1);
    }

    /**
     * Hide the progress bar.
     */
    public void hideProgress() {
        progressBar.setVisible(false);
        progressBar.setProgress(0);
    }

    /**
     * Show error message in red.
     */
    public void showError(String message) {
        statusMessage.set("ERROR: " + message);
        statusLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold; -fx-font-size: 12px;");
    }

    /**
     * Show success message in green.
     */
    public void showSuccess(String message) {
        statusMessage.set(message);
        statusLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold; -fx-font-size: 12px;");
    }

    /**
     * Show warning message in yellow/orange.
     */
    public void showWarning(String message) {
        statusMessage.set("WARNING: " + message);
        statusLabel.setStyle("-fx-text-fill: #f39c12; -fx-font-weight: bold; -fx-font-size: 12px;");
    }

    /**
     * Reset status label to default style.
     */
    private void resetStatusStyle() {
        statusLabel.setStyle("-fx-text-fill: #ecf0f1; -fx-font-size: 12px;");
    }

    /**
     * Set connection status.
     */
    public void setConnectionStatus(boolean connected) {
        if (connected) {
            connectionStatus.set("Connected to MCP Server");
            connectionLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-size: 11px; -fx-font-weight: bold;");
        } else {
            connectionStatus.set("Disconnected");
            connectionLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 11px; -fx-font-weight: bold;");
        }
    }

    /**
     * Update recipe count.
     */
    public void setRecipeCount(int count) {
        if (count == 1) {
            recipeCountLabel.setText("1 recipe");
        } else {
            recipeCountLabel.setText(count + " recipes");
        }
    }

    /**
     * Get the status message property for binding.
     */
    public StringProperty statusMessageProperty() {
        return statusMessage;
    }

    /**
     * Get the connection status property for binding.
     */
    public StringProperty connectionStatusProperty() {
        return connectionStatus;
    }
}
