package com.openrewrite.ui.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Client for communicating with the OpenRewrite MCP server.
 * Provides methods to list recipes, apply transformations, and analyze code.
 */
public class McpClient {

    private static final Logger logger = LoggerFactory.getLogger(McpClient.class);
    private final String serverUrl;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService;
    private boolean connected;
    private int requestIdCounter = 1;

    public McpClient(String serverUrl) {
        this.serverUrl = serverUrl;
        this.objectMapper = new ObjectMapper();
        this.executorService = Executors.newFixedThreadPool(4);
        this.connected = false;
    }

    /**
     * Connect to the MCP server by attempting to list recipes.
     * This validates the connection is working.
     */
    public void connect() {
        try {
            // Test connection by attempting to list available tools
            Map<String, Object> request = new HashMap<>();
            request.put("jsonrpc", "2.0");
            request.put("id", requestIdCounter++);
            request.put("method", "tools/list");
            request.put("params", Collections.emptyMap());

            String response = sendRequest("", request);
            JsonNode root = objectMapper.readTree(response);

            // Check if response has expected structure
            if (root.has("result") && root.path("result").has("tools")) {
                connected = true;
                logger.info("Connected to MCP server at {}", serverUrl);
            } else {
                logger.warn("MCP server response missing expected structure");
                connected = false;
            }
        } catch (Exception e) {
            logger.error("Failed to connect to MCP server at {}", serverUrl, e);
            connected = false;
        }
    }

    /**
     * Disconnect from the MCP server.
     */
    public void disconnect() {
        connected = false;
        executorService.shutdown();
        logger.info("Disconnected from MCP server");
    }

    /**
     * Check if connected to the server.
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * List all available recipes from the server.
     */
    public CompletableFuture<List<Recipe>> listRecipes() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, Object> request = new HashMap<>();
                request.put("jsonrpc", "2.0");
                request.put("id", requestIdCounter++);
                request.put("method", "tools/call");
                request.put("params", Map.of(
                    "name", "list_recipes",
                    "arguments", Collections.emptyMap()
                ));

                String response = sendRequest("", request);
                JsonNode root = objectMapper.readTree(response);

                // Parse the content from the result
                JsonNode contentArray = root.path("result").path("content");
                if (contentArray.isArray() && contentArray.size() > 0) {
                    String jsonContent = contentArray.get(0).path("text").asText();
                    JsonNode recipeData = objectMapper.readTree(jsonContent);
                    JsonNode recipesNode = recipeData.path("recipes");

                    List<Recipe> recipes = new ArrayList<>();
                    if (recipesNode.isArray()) {
                        for (JsonNode recipeNode : recipesNode) {
                            Recipe recipe = parseRecipe(recipeNode);
                            recipes.add(recipe);
                        }
                    }

                    logger.info("Loaded {} recipes from server", recipes.size());
                    return recipes;
                } else {
                    logger.warn("No content in MCP response or unexpected format");
                    return new ArrayList<>();
                }
            } catch (Exception e) {
                logger.error("Failed to list recipes", e);
                throw new RuntimeException("Failed to list recipes: " + e.getMessage(), e);
            }
        }, executorService);
    }

    /**
     * Apply a recipe to transform source code.
     */
    public CompletableFuture<TransformationResult> applyRecipe(String sourceCode, String recipeName, String language) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, Object> params = new HashMap<>();
                params.put("sourceCode", sourceCode);
                params.put("recipeName", recipeName);
                params.put("language", language);

                Map<String, Object> request = new HashMap<>();
                request.put("jsonrpc", "2.0");
                request.put("id", requestIdCounter++);
                request.put("method", "tools/call");
                request.put("params", Map.of(
                    "name", "apply_recipe",
                    "arguments", params
                ));

                String response = sendRequest("", request);
                JsonNode root = objectMapper.readTree(response);

                // Parse the content from the result
                JsonNode contentArray = root.path("result").path("content");
                TransformationResult result;
                if (contentArray.isArray() && contentArray.size() > 0) {
                    String jsonContent = contentArray.get(0).path("text").asText();
                    JsonNode transformData = objectMapper.readTree(jsonContent);
                    result = parseTransformationResult(transformData, sourceCode, recipeName);
                } else {
                    throw new RuntimeException("No content in MCP response");
                }
                logger.info("Applied recipe {} - hasChanges: {}", recipeName, result.hasChanges());
                return result;
            } catch (Exception e) {
                logger.error("Failed to apply recipe", e);
                TransformationResult errorResult = new TransformationResult();
                errorResult.setOriginalCode(sourceCode);
                errorResult.setTransformedCode(sourceCode);
                errorResult.setRecipeName(recipeName);
                errorResult.setErrorMessage(e.getMessage());
                errorResult.setSuccess(false);
                return errorResult;
            }
        }, executorService);
    }

    /**
     * Analyze code and get recipe suggestions.
     */
    public CompletableFuture<List<Recipe>> analyzeCode(String sourceCode, String language) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, Object> params = new HashMap<>();
                params.put("sourceCode", sourceCode);
                params.put("language", language);

                Map<String, Object> request = new HashMap<>();
                request.put("jsonrpc", "2.0");
                request.put("id", requestIdCounter++);
                request.put("method", "tools/call");
                request.put("params", Map.of(
                    "name", "analyze_code",
                    "arguments", params
                ));

                String response = sendRequest("", request);
                JsonNode root = objectMapper.readTree(response);

                // Parse the content from the result
                JsonNode contentArray = root.path("result").path("content");
                JsonNode suggestionsNode = null;
                if (contentArray.isArray() && contentArray.size() > 0) {
                    String jsonContent = contentArray.get(0).path("text").asText();
                    JsonNode analysisData = objectMapper.readTree(jsonContent);
                    suggestionsNode = analysisData.path("suggestions");
                }

                List<Recipe> suggestions = new ArrayList<>();
                if (suggestionsNode != null && suggestionsNode.isArray()) {
                    for (JsonNode suggestionNode : suggestionsNode) {
                        Recipe recipe = new Recipe();
                        recipe.setName(suggestionNode.path("recipeName").asText());
                        recipe.setDisplayName(suggestionNode.path("displayName").asText());
                        recipe.setDescription(suggestionNode.path("description").asText());
                        suggestions.add(recipe);
                    }
                }

                logger.info("Code analysis found {} applicable recipes", suggestions.size());
                return suggestions;
            } catch (Exception e) {
                logger.error("Failed to analyze code", e);
                throw new RuntimeException("Failed to analyze code: " + e.getMessage(), e);
            }
        }, executorService);
    }

    /**
     * Get detailed information about a specific recipe.
     */
    public CompletableFuture<Recipe> getRecipeDetails(String recipeName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, Object> params = new HashMap<>();
                params.put("recipeName", recipeName);

                Map<String, Object> request = new HashMap<>();
                request.put("jsonrpc", "2.0");
                request.put("id", requestIdCounter++);
                request.put("method", "tools/call");
                request.put("params", Map.of(
                    "name", "get_recipe_description",
                    "arguments", params
                ));

                String response = sendRequest("", request);
                JsonNode root = objectMapper.readTree(response);

                // Parse the content from the result
                JsonNode contentArray = root.path("result").path("content");
                if (contentArray.isArray() && contentArray.size() > 0) {
                    String jsonContent = contentArray.get(0).path("text").asText();
                    JsonNode recipeData = objectMapper.readTree(jsonContent);
                    return parseRecipe(recipeData);
                } else {
                    throw new RuntimeException("No content in MCP response");
                }
            } catch (Exception e) {
                logger.error("Failed to get recipe details", e);
                throw new RuntimeException("Failed to get recipe details: " + e.getMessage(), e);
            }
        }, executorService);
    }

    /**
     * Send HTTP request to the MCP server.
     */
    private String sendRequest(String endpoint, Map<String, Object> requestData) throws Exception {
        // If endpoint is empty, use serverUrl as-is, otherwise append endpoint
        URL url = endpoint.isEmpty() ? new URL(serverUrl) : new URL(serverUrl + endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json, text/event-stream");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(30000);

        // Send request
        String jsonRequest = objectMapper.writeValueAsString(requestData);
        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = jsonRequest.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        // Read response
        int responseCode = conn.getResponseCode();
        StringBuilder response = new StringBuilder();

        try (BufferedReader br = new BufferedReader(
            new InputStreamReader(
                responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream(),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line);
            }
        }

        conn.disconnect();

        if (responseCode >= 400) {
            throw new RuntimeException("HTTP error " + responseCode + ": " + response.toString());
        }

        return response.toString();
    }

    /**
     * Parse a Recipe from JSON node.
     */
    private Recipe parseRecipe(JsonNode node) {
        Recipe recipe = new Recipe();
        recipe.setName(node.path("name").asText());
        recipe.setDisplayName(node.path("displayName").asText());
        recipe.setDescription(node.path("description").asText());

        // Parse tags
        JsonNode tagsNode = node.path("tags");
        if (tagsNode.isArray()) {
            Set<String> tags = new HashSet<>();
            tagsNode.forEach(tag -> tags.add(tag.asText()));
            recipe.setTags(tags);
        }

        // Parse options
        JsonNode optionsNode = node.path("options");
        if (optionsNode.isArray()) {
            List<Recipe.RecipeOption> options = new ArrayList<>();
            optionsNode.forEach(optionNode -> {
                Recipe.RecipeOption option = new Recipe.RecipeOption();
                option.setName(optionNode.path("name").asText());
                option.setDescription(optionNode.path("description").asText());
                option.setType(optionNode.path("type").asText());
                option.setRequired(optionNode.path("required").asBoolean(false));
                options.add(option);
            });
            recipe.setOptions(options);
        }

        // Parse examples
        JsonNode examplesNode = node.path("examples");
        if (examplesNode.isArray()) {
            List<String> examples = new ArrayList<>();
            examplesNode.forEach(example -> examples.add(example.asText()));
            recipe.setExamples(examples);
        }

        return recipe;
    }

    /**
     * Parse a TransformationResult from JSON node.
     */
    private TransformationResult parseTransformationResult(JsonNode node, String originalCode, String recipeName) {
        TransformationResult result = new TransformationResult();
        result.setOriginalCode(originalCode);
        result.setTransformedCode(node.path("transformed").asText(originalCode));
        result.setRecipeName(recipeName);
        result.setRecipeDisplayName(node.path("recipeDisplayName").asText(recipeName));
        result.setHasChanges(node.path("hasChanges").asBoolean(false));
        result.setDiff(node.path("diff").asText());
        result.setSuccess(true);

        return result;
    }

    /**
     * Get the server URL.
     */
    public String getServerUrl() {
        return serverUrl;
    }
}
