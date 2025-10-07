# Issue: Recipes Not Making Changes

## Problem
Recipes like `UpgradeToJava17` and `UseTextBlocks` are not making any changes to the code, even though the code has patterns that should be transformed.

## Possible Causes

### 1. Java Language Level
Some recipes (especially modern Java features) require the parser to be configured with the appropriate Java version:
- `UseTextBlocks` requires Java 15+
- `UpgradeToJava17` requires understanding of Java 17 features

**Current Setup**: Using `JavaParser.fromJavaVersion()` which auto-detects from runtime JVM.

### 2. Recipe Configuration
Some recipes may require:
- Project context (build files, dependencies)
- Specific options to be set
- Multiple files to understand the full context

### 3. Recipe Preconditions
Recipes may have preconditions that aren't being met:
- Source compatibility level in project
- Specific imports or dependencies
- File structure requirements

## What Works vs What Doesn't

### Should Work (Simple Transformations)
- `SimplifyBooleanExpression` on BooleanSimplification.java
- `UseStringReplace` on StringOperations.java
- `UseDiamondOperator` on CollectionOperations.java

### May Not Work (Complex/Project-Wide)
- `UpgradeToJava17` - Requires project context
- `UseTextBlocks` - Requires Java 15+ language level
- Migration recipes - Need full project understanding

## Debugging Steps

1. **Test with Simple Recipes First**
   - Try `SimplifyBooleanExpression` on BooleanSimplification.java
   - This should definitely work as it's a simple AST transformation

2. **Check Recipe Execution**
   - Add more logging to see if recipes are finding matches
   - Check if `results.isEmpty()` is always true

3. **Verify Parser Configuration**
   - The parser may need explicit Java version configuration
   - Consider using a different parser builder method

## Solutions to Try

### Solution 1: Explicit Java Version
```java
JavaParser.fromJavaVersion()
    .setSourceClasspath(Collections.emptyList())
    .build()
```

### Solution 2: Use Recipe with Options
Some recipes may need options to be enabled:
```java
Map<String, Object> options = Map.of(
    "compileClasspath", Collections.emptyList()
);
```

### Solution 3: Add Project Context
For migration recipes, might need to parse multiple files together:
- Include pom.xml or build.gradle
- Parse all Java files in the project
- Provide classpath information

## Next Steps

1. **Restart the application** with current fixes
2. **Test with SimplifyBooleanExpression** first (simplest case)
3. If that doesn't work, add detailed logging to see what's happening
4. If simple recipes work but complex ones don't, it's a context/configuration issue