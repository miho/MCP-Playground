package com.devicesim.ui;

import com.devicesim.engine.DeviceSimulator;
import com.devicesim.model.DeviceState;
import com.devicesim.model.Location;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

import java.util.List;

/**
 * 2D visualization canvas for the device simulator.
 * Renders the device head, target locations, and movement paths.
 *
 * @since 1.0.0
 */
public class DeviceCanvas extends Canvas {

    private static final double DEVICE_RADIUS = 10.0;
    private static final double TARGET_RADIUS = 8.0;
    private static final double GRID_SPACING = 50.0;
    private static final double MARGIN = 40.0;

    private final DeviceSimulator simulator;
    private double scale = 1.0;
    private double offsetX = 0.0;
    private double offsetY = 0.0;
    private double pulsePhase = 0.0;

    public DeviceCanvas(DeviceSimulator simulator, double width, double height) {
        super(width, height);
        this.simulator = simulator;

        // Dark background
        setStyle("-fx-background-color: #1a1a1a;");
    }

    /**
     * Render the current simulation state.
     */
    public void render() {
        GraphicsContext gc = getGraphicsContext2D();
        double width = getWidth();
        double height = getHeight();

        // Clear canvas
        gc.setFill(Color.rgb(26, 26, 26));
        gc.fillRect(0, 0, width, height);

        // Update pulse animation
        pulsePhase += 0.05;
        if (pulsePhase > Math.PI * 2) {
            pulsePhase = 0;
        }

        // Get current state
        DeviceState state = simulator.getState();
        List<Location> locations = simulator.getAllLocations();
        Location currentTarget = simulator.getCurrentTarget();

        // Auto-scale to fit all locations
        if (!locations.isEmpty()) {
            autoScale(locations, width, height);
        } else {
            // Default scale centered on origin
            scale = 4.0;
            offsetX = width / 2.0;
            offsetY = height / 2.0;
        }

        // Draw coordinate grid
        drawGrid(gc, width, height);

        // Draw path lines
        drawPaths(gc, locations, state);

        // Draw all target locations
        for (Location loc : locations) {
            boolean isCurrent = loc.equals(currentTarget);
            drawLocation(gc, loc, isCurrent);
        }

        // Draw device head
        drawDevice(gc, state);

        // Draw info overlay
        drawInfoOverlay(gc, state, currentTarget, width, height);
    }

    /**
     * Auto-scale view to fit all locations.
     */
    private void autoScale(List<Location> locations, double width, double height) {
        if (locations.isEmpty()) {
            return;
        }

        // Find bounding box
        double minX = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE;
        double minY = Double.MAX_VALUE;
        double maxY = Double.MIN_VALUE;

        for (Location loc : locations) {
            minX = Math.min(minX, loc.getX());
            maxX = Math.max(maxX, loc.getX());
            minY = Math.min(minY, loc.getY());
            maxY = Math.max(maxY, loc.getY());
        }

        // Include device position
        DeviceState state = simulator.getState();
        minX = Math.min(minX, state.getX());
        maxX = Math.max(maxX, state.getX());
        minY = Math.min(minY, state.getY());
        maxY = Math.max(maxY, state.getY());

        // Calculate scale to fit in canvas with margin
        double rangeX = maxX - minX;
        double rangeY = maxY - minY;

        if (rangeX == 0 && rangeY == 0) {
            scale = 4.0;
            offsetX = width / 2.0 - minX * scale;
            offsetY = height / 2.0 - minY * scale;
            return;
        }

        double scaleX = (width - 2 * MARGIN) / Math.max(rangeX, 1.0);
        double scaleY = (height - 2 * MARGIN) / Math.max(rangeY, 1.0);
        scale = Math.min(scaleX, scaleY);

        // Center the view
        double centerX = (minX + maxX) / 2.0;
        double centerY = (minY + maxY) / 2.0;
        offsetX = width / 2.0 - centerX * scale;
        offsetY = height / 2.0 - centerY * scale;
    }

    /**
     * Draw coordinate grid.
     */
    private void drawGrid(GraphicsContext gc, double width, double height) {
        gc.setStroke(Color.rgb(40, 40, 40));
        gc.setLineWidth(1.0);

        // Vertical lines
        for (double x = 0; x < width; x += GRID_SPACING) {
            gc.strokeLine(x, 0, x, height);
        }

        // Horizontal lines
        for (double y = 0; y < height; y += GRID_SPACING) {
            gc.strokeLine(0, y, width, y);
        }

        // Draw axes
        gc.setStroke(Color.rgb(60, 60, 60));
        gc.setLineWidth(2.0);

        // X-axis
        double originY = transformY(0);
        if (originY >= 0 && originY <= height) {
            gc.strokeLine(0, originY, width, originY);
        }

        // Y-axis
        double originX = transformX(0);
        if (originX >= 0 && originX <= width) {
            gc.strokeLine(originX, 0, originX, height);
        }
    }

    /**
     * Draw path lines connecting locations.
     */
    private void drawPaths(GraphicsContext gc, List<Location> locations, DeviceState state) {
        if (locations.isEmpty()) {
            return;
        }

        gc.setStroke(Color.rgb(100, 100, 100, 0.5));
        gc.setLineWidth(2.0);

        // Draw line from device to current target
        Location currentTarget = simulator.getCurrentTarget();
        if (currentTarget != null) {
            gc.setStroke(Color.rgb(69, 183, 209, 0.7));
            gc.setLineWidth(2.5);
            drawLine(gc, state.getX(), state.getY(), currentTarget.getX(), currentTarget.getY());
        }

        // Draw lines between consecutive locations
        gc.setStroke(Color.rgb(100, 100, 100, 0.3));
        gc.setLineWidth(1.5);
        gc.setLineDashes(5, 5);

        for (int i = 0; i < locations.size() - 1; i++) {
            Location loc1 = locations.get(i);
            Location loc2 = locations.get(i + 1);
            drawLine(gc, loc1.getX(), loc1.getY(), loc2.getX(), loc2.getY());
        }

        gc.setLineDashes(null);
    }

    /**
     * Draw a single location.
     */
    private void drawLocation(GraphicsContext gc, Location loc, boolean isCurrent) {
        double x = transformX(loc.getX());
        double y = transformY(loc.getY());

        if (loc.isVisited()) {
            // Visited: filled green circle with checkmark
            gc.setFill(Color.rgb(39, 174, 96, 0.8));
            gc.fillOval(x - TARGET_RADIUS, y - TARGET_RADIUS, TARGET_RADIUS * 2, TARGET_RADIUS * 2);

            gc.setStroke(Color.rgb(46, 204, 113));
            gc.setLineWidth(2.0);
            gc.strokeOval(x - TARGET_RADIUS, y - TARGET_RADIUS, TARGET_RADIUS * 2, TARGET_RADIUS * 2);

            // Draw checkmark
            gc.setStroke(Color.WHITE);
            gc.setLineWidth(2.0);
            gc.strokeLine(x - 3, y, x - 1, y + 3);
            gc.strokeLine(x - 1, y + 3, x + 3, y - 3);

        } else if (isCurrent) {
            // Current target: pulsing animation
            double pulseRadius = TARGET_RADIUS + Math.sin(pulsePhase) * 3;

            gc.setFill(Color.rgb(78, 205, 196, 0.3));
            gc.fillOval(x - pulseRadius, y - pulseRadius, pulseRadius * 2, pulseRadius * 2);

            gc.setFill(Color.rgb(78, 205, 196, 0.9));
            gc.fillOval(x - TARGET_RADIUS, y - TARGET_RADIUS, TARGET_RADIUS * 2, TARGET_RADIUS * 2);

            gc.setStroke(Color.rgb(78, 205, 196));
            gc.setLineWidth(2.5);
            gc.strokeOval(x - TARGET_RADIUS, y - TARGET_RADIUS, TARGET_RADIUS * 2, TARGET_RADIUS * 2);

        } else {
            // Pending: hollow circle
            gc.setStroke(Color.rgb(160, 160, 160));
            gc.setLineWidth(2.0);
            gc.strokeOval(x - TARGET_RADIUS, y - TARGET_RADIUS, TARGET_RADIUS * 2, TARGET_RADIUS * 2);
        }

        // Draw label (show row number from ID)
        gc.setFill(Color.rgb(200, 200, 200));
        gc.setFont(Font.font("Arial", 10));
        gc.setTextAlign(TextAlignment.CENTER);

        String label = extractLabelFromId(loc.getId());
        gc.fillText(label, x, y - TARGET_RADIUS - 5);
    }

    /**
     * Draw the device head.
     */
    private void drawDevice(GraphicsContext gc, DeviceState state) {
        double x = transformX(state.getX());
        double y = transformY(state.getY());

        // Draw device circle
        gc.setFill(Color.rgb(69, 183, 209));
        gc.fillOval(x - DEVICE_RADIUS, y - DEVICE_RADIUS, DEVICE_RADIUS * 2, DEVICE_RADIUS * 2);

        gc.setStroke(Color.rgb(69, 183, 209, 1.0));
        gc.setLineWidth(3.0);
        gc.strokeOval(x - DEVICE_RADIUS, y - DEVICE_RADIUS, DEVICE_RADIUS * 2, DEVICE_RADIUS * 2);

        // Draw direction indicator (arrow pointing to target)
        if (state.isMoving()) {
            double dx = state.getTargetX() - state.getX();
            double dy = state.getTargetY() - state.getY();
            double distance = Math.sqrt(dx * dx + dy * dy);

            if (distance > 0.1) {
                double dirX = dx / distance;
                double dirY = dy / distance;

                // Arrow from center to edge of circle
                double arrowX = x + dirX * DEVICE_RADIUS;
                double arrowY = y + dirY * DEVICE_RADIUS;

                gc.setStroke(Color.WHITE);
                gc.setLineWidth(2.5);
                gc.strokeLine(x, y, arrowX, arrowY);

                // Arrowhead
                double arrowSize = 5;
                double angle = Math.atan2(dirY, dirX);
                double x1 = arrowX - arrowSize * Math.cos(angle - Math.PI / 6);
                double y1 = arrowY - arrowSize * Math.sin(angle - Math.PI / 6);
                double x2 = arrowX - arrowSize * Math.cos(angle + Math.PI / 6);
                double y2 = arrowY - arrowSize * Math.sin(angle + Math.PI / 6);

                gc.strokeLine(arrowX, arrowY, x1, y1);
                gc.strokeLine(arrowX, arrowY, x2, y2);
            }
        }

        // Draw position label
        gc.setFill(Color.rgb(69, 183, 209));
        gc.setFont(Font.font("Arial", 10));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText(String.format("(%.1f, %.1f)", state.getX(), state.getY()),
                x, y + DEVICE_RADIUS + 15);
    }

    /**
     * Draw info overlay.
     */
    private void drawInfoOverlay(GraphicsContext gc, DeviceState state, Location currentTarget,
                                  double width, double height) {
        gc.setFill(Color.rgb(40, 40, 40, 0.9));
        gc.fillRect(10, 10, 180, 80);

        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Arial", 11));
        gc.setTextAlign(TextAlignment.LEFT);

        double y = 25;
        gc.fillText(String.format("Position: (%.2f, %.2f)", state.getX(), state.getY()), 20, y);
        y += 15;
        gc.fillText(String.format("Speed: %.2f / %.2f", state.getSpeed(), state.getMaxSpeed()), 20, y);
        y += 15;
        gc.fillText(String.format("Accel: %.2f u/s²", state.getAcceleration()), 20, y);
        y += 15;

        if (currentTarget != null) {
            gc.fillText(String.format("Target: %s", extractLabelFromId(currentTarget.getId())), 20, y);
        } else {
            gc.fillText("Target: None", 20, y);
        }
    }

    /**
     * Transform world X coordinate to canvas X.
     */
    private double transformX(double worldX) {
        return worldX * scale + offsetX;
    }

    /**
     * Transform world Y coordinate to canvas Y.
     */
    private double transformY(double worldY) {
        return worldY * scale + offsetY;
    }

    /**
     * Draw a line in world coordinates.
     */
    private void drawLine(GraphicsContext gc, double x1, double y1, double x2, double y2) {
        gc.strokeLine(transformX(x1), transformY(y1), transformX(x2), transformY(y2));
    }

    /**
     * Extract a simple label from location ID.
     */
    private String extractLabelFromId(String id) {
        if (id == null) {
            return "?";
        }

        // Extract row number from ID like "loc_2_12.34_56.78" -> "2"
        if (id.startsWith("loc_")) {
            String[] parts = id.split("_");
            if (parts.length > 1) {
                return parts[1];
            }
        }

        return id.length() > 8 ? id.substring(0, 8) : id;
    }
}
