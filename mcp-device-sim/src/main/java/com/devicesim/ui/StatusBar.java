package com.devicesim.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

/**
 * Bottom status bar displaying MCP server status, device position, and general application state.
 *
 * @since 1.0.0
 */
public class StatusBar extends HBox {

    private final Label serverStatusLabel;
    private final Circle serverIndicator;
    private final Label statusLabel;
    private final StringProperty statusMessage;
    private final Label endpointLabel;
    private final Label positionLabel;
    private final Label targetLabel;
    private final Label speedLabel;

    public StatusBar() {
        this.statusMessage = new SimpleStringProperty("Ready");

        // Server indicator (colored circle)
        serverIndicator = new Circle(6);
        serverIndicator.setFill(Color.GRAY);

        // Server status label
        serverStatusLabel = new Label("Server: Stopped");
        serverStatusLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");

        // Endpoint label
        endpointLabel = new Label("");
        endpointLabel.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11px;");

        // Position label
        positionLabel = new Label("Position: (0.0, 0.0)");
        positionLabel.setStyle("-fx-text-fill: #45b7d1; -fx-font-size: 11px;");

        // Target label
        targetLabel = new Label("Target: None");
        targetLabel.setStyle("-fx-text-fill: #4ecdc4; -fx-font-size: 11px;");

        // Speed label
        speedLabel = new Label("Speed: 0.0 u/s");
        speedLabel.setStyle("-fx-text-fill: #f39c12; -fx-font-size: 11px;");

        // Server info box
        HBox serverBox = new HBox(8);
        serverBox.setAlignment(Pos.CENTER_LEFT);
        serverBox.getChildren().addAll(serverIndicator, serverStatusLabel, endpointLabel);

        // Device info box
        HBox deviceBox = new HBox(15);
        deviceBox.setAlignment(Pos.CENTER_LEFT);
        deviceBox.getChildren().addAll(positionLabel, targetLabel, speedLabel);

        // General status message
        statusLabel = new Label();
        statusLabel.textProperty().bind(statusMessage);
        statusLabel.setStyle("-fx-text-fill: #a0a0a0;");
        HBox.setHgrow(statusLabel, Priority.ALWAYS);

        // Layout
        this.setPadding(new Insets(5, 10, 5, 10));
        this.setSpacing(20);
        this.setAlignment(Pos.CENTER_LEFT);

        Separator sep1 = new Separator();
        sep1.setOrientation(javafx.geometry.Orientation.VERTICAL);

        Separator sep2 = new Separator();
        sep2.setOrientation(javafx.geometry.Orientation.VERTICAL);

        this.getChildren().addAll(serverBox, sep1, deviceBox, sep2, statusLabel);
        this.setStyle("-fx-background-color: #1a1a1a; -fx-border-color: #3a3a3a; -fx-border-width: 1 0 0 0;");
    }

    /**
     * Set general status message.
     */
    public void setStatus(String message) {
        statusMessage.set(message);
        // Reset to normal color
        statusLabel.setStyle("-fx-text-fill: #a0a0a0;");
    }

    /**
     * Update server status.
     */
    public void setServerRunning(boolean running) {
        if (running) {
            serverIndicator.setFill(Color.LIMEGREEN);
            serverStatusLabel.setText("Server: Running");
            serverStatusLabel.setStyle("-fx-text-fill: #4ecdc4; -fx-font-weight: bold;");
        } else {
            serverIndicator.setFill(Color.GRAY);
            serverStatusLabel.setText("Server: Stopped");
            serverStatusLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
        }
    }

    /**
     * Set server endpoint information.
     */
    public void setEndpoint(String endpoint) {
        if (endpoint != null && !endpoint.isEmpty()) {
            endpointLabel.setText("(" + endpoint + ")");
        } else {
            endpointLabel.setText("");
        }
    }

    /**
     * Update device position display.
     */
    public void setPosition(double x, double y) {
        positionLabel.setText(String.format("Position: (%.2f, %.2f)", x, y));
    }

    /**
     * Update target location display.
     */
    public void setTarget(String targetId, double x, double y) {
        if (targetId != null) {
            targetLabel.setText(String.format("Target: %s (%.2f, %.2f)", targetId, x, y));
        } else {
            targetLabel.setText("Target: None");
        }
    }

    /**
     * Update speed display.
     */
    public void setSpeed(double speed) {
        speedLabel.setText(String.format("Speed: %.2f u/s", speed));
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

    public StringProperty statusMessageProperty() {
        return statusMessage;
    }
}
