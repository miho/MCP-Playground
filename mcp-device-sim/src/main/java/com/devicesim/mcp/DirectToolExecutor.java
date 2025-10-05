package com.devicesim.mcp;

import com.devicesim.data.CsvDataReader;
import com.devicesim.data.CsvStateManager;
import com.devicesim.engine.DeviceSimulator;
import com.devicesim.model.DeviceState;
import com.devicesim.model.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Execute MCP tools directly by calling DeviceSimulator and CsvDataReader (no HTTP/RPC overhead).
 * This is used by the JavaFX UI for internal tool execution.
 * External MCP clients can still use the embedded MCP server for remote access.
 *
 * @since 1.0.0
 */
public class DirectToolExecutor {

    private static final Logger logger = LoggerFactory.getLogger(DirectToolExecutor.class);

    private final DeviceSimulator simulator;
    private final CsvDataReader csvReader;
    private final ExecutorService executor;
    private volatile boolean cancelled = false;

    /**
     * Constructs a new DirectToolExecutor.
     *
     * @param simulator the device simulator instance
     * @param csvReader the CSV data reader instance
     */
    public DirectToolExecutor(DeviceSimulator simulator, CsvDataReader csvReader) {
        this.simulator = simulator;
        this.csvReader = csvReader;
        this.executor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "DirectToolExecutor");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Execute a single tool directly.
     *
     * @param toolName the name of the tool to execute
     * @param params the tool parameters
     * @return future containing the tool result
     */
    public CompletableFuture<ToolResult> executeTool(String toolName, Map<String, Object> params) {
        return CompletableFuture.supplyAsync(() -> {
            if (cancelled) {
                logger.debug("Tool execution cancelled before start: {}", toolName);
                return ToolResult.cancelled();
            }

            try {
                logger.info("Executing tool: {} with params: {}", toolName, params);

                Object result = executeToolInternal(toolName, params);

                if (cancelled) {
                    logger.info("Tool execution cancelled after completion: {}", toolName);
                    return ToolResult.cancelled();
                }

                String resultMessage = result != null ? result.toString() : "Success";
                logger.info("Tool completed successfully: {} - {}", toolName, resultMessage);
                return ToolResult.success(resultMessage);

            } catch (Exception e) {
                logger.error("Tool execution failed: " + toolName, e);
                String errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                return ToolResult.error(errorMessage);
            }
        }, executor);
    }

    /**
     * Cancel ongoing execution.
     */
    public void cancel() {
        cancelled = true;
    }

    /**
     * Shutdown the executor service.
     */
    public void shutdown() {
        executor.shutdown();
    }

    /**
     * Route tool execution to the appropriate method.
     */
    private Object executeToolInternal(String toolName, Map<String, Object> params) throws Exception {
        return switch (toolName) {
            case "csv_get_headers" -> executeCsvGetHeaders(params);
            case "csv_query_locations" -> executeCsvQueryLocations(params);
            case "device_get_state" -> executeDeviceGetState(params);
            case "device_set_targets" -> executeDeviceSetTargets(params);
            case "device_set_speed" -> executeDeviceSetSpeed(params);
            case "device_set_acceleration" -> executeDeviceSetAcceleration(params);
            case "device_mark_visited" -> executeDeviceMarkVisited(params);
            case "device_get_all_locations" -> executeDeviceGetAllLocations(params);
            default -> throw new IllegalArgumentException("Unknown tool: " + toolName);
        };
    }

    // ==================== TOOL IMPLEMENTATIONS ====================

    private Object executeCsvGetHeaders(Map<String, Object> params) throws Exception {
        String filePath = getStringParam(params, "filePath");
        List<String> headers = csvReader.getHeaders(filePath);

        // Notify CSV state manager
        CsvStateManager.getInstance().notifyHeadersRead(filePath, headers);

        return String.format("CSV Headers (%d columns):\n%s",
                headers.size(),
                String.join(", ", headers));
    }

    private Object executeCsvQueryLocations(Map<String, Object> params) throws Exception {
        String filePath = getStringParam(params, "filePath");
        String xColumn = getStringParam(params, "xColumn");
        String yColumn = getStringParam(params, "yColumn");

        // Parse optional filters
        Map<String, CsvDataReader.FilterCriteria> filters = new HashMap<>();
        @SuppressWarnings("unchecked")
        Map<String, Object> filtersObj = (Map<String, Object>) params.get("filters");

        if (filtersObj != null) {
            for (Map.Entry<String, Object> entry : filtersObj.entrySet()) {
                String columnName = entry.getKey();
                @SuppressWarnings("unchecked")
                Map<String, Object> criteria = (Map<String, Object>) entry.getValue();

                if (criteria.containsKey("equals")) {
                    filters.put(columnName, new CsvDataReader.FilterCriteria(
                            columnName, criteria.get("equals")));
                } else {
                    Double min = criteria.containsKey("min") ?
                            getDoubleValue(criteria.get("min")) : null;
                    Double max = criteria.containsKey("max") ?
                            getDoubleValue(criteria.get("max")) : null;
                    filters.put(columnName, new CsvDataReader.FilterCriteria(
                            columnName, min, max));
                }
            }
        }

        List<Location> locations = csvReader.readLocations(filePath, xColumn, yColumn, filters);

        // Notify CSV state manager
        CsvStateManager.getInstance().notifyLocationsQueried(filePath, xColumn, yColumn, locations.size());

        // Format as JSON array
        String jsonArray = locations.stream()
                .map(loc -> String.format("{\"id\": \"%s\", \"x\": %.2f, \"y\": %.2f, \"properties\": %s}",
                        loc.getId(), loc.getX(), loc.getY(), formatProperties(loc.getProperties())))
                .collect(Collectors.joining(",\n  "));

        return String.format("Found %d locations:\n[\n  %s\n]", locations.size(), jsonArray);
    }

    private Object executeDeviceGetState(Map<String, Object> params) {
        DeviceState state = simulator.getState();
        Location currentTarget = simulator.getCurrentTarget();

        String targetInfo = currentTarget != null
                ? String.format("Target: %s (%.2f, %.2f)",
                        currentTarget.getId(), currentTarget.getX(), currentTarget.getY())
                : "No target";

        return String.format(
                "Device State:\n" +
                "Position: (%.2f, %.2f)\n" +
                "%s\n" +
                "Speed: %.2f units/s (max: %.2f)\n" +
                "Acceleration: %.2f units/s²\n" +
                "Moving: %s\n" +
                "Target Index: %d",
                state.getX(), state.getY(),
                targetInfo,
                state.getSpeed(), state.getMaxSpeed(),
                state.getAcceleration(),
                state.isMoving() ? "Yes" : "No",
                simulator.getCurrentTargetIndex());
    }

    private Object executeDeviceSetTargets(Map<String, Object> params) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> locationsData = (List<Map<String, Object>>) params.get("locations");

        if (locationsData == null || locationsData.isEmpty()) {
            throw new IllegalArgumentException("Locations array cannot be null or empty");
        }

        List<Location> locations = new ArrayList<>();
        for (Map<String, Object> locData : locationsData) {
            String id = getStringParam(locData, "id");
            double x = getDoubleParam(locData, "x");
            double y = getDoubleParam(locData, "y");

            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) locData.get("properties");

            Location location = properties != null
                    ? new Location(id, x, y, properties)
                    : new Location(id, x, y);
            locations.add(location);
        }

        simulator.setTargetLocations(locations);
        simulator.setAutoAdvance(true); // Enable auto-advance for continuous movement

        return String.format("Set %d target locations successfully (auto-advance enabled).\nFirst target: %s",
                locations.size(), locations.get(0).getId());
    }

    private Object executeDeviceSetSpeed(Map<String, Object> params) {
        double maxSpeed = getDoubleParam(params, "maxSpeed");
        simulator.setSpeed(maxSpeed);
        return String.format("Max speed set to %.2f units/s", maxSpeed);
    }

    private Object executeDeviceSetAcceleration(Map<String, Object> params) {
        double acceleration = getDoubleParam(params, "acceleration");
        simulator.setAcceleration(acceleration);
        return String.format("Acceleration set to %.2f units/s²", acceleration);
    }

    private Object executeDeviceMarkVisited(Map<String, Object> params) {
        Location currentTarget = simulator.getCurrentTarget();
        if (currentTarget == null) {
            return "No current target to mark as visited";
        }

        String targetId = currentTarget.getId();
        simulator.markCurrentAsVisited();

        Location newTarget = simulator.getCurrentTarget();
        String newTargetInfo = newTarget != null
                ? String.format("Next target: %s", newTarget.getId())
                : "No more targets";

        return String.format("Marked %s as visited. %s", targetId, newTargetInfo);
    }

    private Object executeDeviceGetAllLocations(Map<String, Object> params) {
        List<Location> locations = simulator.getAllLocations();

        if (locations.isEmpty()) {
            return "No locations set";
        }

        String jsonArray = locations.stream()
                .map(loc -> String.format(
                        "{\"id\": \"%s\", \"x\": %.2f, \"y\": %.2f, \"visited\": %s, \"properties\": %s}",
                        loc.getId(), loc.getX(), loc.getY(), loc.isVisited(),
                        formatProperties(loc.getProperties())))
                .collect(Collectors.joining(",\n  "));

        long visitedCount = locations.stream().filter(Location::isVisited).count();

        return String.format("All Locations (%d total, %d visited):\n[\n  %s\n]",
                locations.size(), visitedCount, jsonArray);
    }

    // ==================== HELPER METHODS ====================

    private String formatProperties(Map<String, Object> properties) {
        if (properties == null || properties.isEmpty()) {
            return "{}";
        }

        String propsStr = properties.entrySet().stream()
                .map(e -> String.format("\"%s\": %s", e.getKey(), formatValue(e.getValue())))
                .collect(Collectors.joining(", "));

        return "{" + propsStr + "}";
    }

    private String formatValue(Object value) {
        if (value instanceof String) {
            return "\"" + value + "\"";
        } else if (value instanceof Number) {
            return value.toString();
        } else {
            return "\"" + value + "\"";
        }
    }

    private String getStringParam(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Required parameter missing: " + key);
        }
        return value.toString();
    }

    private double getDoubleParam(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Required parameter missing: " + key);
        }
        return getDoubleValue(value);
    }

    private double getDoubleValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number format: " + value, e);
        }
    }

    // ==================== RESULT CLASS ====================

    /**
     * Result of executing a single tool.
     */
    public static class ToolResult {
        private final boolean success;
        private final boolean cancelled;
        private final String message;

        private ToolResult(boolean success, boolean cancelled, String message) {
            this.success = success;
            this.cancelled = cancelled;
            this.message = message;
        }

        public static ToolResult success(String message) {
            return new ToolResult(true, false, message);
        }

        public static ToolResult error(String message) {
            return new ToolResult(false, false, message);
        }

        public static ToolResult cancelled() {
            return new ToolResult(false, true, "Cancelled");
        }

        public boolean isSuccess() {
            return success;
        }

        public boolean isCancelled() {
            return cancelled;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public String toString() {
            return message;
        }
    }
}
