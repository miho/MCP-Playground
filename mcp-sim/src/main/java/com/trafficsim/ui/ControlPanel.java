package com.trafficsim.ui;

import com.trafficsim.model.Direction;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.util.HashMap;
import java.util.Map;

/**
 * Control panel with sliders for arrival rates and action buttons.
 */
public class ControlPanel extends VBox {
    private final Map<Direction, Slider> arrivalSliders;
    private final Button resetButton;
    private final Button spikeButton;
    private final Button baselineButton;
    private final Button optimizeButton;
    private final Button updateMetricsButton;
    private final Button applyTimingButton;
    private final Label statusLabel;
    private final Slider speedSlider;
    private final Label speedLabel;
    private final Slider nsGreenSlider;
    private final Slider ewGreenSlider;
    private final Label nsGreenLabel;
    private final Label ewGreenLabel;
    private Runnable arrivalRateChangeListener;

    public ControlPanel() {
        this.arrivalSliders = new HashMap<>();
        this.resetButton = new Button("Reset");
        this.spikeButton = new Button("Rush Hour Spike");
        this.baselineButton = new Button("Baseline Plan");
        this.optimizeButton = new Button("LLM Optimize");
        this.updateMetricsButton = new Button("Update Metrics");
        this.applyTimingButton = new Button("Apply Timing");
        this.statusLabel = new Label("Status: Ready");
        this.speedSlider = new Slider(0.5, 10, 1);
        this.speedLabel = new Label("1.0x");
        this.nsGreenSlider = new Slider(10, 60, 25);
        this.ewGreenSlider = new Slider(10, 60, 25);
        this.nsGreenLabel = new Label("25s");
        this.ewGreenLabel = new Label("25s");

        setupUI();
    }

    private void setupUI() {
        setSpacing(10);
        setPadding(new Insets(10));
        setStyle("-fx-background-color: #2b2b2b;");

        // Title
        Label title = new Label("Control Panel");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: white;");

        // Arrival rate sliders
        VBox slidersBox = new VBox(5);
        for (Direction dir : Direction.values()) {
            VBox sliderBox = createSlider(dir);
            arrivalSliders.put(dir, (Slider) ((HBox) sliderBox.getChildren().get(1)).getChildren().get(0));
            slidersBox.getChildren().add(sliderBox);
        }

        // Buttons
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER);

        resetButton.setStyle("-fx-background-color: #4a4a4a; -fx-text-fill: white;");
        spikeButton.setStyle("-fx-background-color: #ff6b6b; -fx-text-fill: white;");
        baselineButton.setStyle("-fx-background-color: #4ecdc4; -fx-text-fill: white;");
        optimizeButton.setStyle("-fx-background-color: #45b7d1; -fx-text-fill: white;");
        updateMetricsButton.setStyle("-fx-background-color: #95a5a6; -fx-text-fill: white; -fx-font-size: 11px;");

        buttonBox.getChildren().addAll(resetButton, spikeButton);

        HBox planButtonBox = new HBox(10);
        planButtonBox.setAlignment(Pos.CENTER);
        planButtonBox.getChildren().addAll(baselineButton, optimizeButton);

        HBox metricsButtonBox = new HBox(10);
        metricsButtonBox.setAlignment(Pos.CENTER);
        metricsButtonBox.getChildren().add(updateMetricsButton);

        // Simulation speed control
        VBox speedBox = createSpeedControl();

        // Signal timing control
        VBox timingBox = createTimingControl();

        // Status label
        statusLabel.setStyle("-fx-text-fill: #a0a0a0; -fx-font-style: italic;");

        getChildren().addAll(
                title,
                new Label("Arrival Rates (veh/min)") {{
                    setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
                }},
                slidersBox,
                new Separator(),
                timingBox,
                new Separator(),
                speedBox,
                new Separator(),
                buttonBox,
                planButtonBox,
                metricsButtonBox,
                new Separator(),
                statusLabel
        );
    }

    private VBox createSlider(Direction dir) {
        VBox box = new VBox(3);

        Label label = new Label(dir.name() + ":");
        label.setStyle("-fx-text-fill: white;");

        Slider slider = new Slider(0, 40, 10);
        slider.setShowTickLabels(true);
        slider.setShowTickMarks(true);
        slider.setMajorTickUnit(10);
        slider.setMinorTickCount(1);

        Label valueLabel = new Label("10.0");
        valueLabel.setStyle("-fx-text-fill: white;");

        slider.valueProperty().addListener((obs, oldVal, newVal) -> {
            valueLabel.setText(String.format("%.1f", newVal.doubleValue()));
            // Notify listener when arrival rate changes
            if (arrivalRateChangeListener != null) {
                arrivalRateChangeListener.run();
            }
        });

        HBox sliderBox = new HBox(10);
        sliderBox.setAlignment(Pos.CENTER_LEFT);
        sliderBox.getChildren().addAll(slider, valueLabel);

        box.getChildren().addAll(label, sliderBox);
        return box;
    }

    public Map<Direction, Double> getArrivalRates() {
        Map<Direction, Double> rates = new HashMap<>();
        for (Map.Entry<Direction, Slider> entry : arrivalSliders.entrySet()) {
            rates.put(entry.getKey(), entry.getValue().getValue());
        }
        return rates;
    }

    public void setArrivalRate(Direction dir, double value) {
        Slider slider = arrivalSliders.get(dir);
        if (slider != null) {
            slider.setValue(value);
        }
    }

    public Button getResetButton() {
        return resetButton;
    }

    public Button getSpikeButton() {
        return spikeButton;
    }

    public Button getBaselineButton() {
        return baselineButton;
    }

    public Button getOptimizeButton() {
        return optimizeButton;
    }

    public Button getUpdateMetricsButton() {
        return updateMetricsButton;
    }

    private VBox createSpeedControl() {
        VBox box = new VBox(8);

        Label title = new Label("Simulation Speed");
        title.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");

        // Speed slider
        speedSlider.setShowTickMarks(true);
        speedSlider.setMajorTickUnit(2);
        speedSlider.setMinorTickCount(1);
        speedSlider.setBlockIncrement(0.5);

        speedLabel.setStyle("-fx-text-fill: #45b7d1; -fx-font-size: 16px; -fx-font-weight: bold;");

        speedSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            speedLabel.setText(String.format("%.1fx", newVal.doubleValue()));
        });

        HBox sliderBox = new HBox(10);
        sliderBox.setAlignment(Pos.CENTER_LEFT);
        sliderBox.getChildren().addAll(speedSlider, speedLabel);

        // Quick speed buttons
        HBox quickButtons = new HBox(5);
        quickButtons.setAlignment(Pos.CENTER);

        Button speed1x = new Button("1x");
        Button speed2x = new Button("2x");
        Button speed5x = new Button("5x");
        Button speed10x = new Button("10x");

        String buttonStyle = "-fx-background-color: #3a3a3a; -fx-text-fill: white; -fx-font-size: 11px; -fx-padding: 3 8 3 8;";
        speed1x.setStyle(buttonStyle);
        speed2x.setStyle(buttonStyle);
        speed5x.setStyle(buttonStyle);
        speed10x.setStyle(buttonStyle);

        speed1x.setOnAction(e -> speedSlider.setValue(1));
        speed2x.setOnAction(e -> speedSlider.setValue(2));
        speed5x.setOnAction(e -> speedSlider.setValue(5));
        speed10x.setOnAction(e -> speedSlider.setValue(10));

        quickButtons.getChildren().addAll(speed1x, speed2x, speed5x, speed10x);

        box.getChildren().addAll(title, sliderBox, quickButtons);
        return box;
    }

    public double getSimulationSpeed() {
        return speedSlider.getValue();
    }

    public void setStatus(String status) {
        statusLabel.setText("Status: " + status);
    }

    private VBox createTimingControl() {
        VBox box = new VBox(8);

        Label title = new Label("Signal Timing");
        title.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px;");

        // N-S green time
        Label nsLabel = new Label("N-S Green:");
        nsLabel.setStyle("-fx-text-fill: white;");

        nsGreenSlider.setMin(10);
        nsGreenSlider.setMax(120);
        nsGreenSlider.setValue(30);
        nsGreenSlider.setShowTickMarks(true);
        nsGreenSlider.setMajorTickUnit(20);
        nsGreenSlider.setBlockIncrement(5);
        nsGreenLabel.setStyle("-fx-text-fill: #4ecdc4; -fx-font-weight: bold;");

        nsGreenSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            nsGreenLabel.setText(String.format("%ds", newVal.intValue()));
        });

        HBox nsBox = new HBox(10);
        nsBox.setAlignment(Pos.CENTER_LEFT);
        nsBox.getChildren().addAll(nsGreenSlider, nsGreenLabel);

        // E-W green time
        Label ewLabel = new Label("E-W Green:");
        ewLabel.setStyle("-fx-text-fill: white;");

        ewGreenSlider.setMin(10);
        ewGreenSlider.setMax(120);
        ewGreenSlider.setValue(30);
        ewGreenSlider.setShowTickMarks(true);
        ewGreenSlider.setMajorTickUnit(20);
        ewGreenSlider.setBlockIncrement(5);
        ewGreenLabel.setStyle("-fx-text-fill: #4ecdc4; -fx-font-weight: bold;");

        ewGreenSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            ewGreenLabel.setText(String.format("%ds", newVal.intValue()));
        });

        HBox ewBox = new HBox(10);
        ewBox.setAlignment(Pos.CENTER_LEFT);
        ewBox.getChildren().addAll(ewGreenSlider, ewGreenLabel);

        // Cycle time display
        Label cycleLabel = new Label("Total Cycle: 68s");
        cycleLabel.setStyle("-fx-text-fill: #95a5a6; -fx-font-size: 11px;");

        // Update cycle display when sliders change
        Runnable updateCycle = () -> {
            int ns = (int) nsGreenSlider.getValue();
            int ew = (int) ewGreenSlider.getValue();
            int cycle = ns + ew + 8; // +8 for yellow and all-red times
            cycleLabel.setText(String.format("Total Cycle: %ds (Yellow: 6s, All-Red: 2s)", cycle));
        };

        nsGreenSlider.valueProperty().addListener((obs, old, val) -> updateCycle.run());
        ewGreenSlider.valueProperty().addListener((obs, old, val) -> updateCycle.run());
        updateCycle.run();

        // Apply button
        applyTimingButton.setText("Apply Signal Timing");
        applyTimingButton.setStyle("-fx-background-color: #f39c12; -fx-text-fill: white; -fx-font-weight: bold;");

        box.getChildren().addAll(title, nsLabel, nsBox, ewLabel, ewBox, cycleLabel, applyTimingButton);
        return box;
    }

    public Button getApplyTimingButton() {
        return applyTimingButton;
    }

    public int getNsGreenTime() {
        return (int) nsGreenSlider.getValue();
    }

    public int getEwGreenTime() {
        return (int) ewGreenSlider.getValue();
    }

    /**
     * Update slider values to match current signal plan.
     * Used when MCP tools change the timing.
     */
    public void setGreenTimes(int nsGreen, int ewGreen) {
        nsGreenSlider.setValue(nsGreen);
        ewGreenSlider.setValue(ewGreen);
    }

    public void setNsGreenTime(int seconds) {
        nsGreenSlider.setValue(seconds);
    }

    public void setEwGreenTime(int seconds) {
        ewGreenSlider.setValue(seconds);
    }

    public void setOnArrivalRateChange(Runnable listener) {
        this.arrivalRateChangeListener = listener;
    }
}
