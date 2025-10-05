# Quick Start Guide

## What's Been Implemented

✅ **Option A: Smart Intersection Optimizer** - A complete traffic simulation system with:

### Core Components

1. **Simulation Engine** (`IntersectionSimulator`)
   - Discrete-event simulation at 10 Hz
   - Poisson vehicle arrivals
   - Queue management for 4 directions (N, S, E, W)
   - Signal plan execution with phase timing
   - Metrics calculation (delay, throughput, queue length, stops)

2. **MCP Server** (`IntersectionMcpServer`)
   - 4 MCP tools for LLM interaction:
     - `intersection_reset` - Initialize simulation
     - `intersection_evaluate_plan` - Test signal plans
     - `intersection_apply_plan` - Apply optimized plan
     - `intersection_get_state` - Monitor current state

3. **JavaFX UI** (`TrafficSimApp`)
   - Real-time intersection visualization
   - Interactive arrival rate sliders
   - Performance metrics display
   - Control buttons (Reset, Rush Hour Spike, Baseline, Optimize)

## Running the Application

### 1. Run the Visual Simulator

```bash
./gradlew run
```

**What you'll see:**
- Animated intersection with cars and traffic lights
- Control panel with arrival rate sliders (N, S, E, W)
- **Simulation speed control** (0.5x - 10x) - speed up time for faster demos!
- **Live performance metrics** (auto-updated every 2 seconds):
  - Average delay (seconds)
  - Queue P95 (vehicles)
  - Throughput (vehicles/hour)
  - Stops per vehicle

**Try this:**
1. Click "Baseline Plan" to apply a simple 60s cycle
2. **Adjust arrival rate sliders in real-time** - watch queues grow/shrink immediately!
   - Try setting North to 40, others to 0 - see all traffic from one direction
   - Try setting all to 30 - create heavy congestion
3. **Set speed to 5x or 10x** to see faster results
4. **Watch metrics update automatically** every 2 seconds
5. Click "Update Metrics" for an instant refresh
6. Click "Rush Hour Spike" to double traffic for 30 seconds and see metrics change
7. Click "LLM Optimize" to find the best signal plan for current traffic

### 2. Run the MCP Server (for LLM)

```bash
./gradlew runServer
```

**What it does:**
- Starts stdio-based MCP server
- Exposes 4 tools for optimization
- Ready to connect with Claude Desktop or other MCP clients

### 3. Build Standalone Server JAR

```bash
./gradlew serverJar
java -jar build/libs/traffic-sim-mcp-server-all.jar
```

## Example MCP Tool Usage

### Reset Simulation

```json
{
  "tool": "intersection_reset",
  "arguments": {
    "seed": 12345,
    "arrivals": {
      "N": 15,
      "S": 15,
      "E": 10,
      "W": 10
    }
  }
}
```

### Evaluate a Signal Plan

```json
{
  "tool": "intersection_evaluate_plan",
  "arguments": {
    "plan": {
      "cycleSeconds": 80,
      "phases": [
        {"name": "NS_through", "greenSeconds": 35},
        {"name": "EW_through", "greenSeconds": 35}
      ],
      "yellowSeconds": 3,
      "allRedSeconds": 1
    },
    "durationSeconds": 120,
    "replications": 3
  }
}
```

**Returns:**
```json
{
  "metrics": {
    "avgDelaySec": 23.8,
    "queueP95": 9.0,
    "throughputVph": 1560,
    "stopsPerVeh": 1.4
  }
}
```

## LLM Optimization Workflow

### Prompt Template for Claude

```
You are optimizing traffic signal timing for a 4-way intersection. Your goal is to minimize average vehicle delay.

Available tools:
- intersection_reset: Initialize simulation with seed and arrival rates
- intersection_evaluate_plan: Test a signal plan (returns metrics)
- intersection_apply_plan: Apply the best plan found
- intersection_get_state: Check current intersection state

Current traffic demand:
- North: 15 veh/min
- South: 15 veh/min
- East: 10 veh/min
- West: 10 veh/min

Strategy:
1. Reset with seed 12345 and the above arrival rates
2. Test 6-10 diverse signal plans:
   - Vary cycle length: 40-120 seconds
   - Vary N-S green split: 20-50 seconds
   - Vary E-W green split accordingly
3. Identify the best plan (lowest avgDelaySec)
4. Refine with local search (±10% adjustments)
5. Apply the optimized plan

Constraints:
- Cycle length: 30-180 seconds
- Green time per phase: 6-120 seconds
- Yellow: 3 seconds (fixed)
- All-red: 1 second (fixed)

Start optimizing!
```

### Expected Results

**Baseline Plan** (equal splits):
- Cycle: 60s
- Phases: NS=25s, EW=25s
- Avg delay: ~25-30s

**Optimized Plan** (favoring N-S):
- Cycle: 70-80s
- Phases: NS=35s, EW=25s
- Avg delay: ~18-22s (20-30% improvement)

## Demo Script (5 minutes)

1. **Show Baseline** (1 min)
   - Run UI: `./gradlew run`
   - **Set speed to 5x** for faster demo
   - Click "Baseline Plan"
   - Note metrics

2. **LLM Optimizes** (2 min)
   - In Claude Desktop with MCP configured
   - Give optimization prompt
   - Watch it evaluate multiple plans

3. **Compare Results** (1 min)
   - Show improved metrics
   - **Speed up to 10x** for dramatic effect
   - Trigger "Rush Hour Spike"
   - Watch LLM re-optimize

4. **Q&A** (1 min)
   - Explain how MCP enables real-time optimization
   - Show tool definitions in code

## Architecture Highlights

```
┌─────────────┐
│  LLM (via   │
│  MCP Client)│
└──────┬──────┘
       │ stdio/JSON-RPC
┌──────▼──────────────┐
│  MCP Server         │
│  (IntersectionMcp   │
│   Server)           │
└──────┬──────────────┘
       │
┌──────▼──────────────┐
│  Simulation Engine  │
│  (Intersection      │
│   Simulator)        │
└─────────────────────┘
       │
┌──────▼──────────────┐
│  JavaFX UI          │
│  (TrafficSimApp)    │
└─────────────────────┘
```

## Next Steps

1. **Connect to Claude Desktop**:
   - Add MCP server config to Claude Desktop
   - Test optimization with the prompt above

2. **Extend Functionality**:
   - Add left-turn phases
   - Implement actuated signal control
   - Add pedestrian crossings

3. **Improve Visualization**:
   - Add time-series charts for metrics
   - Show multiple plan comparisons
   - Animate vehicles more realistically

Enjoy optimizing! 🚦
