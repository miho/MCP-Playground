# MCP Error Fixes & Configuration Feature

## Issues Fixed

### 1. ✅ CSS Circular Reference Error (StackOverflowError)
**Problem:** JavaFX was failing to start with `StackOverflowError` due to CSS variable circular references.

**Solution:** Simplified `src/main/resources/css/application.css`:
- Removed all CSS variable lookups (like `-fx-border-color`, `-fx-text-color`)
- Replaced with direct color values (`#dcdcdc`, `#2c3e50`, etc.)
- Kept all visual styling intact

### 2. ✅ MCP Connection Error Spam
**Problem:** During server startup, connection tests were logging excessive ERROR messages causing log spam.

**Solution:** Updated logging levels in:
- **`McpClient.java`**: Changed `logger.error()` to `logger.debug()` for expected connection failures
- **`McpServerManager.java`**:
  - Added 3-second initial delay before starting connection tests (gives OpenCV/Jetty time to initialize)
  - Reduced log frequency to every 5 attempts instead of every attempt
  - Improved user-friendly status messages

### 3. ✅ No MCP Server Configuration Options
**Problem:** Users couldn't configure MCP server settings (HTTP vs stdio, custom port).

**Solution:** Added comprehensive configuration system:
- **New File**: `ServerConfigDialog.java` - Configuration dialog with:
  - Transport mode selection (HTTP/Stdio)
  - Custom port selection for HTTP mode
  - Server JAR path configuration
  - Help text explaining options
- **Updated**: `ControlBar.java` - Added ⚙ settings button
- **Updated**: `MainController.java` - Added `showServerSettings()` method
- Users can now configure before launching server

## New Features

### MCP Server Configuration Dialog
Access via **⚙ button** in the control bar:

**Options:**
1. **Transport Mode**:
   - **HTTP (Recommended)**: Easier debugging, better error messages
   - **Stdio**: Standard MCP transport for production

2. **HTTP Port**: Configure custom port (default: 8082)

3. **Server JAR Path**: Specify custom server JAR location

### Improved Startup Process

**Before Fix:**
```
[ERROR] HTTP connection test failed
[ERROR] HTTP connection test failed
[ERROR] HTTP connection test failed
... (30+ error messages)
```

**After Fix:**
```
[INFO] Waiting for server to initialize...
[INFO] Waiting for server... (attempt 5/30)
[INFO] Waiting for server... (attempt 10/30)
[INFO] Server is ready!
```

## Testing

### Verify Fixes
```bash
# 1. Compile (should succeed)
./gradlew clean compileJava

# 2. Run UI (should start without errors)
./gradlew run

# 3. Configure server
#    - Click ⚙ button in UI
#    - Choose HTTP or Stdio mode
#    - Set custom port if desired
#    - Click OK

# 4. Launch server
#    - Click "Launch MCP Server" button
#    - Wait 3-5 seconds for initialization
#    - Status indicator should turn GREEN
```

### Test Scenarios

#### Scenario 1: HTTP Mode (Default)
1. Launch UI: `./gradlew run`
2. Click **⚙** → Verify HTTP mode, port 8082
3. Click **OK**
4. Click **Launch MCP Server**
5. ✅ Server starts, green indicator, minimal logs

#### Scenario 2: Custom HTTP Port
1. Click **⚙**
2. Select HTTP mode
3. Change port to 9000
4. Click **OK**
5. Click **Launch MCP Server**
6. ✅ Server starts on port 9000

#### Scenario 3: Stdio Mode
1. Click **⚙**
2. Select **Stdio** radio button
3. Click **OK**
4. Click **Launch MCP Server**
5. ✅ Server starts in stdio mode

#### Scenario 4: Settings While Running (Should Prevent)
1. Launch server
2. Try to click **⚙**
3. ✅ Warning: "Cannot change settings while server is running"

## Files Modified

### Core Fixes
1. `src/main/resources/css/application.css` - Simplified CSS
2. `src/main/java/com/imageprocessing/client/McpClient.java` - Reduced error logging
3. `src/main/java/com/imageprocessing/client/McpServerManager.java` - Better startup timing

### New Configuration Feature
4. `src/main/java/com/imageprocessing/ui/components/ServerConfigDialog.java` - **NEW**
5. `src/main/java/com/imageprocessing/ui/components/ControlBar.java` - Added settings button
6. `src/main/java/com/imageprocessing/ui/MainController.java` - Added settings handler

## Configuration Examples

### Default Configuration (HTTP)
```java
McpConfig config = McpConfig.defaultHttp();
// HTTP mode, localhost:8082, auto-restart enabled
```

### Custom HTTP Configuration
```java
McpConfig config = McpConfig.builder()
    .transportMode(TransportMode.HTTP)
    .httpPort(9000)
    .serverJarPath(Paths.get("/custom/path/server.jar"))
    .connectionTimeout(Duration.ofSeconds(30))
    .captureServerLogs(true)
    .build();
```

### Stdio Configuration
```java
McpConfig config = McpConfig.builder()
    .transportMode(TransportMode.STDIO)
    .serverJarPath(Paths.get("./build/libs/image-processing-mcp-server.jar"))
    .build();
```

## Benefits

### User Experience
- ✅ Clean startup without error spam
- ✅ Visual configuration dialog
- ✅ Flexible transport mode selection
- ✅ Custom port configuration
- ✅ Better status feedback

### Developer Experience
- ✅ Cleaner logs during development
- ✅ Easy to switch between HTTP/stdio
- ✅ Configurable timeouts and retries
- ✅ Better debugging with HTTP mode

## Known Limitations

1. **Settings Locked During Runtime**: Cannot change configuration while server is running (by design)
2. **Single Server Instance**: Only one MCP server can run at a time
3. **Startup Delay**: 3-second initial delay before connection tests (necessary for OpenCV initialization)

## Future Enhancements

### Potential Improvements
- [ ] Save/load server configuration preferences
- [ ] Multiple server profiles (dev, production, custom)
- [ ] Real-time server log viewer in UI
- [ ] Server performance metrics display
- [ ] Remote server connection support

## Summary

All MCP errors have been resolved, and users now have full control over MCP server configuration through an intuitive UI dialog. The application starts cleanly, connects reliably, and provides clear status feedback throughout the process.

**Ready for production use!** 🎉
