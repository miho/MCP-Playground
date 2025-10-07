# MCP Tools Fix Summary

## Issues Fixed

### 1. Missing Tools in LM Studio (FIXED ✅)
**Problem**: The three new tools (`apply_recipe_to_file`, `analyze_file_structure`, `list_instrumentation_recipes`) were not visible in LM Studio.

**Root Cause**: The `ServerLauncher` class (used by JavaFX UI's embedded server) was only registering 5 tools instead of all 8.

**Fix Applied**: Updated `ServerLauncher.java` to register all 8 tools in both stdio and HTTP modes.

### 2. File Path Conversion Issue (FIXED ✅)
**Problem**: `apply_recipe_to_file` was failing with "null" error when called from LM Studio.

**Root Cause**: The server running in WSL couldn't handle Windows-style paths (e.g., `C:/Dev/repos/...`).

**Fix Applied**:
- Added `convertWindowsPathToWSL()` method to convert Windows paths to WSL paths
- Handles both forward slash (`C:/Dev/...`) and backslash (`C:\Dev\...`) formats
- Converts to WSL format: `/mnt/c/Dev/...`
- Added better error handling and logging

## Files Modified

1. **ServerLauncher.java**
   - Lines 85-92: Added registration of 3 new tools for stdio mode
   - Lines 139-146: Added registration of 3 new tools for HTTP mode

2. **RewriteEngine.java**
   - Lines 810-860: Enhanced `applyRecipeToFile` with path conversion and better error handling
   - Lines 866-880: Added `convertWindowsPathToWSL()` method
   - Lines 886-901: Enhanced `analyzeFileStructure` with path conversion

## How to Apply the Fixes

1. **Stop all running processes**:
   ```bash
   ./gradlew --stop
   pkill -f "java.*openrewrite"
   ```

2. **Rebuild the project**:
   ```bash
   ./gradlew clean build
   ```

3. **Restart the JavaFX application**:
   ```bash
   ./gradlew run
   ```

4. **In LM Studio**:
   - Disconnect from the MCP server
   - Clear any cached connections
   - Reconnect to `http://localhost:3001/mcp`
   - All 8 tools should now be visible

## Testing the Fix

### Test Windows Path Conversion
The `apply_recipe_to_file` tool should now correctly handle:
- `C:/Dev/repos/MCP-Playground/code-reflection-01/openrewrite-jfx/examples/java/ModernJava.java`
- `C:\Dev\repos\MCP-Playground\code-reflection-01\openrewrite-jfx\examples\java\ModernJava.java`

Both will be converted to:
- `/mnt/c/Dev/repos/MCP-Playground/code-reflection-01/openrewrite-jfx/examples/java/ModernJava.java`

### Verify All Tools
Run the test script to confirm all tools are available:
```bash
./test-mcp-tools.sh
```

Expected output:
```
✓ apply_recipe_to_file found
✓ analyze_file_structure found
✓ list_instrumentation_recipes found
```

## Complete Tool List

The MCP server now exposes all 8 tools:

1. **list_recipes** - List available OpenRewrite recipes (with filter support)
2. **get_recipe_description** - Get detailed recipe information
3. **apply_recipe** - Apply recipe to source code
4. **analyze_code** - Analyze code and suggest recipes
5. **create_custom_recipe** - Create custom YAML recipes
6. **apply_recipe_to_file** ✅ - Apply recipe to files on disk
7. **analyze_file_structure** ✅ - Extract file structure without full code
8. **list_instrumentation_recipes** ✅ - List monitoring/logging recipes

## Notes

- The embedded MCP server in JavaFX UI now matches the standalone server functionality
- Windows/WSL path conversion is automatic and transparent
- Better error messages help diagnose issues
- Logging has been enhanced for troubleshooting

## Troubleshooting

If tools still don't appear:
1. Check server logs for errors
2. Ensure the server is running on port 3001
3. Try clearing LM Studio's MCP cache
4. Verify the test script shows all tools
5. Check that the build completed successfully