package com.trafficsim.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Panel for displaying MCP tool call logs and results.
 * Shows a scrollable log of all tool invocations and their outcomes.
 */
public class McpLogPanel extends VBox {

    private final TextArea logArea;
    private final Label titleLabel;
    private final Button clearButton;
    private final DateTimeFormatter timeFormatter;
    private int logEntryCount = 0;

    public McpLogPanel() {
        this.logArea = new TextArea();
        this.titleLabel = new Label("MCP Activity Log");
        this.clearButton = new Button("Clear Log");
        this.timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

        setupUI();
    }

    private void setupUI() {
        setSpacing(5);
        setPadding(new Insets(10));
        setStyle("-fx-background-color: #2b2b2b;");

        // Title bar with clear button
        HBox titleBar = new HBox(10);
        titleBar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: white;");
        HBox.setHgrow(titleLabel, Priority.ALWAYS);

        clearButton.setStyle("-fx-background-color: #4a4a4a; -fx-text-fill: white; -fx-font-size: 11px;");
        clearButton.setOnAction(e -> clearLog());

        titleBar.getChildren().addAll(titleLabel, clearButton);

        // Log area
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setStyle(
                "-fx-control-inner-background: #1a1a1a; " +
                "-fx-text-fill: #e0e0e0; " +
                "-fx-font-family: 'Courier New', monospace; " +
                "-fx-font-size: 11px;"
        );
        VBox.setVgrow(logArea, Priority.ALWAYS);

        getChildren().addAll(titleBar, logArea);
    }

    /**
     * Log a tool call being initiated.
     */
    public void logToolCall(String toolName, String params) {
        Platform.runLater(() -> {
            String timestamp = LocalDateTime.now().format(timeFormatter);
            String entry = String.format("[%s] CALL: %s\n  Params: %s\n",
                    timestamp, toolName, params);
            appendLog(entry, "#45b7d1");
            logEntryCount++;
            updateTitle();
        });
    }

    /**
     * Log a successful tool result.
     */
    public void logToolSuccess(String toolName, String result) {
        Platform.runLater(() -> {
            String timestamp = LocalDateTime.now().format(timeFormatter);
            String entry = String.format("[%s] SUCCESS: %s\n  Result: %s\n",
                    timestamp, toolName, result);
            appendLog(entry, "#27ae60");
        });
    }

    /**
     * Log a tool error.
     */
    public void logToolError(String toolName, String error) {
        Platform.runLater(() -> {
            String timestamp = LocalDateTime.now().format(timeFormatter);
            String entry = String.format("[%s] ERROR: %s\n  Error: %s\n",
                    timestamp, toolName, error);
            appendLog(entry, "#e74c3c");
        });
    }

    /**
     * Log a general message.
     */
    public void logMessage(String message) {
        Platform.runLater(() -> {
            String timestamp = LocalDateTime.now().format(timeFormatter);
            String entry = String.format("[%s] %s\n", timestamp, message);
            appendLog(entry, "#a0a0a0");
        });
    }

    /**
     * Log optimization progress.
     */
    public void logOptimization(String message) {
        Platform.runLater(() -> {
            String timestamp = LocalDateTime.now().format(timeFormatter);
            String entry = String.format("[%s] OPTIMIZE: %s\n", timestamp, message);
            appendLog(entry, "#f39c12");
        });
    }

    /**
     * Append text to log area.
     */
    private void appendLog(String text, String color) {
        // For simplicity, we'll use plain text without color coding
        // A more advanced implementation could use a ListView with styled cells
        logArea.appendText(text + "\n");

        // Auto-scroll to bottom
        logArea.setScrollTop(Double.MAX_VALUE);
    }

    /**
     * Clear the log.
     */
    public void clearLog() {
        logArea.clear();
        logEntryCount = 0;
        updateTitle();
        logMessage("Log cleared");
    }

    /**
     * Update title with entry count.
     */
    private void updateTitle() {
        titleLabel.setText(String.format("MCP Activity Log (%d calls)", logEntryCount));
    }

    /**
     * Get the log content as string.
     */
    public String getLogContent() {
        return logArea.getText();
    }
}
