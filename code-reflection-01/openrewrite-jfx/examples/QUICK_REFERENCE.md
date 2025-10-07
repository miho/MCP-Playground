# OpenRewrite Quick Reference - Common Transformations

## Boolean Simplifications

| Before | After | Recipe |
|--------|-------|--------|
| `if (x == true)` | `if (x)` | SimplifyBooleanExpression |
| `return x ? true : false` | `return x` | SimplifyBooleanReturn |
| `if (x) return true; else return false;` | `return x;` | SimplifyBooleanReturn |
| `!(!x && !y)` | `x \|\| y` | SimplifyBooleanExpression |
| `x != false` | `x` | SimplifyBooleanExpression |

## String Operations

| Before | After | Recipe |
|--------|-------|--------|
| `str.replaceAll(" ", "_")` | `str.replace(" ", "_")` | UseStringReplace |
| `"a" + "b" + "c"` | `"abc"` | CombineStringLiterals |
| `String.valueOf(stringVar)` | `stringVar` | RemoveRedundantTypeCast |
| `str.length() == 0` | `str.isEmpty()` | UseStringIsEmpty |
| `new StringBuilder().append(a).append(b)` | `a + b` | ReplaceStringBuilderWithString |

## Collections

| Before | After | Recipe |
|--------|-------|--------|
| `ArrayList<String> list` | `List<String> list` | UseCollectionInterfaces |
| `new ArrayList<String>()` | `new ArrayList<>()` | UseDiamondOperator |
| `Collections.EMPTY_LIST` | `Collections.emptyList()` | PreferJavaUtilCollections |
| `list.size() == 0` | `list.isEmpty()` | UseCollectionIsEmpty |
| `if (!set.contains(x)) set.add(x)` | `set.add(x)` | SimplifySetOperations |

## Null Handling

| Before | After | Recipe |
|--------|-------|--------|
| `null == obj` | `obj == null` | EqualsAvoidsNull |
| `str1 == str2` (strings) | `str1.equals(str2)` | UseStringEquals |
| Manual null checks | `Objects.equals(a, b)` | UseObjectEquals |
| Missing braces | Added braces | NeedBraces |

## Modern Java

| Before | After | Recipe |
|--------|-------|--------|
| `new HashMap<String, String>()` | `new HashMap<>()` | UseDiamondOperator |
| Try-finally | Try-with-resources | UseTryWithResources |
| String concatenation | Text blocks (Java 15+) | UseTextBlocks |
| Anonymous class | Lambda | UseLambda |
| For loop | Stream API | UseStreamAPI |

## Common Recipe Commands

### List Recipes by Category
```bash
# Boolean operations
list_recipes filter:"boolean"

# String operations
list_recipes filter:"string"

# Collection operations
list_recipes filter:"collection"

# Java migration
list_recipes filter:"migrate"

# Cleanup recipes
list_recipes filter:"cleanup"
```

### Apply to Files
```bash
# Analyze file structure first
analyze_file_structure examples/java/BooleanSimplification.java

# Apply single recipe
apply_recipe_to_file {
  "filePath": "examples/java/BooleanSimplification.java",
  "recipeName": "org.openrewrite.java.cleanup.SimplifyBooleanExpression",
  "saveChanges": false
}

# Apply and save
apply_recipe_to_file {
  "filePath": "examples/java/StringOperations.java",
  "recipeName": "org.openrewrite.java.cleanup.UseStringReplace",
  "saveChanges": true
}
```

### Batch Analysis
```bash
# Find all applicable recipes for a file
analyze_code {
  "sourceCode": "/* file content */",
  "language": "java"
}

# List instrumentation recipes
list_instrumentation_recipes
```

## Most Useful Recipes

### Top 10 for Code Cleanup
1. `org.openrewrite.java.cleanup.CommonStaticAnalysis` - Applies many cleanups at once
2. `org.openrewrite.java.cleanup.SimplifyBooleanExpression`
3. `org.openrewrite.java.cleanup.UseStringReplace`
4. `org.openrewrite.java.cleanup.UseCollectionInterfaces`
5. `org.openrewrite.staticanalysis.NeedBraces`
6. `org.openrewrite.java.cleanup.UseDiamondOperator`
7. `org.openrewrite.java.cleanup.RemoveUnusedImports`
8. `org.openrewrite.java.cleanup.EqualsAvoidsNull`
9. `org.openrewrite.java.cleanup.ExplicitInitialization`
10. `org.openrewrite.java.cleanup.MethodNameCasing`

### Top 5 for Modernization
1. `org.openrewrite.java.migrate.UpgradeToJava17`
2. `org.openrewrite.java.migrate.UpgradeToJava11`
3. `org.openrewrite.java.testing.junit5.JUnit4to5Migration`
4. `org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_0`
5. `org.openrewrite.java.migrate.util.UseMapOf`

### Top 5 for Dependencies
1. `org.openrewrite.maven.UpgradeDependencyVersion`
2. `org.openrewrite.maven.UpgradePluginVersion`
3. `org.openrewrite.gradle.UpdateGradleWrapper`
4. `org.openrewrite.java.dependencies.UpgradeDependencyVersion`
5. `org.openrewrite.maven.RemoveDuplicateDependencies`