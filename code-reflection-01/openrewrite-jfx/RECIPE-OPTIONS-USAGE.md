# Recipe Options - User Guide

## Overview

This guide explains how to use the interactive recipe options feature in the OpenRewrite JavaFX application to customize recipe behavior.

## Features

- Interactive input controls for recipe configuration
- Type-aware input validation
- Default value support
- Required option validation
- Real-time feedback

## How to Use Recipe Options

### Step 1: Select a Recipe

1. Launch the OpenRewrite JavaFX application
2. Wait for recipes to load from the MCP server
3. Browse or search for a recipe in the recipe list
4. Click on a recipe to select it

### Step 2: View Recipe Options

When you select a recipe, the Recipe Details pane will show:

- Recipe name and description
- Tags
- **Options section** (if the recipe has configurable options)

If a recipe has options, you'll see:
- Option name with asterisk (*) for required options
- Option type in parentheses (String, Integer, Boolean, etc.)
- Description of what the option does
- Input control appropriate for the option type

### Step 3: Configure Options

Based on the option type, you'll see different input controls:

#### Boolean Options
- **Control:** Checkbox
- **How to use:** Click to toggle between true/false
- **Example:** Enable/disable a specific transformation

#### String Options
- **Control:** Text field
- **How to use:** Type text value
- **Example:** Package name, class name, custom string

#### Integer/Long Options
- **Control:** Text field with numeric validation
- **How to use:** Type numeric value (only digits allowed)
- **Example:** Maximum iterations, threshold value

#### Double/Float Options
- **Control:** Text field with decimal validation
- **How to use:** Type decimal number
- **Example:** Percentage, ratio, precision value

#### List/Array Options
- **Control:** Multi-line text area
- **How to use:** Enter one value per line
- **Example:** List of packages to exclude, list of patterns

### Step 4: Apply Recipe

1. Enter or load source code in the Source Code editor
2. Verify all required options (marked with *) have values
3. Click the "Transform" button
4. If required options are missing, you'll see an error message
5. Once validation passes, the recipe will be applied with your options
6. View the transformed code and diff

## Example Scenarios

### Scenario 1: Simple String Option

**Recipe:** ChangePackage
**Options:**
- `oldPackageName` (String) * - The package to rename
- `newPackageName` (String) * - The new package name

**Steps:**
1. Select "ChangePackage" recipe
2. Enter "com.example.old" in oldPackageName field
3. Enter "com.example.new" in newPackageName field
4. Click Transform

### Scenario 2: Boolean Flag Option

**Recipe:** RemoveUnusedImports
**Options:**
- `removeStarImports` (Boolean) - Remove wildcard imports

**Steps:**
1. Select "RemoveUnusedImports" recipe
2. Check the box if you want to remove wildcard imports
3. Leave unchecked to keep wildcard imports
4. Click Transform

### Scenario 3: List Option

**Recipe:** AddDependency
**Options:**
- `dependencies` (List<String>) * - List of dependencies to add

**Steps:**
1. Select "AddDependency" recipe
2. In the text area, enter:
   ```
   org.springframework.boot:spring-boot-starter-web:3.0.0
   org.springframework.boot:spring-boot-starter-data-jpa:3.0.0
   ```
3. Click Transform

### Scenario 4: Numeric Option

**Recipe:** OptimizeImports
**Options:**
- `maxImports` (Integer) - Maximum number of imports before using wildcard

**Steps:**
1. Select "OptimizeImports" recipe
2. Enter "5" in the maxImports field
3. Click Transform

## Input Validation

### Automatic Validation

- **Numeric fields:** Only allow digits and decimal points
- **Required fields:** Cannot apply recipe if empty
- **Type checking:** Values are converted to appropriate Java types

### Validation Errors

If you try to apply a recipe without filling required options, you'll see an alert:

```
Invalid Options
Required options missing: oldPackageName, newPackageName
```

Simply fill in the missing values and try again.

## Default Values

Some options have default values:

- If a default value exists, it will be pre-filled in the input control
- You can modify or clear the default value
- Empty optional fields will not be sent to the recipe

## Tips and Best Practices

1. **Read Option Descriptions:** Hover over or read the description to understand what each option does

2. **Start with Defaults:** If default values are provided, they're usually sensible starting points

3. **Required vs Optional:**
   - Required options (marked with *) must be filled
   - Optional options can be left empty

4. **List Values:**
   - Enter one value per line
   - Empty lines are ignored
   - Leading/trailing spaces are trimmed

5. **Numeric Values:**
   - Enter only valid numbers
   - For decimals, use a dot (.) as separator
   - Invalid input will be rejected automatically

6. **Boolean Options:**
   - Unchecked = false
   - Checked = true
   - Default value (if any) is pre-selected

## Troubleshooting

### Problem: Options not showing

**Possible causes:**
- Recipe has no configurable options
- Recipe metadata not properly loaded

**Solution:**
- Try refreshing recipes
- Check that MCP server is running

### Problem: Cannot type in numeric field

**Cause:** Input validation preventing non-numeric characters

**Solution:** Only enter digits (and decimal point for float/double)

### Problem: Recipe applied but options seem ignored

**Possible causes:**
- Option name mismatch
- Recipe doesn't support dynamic configuration
- Value type conversion failed

**Solution:**
- Check application logs for warnings
- Verify option values are correct type
- Some recipes may have fixed behavior

### Problem: "Required options missing" error

**Cause:** One or more required options (marked with *) are empty

**Solution:** Fill in all required options before clicking Transform

## Advanced Usage

### Checking Logs

For debugging, check the application logs:
- Options being sent are logged with INFO level
- Setter invocation logged with DEBUG level
- Conversion errors logged with WARN level

### Custom Recipes

If you create custom recipes with configurable options:

1. Define options in recipe metadata
2. Provide getter/setter methods following Java Bean convention
3. Options will automatically appear in the UI
4. Users can configure your recipe through the UI

## Technical Details

### Option Types Supported

| Type | Java Type | UI Control |
|------|-----------|-----------|
| String | String | TextField |
| Integer | int/Integer | TextField (numeric) |
| Long | long/Long | TextField (numeric) |
| Double | double/Double | TextField (decimal) |
| Float | float/Float | TextField (decimal) |
| Boolean | boolean/Boolean | CheckBox |
| List | List\<String\> | TextArea (multi-line) |

### Data Flow

```
UI Input → Validation → Type Conversion → MCP Request →
OpenRewrite Engine → Recipe Configuration → Transformation
```

## Feedback and Issues

If you encounter problems:

1. Check this guide for common issues
2. Review application logs
3. Verify recipe metadata is correct
4. Report issues with:
   - Recipe name
   - Option name and type
   - Expected vs actual behavior
   - Log excerpts (if available)

## Examples by Recipe Type

### Migration Recipes

Example: **UpgradeJava**
- `targetVersion` (String) - Java version to migrate to
- Usage: Select recipe, enter "17" or "21", apply

### Best Practices Recipes

Example: **RenameVariable**
- `oldName` (String) * - Current variable name
- `newName` (String) * - New variable name
- Usage: Both fields required, enter old and new names

### Cleanup Recipes

Example: **RemoveUnusedCode**
- `removePrivateMethods` (Boolean) - Remove unused private methods
- `removeFields` (Boolean) - Remove unused fields
- Usage: Check boxes for desired cleanup level

### Custom Transformation Recipes

Example: **ReplaceTextInComments**
- `oldText` (String) * - Text to find
- `newText` (String) * - Replacement text
- `caseSensitive` (Boolean) - Case-sensitive matching
- Usage: Fill strings, set case sensitivity, apply

## Conclusion

The recipe options feature makes OpenRewrite recipes more flexible and powerful. By providing appropriate values for recipe options, you can fine-tune transformations to your specific needs without writing code.

Remember:
- Options are recipe-specific
- Required options must be filled
- Type validation helps prevent errors
- Default values provide good starting points
- Logs show what's happening behind the scenes

Happy refactoring!
