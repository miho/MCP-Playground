package com.openrewrite.server;

import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.config.RecipeDescriptor;
import org.openrewrite.config.OptionDescriptor;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility to find all recipes that have configurable options.
 */
public class RecipeOptionsFinder {

    public static void main(String[] args) {
        Environment environment = Environment.builder()
            .scanRuntimeClasspath()
            .build();

        Map<String, List<String>> recipesWithOptions = new TreeMap<>();
        int totalRecipes = 0;
        int recipesWithOptionsCount = 0;

        for (Recipe recipe : environment.listRecipes()) {
            totalRecipes++;
            String recipeName = recipe.getName();

            if (recipeName == null) continue;

            List<String> options = new ArrayList<>();

            // Check via RecipeDescriptor
            RecipeDescriptor descriptor = recipe.getDescriptor();
            if (descriptor != null && descriptor.getOptions() != null && !descriptor.getOptions().isEmpty()) {
                for (OptionDescriptor option : descriptor.getOptions()) {
                    String optionInfo = String.format("%s (%s)%s",
                        option.getName(),
                        option.getType(),
                        option.isRequired() ? " *required*" : "");
                    options.add(optionInfo);
                }
            }

            // Also check via reflection for @Option annotated fields
            Class<?> recipeClass = recipe.getClass();
            for (Field field : recipeClass.getDeclaredFields()) {
                if (field.isAnnotationPresent(org.openrewrite.Option.class)) {
                    org.openrewrite.Option optionAnnotation = field.getAnnotation(org.openrewrite.Option.class);
                    String optionInfo = String.format("%s (%s)%s - %s",
                        field.getName(),
                        field.getType().getSimpleName(),
                        optionAnnotation.required() ? " *required*" : "",
                        optionAnnotation.description());
                    if (!options.contains(optionInfo)) {
                        options.add(optionInfo);
                    }
                }
            }

            if (!options.isEmpty()) {
                recipesWithOptions.put(recipeName, options);
                recipesWithOptionsCount++;
            }
        }

        System.out.println("=== OPENREWRITE RECIPES WITH CONFIGURABLE OPTIONS ===\n");
        System.out.printf("Found %d recipes with options out of %d total recipes\n\n",
            recipesWithOptionsCount, totalRecipes);

        // Group by category
        Map<String, List<Map.Entry<String, List<String>>>> categorized = recipesWithOptions.entrySet().stream()
            .collect(Collectors.groupingBy(entry -> {
                String name = entry.getKey();
                if (name.contains(".java.migrate")) return "Java Migration";
                if (name.contains(".java.spring")) return "Spring";
                if (name.contains(".java.security")) return "Security";
                if (name.contains(".java.testing")) return "Testing";
                if (name.contains(".java.logging")) return "Logging";
                if (name.contains(".java.format")) return "Formatting";
                if (name.contains(".java.cleanup")) return "Cleanup";
                if (name.contains(".maven")) return "Maven";
                if (name.contains(".gradle")) return "Gradle";
                if (name.contains(".yaml") || name.contains(".xml") || name.contains(".json")) return "Configuration";
                if (name.contains(".kubernetes") || name.contains(".docker")) return "Container";
                return "Other";
            }));

        // Print categorized recipes
        for (Map.Entry<String, List<Map.Entry<String, List<String>>>> category : categorized.entrySet()) {
            System.out.println("\n### " + category.getKey() + " (" + category.getValue().size() + " recipes)");
            System.out.println("─".repeat(60));

            for (Map.Entry<String, List<String>> recipe : category.getValue()) {
                System.out.println("\n" + recipe.getKey());
                for (String option : recipe.getValue()) {
                    System.out.println("  • " + option);
                }
            }
        }

        // Print some example recipes with interesting options
        System.out.println("\n\n=== NOTABLE RECIPES WITH OPTIONS ===\n");

        String[] notableRecipes = {
            "org.openrewrite.java.migrate.UpgradeToJava",
            "org.openrewrite.java.spring.boot3.UpgradeSpringBoot",
            "org.openrewrite.java.ChangeType",
            "org.openrewrite.java.ChangeMethodName",
            "org.openrewrite.java.AddOrUpdateAnnotationAttribute",
            "org.openrewrite.maven.ChangePropertyValue",
            "org.openrewrite.java.ReplaceConstantWithAnotherConstant"
        };

        for (String pattern : notableRecipes) {
            recipesWithOptions.entrySet().stream()
                .filter(e -> e.getKey().contains(pattern))
                .findFirst()
                .ifPresent(entry -> {
                    System.out.println("• " + entry.getKey());
                    entry.getValue().forEach(opt -> System.out.println("    " + opt));
                    System.out.println();
                });
        }
    }
}