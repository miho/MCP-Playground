# Device Simulator - MCP Integration Demo

A JavaFX-based device simulation system with Model Context Protocol (MCP) integration, perfect for demonstrating LLM-powered automation and control of physical devices.

![Java](https://img.shields.io/badge/Java-17-orange)
![JavaFX](https://img.shields.io/badge/JavaFX-21-blue)
![MCP](https://img.shields.io/badge/MCP-0.13.1-green)
![License](https://img.shields.io/badge/License-MIT-yellow)

## 🎯 Overview

This project demonstrates how to integrate the Model Context Protocol (MCP) with JavaFX applications. It simulates a device head that moves to target locations based on CSV data, with intelligent filtering capabilities that an LLM can discover and use dynamically.

**Perfect for talks and demos on:**
- MCP integration with JavaFX
- LLM-controlled device automation
- Dynamic data filtering without prior knowledge
- Smooth 2D animation with realistic physics

## ✨ Features

### 🤖 MCP Integration
- **Dual Transport**: Supports both HTTP and STDIO MCP servers
- **8 MCP Tools**:
  - `csv_get_headers` - Query CSV column names
  - `csv_query_locations` - Filter data by circularity, area, etc.
  - `device_get_state` - Get current device position and status
  - `device_set_targets` - Set target locations
  - `device_set_speed` - Configure movement speed
  - `device_set_acceleration` - Set acceleration parameters
  - `device_mark_visited` - Mark locations as visited
  - `device_get_all_locations` - Get all locations with status

### 📊 CSV Data Processing
- Dynamic header discovery - LLM can query available columns
- Flexible filtering: min/max ranges, equality matching
- Support for any CSV structure with x, y coordinates
- Built-in sample data with particle properties

### 🎮 Interactive Visualization
- **2D Canvas** with auto-scaling viewport
- **Device Head** visualization with direction indicator
- **Target Locations**:
  - Pending: Hollow circles
  - Current: Pulsing teal highlight
  - Visited: Green checkmarks
- Smooth 60 FPS animations
- Path trajectory visualization

### 🎨 Beautiful UI
- Modern dark theme
- Responsive layout with adjustable panels
- Real-time controls:
  - Speed slider (1-100 units/s)
  - Acceleration slider (1-50 units/s²)
  - Dynamic filter configuration
- MCP activity log panel
- Status indicators and metrics

### ⚙️ Realistic Physics
- Smooth acceleration from rest
- Deceleration when approaching targets
- Configurable max speed and acceleration
- Precise arrival detection

## 🚀 Quick Start

### Prerequisites
- Java 17 or higher
- Gradle 8.0+

### Running the Application

```bash
# Run with default HTTP MCP server (port 8084)
./gradlew run

# Run with custom MCP configuration
./gradlew run --args="--mcp-mode http --mcp-port 9000"

# Run with STDIO MCP server
./gradlew run --args="--mcp-mode stdio"

# Disable MCP server (UI only)
./gradlew run --args="--mcp-enabled false"

# Show help
./gradlew run --args="--help"
```

### Running Standalone MCP Server

```bash
# Build the server JAR
./gradlew serverJar

# Run in STDIO mode (for Claude Desktop, etc.)
java -jar build/libs/device-sim-mcp-server-all.jar
```

### Building the Project

```bash
# Build everything
./gradlew build

# Run tests
./gradlew test

# Run example workflow
./gradlew runExample
```

## 📋 Usage Example

### 1. Load CSV Data
- Click "Browse" to select a CSV file (or use `data/sample_particles.csv`)
- Select X and Y coordinate columns from dropdowns
- The app auto-loads column headers for filtering

### 2. Configure Filters
- Click "Add Filter" to create filter criteria
- Select column (e.g., "circularity")
- Set min/max range (e.g., 0.9 - 1.0 for circular particles)
- Multiple filters can be combined

### 3. Load Locations
- Click "Load Locations" to query and filter CSV data
- Targets appear on the 2D canvas
- Location list updates on the right panel

### 4. Control Movement
- Adjust speed and acceleration sliders
- Click "Start" to begin movement
- Device head smoothly moves to each target
- Click "Mark Visited" to mark current and move to next
- "Reset" clears all and returns to start

### 5. MCP Integration
- MCP server starts automatically (if enabled)
- Check status bar for server endpoint URL
- View MCP activity in the log panel
- Connect Claude or other MCP clients to the endpoint

## 🔌 MCP Client Configuration

### Claude Desktop Configuration

Add to your Claude Desktop config file:

```json
{
  "mcpServers": {
    "device-sim": {
      "command": "java",
      "args": [
        "-jar",
        "/path/to/device-sim-mcp-server-all.jar"
      ]
    }
  }
}
```

Or for HTTP mode:

```json
{
  "mcpServers": {
    "device-sim-http": {
      "url": "http://localhost:8084/mcp"
    }
  }
}
```

### Example LLM Interaction

```
User: "I have a CSV file with particle data. Show me particles with circularity above 0.9"

Claude (via MCP):
1. Calls csv_get_headers to discover columns
2. Finds "circularity" column
3. Calls csv_query_locations with filter: circularity min=0.9
4. Calls device_set_targets with filtered locations
5. Device automatically moves to highly circular particles!
```

## 🏗️ Architecture

```
mcp-device-sim/
├── src/main/java/com/devicesim/
│   ├── model/          # Data models
│   │   ├── Location.java
│   │   └── DeviceState.java
│   ├── engine/         # Simulation engine
│   │   └── DeviceSimulator.java
│   ├── data/           # CSV processing
│   │   └── CsvDataReader.java
│   ├── server/         # MCP server
│   │   └── DeviceDataMcpServer.java
│   ├── mcp/           # MCP infrastructure
│   │   ├── McpConfig.java
│   │   ├── ServerLauncher.java
│   │   └── DirectToolExecutor.java
│   └── ui/            # JavaFX UI
│       ├── DeviceSimApp.java
│       ├── DeviceCanvas.java
│       ├── ControlPanel.java
│       ├── LocationListPanel.java
│       ├── StatusBar.java
│       ├── McpLogPanel.java
│       └── McpCliOptions.java
├── src/main/resources/
│   └── css/
│       └── application.css
├── data/
│   └── sample_particles.csv
└── build.gradle
```

## 🎨 Customization

### Adding Custom CSV Columns
The system auto-discovers CSV columns - just add them to your CSV and they'll be available for filtering!

### Adjusting Animation
Modify `DeviceSimulator.java`:
- `DEFAULT_MAX_SPEED` - Maximum movement speed
- `DEFAULT_ACCELERATION` - Acceleration rate
- `ARRIVAL_THRESHOLD` - Distance to consider "arrived"

### Styling
Edit `src/main/resources/css/application.css` for custom themes.

## 🔧 Development

### Project Structure
- **Model Layer**: Immutable data classes
- **Engine Layer**: Physics simulation with thread-safe operations
- **Data Layer**: CSV parsing with filtering
- **Server Layer**: MCP tools (async for STDIO, sync for HTTP)
- **UI Layer**: JavaFX components with 60 FPS animations

### Thread Safety
- `DeviceState`: Immutable (inherently thread-safe)
- `Location`: Volatile fields for visited status
- `DeviceSimulator`: ReadWriteLock for all operations
- UI updates: Platform.runLater() for thread safety

### Adding New MCP Tools
1. Add tool specification to `DeviceDataMcpServer`
2. Implement async version (STDIO) and sync version (HTTP)
3. Add to `DirectToolExecutor` for UI integration
4. Update documentation

## 📊 Sample Data

The included `data/sample_particles.csv` contains 40 particles with properties:
- `id`: Unique identifier
- `x, y`: Coordinates (0-600 range)
- `area`: Particle area (140-325 range)
- `circularity`: Shape circularity (0.72-0.96)
- `perimeter`: Perimeter length
- `intensity`: Brightness value
- `aspect_ratio`: Width/height ratio

Perfect for demonstrating filtering by circularity, area, or other properties!

## 🎤 Demo Tips for Talks

### Setup
1. Load sample data: `data/sample_particles.csv`
2. Show column headers being discovered
3. Add filter: circularity 0.9-1.0
4. Load locations - only highly circular particles appear
5. Start movement to show smooth animation

### LLM Integration Demo
1. Connect Claude to the MCP server
2. Ask: "What columns are in the CSV?"
3. Ask: "Find particles with area over 250 and circularity above 0.9"
4. Watch as Claude discovers schema and filters dynamically!
5. Show device moving to filtered locations

### Key Points to Highlight
- ✅ LLM discovers CSV structure without prior knowledge
- ✅ Dynamic filtering based on discovered columns
- ✅ Smooth, realistic device movement
- ✅ Both HTTP and STDIO transports supported
- ✅ Beautiful, responsive UI
- ✅ Full MCP tool suite for device control

## 🛠️ CLI Options

```
Usage: DeviceSimApp [OPTIONS]

Options:
  --mcp-mode <mode>      Transport mode: http, stdio, or disabled (default: http)
  --mcp-port <port>      HTTP port number (default: 8084)
  --mcp-host <host>      HTTP hostname (default: localhost)
  --mcp-enabled <bool>   Enable/disable MCP server (default: true)
  --help                 Show this help message
  --version              Show version information
```

## 📝 License

MIT License - feel free to use for your talks and demos!

## 🤝 Contributing

Contributions welcome! This is a demo project designed for educational purposes.

## 🙏 Acknowledgments

- Built with [MCP SDK](https://github.com/modelcontextprotocol/java-sdk)
- Inspired by the Model Context Protocol specification
- UI patterns from modern JavaFX applications

## 📧 Support

For questions or issues, please open a GitHub issue.

---

**Happy Demoing! 🚀**

*Perfect for demonstrating MCP + JavaFX integration at conferences, meetups, and technical talks!*
