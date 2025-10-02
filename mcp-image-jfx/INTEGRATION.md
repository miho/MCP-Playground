# MCP Client Integration Documentation

This document describes the integration layer that connects the JavaFX UI to the MCP server backend.

## Overview

The integration layer provides seamless communication between the JavaFX user interface and the MCP (Model Context Protocol) image processing server. It handles server lifecycle management, tool execution, result synchronization, and error handling.

## Architecture

### Component Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    JavaFX UI Layer                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │ MainController│  │ToolPipelinePane│ParameterEditor│     │
│  └──────┬───────┘  └──────────────┘  └──────┬───────┘     │
│         │                                     │              │
└─────────┼─────────────────────────────────────┼─────────────┘
          │                                     │
┌─────────┼─────────────────────────────────────┼─────────────┐
│         │      Integration Layer              │              │
│  ┌──────▼─────────┐  ┌────────────────┐  ┌──▼──────────┐  │
│  │McpServerManager│  │ToolExecutorSvc │  │CacheSyncSvc │  │
│  └──────┬─────────┘  └────────┬───────┘  └─────────────┘  │
│         │                     │                             │
│  ┌──────▼─────────────────────▼───────────────────────┐   │
│  │              McpClient (HTTP/Stdio)                  │   │
│  └──────────────────────────┬───────────────────────────┘   │
└─────────────────────────────┼─────────────────────────────┘
                              │
┌─────────────────────────────▼─────────────────────────────┐
│            MCP Server (Image Processing)                   │
│  ┌──────────────────────────────────────────────────┐     │
│  │  Tools: load_image, resize_image, segment, etc.  │     │
│  └──────────────────────────────────────────────────┘     │
└────────────────────────────────────────────────────────────┘
```

## Core Components

### 1. McpConfig (`com.imageprocessing.client.McpConfig`)

**Purpose**: Configuration settings for MCP server connection and execution.

**Key Features**:
- Transport mode selection (HTTP or stdio)
- Connection parameters (host, port, endpoints)
- Timeout and retry policies
- Server process settings

**Usage**:
```java
// Default HTTP configuration
McpConfig config = McpConfig.defaultHttp();

// Custom configuration
McpConfig config = McpConfig.builder()
    .transportMode(TransportMode.HTTP)
    .httpPort(8082)
    .connectionTimeout(Duration.ofSeconds(10))
    .executionTimeout(Duration.ofMinutes(5))
    .maxRetries(3)
    .build();
```

### 2. McpClient (`com.imageprocessing.client.McpClient`)

**Purpose**: HTTP/stdio client for communicating with MCP server using JSON-RPC protocol.

**Key Features**:
- Connection testing
- Tool invocation with retry logic
- Request/response handling
- Support for both HTTP and stdio transports
- Async execution using CompletableFuture

**Usage**:
```java
McpClient client = new McpClient(config);

// Test connection
boolean connected = client.testConnection().get();

// Call a tool
Map<String, Object> args = new HashMap<>();
args.put("image_path", "/path/to/image.jpg");
args.put("width", 800);
args.put("height", 600);

McpClient.ToolResult result = client.callTool("resize_image", args).get();
if (result.isSuccess()) {
    System.out.println(result.getContent());
} else {
    System.err.println(result.getErrorMessage());
}
```

### 3. McpServerManager (`com.imageprocessing.client.McpServerManager`)

**Purpose**: Manages MCP server process lifecycle.

**Key Features**:
- Start/stop server as subprocess
- Process health monitoring
- Auto-restart on failure
- Log capture
- Status notifications

**Usage**:
```java
McpServerManager manager = new McpServerManager(config);

// Register status listener
manager.addStatusListener(status -> {
    System.out.println("Server status: " + status);
});

// Start server
manager.startServer().thenAccept(success -> {
    if (success) {
        System.out.println("Server started successfully");
    }
});

// Get client
McpClient client = manager.getClient();

// Stop server
manager.stopServer();
```

### 4. ToolExecutorService (`com.imageprocessing.client.ToolExecutorService`)

**Purpose**: Executes tools individually or as a pipeline.

**Key Features**:
- Single tool execution
- Sequential pipeline execution
- Progress callbacks
- Cancellation support
- Status updates to ToolInstance

**Usage**:
```java
ToolExecutorService executor = new ToolExecutorService(client);

// Execute single tool
ToolInstance tool = new ToolInstance(metadata);
tool.setParameter("width", 800);
executor.executeTool(tool).thenAccept(result -> {
    if (result.isSuccess()) {
        tool.setStatus(Status.COMPLETED);
        tool.setResult(result.getMessage());
    }
});

// Execute pipeline
List<ToolInstance> pipeline = workflowModel.getToolInstances();
executor.executePipeline(pipeline, tool -> {
    // Progress callback (called on background thread)
    Platform.runLater(() -> updateUI(tool));
}).thenAccept(result -> {
    System.out.println(result.getSummary());
});
```

### 5. CacheSyncService (`com.imageprocessing.client.CacheSyncService`)

**Purpose**: Synchronizes cached result keys between UI and server.

**Key Features**:
- Track output keys from tool executions
- Provide keys to UI ComboBoxes
- Update listeners for real-time sync
- Import/export for workflow persistence

**Usage**:
```java
CacheSyncService cacheSync = new CacheSyncService(client);

// Register update listener
cacheSync.addUpdateListener(keys -> {
    Platform.runLater(() -> {
        resultKeyComboBox.getItems().setAll(keys);
    });
});

// Add keys from tool execution
cacheSync.addResultKey("processed_image");
cacheSync.extractAndRegisterKeys(toolResult);

// Get available keys
Set<String> keys = cacheSync.getAvailableResultKeys();
```

## Integration Flow

### Application Startup

1. **UI Initialization**: `ImageProcessingApp` creates `MainController`
2. **MCP Components**: `MainController` initializes `McpConfig` with defaults
3. **Ready State**: Application is ready, waiting for user to launch server

### Server Launch

1. **User Action**: User clicks "Launch MCP Server" button
2. **Create Manager**: `MainController` creates `McpServerManager`
3. **Register Listener**: Status listener registered for UI updates
4. **Start Process**: Server process launched via `ProcessBuilder`
5. **Wait for Ready**: Manager polls server until connection succeeds
6. **Initialize Services**: `ToolExecutorService` and `CacheSyncService` created
7. **Link UI**: Services linked to UI components
8. **Status Update**: UI updated to show server running

```java
private void launchMcpServer() {
    serverManager = new McpServerManager(mcpConfig);

    serverManager.addStatusListener(status -> {
        Platform.runLater(() -> {
            switch (status) {
                case RUNNING:
                    initializeServices();
                    statusBar.showSuccess("MCP server connected");
                    break;
                case ERROR:
                    statusBar.showError("MCP server error");
                    break;
            }
        });
    });

    serverManager.startServer();
}

private void initializeServices() {
    McpClient client = serverManager.getClient();
    this.executorService = new ToolExecutorService(client);
    this.cacheSyncService = new CacheSyncService(client);
    parameterEditorPane.setCacheSyncService(cacheSyncService);
}
```

### Pipeline Execution

1. **User Action**: User clicks "Play" to execute pipeline
2. **Validation**: Check server is running and pipeline is not empty
3. **Reset State**: All tools reset to PENDING status
4. **Start Execution**: `ToolExecutorService.executePipeline()` called
5. **Sequential Processing**:
   - For each tool in pipeline:
     - Update tool status to RUNNING
     - Extract parameters from ToolInstance
     - Call MCP server via client
     - Update tool status (COMPLETED or ERROR)
     - Extract output keys for cache
     - Invoke progress callback
6. **Completion**: Display success/error summary
7. **Cache Update**: UI ComboBoxes updated with new result keys

```java
private void executeRealPipeline() {
    executorService.executePipeline(
        workflowModel.getToolInstances(),
        tool -> {
            Platform.runLater(() -> {
                statusBar.setStatus("Executing: " + tool.getName());
                if (tool.getStatus() == Status.COMPLETED) {
                    cacheSyncService.extractAndRegisterKeys(tool.getResult());
                }
            });
        }
    ).thenAccept(result -> {
        Platform.runLater(() -> {
            if (result.hasErrors()) {
                statusBar.showError("Pipeline completed with errors");
            } else {
                statusBar.showSuccess("Pipeline completed successfully!");
            }
        });
    });
}
```

### Cache Synchronization

1. **Tool Completion**: Tool execution completes successfully
2. **Extract Keys**: `CacheSyncService` parses result for "Cached with key: ..." pattern
3. **Register Key**: Key added to internal set
4. **Notify Listeners**: All registered listeners notified
5. **Update UI**: ComboBoxes in `ParameterEditorPane` refreshed with new keys
6. **User Selection**: User can now select cached results in subsequent tools

### Application Shutdown

1. **User Closes App**: JavaFX calls `Application.stop()`
2. **Controller Shutdown**: `MainController.shutdown()` called
3. **Stop Server**: `McpServerManager` stops server process gracefully
4. **Close Client**: `McpClient` closes connections
5. **Executor Shutdown**: `ToolExecutorService` shuts down thread pool
6. **Cleanup Complete**: Resources released, application exits

## MCP Protocol Details

### JSON-RPC Format

All communication uses JSON-RPC 2.0 format:

**Request**:
```json
{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "params": {
    "name": "resize_image",
    "arguments": {
      "image_path": "/path/to/image.jpg",
      "width": 800,
      "height": 600,
      "output_key": "resized_image"
    }
  },
  "id": 1
}
```

**Response (Success)**:
```json
{
  "jsonrpc": "2.0",
  "result": {
    "content": [
      {
        "type": "text",
        "text": "Image resized successfully!\n- Original size: 1920x1080\n- New size: 800x600\n- Cached with key: resized_image"
      }
    ],
    "isError": false
  },
  "id": 1
}
```

**Response (Error)**:
```json
{
  "jsonrpc": "2.0",
  "result": {
    "content": [
      {
        "type": "text",
        "text": "Error: Image file not found"
      }
    ],
    "isError": true
  },
  "id": 1
}
```

## Error Handling

### Connection Errors

- **Symptom**: Cannot connect to server
- **Handling**: Retry with exponential backoff (up to `maxRetries`)
- **User Feedback**: Error dialog with clear message
- **Recovery**: User can restart server manually

### Tool Execution Errors

- **Symptom**: Tool returns `isError: true`
- **Handling**: Stop pipeline (unless `continueOnError` is true)
- **User Feedback**: Tool card shows ERROR status with message
- **Recovery**: User can fix parameters and re-run

### Server Crash

- **Symptom**: Process dies unexpectedly
- **Handling**: Auto-restart if `autoRestartOnFailure` is enabled
- **User Feedback**: Status bar shows "Server error" or "Reconnecting..."
- **Recovery**: Automatic if configured, otherwise manual restart

### Timeout Errors

- **Symptom**: Tool execution exceeds `executionTimeout`
- **Handling**: Cancel request and mark as ERROR
- **User Feedback**: Error message indicates timeout
- **Recovery**: Increase timeout or optimize tool

## Threading Model

### JavaFX Application Thread

- **UI updates**: All UI updates must use `Platform.runLater()`
- **Event handlers**: Executed on JavaFX thread
- **Long operations**: Offloaded to background threads

### Background Threads

- **Server process**: Managed by `McpServerManager` executor
- **Tool execution**: Single-threaded executor in `ToolExecutorService`
- **HTTP requests**: Java HttpClient internal threads

### Thread Safety

- **ToolInstance**: Properties are JavaFX properties (thread-safe updates)
- **CacheSyncService**: Uses `ConcurrentHashMap.newKeySet()` for thread-safe set
- **Status listeners**: Always wrap UI updates in `Platform.runLater()`

## Configuration Options

### Transport Mode

**HTTP Mode** (recommended for development):
- Easier to debug (can use tools like Postman)
- More mature protocol support
- Better error messages
- Default port: 8082

**Stdio Mode**:
- Lower overhead
- Simpler deployment
- Requires careful stream handling
- Uses stdin/stdout

### Timeouts

- **Connection Timeout**: 10 seconds (how long to wait for connection)
- **Execution Timeout**: 5 minutes (max time for tool execution)
- **Retry Delay**: 2 seconds (wait between retry attempts)

### Retry Policy

- **Max Retries**: 3 attempts
- **Strategy**: Simple retry with fixed delay
- **Conditions**: Retry on IOException, SocketException, TimeoutException

## Testing

### Unit Tests

Run unit tests:
```bash
./gradlew test
```

Basic tests verify:
- Configuration building
- Client initialization
- Result object creation

### Integration Tests

Integration tests require a running MCP server and are disabled by default.

To enable:
1. Build server JAR: `./gradlew shadowJar`
2. Start server manually
3. Remove `@Disabled` annotation from tests
4. Run tests: `./gradlew test`

### Manual Testing

1. **Build Project**:
   ```bash
   ./gradlew build
   ```

2. **Run UI**:
   ```bash
   ./gradlew runUI
   ```

3. **Test Workflow**:
   - Click "Launch MCP Server"
   - Wait for "MCP server connected" message
   - Add tools to pipeline
   - Configure parameters
   - Click "Play" to execute
   - Verify results in tool cards

## Known Limitations

1. **Cache Persistence**: Cache is in-memory only. Restarting server clears cache.
2. **No Progress Bars**: Individual tool progress not reported (only completion status).
3. **Single Pipeline**: Cannot run multiple pipelines concurrently.
4. **No Workflow Serialization**: Save/Load workflow not yet implemented.
5. **Limited Error Recovery**: Some errors require manual intervention.

## Future Improvements

1. **Persistent Cache**: Store intermediate results on disk
2. **Streaming Progress**: Real-time progress updates during long operations
3. **Parallel Execution**: Execute independent tools in parallel
4. **Workflow Serialization**: Save/load pipelines as JSON
5. **Retry Strategies**: Exponential backoff, circuit breaker pattern
6. **Connection Pooling**: Reuse HTTP connections for better performance
7. **Metrics Dashboard**: Show execution time, success rate, cache hit rate
8. **Tool Validation**: Pre-execution validation of parameters
9. **Undo/Redo**: Implement command pattern for workflow editing
10. **Remote Server**: Support for connecting to remote MCP servers

## Troubleshooting

### "Failed to start MCP server"

**Possible Causes**:
- Server JAR not built
- OpenCV native libraries not found
- Port already in use

**Solutions**:
1. Build server: `./gradlew shadowJar`
2. Check port: `netstat -an | grep 8082`
3. Verify OpenCV: Check server logs in `./logs/`

### "Connection timeout"

**Possible Causes**:
- Firewall blocking connection
- Server taking too long to start
- Wrong host/port configuration

**Solutions**:
1. Check firewall settings
2. Increase `connectionTimeout` in config
3. Verify server is running: `ps aux | grep image-processing`

### "Tool execution failed"

**Possible Causes**:
- Invalid parameters
- Missing input files
- Server internal error

**Solutions**:
1. Check parameter values in UI
2. Verify file paths are absolute and exist
3. Check server logs for detailed error

### "Cache key not found"

**Possible Causes**:
- Key typo in parameter
- Server restarted (cache cleared)
- Tool failed before caching result

**Solutions**:
1. Verify key name matches exactly
2. Re-run previous tool to regenerate cache
3. Use output_key consistently across pipeline

## Contact & Support

For issues or questions about the integration layer:
- Review server logs in `./logs/`
- Enable debug logging: `Logger.setLevel(Level.DEBUG)`
- Check MCP SDK documentation: https://modelcontextprotocol.io/
