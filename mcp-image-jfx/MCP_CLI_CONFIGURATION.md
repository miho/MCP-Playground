# MCP Server CLI Configuration

This document describes how to configure the embedded MCP server in the Image Processing JavaFX application using command-line arguments.

## Overview

The JavaFX application now supports configuring the embedded MCP server through command-line arguments using picocli. This allows you to:

- Choose between stdio and HTTP transport modes
- Configure HTTP server settings (host, port, endpoint)
- Enable/disable the MCP server
- Configure logging settings

## Command-Line Options

### Display Help

```bash
java -jar image-processing-ui.jar --help
```

### Display Version

```bash
java -jar image-processing-ui.jar --version
```

### Available Options

| Option | Description | Default Value |
|--------|-------------|---------------|
| `--mcp-enabled` | Enable embedded MCP server | `true` |
| `--mcp-mode` | Transport mode: `stdio` or `http` | `http` |
| `--mcp-port` | HTTP port for MCP server | `8082` |
| `--mcp-host` | HTTP host for MCP server | `localhost` |
| `--mcp-endpoint` | HTTP endpoint path for MCP server | `/mcp` |
| `--mcp-log-enabled` | Enable MCP server logging | `true` |
| `--mcp-log-dir` | Directory for MCP server logs | `./logs` |

## Usage Examples

### 1. Run with Default Configuration (HTTP mode)

```bash
# Default: HTTP mode on localhost:8082/mcp
gradle run

# Or with the JAR:
java -jar build/libs/image-processing-ui.jar
```

### 2. Run in stdio Mode

```bash
# Enable stdio transport mode
gradle run --args="--mcp-mode=stdio"

# Or with the JAR:
java -jar build/libs/image-processing-ui.jar --mcp-mode=stdio
```

**Note:** stdio mode is useful when the JavaFX UI needs to be embedded in another application that communicates via standard input/output. However, in a JavaFX application context, HTTP mode is generally more practical.

### 3. Run with Custom HTTP Port

```bash
# Run HTTP server on port 9000
gradle run --args="--mcp-mode=http --mcp-port=9000"

# Or with the JAR:
java -jar build/libs/image-processing-ui.jar --mcp-mode=http --mcp-port=9000
```

### 4. Run with MCP Server Disabled

```bash
# Disable the embedded MCP server entirely
gradle run --args="--mcp-enabled=false"

# Or with the JAR:
java -jar build/libs/image-processing-ui.jar --mcp-enabled=false
```

This is useful when you only want to use the UI without exposing an MCP interface.

### 5. Run with Custom Configuration

```bash
# Full custom configuration
gradle run --args="--mcp-mode=http --mcp-host=0.0.0.0 --mcp-port=8090 --mcp-endpoint=/api/mcp --mcp-log-dir=/var/log/mcp"

# Or with the JAR:
java -jar build/libs/image-processing-ui.jar \
  --mcp-mode=http \
  --mcp-host=0.0.0.0 \
  --mcp-port=8090 \
  --mcp-endpoint=/api/mcp \
  --mcp-log-dir=/var/log/mcp
```

## Transport Modes Explained

### HTTP Mode (Default)

- **Use Case:** Remote access, REST-like communication, web integration
- **Benefits:**
  - Multiple clients can connect simultaneously
  - Easy to test with tools like curl or Postman
  - Works well with external MCP clients
  - No blocking of stdout/stderr
- **Configuration:** Requires host, port, and endpoint settings
- **Example Endpoint:** `http://localhost:8082/mcp`

### stdio Mode

- **Use Case:** Embedded in other applications, command-line tools, piped workflows
- **Benefits:**
  - Direct communication via standard input/output
  - Lower latency for local communication
  - Simpler protocol for single-client scenarios
- **Limitations:**
  - Only one client can connect at a time
  - Blocks standard input/output streams
  - May interfere with JavaFX UI logging
- **Configuration:** No HTTP settings needed

## Implementation Details

### Architecture

1. **McpCliOptions.java** - Picocli-annotated class that parses CLI arguments
2. **ImageProcessingApp.java** - Main JavaFX application that processes CLI options before UI initialization
3. **MainController.java** - Controller that accepts McpConfig from CLI or uses defaults
4. **McpConfig.java** - Configuration model supporting both transport modes
5. **ServerLauncher.java** - Launches MCP server with the specified configuration

### Configuration Flow

```
CLI Arguments → McpCliOptions.parse()
             ↓
        McpConfig.build()
             ↓
        MainController(config)
             ↓
        ServerLauncher.startAsync()
             ↓
        MCP Server Running
```

### Viewing Current Configuration

Once the application is running, you can view the current MCP server configuration:

1. Click the "Server Settings" button (gear icon) in the control bar
2. A dialog will display:
   - Server status (Enabled/Disabled)
   - Transport mode
   - HTTP configuration (if applicable)
   - Logging settings
   - CLI usage hints

## Building the Application

### Build UI JAR

```bash
gradle uiJar
```

Output: `build/libs/image-processing-ui.jar`

### Build Server-Only JAR

```bash
gradle shadowJar
```

Output: `build/libs/image-processing-mcp-server.jar`

The server-only JAR excludes JavaFX dependencies and UI classes, suitable for headless deployment.

## Standalone MCP Server

For a headless MCP server without the UI, use the standalone server:

```bash
# stdio mode (default)
java -jar build/libs/image-processing-mcp-server.jar

# HTTP mode
java -jar build/libs/image-processing-mcp-server.jar --http 8082
```

## Testing the Configuration

### Test HTTP Mode

```bash
# Start the application with HTTP mode
gradle run --args="--mcp-mode=http --mcp-port=8082"

# In another terminal, test the endpoint:
curl -X POST http://localhost:8082/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"tools/list","id":1}'
```

### Test stdio Mode

stdio mode is best tested by integrating with an MCP client that can communicate via stdin/stdout. The JavaFX UI will still function, but stdout may be captured by the MCP protocol.

## Troubleshooting

### Server Won't Start

- **Check if port is already in use:**
  ```bash
  netstat -an | grep 8082
  ```
- **Try a different port:**
  ```bash
  gradle run --args="--mcp-port=8090"
  ```

### stdio Mode Issues

- stdio mode may conflict with JavaFX UI logging
- Consider using HTTP mode for UI-based deployments
- stdio mode is more suitable for the standalone server JAR

### Configuration Not Applied

- Ensure arguments are passed correctly:
  ```bash
  gradle run --args="--mcp-mode=http"  # Correct
  gradle run --mcp-mode=http           # Incorrect
  ```
- Check the console output for configuration confirmation
- View settings in the UI via "Server Settings" button

## Migration from Previous Versions

If you were using the application before CLI configuration was added:

- **No changes required** - The default behavior is unchanged (HTTP mode on port 8082)
- **Previous code using `new MainController()`** - Still works but is deprecated
- **Recommended approach** - Use `new MainController(mcpConfig, enabled)` constructor

## Summary

The MCP CLI configuration system provides flexible options for running the embedded MCP server in different modes and environments. The default HTTP mode works well for most use cases, while stdio mode is available for specialized integration scenarios.

For questions or issues, please refer to the source code documentation in:
- `/src/main/java/com/imageprocessing/ui/McpCliOptions.java`
- `/src/main/java/com/imageprocessing/server/McpConfig.java`
- `/src/main/java/com/imageprocessing/server/ServerLauncher.java`
