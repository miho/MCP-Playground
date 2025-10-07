# Recipe Options Interactive Implementation

## Overview

This document describes the implementation of interactive recipe options functionality in the OpenRewrite JavaFX application. Users can now configure recipe behavior through interactive UI controls before applying recipes.

## Implementation Summary

### 1. RecipeDetailsPane - Interactive Controls

**File:** `/mnt/c/Dev/repos/MCP-Playground/code-reflection-01/openrewrite-jfx/src/main/java/com/openrewrite/ui/components/RecipeDetailsPane.java`

#### Key Changes:

- **Added `Map<String, Control> optionControls`** - Stores references to input controls for each option
- **Enhanced `showRecipe()` method** - Creates interactive controls based on option types
- **Added `createInputControl()` method** - Factory method that creates appropriate JavaFX controls:
  - `CheckBox` for boolean options
  - `TextField` for string, integer, long, double, and float types with validation
  - `TextArea` for list/array types (one value per line)

#### Control Type Mapping:

| Option Type | JavaFX Control | Features |
|------------|---------------|----------|
| boolean | CheckBox | Shows checked/unchecked state |
| integer, int | TextField | Numeric validation, only allows digits |
| long | TextField | Numeric validation for long values |
| double, float | TextField | Decimal number validation |
| string | TextField | Plain text input |
| list, array | TextArea | Multi-line input, split by newlines |

#### New Methods:

1. **`getOptionValues(): Map<String, Object>`**
   - Retrieves current values from all option controls
   - Converts values to appropriate Java types based on option type
   - Returns empty map if no options or no values entered

2. **`extractValueFromControl(Control, String): Object`**
   - Extracts and parses values from different control types
   - Handles type conversion (string to int, double, etc.)
   - Returns null for empty values

3. **`validateOptions(): ValidationResult`**
   - Validates that all required options have values
   - Returns ValidationResult with success status and error message
   - Used before applying recipe to ensure required options are set

4. **`ValidationResult` inner class**
   - Simple result object with `isValid()` and `getErrorMessage()` methods
   - Used to communicate validation status to MainController

#### UI Enhancements:

- Required options marked with asterisk (*)
- Default values pre-populated in controls
- Type information displayed next to option name
- Input validation prevents invalid data entry
- Clear visual hierarchy with labels, descriptions, and inputs

### 2. McpClient - Options Parameter

**File:** `/mnt/c/Dev/repos/MCP-Playground/code-reflection-01/openrewrite-jfx/src/main/java/com/openrewrite/ui/model/McpClient.java`

#### Key Changes:

- **Added overloaded `applyRecipe()` method** accepting `Map<String, Object> options`
- Original method now delegates to new method with null options for backward compatibility
- Options are serialized as part of MCP request arguments
- Enhanced logging to show when options are being sent

#### MCP Request Structure:

```json
{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "params": {
    "name": "apply_recipe",
    "arguments": {
      "sourceCode": "...",
      "recipeName": "...",
      "language": "java",
      "options": {
        "optionName1": "value1",
        "optionName2": 42
      }
    }
  }
}
```

#### Recipe Parsing Enhancement:

- Enhanced `parseRecipe()` to extract default values from recipe options
- Handles different JSON value types (text, number, boolean, array)
- Default values used to pre-populate UI controls

### 3. MainController - Option Validation and Retrieval

**File:** `/mnt/c/Dev/repos/MCP-Playground/code-reflection-01/openrewrite-jfx/src/main/java/com/openrewrite/ui/MainController.java`

#### Key Changes in `applySelectedRecipe()`:

1. **Validation Step:**
   ```java
   RecipeDetailsPane.ValidationResult validation = recipeDetailsPane.validateOptions();
   if (!validation.isValid()) {
       showAlert("Invalid Options", validation.getErrorMessage());
       return;
   }
   ```

2. **Option Retrieval:**
   ```java
   Map<String, Object> options = recipeDetailsPane.getOptionValues();
   ```

3. **Pass Options to Client:**
   ```java
   CompletableFuture<TransformationResult> future = mcpClient.applyRecipe(
       sourceCode,
       selectedRecipe.getName(),
       "java",
       options  // Now passes options
   );
   ```

#### User Experience:

- Users cannot apply recipe with missing required options
- Clear error message shows which options are missing
- Options are logged for debugging
- Seamless integration with existing transformation workflow

### 4. ToolFactory - MCP Tool Schema Update

**File:** `/mnt/c/Dev/repos/MCP-Playground/code-reflection-01/openrewrite-jfx/src/main/java/com/openrewrite/server/ToolFactory.java`

#### Key Changes:

1. **Updated `apply_recipe` tool schema** to include options parameter:
   ```json
   "options": {
     "type": "object",
     "description": "Optional recipe configuration options as key-value pairs",
     "additionalProperties": true
   }
   ```

2. **Enhanced call handler** to extract and pass options:
   ```java
   Map<String, Object> options = null;
   if (args.containsKey("options") && args.get("options") instanceof Map) {
       options = (Map<String, Object>) args.get("options");
   }

   Map<String, Object> result = rewriteEngine.applyRecipe(
       sourceCode, recipeName, language, options);
   ```

### 5. RewriteEngine - Recipe Option Application

**File:** `/mnt/c/Dev/repos/MCP-Playground/code-reflection-01/openrewrite-jfx/src/main/java/com/openrewrite/server/RewriteEngine.java`

#### Key Changes:

1. **Added overloaded `applyRecipe()` method** accepting options parameter

2. **New `applyOptionsToRecipe()` method** - Core option application logic:
   - Uses Java reflection to find and invoke setter methods on recipe instances
   - Converts option names to setter names (e.g., "myOption" → "setMyOption")
   - Handles type conversion for primitive and object types
   - Gracefully handles missing setters or conversion errors

3. **Helper Methods:**

   - **`findSetter(Class, String, Object)`** - Locates appropriate setter method
   - **`convertValue(Object, Class)`** - Type conversion utility:
     - String to primitives (int, long, double, float, boolean)
     - List handling
     - Type-safe conversion with fallback
   - **`capitalize(String)`** - Capitalizes first letter for setter name construction

#### Reflection-Based Configuration:

OpenRewrite recipes are configured using Java Bean-style setter methods. The implementation:

1. Iterates through provided options
2. Constructs setter method name from option name
3. Finds matching setter using reflection
4. Converts value to expected parameter type
5. Invokes setter with converted value
6. Logs success or warnings for each option

**Example:**
```java
// Option: "maxIterations" with value 5
// Setter: setMaxIterations(int)
// Result: recipe.setMaxIterations(5)
```

## Data Flow

```
User Input (UI Controls)
    ↓
RecipeDetailsPane.getOptionValues()
    ↓
Map<String, Object> options
    ↓
MainController.applySelectedRecipe()
    ↓
McpClient.applyRecipe(sourceCode, recipeName, language, options)
    ↓
MCP Request with options in arguments
    ↓
ToolFactory.createApplyRecipeTool() handler
    ↓
RewriteEngine.applyRecipe(sourceCode, recipeName, language, options)
    ↓
RewriteEngine.applyOptionsToRecipe() - Reflection-based setter invocation
    ↓
Configured Recipe → OpenRewrite transformation
```

## Type System

### JavaFX Control → Java Type Conversion

| UI Control | Extracted Type | Java Target Type |
|-----------|---------------|-----------------|
| CheckBox | Boolean | boolean/Boolean |
| TextField (int) | Integer | int/Integer |
| TextField (long) | Long | long/Long |
| TextField (double) | Double | double/Double |
| TextField (float) | Float | float/Float |
| TextField (string) | String | String |
| TextArea (list) | List\<String\> | List\<String\> |

### Type Conversion Logic

The `convertValue()` method handles type mismatches:

1. **Direct Assignment:** If types match, use value as-is
2. **String Conversion:** Convert to String if target is String
3. **Primitive Parsing:** Parse strings to numeric primitives
4. **List Handling:** Pass List objects to List parameters
5. **Fallback:** Return value as-is and let Java handle conversion

## Error Handling

### Validation Errors

- Required options without values → Alert shown before applying
- Type validation in UI → Invalid characters rejected during input
- Missing setters → Logged as warnings, doesn't block execution
- Type conversion failures → Logged, original string value used as fallback

### Graceful Degradation

- If option application fails, recipe still runs with default configuration
- Warnings logged for debugging
- User sees transformation result even if some options couldn't be applied

## Testing Recommendations

1. **UI Testing:**
   - Select recipe with options
   - Verify controls are created with correct types
   - Enter valid values and verify they're retrieved correctly
   - Test validation with missing required options
   - Test default value population

2. **Type Conversion Testing:**
   - Test each control type with appropriate values
   - Test boundary values (min/max integers, empty strings, etc.)
   - Test list parsing with multiple lines

3. **Recipe Application Testing:**
   - Test recipes with various option types
   - Verify options actually affect recipe behavior
   - Test with no options (backward compatibility)
   - Test with invalid option names (should be logged)

4. **Integration Testing:**
   - Complete flow from UI to recipe execution
   - Verify MCP protocol includes options
   - Check event bus receives correct information
   - Test with external MCP clients

## Known Limitations

1. **Recipe Discovery:** Options are only shown if recipe metadata includes them
2. **Complex Types:** Advanced types (nested objects, custom classes) may not be fully supported
3. **Reflection Limitations:** Private setters or non-standard setter names may not be found
4. **UI Constraints:** Very long option lists may require scrolling

## Future Enhancements

1. **ComboBox for Enums:** Detect enum types and provide dropdown selection
2. **File Picker:** For file path options, provide file chooser dialog
3. **Advanced Validation:** Regex patterns, range validation, custom validators
4. **Option Dependencies:** Enable/disable options based on other option values
5. **Tooltips:** Show more detailed help for each option
6. **Reset Button:** Clear all options to default values
7. **Save/Load Configurations:** Store frequently used option combinations

## Files Modified

1. **RecipeDetailsPane.java** - Interactive controls and validation
2. **MainController.java** - Option retrieval and validation before applying
3. **McpClient.java** - Options parameter in applyRecipe() and parsing
4. **ToolFactory.java** - MCP tool schema and handler updates
5. **RewriteEngine.java** - Reflection-based option application

## Backward Compatibility

All changes maintain backward compatibility:

- Overloaded methods with options parameter
- Original methods delegate to new methods with null options
- Recipes without options work exactly as before
- Empty options map is equivalent to no options

## Conclusion

The interactive recipe options implementation provides a complete, type-safe system for configuring OpenRewrite recipes through the JavaFX UI. The implementation follows JavaFX best practices with:

- Appropriate control selection based on data types
- Input validation at the UI level
- Proper type conversion and error handling
- Clean separation of concerns between UI, client, and engine layers
- Comprehensive logging for debugging
- Graceful degradation on errors
