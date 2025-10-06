# OpenRewrite MCP Server - Setup Guide

## Overview
The OpenRewrite MCP Server provides automated code refactoring and static analysis capabilities through the Model Context Protocol (MCP). It supports both HTTP and STDIO transports, making it compatible with various AI tools including LM-Studio and Claude Code.

## Available Tools

1. **listRecipes** - List all available refactoring recipes
2. **getRecipeDescription** - Get detailed description of a specific recipe
3. **applyRecipe** - Apply a refactoring recipe to source code
4. **analyzeCode** - Analyze code and suggest applicable recipes
5. **createCustomRecipe** - Create custom refactoring recipes from YAML

## Installation

### Build the Server
```bash
# Build the project
./gradlew clean build

# This creates two JAR files:
# - build/libs/openrewrite-mcp-server.jar (MCP server only)
# - build/libs/openrewrite-jfx-1.0.0.jar (JavaFX UI with embedded server)
```

## Usage Modes

### 1. JavaFX UI with Embedded Server (Recommended for Visual Feedback)

Run the full application with UI:
```bash
# Default (HTTP mode on port 3001)
java -jar build/libs/openrewrite-jfx-1.0.0.jar

# Custom configuration
java -jar build/libs/openrewrite-jfx-1.0.0.jar --mcp-port 8080

# Disable MCP server (UI only)
java -jar build/libs/openrewrite-jfx-1.0.0.jar --mcp-enabled false
```

### 2. Standalone MCP Server (Headless)

#### HTTP Mode (for LM-Studio)
```bash
# Start on default port 3001
java -jar build/libs/openrewrite-mcp-server.jar --http

# Start on custom port
java -jar build/libs/openrewrite-mcp-server.jar --http 8080
```

#### STDIO Mode (for Claude Code)
```bash
# Interactive mode
java -jar build/libs/openrewrite-mcp-server.jar

# Or pipe commands
echo '{"jsonrpc":"2.0","method":"initialize","params":{},"id":1}' | java -jar build/libs/openrewrite-mcp-server.jar
```

## Integration Setup

### Claude Code Integration

1. Add to your Claude MCP configuration (`claude-mcp-config.json`):
```json
{
  "mcpServers": {
    "openrewrite": {
      "command": "java",
      "args": [
        "-jar",
        "C:/Dev/repos/MCP-Playground/code-reflection-01/openrewrite-jfx/build/libs/openrewrite-mcp-server.jar"
      ],
      "env": {},
      "description": "OpenRewrite MCP Server - Automated code refactoring and static analysis"
    }
  }
}
```

2. Restart Claude Code to load the server

3. Use the tools in your conversations:
   - "List all available refactoring recipes"
   - "Apply the RemoveUnusedImports recipe to my Java code"
   - "Analyze this code for potential improvements"

### LM-Studio Integration

1. Start the server in HTTP mode:
```bash
java -jar build/libs/openrewrite-mcp-server.jar --http 3001
```

2. Configure LM-Studio to connect to:
   - Endpoint: `http://localhost:3001/mcp`
   - Transport: HTTP
   - Use the configuration from `lm-studio-config.json`

3. The server will be available for all LM-Studio conversations

## Testing

### Test HTTP Transport
```bash
chmod +x test-http.sh
./test-http.sh
```

### Test STDIO Transport
```bash
chmod +x test-stdio.sh
./test-stdio.sh
```

### Manual HTTP Test
```bash
# Initialize connection
curl -X POST http://localhost:3001/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "method": "initialize",
    "params": {
      "protocolVersion": "2024-11-05",
      "clientInfo": {
        "name": "test-client",
        "version": "1.0.0"
      }
    },
    "id": 1
  }'

# List available recipes
curl -X POST http://localhost:3001/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "method": "tools/call",
    "params": {
      "name": "listRecipes",
      "arguments": {}
    },
    "id": 2
  }'
```

## Example Usage

### Apply a Recipe
```json
{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "params": {
    "name": "applyRecipe",
    "arguments": {
      "recipeName": "org.openrewrite.java.format.AutoFormat",
      "sourceCode": "public class Example{private int x;public void method(){System.out.println(x);}}",
      "language": "java"
    }
  },
  "id": 3
}
```

### Analyze Code
```json
{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "params": {
    "name": "analyzeCode",
    "arguments": {
      "sourceCode": "import java.util.*;\nimport java.io.*;\npublic class Example {\n  private String unused;\n  public void method() {\n    System.out.println(\"hello\");\n  }\n}",
      "language": "java"
    }
  },
  "id": 4
}
```

## Available Recipes

The server includes hundreds of built-in recipes for:
- **Code Formatting**: AutoFormat, RemoveTrailingWhitespace, TabsAndIndents
- **Code Cleanup**: RemoveUnusedImports, RemoveUnusedLocalVariables, SimplifyBooleanExpression
- **Modernization**: UseStaticImport, UseCollectionInterfaces, PreferOptionalOrElseNull
- **Security**: SecureRandomPrefersDefaultSeed, UseFilesCreateTempDirectory
- **Best Practices**: ExplicitInitialization, FinalizeLocalVariables, NoFinalize
- **Migration**: Java version upgrades, framework migrations
- **Custom Recipes**: Create your own using YAML definitions

Run `listRecipes` to see all available recipes with descriptions.

## CLI Options (JavaFX UI)

```bash
java -jar openrewrite-jfx-1.0.0.jar [options]

Options:
  --mcp-enabled <true|false>     Enable/disable MCP server (default: true)
  --mcp-mode <stdio|http>        Transport mode (default: http)
  --mcp-port <port>              HTTP port (default: 3001)
  --mcp-host <host>              HTTP host (default: localhost)
  --mcp-endpoint <path>          HTTP endpoint path (default: /mcp)
  --mcp-log-enabled <true|false> Enable logging (default: true)
  --mcp-log-dir <dir>            Log directory (default: ./logs)
  --help                         Show help message
```

## Troubleshooting

### Server Won't Start
- Check if port 3001 is already in use: `netstat -an | grep 3001`
- Use a different port: `--http 8080`
- Check Java version (requires Java 17+): `java -version`

### Connection Issues
- Verify server is running: Check for "Server started" message
- Test with curl or test scripts
- Check firewall settings for the port

### Recipe Not Found
- Use `listRecipes` to see available recipes
- Recipe names are case-sensitive
- Use full qualified names (e.g., `org.openrewrite.java.format.AutoFormat`)

## Logs

Server logs are written to:
- Console output (stderr) for status messages
- `./logs/` directory when logging is enabled
- Use `--mcp-log-dir` to change log location

## Support

For issues or questions:
- GitHub Issues: [OpenRewrite MCP Server](https://github.com/your-repo/openrewrite-mcp)
- OpenRewrite Documentation: https://docs.openrewrite.org/
- MCP Specification: https://modelcontextprotocol.io/