# Traffic Intersection Optimizer with MCP

A JavaFX-based traffic intersection simulator that can be optimized by an LLM via the Model Context Protocol (MCP).

## Overview

This application implements **Option A: Smart Intersection Optimizer** from the instructions - a 4-way traffic intersection simulation where an LLM can optimize signal timing plans to minimize vehicle delays.

### Features

- **Visual Simulation**: Real-time visualization of a 4-way intersection with vehicles, traffic signals, and queues
- **Adjustable Parameters**: Control arrival rates for each direction (N, S, E, W)
- **Simulation Speed Control**: Speed up time from 0.5x to 10x for faster demonstrations
- **Live Performance Metrics**: Automatically updated every 2 seconds showing average delay, queue lengths, throughput, and stops per vehicle
- **MCP Integration**: Expose simulation tools for LLM-based optimization

## Project Structure

```
src/main/java/com/trafficsim/
├── model/              # Data models (SignalPlan, Phase, Metrics, Direction)
├── engine/             # Simulation engine (IntersectionSimulator, Vehicle)
├── server/             # MCP server implementation
└── ui/                 # JavaFX user interface
```

## Running the Application

### Run the JavaFX UI with Embedded HTTP Server (Recommended)

```bash
./gradlew run
```

This launches the interactive visualization with an **embedded HTTP MCP server** on port 8083. You get:

- **Visual Simulation**: Real-time traffic intersection with vehicles and signals
- **Embedded HTTP Server**: MCP server running at `http://localhost:8083/mcp`
- **Claude Desktop Integration**: Connect Claude Desktop to the running UI (see below)
- **Dual Access**: Both the UI's "LLM Optimize" button AND Claude Desktop work simultaneously
- **Real-time Updates**: Watch Claude interact with the simulation in real-time

**UI Features:**
- **Adjust arrival rates in real-time** with sliders (0-40 veh/min per direction) - changes apply immediately!
- **Control simulation speed** (0.5x - 10x) with slider or quick buttons (1x, 2x, 5x, 10x)
- Trigger "Rush Hour Spike" to double traffic temporarily
- Apply baseline signal plans
- **View live performance metrics** (auto-updated every 2 seconds, or click "Update Metrics" for instant refresh)
- **MCP Log Panel**: See all tool calls from both the UI and Claude Desktop

**Server Status:** Check the status bar at the bottom - it will show "Server: Running on http://localhost:8083/mcp"

### Connect Claude Desktop to Running UI

⚠️ **IMPORTANT**: Claude Desktop only supports stdio transport. HTTP mode is for API testing only.

**Option 1: Self-Contained Distribution (Recommended for Production)**

1. Build the runtime image:
   ```bash
   ./gradlew createRuntime
   ```

2. Add to Claude Desktop config:
   ```json
   {
     "mcpServers": {
       "traffic-sim-ui": {
         "command": "/mnt/c/Dev/repos/MCP-Playground/mcp-sim/build/jpackage/mcp-sim/bin/mcp-sim",
         "args": ["--mcp-mode", "stdio"]
       }
     }
   }
   ```

   **Note:** Use absolute path. The runtime includes bundled JRE and JavaFX - no separate Java installation needed!

**Option 2: Development Mode (Simpler for Development)**

1. Add to Claude Desktop config:
   ```json
   {
     "mcpServers": {
       "traffic-sim-ui": {
         "command": "./gradlew",
         "args": ["run", "--args=--mcp-mode stdio", "--console=plain", "--quiet"],
         "cwd": "/mnt/c/Dev/repos/MCP-Playground/mcp-sim"
       }
     }
   }
   ```

   **Note:** Requires Gradle installed. On Windows, use `gradlew.bat`.

3. Restart Claude Desktop
4. Chat with Claude - the UI window will appear automatically!

See [DISTRIBUTION.md](DISTRIBUTION.md) for packaging and deployment details.

**HTTP Mode (For API Testing):**

HTTP mode provides an endpoint at `http://localhost:8083/mcp` for testing with curl/Postman, but is **NOT compatible with Claude Desktop**.

Test with curl:
```bash
curl -X POST http://localhost:8083/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","method":"tools/list","id":1}'
```

**See [MCP_CONNECTION_MODES.md](MCP_CONNECTION_MODES.md) for detailed explanation and [CLI_OPTIONS.md](CLI_OPTIONS.md) for all command-line options.**

### Run the MCP Server (Standalone - Alternative)

```bash
./gradlew runServer
```

This starts the MCP server in stdio mode without the UI. Use this for:
- Headless/server environments
- Command-line MCP clients
- Testing without GUI

The standalone server exposes the same tools but uses stdio transport instead of HTTP.

#### MCP Tools

1. **intersection_reset** - Reset simulation with seed and arrival rates
   ```json
   {
     "seed": 12345,
     "arrivals": {"N": 10, "S": 10, "E": 10, "W": 10}
   }
   ```

2. **intersection_evaluate_plan** - Evaluate a signal plan and return metrics
   ```json
   {
     "plan": {
       "cycleSeconds": 60,
       "phases": [
         {"name": "NS_through", "greenSeconds": 25},
         {"name": "EW_through", "greenSeconds": 25}
       ],
       "yellowSeconds": 3,
       "allRedSeconds": 1
     },
     "durationSeconds": 120,
     "replications": 3
   }
   ```

3. **intersection_apply_plan** - Apply a plan to the running simulation

4. **intersection_get_state** - Get current state (queues, signal phase, time)

## Building

### Build Server JAR

```bash
./gradlew serverJar
```

The server JAR will be created at: `build/libs/traffic-sim-mcp-server-all.jar`

Run it with:
```bash
java -jar build/libs/traffic-sim-mcp-server-all.jar
```

## LLM Optimization Strategy

The LLM can optimize signal plans by:

1. **Initial Exploration**: Test 6-10 diverse signal plans with different cycle lengths and phase splits
2. **Local Search**: Refine the best plan by adjusting parameters ±10%
3. **Metrics Tracking**: Minimize average delay as the primary objective
4. **Constraints**: Respect cycle length (30-180s), green time (6-120s) limits

### Example Prompt for LLM

> Your goal is to minimize average delay at a 4-way intersection. Start by resetting the simulation with seed 12345 and arrivals N=15, S=15, E=10, W=10. Then evaluate diverse signal plans (vary cycle length 40-120s and green splits). Pick the best plan and do local refinement. Finally, apply the optimized plan.

## Demo Flow (5-7 minutes)

1. Start with baseline fixed-time plan → Show metrics
2. Enable LLM optimization → LLM tests plans via MCP
3. Apply optimized plan → Compare metrics
4. Trigger "Rush Hour Spike" → LLM re-optimizes
5. Toggle back to baseline → Highlight improvement

## Technical Details

### Simulation Model

- **Discrete-time simulation**: 10 Hz update rate
- **Poisson arrivals**: Configurable λ per direction
- **Saturation flow**: 1800 veh/hour/lane
- **Deterministic**: Seeded RNG for reproducible results

### Performance Metrics

- **Average Delay**: Mean wait time per vehicle (seconds)
- **Queue P95**: 95th percentile queue length
- **Throughput**: Vehicles per hour departing intersection
- **Stops/Vehicle**: Average number of stops per vehicle

## Requirements

- Java 17+
- Gradle 7.0+
- JavaFX 21.0.1

## License

MIT
