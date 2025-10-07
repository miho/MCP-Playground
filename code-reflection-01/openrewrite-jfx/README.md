# OpenRewrite JavaFX Application

A JavaFX application that transforms source code using OpenRewrite recipes via MCP (Model Context Protocol).

## Features

- **Visual Code Editor**: Syntax-highlighted code editor with line numbers
- **Recipe Selection**: Browse and select from available OpenRewrite recipes including:
  - Java 17, 21, and 25 migration recipes
  - Static analysis improvements
  - Code modernization transformations
- **Real-time Transformation**: Apply recipes and see transformed code instantly
- **Diff Viewer**: Visual comparison between original and transformed code
- **MCP Integration**: Communicates with OpenRewrite via Model Context Protocol
- **External Synchronization**: UI automatically updates when external MCP clients (Claude Code, LM Studio, etc.) trigger transformations
- **Theme Support**: Dark and light themes

## Architecture

The application uses a client-server architecture:

- **MCP Server** (`OpenRewriteMcpServer`): Handles OpenRewrite operations
- **JavaFX Client**: Provides the UI and communicates with the server via MCP
- **MCP Client** (`McpClient`): Manages async communication between UI and server

## Prerequisites

- Java 17 or higher
- Gradle 8.0 or higher

## Building the Application

```bash
cd openrewrite-jfx

# Build the project
./gradlew build

# Build the MCP server JAR
./gradlew shadowJar

# Build the UI JAR
./gradlew uiJar
```

## Running the Application

### Option 1: Run UI with Embedded Server (Recommended)

```bash
./gradlew run
```

This starts the JavaFX UI which automatically launches an embedded MCP server.

### Option 2: Run Server and UI Separately

```bash
# Terminal 1: Start the MCP server
./gradlew runServer

# Terminal 2: Start the JavaFX UI
./gradlew runUI
```

### Option 3: Run from JAR Files

```bash
# Build the JARs first
./gradlew shadowJar uiJar

# Run the server
java -jar build/libs/openrewrite-mcp-server.jar

# In another terminal, run the UI
java -jar build/libs/openrewrite-ui.jar
```

## Usage

1. **Start the Application**: Run using one of the methods above
2. **Write or Load Code**: Enter Java code in the left editor or use File → Open
3. **Select a Recipe**: Choose from the recipe list on the left panel
4. **Apply Transformation**: Click "Apply Recipe" button
5. **View Results**: See transformed code and diff in the right panels
6. **Save Results**: Use File → Save to export transformed code

## Available Recipes

### Migration Recipes
- **Upgrade to Java 17**: Migrate code to Java 17 standards
- **Upgrade to Java 21**: Migrate code to Java 21 standards
- **Upgrade to Java 25**: Migrate code to Java 25 (includes pattern matching, string templates, value classes)

### Static Analysis
- **Common Static Analysis**: Apply common code improvements
- **Remove Unused Imports**: Clean up import statements
- **Simplify Boolean Expressions**: Simplify complex boolean logic
- **Remove Unused Local Variables**: Clean up unused variables
- **Use String Replace**: Optimize string replacements

## MCP Tools

The MCP server exposes these tools:

- `listRecipes`: List all available recipes
- `getRecipeDescription`: Get details about a specific recipe
- `applyRecipe`: Apply a recipe to source code
- `analyzeCode`: Analyze code and suggest applicable recipes
- `createCustomRecipe`: Create custom recipes from YAML

## External Synchronization

**NEW:** The UI now automatically updates when external MCP clients trigger transformations!

When Claude Code, LM Studio, or other MCP clients call the `apply_recipe` tool, the JavaFX UI automatically synchronizes to show:
- The source code being transformed
- The transformation result
- The diff view with highlighted changes
- Real-time status updates

### Testing External Sync

Run the test script to see it in action:
```bash
./test-external-sync.sh
```

Watch the UI update automatically as transformations are triggered!

### How It Works

The application uses an event-driven architecture:
1. External client calls `apply_recipe` via MCP
2. `ToolFactory` publishes transformation events to `TransformationEventBus`
3. `MainController` subscribes to events and updates the UI
4. All UI updates use `Platform.runLater()` for thread safety

**For complete documentation**, see:
- `EXTERNAL-SYNC-GUIDE.md` - Usage guide and API reference
- `IMPLEMENTATION-SUMMARY.md` - Technical implementation details

## Configuration

### MCP Server Options

```bash
# Run server on different port
java -jar openrewrite-mcp-server.jar --port 3002

# Use stdio transport instead of HTTP
java -jar openrewrite-mcp-server.jar --transport stdio
```

## Development

### Project Structure

```
openrewrite-jfx/
├── src/main/java/com/openrewrite/
│   ├── server/               # MCP server implementation
│   │   ├── OpenRewriteMcpServer.java
│   │   └── RewriteEngine.java
│   └── ui/                   # JavaFX UI
│       ├── OpenRewriteApp.java
│       ├── MainController.java
│       ├── components/       # UI components
│       └── model/           # Data models and MCP client
└── src/main/resources/
    └── css/                 # Stylesheets
```

### Adding New Recipes

To add new OpenRewrite recipes:

1. Add the recipe dependency in `build.gradle`
2. Register the recipe in `RewriteEngine.loadAvailableRecipes()`
3. Rebuild and restart the application

## Troubleshooting

### MCP Server Connection Issues

- Ensure port 3001 is not in use
- Check firewall settings allow localhost connections
- Verify Java 17+ is installed

### UI Not Displaying Properly

- Ensure JavaFX modules are available
- Try switching between dark/light themes
- Check console for any JavaFX errors

## License

This project demonstrates OpenRewrite and MCP integration for educational purposes.