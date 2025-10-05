# Claude Desktop Configuration for Traffic Sim MCP Server

This guide shows how to connect Claude Desktop to the running Traffic Simulation UI's embedded HTTP MCP server.

## Overview

The Traffic Simulation application now runs with an **embedded HTTP MCP server** on port `8083`. This means:

- You can run the JavaFX UI and see the simulation visually
- Claude Desktop can connect to the same running instance via HTTP
- When Claude calls MCP tools, the changes are immediately visible in the UI
- The UI's "LLM Optimize" button still works (uses DirectToolExecutor)
- Both work simultaneously - you can watch Claude interact with the simulation in real-time

## Running the Application

### Start the UI with Embedded HTTP Server

```bash
./gradlew run
```

The application will:
1. Open the JavaFX UI showing the traffic intersection
2. Start an embedded HTTP MCP server on `http://localhost:8083/mcp`
3. Display "Server: Running on http://localhost:8083/mcp" in the status bar

## Configuring Claude Desktop

### Option 1: HTTP Configuration (Recommended)

Add this to your Claude Desktop configuration file:

**Location:**
- macOS: `~/Library/Application Support/Claude/claude_desktop_config.json`
- Windows: `%APPDATA%\Claude\claude_desktop_config.json`
- Linux: `~/.config/Claude/claude_desktop_config.json`

**Configuration:**

```json
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
```

### Restart Claude Desktop

After updating the configuration:
1. Quit Claude Desktop completely
2. Restart Claude Desktop
3. The traffic-sim-ui server should appear in the MCP servers list
4. You should see the 4 tools available:
   - `intersection_reset`
   - `intersection_evaluate_plan`
   - `intersection_apply_plan`
   - `intersection_get_state`

## Testing the Connection

In Claude Desktop, you can test with prompts like:

1. **Check current state:**
   ```
   What is the current state of the traffic intersection?
   ```

2. **Evaluate a signal plan:**
   ```
   Evaluate this signal plan:
   - Cycle: 80 seconds
   - NS green: 35 seconds
   - EW green: 35 seconds
   ```

3. **Optimize the intersection:**
   ```
   Can you optimize the traffic signal timing for this intersection?
   Try different cycle lengths and green time allocations.
   ```

## Troubleshooting

### Server Not Connecting

1. **Check the UI is running**: Make sure `./gradlew run` is still active
2. **Check the status bar**: Should show "Server: Running on http://localhost:8083/mcp"
3. **Check port availability**: Make sure port 8083 is not in use by another application
4. **Check firewall**: Ensure localhost connections are allowed

### Server Shows as Offline in Claude Desktop

1. **Restart Claude Desktop**: Sometimes it needs a fresh start to connect
2. **Check the URL**: Must be exactly `http://localhost:8083/mcp` (including `/mcp`)
3. **Check logs**: Look at the UI console for any error messages

### Tools Not Appearing

1. **Wait a moment**: Claude Desktop may take a few seconds to discover tools
2. **Check server type**: Make sure you're using `"type": "http"` not `"type": "stdio"`
3. **Verify configuration**: Ensure the JSON is valid (use a JSON validator)

## How It Works

### Architecture

```
┌─────────────────────────────────────┐
│     Traffic Simulation UI (JavaFX)  │
│                                     │
│  ┌──────────────┐  ┌─────────────┐ │
│  │ Intersection │  │   Metrics   │ │
│  │  Simulator   │  │   Panel     │ │
│  └──────────────┘  └─────────────┘ │
│         ▲                           │
│         │                           │
│         │ (shared instance)         │
│         │                           │
│  ┌──────┴───────┐  ┌─────────────┐ │
│  │ Direct Tool  │  │  HTTP MCP   │ │
│  │  Executor    │  │   Server    │ │
│  │ (UI Button)  │  │ (Port 8083) │ │
│  └──────────────┘  └──────┬──────┘ │
└────────────────────────────┼────────┘
                             │
                             │ HTTP
                             │
                    ┌────────▼────────┐
                    │ Claude Desktop  │
                    └─────────────────┘
```

### Key Components

1. **IntersectionSimulator**: Core simulation engine (shared instance)
2. **DirectToolExecutor**: Used by UI's "LLM Optimize" button
3. **ServerLauncher**: Manages the embedded HTTP MCP server
4. **ToolFactory**: Creates stateless sync tools for HTTP transport
5. **Jetty Server**: Embedded HTTP server on port 8083

### Tool Sharing

Both the DirectToolExecutor (UI) and HTTP MCP server (Claude Desktop) operate on the **same simulator instance**. This means:
- Changes from Claude Desktop are immediately visible in the UI
- The UI and Claude Desktop never conflict
- You can see the simulation update in real-time as Claude interacts with it

## Available MCP Tools

### 1. intersection_reset
Reset the simulation with new seed and arrival rates.

**Parameters:**
- `seed` (integer): Random seed for reproducibility
- `arrivals` (object): Arrival rates for each direction
  - `N` (number): North arrival rate (vehicles/min)
  - `S` (number): South arrival rate (vehicles/min)
  - `E` (number): East arrival rate (vehicles/min)
  - `W` (number): West arrival rate (vehicles/min)

### 2. intersection_evaluate_plan
Evaluate a signal plan and return performance metrics.

**Parameters:**
- `plan` (object): Signal plan definition
  - `cycleSeconds` (number): Total cycle length (30-180 seconds)
  - `phases` (array): List of phases
    - `name` (string): Phase name (e.g., "NS_through")
    - `greenSeconds` (number): Green time (6-120 seconds)
  - `yellowSeconds` (number, optional): Yellow time (default: 3)
  - `allRedSeconds` (number, optional): All-red time (default: 1)
- `durationSeconds` (number, optional): Simulation duration (default: 120)
- `replications` (integer, optional): Number of runs to average (default: 3)

**Returns:**
- Average delay per vehicle (seconds)
- 95th percentile queue length (vehicles)
- Throughput (vehicles/hour)
- Stops per vehicle

### 3. intersection_apply_plan
Apply a signal plan to the running simulation.

**Parameters:**
- Same `plan` object as evaluate_plan

**Effect:**
- Updates the signal timing in the live simulation
- Visible immediately in the UI

### 4. intersection_get_state
Get current state of the intersection.

**Returns:**
- Current simulation time
- Current signal phase
- Signal state (green/yellow/red for each direction)
- Queue lengths for each direction

## Example Claude Conversation

```
You: Can you help optimize this traffic intersection?

Claude: I'll start by checking the current state of the intersection.
[Calls intersection_get_state]

The intersection currently has:
- Time: 42.5s
- Phase: 0 (NS through)
- Queues: N=3, S=2, E=5, W=4

Let me evaluate a few different signal timing plans to find the optimal one.
[Calls intersection_evaluate_plan multiple times with different configurations]

Based on my evaluation:
- 80-second cycle with 35/35 split: 12.3s avg delay
- 60-second cycle with 25/25 split: 14.1s avg delay
- 100-second cycle with 45/45 split: 11.8s avg delay ← Best!

Let me apply the best plan now.
[Calls intersection_apply_plan]

Done! The new signal timing has been applied. You should see the changes in the UI.
```

## Tips for Using with Claude Desktop

1. **Watch the UI**: Keep the UI visible while chatting with Claude to see changes in real-time
2. **Check the log panel**: All tool calls appear in the UI's log panel at the bottom
3. **Use the metrics panel**: Shows current performance metrics that update every 2 seconds
4. **Try the UI button too**: The "LLM Optimize" button in the UI works alongside Claude Desktop
5. **Experiment**: Try different optimization strategies and compare results

## Differences from Standalone Server

### Embedded HTTP Server (Current)
- UI runs with embedded HTTP server
- See simulation visually while Claude interacts
- Both UI and Claude Desktop work simultaneously
- Port: 8083

### Standalone Server (Alternative)
- Run server without UI: `./gradlew runServer`
- Command-line only, no visual feedback
- Uses stdio transport (not HTTP)
- For headless/server environments

## Performance Notes

- The HTTP server runs in a background thread
- Tool calls are synchronous but non-blocking
- Multiple Claude Desktop instances can connect (though they share the same simulator)
- UI remains responsive during MCP operations

## Next Steps

1. Start the UI: `./gradlew run`
2. Configure Claude Desktop with the HTTP transport
3. Restart Claude Desktop
4. Start chatting with Claude about optimizing traffic signals
5. Watch the magic happen in real-time!

---

For more information about the MCP protocol, see: https://modelcontextprotocol.io
