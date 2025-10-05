package com.devicesim.ui;

import com.devicesim.data.CsvDataReader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Control panel for CSV file selection, filtering, and device control.
 *
 * @since 1.0.0
 */
public class ControlPanel extends VBox {

    private final TextField csvPathField;
    private final Button browseButton;
    private final ComboBox<String> xColumnCombo;
    private final ComboBox<String> yColumnCombo;
    private final VBox filterRowsContainer;
    private final Button addFilterButton;
    private final Button loadLocationsButton;
    private final Slider speedSlider;
    private final Label speedLabel;
    private final Slider accelerationSlider;
    private final Label accelerationLabel;
    private final Button startPauseButton;
    private final Button markVisitedButton;
    private final Button resetButton;
    private final Label statusLabel;

    private final List<FilterRow> filterRows = new ArrayList<>();
    private List<String> availableHeaders = new ArrayList<>();
    private boolean isRunning = false;

    private Runnable onBrowseFile;
    private Runnable onLoadLocations;
    private Runnable onStartPause;
    private Runnable onMarkVisited;
    private Runnable onReset;
    private Runnable onSpeedChange;
    private Runnable onAccelerationChange;

    public ControlPanel() {
        this.csvPathField = new TextField();
        this.browseButton = new Button("Browse...");
        this.xColumnCombo = new ComboBox<>();
        this.yColumnCombo = new ComboBox<>();
        this.filterRowsContainer = new VBox(5);
        this.addFilterButton = new Button("+ Add Filter");
        this.loadLocationsButton = new Button("Load Locations");
        this.speedSlider = new Slider(1, 100, 10);
        this.speedLabel = new Label("10.0 u/s");
        this.accelerationSlider = new Slider(1, 50, 5);
        this.accelerationLabel = new Label("5.0 u/s²");
        this.startPauseButton = new Button("Start");
        this.markVisitedButton = new Button("Mark Visited");
        this.resetButton = new Button("Reset");
        this.statusLabel = new Label("Ready");

        setupUI();
    }

    private void setupUI() {
        setSpacing(10);
        setPadding(new Insets(10));
        setStyle("-fx-background-color: #2b2b2b;");
        setMinWidth(200);
        setPrefWidth(300);
        setMaxWidth(Double.MAX_VALUE);

        // Title
        Label title = new Label("Control Panel");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: white;");

        // CSV File Selection
        VBox csvBox = createCsvSelectionBox();

        // Column Selection
        VBox columnBox = createColumnSelectionBox();

        // Filter Section
        VBox filterBox = createFilterBox();

        // Load Button
        loadLocationsButton.setStyle("-fx-background-color: #4ecdc4; -fx-text-fill: white; -fx-font-weight: bold;");
        loadLocationsButton.setPrefWidth(Double.MAX_VALUE);
        loadLocationsButton.setOnAction(e -> {
            if (onLoadLocations != null) {
                onLoadLocations.run();
            }
        });

        // Speed Control
        VBox speedBox = createSpeedControl();

        // Acceleration Control
        VBox accelBox = createAccelerationControl();

        // Control Buttons
        HBox buttonBox = createControlButtons();

        // Status Label
        statusLabel.setStyle("-fx-text-fill: #a0a0a0; -fx-font-style: italic; -fx-wrap-text: true;");
        statusLabel.setPrefWidth(280);

        // Add all sections
        getChildren().addAll(
                title,
                new Separator(),
                csvBox,
                columnBox,
                new Separator(),
                filterBox,
                new Separator(),
                loadLocationsButton,
                new Separator(),
                speedBox,
                accelBox,
                new Separator(),
                buttonBox,
                new Separator(),
                statusLabel
        );
    }

    private VBox createCsvSelectionBox() {
        VBox box = new VBox(5);

        Label label = new Label("CSV File:");
        label.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");

        csvPathField.setPromptText("Select CSV file...");
        csvPathField.setEditable(false);
        csvPathField.setStyle("-fx-background-color: #3a3a3a; -fx-text-fill: white;");

        browseButton.setStyle("-fx-background-color: #4a4a4a; -fx-text-fill: white;");
        browseButton.setOnAction(e -> {
            if (onBrowseFile != null) {
                onBrowseFile.run();
            }
        });

        HBox pathBox = new HBox(5);
        HBox.setHgrow(csvPathField, Priority.ALWAYS);
        pathBox.getChildren().addAll(csvPathField, browseButton);

        box.getChildren().addAll(label, pathBox);
        return box;
    }

    private VBox createColumnSelectionBox() {
        VBox box = new VBox(5);

        Label label = new Label("Coordinate Columns:");
        label.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");

        // X Column
        HBox xBox = new HBox(5);
        xBox.setAlignment(Pos.CENTER_LEFT);
        Label xLabel = new Label("X:");
        xLabel.setStyle("-fx-text-fill: white;");
        xLabel.setPrefWidth(20);
        xColumnCombo.setPromptText("Select X column");
        xColumnCombo.setPrefWidth(200);
        xColumnCombo.setStyle("-fx-background-color: #3a3a3a;");
        xBox.getChildren().addAll(xLabel, xColumnCombo);

        // Y Column
        HBox yBox = new HBox(5);
        yBox.setAlignment(Pos.CENTER_LEFT);
        Label yLabel = new Label("Y:");
        yLabel.setStyle("-fx-text-fill: white;");
        yLabel.setPrefWidth(20);
        yColumnCombo.setPromptText("Select Y column");
        yColumnCombo.setPrefWidth(200);
        yColumnCombo.setStyle("-fx-background-color: #3a3a3a;");
        yBox.getChildren().addAll(yLabel, yColumnCombo);

        box.getChildren().addAll(label, xBox, yBox);
        return box;
    }

    private VBox createFilterBox() {
        VBox box = new VBox(5);

        Label label = new Label("Filters:");
        label.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");

        ScrollPane scrollPane = new ScrollPane(filterRowsContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(120);
        scrollPane.setStyle("-fx-background: #2b2b2b; -fx-background-color: #2b2b2b;");

        addFilterButton.setStyle("-fx-background-color: #4a4a4a; -fx-text-fill: white; -fx-font-size: 11px;");
        addFilterButton.setPrefWidth(Double.MAX_VALUE);
        addFilterButton.setOnAction(e -> addFilterRow());

        box.getChildren().addAll(label, scrollPane, addFilterButton);
        return box;
    }

    private VBox createSpeedControl() {
        VBox box = new VBox(5);

        Label label = new Label("Max Speed:");
        label.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");

        speedSlider.setShowTickLabels(false);
        speedSlider.setShowTickMarks(true);
        speedSlider.setMajorTickUnit(25);
        speedSlider.setMinorTickCount(4);

        speedLabel.setStyle("-fx-text-fill: #45b7d1; -fx-font-size: 14px; -fx-font-weight: bold;");

        speedSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            speedLabel.setText(String.format("%.1f u/s", newVal.doubleValue()));
            if (onSpeedChange != null) {
                onSpeedChange.run();
            }
        });

        HBox sliderBox = new HBox(10);
        sliderBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(speedSlider, Priority.ALWAYS);
        sliderBox.getChildren().addAll(speedSlider, speedLabel);

        box.getChildren().addAll(label, sliderBox);
        return box;
    }

    private VBox createAccelerationControl() {
        VBox box = new VBox(5);

        Label label = new Label("Acceleration:");
        label.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");

        accelerationSlider.setShowTickLabels(false);
        accelerationSlider.setShowTickMarks(true);
        accelerationSlider.setMajorTickUnit(10);
        accelerationSlider.setMinorTickCount(4);

        accelerationLabel.setStyle("-fx-text-fill: #f39c12; -fx-font-size: 14px; -fx-font-weight: bold;");

        accelerationSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            accelerationLabel.setText(String.format("%.1f u/s²", newVal.doubleValue()));
            if (onAccelerationChange != null) {
                onAccelerationChange.run();
            }
        });

        HBox sliderBox = new HBox(10);
        sliderBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(accelerationSlider, Priority.ALWAYS);
        sliderBox.getChildren().addAll(accelerationSlider, accelerationLabel);

        box.getChildren().addAll(label, sliderBox);
        return box;
    }

    private HBox createControlButtons() {
        HBox box = new HBox(5);
        box.setAlignment(Pos.CENTER);

        startPauseButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold;");
        startPauseButton.setPrefWidth(80);
        startPauseButton.setOnAction(e -> {
            if (onStartPause != null) {
                onStartPause.run();
            }
        });

        markVisitedButton.setStyle("-fx-background-color: #f39c12; -fx-text-fill: white;");
        markVisitedButton.setPrefWidth(100);
        markVisitedButton.setOnAction(e -> {
            if (onMarkVisited != null) {
                onMarkVisited.run();
            }
        });

        resetButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white;");
        resetButton.setPrefWidth(70);
        resetButton.setOnAction(e -> {
            if (onReset != null) {
                onReset.run();
            }
        });

        box.getChildren().addAll(startPauseButton, markVisitedButton, resetButton);
        return box;
    }

    /**
     * Add a new filter row.
     */
    private void addFilterRow() {
        FilterRow row = new FilterRow();
        filterRows.add(row);
        filterRowsContainer.getChildren().add(row);
    }

    /**
     * Remove a filter row.
     */
    private void removeFilterRow(FilterRow row) {
        filterRows.remove(row);
        filterRowsContainer.getChildren().remove(row);
    }

    /**
     * Update available headers for filter dropdowns.
     */
    public void updateHeaders(List<String> headers) {
        this.availableHeaders = new ArrayList<>(headers);
        xColumnCombo.getItems().clear();
        yColumnCombo.getItems().clear();
        xColumnCombo.getItems().addAll(headers);
        yColumnCombo.getItems().addAll(headers);

        // Update filter rows
        for (FilterRow row : filterRows) {
            row.updateColumns(headers);
        }
    }

    /**
     * Get all filter criteria.
     */
    public Map<String, CsvDataReader.FilterCriteria> getFilters() {
        Map<String, CsvDataReader.FilterCriteria> filters = new HashMap<>();
        for (FilterRow row : filterRows) {
            CsvDataReader.FilterCriteria criteria = row.getCriteria();
            if (criteria != null) {
                filters.put(criteria.getColumnName(), criteria);
            }
        }
        return filters;
    }

    /**
     * Toggle running state.
     */
    public void setRunning(boolean running) {
        this.isRunning = running;
        if (running) {
            startPauseButton.setText("Pause");
            startPauseButton.setStyle("-fx-background-color: #f39c12; -fx-text-fill: white; -fx-font-weight: bold;");
        } else {
            startPauseButton.setText("Start");
            startPauseButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold;");
        }
    }

    // Getters and setters

    public String getCsvPath() {
        return csvPathField.getText();
    }

    public void setCsvPath(String path) {
        csvPathField.setText(path);
    }

    public String getXColumn() {
        return xColumnCombo.getValue();
    }

    public String getYColumn() {
        return yColumnCombo.getValue();
    }

    public double getSpeed() {
        return speedSlider.getValue();
    }

    public double getAcceleration() {
        return accelerationSlider.getValue();
    }

    public void setStatus(String status) {
        statusLabel.setText(status);
    }

    public void setOnBrowseFile(Runnable handler) {
        this.onBrowseFile = handler;
    }

    public void setOnLoadLocations(Runnable handler) {
        this.onLoadLocations = handler;
    }

    public void setOnStartPause(Runnable handler) {
        this.onStartPause = handler;
    }

    public void setOnMarkVisited(Runnable handler) {
        this.onMarkVisited = handler;
    }

    public void setOnReset(Runnable handler) {
        this.onReset = handler;
    }

    public void setOnSpeedChange(Runnable handler) {
        this.onSpeedChange = handler;
    }

    public void setOnAccelerationChange(Runnable handler) {
        this.onAccelerationChange = handler;
    }

    /**
     * Inner class representing a single filter row.
     */
    private class FilterRow extends HBox {
        private final ComboBox<String> columnCombo;
        private final TextField minField;
        private final TextField maxField;
        private final TextField equalsField;
        private final Button removeButton;

        public FilterRow() {
            setSpacing(5);
            setAlignment(Pos.CENTER_LEFT);
            setStyle("-fx-background-color: #3a3a3a; -fx-padding: 5;");

            columnCombo = new ComboBox<>();
            columnCombo.setPromptText("Column");
            columnCombo.setPrefWidth(80);
            columnCombo.getItems().addAll(availableHeaders);

            minField = new TextField();
            minField.setPromptText("Min");
            minField.setPrefWidth(50);

            maxField = new TextField();
            maxField.setPromptText("Max");
            maxField.setPrefWidth(50);

            equalsField = new TextField();
            equalsField.setPromptText("Equals");
            equalsField.setPrefWidth(60);

            removeButton = new Button("X");
            removeButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-size: 10px;");
            removeButton.setPrefWidth(25);
            removeButton.setOnAction(e -> removeFilterRow(this));

            getChildren().addAll(columnCombo, minField, maxField, equalsField, removeButton);
        }

        public void updateColumns(List<String> columns) {
            String selected = columnCombo.getValue();
            columnCombo.getItems().clear();
            columnCombo.getItems().addAll(columns);
            if (columns.contains(selected)) {
                columnCombo.setValue(selected);
            }
        }

        public CsvDataReader.FilterCriteria getCriteria() {
            String column = columnCombo.getValue();
            if (column == null || column.isEmpty()) {
                return null;
            }

            String equalsText = equalsField.getText().trim();
            if (!equalsText.isEmpty()) {
                // Try to parse as number, otherwise treat as string
                try {
                    return new CsvDataReader.FilterCriteria(column, Double.parseDouble(equalsText));
                } catch (NumberFormatException e) {
                    return new CsvDataReader.FilterCriteria(column, equalsText);
                }
            }

            String minText = minField.getText().trim();
            String maxText = maxField.getText().trim();

            Double min = minText.isEmpty() ? null : Double.parseDouble(minText);
            Double max = maxText.isEmpty() ? null : Double.parseDouble(maxText);

            if (min == null && max == null) {
                return null;
            }

            return new CsvDataReader.FilterCriteria(column, min, max);
        }
    }
}
