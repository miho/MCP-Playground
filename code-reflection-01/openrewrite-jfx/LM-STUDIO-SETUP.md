# LM Studio Integration with OpenRewrite MCP Server

## The Problem

The error you're seeing occurs because LM Studio is trying to connect to the MCP server at `http://localhost:3001/mcp`, but the server isn't running. The JavaFX app embeds the MCP server but needs to be configured properly.

## Solution Options

### Option 1: Run MCP Server Standalone (Recommended for LM Studio)

1. **Start the MCP server independently:**

   On Windows:
   ```bash
   .\start-mcp-server.bat
   ```

   On Linux/Mac:
   ```bash
   ./start-mcp-server.sh
   ```

2. **Configure LM Studio:**
   - Open LM Studio settings
   - Go to the MCP/Plugin configuration
   - Set the MCP server URL to: `http://localhost:3001/mcp`
   - Test the connection

3. **Run your JavaFX application:**
   - The JavaFX app will automatically connect to the running MCP server
   - It will use the same endpoint: `http://localhost:3001/mcp`

### Option 2: Use JavaFX App's Embedded Server

1. **Ensure JavaFX app starts the server in HTTP mode:**
   - The app should be configured to use HTTP transport (not STDIO)
   - Default port is 3001

2. **Start the JavaFX application first:**
   ```bash
   ./gradlew run
   ```

3. **Then configure LM Studio to connect to it:**
   - Set URL to: `http://localhost:3001/mcp`

## Troubleshooting

### Check if MCP Server is Running

Test the server endpoint:
```bash
curl -X POST http://localhost:3001/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}'
```

Expected response: JSON with available tools list

### Common Issues

1. **Port 3001 Already in Use:**
   - The server will try ports 3001, 3002, 3003
   - Check console output for actual port used

2. **Connection Refused:**
   - Ensure the MCP server is running BEFORE LM Studio tries to connect
   - Check firewall settings

3. **404 Not Found:**
   - Verify the endpoint path is `/mcp`
   - Check that the server is running in HTTP mode, not STDIO

## LM Studio MCP Bridge Configuration

In LM Studio's MCP configuration:

1. **Server Type:** HTTP
2. **URL:** `http://localhost:3001/mcp`
3. **Method:** POST
4. **Headers:**
   - Content-Type: `application/json`
   - Accept: `application/json`

## Testing the Connection

Once both are running:

1. In LM Studio, test the MCP connection
2. You should see "Connected to MCP server" message
3. Try listing available recipes or tools

## Available MCP Tools

The server provides these tools:
- `list_recipes` - List all available OpenRewrite recipes
- `get_recipe_description` - Get details about a specific recipe
- `apply_recipe` - Apply a recipe to transform code
- `analyze_code` - Analyze code and suggest applicable recipes
- `create_custom_recipe` - Create custom transformation recipes