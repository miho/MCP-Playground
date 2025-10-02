# MCP CLI Configuration - Quick Reference

## Common Commands

### Run with Defaults (HTTP mode on localhost:8082)
```bash
gradle run
```

### Run in stdio Mode
```bash
gradle run --args="--mcp-mode=stdio"
```

### Run on Custom Port
```bash
gradle run --args="--mcp-port=9000"
```

### Run without MCP Server
```bash
gradle run --args="--mcp-enabled=false"
```

### Display Help
```bash
gradle run --args="--help"
```

## All CLI Options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `--mcp-enabled` | boolean | true | Enable/disable MCP server |
| `--mcp-mode` | string | http | Transport mode: stdio or http |
| `--mcp-port` | integer | 8082 | HTTP port |
| `--mcp-host` | string | localhost | HTTP host |
| `--mcp-endpoint` | string | /mcp | HTTP endpoint path |
| `--mcp-log-enabled` | boolean | true | Enable logging |
| `--mcp-log-dir` | string | ./logs | Log directory |
| `--help` | - | - | Display help |
| `--version` | - | - | Display version |

## Transport Modes

### HTTP Mode (Default)
- Best for: Remote access, multiple clients, web integration
- Configuration: Requires host, port, endpoint
- Example: `http://localhost:8082/mcp`

### stdio Mode
- Best for: Embedded apps, CLI tools, piped workflows
- Configuration: No HTTP settings needed
- Note: May conflict with JavaFX UI logging

## Examples

### Production HTTP Server
```bash
gradle run --args="--mcp-mode=http --mcp-host=0.0.0.0 --mcp-port=8080 --mcp-endpoint=/api/mcp"
```

### Development with Custom Port
```bash
gradle run --args="--mcp-port=8090"
```

### UI Only (No MCP)
```bash
gradle run --args="--mcp-enabled=false"
```

## Building JARs

### UI JAR (includes JavaFX)
```bash
gradle uiJar
# Output: build/libs/image-processing-ui.jar
```

### Server JAR (headless)
```bash
gradle shadowJar
# Output: build/libs/image-processing-mcp-server.jar
```

## Testing

### Run Tests
```bash
gradle test
```

### Run Specific Test
```bash
gradle test --tests McpCliOptionsTest
```

## Viewing Configuration

Once running, click the **Server Settings** button (gear icon) in the UI to view current configuration.

## Troubleshooting

**Port already in use:**
```bash
netstat -an | grep 8082
# Then use a different port:
gradle run --args="--mcp-port=8090"
```

**Configuration not applied:**
- Ensure correct syntax: `--args="--option=value"`
- Check console output for configuration confirmation

## More Information

- Full documentation: [MCP_CLI_CONFIGURATION.md](MCP_CLI_CONFIGURATION.md)
- Implementation details: [IMPLEMENTATION_SUMMARY.md](IMPLEMENTATION_SUMMARY.md)
