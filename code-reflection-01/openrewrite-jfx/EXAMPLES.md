# OpenRewrite MCP Server - Code Examples

This directory contains sample code files that demonstrate common patterns that can be improved using OpenRewrite recipes. These examples are designed to be used with the OpenRewrite MCP Server and JavaFX UI.

## Directory Structure

```
examples/
├── java/
│   ├── BooleanSimplification.java
│   ├── StringOperations.java
│   ├── CollectionOperations.java
│   ├── NullHandling.java
│   └── ModernJava.java
├── maven/
│   └── pom.xml
└── gradle/
    └── build.gradle
```

## Java Examples

### 1. BooleanSimplification.java

**Purpose**: Demonstrates complex boolean expressions that can be simplified.

**Common Issues**:
- Redundant boolean comparisons (`== true`, `== false`)
- Unnecessary ternary operators returning boolean literals
- Complex negations that can be simplified
- Double negations
- Redundant if-else structures

**Recommended Recipes**:
```bash
# Simplify boolean expressions
org.openrewrite.java.cleanup.SimplifyBooleanExpression
org.openrewrite.java.cleanup.SimplifyBooleanReturn
org.openrewrite.staticanalysis.SimplifyTernary
```

**Example Usage via UI**:
1. Load the file in the UI
2. Click "Analyze Code" to find applicable recipes
3. Apply "SimplifyBooleanExpression" recipe
4. Review the changes in the diff viewer

**Example Usage via MCP**:
```json
// Using file-based operation
{
  "tool": "apply_recipe_to_file",
  "filePath": "examples/java/BooleanSimplification.java",
  "recipeName": "org.openrewrite.java.cleanup.SimplifyBooleanExpression",
  "saveChanges": false
}
```

### 2. StringOperations.java

**Purpose**: Demonstrates string operations that can be improved.

**Common Issues**:
- Using `replaceAll()` with plain text (should use `replace()`)
- Inefficient string concatenation in loops
- Using StringBuilder for simple concatenation
- Unnecessary `String.valueOf()` on strings
- Concatenating string literals
- Using `String.format()` for simple concatenation
- Checking empty with `length() == 0`

**Recommended Recipes**:
```bash
# String improvements
org.openrewrite.java.cleanup.UseStringReplace
org.openrewrite.java.cleanup.ReplaceStringBuilderWithString
org.openrewrite.staticanalysis.UseStringConcatenation
org.openrewrite.java.cleanup.CombineStringLiterals
```

**Example Transformations**:
- `text.replaceAll(" ", "_")` → `text.replace(" ", "_")`
- `str.length() == 0` → `str.isEmpty()`
- `"=== " + "Header" + " ==="` → `"=== Header ==="`

### 3. CollectionOperations.java

**Purpose**: Demonstrates collection operations that can be modernized.

**Common Issues**:
- Using concrete types instead of interfaces
- Manual collection initialization instead of factory methods
- Old-style empty collection constants
- Manual contains check before add
- Using iterator instead of enhanced for loop
- Not using Stream API where appropriate
- Using `size() == 0` instead of `isEmpty()`

**Recommended Recipes**:
```bash
# Collection modernization
org.openrewrite.java.cleanup.UseCollectionInterfaces
org.openrewrite.java.migrate.util.UseMapOf
org.openrewrite.java.migrate.util.UseListOf
org.openrewrite.staticanalysis.UseCollectionIsEmpty
```

**Example Transformations**:
- `ArrayList<String> names` → `List<String> names`
- `new ArrayList<String>()` → `new ArrayList<>()`
- `Collections.EMPTY_LIST` → `Collections.emptyList()`
- `collection.size() != 0` → `!collection.isEmpty()`

### 4. NullHandling.java

**Purpose**: Demonstrates null handling patterns that can be improved.

**Common Issues**:
- Manual null checking without braces
- Comparing with null on wrong side
- Using `==` for object comparison
- Not using `Objects.equals()`
- Missing braces in if statements
- Complex nested null checks

**Recommended Recipes**:
```bash
# Null handling improvements
org.openrewrite.java.cleanup.UseObjectEquals
org.openrewrite.java.cleanup.EqualsAvoidsNull
org.openrewrite.staticanalysis.NeedBraces
org.openrewrite.staticanalysis.SimplifyMethodChain
```

### 5. ModernJava.java

**Purpose**: Demonstrates code that can be migrated to modern Java features.

**Common Issues**:
- Not using diamond operator
- Old-style try-finally for resource management
- String concatenation instead of text blocks
- Not using `var` for local variables
- Old switch statements
- Anonymous classes that could be lambdas
- Missing method references

**Recommended Recipes**:
```bash
# Java modernization
org.openrewrite.java.migrate.UpgradeToJava11
org.openrewrite.java.migrate.UpgradeToJava17
org.openrewrite.java.cleanup.UseDiamondOperator
org.openrewrite.java.cleanup.UseTryWithResources
org.openrewrite.java.migrate.UseTextBlocks
```

## Maven Example (pom.xml)

**Purpose**: Demonstrates an outdated Maven POM that can be modernized.

**Common Issues**:
- Old Java compiler version (1.8)
- Outdated dependency versions
- JUnit 4 instead of JUnit 5
- Old Spring versions
- Outdated plugins

**Recommended Recipes**:
```bash
# Maven modernization
org.openrewrite.maven.UpgradePluginVersion
org.openrewrite.maven.UpgradeDependencyVersion
org.openrewrite.java.migrate.UpgradeToJava11
org.openrewrite.java.testing.junit5.JUnit4to5Migration
```

## Gradle Example (build.gradle)

**Purpose**: Demonstrates an outdated Gradle build that can be modernized.

**Common Issues**:
- Deprecated jcenter repository
- Old configuration names (compile, testCompile, runtime)
- Outdated dependency versions
- Old task definition syntax
- Old Gradle wrapper version

**Recommended Recipes**:
```bash
# Gradle modernization
org.openrewrite.gradle.UpdateGradleWrapper
org.openrewrite.gradle.plugins.UpgradePluginVersion
org.openrewrite.gradle.RemoveEnableFeaturePreview
```

## How to Use These Examples

### Via JavaFX UI

1. **Start the application**:
   ```bash
   ./gradlew run
   ```

2. **Load an example file**:
   - Click "Load Code" or paste the content
   - Select the appropriate language

3. **Analyze the code**:
   - Click "Analyze Code" to find applicable recipes
   - Review the suggested improvements

4. **Apply recipes**:
   - Select a recipe from the dropdown
   - Click "Apply Recipe"
   - Review changes in the diff viewer

### Via MCP Tools

1. **Start the MCP server**:
   ```bash
   ./start-mcp-server.sh  # or .bat on Windows
   ```

2. **Analyze file structure** (without loading content):
   ```json
   {
     "tool": "analyze_file_structure",
     "filePath": "examples/java/BooleanSimplification.java"
   }
   ```

3. **List applicable recipes**:
   ```json
   {
     "tool": "list_recipes",
     "filter": "boolean"
   }
   ```

4. **Apply recipe to file**:
   ```json
   {
     "tool": "apply_recipe_to_file",
     "filePath": "examples/java/StringOperations.java",
     "recipeName": "org.openrewrite.java.cleanup.UseStringReplace",
     "saveChanges": true
   }
   ```

### Via Direct Code Analysis

```json
{
  "tool": "analyze_code",
  "sourceCode": "/* paste code here */",
  "language": "java"
}
```

## Testing Workflow

### Basic Testing Steps

1. **Test Boolean Simplification**:
   ```bash
   # Analyze the file
   analyze_file_structure examples/java/BooleanSimplification.java

   # Apply simplification
   apply_recipe_to_file BooleanSimplification.java SimplifyBooleanExpression
   ```

2. **Test String Operations**:
   ```bash
   # Find string-related recipes
   list_recipes filter:"string"

   # Apply improvements
   apply_recipe_to_file StringOperations.java UseStringReplace
   ```

3. **Test Collection Modernization**:
   ```bash
   # Analyze collections code
   analyze_code CollectionOperations.java

   # Apply modern patterns
   apply_recipe_to_file CollectionOperations.java UseCollectionInterfaces
   ```

### Batch Processing

You can process multiple files at once:

```javascript
const files = [
  "BooleanSimplification.java",
  "StringOperations.java",
  "CollectionOperations.java"
];

files.forEach(file => {
  // Analyze each file
  analyzeFileStructure(`examples/java/${file}`);

  // Apply cleanup recipes
  applyRecipeToFile(`examples/java/${file}`,
    "org.openrewrite.java.cleanup.CommonStaticAnalysis");
});
```

## Expected Results

### Before and After Examples

#### Boolean Simplification
**Before**:
```java
if (isActive == true) {
    return true;
} else {
    return false;
}
```

**After**:
```java
return isActive;
```

#### String Operations
**Before**:
```java
text.replaceAll(" ", "_").replaceAll("-", "_");
```

**After**:
```java
text.replace(" ", "_").replace("-", "_");
```

#### Collections
**Before**:
```java
ArrayList<String> names = new ArrayList<String>();
```

**After**:
```java
List<String> names = new ArrayList<>();
```

## Recipe Categories

### Cleanup Recipes
- `SimplifyBooleanExpression`
- `UseStringReplace`
- `UseCollectionInterfaces`
- `NeedBraces`
- `UseDiamondOperator`

### Migration Recipes
- `UpgradeToJava11`
- `UpgradeToJava17`
- `JUnit4to5Migration`

### Static Analysis
- `CommonStaticAnalysis`
- `CodeCleanup`
- `RemoveUnusedImports`

## Troubleshooting

### Common Issues

1. **Recipe not found**:
   - Use `list_recipes` with a filter to find the exact name
   - Check that the recipe is available in your OpenRewrite version

2. **No changes detected**:
   - The code might already follow best practices
   - Try a different recipe or analyze the code first

3. **Compilation errors after transformation**:
   - Some recipes require additional configuration
   - Check recipe options and dependencies

## Contributing

To add more examples:

1. Create a new file in the appropriate directory
2. Add comments explaining the issues
3. List recommended recipes in the file header
4. Update this documentation

## Resources

- [OpenRewrite Documentation](https://docs.openrewrite.org/)
- [Recipe Catalog](https://docs.openrewrite.org/recipes)
- [MCP Server Documentation](./README.md)