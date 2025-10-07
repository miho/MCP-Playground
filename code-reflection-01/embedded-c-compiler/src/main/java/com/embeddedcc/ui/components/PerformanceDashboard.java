package com.embeddedcc.ui.components;

import com.embeddedcc.analysis.CacheSummary;
import javafx.animation.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.*;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

import java.text.DecimalFormat;

/**
 * Modern performance dashboard displaying cache statistics with gauges,
 * charts, and animated metric cards.
 */
public class PerformanceDashboard extends VBox {

    private final Label hitRateLabel = new Label("0%");
    private final Label missCountLabel = new Label("0");
    private final Label evictionCountLabel = new Label("0");
    private final Label totalAccessLabel = new Label("0");

    private final Arc hitRateGauge = new Arc();
    private final PieChart distributionChart = new PieChart();
    private final BarChart<String, Number> metricsChart;

    private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("#0.0");
    private static final DecimalFormat NUMBER_FORMAT = new DecimalFormat("#,###");

    public PerformanceDashboard() {
        setSpacing(16);
        setPadding(new Insets(16));
        getStyleClass().add("performance-dashboard");

        // Title
        Label title = new Label("Performance Metrics");
        title.getStyleClass().add("dashboard-title");

        // Top row: Metric cards
        HBox metricsRow = createMetricsRow();

        // Middle: Gauge and Pie Chart
        HBox visualsRow = createVisualsRow();

        // Bottom: Bar chart for detailed metrics
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        metricsChart = new BarChart<>(xAxis, yAxis);
        metricsChart.setTitle("Cache Event Distribution");
        metricsChart.setLegendVisible(false);
        metricsChart.setPrefHeight(200);
        metricsChart.getStyleClass().add("metrics-chart");

        getChildren().addAll(title, metricsRow, visualsRow, metricsChart);
        VBox.setVgrow(metricsChart, Priority.SOMETIMES);
    }

    /**
     * Creates the top row of metric cards
     */
    private HBox createMetricsRow() {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER);

        VBox hitRateCard = createMetricCard("Cache Hit Rate", hitRateLabel, "metric-card-success");
        VBox missCard = createMetricCard("Total Misses", missCountLabel, "metric-card-warning");
        VBox evictionCard = createMetricCard("Evictions", evictionCountLabel, "metric-card-danger");
        VBox totalCard = createMetricCard("Total Accesses", totalAccessLabel, "metric-card-info");

        row.getChildren().addAll(hitRateCard, missCard, evictionCard, totalCard);
        HBox.setHgrow(hitRateCard, Priority.ALWAYS);
        HBox.setHgrow(missCard, Priority.ALWAYS);
        HBox.setHgrow(evictionCard, Priority.ALWAYS);
        HBox.setHgrow(totalCard, Priority.ALWAYS);

        return row;
    }

    /**
     * Creates a modern metric card with icon and value
     */
    private VBox createMetricCard(String title, Label valueLabel, String styleClass) {
        VBox card = new VBox(8);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(16));
        card.getStyleClass().addAll("metric-card", styleClass);

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("metric-card-title");

        valueLabel.getStyleClass().add("metric-card-value");

        card.getChildren().addAll(titleLabel, valueLabel);
        return card;
    }

    /**
     * Creates the row with gauge and pie chart
     */
    private HBox createVisualsRow() {
        HBox row = new HBox(16);
        row.setAlignment(Pos.CENTER);

        // Hit rate gauge
        VBox gaugeContainer = createHitRateGauge();

        // Distribution pie chart
        distributionChart.setTitle("Event Distribution");
        distributionChart.setPrefSize(300, 250);
        distributionChart.setLegendVisible(true);
        distributionChart.getStyleClass().add("distribution-chart");

        row.getChildren().addAll(gaugeContainer, distributionChart);
        HBox.setHgrow(gaugeContainer, Priority.NEVER);
        HBox.setHgrow(distributionChart, Priority.ALWAYS);

        return row;
    }

    /**
     * Creates an animated circular gauge for hit rate
     */
    private VBox createHitRateGauge() {
        VBox container = new VBox(8);
        container.setAlignment(Pos.CENTER);
        container.setPrefWidth(250);

        Label gaugeTitle = new Label("Hit Rate");
        gaugeTitle.getStyleClass().add("gauge-title");

        StackPane gaugePane = new StackPane();
        gaugePane.setPrefSize(180, 180);

        // Background circle
        Circle background = new Circle(80);
        background.getStyleClass().add("gauge-background");

        // Gauge arc
        hitRateGauge.setCenterX(0);
        hitRateGauge.setCenterY(0);
        hitRateGauge.setRadiusX(70);
        hitRateGauge.setRadiusY(70);
        hitRateGauge.setStartAngle(90);
        hitRateGauge.setLength(0);
        hitRateGauge.setType(ArcType.OPEN);
        hitRateGauge.getStyleClass().add("gauge-arc");
        hitRateGauge.setStrokeWidth(12);
        hitRateGauge.setFill(null);

        // Center label
        Label centerLabel = new Label("0%");
        centerLabel.getStyleClass().add("gauge-center-label");

        gaugePane.getChildren().addAll(background, hitRateGauge, centerLabel);

        container.getChildren().addAll(gaugeTitle, gaugePane);
        return container;
    }

    /**
     * Updates dashboard with new cache summary data
     */
    public void updateMetrics(CacheSummary summary) {
        if (summary == null) {
            reset();
            return;
        }

        int hits = summary.getHits();
        int misses = summary.getMisses();
        int evictions = summary.getEvictions();
        int total = hits + misses;

        double hitRate = total > 0 ? (hits * 100.0) / total : 0.0;

        // Animate metric cards
        animateValue(hitRateLabel, hitRate, PERCENT_FORMAT.format(hitRate) + "%");
        animateValue(missCountLabel, misses, NUMBER_FORMAT.format(misses));
        animateValue(evictionCountLabel, evictions, NUMBER_FORMAT.format(evictions));
        animateValue(totalAccessLabel, total, NUMBER_FORMAT.format(total));

        // Animate gauge
        animateGauge(hitRate);

        // Update pie chart
        updatePieChart(hits, misses, evictions);

        // Update bar chart
        updateBarChart(hits, misses, evictions);
    }

    /**
     * Animates a label value with fade transition
     */
    private void animateValue(Label label, double value, String displayText) {
        FadeTransition fade = new FadeTransition(Duration.millis(300), label);
        fade.setFromValue(1.0);
        fade.setToValue(0.3);
        fade.setAutoReverse(true);
        fade.setCycleCount(2);

        fade.setOnFinished(e -> label.setText(displayText));
        fade.play();
    }

    /**
     * Animates the hit rate gauge arc
     */
    private void animateGauge(double hitRate) {
        double targetAngle = -(hitRate / 100.0) * 270; // 270 degrees max

        Timeline timeline = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(hitRateGauge.lengthProperty(), hitRateGauge.getLength(), Interpolator.EASE_BOTH)),
            new KeyFrame(Duration.millis(1000),
                new KeyValue(hitRateGauge.lengthProperty(), targetAngle, Interpolator.EASE_BOTH))
        );
        timeline.play();

        // Update gauge color based on hit rate
        hitRateGauge.getStyleClass().removeAll("gauge-excellent", "gauge-good", "gauge-fair", "gauge-poor");
        if (hitRate >= 90) {
            hitRateGauge.getStyleClass().add("gauge-excellent");
        } else if (hitRate >= 75) {
            hitRateGauge.getStyleClass().add("gauge-good");
        } else if (hitRate >= 50) {
            hitRateGauge.getStyleClass().add("gauge-fair");
        } else {
            hitRateGauge.getStyleClass().add("gauge-poor");
        }

        // Update center label
        StackPane gaugeParent = (StackPane) hitRateGauge.getParent();
        if (gaugeParent != null) {
            for (Node child : gaugeParent.getChildren()) {
                if (child instanceof Label && child.getStyleClass().contains("gauge-center-label")) {
                    ((Label) child).setText(PERCENT_FORMAT.format(hitRate) + "%");
                }
            }
        }
    }

    /**
     * Updates pie chart with event distribution
     */
    private void updatePieChart(int hits, int misses, int evictions) {
        distributionChart.getData().clear();

        if (hits > 0) {
            PieChart.Data hitData = new PieChart.Data("Hits", hits);
            distributionChart.getData().add(hitData);
        }

        if (misses > 0) {
            PieChart.Data missData = new PieChart.Data("Misses", misses);
            distributionChart.getData().add(missData);
        }

        if (evictions > 0) {
            PieChart.Data evictionData = new PieChart.Data("Evictions", evictions);
            distributionChart.getData().add(evictionData);
        }

        // Apply custom colors
        distributionChart.getData().forEach(data -> {
            Node node = data.getNode();
            switch (data.getName()) {
                case "Hits" -> node.getStyleClass().add("pie-hits");
                case "Misses" -> node.getStyleClass().add("pie-misses");
                case "Evictions" -> node.getStyleClass().add("pie-evictions");
            }
        });
    }

    /**
     * Updates bar chart with detailed metrics
     */
    private void updateBarChart(int hits, int misses, int evictions) {
        metricsChart.getData().clear();

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Events");

        series.getData().add(new XYChart.Data<>("Hits", hits));
        series.getData().add(new XYChart.Data<>("Misses", misses));
        series.getData().add(new XYChart.Data<>("Evictions", evictions));

        metricsChart.getData().add(series);

        // Animate bars
        metricsChart.getData().forEach(dataSeries -> {
            dataSeries.getData().forEach(data -> {
                Node bar = data.getNode();
                if (bar != null) {
                    bar.setScaleY(0);
                    ScaleTransition st = new ScaleTransition(Duration.millis(800), bar);
                    st.setToY(1);
                    st.setInterpolator(Interpolator.EASE_BOTH);
                    st.play();
                }
            });
        });
    }

    /**
     * Resets dashboard to initial state
     */
    public void reset() {
        hitRateLabel.setText("0%");
        missCountLabel.setText("0");
        evictionCountLabel.setText("0");
        totalAccessLabel.setText("0");

        hitRateGauge.setLength(0);
        distributionChart.getData().clear();
        metricsChart.getData().clear();
    }
}
