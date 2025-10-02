package com.imageprocessing.ui.components;

import com.imageprocessing.ui.model.WorkflowModel;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/**
 * Bottom status bar displaying execution status, progress, and statistics.
 */
public class StatusBar extends HBox {

    private final Label statusLabel;
    private final ProgressBar progressBar;
    private final Label statsLabel;
    private final StringProperty statusMessage;

    public StatusBar() {
        this.statusMessage = new SimpleStringProperty("Ready");

        // Status message
        statusLabel = new Label();
        statusLabel.textProperty().bind(statusMessage);
        statusLabel.getStyleClass().add("status-message");
        HBox.setHgrow(statusLabel, Priority.ALWAYS);

        // Progress bar
        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(200);
        progressBar.setVisible(false);

        // Statistics
        statsLabel = new Label("0 tools");
        statsLabel.getStyleClass().add("stats-label");

        // Layout
        this.setPadding(new Insets(5, 10, 5, 10));
        this.setSpacing(10);
        this.setAlignment(Pos.CENTER_LEFT);
        this.getChildren().addAll(statusLabel, progressBar, statsLabel);
        this.getStyleClass().add("status-bar");
    }

    /**
     * Set status message.
     */
    public void setStatus(String message) {
        statusMessage.set(message);
    }

    /**
     * Show progress bar with value.
     */
    public void showProgress(double progress) {
        progressBar.setVisible(true);
        progressBar.setProgress(progress);
    }

    /**
     * Hide progress bar.
     */
    public void hideProgress() {
        progressBar.setVisible(false);
        progressBar.setProgress(0);
    }

    /**
     * Update statistics from workflow model.
     */
    public void updateStats(WorkflowModel model) {
        if (model.isEmpty()) {
            statsLabel.setText("0 tools");
            return;
        }

        WorkflowModel.ExecutionStats stats = model.getStats();
        StringBuilder sb = new StringBuilder();
        sb.append(stats.getTotal()).append(" tools");

        if (stats.getCompleted() > 0) {
            sb.append(" | ").append(stats.getCompleted()).append(" completed");
        }
        if (stats.getRunning() > 0) {
            sb.append(" | ").append(stats.getRunning()).append(" running");
        }
        if (stats.getError() > 0) {
            sb.append(" | ").append(stats.getError()).append(" errors");
        }
        if (stats.getPending() > 0) {
            sb.append(" | ").append(stats.getPending()).append(" pending");
        }

        statsLabel.setText(sb.toString());
    }

    /**
     * Show error message.
     */
    public void showError(String message) {
        statusMessage.set("ERROR: " + message);
        statusLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
    }

    /**
     * Show success message.
     */
    public void showSuccess(String message) {
        statusMessage.set(message);
        statusLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
    }

    /**
     * Reset to normal status.
     */
    public void resetStatus() {
        statusLabel.setStyle("");
    }

    public StringProperty statusMessageProperty() {
        return statusMessage;
    }
}
