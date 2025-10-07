package com.embeddedcc.ui.components;

import com.embeddedcc.ui.server.ServerMode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

/**
 * Control bar providing MCP server controls and theme toggle.
 */
public class ServerControlBar extends HBox {

    private final Circle statusIndicator = new Circle(7);
    private final Label statusLabel = new Label("Server stopped");
    private final Label modeLabel = new Label();
    private final Button launchButton = new Button("Launch MCP Server");
    private final Button stopButton = new Button("Stop");
    private final Button settingsButton = new Button("Settings");
    private final Button themeButton = new Button("\u263E"); // moon

    private Runnable onLaunch;
    private Runnable onStop;
    private Runnable onSettings;
    private Runnable onThemeToggle;

    private boolean darkTheme = true;

    public ServerControlBar() {
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(10);
        setPadding(new Insets(10));
        getStyleClass().add("server-control-bar");

        statusIndicator.setFill(Color.RED);
        statusIndicator.setStroke(Color.DARKGRAY);

        statusLabel.getStyleClass().add("server-status-text");
        modeLabel.getStyleClass().add("server-mode-label");

        launchButton.getStyleClass().add("primary-button");
        stopButton.getStyleClass().add("secondary-button");
        stopButton.setDisable(true);
        settingsButton.getStyleClass().add("secondary-button");
        themeButton.getStyleClass().add("secondary-button");

        launchButton.setTooltip(new Tooltip("Start the embedded MCP server"));
        stopButton.setTooltip(new Tooltip("Stop the MCP server"));
        settingsButton.setTooltip(new Tooltip("Configure MCP server mode and port"));
        themeButton.setTooltip(new Tooltip("Toggle Dark/Light Theme"));

        launchButton.setOnAction(e -> {
            if (onLaunch != null) {
                onLaunch.run();
            }
        });

        stopButton.setOnAction(e -> {
            if (onStop != null) {
                onStop.run();
            }
        });

        settingsButton.setOnAction(e -> {
            if (onSettings != null) {
                onSettings.run();
            }
        });

        themeButton.setOnAction(e -> {
            darkTheme = !darkTheme;
            themeButton.setText(darkTheme ? "\u263E" : "\u263C");
            if (onThemeToggle != null) {
                onThemeToggle.run();
            }
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        getChildren().addAll(
                statusIndicator,
                statusLabel,
                modeLabel,
                launchButton,
                stopButton,
                settingsButton,
                spacer,
                themeButton
        );
    }

    public void setServerRunning(boolean running, String message) {
        statusIndicator.setFill(running ? Color.GREEN : Color.RED);
        String text;
        if (message != null && !message.isBlank()) {
            text = message;
        } else {
            text = running ? "Server running" : "Server stopped";
        }
        statusLabel.setText(text);
        launchButton.setDisable(running);
        stopButton.setDisable(!running);
    }

    public void setMode(ServerMode mode) {
        String text = switch (mode) {
            case HTTP -> "(HTTP)";
            case STDIO -> "(STDIO)";
        };
        modeLabel.setText(text);
    }

    public void setDarkTheme(boolean dark) {
        this.darkTheme = dark;
        themeButton.setText(dark ? "\u263E" : "\u263C");
    }

    public void setOnLaunch(Runnable onLaunch) {
        this.onLaunch = onLaunch;
    }

    public void setOnStop(Runnable onStop) {
        this.onStop = onStop;
    }

    public void setOnSettings(Runnable onSettings) {
        this.onSettings = onSettings;
    }

    public void setOnThemeToggle(Runnable onThemeToggle) {
        this.onThemeToggle = onThemeToggle;
    }
}
