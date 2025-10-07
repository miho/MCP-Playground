# Text Blocks Recipe - What It Should Do

## What Are Text Blocks?

Text blocks are a Java 15+ feature that allows multi-line strings without concatenation:

### Before (String Concatenation):
```java
String json = "{\n" +
              "  \"name\": \"example\",\n" +
              "  \"version\": \"1.0\"\n" +
              "}";
```

### After (Text Block):
```java
String json = """
    {
      "name": "example",
      "version": "1.0"
    }""";
```

## Why The Recipe May Not Work

### 1. **Java Version Requirement**
- Text blocks were introduced in Java 15 (preview in 13-14)
- The parser needs to be configured for Java 15+
- The runtime JVM must support text blocks

### 2. **Recipe Preconditions**
The `UseTextBlocks` recipe typically only transforms strings that:
- Span multiple lines (contain `\n`)
- Use concatenation with `+`
- Are string literals (not variables)

### 3. **Parser Configuration Issue**
Our current parser setup:
```java
JavaParser.fromJavaVersion()
    .build()
```

This uses the runtime JVM version, but may not enable language features properly.

## Solution Attempts

### Option 1: Correct Recipe Name
The recipe might be one of:
- `org.openrewrite.java.migrate.lang.UseTextBlocks`
- `org.openrewrite.java.migrate.Java15`
- `org.openrewrite.java.format.UseTextBlocks`

### Option 2: Enable Preview Features
Some Java features need preview mode:
```java
JavaParser.fromJavaVersion()
    .styles(List.of(new TabsAndIndentsStyle()))
    .build()
```

### Option 3: Use Java Migration Recipe
Instead of targeting text blocks directly, use:
- `org.openrewrite.java.migrate.UpgradeToJava15` or
- `org.openrewrite.java.migrate.UpgradeToJava17`

These migration recipes handle multiple transformations including text blocks.

## Testing Strategy

1. **Verify Recipe Exists**: Check if the text blocks recipe is in the available recipes list
2. **Test Simple Case**: Try with the simplest possible string concatenation
3. **Check Logs**: Enable logging to see if the recipe is finding patterns but not transforming

## Alternative Approach

If text blocks don't work, try simpler transformations first:
1. `SimplifyBooleanExpression` - Very basic AST transformation
2. `UseStringReplace` - Simple method replacement
3. `UseDiamondOperator` - Generic type inference

These should definitely work and will help verify the core transformation engine is functioning.

## The Real Issue

The core problem is likely that:
1. **The recipe exists but has strict preconditions** that aren't being met
2. **The parser isn't configured for the target Java version** needed for the transformation
3. **The recipe needs project context** (like pom.xml or build.gradle) to determine target Java version

Without proper Java 15+ parsing support, the recipe can't recognize where text blocks would be valid.