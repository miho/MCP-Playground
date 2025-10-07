# Fixed: UnsupportedOperationException

## The Problem
The `apply_recipe_to_file` method was throwing `UnsupportedOperationException` when trying to add file information to the result map.

## Root Cause
The `applyRecipe` method returns results using `Map.of()` which creates an **immutable map** in Java. When we tried to add additional fields like `filePath` and `fileSize` with `result.put()`, it threw `UnsupportedOperationException` because immutable maps cannot be modified.

## The Fix
Changed the code to create a new mutable HashMap from the recipe result:

### Before (Wrong):
```java
// Apply the recipe
Map<String, Object> result = applyRecipe(sourceCode, recipeName, language, options);

// This fails - can't modify immutable map!
result.put("filePath", filePath);
result.put("fileSize", sourceCode.length());
```

### After (Fixed):
```java
// Apply the recipe
Map<String, Object> recipeResult = applyRecipe(sourceCode, recipeName, language, options);

// Create a mutable result map
Map<String, Object> result = new HashMap<>(recipeResult);

// Now we can add fields
result.put("filePath", filePath);
result.put("fileSize", sourceCode.length());
```

## All Issues Now Fixed

1. ✅ **Missing tools** - All 8 tools are registered
2. ✅ **Path handling** - Windows paths work correctly (no conversion needed)
3. ✅ **Immutable map** - Results can now be modified

## To Apply

1. Stop all processes:
   ```bash
   ./gradlew --stop
   ```

2. Rebuild:
   ```bash
   ./gradlew clean build
   ```

3. Restart JavaFX:
   ```bash
   ./gradlew run
   ```

The `apply_recipe_to_file` tool should now work correctly!