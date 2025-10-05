# Quick Start Guide - Traffic Sim with Claude Desktop

Get up and running with the Traffic Simulation UI and Claude Desktop in 5 minutes!

## Step 1: Start the Application

```bash
./gradlew run
```

Wait for the UI to appear. You should see:
- A traffic intersection visualization
- A status bar at the bottom showing: **"Server: Running on http://localhost:8083/mcp"**
- Control panels on the left and right
- A log panel at the bottom

![Status Bar Example]
```
Server: Running (http://localhost:8083/mcp) | Ready
```

## Step 2: Configure Claude Desktop

### Find Your Config File

**macOS:**
```bash
open ~/Library/Application\ Support/Claude/
```

**Windows:**
```
%APPDATA%\Claude\
```

**Linux:**
```bash
~/.config/Claude/
```

### Edit `claude_desktop_config.json`

Add this configuration (or create the file if it doesn't exist):

```json
{
  "mcpServers": {
    "traffic-sim": {
      "transport": {
        "type": "http",
        "url": "http://localhost:8083/mcp"
      }
    }
  }
}
```

**Important:** Make sure the JSON is valid! Use a JSON validator if unsure.

## Step 3: Restart Claude Desktop

1. Quit Claude Desktop completely (check system tray/menu bar)
2. Restart Claude Desktop
3. Look for the MCP icon in Claude Desktop
4. The "traffic-sim" server should appear with 4 tools:
   - intersection_reset
   - intersection_evaluate_plan
   - intersection_apply_plan
   - intersection_get_state

## Step 4: Test It!

In Claude Desktop, try this prompt:

```
Can you check the current state of the traffic intersection?
```

Claude should call the `intersection_get_state` tool and show you:
- Current simulation time
- Current signal phase
- Queue lengths for each direction (N, S, E, W)

**Watch the UI!** You should see the tool call appear in the log panel at the bottom.

## Step 5: Optimize Traffic!

Try this prompt:

```
Can you optimize the traffic signal timing?
Test a few different configurations and apply the best one.
```

Claude will:
1. Evaluate multiple signal plans
2. Compare their performance
3. Apply the best plan
4. You'll see all of this happening in the UI log panel!

## What to Watch For

### In the UI:
- **Log Panel**: Shows all tool calls (bottom of window)
- **Metrics Panel**: Updates every 2 seconds with performance data
- **Status Bar**: Shows server status and messages
- **Intersection View**: See vehicles and signals update in real-time

### In Claude Desktop:
- Tool calls appear with their results
- Claude analyzes the metrics
- Claude makes decisions about which plans to try

## Common Issues

### "Server not found" in Claude Desktop

**Fix:**
1. Make sure the UI is still running (`./gradlew run`)
2. Check the status bar shows "Server: Running"
3. Verify the URL in config is exactly: `http://localhost:8083/mcp`
4. Restart Claude Desktop

### Tools not appearing

**Fix:**
1. Wait 10-20 seconds after Claude Desktop starts
2. Check the JSON config is valid
3. Try restarting Claude Desktop again

### Port 8083 already in use

**Fix:**
1. Stop any other applications using port 8083
2. Or change the port:
   - Edit `McpConfig.java` line 90: `private int httpPort = 8083;`
   - Change to a different port (e.g., 8084)
   - Rebuild: `./gradlew build`
   - Update Claude Desktop config to match

## Example Conversation

```
You: What's the current traffic situation?

Claude: Let me check the intersection state.
[Calls intersection_get_state]

Currently:
- Time: 23.4 seconds
- Phase: 0 (North-South through)
- Queues: N=2, S=3, E=4, W=5 vehicles

You: Can you improve the signal timing?

Claude: I'll evaluate several signal plans to find the optimal timing.
[Calls intersection_evaluate_plan multiple times]

I tested 5 different configurations:
- 60s cycle, 25/25 split: 14.2s avg delay
- 80s cycle, 35/35 split: 12.8s avg delay ← Best!
- 100s cycle, 45/45 split: 13.5s avg delay

Applying the best plan now...
[Calls intersection_apply_plan]

Done! The new timing should reduce average delay by ~10%.
```

## Try These Prompts

**Basic:**
- "What is the current state of the intersection?"
- "Show me the current queue lengths"

**Optimization:**
- "Optimize the signal timing to minimize delays"
- "Test different cycle lengths and find the best one"
- "Compare a 60-second vs 90-second cycle"

**Advanced:**
- "Set up the simulation with higher north-south traffic, then optimize"
- "Find a signal plan that balances delays across all directions"
- "What happens if we increase the cycle length by 20%?"

## Using Both UI and Claude Desktop

You can use both at the same time!

1. **UI's "LLM Optimize" Button**: Tests pre-defined plans
2. **Claude Desktop**: Intelligent exploration and optimization
3. **Watch them both work**: Changes from either appear in the log panel

## Next Steps

Once you're comfortable with the basics:

1. Read [CLAUDE_DESKTOP_CONFIG.md](CLAUDE_DESKTOP_CONFIG.md) for advanced configuration
2. See [README.md](README.md) for detailed tool documentation
3. Check [IMPLEMENTATION_SUMMARY.md](IMPLEMENTATION_SUMMARY.md) for technical details

## Need Help?

**UI not starting?**
- Check Java version: `java -version` (need 17+)
- Try: `./gradlew clean build run`

**Claude Desktop issues?**
- Check Claude Desktop logs
- Verify JSON config syntax
- Try removing and re-adding the server config

**Server issues?**
- Run test script: `./test-http-server.sh`
- Check console output for errors
- Verify port 8083 is available

---

**Happy optimizing!** Watch as Claude learns to manage traffic flow in real-time.
