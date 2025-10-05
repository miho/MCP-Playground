package com.trafficsim.ui;

import com.trafficsim.engine.IntersectionSimulator;
import com.trafficsim.model.Direction;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.Queue;

/**
 * Renders the intersection visualization on a JavaFX Canvas.
 */
public class IntersectionRenderer {
    private final Canvas canvas;
    private final IntersectionSimulator simulator;

    private static final double LANE_WIDTH = 60;
    private static final double CAR_SIZE = 40;
    private static final double SIGNAL_SIZE = 20;

    private double currentSpeed = 1.0;

    public IntersectionRenderer(Canvas canvas, IntersectionSimulator simulator) {
        this.canvas = canvas;
        this.simulator = simulator;
    }

    public void setSimulationSpeed(double speed) {
        this.currentSpeed = speed;
    }

    public void render() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        double centerX = width / 2;
        double centerY = height / 2;

        // Clear canvas
        gc.setFill(Color.rgb(40, 40, 40));
        gc.fillRect(0, 0, width, height);

        // Draw roads
        drawRoads(gc, centerX, centerY);

        // Draw traffic signals
        drawSignals(gc, centerX, centerY);

        // Draw vehicles
        drawVehicles(gc, centerX, centerY);

        // Draw labels
        drawLabels(gc, centerX, centerY);
    }

    private void drawRoads(GraphicsContext gc, double centerX, double centerY) {
        gc.setFill(Color.rgb(80, 80, 80));

        // Horizontal road
        gc.fillRect(0, centerY - LANE_WIDTH, canvas.getWidth(), LANE_WIDTH * 2);

        // Vertical road
        gc.fillRect(centerX - LANE_WIDTH, 0, LANE_WIDTH * 2, canvas.getHeight());

        // Road markings (dashed lines)
        gc.setStroke(Color.YELLOW);
        gc.setLineWidth(2);
        gc.setLineDashes(10, 10);

        // Horizontal center line
        gc.strokeLine(0, centerY, canvas.getWidth(), centerY);

        // Vertical center line
        gc.strokeLine(centerX, 0, centerX, canvas.getHeight());

        gc.setLineDashes(null);
    }

    private void drawSignals(GraphicsContext gc, double centerX, double centerY) {
        // Get which directions have green in current phase
        var currentPlan = simulator.getCurrentPlan();
        if (currentPlan == null) {
            return;
        }

        String signalState = simulator.getCurrentSignalState();
        var phases = currentPlan.getPhases();
        var currentPhase = phases.get(simulator.getCurrentPhaseIndex());
        String phaseName = currentPhase.getName().toUpperCase();

        // Determine which directions have green
        boolean nsGreen = phaseName.contains("NS") || phaseName.contains("NORTH") || phaseName.contains("SOUTH");
        boolean ewGreen = phaseName.contains("EW") || phaseName.contains("EAST") || phaseName.contains("WEST");

        double offset = LANE_WIDTH + 10;

        // North signal
        Color northColor = getSignalColor(nsGreen, signalState);
        gc.setFill(northColor);
        gc.fillOval(centerX - SIGNAL_SIZE / 2, centerY - offset - SIGNAL_SIZE, SIGNAL_SIZE, SIGNAL_SIZE);

        // South signal
        Color southColor = getSignalColor(nsGreen, signalState);
        gc.setFill(southColor);
        gc.fillOval(centerX - SIGNAL_SIZE / 2, centerY + offset, SIGNAL_SIZE, SIGNAL_SIZE);

        // East signal
        Color eastColor = getSignalColor(ewGreen, signalState);
        gc.setFill(eastColor);
        gc.fillOval(centerX + offset, centerY - SIGNAL_SIZE / 2, SIGNAL_SIZE, SIGNAL_SIZE);

        // West signal
        Color westColor = getSignalColor(ewGreen, signalState);
        gc.setFill(westColor);
        gc.fillOval(centerX - offset - SIGNAL_SIZE, centerY - SIGNAL_SIZE / 2, SIGNAL_SIZE, SIGNAL_SIZE);
    }

    private Color getSignalColor(boolean hasGreen, String signalState) {
        if (!hasGreen) {
            return Color.RED; // This direction always red during this phase
        }

        // This direction can be green/yellow during this phase
        switch (signalState) {
            case "green":
                return Color.GREEN;
            case "yellow":
                return Color.YELLOW;
            case "red":
            default:
                return Color.RED;
        }
    }

    private void drawVehicles(GraphicsContext gc, double centerX, double centerY) {
        gc.setFill(Color.BLUE);

        var queues = simulator.getQueues();

        // Draw vehicles in each direction
        drawDirectionVehicles(gc, queues.get(Direction.NORTH), centerX, centerY, Direction.NORTH);
        drawDirectionVehicles(gc, queues.get(Direction.SOUTH), centerX, centerY, Direction.SOUTH);
        drawDirectionVehicles(gc, queues.get(Direction.EAST), centerX, centerY, Direction.EAST);
        drawDirectionVehicles(gc, queues.get(Direction.WEST), centerX, centerY, Direction.WEST);
    }

    private void drawDirectionVehicles(GraphicsContext gc, Queue<?> queue, double centerX, double centerY, Direction direction) {
        int count = Math.min(queue.size(), 10); // Show up to 10 vehicles

        for (int i = 0; i < count; i++) {
            double x, y;
            double spacing = CAR_SIZE + 5;

            switch (direction) {
                case NORTH:
                    x = centerX - CAR_SIZE / 2 - LANE_WIDTH / 2;
                    y = centerY - LANE_WIDTH - spacing * (i + 1);
                    break;
                case SOUTH:
                    x = centerX - CAR_SIZE / 2 + LANE_WIDTH / 2;
                    y = centerY + LANE_WIDTH + spacing * (i + 1);
                    break;
                case EAST:
                    x = centerX + LANE_WIDTH + spacing * (i + 1);
                    y = centerY - CAR_SIZE / 2 + LANE_WIDTH / 2;
                    break;
                case WEST:
                default:
                    x = centerX - LANE_WIDTH - spacing * (i + 1);
                    y = centerY - CAR_SIZE / 2 - LANE_WIDTH / 2;
                    break;
            }

            // Draw vehicle rectangle
            gc.fillRoundRect(x, y, CAR_SIZE, CAR_SIZE * 0.6, 5, 5);
        }
    }

    private void drawLabels(GraphicsContext gc, double centerX, double centerY) {
        gc.setFill(Color.WHITE);
        gc.setFont(javafx.scene.text.Font.font(14));

        var queues = simulator.getQueues();

        // Queue length labels
        gc.fillText("N: " + queues.get(Direction.NORTH).size(), centerX - 20, 20);
        gc.fillText("S: " + queues.get(Direction.SOUTH).size(), centerX - 20, canvas.getHeight() - 10);
        gc.fillText("E: " + queues.get(Direction.EAST).size(), canvas.getWidth() - 40, centerY - 10);
        gc.fillText("W: " + queues.get(Direction.WEST).size(), 10, centerY - 10);

        // Signal state label
        String stateText = "Signal: " + simulator.getCurrentSignalState().toUpperCase();
        gc.fillText(stateText, 10, 20);

        // Simulation time and speed
        String timeText = String.format("Time: %.1fs", simulator.getSimTime());
        gc.fillText(timeText, canvas.getWidth() - 100, 20);

        // Speed indicator
        gc.setFill(Color.rgb(69, 183, 209)); // Cyan color
        String speedText = String.format("Speed: %.1fx", currentSpeed);
        gc.fillText(speedText, canvas.getWidth() - 100, 40);
    }
}
