# Quick Setup: Connect Claude Desktop to Your Intersection Optimizer

⚠️ **IMPORTANT**: This guide uses the **stdio mode with UI** approach, which is the ONLY way to connect Claude Desktop. HTTP mode is NOT compatible with Claude Desktop.

## Goal
Prove that Claude (the LLM) can:
1. Get current arrival rates and metrics
2. Analyze the traffic pattern
3. Suggest optimal N-S and E-W green times
4. Apply the timing to your running simulation
5. **See everything happening live in the JavaFX UI!**

## Step 1: Build the Runtime (Recommended) or Use Development Mode

### Option A: Self-Contained Runtime (Recommended for Production)

Build a standalone application with bundled JRE and JavaFX:

```bash
cd /mnt/c/Dev/repos/MCP-Playground/mcp-sim
./gradlew createRuntime
```

This creates a self-contained application in `build/jpackage/mcp-sim/` that includes:
- Native launcher with bundled JRE
- All JavaFX runtime components
- No separate Java installation required!

### Option B: Development Mode (Simpler, Requires Gradle)

Skip building - use Gradle directly (requires Java + Gradle installed).

## Step 2: Configure Claude Desktop

### Find the Config File

**Windows:**
```
%APPDATA%\Claude\claude_desktop_config.json
```

**macOS:**
```
~/Library/Application Support/Claude/claude_desktop_config.json
```

**Linux:**
```
~/.config/Claude/claude_desktop_config.json
```

### Edit the Config

⚠️ **Use stdio mode** - Claude Desktop does NOT support HTTP transport!

### Option A: Self-Contained Runtime (Recommended)

**For WSL/Linux:**
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

**For Windows:**
```json
{
  "mcpServers": {
    "traffic-sim-ui": {
      "command": "C:\\Dev\\repos\\MCP-Playground\\mcp-sim\\build\\jpackage\\mcp-sim\\bin\\mcp-sim.exe",
      "args": ["--mcp-mode", "stdio"]
    }
  }
}
```

**For macOS:**
```json
{
  "mcpServers": {
    "traffic-sim-ui": {
      "command": "/Users/yourname/dev/MCP-Playground/mcp-sim/build/jpackage/mcp-sim/bin/mcp-sim",
      "args": ["--mcp-mode", "stdio"]
    }
  }
}
```

### Option B: Development Mode (Gradle)

**For WSL/Linux:**
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

**For Windows:**
```json
{
  "mcpServers": {
    "traffic-sim-ui": {
      "command": "gradlew.bat",
      "args": ["run", "--args=--mcp-mode stdio", "--console=plain", "--quiet"],
      "cwd": "C:\\Dev\\repos\\MCP-Playground\\mcp-sim"
    }
  }
}
```

**Important:**
- Option A uses **absolute path** to the runtime executable
- Option B uses **cwd** (current working directory) - adjust to your actual project location

### Step 3: Restart Claude Desktop

Completely quit and restart Claude Desktop to load the new configuration.

## Step 4: Verify Connection

In Claude Desktop, start a new conversation and type:

```
What MCP tools do you have access to?
```

🎉 **A JavaFX UI window should appear!** This is the traffic intersection visualization.

Claude should respond with something like:
```
I have access to these MCP tools from the traffic-sim-ui server:
- intersection_reset
- intersection_evaluate_plan
- intersection_apply_plan
- intersection_get_state
```

**In the UI window, you should see:**
- Status bar showing: "Server: Running (stdio mode)"
- Empty MCP log panel (ready for tool calls)
- Traffic intersection visualization
- Control sliders for arrival rates
- Metrics panel (will update when simulation runs)

✅ **If you see the UI window and Claude lists the tools, MCP is connected!**

## Step 5: Run the Optimization

### 5b. Give Claude This Prompt

```
I have a traffic intersection with these arrival rates:
- North: 25 veh/min
- South: 10 veh/min
- East: 8 veh/min
- West: 8 veh/min

Please:
1. Reset the simulation with these rates (seed: 12345)
2. Evaluate multiple signal plans to find the optimal N-S and E-W green times
3. Tell me the best green times and the expected delay
4. Apply the optimal plan
```

### What Claude Will Do

**Step 1: Reset**
```
I'll reset the simulation with those arrival rates.

[Calls intersection_reset with seed=12345 and your arrival rates]

✓ Simulation reset successfully with the specified traffic demand.
```

**Step 2: Evaluate Plans**
```
Now I'll test several signal timing configurations to find the optimal plan.

Testing Plan 1: NS=30s, EW=30s (balanced)
[Calls intersection_evaluate_plan]
Results: Avg delay = 21.5s, Queue P95 = 8.5

Testing Plan 2: NS=40s, EW=20s (NS priority)
[Calls intersection_evaluate_plan]
Results: Avg delay = 18.2s, Queue P95 = 7.8 ← Better!

Testing Plan 3: NS=45s, EW=15s (strong NS priority)
[Calls intersection_evaluate_plan]
Results: Avg delay = 19.1s, Queue P95 = 7.2

Testing Plan 4: NS=35s, EW=25s (medium)
[Calls intersection_evaluate_plan]
Results: Avg delay = 19.8s, Queue P95 = 8.1

Testing Plan 5: NS=38s, EW=22s (refined)
[Calls intersection_evaluate_plan]
Results: Avg delay = 18.5s, Queue P95 = 7.9
```

**Step 3: Recommend**
```
Based on my analysis, the optimal signal timing is:

**N-S Green Time: 40 seconds**
**E-W Green Time: 20 seconds**

This configuration achieves:
- Average delay: 18.2 seconds
- Queue P95: 7.8 vehicles
- Throughput: 1,720 veh/hr

The 2:1 ratio favoring N-S makes sense because:
- N-S has 35 veh/min combined demand
- E-W has 16 veh/min combined demand
- Ratio is approximately 2.2:1
```

**Step 4: Apply**
```
I'll apply this optimal plan now.

[Calls intersection_apply_plan with NS=40s, EW=20s]

✓ Signal plan applied successfully!
The intersection is now operating with the optimized timing.
```

### 5c. Watch the UI in Real-Time!

**As Claude works, you'll see in the UI:**
- **MCP Log Panel** shows each tool call Claude makes (e.g., `[TOOL CALL] intersection_evaluate_plan`)
- **Metrics Panel** updates with delay, throughput, and queue lengths after each plan evaluation
- **Signal visualization** changes when Claude applies the optimal plan
- **Status bar** shows "Signal plan applied successfully" when Claude applies timing

This is the magic of stdio mode with UI - you get BOTH Claude Desktop AND the visualization!

## Step 6: Test Dynamic Optimization

Now try changing traffic:

```
Traffic just changed! North is now 40 veh/min and South is 15 veh/min.
East and West are still 8 veh/min each.

Please re-optimize the signal timing for this new pattern.
```

Claude will:
1. Recognize the traffic pattern shifted
2. Test new plans with even more N-S priority
3. Suggest something like NS=50s, EW=15s
4. Apply the new plan

**Watch the UI update in real-time!**

## Proof That It Works

### In Claude Desktop:
- ✅ You see Claude calling MCP tools
- ✅ Claude reports metrics it receives
- ✅ Claude suggests specific N-S and E-W times
- ✅ Claude explains its reasoning

### In the UI:
- ✅ Log shows `[TOOL CALL] intersection_evaluate_plan: cycle=70s...`
- ✅ Log shows `[SUCCESS] intersection_evaluate_plan: {"metrics": {...}}`
- ✅ Signal visualization changes when plan is applied
- ✅ Metrics panel shows new delay/throughput numbers

### This Proves:
1. ✅ LLM can read arrival rates and metrics via MCP
2. ✅ LLM can test multiple signal plans
3. ✅ LLM can analyze results and find optimal timing
4. ✅ LLM can apply the optimal N-S and E-W green times
5. ✅ The changes actually affect the running simulation

## Troubleshooting

### "I don't see the MCP tools"
- Check the path in `claude_desktop_config.json` is correct
- Make sure you ran: `./gradlew installDist`
- Use full absolute path to the `mcp-sim` script
- Restart Claude Desktop completely (quit, don't just close window)
- Check Claude Desktop logs (Help → Show Logs)

### "No UI window appears"
- Verify Java 17+ with JavaFX is installed
- Check stderr output for JavaFX errors
- On Linux/WSL: Ensure X server is running for GUI apps
- Try running manually: `build/install/mcp-sim/bin/mcp-sim --mcp-mode stdio`

### "UI appears but no tool calls shown"
- This is normal until Claude starts calling tools
- Try asking Claude to reset the intersection or evaluate a plan
- Check that Claude actually listed the MCP tools in Step 4

### "Can't find the config file"
- Windows: Press Win+R, type `%APPDATA%`, navigate to Claude folder
- macOS: Press Cmd+Shift+G in Finder, paste `~/Library/Application Support/Claude`
- Linux: `~/.config/Claude/`

### "HTTP transport not working"
❌ **Claude Desktop does NOT support HTTP transport**. You must use stdio mode as shown in this guide. HTTP mode is only for API testing with curl/Postman.

## Next Steps

Once this is working, try:

1. **Different traffic patterns:**
   ```
   Test these 3 scenarios and tell me which timing works best overall:
   - Morning: N=30, S=15, E=10, W=8
   - Evening: N=12, S=25, E=18, W=15
   - Midday: N=15, S=15, E=15, W=15
   ```

2. **Constrained optimization:**
   ```
   Find optimal timing with these constraints:
   - Average delay must be < 20 seconds
   - Queue P95 must be < 10 vehicles
   - N-S green time must be >= 30s (policy requirement)
   ```

3. **What-if analysis:**
   ```
   What would happen to delay if we increased North traffic to 50 veh/min?
   ```

## The Power of MCP

You've now proven that:
- ✅ An LLM can control your traffic intersection
- ✅ It can optimize based on real data
- ✅ It can explain its reasoning
- ✅ It can adapt to changing conditions
- ✅ All through standard MCP tools

**This is what MCP enables: AI assistants with real-world capabilities! 🚦🤖**
