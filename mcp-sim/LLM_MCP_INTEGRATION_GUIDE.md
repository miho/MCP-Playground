# Using an LLM to Optimize Traffic Signals via MCP

## What the LLM Can Actually Do

When you connect Claude (or another LLM) to your MCP server, it can:

1. **Get current intersection state** - arrival rates, queue lengths, current timing
2. **Evaluate signal plans** - test different N-S and E-W green times
3. **Analyze metrics** - understand delay, throughput, queues
4. **Suggest optimal timing** - recommend N-S and E-W green times
5. **Apply the plan** - actually change the signal timing

## The MCP Tools Available

Your `IntersectionMcpServer` exposes 4 tools:

### 1. `intersection_get_state`
**What it does:** Returns current traffic conditions
**Returns:**
```json
{
  "simTime": 45.3,
  "currentPhase": 0,
  "signalState": "green",
  "queueLengths": {
    "N": 8,
    "S": 3,
    "E": 2,
    "W": 1
  }
}
```

### 2. `intersection_reset`
**What it does:** Reset simulation with specific arrival rates
**Input:**
```json
{
  "seed": 12345,
  "arrivals": {
    "N": 15,
    "S": 10,
    "E": 8,
    "W": 5
  }
}
```

### 3. `intersection_evaluate_plan`
**What it does:** Test a signal plan and return performance metrics
**Input:**
```json
{
  "plan": {
    "cycleSeconds": 70,
    "phases": [
      {"name": "NS_through", "greenSeconds": 35},
      {"name": "EW_through", "greenSeconds": 25}
    ],
    "yellowSeconds": 3,
    "allRedSeconds": 1
  },
  "durationSeconds": 120,
  "replications": 3
}
```
**Returns:**
```json
{
  "metrics": {
    "avgDelaySec": 18.5,
    "queueP95": 7.0,
    "throughputVph": 1680,
    "stopsPerVeh": 1.3
  }
}
```

### 4. `intersection_apply_plan`
**What it does:** Apply a signal plan to the running simulation
**Input:** Same as evaluate_plan
**Returns:** Success message

## Setting Up Claude Desktop to Connect

### Step 1: Start Your MCP Server

**Option A: Standalone server (recommended for Claude Desktop)**
```bash
cd /mnt/c/Dev/repos/MCP-Playground/mcp-sim
./gradlew runServer
```

This starts the server in stdio mode, ready for MCP connections.

**Option B: Run the UI (server starts automatically)**
```bash
./gradlew run
```
The server runs on port 8083 (HTTP mode).

### Step 2: Configure Claude Desktop

Edit Claude Desktop's MCP config file:

**Location:**
- macOS: `~/Library/Application Support/Claude/claude_desktop_config.json`
- Windows: `%APPDATA%\Claude\claude_desktop_config.json`

**Add this configuration:**
```json
{
  "mcpServers": {
    "traffic-optimizer": {
      "command": "java",
      "args": [
        "-jar",
        "C:/Dev/repos/MCP-Playground/mcp-sim/build/libs/traffic-sim-mcp-server-1.0.0-all.jar"
      ]
    }
  }
}
```

**Note:** Adjust the path to match your system!

### Step 3: Restart Claude Desktop

After saving the config, restart Claude Desktop. You should see the MCP server tools available.

## Example Prompts for the LLM

### Prompt 1: Get Current State

```
I have a traffic intersection simulator connected via MCP.
Can you check the current state of the intersection?
```

**What Claude will do:**
```
I'll use the intersection_get_state tool to check the current conditions.

[Claude calls: intersection_get_state()]

The intersection is currently at 45.3 seconds of simulation time.
The signal is green for phase 0 (North-South).
Current queue lengths:
- North: 8 vehicles
- South: 3 vehicles
- East: 2 vehicles
- West: 1 vehicle

It looks like North has significant congestion!
```

### Prompt 2: Optimize for Current Traffic

```
The intersection has these arrival rates:
- North: 25 vehicles/min
- South: 10 vehicles/min
- East: 8 vehicles/min
- West: 8 vehicles/min

Can you find the optimal signal timing (N-S green time and E-W green time)
to minimize average delay?
```

**What Claude will do:**
```
I'll test several signal timing configurations to find the optimal plan.

Let me evaluate 5 different plans:
1. Balanced (30s/30s)
2. NS Priority (40s/20s)
3. Strong NS Priority (45s/15s)
4. Medium cycle (35s/25s)
5. Long cycle (40s/30s)

[Claude calls: intersection_evaluate_plan() for each plan]

Plan 1 (NS=30s, EW=30s):
- Average delay: 22.3s
- Queue P95: 9 vehicles
- Score: 49.1

Plan 2 (NS=40s, EW=20s):
- Average delay: 18.7s
- Queue P95: 8 vehicles
- Score: 41.4 ← BEST!

Plan 3 (NS=45s, EW=15s):
- Average delay: 19.2s
- Queue P95: 7 vehicles
- Score: 41.9

Plan 4 (NS=35s, EW=25s):
- Average delay: 20.1s
- Queue P95: 8 vehicles
- Score: 44.2

Plan 5 (NS=40s, EW=30s):
- Average delay: 21.5s
- Queue P95: 9 vehicles
- Score: 47.5

**Optimal Timing:**
- N-S Green Time: 40 seconds
- E-W Green Time: 20 seconds
- Expected average delay: 18.7 seconds

This prioritizes North-South (which has 35 veh/min combined)
over East-West (which has 16 veh/min).

Would you like me to apply this plan?
```

### Prompt 3: Apply the Optimal Plan

```
Yes, please apply the optimal plan you found.
```

**What Claude will do:**
```
I'll apply the optimal signal plan now.

[Claude calls: intersection_apply_plan({
  plan: {
    cycleSeconds: 68,
    phases: [
      {name: "NS_through", greenSeconds: 40},
      {name: "EW_through", greenSeconds: 20}
    ],
    yellowSeconds: 3,
    allRedSeconds: 1
  }
})]

✓ Signal plan applied successfully!

The intersection now operates with:
- Cycle length: 68 seconds
- N-S green: 40 seconds (58.8% of cycle)
- E-W green: 20 seconds (29.4% of cycle)
- Yellow + all-red: 8 seconds (11.8% of cycle)

The new timing should reduce average delay from ~22s to ~18.7s.
```

### Prompt 4: Handle Dynamic Changes

```
Traffic has increased! North is now 40 veh/min and South is 15 veh/min.
East and West are still 8 veh/min each. Can you re-optimize?
```

**What Claude will do:**
```
The traffic pattern has changed significantly. Let me re-evaluate.

Current demand:
- N-S: 55 veh/min (was 35)
- E-W: 16 veh/min (same)

This is a 3.4:1 ratio favoring N-S. Let me test new configurations:

[Claude tests more aggressive N-S priority plans]

Plan 1 (NS=50s, EW=15s):
- Average delay: 16.2s
- Queue P95: 8 vehicles
- Score: 36.4 ← NEW BEST!

Plan 2 (NS=45s, EW=20s):
- Average delay: 17.8s
- Queue P95: 9 vehicles
- Score: 40.1

**New Optimal Timing:**
- N-S Green Time: 50 seconds (up from 40s)
- E-W Green Time: 15 seconds (down from 20s)
- Expected average delay: 16.2 seconds (improved!)

The system adapts to the heavier North traffic by allocating more green time.

Shall I apply this new plan?
```

## What Makes This Powerful

### 1. **The LLM Reasons About Traffic**

Unlike hardcoded optimization, the LLM:
- Understands traffic engineering concepts
- Explains its reasoning
- Adapts its testing strategy based on traffic patterns
- Can handle edge cases ("What if there's an emergency vehicle?")

### 2. **Interactive Optimization**

You can have a conversation:
```
User: "That's good, but I'm concerned about East-West getting only 15s.
       What if we give them 18s instead?"

Claude: "Good point! Let me test NS=48s, EW=18s to compare..."
```

### 3. **Multi-Objective Optimization**

```
User: "Minimize delay, but also keep queue P95 below 10 vehicles."

Claude: "I'll search for plans that meet both criteria..."
```

### 4. **Explanation and Education**

```
User: "Why did you choose 40s for N-S instead of 45s?"

Claude: "Good question! While 45s would give slightly more capacity to N-S,
         it would increase the cycle length to 73s. The longer cycle means
         vehicles arriving late in the red phase wait longer. The 40s plan
         balances capacity with shorter cycles, resulting in lower average
         delay overall."
```

## Real-World Demo Flow

### Setup
1. Start the MCP server: `./gradlew runServer`
2. Configure Claude Desktop with the server path
3. Restart Claude Desktop
4. Open the UI in another window: `./gradlew run`

### Demo Script

**In Claude Desktop:**
```
User: "Check the current intersection state"
Claude: [calls intersection_get_state, reports queues]

User: "The traffic is N=25, S=10, E=8, W=8. Find optimal signal timing."
Claude: [tests multiple plans, finds optimal NS=40s, EW=20s]

User: "Apply that plan"
Claude: [calls intersection_apply_plan]
```

**In the UI window:**
- Watch the signal timing change in real-time!
- See the log panel show Claude's tool calls
- Observe metrics improve as the new plan takes effect

**Back in Claude Desktop:**
```
User: "North traffic just spiked to 40! Re-optimize."
Claude: [re-tests, finds NS=50s, EW=15s]

User: "Apply it"
Claude: [applies new plan]
```

**In the UI:**
- Signal timing changes again
- Queues stabilize
- Metrics show improvement

## Verification

### How to Know It's Working

1. **In Claude Desktop:**
   - You see tool calls like `[intersection_evaluate_plan]`
   - Claude explains the metrics it receives
   - Claude suggests specific N-S and E-W times

2. **In the UI:**
   - Log panel shows: `[TOOL CALL] intersection_evaluate_plan: cycle=70s, NS=35s, EW=25s`
   - Log panel shows: `[SUCCESS] intersection_evaluate_plan: {"metrics": ...}`
   - Status bar updates when plan is applied

3. **In the simulation:**
   - Signal timing actually changes (watch the visualization)
   - Metrics panel shows new performance numbers
   - Queue lengths change according to the new plan

## Advanced Prompts

### Multi-Scenario Testing
```
Test these 3 scenarios and tell me which signal plan works best across all:
1. Morning rush: N=30, S=15, E=10, W=8
2. Evening rush: N=12, S=25, E=18, W=15
3. Midday: N=15, S=15, E=15, W=15
```

### Constraint-Based Optimization
```
Find the optimal timing with these constraints:
- Average delay < 20 seconds
- Queue P95 < 12 vehicles
- N-S green time >= 30s (policy requirement)
```

### Adaptive Strategy
```
Create a time-of-day signal schedule:
- 7-9am: Morning rush pattern
- 9am-4pm: Balanced pattern
- 4-6pm: Evening rush pattern
Give me the optimal timing for each period.
```

## The Power of MCP

This demonstrates MCP's value:

1. **LLM has tools** - can query state and test plans
2. **LLM has domain knowledge** - understands traffic engineering
3. **LLM is interactive** - you can guide and constrain the optimization
4. **LLM explains itself** - not a black box
5. **Integration is seamless** - LLM calls tools just like it would search the web

**You've built a traffic engineering assistant powered by Claude! 🚦🤖**
