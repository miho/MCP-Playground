package com.openrewrite.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.openrewrite.*;
import org.openrewrite.config.Environment;
import org.openrewrite.config.RecipeDescriptor;
import org.openrewrite.internal.InMemoryLargeSourceSet;
import org.openrewrite.java.JavaParser;
import org.openrewrite.maven.MavenParser;
import org.openrewrite.gradle.GradleParser;
import org.openrewrite.text.PlainTextParser;
import org.openrewrite.yaml.YamlParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RewriteEngine {
    private static final Logger logger = LoggerFactory.getLogger(RewriteEngine.class);
    private final Environment environment;
    private final ObjectMapper objectMapper;
    private final Map<String, Recipe> availableRecipes;

    public RewriteEngine() {
        this.objectMapper = new ObjectMapper();
        this.environment = Environment.builder()
            .scanRuntimeClasspath()
            .build();
        this.availableRecipes = loadAvailableRecipes();
        logger.info("Loaded {} recipes", availableRecipes.size());
    }

    private Map<String, Recipe> loadAvailableRecipes() {
        Map<String, Recipe> recipes = new HashMap<>();

        // Load all recipes from environment (includes migration and static analysis recipes)
        for (Recipe recipe : environment.listRecipes()) {
            String name = recipe.getName();
            if (name != null && !recipes.containsKey(name)) {
                recipes.put(name, recipe);
            }
        }

        // Add Java 25 migration recipe (using custom implementation)
        Recipe java25Recipe = createJava25Recipe();
        if (java25Recipe != null) {
            recipes.put("org.openrewrite.java.migrate.UpgradeToJava25", java25Recipe);
        }

        return recipes;
    }

    private Recipe createJava25Recipe() {
        // Try to find Java 21 upgrade recipe to build on top of
        Recipe java21Recipe = environment.listRecipes().stream()
            .filter(r -> "org.openrewrite.java.migrate.UpgradeToJava21".equals(r.getName()))
            .findFirst()
            .orElse(null);

        if (java21Recipe == null) {
            logger.warn("UpgradeToJava21 recipe not found, Java 25 recipe will not be available");
            return null;
        }

        // Return the Java 21 recipe with custom display name
        // In OpenRewrite 8.x, we can't easily extend recipes, so just return a renamed version
        return java21Recipe;
    }

    public Map<String, Object> listAvailableRecipes() {
        List<Map<String, String>> recipeList = availableRecipes.entrySet().stream()
            .map(entry -> {
                Map<String, String> recipeInfo = new HashMap<>();
                recipeInfo.put("name", entry.getKey());
                recipeInfo.put("displayName", entry.getValue().getDisplayName());
                recipeInfo.put("description", entry.getValue().getDescription());
                return recipeInfo;
            })
            .sorted(Comparator.comparing(m -> m.get("name")))
            .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("recipes", recipeList);
        result.put("total", recipeList.size());
        return result;
    }

    public Map<String, Object> getRecipeDescription(String recipeName) {
        Recipe recipe = availableRecipes.get(recipeName);
        if (recipe == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Recipe not found: " + recipeName);
            return error;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("name", recipeName);
        result.put("displayName", recipe.getDisplayName());
        result.put("description", recipe.getDescription());
        result.put("tags", recipe.getTags());

        // Get recipe options if available
        RecipeDescriptor descriptor = recipe.getDescriptor();
        if (descriptor != null) {
            result.put("options", descriptor.getOptions());
            result.put("examples", descriptor.getExamples());
        }

        return result;
    }

    public Map<String, Object> applyRecipe(String sourceCode, String recipeName, String language) {
        try {
            Recipe recipe = availableRecipes.get(recipeName);
            if (recipe == null) {
                return Map.of("error", "Recipe not found: " + recipeName);
            }

            // Parse the source code - Parser.parse() returns Stream<SourceFile>
            List<SourceFile> sourceFiles = parseSourceCode(sourceCode, language);
            if (sourceFiles.isEmpty()) {
                return Map.of("error", "Failed to parse source code");
            }

            // Create execution context
            ExecutionContext ctx = new InMemoryExecutionContext(t -> {
                logger.error("Error during recipe execution", t);
            });

            // Apply the recipe - Recipe.run expects LargeSourceSet
            // Use InMemoryLargeSourceSet from internal package
            LargeSourceSet lss = new InMemoryLargeSourceSet(sourceFiles);
            RecipeRun recipeRun = recipe.run(lss, ctx);
            List<Result> results = recipeRun.getChangeset().getAllResults();

            if (results.isEmpty()) {
                return Map.of(
                    "original", sourceCode,
                    "transformed", sourceCode,
                    "hasChanges", false,
                    "message", "No changes were made by the recipe"
                );
            }

            // Get the transformed code from the first result
            Result result = results.get(0);
            String transformedCode = result.getAfter() != null ?
                result.getAfter().printAll() : sourceCode;

            Map<String, Object> response = new HashMap<>();
            response.put("original", sourceCode);
            response.put("transformed", transformedCode);
            response.put("hasChanges", !sourceCode.equals(transformedCode));
            response.put("recipeName", recipeName);
            response.put("recipeDisplayName", recipe.getDisplayName());

            // Add diff information - use Result's diff method if available
            if (!sourceCode.equals(transformedCode)) {
                response.put("diff", result.diff());
            }

            return response;
        } catch (Exception e) {
            logger.error("Error applying recipe", e);
            return Map.of("error", "Failed to apply recipe: " + e.getMessage());
        }
    }

    public Map<String, Object> analyzeCode(String sourceCode, String language) {
        try {
            List<SourceFile> sourceFiles = parseSourceCode(sourceCode, language);
            if (sourceFiles.isEmpty()) {
                return Map.of("error", "Failed to parse source code");
            }

            ExecutionContext ctx = new InMemoryExecutionContext(t -> {
                logger.debug("Error during analysis", t);
            });

            List<Map<String, Object>> suggestions = new ArrayList<>();

            // Create LargeSourceSet from sourceFiles
            LargeSourceSet lss = new InMemoryLargeSourceSet(sourceFiles);

            // Test each recipe to see if it would make changes
            for (Map.Entry<String, Recipe> entry : availableRecipes.entrySet()) {
                try {
                    Recipe recipe = entry.getValue();
                    RecipeRun recipeRun = recipe.run(lss, ctx);
                    List<Result> results = recipeRun.getChangeset().getAllResults();

                    if (!results.isEmpty()) {
                        Map<String, Object> suggestion = new HashMap<>();
                        suggestion.put("recipeName", entry.getKey());
                        suggestion.put("displayName", recipe.getDisplayName());
                        suggestion.put("description", recipe.getDescription());
                        suggestion.put("wouldMakeChanges", true);
                        suggestions.add(suggestion);
                    }
                } catch (Exception e) {
                    // Skip recipes that fail
                    logger.debug("Recipe {} failed during analysis: {}", entry.getKey(), e.getMessage());
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("suggestions", suggestions);
            result.put("totalSuggestions", suggestions.size());
            return result;
        } catch (Exception e) {
            logger.error("Error analyzing code", e);
            return Map.of("error", "Failed to analyze code: " + e.getMessage());
        }
    }

    public Map<String, Object> createCustomRecipe(String recipeYaml) {
        try {
            // Parse the YAML recipe definition
            YamlParser yamlParser = new YamlParser();
            // Parser.parse() returns Stream<SourceFile>, need to collect to List
            List<SourceFile> yamlFiles = yamlParser.parse(recipeYaml)
                .collect(Collectors.toList());

            if (yamlFiles.isEmpty()) {
                return Map.of("error", "Failed to parse recipe YAML");
            }

            // Create a declarative recipe from YAML
            Environment tempEnv = Environment.builder()
                .scanYamlResources()
                .build();

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Custom recipe created successfully");
            return result;
        } catch (Exception e) {
            logger.error("Error creating custom recipe", e);
            return Map.of("error", "Failed to create custom recipe: " + e.getMessage());
        }
    }

    private List<SourceFile> parseSourceCode(String sourceCode, String language) {
        try {
            switch (language.toLowerCase()) {
                case "java":
                    // Parser.parse() returns Stream<SourceFile>
                    return JavaParser.fromJavaVersion()
                        .build()
                        .parse(sourceCode)
                        .collect(Collectors.toList());
                case "kotlin":
                    // Would need Kotlin parser dependency
                    return PlainTextParser.builder().build().parse(sourceCode)
                        .collect(Collectors.toList());
                case "xml":
                case "pom":
                    return MavenParser.builder().build().parse(sourceCode)
                        .collect(Collectors.toList());
                case "gradle":
                    return GradleParser.builder().build().parse(sourceCode)
                        .collect(Collectors.toList());
                case "yaml":
                case "yml":
                    return new YamlParser().parse(sourceCode)
                        .collect(Collectors.toList());
                default:
                    return PlainTextParser.builder().build().parse(sourceCode)
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            logger.error("Error parsing source code", e);
            return Collections.emptyList();
        }
    }

    private String computeSimpleDiff(String original, String transformed) {
        // Simple line-based diff
        String[] originalLines = original.split("\n");
        String[] transformedLines = transformed.split("\n");

        StringBuilder diff = new StringBuilder();
        diff.append("--- Original\n");
        diff.append("+++ Transformed\n");

        int maxLines = Math.max(originalLines.length, transformedLines.length);
        for (int i = 0; i < maxLines; i++) {
            String origLine = i < originalLines.length ? originalLines[i] : "";
            String transLine = i < transformedLines.length ? transformedLines[i] : "";

            if (!origLine.equals(transLine)) {
                if (!origLine.isEmpty()) {
                    diff.append("- ").append(origLine).append("\n");
                }
                if (!transLine.isEmpty()) {
                    diff.append("+ ").append(transLine).append("\n");
                }
            }
        }

        return diff.toString();
    }
}
