package com.trafficsim.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

/**
 * Bottom status bar displaying MCP server status and general application state.
 */
public class StatusBar extends HBox {

    private final Label serverStatusLabel;
    private final Circle serverIndicator;
    private final Label statusLabel;
    private final StringProperty statusMessage;
    private final Label endpointLabel;

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

        // Server info box
        HBox serverBox = new HBox(8);
        serverBox.setAlignment(Pos.CENTER_LEFT);
        serverBox.getChildren().addAll(serverIndicator, serverStatusLabel, endpointLabel);

        // General status message
        statusLabel = new Label();
        statusLabel.textProperty().bind(statusMessage);
        statusLabel.setStyle("-fx-text-fill: #a0a0a0;");
        HBox.setHgrow(statusLabel, Priority.ALWAYS);

        // Layout
        this.setPadding(new Insets(5, 10, 5, 10));
        this.setSpacing(20);
        this.setAlignment(Pos.CENTER_LEFT);
        this.getChildren().addAll(serverBox, new Separator(), statusLabel);
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
