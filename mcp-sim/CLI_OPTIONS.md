# Command-Line Options for MCP Configuration

## Overview

The Traffic Intersection Optimizer supports both **HTTP** and **stdio** transport modes for the embedded MCP server. You can configure the server using command-line arguments.

## Quick Start

### Default (HTTP Mode)

```bash
./gradlew run
```

This starts the UI with HTTP server on `http://localhost:8083/mcp` (default).

### Stdio Mode (for Claude Desktop stdio connection)

```bash
./gradlew run --args="--mcp-mode stdio"
```

This starts the UI with stdio transport for direct MCP connection.

### Custom HTTP Port

```bash
./gradlew run --args="--mcp-port 9000"
```

This starts the HTTP server on port 9000 instead of the default 8083.

### Disable MCP Server

```bash
./gradlew run --args="--mcp-enabled false"
```

This runs the UI without the MCP server (UI controls still work).

## All Available Options

### MCP Server Control

**`--mcp-enabled`** (default: `true`)
- Enable or disable the embedded MCP server
- Example: `--mcp-enabled false`

**`--mcp-mode`** (default: `http`)
- Transport mode: `stdio` or `http`
- `http`: HTTP server (for Claude Desktop HTTP connections)
- `stdio`: Standard I/O (for Claude Desktop stdio connections)
- Example: `--mcp-mode stdio`

### HTTP Server Configuration (for HTTP mode)

**`--mcp-port`** (default: `8083`)
- HTTP server port
- Example: `--mcp-port 9000`

**`--mcp-host`** (default: `localhost`)
- HTTP server host
- Example: `--mcp-host 0.0.0.0`

**`--mcp-endpoint`** (default: `/mcp`)
- HTTP endpoint path
- Example: `--mcp-endpoint /traffic-mcp`

### Logging Configuration

**`--mcp-log-enabled`** (default: `true`)
- Enable MCP server logging
- Example: `--mcp-log-enabled false`

**`--mcp-log-dir`** (default: `./logs`)
- Directory for MCP server logs
- Example: `--mcp-log-dir /tmp/traffic-logs`

### Help and Version

**`--help`** or `-h`
- Show help message with all options
- Example: `--help`

**`--version`** or `-V`
- Show application version
- Example: `--version`

## Usage Examples

### Example 1: HTTP Mode (For API Testing Only)

```bash
./gradlew run
```

**Test with curl (NOT Claude Desktop):**
```bash
curl -X POST http://localhost:8083/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","method":"tools/list","id":1}'
```

⚠️ **Claude Desktop does NOT support HTTP transport** - use stdio mode instead (see Example 2).

### Example 2: Stdio Mode (REQUIRED for Claude Desktop)

```bash
./gradlew run --args="--mcp-mode stdio"
```

**Claude Desktop config:**
```json
{
  "mcpServers": {
    "traffic-sim-ui": {
      "command": "java",
      "args": [
        "-cp",
        "build/install/mcp-sim/lib/*",
        "com.trafficsim.ui.TrafficSimApp",
        "--mcp-mode",
        "stdio"
      ]
    }
  }
}
```

**Note for stdio mode:** You need to install the app first:
```bash
./gradlew installDist
```

### Example 3: Custom Port

```bash
./gradlew run --args="--mcp-port 9090"
```

**Claude Desktop config:**
```json
{
  "mcpServers": {
    "traffic-sim-ui": {
      "transport": {
        "type": "http",
        "url": "http://localhost:9090/mcp"
      }
    }
  }
}
```

### Example 4: Multiple Options

```bash
./gradlew run --args="--mcp-mode http --mcp-port 9090 --mcp-log-enabled false"
```

### Example 5: No MCP (UI Only)

```bash
./gradlew run --args="--mcp-enabled false"
```

The UI works normally, but no MCP server starts. Useful for testing the UI without MCP.

## When to Use HTTP vs Stdio

### Use Stdio Mode When:
✅ **Connecting Claude Desktop** (REQUIRED - Claude Desktop only supports stdio)
✅ You want to see the UI while Claude interacts with it
✅ You want real-time visualization of optimization
✅ You want to use both the UI and Claude Desktop simultaneously

### Use HTTP Mode When:
✅ Testing with curl or Postman
✅ Integrating with HTTP-based MCP clients (not Claude Desktop)
✅ Building custom integrations via REST API
❌ **NOT for Claude Desktop** (Claude Desktop only supports stdio)

## Verifying the Configuration

After starting the UI, check the **status bar** at the bottom:

**HTTP Mode:**
```
Server: Running on http://localhost:8083/mcp
```

**Stdio Mode:**
```
Server: Running (stdio mode)
```

**MCP Disabled:**
```
MCP server disabled
```

Also check the **log panel** for startup messages.

## Troubleshooting

### "Address already in use" error

Someone else is using port 8083. Use a different port:
```bash
./gradlew run --args="--mcp-port 9000"
```

### Claude Desktop can't connect (HTTP mode)

1. Check status bar shows server is running
2. Verify the URL matches: `http://localhost:8083/mcp`
3. Make sure no firewall is blocking the connection
4. Check Claude Desktop logs for errors

### Stdio mode not working

1. Make sure you built the distribution:
   ```bash
   ./gradlew installDist
   ```

2. Use the correct classpath in Claude Desktop config
3. Check the app's stderr output for errors

### MCP tools not appearing

1. Restart Claude Desktop after config changes
2. Check the config file syntax (valid JSON)
3. Verify the server is actually running (check status bar)
4. Try running with `--mcp-log-enabled true` and check logs

## Advanced Configuration

### Running as Installed Application

First, install the application:
```bash
./gradlew installDist
```

This creates scripts in `build/install/mcp-sim/bin/`:
- `mcp-sim` (Linux/Mac)
- `mcp-sim.bat` (Windows)

Then run with options:
```bash
build/install/mcp-sim/bin/mcp-sim --mcp-mode stdio
```

### Environment Variables

You can also set options via Java system properties:
```bash
./gradlew run -Dmcp.port=9090 -Dmcp.mode=http
```

(Note: CLI args take precedence over system properties)

## Summary

**For Claude Desktop (REQUIRED approach):**
```bash
./gradlew installDist
```
Then configure Claude Desktop with stdio mode as shown in Example 2 above.

**For API testing:**
```bash
./gradlew run
```
HTTP endpoint available at `http://localhost:8083/mcp` (NOT compatible with Claude Desktop).

**For developers:**
Use `--help` to see all options:
```bash
./gradlew run --args="--help"
```
