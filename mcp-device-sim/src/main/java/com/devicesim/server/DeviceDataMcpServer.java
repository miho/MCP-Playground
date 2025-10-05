package com.devicesim.server;

import com.devicesim.data.CsvDataReader;
import com.devicesim.engine.DeviceSimulator;
import com.devicesim.model.DeviceState;
import com.devicesim.model.Location;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpAsyncServer;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

/**
 * MCP Server for device simulation and CSV data operations.
 * Exposes tools for querying CSV data and controlling device movement.
 *
 * @since 1.0.0
 */
public class DeviceDataMcpServer {

    private static DeviceSimulator simulator;
    private static CsvDataReader csvReader;
    private static String currentCsvPath = "";

    public static void main(String[] args) {
        try {
            simulator = new DeviceSimulator();
            csvReader = new CsvDataReader();

            startStdioServer();
        } catch (Exception e) {
            System.err.println("Server error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Create an async stdio server with a shared simulator.
     * Used by UI when running in stdio mode.
     *
     * @param sharedSimulator the shared device simulator
     * @param csvPath the current CSV file path
     * @return the async MCP server
     */
    public static McpAsyncServer createStdioServer(DeviceSimulator sharedSimulator, String csvPath) {
        // Use the shared simulator instead of creating a new one
        simulator = sharedSimulator;
        csvReader = new CsvDataReader();
        currentCsvPath = csvPath != null ? csvPath : "";

        StdioServerTransportProvider transportProvider =
                new StdioServerTransportProvider(McpJsonMapper.createDefault());

        return McpServer.async(transportProvider)
                .serverInfo("device-simulator-server", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .tools(
                        createCsvGetHeadersTool(),
                        createCsvQueryLocationsTool(),
                        createDeviceGetStateTool(),
                        createDeviceSetTargetsTool(),
                        createDeviceSetSpeedTool(),
                        createDeviceSetAccelerationTool(),
                        createDeviceMarkVisitedTool(),
                        createDeviceGetAllLocationsTool()
                )
                .build();
    }

    /**
     * Update the current CSV file path.
     * Used by UI to keep MCP server in sync with file changes.
     *
     * @param path the new CSV file path
     */
    public static void updateCurrentCsvPath(String path) {
        currentCsvPath = path != null ? path : "";
    }

    private static void startStdioServer() throws InterruptedException {
        McpAsyncServer server = createStdioServer(simulator, currentCsvPath);

        System.err.println("Device Simulator MCP Server started (stdio mode)");
        System.err.println("Version: 1.0.0");
        System.err.println("Ready for connections...");

        CountDownLatch latch = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.err.println("Shutting down server...");
            server.close();
            latch.countDown();
        }));

        latch.await();
    }

    // ==================== ASYNC TOOL SPECIFICATIONS ====================

    private static McpServerFeatures.AsyncToolSpecification createCsvGetHeadersTool() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "filePath": {
                  "type": "string",
                  "description": "Path to the CSV file"
                }
              },
              "required": ["filePath"]
            }
            """;

        return new McpServerFeatures.AsyncToolSpecification.Builder()
                .tool(McpSchema.Tool.builder()
                        .name("csv_get_headers")
                        .description("Get column headers from a CSV file")
                        .inputSchema(McpJsonMapper.createDefault(), schema)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        Map<String, Object> args = request.arguments();
                        String filePath = getStringArg(args, "filePath");

                        List<String> headers = csvReader.getHeaders(filePath);
                        String headersJson = headers.stream()
                                .map(h -> "\"" + h + "\"")
                                .collect(Collectors.joining(", "));

                        String message = String.format("[%s]", headersJson);

                        return Mono.just(new McpSchema.CallToolResult.Builder()
                                .content(List.of(new McpSchema.TextContent(message)))
                                .isError(false)
                                .build());
                    } catch (Exception e) {
                        return Mono.just(new McpSchema.CallToolResult.Builder()
                                .content(List.of(new McpSchema.TextContent("Error: " + e.getMessage())))
                                .isError(true)
                                .build());
                    }
                })
                .build();
    }

    private static McpServerFeatures.AsyncToolSpecification createCsvQueryLocationsTool() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "filePath": {"type": "string", "description": "Path to the CSV file"},
                "xColumn": {"type": "string", "description": "Name of the x-coordinate column"},
                "yColumn": {"type": "string", "description": "Name of the y-coordinate column"},
                "filters": {
                  "type": "object",
                  "description": "Optional filters as {columnName: {min, max, equals}}",
                  "additionalProperties": {
                    "type": "object",
                    "properties": {
                      "min": {"type": "number"},
                      "max": {"type": "number"},
                      "equals": {}
                    }
                  }
                }
              },
              "required": ["filePath", "xColumn", "yColumn"]
            }
            """;

        return new McpServerFeatures.AsyncToolSpecification.Builder()
                .tool(McpSchema.Tool.builder()
                        .name("csv_query_locations")
                        .description("Query and filter location data from CSV file")
                        .inputSchema(McpJsonMapper.createDefault(), schema)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        Map<String, Object> args = request.arguments();
                        String filePath = getStringArg(args, "filePath");
                        String xColumn = getStringArg(args, "xColumn");
                        String yColumn = getStringArg(args, "yColumn");

                        // Parse optional filters
                        Map<String, CsvDataReader.FilterCriteria> filters = new HashMap<>();
                        @SuppressWarnings("unchecked")
                        Map<String, Object> filtersObj = (Map<String, Object>) args.get("filters");

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
                                            getDoubleArg(criteria, "min") : null;
                                    Double max = criteria.containsKey("max") ?
                                            getDoubleArg(criteria, "max") : null;
                                    filters.put(columnName, new CsvDataReader.FilterCriteria(
                                            columnName, min, max));
                                }
                            }
                        }

                        List<Location> locations = csvReader.readLocations(filePath, xColumn, yColumn, filters);

                        // Format as JSON array
                        String jsonArray = locations.stream()
                                .map(loc -> String.format(
                                        "{\\\"id\\\": \\\"%s\\\", \\\"x\\\": %.2f, \\\"y\\\": %.2f, \\\"properties\\\": %s}",
                                        loc.getId(), loc.getX(), loc.getY(),
                                        formatPropertiesJson(loc.getProperties())))
                                .collect(Collectors.joining(", "));

                        String message = String.format("[%s]", jsonArray);

                        return Mono.just(new McpSchema.CallToolResult.Builder()
                                .content(List.of(new McpSchema.TextContent(message)))
                                .isError(false)
                                .build());
                    } catch (Exception e) {
                        return Mono.just(new McpSchema.CallToolResult.Builder()
                                .content(List.of(new McpSchema.TextContent("Error: " + e.getMessage())))
                                .isError(true)
                                .build());
                    }
                })
                .build();
    }

    private static McpServerFeatures.AsyncToolSpecification createDeviceGetStateTool() {
        String schema = """
            {
              "type": "object",
              "properties": {}
            }
            """;

        return new McpServerFeatures.AsyncToolSpecification.Builder()
                .tool(McpSchema.Tool.builder()
                        .name("device_get_state")
                        .description("Get current device state (position, target, speed, moving status)")
                        .inputSchema(McpJsonMapper.createDefault(), schema)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        DeviceState state = simulator.getState();
                        Location currentTarget = simulator.getCurrentTarget();

                        String targetInfo = currentTarget != null
                                ? String.format("\\\"target\\\": {\\\"id\\\": \\\"%s\\\", \\\"x\\\": %.2f, \\\"y\\\": %.2f}",
                                        currentTarget.getId(), currentTarget.getX(), currentTarget.getY())
                                : "\\\"target\\\": null";

                        String message = String.format(
                                "{\\\"position\\\": {\\\"x\\\": %.2f, \\\"y\\\": %.2f}, " +
                                "%s, " +
                                "\\\"speed\\\": %.2f, \\\"maxSpeed\\\": %.2f, " +
                                "\\\"acceleration\\\": %.2f, \\\"moving\\\": %s, " +
                                "\\\"targetIndex\\\": %d}",
                                state.getX(), state.getY(),
                                targetInfo,
                                state.getSpeed(), state.getMaxSpeed(),
                                state.getAcceleration(), state.isMoving(),
                                simulator.getCurrentTargetIndex());

                        return Mono.just(new McpSchema.CallToolResult.Builder()
                                .content(List.of(new McpSchema.TextContent(message)))
                                .isError(false)
                                .build());
                    } catch (Exception e) {
                        return Mono.just(new McpSchema.CallToolResult.Builder()
                                .content(List.of(new McpSchema.TextContent("Error: " + e.getMessage())))
                                .isError(true)
                                .build());
                    }
                })
                .build();
    }

    private static McpServerFeatures.AsyncToolSpecification createDeviceSetTargetsTool() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "locations": {
                  "type": "array",
                  "description": "Array of target locations",
                  "items": {
                    "type": "object",
                    "properties": {
                      "id": {"type": "string"},
                      "x": {"type": "number"},
                      "y": {"type": "number"},
                      "properties": {"type": "object"}
                    },
                    "required": ["id", "x", "y"]
                  }
                }
              },
              "required": ["locations"]
            }
            """;

        return new McpServerFeatures.AsyncToolSpecification.Builder()
                .tool(McpSchema.Tool.builder()
                        .name("device_set_targets")
                        .description("Set target locations for the device to visit")
                        .inputSchema(McpJsonMapper.createDefault(), schema)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        Map<String, Object> args = request.arguments();
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> locationsData =
                                (List<Map<String, Object>>) args.get("locations");

                        if (locationsData == null || locationsData.isEmpty()) {
                            throw new IllegalArgumentException("Locations array cannot be null or empty");
                        }

                        List<Location> locations = new ArrayList<>();
                        for (Map<String, Object> locData : locationsData) {
                            String id = getStringArg(locData, "id");
                            double x = getDoubleArg(locData, "x");
                            double y = getDoubleArg(locData, "y");

                            @SuppressWarnings("unchecked")
                            Map<String, Object> properties = (Map<String, Object>) locData.get("properties");

                            Location location = properties != null
                                    ? new Location(id, x, y, properties)
                                    : new Location(id, x, y);
                            locations.add(location);
                        }

                        simulator.setTargetLocations(locations);

                        String message = String.format(
                                "Set %d target locations successfully. First target: %s",
                                locations.size(), locations.get(0).getId());

                        return Mono.just(new McpSchema.CallToolResult.Builder()
                                .content(List.of(new McpSchema.TextContent(message)))
                                .isError(false)
                                .build());
                    } catch (Exception e) {
                        return Mono.just(new McpSchema.CallToolResult.Builder()
                                .content(List.of(new McpSchema.TextContent("Error: " + e.getMessage())))
                                .isError(true)
                                .build());
                    }
                })
                .build();
    }

    private static McpServerFeatures.AsyncToolSpecification createDeviceSetSpeedTool() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "maxSpeed": {
                  "type": "number",
                  "description": "Maximum speed in units per second",
                  "minimum": 0.1
                }
              },
              "required": ["maxSpeed"]
            }
            """;

        return new McpServerFeatures.AsyncToolSpecification.Builder()
                .tool(McpSchema.Tool.builder()
                        .name("device_set_speed")
                        .description("Set maximum speed for the device")
                        .inputSchema(McpJsonMapper.createDefault(), schema)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        Map<String, Object> args = request.arguments();
                        double maxSpeed = getDoubleArg(args, "maxSpeed");

                        simulator.setSpeed(maxSpeed);

                        String message = String.format("Max speed set to %.2f units/s", maxSpeed);

                        return Mono.just(new McpSchema.CallToolResult.Builder()
                                .content(List.of(new McpSchema.TextContent(message)))
                                .isError(false)
                                .build());
                    } catch (Exception e) {
                        return Mono.just(new McpSchema.CallToolResult.Builder()
                                .content(List.of(new McpSchema.TextContent("Error: " + e.getMessage())))
                                .isError(true)
                                .build());
                    }
                })
                .build();
    }

    private static McpServerFeatures.AsyncToolSpecification createDeviceSetAccelerationTool() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "acceleration": {
                  "type": "number",
                  "description": "Acceleration in units per second squared",
                  "minimum": 0.1
                }
              },
              "required": ["acceleration"]
            }
            """;

        return new McpServerFeatures.AsyncToolSpecification.Builder()
                .tool(McpSchema.Tool.builder()
                        .name("device_set_acceleration")
                        .description("Set acceleration for the device")
                        .inputSchema(McpJsonMapper.createDefault(), schema)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        Map<String, Object> args = request.arguments();
                        double acceleration = getDoubleArg(args, "acceleration");

                        simulator.setAcceleration(acceleration);

                        String message = String.format("Acceleration set to %.2f units/s²", acceleration);

                        return Mono.just(new McpSchema.CallToolResult.Builder()
                                .content(List.of(new McpSchema.TextContent(message)))
                                .isError(false)
                                .build());
                    } catch (Exception e) {
                        return Mono.just(new McpSchema.CallToolResult.Builder()
                                .content(List.of(new McpSchema.TextContent("Error: " + e.getMessage())))
                                .isError(true)
                                .build());
                    }
                })
                .build();
    }

    private static McpServerFeatures.AsyncToolSpecification createDeviceMarkVisitedTool() {
        String schema = """
            {
              "type": "object",
              "properties": {}
            }
            """;

        return new McpServerFeatures.AsyncToolSpecification.Builder()
                .tool(McpSchema.Tool.builder()
                        .name("device_mark_visited")
                        .description("Mark current target as visited and move to next target")
                        .inputSchema(McpJsonMapper.createDefault(), schema)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        Location currentTarget = simulator.getCurrentTarget();
                        if (currentTarget == null) {
                            String message = "No current target to mark as visited";
                            return Mono.just(new McpSchema.CallToolResult.Builder()
                                    .content(List.of(new McpSchema.TextContent(message)))
                                    .isError(false)
                                    .build());
                        }

                        String targetId = currentTarget.getId();
                        simulator.markCurrentAsVisited();

                        Location newTarget = simulator.getCurrentTarget();
                        String newTargetInfo = newTarget != null
                                ? String.format("Next target: %s", newTarget.getId())
                                : "No more targets";

                        String message = String.format("Marked %s as visited. %s", targetId, newTargetInfo);

                        return Mono.just(new McpSchema.CallToolResult.Builder()
                                .content(List.of(new McpSchema.TextContent(message)))
                                .isError(false)
                                .build());
                    } catch (Exception e) {
                        return Mono.just(new McpSchema.CallToolResult.Builder()
                                .content(List.of(new McpSchema.TextContent("Error: " + e.getMessage())))
                                .isError(true)
                                .build());
                    }
                })
                .build();
    }

    private static McpServerFeatures.AsyncToolSpecification createDeviceGetAllLocationsTool() {
        String schema = """
            {
              "type": "object",
              "properties": {}
            }
            """;

        return new McpServerFeatures.AsyncToolSpecification.Builder()
                .tool(McpSchema.Tool.builder()
                        .name("device_get_all_locations")
                        .description("Get all target locations with visited status")
                        .inputSchema(McpJsonMapper.createDefault(), schema)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        List<Location> locations = simulator.getAllLocations();

                        if (locations.isEmpty()) {
                            return Mono.just(new McpSchema.CallToolResult.Builder()
                                    .content(List.of(new McpSchema.TextContent("[]")))
                                    .isError(false)
                                    .build());
                        }

                        String jsonArray = locations.stream()
                                .map(loc -> String.format(
                                        "{\\\"id\\\": \\\"%s\\\", \\\"x\\\": %.2f, \\\"y\\\": %.2f, " +
                                        "\\\"visited\\\": %s, \\\"properties\\\": %s}",
                                        loc.getId(), loc.getX(), loc.getY(), loc.isVisited(),
                                        formatPropertiesJson(loc.getProperties())))
                                .collect(Collectors.joining(", "));

                        String message = String.format("[%s]", jsonArray);

                        return Mono.just(new McpSchema.CallToolResult.Builder()
                                .content(List.of(new McpSchema.TextContent(message)))
                                .isError(false)
                                .build());
                    } catch (Exception e) {
                        return Mono.just(new McpSchema.CallToolResult.Builder()
                                .content(List.of(new McpSchema.TextContent("Error: " + e.getMessage())))
                                .isError(true)
                                .build());
                    }
                })
                .build();
    }

    // ==================== STATELESS TOOL FACTORY ====================

    /**
     * Factory for creating stateless sync tools for HTTP transport.
     * These tools work with a shared simulator instance passed from the UI.
     */
    public static class ToolFactory {

        public static List<io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification>
        createAllStatelessTools(DeviceSimulator sharedSimulator, String csvPath) {

            CsvDataReader reader = new CsvDataReader();

            return List.of(
                    createCsvGetHeadersToolStateless(reader),
                    createCsvQueryLocationsToolStateless(reader),
                    createDeviceGetStateToolStateless(sharedSimulator),
                    createDeviceSetTargetsToolStateless(sharedSimulator),
                    createDeviceSetSpeedToolStateless(sharedSimulator),
                    createDeviceSetAccelerationToolStateless(sharedSimulator),
                    createDeviceMarkVisitedToolStateless(sharedSimulator),
                    createDeviceGetAllLocationsToolStateless(sharedSimulator)
            );
        }

        private static io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification
        createCsvGetHeadersToolStateless(CsvDataReader reader) {
            String schema = """
                {
                  "type": "object",
                  "properties": {
                    "filePath": {"type": "string", "description": "Path to the CSV file"}
                  },
                  "required": ["filePath"]
                }
                """;

            return new io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification.Builder()
                    .tool(McpSchema.Tool.builder()
                            .name("csv_get_headers")
                            .description("Get column headers from a CSV file")
                            .inputSchema(McpJsonMapper.createDefault(), schema)
                            .build())
                    .callHandler((transportContext, request) -> {
                        try {
                            Map<String, Object> args = request.arguments();
                            String filePath = getStringArg(args, "filePath");

                            List<String> headers = reader.getHeaders(filePath);
                            String headersJson = headers.stream()
                                    .map(h -> "\"" + h + "\"")
                                    .collect(Collectors.joining(", "));

                            String message = String.format("[%s]", headersJson);

                            return new McpSchema.CallToolResult.Builder()
                                    .content(List.of(new McpSchema.TextContent(message)))
                                    .isError(false)
                                    .build();
                        } catch (Exception e) {
                            return new McpSchema.CallToolResult.Builder()
                                    .content(List.of(new McpSchema.TextContent("Error: " + e.getMessage())))
                                    .isError(true)
                                    .build();
                        }
                    })
                    .build();
        }

        private static io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification
        createCsvQueryLocationsToolStateless(CsvDataReader reader) {
            String schema = """
                {
                  "type": "object",
                  "properties": {
                    "filePath": {"type": "string", "description": "Path to the CSV file"},
                    "xColumn": {"type": "string", "description": "Name of the x-coordinate column"},
                    "yColumn": {"type": "string", "description": "Name of the y-coordinate column"},
                    "filters": {
                      "type": "object",
                      "description": "Optional filters as {columnName: {min, max, equals}}",
                      "additionalProperties": {
                        "type": "object",
                        "properties": {
                          "min": {"type": "number"},
                          "max": {"type": "number"},
                          "equals": {}
                        }
                      }
                    }
                  },
                  "required": ["filePath", "xColumn", "yColumn"]
                }
                """;

            return new io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification.Builder()
                    .tool(McpSchema.Tool.builder()
                            .name("csv_query_locations")
                            .description("Query and filter location data from CSV file")
                            .inputSchema(McpJsonMapper.createDefault(), schema)
                            .build())
                    .callHandler((transportContext, request) -> {
                        try {
                            Map<String, Object> args = request.arguments();
                            String filePath = getStringArg(args, "filePath");
                            String xColumn = getStringArg(args, "xColumn");
                            String yColumn = getStringArg(args, "yColumn");

                            Map<String, CsvDataReader.FilterCriteria> filters = parseFilters(args);
                            List<Location> locations = reader.readLocations(filePath, xColumn, yColumn, filters);

                            String jsonArray = locations.stream()
                                    .map(loc -> String.format(
                                            "{\\\"id\\\": \\\"%s\\\", \\\"x\\\": %.2f, \\\"y\\\": %.2f, \\\"properties\\\": %s}",
                                            loc.getId(), loc.getX(), loc.getY(),
                                            formatPropertiesJson(loc.getProperties())))
                                    .collect(Collectors.joining(", "));

                            String message = String.format("[%s]", jsonArray);

                            return new McpSchema.CallToolResult.Builder()
                                    .content(List.of(new McpSchema.TextContent(message)))
                                    .isError(false)
                                    .build();
                        } catch (Exception e) {
                            return new McpSchema.CallToolResult.Builder()
                                    .content(List.of(new McpSchema.TextContent("Error: " + e.getMessage())))
                                    .isError(true)
                                    .build();
                        }
                    })
                    .build();
        }

        private static io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification
        createDeviceGetStateToolStateless(DeviceSimulator sharedSimulator) {
            String schema = """
                {
                  "type": "object",
                  "properties": {}
                }
                """;

            return new io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification.Builder()
                    .tool(McpSchema.Tool.builder()
                            .name("device_get_state")
                            .description("Get current device state (position, target, speed, moving status)")
                            .inputSchema(McpJsonMapper.createDefault(), schema)
                            .build())
                    .callHandler((transportContext, request) -> {
                        try {
                            DeviceState state = sharedSimulator.getState();
                            Location currentTarget = sharedSimulator.getCurrentTarget();

                            String targetInfo = currentTarget != null
                                    ? String.format("\\\"target\\\": {\\\"id\\\": \\\"%s\\\", \\\"x\\\": %.2f, \\\"y\\\": %.2f}",
                                            currentTarget.getId(), currentTarget.getX(), currentTarget.getY())
                                    : "\\\"target\\\": null";

                            String message = String.format(
                                    "{\\\"position\\\": {\\\"x\\\": %.2f, \\\"y\\\": %.2f}, " +
                                    "%s, " +
                                    "\\\"speed\\\": %.2f, \\\"maxSpeed\\\": %.2f, " +
                                    "\\\"acceleration\\\": %.2f, \\\"moving\\\": %s, " +
                                    "\\\"targetIndex\\\": %d}",
                                    state.getX(), state.getY(),
                                    targetInfo,
                                    state.getSpeed(), state.getMaxSpeed(),
                                    state.getAcceleration(), state.isMoving(),
                                    sharedSimulator.getCurrentTargetIndex());

                            return new McpSchema.CallToolResult.Builder()
                                    .content(List.of(new McpSchema.TextContent(message)))
                                    .isError(false)
                                    .build();
                        } catch (Exception e) {
                            return new McpSchema.CallToolResult.Builder()
                                    .content(List.of(new McpSchema.TextContent("Error: " + e.getMessage())))
                                    .isError(true)
                                    .build();
                        }
                    })
                    .build();
        }

        private static io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification
        createDeviceSetTargetsToolStateless(DeviceSimulator sharedSimulator) {
            String schema = """
                {
                  "type": "object",
                  "properties": {
                    "locations": {
                      "type": "array",
                      "description": "Array of target locations",
                      "items": {
                        "type": "object",
                        "properties": {
                          "id": {"type": "string"},
                          "x": {"type": "number"},
                          "y": {"type": "number"},
                          "properties": {"type": "object"}
                        },
                        "required": ["id", "x", "y"]
                      }
                    }
                  },
                  "required": ["locations"]
                }
                """;

            return new io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification.Builder()
                    .tool(McpSchema.Tool.builder()
                            .name("device_set_targets")
                            .description("Set target locations for the device to visit")
                            .inputSchema(McpJsonMapper.createDefault(), schema)
                            .build())
                    .callHandler((transportContext, request) -> {
                        try {
                            Map<String, Object> args = request.arguments();
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> locationsData =
                                    (List<Map<String, Object>>) args.get("locations");

                            if (locationsData == null || locationsData.isEmpty()) {
                                throw new IllegalArgumentException("Locations array cannot be null or empty");
                            }

                            List<Location> locations = new ArrayList<>();
                            for (Map<String, Object> locData : locationsData) {
                                String id = getStringArg(locData, "id");
                                double x = getDoubleArg(locData, "x");
                                double y = getDoubleArg(locData, "y");

                                @SuppressWarnings("unchecked")
                                Map<String, Object> properties = (Map<String, Object>) locData.get("properties");

                                Location location = properties != null
                                        ? new Location(id, x, y, properties)
                                        : new Location(id, x, y);
                                locations.add(location);
                            }

                            sharedSimulator.setTargetLocations(locations);

                            String message = String.format(
                                    "Set %d target locations successfully. First target: %s",
                                    locations.size(), locations.get(0).getId());

                            return new McpSchema.CallToolResult.Builder()
                                    .content(List.of(new McpSchema.TextContent(message)))
                                    .isError(false)
                                    .build();
                        } catch (Exception e) {
                            return new McpSchema.CallToolResult.Builder()
                                    .content(List.of(new McpSchema.TextContent("Error: " + e.getMessage())))
                                    .isError(true)
                                    .build();
                        }
                    })
                    .build();
        }

        private static io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification
        createDeviceSetSpeedToolStateless(DeviceSimulator sharedSimulator) {
            String schema = """
                {
                  "type": "object",
                  "properties": {
                    "maxSpeed": {
                      "type": "number",
                      "description": "Maximum speed in units per second",
                      "minimum": 0.1
                    }
                  },
                  "required": ["maxSpeed"]
                }
                """;

            return new io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification.Builder()
                    .tool(McpSchema.Tool.builder()
                            .name("device_set_speed")
                            .description("Set maximum speed for the device")
                            .inputSchema(McpJsonMapper.createDefault(), schema)
                            .build())
                    .callHandler((transportContext, request) -> {
                        try {
                            Map<String, Object> args = request.arguments();
                            double maxSpeed = getDoubleArg(args, "maxSpeed");

                            sharedSimulator.setSpeed(maxSpeed);

                            String message = String.format("Max speed set to %.2f units/s", maxSpeed);

                            return new McpSchema.CallToolResult.Builder()
                                    .content(List.of(new McpSchema.TextContent(message)))
                                    .isError(false)
                                    .build();
                        } catch (Exception e) {
                            return new McpSchema.CallToolResult.Builder()
                                    .content(List.of(new McpSchema.TextContent("Error: " + e.getMessage())))
                                    .isError(true)
                                    .build();
                        }
                    })
                    .build();
        }

        private static io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification
        createDeviceSetAccelerationToolStateless(DeviceSimulator sharedSimulator) {
            String schema = """
                {
                  "type": "object",
                  "properties": {
                    "acceleration": {
                      "type": "number",
                      "description": "Acceleration in units per second squared",
                      "minimum": 0.1
                    }
                  },
                  "required": ["acceleration"]
                }
                """;

            return new io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification.Builder()
                    .tool(McpSchema.Tool.builder()
                            .name("device_set_acceleration")
                            .description("Set acceleration for the device")
                            .inputSchema(McpJsonMapper.createDefault(), schema)
                            .build())
                    .callHandler((transportContext, request) -> {
                        try {
                            Map<String, Object> args = request.arguments();
                            double acceleration = getDoubleArg(args, "acceleration");

                            sharedSimulator.setAcceleration(acceleration);

                            String message = String.format("Acceleration set to %.2f units/s²", acceleration);

                            return new McpSchema.CallToolResult.Builder()
                                    .content(List.of(new McpSchema.TextContent(message)))
                                    .isError(false)
                                    .build();
                        } catch (Exception e) {
                            return new McpSchema.CallToolResult.Builder()
                                    .content(List.of(new McpSchema.TextContent("Error: " + e.getMessage())))
                                    .isError(true)
                                    .build();
                        }
                    })
                    .build();
        }

        private static io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification
        createDeviceMarkVisitedToolStateless(DeviceSimulator sharedSimulator) {
            String schema = """
                {
                  "type": "object",
                  "properties": {}
                }
                """;

            return new io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification.Builder()
                    .tool(McpSchema.Tool.builder()
                            .name("device_mark_visited")
                            .description("Mark current target as visited and move to next target")
                            .inputSchema(McpJsonMapper.createDefault(), schema)
                            .build())
                    .callHandler((transportContext, request) -> {
                        try {
                            Location currentTarget = sharedSimulator.getCurrentTarget();
                            if (currentTarget == null) {
                                String message = "No current target to mark as visited";
                                return new McpSchema.CallToolResult.Builder()
                                        .content(List.of(new McpSchema.TextContent(message)))
                                        .isError(false)
                                        .build();
                            }

                            String targetId = currentTarget.getId();
                            sharedSimulator.markCurrentAsVisited();

                            Location newTarget = sharedSimulator.getCurrentTarget();
                            String newTargetInfo = newTarget != null
                                    ? String.format("Next target: %s", newTarget.getId())
                                    : "No more targets";

                            String message = String.format("Marked %s as visited. %s", targetId, newTargetInfo);

                            return new McpSchema.CallToolResult.Builder()
                                    .content(List.of(new McpSchema.TextContent(message)))
                                    .isError(false)
                                    .build();
                        } catch (Exception e) {
                            return new McpSchema.CallToolResult.Builder()
                                    .content(List.of(new McpSchema.TextContent("Error: " + e.getMessage())))
                                    .isError(true)
                                    .build();
                        }
                    })
                    .build();
        }

        private static io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification
        createDeviceGetAllLocationsToolStateless(DeviceSimulator sharedSimulator) {
            String schema = """
                {
                  "type": "object",
                  "properties": {}
                }
                """;

            return new io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification.Builder()
                    .tool(McpSchema.Tool.builder()
                            .name("device_get_all_locations")
                            .description("Get all target locations with visited status")
                            .inputSchema(McpJsonMapper.createDefault(), schema)
                            .build())
                    .callHandler((transportContext, request) -> {
                        try {
                            List<Location> locations = sharedSimulator.getAllLocations();

                            if (locations.isEmpty()) {
                                return new McpSchema.CallToolResult.Builder()
                                        .content(List.of(new McpSchema.TextContent("[]")))
                                        .isError(false)
                                        .build();
                            }

                            String jsonArray = locations.stream()
                                    .map(loc -> String.format(
                                            "{\\\"id\\\": \\\"%s\\\", \\\"x\\\": %.2f, \\\"y\\\": %.2f, " +
                                            "\\\"visited\\\": %s, \\\"properties\\\": %s}",
                                            loc.getId(), loc.getX(), loc.getY(), loc.isVisited(),
                                            formatPropertiesJson(loc.getProperties())))
                                    .collect(Collectors.joining(", "));

                            String message = String.format("[%s]", jsonArray);

                            return new McpSchema.CallToolResult.Builder()
                                    .content(List.of(new McpSchema.TextContent(message)))
                                    .isError(false)
                                    .build();
                        } catch (Exception e) {
                            return new McpSchema.CallToolResult.Builder()
                                    .content(List.of(new McpSchema.TextContent("Error: " + e.getMessage())))
                                    .isError(true)
                                    .build();
                        }
                    })
                    .build();
        }

        private static Map<String, CsvDataReader.FilterCriteria> parseFilters(Map<String, Object> args) {
            Map<String, CsvDataReader.FilterCriteria> filters = new HashMap<>();
            @SuppressWarnings("unchecked")
            Map<String, Object> filtersObj = (Map<String, Object>) args.get("filters");

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
                                getDoubleArg(criteria, "min") : null;
                        Double max = criteria.containsKey("max") ?
                                getDoubleArg(criteria, "max") : null;
                        filters.put(columnName, new CsvDataReader.FilterCriteria(
                                columnName, min, max));
                    }
                }
            }
            return filters;
        }
    }

    // ==================== HELPER METHODS ====================

    private static String formatPropertiesJson(Map<String, Object> properties) {
        if (properties == null || properties.isEmpty()) {
            return "{}";
        }

        String propsStr = properties.entrySet().stream()
                .map(e -> String.format("\\\"%s\\\": %s", e.getKey(), formatValueJson(e.getValue())))
                .collect(Collectors.joining(", "));

        return "{" + propsStr + "}";
    }

    private static String formatValueJson(Object value) {
        if (value instanceof String) {
            return "\\\"" + value + "\\\"";
        } else if (value instanceof Number) {
            return value.toString();
        } else {
            return "\\\"" + value + "\\\"";
        }
    }

    private static String getStringArg(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Required parameter missing: " + key);
        }
        return value.toString();
    }

    private static double getDoubleArg(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Required parameter missing: " + key);
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return Double.parseDouble(value.toString());
    }
}
