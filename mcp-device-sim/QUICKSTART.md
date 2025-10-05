# Quick Start Guide - Device Simulator

Get up and running in 5 minutes!

## 🎯 What You'll Build

A device simulation that moves a head to filtered CSV locations with MCP integration for LLM control.

## 📦 Step 1: Prerequisites

```bash
# Check Java version (need 17+)
java -version

# Navigate to project
cd mcp-device-sim
```

## 🚀 Step 2: Run the Application

```bash
# Option 1: Default (HTTP MCP on port 8084)
./gradlew run

# Option 2: Custom port
./gradlew run --args="--mcp-port 9000"

# Option 3: STDIO mode (for Claude Desktop)
./gradlew run --args="--mcp-mode stdio"
```

## 📊 Step 3: Load Sample Data

1. Click **"Browse"** button
2. Navigate to `data/sample_particles.csv`
3. Select **X column**: `x`
4. Select **Y column**: `y`

## 🎯 Step 4: Filter Locations

1. Click **"Add Filter"**
2. Select column: **circularity**
3. Set **Min**: `0.9` (finds highly circular particles)
4. Click **"Load Locations"**

Watch the canvas populate with filtered targets!

## 🎮 Step 5: Start Movement

1. Adjust **Speed** slider (try 50 units/s)
2. Adjust **Acceleration** slider (try 30 units/s²)
3. Click **"Start"** button
4. Watch the device smoothly move to each target!

## 🤖 Step 6: Connect an LLM (Optional)

### For Claude Desktop

1. Build the server JAR:
```bash
./gradlew serverJar
```

2. Add to Claude Desktop config (`~/Library/Application Support/Claude/claude_desktop_config.json` on Mac):
```json
{
  "mcpServers": {
    "device-sim": {
      "command": "java",
      "args": [
        "-jar",
        "/full/path/to/mcp-device-sim/build/libs/device-sim-mcp-server-all.jar"
      ]
    }
  }
}
```

3. Restart Claude Desktop

4. Ask Claude:
   - "What columns are in the device CSV?"
   - "Move to particles with circularity above 0.9"
   - "Set device speed to 75 units per second"

### For HTTP Clients

The MCP server runs at `http://localhost:8084/mcp` by default.

Test with curl:
```bash
# Get CSV headers
curl -X POST http://localhost:8084/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "method": "tools/call",
    "params": {
      "name": "csv_get_headers",
      "arguments": {"filePath": "data/sample_particles.csv"}
    },
    "id": 1
  }'
```

## 🎨 Step 7: Explore the UI

### Canvas View (Center)
- **Blue Circle**: Device head
- **Gray Circles**: Pending targets
- **Teal Pulsing**: Current target
- **Green with ✓**: Visited targets

### Control Panel (Left)
- CSV file selection
- Column mapping
- Dynamic filters
- Speed/acceleration controls
- Movement controls

### Location List (Right)
- Table showing all targets
- Status indicators
- Summary statistics

### Log Panel (Bottom)
- MCP tool calls
- Device actions
- Success/error messages

### Status Bar (Bottom)
- Current position
- Current target
- Speed
- MCP server status

## 🔧 Common Tasks

### Change Speed During Movement
Just drag the speed slider - changes apply immediately!

### Skip to Next Target
Click **"Mark Visited"** to mark current as done and move to next.

### Reset Everything
Click **"Reset"** to clear targets and return device to origin.

### Add More Filters
1. Click **"Add Filter"** again
2. Combine multiple criteria (e.g., circularity AND area)
3. Reload locations

### Try Different Data
Create your own CSV with any columns containing x, y coordinates!

## 📝 Sample Filter Scenarios

### Highly Circular Particles
- Column: `circularity`
- Min: `0.9`, Max: `1.0`

### Large Particles
- Column: `area`
- Min: `250`, Max: `350`

### Combined Filters
- Filter 1: `circularity` min `0.9`
- Filter 2: `area` min `200`

### Exact Match
- Column: `id`
- Equals: `particle_010`

## 🐛 Troubleshooting

### Port Already in Use
```bash
# Use different port
./gradlew run --args="--mcp-port 9000"
```

### No Locations Appear
- Check CSV has x,y columns selected
- Try relaxing filter criteria
- Check CSV file path is correct

### Device Not Moving
- Click "Start" button
- Check speed is above 0
- Ensure locations are loaded

### MCP Server Won't Start
- Check port is available
- Try STDIO mode: `--mcp-mode stdio`
- Disable MCP: `--mcp-enabled false`

## 🎤 Demo Script (5 Minutes)

**Minute 1:** Run app, load sample data
```bash
./gradlew run
# Load data/sample_particles.csv
```

**Minute 2:** Show dynamic discovery
- "LLM doesn't know the columns ahead of time"
- Click Browse, show x/y column selection
- Show available filter columns

**Minute 3:** Filter and visualize
- Add circularity filter (0.9-1.0)
- Load locations
- Show filtered results on canvas

**Minute 4:** Demonstrate movement
- Set speed to 50
- Click Start
- Show smooth animation
- Mark visited, show auto-advance

**Minute 5:** LLM integration
- Show MCP server running
- Connect Claude
- Ask Claude to query columns
- Ask Claude to filter and move device

**Key Points:**
✅ CSV schema discovered at runtime
✅ Flexible filtering without prior knowledge
✅ Smooth realistic physics
✅ Full MCP integration
✅ Beautiful, responsive UI

## 📚 Next Steps

- Read the full [README.md](README.md) for architecture details
- Check [IMPLEMENTATION.md](IMPLEMENTATION.md) for technical deep-dive
- Explore the MCP tools in the server code
- Customize the UI styling in `src/main/resources/css/application.css`
- Create custom CSV data files

## 🎉 You're Ready!

Your device simulation is now running with full MCP integration. Perfect for demonstrating:
- JavaFX + MCP integration
- LLM-controlled devices
- Dynamic data filtering
- Smooth 2D animations

**Happy demoing!** 🚀
