# MCP Connection Modes

## Overview

⚠️ **IMPORTANT**: Claude Desktop ONLY supports stdio transport. HTTP mode does NOT work with Claude Desktop.

The Traffic Intersection Optimizer supports **three connection modes**:

1. **HTTP Mode** - For API testing with curl/Postman (NOT Claude Desktop)
2. **Stdio Mode with UI** - For Claude Desktop (Recommended ⭐)
3. **Standalone Server** - Headless server for command-line MCP clients

**For Claude Desktop:** Use Mode 2 (Stdio with UI). This gives you both the visualization AND Claude Desktop integration.

## Mode 1: HTTP Mode with UI (For API Testing) 🌐

### What You Get
- ✅ See the UI with live traffic visualization
- ✅ HTTP endpoint for testing with curl/Postman
- ✅ Suitable for other MCP clients that support HTTP
- ❌ **NOT compatible with Claude Desktop** (Claude Desktop only supports stdio)

### Setup

**1. Start the UI:**
```bash
./gradlew run
```

**2. Test with curl:**
```bash
curl -X POST http://localhost:8083/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","method":"tools/list","id":1}'
```

### When to Use
- ✅ API testing and development
- ✅ Integration with HTTP-based MCP clients
- ✅ Direct REST API access
- ❌ **NOT for Claude Desktop** (use stdio mode instead)

---

## Mode 2: Stdio Mode with UI (Recommended for Claude Desktop) 🌟

### What You Get
- ✅ See the UI with live traffic visualization
- ✅ Direct stdio communication with Claude
- ✅ Traditional MCP stdio approach
- ✅ Same visual feedback as HTTP mode

### Setup

**1. Install the distribution:**
```bash
./gradlew installDist
```

**2. Start the UI in stdio mode:**
```bash
./gradlew run --args="--mcp-mode stdio"
```

Or use the installed script:
```bash
build/install/mcp-sim/bin/mcp-sim --mcp-mode stdio
```

**3. Configure Claude Desktop:**

Edit your `claude_desktop_config.json`:
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

Or if using the installed script:
```json
{
  "mcpServers": {
    "traffic-sim-ui": {
      "command": "/path/to/build/install/mcp-sim/bin/mcp-sim",
      "args": ["--mcp-mode", "stdio"]
    }
  }
}
```

**4. Restart Claude Desktop**

### When to Use
- ✅ **Connecting Claude Desktop** (REQUIRED - Claude Desktop only supports stdio)
- ✅ When you want to see the UI while Claude interacts
- ✅ Network access is restricted
- ✅ When HTTP port conflicts occur

### Note
In stdio mode, the status bar shows: "Server: Running (stdio mode)"

---

## Mode 3: Standalone Server (No UI)

### What You Get
- ✅ Headless server for CI/CD or servers
- ✅ Stdio transport
- ✅ No JavaFX dependencies needed
- ✅ Lower resource usage

### Setup

**1. Run the standalone server:**
```bash
./gradlew runServer
```

Or use the standalone JAR:
```bash
./gradlew serverJar
java -jar build/libs/traffic-sim-mcp-server-1.0.0-all.jar
```

**2. Configure Claude Desktop:**

```json
{
  "mcpServers": {
    "traffic-sim-server": {
      "command": "java",
      "args": [
        "-jar",
        "/path/to/build/libs/traffic-sim-mcp-server-1.0.0-all.jar"
      ]
    }
  }
}
```

### When to Use
- ✅ Server environments (no GUI)
- ✅ Automated testing
- ✅ CI/CD pipelines
- ✅ When you don't need visualization

### Note
This mode uses stdio and has no UI - you won't see the intersection, just the results.

---

## Comparison Table

| Feature | HTTP Mode | Stdio Mode | Standalone |
|---------|-----------|------------|------------|
| **UI Visualization** | ✅ Yes | ✅ Yes | ❌ No |
| **Transport** | HTTP | stdio | stdio |
| **Port Required** | 8083 | None | None |
| **Claude Desktop Compatible** | ❌ **NO** | ✅ **YES** | ✅ Yes |
| **Dual Access** | ❌ No (API only) | ✅ Yes (UI + Claude) | ❌ No UI |
| **Real-time Visual Feedback** | ✅ Yes | ✅ Yes | ❌ No |
| **Resource Usage** | Medium | Medium | Low |
| **Setup Complexity** | Easy | Medium | Easy |
| **Best For** | API testing | **Claude Desktop** | Headless servers |

---

## Which Mode Should I Use?

### For Claude Desktop: **Stdio Mode with UI** 🎯 (REQUIRED)

**Why?**
- Claude Desktop ONLY supports stdio transport
- You still get the full UI visualization
- Watch Claude interact with the simulation in real-time
- Both UI buttons AND Claude work simultaneously

**Demo Flow:**
1. Install distribution: `./gradlew installDist`
2. Configure Claude Desktop with stdio mode (see above)
3. Restart Claude Desktop
4. Chat with Claude in Claude Desktop
5. **Point to the UI**: "See, Claude is calling tools and the simulation is updating!"
6. Audience sees:
   - Tool calls in log panel
   - Signal timing changing
   - Metrics improving
   - Queue lengths adapting

### For API Testing: **HTTP Mode**

- Test endpoints with curl/Postman
- Integration with HTTP-based tools
- Direct REST API access
- **NOT compatible with Claude Desktop**

### For Production/Automation: **Standalone Server**

Headless, no UI overhead, pure stdio for command-line MCP clients.

---

## Testing Each Mode

### HTTP Mode Test
```bash
# Terminal 1: Start UI
./gradlew run

# Terminal 2: Test with curl
curl -X POST http://localhost:8083/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"tools/list","id":1}'

# Should return list of 4 tools
```

### Stdio Mode Test
```bash
# Start in stdio mode
./gradlew run --args="--mcp-mode stdio"

# UI should show: "Server: Running (stdio mode)"
# Connect Claude Desktop with stdio config
```

### Standalone Test
```bash
# Start server
./gradlew runServer

# It outputs to stdout/stderr
# Connect Claude Desktop with stdio config
```

---

## Troubleshooting

### HTTP Mode Issues

**Problem:** "Address already in use"
**Solution:** Change port
```bash
./gradlew run --args="--mcp-port 9090"
```

**Problem:** Claude Desktop can't connect with HTTP config
**Solution:** Claude Desktop does NOT support HTTP transport!
```json
// ❌ This DOES NOT WORK with Claude Desktop:
{
  "mcpServers": {
    "traffic-sim-ui": {
      "transport": {
        "type": "http",
        "url": "http://localhost:8083/mcp"
      }
    }
  }
}

// ✅ Use stdio mode instead:
{
  "mcpServers": {
    "traffic-sim-ui": {
      "command": "build/install/mcp-sim/bin/mcp-sim",
      "args": ["--mcp-mode", "stdio"]
    }
  }
}
```

**Problem:** "Both application/json and text/event-stream required in Accept header"
**Solution:** This error occurs when testing HTTP endpoint without proper headers. Add both content types:
```bash
curl -X POST http://localhost:8083/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","method":"tools/list","id":1}'
```

### Stdio Mode Issues

**Problem:** "Command not found"
**Solution:** Use full path or install first
```bash
./gradlew installDist
# Then use: build/install/mcp-sim/bin/mcp-sim
```

**Problem:** UI doesn't start with stdio
**Solution:** Check stderr output for errors
```bash
./gradlew run --args="--mcp-mode stdio" 2>&1 | tee output.log
```

### Standalone Issues

**Problem:** No output/feedback
**Solution:** That's normal - it's headless! Use Claude Desktop to interact.

---

## Advanced: Custom Ports

**HTTP on custom port:**
```bash
./gradlew run --args="--mcp-port 9090"
```

**Claude Desktop config:**
```json
"url": "http://localhost:9090/mcp"
```

**Check all options:**
```bash
./gradlew run --args="--help"
```

---

## Summary

**For Claude Desktop (demos/talks): Use Stdio Mode with UI!**

```bash
# Install distribution
./gradlew installDist

# Configure Claude Desktop
{
  "mcpServers": {
    "traffic-sim-ui": {
      "command": "build/install/mcp-sim/bin/mcp-sim",
      "args": ["--mcp-mode", "stdio"]
    }
  }
}

# Restart Claude Desktop

# Chat with Claude and watch the UI! 🚦
```

It's the perfect setup:
- ✅ Compatible with Claude Desktop (required)
- ✅ Full UI visualization
- ✅ Real-time feedback
- ✅ Both UI and Claude work together

**Note**: HTTP mode at `http://localhost:8083/mcp` is for API testing only - Claude Desktop does not support direct HTTP connections.
