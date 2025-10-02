package com.imageprocessing.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.json.McpJsonMapper;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * MCP tools for managing the JavaFX UI workflow.
 * External clients can add tools to the workflow, check status, and trigger execution.
 */
public class WorkflowMcpTools {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== ADD_TO_WORKFLOW ====================

    public static McpServerFeatures.AsyncToolSpecification createAsyncAddToWorkflow() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "tool": {
                  "type": "string",
                  "description": "Name of the tool to add (e.g., 'load_image', 'segment_image')"
                },
                "parameters": {
                  "type": "object",
                  "description": "Tool parameters as key-value pairs"
                }
              },
              "required": ["tool", "parameters"]
            }
            """;

        return new McpServerFeatures.AsyncToolSpecification.Builder().tool(
                McpSchema.Tool.builder()
                        .name("add_to_workflow")
                        .description("Add a tool to the UI workflow for user review. The user can then execute it from the UI.")
                        .inputSchema(McpJsonMapper.createDefault(), schema)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args = request.arguments();
                        String toolName = (String) args.get("tool");
                        @SuppressWarnings("unchecked")
                        Map<String, Object> params = (Map<String, Object>) args.get("parameters");

                        String result = WorkflowManager.addToolToWorkflow(toolName, params).get();
                        return Mono.just(new McpSchema.CallToolResult.Builder()
                                .content(List.of(new McpSchema.TextContent(result)))
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

    public static McpStatelessServerFeatures.SyncToolSpecification createStatelessAddToWorkflow() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "tool": {
                  "type": "string",
                  "description": "Name of the tool to add"
                },
                "parameters": {
                  "type": "object",
                  "description": "Tool parameters"
                }
              },
              "required": ["tool", "parameters"]
            }
            """;

        return new McpStatelessServerFeatures.SyncToolSpecification.Builder().tool(
                McpSchema.Tool.builder()
                        .name("add_to_workflow")
                        .description("Add a tool to the UI workflow for user review")
                        .inputSchema(McpJsonMapper.createDefault(), schema)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args = request.arguments();
                        String toolName = (String) args.get("tool");
                        @SuppressWarnings("unchecked")
                        Map<String, Object> params = (Map<String, Object>) args.get("parameters");

                        String result = WorkflowManager.addToolToWorkflow(toolName, params).get();
                        return new McpSchema.CallToolResult.Builder()
                                .content(List.of(new McpSchema.TextContent(result)))
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

    // ==================== CLEAR_WORKFLOW ====================

    public static McpServerFeatures.AsyncToolSpecification createAsyncClearWorkflow() {
        String schema = """
            {
              "type": "object",
              "properties": {}
            }
            """;

        return new McpServerFeatures.AsyncToolSpecification.Builder().tool(
                McpSchema.Tool.builder()
                        .name("clear_workflow")
                        .description("Clear all tools from the UI workflow")
                        .inputSchema(McpJsonMapper.createDefault(), schema)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        String result = WorkflowManager.clearWorkflow().get();
                        return Mono.just(new McpSchema.CallToolResult.Builder()
                                .content(List.of(new McpSchema.TextContent(result)))
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

    public static McpStatelessServerFeatures.SyncToolSpecification createStatelessClearWorkflow() {
        String schema = """
            {
              "type": "object",
              "properties": {}
            }
            """;

        return new McpStatelessServerFeatures.SyncToolSpecification.Builder().tool(
                McpSchema.Tool.builder()
                        .name("clear_workflow")
                        .description("Clear all tools from the UI workflow")
                        .inputSchema(McpJsonMapper.createDefault(), schema)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        String result = WorkflowManager.clearWorkflow().get();
                        return new McpSchema.CallToolResult.Builder()
                                .content(List.of(new McpSchema.TextContent(result)))
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

    // ==================== GET_WORKFLOW_STATUS ====================

    public static McpServerFeatures.AsyncToolSpecification createAsyncGetWorkflowStatus() {
        String schema = """
            {
              "type": "object",
              "properties": {}
            }
            """;

        return new McpServerFeatures.AsyncToolSpecification.Builder().tool(
                McpSchema.Tool.builder()
                        .name("get_workflow_status")
                        .description("Get the current status of the UI workflow")
                        .inputSchema(McpJsonMapper.createDefault(), schema)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        String result = WorkflowManager.getWorkflowStatus().get();
                        return Mono.just(new McpSchema.CallToolResult.Builder()
                                .content(List.of(new McpSchema.TextContent(result)))
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

    public static McpStatelessServerFeatures.SyncToolSpecification createStatelessGetWorkflowStatus() {
        String schema = """
            {
              "type": "object",
              "properties": {}
            }
            """;

        return new McpStatelessServerFeatures.SyncToolSpecification.Builder().tool(
                McpSchema.Tool.builder()
                        .name("get_workflow_status")
                        .description("Get the current status of the UI workflow")
                        .inputSchema(McpJsonMapper.createDefault(), schema)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        String result = WorkflowManager.getWorkflowStatus().get();
                        return new McpSchema.CallToolResult.Builder()
                                .content(List.of(new McpSchema.TextContent(result)))
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

    // ==================== EXECUTE_WORKFLOW ====================

    public static McpServerFeatures.AsyncToolSpecification createAsyncExecuteWorkflow() {
        String schema = """
            {
              "type": "object",
              "properties": {}
            }
            """;

        return new McpServerFeatures.AsyncToolSpecification.Builder().tool(
                McpSchema.Tool.builder()
                        .name("execute_workflow")
                        .description("Execute the UI workflow and return all image results displayed in the UI.")
                        .inputSchema(McpJsonMapper.createDefault(), schema)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        Map<String, Object> result = WorkflowManager.executeWorkflow().get();

                        // Check for error
                        if (result.containsKey("error")) {
                            return Mono.just(new McpSchema.CallToolResult.Builder()
                                    .content(List.of(new McpSchema.TextContent("Error: " + result.get("error"))))
                                    .isError(true)
                                    .build());
                        }

                        // Build response with status and images
                        List<McpSchema.Content> contentList = new java.util.ArrayList<>();

                        // Add summary text
                        StringBuilder summary = new StringBuilder();
                        summary.append("Workflow execution completed!\n");
                        summary.append(String.format("- Total tools: %d\n", result.get("totalTools")));
                        summary.append(String.format("- Completed: %d\n", result.get("completed")));
                        summary.append(String.format("- Errors: %d\n", result.get("errors")));
                        summary.append(String.format("- Images generated: %d\n", result.get("imageCount")));
                        contentList.add(new McpSchema.TextContent(summary.toString()));

                        // Add all images
                        @SuppressWarnings("unchecked")
                        List<Map<String, String>> images = (List<Map<String, String>>) result.get("images");
                        if (images != null) {
                            for (Map<String, String> img : images) {
                                // Extract base64 data from data URI
                                String dataUri = img.get("data");
                                String base64Data = dataUri.substring(dataUri.indexOf(",") + 1);
                                contentList.add(new McpSchema.ImageContent(null, base64Data, "image/png"));
                            }
                        }

                        return Mono.just(new McpSchema.CallToolResult.Builder()
                                .content(contentList)
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

    public static McpStatelessServerFeatures.SyncToolSpecification createStatelessExecuteWorkflow() {
        String schema = """
            {
              "type": "object",
              "properties": {}
            }
            """;

        return new McpStatelessServerFeatures.SyncToolSpecification.Builder().tool(
                McpSchema.Tool.builder()
                        .name("execute_workflow")
                        .description("Execute the UI workflow and return all image results displayed in the UI.")
                        .inputSchema(McpJsonMapper.createDefault(), schema)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        Map<String, Object> result = WorkflowManager.executeWorkflow().get();

                        // Check for error
                        if (result.containsKey("error")) {
                            return new McpSchema.CallToolResult.Builder()
                                    .content(List.of(new McpSchema.TextContent("Error: " + result.get("error"))))
                                    .isError(true)
                                    .build();
                        }

                        // Build response with status and images
                        List<McpSchema.Content> contentList = new java.util.ArrayList<>();

                        // Add summary text
                        StringBuilder summary = new StringBuilder();
                        summary.append("Workflow execution completed!\n");
                        summary.append(String.format("- Total tools: %d\n", result.get("totalTools")));
                        summary.append(String.format("- Completed: %d\n", result.get("completed")));
                        summary.append(String.format("- Errors: %d\n", result.get("errors")));
                        summary.append(String.format("- Images generated: %d\n", result.get("imageCount")));
                        contentList.add(new McpSchema.TextContent(summary.toString()));

                        // Add all images
                        @SuppressWarnings("unchecked")
                        List<Map<String, String>> images = (List<Map<String, String>>) result.get("images");
                        if (images != null) {
                            for (Map<String, String> img : images) {
                                // Extract base64 data from data URI
                                String dataUri = img.get("data");
                                String base64Data = dataUri.substring(dataUri.indexOf(",") + 1);
                                contentList.add(new McpSchema.ImageContent(null, base64Data, "image/png"));
                            }
                        }

                        return new McpSchema.CallToolResult.Builder()
                                .content(contentList)
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
}
