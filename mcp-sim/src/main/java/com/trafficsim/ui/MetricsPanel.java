package com.trafficsim.ui;

import com.trafficsim.model.SimulationMetrics;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/**
 * Panel displaying simulation performance metrics.
 */
public class MetricsPanel extends VBox {
    private final Label avgDelayLabel;
    private final Label queueP95Label;
    private final Label throughputLabel;
    private final Label stopsPerVehLabel;

    public MetricsPanel() {
        this.avgDelayLabel = createMetricLabel();
        this.queueP95Label = createMetricLabel();
        this.throughputLabel = createMetricLabel();
        this.stopsPerVehLabel = createMetricLabel();

        setupUI();
    }

    private void setupUI() {
        setSpacing(10);
        setPadding(new Insets(10));
        setStyle("-fx-background-color: #2b2b2b;");

        Label title = new Label("Performance Metrics");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: white;");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);

        int row = 0;
        addMetricRow(grid, row++, "Avg Delay:", avgDelayLabel);
        addMetricRow(grid, row++, "Queue P95:", queueP95Label);
        addMetricRow(grid, row++, "Throughput:", throughputLabel);
        addMetricRow(grid, row++, "Stops/Veh:", stopsPerVehLabel);

        getChildren().addAll(title, grid);
    }

    private void addMetricRow(GridPane grid, int row, String labelText, Label valueLabel) {
        Label label = new Label(labelText);
        label.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
        grid.add(label, 0, row);
        grid.add(valueLabel, 1, row);
    }

    private Label createMetricLabel() {
        Label label = new Label("--");
        label.setStyle("-fx-text-fill: #4ecdc4; -fx-font-size: 16px;");
        return label;
    }

    public void updateMetrics(SimulationMetrics metrics) {
        avgDelayLabel.setText(String.format("%.1f s", metrics.getAvgDelaySec()));
        queueP95Label.setText(String.format("%.0f veh", metrics.getQueueP95()));
        throughputLabel.setText(String.format("%.0f vph", metrics.getThroughputVph()));
        stopsPerVehLabel.setText(String.format("%.2f", metrics.getStopsPerVeh()));
    }

    public void clear() {
        avgDelayLabel.setText("--");
        queueP95Label.setText("--");
        throughputLabel.setText("--");
        stopsPerVehLabel.setText("--");
    }
}
