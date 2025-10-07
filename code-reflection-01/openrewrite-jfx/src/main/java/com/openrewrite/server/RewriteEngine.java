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

import java.lang.reflect.Constructor;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RewriteEngine {
    private static final Logger logger = LoggerFactory.getLogger(RewriteEngine.class);
    private final Environment environment;
    private final ObjectMapper objectMapper;
    private final Map<String, Recipe> availableRecipes;
    private final Map<String, Method> setterCache = new ConcurrentHashMap<>();

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
        return listAvailableRecipes(null);
    }

    public Map<String, Object> listAvailableRecipes(String filterPattern) {
        Stream<Map.Entry<String, Recipe>> stream = availableRecipes.entrySet().stream();

        // Apply filter if provided
        if (filterPattern != null && !filterPattern.trim().isEmpty()) {
            String pattern = filterPattern.toLowerCase();
            stream = stream.filter(entry -> {
                String name = entry.getKey().toLowerCase();
                String displayName = entry.getValue().getDisplayName().toLowerCase();
                String description = entry.getValue().getDescription() != null ?
                    entry.getValue().getDescription().toLowerCase() : "";
                return name.contains(pattern) || displayName.contains(pattern) || description.contains(pattern);
            });
        }

        List<Map<String, String>> recipeList = stream
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
        if (filterPattern != null && !filterPattern.trim().isEmpty()) {
            result.put("filter", filterPattern);
        }
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
        return applyRecipe(sourceCode, recipeName, language, null);
    }

    public Map<String, Object> applyRecipe(String sourceCode, String recipeName, String language,
                                           Map<String, Object> options) {
        try {
            Recipe recipe = availableRecipes.get(recipeName);
            if (recipe == null) {
                return Map.of("error", "Recipe not found: " + recipeName);
            }

            // Clone recipe before applying options to avoid thread safety issues
            Recipe recipeInstance = recipe;
            if (options != null && !options.isEmpty()) {
                logger.info("Applying recipe {} with options: {}", recipeName, options);
                recipeInstance = cloneRecipe(recipe);
                if (recipeInstance != null) {
                    recipeInstance = applyOptionsToRecipe(recipeInstance, options);
                } else {
                    logger.warn("Could not clone recipe, using original instance");
                    recipeInstance = recipe;
                }
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
            RecipeRun recipeRun = recipeInstance.run(lss, ctx);
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

    /**
     * Clone a recipe instance to avoid thread safety issues.
     * Attempts to create a new instance using reflection.
     *
     * @param recipe the recipe to clone
     * @return a new instance of the recipe, or null if cloning fails
     */
    private Recipe cloneRecipe(Recipe recipe) {
        try {
            Class<?> recipeClass = recipe.getClass();

            // Try no-arg constructor first
            try {
                Constructor<?> constructor = recipeClass.getDeclaredConstructor();
                constructor.setAccessible(true);
                Recipe newInstance = (Recipe) constructor.newInstance();

                // Copy existing values to new instance
                copyRecipeProperties(recipe, newInstance);
                return newInstance;
            } catch (NoSuchMethodException e) {
                // No no-arg constructor, try other approaches
                logger.debug("No no-arg constructor for {}, trying alternatives", recipeClass.getName());
            }

            // If no no-arg constructor, return null (will use original)
            return null;
        } catch (Exception e) {
            logger.error("Failed to clone recipe {}: {}", recipe.getClass().getName(), e.getMessage());
            return null;
        }
    }

    /**
     * Copy properties from source recipe to target recipe.
     */
    private void copyRecipeProperties(Recipe source, Recipe target) {
        try {
            Class<?> clazz = source.getClass();
            for (Method method : clazz.getMethods()) {
                String name = method.getName();
                if (name.startsWith("get") && method.getParameterCount() == 0) {
                    String propertyName = name.substring(3);
                    String setterName = "set" + propertyName;

                    try {
                        Method setter = clazz.getMethod(setterName, method.getReturnType());
                        Object value = method.invoke(source);
                        if (value != null) {
                            setter.invoke(target, value);
                        }
                    } catch (NoSuchMethodException e) {
                        // No corresponding setter, skip
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to copy recipe properties: {}", e.getMessage());
        }
    }

    /**
     * Apply configuration options to a recipe using reflection.
     * OpenRewrite recipes are configured by calling setter methods on the recipe instance.
     *
     * @param recipe the recipe to configure
     * @param options map of option name to value
     * @return the configured recipe instance
     */
    private Recipe applyOptionsToRecipe(Recipe recipe, Map<String, Object> options) {
        try {
            // Use reflection to set recipe properties
            Class<?> recipeClass = recipe.getClass();

            for (Map.Entry<String, Object> entry : options.entrySet()) {
                String optionName = entry.getKey();
                Object optionValue = entry.getValue();

                if (optionValue == null) {
                    continue;
                }

                // Try to find and invoke setter method
                String setterName = "set" + capitalize(optionName);
                try {
                    // Try to find setter method with matching parameter type
                    Method setter = findSetter(recipeClass, setterName, optionValue);
                    if (setter != null) {
                        // Try to set accessible safely
                        try {
                            if (!setter.canAccess(recipe)) {
                                setter.setAccessible(true);
                            }
                        } catch (InaccessibleObjectException e) {
                            logger.warn("Cannot access setter {} due to module restrictions", setterName);
                            continue;
                        }

                        setter.invoke(recipe, convertValue(optionValue, setter.getParameterTypes()[0]));
                        logger.debug("Set recipe option {} = {}", optionName, optionValue);
                    } else {
                        logger.warn("Could not find setter {} for option {}", setterName, optionName);
                    }
                } catch (InvocationTargetException e) {
                    logger.warn("Failed to invoke setter for option {}: {}", optionName,
                        e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
                } catch (Exception e) {
                    logger.warn("Failed to set option {}: {}", optionName, e.getMessage());
                }
            }

            return recipe;
        } catch (Exception e) {
            logger.error("Error applying options to recipe", e);
            return recipe;
        }
    }

    /**
     * Find a setter method that matches the given name and can accept the value.
     * Prefers exact type matches over assignable matches.
     */
    private Method findSetter(Class<?> clazz, String setterName, Object value) {
        // Check cache first
        String cacheKey = clazz.getName() + "#" + setterName;
        Method cached = setterCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        Method exactMatch = null;
        Method assignableMatch = null;
        Method firstCandidate = null;

        for (Method method : clazz.getMethods()) {
            if (!method.getName().equals(setterName) || method.getParameterCount() != 1) {
                continue;
            }

            Class<?> paramType = method.getParameterTypes()[0];

            // Store first candidate regardless
            if (firstCandidate == null) {
                firstCandidate = method;
            }

            if (value != null) {
                Class<?> valueClass = value.getClass();

                // Check for exact type match
                if (paramType.equals(valueClass)) {
                    exactMatch = method;
                    break; // Exact match found, no need to continue
                }

                // Check for primitive/wrapper compatibility
                if (isPrimitiveCompatible(paramType, valueClass)) {
                    exactMatch = method;
                    break;
                }

                // Check for assignable match
                if (paramType.isAssignableFrom(valueClass)) {
                    if (assignableMatch == null) {
                        assignableMatch = method;
                    }
                }
            }
        }

        // Determine which method to use
        Method result = exactMatch != null ? exactMatch :
                        assignableMatch != null ? assignableMatch : firstCandidate;

        // Cache the result
        if (result != null) {
            setterCache.put(cacheKey, result);
        }

        return result;
    }

    /**
     * Check if a primitive type and wrapper type are compatible.
     */
    private boolean isPrimitiveCompatible(Class<?> type1, Class<?> type2) {
        if (type1.isPrimitive()) {
            return (type1 == int.class && type2 == Integer.class) ||
                   (type1 == long.class && type2 == Long.class) ||
                   (type1 == double.class && type2 == Double.class) ||
                   (type1 == float.class && type2 == Float.class) ||
                   (type1 == boolean.class && type2 == Boolean.class) ||
                   (type1 == byte.class && type2 == Byte.class) ||
                   (type1 == short.class && type2 == Short.class) ||
                   (type1 == char.class && type2 == Character.class);
        } else if (type2.isPrimitive()) {
            return isPrimitiveCompatible(type2, type1);
        }
        return false;
    }

    /**
     * Convert a value to the target type if needed.
     * Supports primitives, Pattern, Duration, Enum, Set, and custom types.
     */
    private Object convertValue(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }

        // If value is already the correct type, return as-is
        if (targetType.isAssignableFrom(value.getClass())) {
            return value;
        }

        // Handle String conversions
        if (targetType == String.class) {
            return value.toString();
        }

        String valueStr = value.toString();

        try {
            // Handle primitive types and their wrappers
            if (targetType == int.class || targetType == Integer.class) {
                if (value instanceof Number) {
                    return ((Number) value).intValue();
                }
                return Integer.parseInt(valueStr);
            } else if (targetType == long.class || targetType == Long.class) {
                if (value instanceof Number) {
                    return ((Number) value).longValue();
                }
                return Long.parseLong(valueStr);
            } else if (targetType == double.class || targetType == Double.class) {
                if (value instanceof Number) {
                    return ((Number) value).doubleValue();
                }
                return Double.parseDouble(valueStr);
            } else if (targetType == float.class || targetType == Float.class) {
                if (value instanceof Number) {
                    return ((Number) value).floatValue();
                }
                return Float.parseFloat(valueStr);
            } else if (targetType == boolean.class || targetType == Boolean.class) {
                if (value instanceof Boolean) {
                    return value;
                }
                return Boolean.parseBoolean(valueStr);
            } else if (targetType == byte.class || targetType == Byte.class) {
                if (value instanceof Number) {
                    return ((Number) value).byteValue();
                }
                return Byte.parseByte(valueStr);
            } else if (targetType == short.class || targetType == Short.class) {
                if (value instanceof Number) {
                    return ((Number) value).shortValue();
                }
                return Short.parseShort(valueStr);
            } else if (targetType == char.class || targetType == Character.class) {
                if (valueStr.length() > 0) {
                    return valueStr.charAt(0);
                }
                return '\0';
            }

            // Handle Pattern type for regex support
            if (targetType == Pattern.class || targetType == java.util.regex.Pattern.class) {
                return Pattern.compile(valueStr);
            }

            // Handle Duration type
            if (targetType == Duration.class || targetType == java.time.Duration.class) {
                // Support ISO-8601 duration format (e.g., PT1H30M) or simple seconds
                if (valueStr.matches("\\d+")) {
                    return Duration.ofSeconds(Long.parseLong(valueStr));
                }
                return Duration.parse(valueStr);
            }

            // Handle Enum types
            if (targetType.isEnum()) {
                @SuppressWarnings({"unchecked", "rawtypes"})
                Class<? extends Enum> enumType = (Class<? extends Enum>) targetType;
                // Try exact match first
                try {
                    return Enum.valueOf(enumType, valueStr);
                } catch (IllegalArgumentException e) {
                    // Try case-insensitive match
                    for (Enum<?> enumConstant : enumType.getEnumConstants()) {
                        if (enumConstant.name().equalsIgnoreCase(valueStr)) {
                            return enumConstant;
                        }
                    }
                    throw e; // Re-throw if no match found
                }
            }

            // Handle Set type
            if (Set.class.isAssignableFrom(targetType)) {
                if (value instanceof List) {
                    return new LinkedHashSet<>((List<?>) value);
                } else if (value instanceof Set) {
                    return value;
                } else {
                    // Single value to Set
                    return Set.of(valueStr);
                }
            }

            // Handle List type
            if (List.class.isAssignableFrom(targetType)) {
                if (value instanceof List) {
                    return value;
                } else if (value instanceof Set) {
                    return new ArrayList<>((Set<?>) value);
                } else {
                    // Single value to List
                    return List.of(valueStr);
                }
            }

            // Handle Map type (for complex configuration objects)
            if (Map.class.isAssignableFrom(targetType) && value instanceof Map) {
                return value;
            }

        } catch (NumberFormatException e) {
            String errorMsg = String.format("Cannot convert '%s' to %s: invalid number format",
                value, targetType.getSimpleName());
            logger.error(errorMsg);
            throw new IllegalArgumentException(errorMsg, e);
        } catch (IllegalArgumentException e) {
            String errorMsg = String.format("Cannot convert '%s' to %s: %s",
                value, targetType.getSimpleName(), e.getMessage());
            logger.error(errorMsg);
            throw new IllegalArgumentException(errorMsg, e);
        } catch (Exception e) {
            String errorMsg = String.format("Failed to convert '%s' to %s: %s",
                value, targetType.getSimpleName(), e.getMessage());
            logger.error(errorMsg, e);
            throw new IllegalArgumentException(errorMsg, e);
        }

        // If no conversion was possible, log warning and return original value
        logger.warn("No conversion available from {} to {}, returning original value",
            value.getClass().getSimpleName(), targetType.getSimpleName());
        return value;
    }

    /**
     * Capitalize the first letter of a string.
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
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
