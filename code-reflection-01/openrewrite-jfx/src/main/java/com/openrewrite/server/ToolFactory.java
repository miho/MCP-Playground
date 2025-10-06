package com.openrewrite.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.json.McpJsonMapper;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Factory class for creating MCP tool specifications for OpenRewrite operations.
 * Provides both async (stdio) and stateless sync (HTTP) tool implementations.
 */
public class ToolFactory {

    private static final RewriteEngine rewriteEngine = new RewriteEngine();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== ASYNC TOOLS (STDIO) ====================

    public static McpServerFeatures.AsyncToolSpecification createListRecipesTool() {
        String schema = """
            {
              "type": "object",
              "properties": {},
              "description": "List all available OpenRewrite recipes"
            }
            """;

        return new McpServerFeatures.AsyncToolSpecification.Builder().tool(
                McpSchema.Tool.builder()
                        .name("list_recipes")
                        .description("List all available OpenRewrite recipes with their descriptions")
                        .inputSchema(McpJsonMapper.createDefault(), schema)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        Map<String, Object> result = rewriteEngine.listAvailableRecipes();
                        String jsonResult = objectMapper.writeValueAsString(result);

                        return Mono.just(new McpSchema.CallToolResult.Builder()
                                .content(List.of(new McpSchema.TextContent(jsonResult)))
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

    public static McpServerFeatures.AsyncToolSpecification createGetRecipeDescriptionTool() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "recipeName": {
                  "type": "string",
                  "description": "Full name of the recipe (e.g., org.openrewrite.java.migrate.UpgradeToJava17)"
                }
              },
              "required": ["recipeName"]
            }
            """;

        return new McpServerFeatures.AsyncToolSpecification.Builder().tool(
                McpSchema.Tool.builder()
                        .name("get_recipe_description")
                        .description("Get detailed description and metadata for a specific OpenRewrite recipe")
                        .inputSchema(McpJsonMapper.createDefault(), schema)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args = request.arguments();
                        String recipeName = getStringArg(args, "recipeName");

                        Map<String, Object> result = rewriteEngine.getRecipeDescription(recipeName);
                        String jsonResult = objectMapper.writeValueAsString(result);

                        return Mono.just(new McpSchema.CallToolResult.Builder()
                                .content(List.of(new McpSchema.TextContent(jsonResult)))
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

    public static McpServerFeatures.AsyncToolSpecification createApplyRecipeTool() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "sourceCode": {
                  "type": "string",
                  "description": "Source code to transform"
                },
                "recipeName": {
                  "type": "string",
                  "description": "Full name of the recipe to apply"
                },
                "language": {
                  "type": "string",
                  "description": "Source code language: java, kotlin, xml, gradle, yaml",
                  "default": "java"
                }
              },
              "required": ["sourceCode", "recipeName"]
            }
            """;

        return new McpServerFeatures.AsyncToolSpecification.Builder().tool(
                McpSchema.Tool.builder()
                        .name("apply_recipe")
                        .description("Apply an OpenRewrite recipe to transform source code")
                        .inputSchema(McpJsonMapper.createDefault(), schema)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args = request.arguments();
                        String sourceCode = getStringArg(args, "sourceCode");
                        String recipeName = getStringArg(args, "recipeName");
                        String language = getStringArg(args, "language", "java");

                        Map<String, Object> result = rewriteEngine.applyRecipe(sourceCode, recipeName, language);
                        String jsonResult = objectMapper.writeValueAsString(result);

                        return Mono.just(new McpSchema.CallToolResult.Builder()
                                .content(List.of(new McpSchema.TextContent(jsonResult)))
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

    public static McpServerFeatures.AsyncToolSpecification createAnalyzeCodeTool() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "sourceCode": {
                  "type": "string",
                  "description": "Source code to analyze"
                },
                "language": {
                  "type": "string",
                  "description": "Source code language: java, kotlin, xml, gradle, yaml",
                  "default": "java"
                }
              },
              "required": ["sourceCode"]
            }
            """;

        return new McpServerFeatures.AsyncToolSpecification.Builder().tool(
                McpSchema.Tool.builder()
                        .name("analyze_code")
                        .description("Analyze source code and suggest applicable OpenRewrite recipes")
                        .inputSchema(McpJsonMapper.createDefault(), schema)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args = request.arguments();
                        String sourceCode = getStringArg(args, "sourceCode");
                        String language = getStringArg(args, "language", "java");

                        Map<String, Object> result = rewriteEngine.analyzeCode(sourceCode, language);
                        String jsonResult = objectMapper.writeValueAsString(result);

                        return Mono.just(new McpSchema.CallToolResult.Builder()
                                .content(List.of(new McpSchema.TextContent(jsonResult)))
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

    public static McpServerFeatures.AsyncToolSpecification createCustomRecipeTool() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "recipeYaml": {
                  "type": "string",
                  "description": "YAML recipe specification"
                }
              },
              "required": ["recipeYaml"]
            }
            """;

        return new McpServerFeatures.AsyncToolSpecification.Builder().tool(
                McpSchema.Tool.builder()
                        .name("create_custom_recipe")
                        .description("Create a custom OpenRewrite recipe from YAML specification")
                        .inputSchema(McpJsonMapper.createDefault(), schema)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args = request.arguments();
                        String recipeYaml = getStringArg(args, "recipeYaml");

                        Map<String, Object> result = rewriteEngine.createCustomRecipe(recipeYaml);
                        String jsonResult = objectMapper.writeValueAsString(result);

                        return Mono.just(new McpSchema.CallToolResult.Builder()
                                .content(List.of(new McpSchema.TextContent(jsonResult)))
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

    // ==================== STATELESS SYNC TOOLS (HTTP) ====================

    public static McpStatelessServerFeatures.SyncToolSpecification createStatelessListRecipesTool() {
        return createSyncToolFromAsync(createListRecipesTool());
    }

    public static McpStatelessServerFeatures.SyncToolSpecification createStatelessGetRecipeDescriptionTool() {
        return createSyncToolFromAsync(createGetRecipeDescriptionTool());
    }

    public static McpStatelessServerFeatures.SyncToolSpecification createStatelessApplyRecipeTool() {
        return createSyncToolFromAsync(createApplyRecipeTool());
    }

    public static McpStatelessServerFeatures.SyncToolSpecification createStatelessAnalyzeCodeTool() {
        return createSyncToolFromAsync(createAnalyzeCodeTool());
    }

    public static McpStatelessServerFeatures.SyncToolSpecification createStatelessCustomRecipeTool() {
        return createSyncToolFromAsync(createCustomRecipeTool());
    }

    // ==================== HELPER METHODS ====================

    /**
     * Helper method to convert async tool handler to sync tool handler.
     * This wraps the async Mono response and blocks to get the result.
     */
    private static McpStatelessServerFeatures.SyncToolSpecification createSyncToolFromAsync(
            McpServerFeatures.AsyncToolSpecification asyncTool) {

        var asyncHandler = asyncTool.callHandler();
        var tool = asyncTool.tool();

        return new McpStatelessServerFeatures.SyncToolSpecification.Builder()
                .tool(tool)
                .callHandler((transportContext, request) -> {
                    // Block on the async handler to get the result synchronously
                    return asyncHandler.apply(null, request).block();
                })
                .build();
    }

    private static String getStringArg(Map<String, Object> args, String key) {
        return getStringArg(args, key, null);
    }

    private static String getStringArg(Map<String, Object> args, String key, String defaultValue) {
        Object value = args.get(key);
        if (value == null) return defaultValue;
        return value.toString();
    }
}
