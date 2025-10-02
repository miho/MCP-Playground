# Architecture Guide: Embedded MCP in JavaFX

This document explains the **embedded MCP server architecture** used in this JavaFX application. This pattern is ideal for desktop applications that need both a rich UI and external programmability.

## 🎯 Architecture Goals

1. **Single Process** - Everything runs in one JVM
2. **Dual Access** - UI users and external MCP clients can both use the tools
3. **Zero Overhead** - UI calls tools directly (no HTTP roundtrips)
4. **Resource Sharing** - Processing engine and cache shared between UI and MCP server
5. **Clean Separation** - UI layer, execution layer, and server layer are decoupled

## 📐 System Architecture

### High-Level Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    JavaFX Application Process                │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌────────────────────┐         ┌──────────────────────┐   │
│  │   JavaFX UI Layer  │         │  MCP Server Layer    │   │
│  │                    │         │                      │   │
│  │ • ToolCollection   │         │ • ServerLauncher     │   │
│  │ • ToolPipeline     │         │ • Jetty HTTP Server  │   │
│  │ • ParameterEditor  │         │ • MCP Protocol       │   │
│  │ • ImagePreview     │         │ • Tool Handlers      │   │
│  │ • ControlBar       │         │                      │   │
│  └────────┬───────────┘         └──────────┬───────────┘   │
│           │                                │               │
│           ▼                                ▼               │
│  ┌──────────────────────────────────────────────────────┐  │
│  │         Execution & Processing Layer                  │  │
│  │                                                        │  │
│  │  DirectToolExecutor        OpenCVImageProcessor       │  │
│  │  (for UI execution)        (shared processing engine) │  │
│  │                                                        │  │
│  │              IntermediateResultCache                  │  │
│  │              (shared result storage)                  │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                               │
└─────────────────────────────────────────────────────────────┘
                              │
                              │ External Access
                              ▼
                    ┌──────────────────┐
                    │  MCP Clients     │
                    │  (Claude, etc.)  │
                    └──────────────────┘
```

### Component Layers

#### 1. UI Layer (JavaFX)
**Purpose:** User interface for manual tool execution and result visualization

**Components:**
- `ImageProcessingApp` - Application entry point
- `MainController` - Coordinates all UI components
- `ToolCollectionPane` - Searchable tool library
- `ToolPipelinePane` - Visual pipeline builder
- `ParameterEditorPane` - Dynamic parameter forms
- `ImagePreviewPanel` - Thumbnail grid with zoom
- `ControlBar` - Execution controls
- `StatusBar` - Status and progress

**Execution Path:**
```
User Action → MainController.executePipeline()
           → DirectToolExecutor.executeTool()
           → OpenCVImageProcessor.processImage()
           → Result displayed in ImagePreviewPanel
```

#### 2. MCP Server Layer
**Purpose:** Expose tools to external MCP clients via HTTP/stdio

**Components:**
- `ServerLauncher` - Embedded server wrapper
- `ImageProcessingMcpServer` - MCP server implementation
- `McpConfig` - Configuration (HTTP/stdio, port, etc.)

**Execution Path:**
```
External Client → HTTP Request (POST /mcp)
               → ServerLauncher (Jetty receives)
               → MCP Protocol Handler
               → OpenCVImageProcessor.processImage()
               → JSON Response → External Client
```

#### 3. Execution & Processing Layer
**Purpose:** Shared processing engine and result storage

**Components:**
- `DirectToolExecutor` - Direct tool execution for UI
- `OpenCVImageProcessor` - Image processing operations
- `IntermediateResultCache` - Thread-safe result cache

**Key Design:**
- **Single Instance** - Only one OpenCVImageProcessor and cache
- **Thread-Safe** - ConcurrentHashMap for cache, synchronized operations
- **Shared Access** - Both UI and MCP server use same instances

## 🔄 Execution Flows

### Flow 1: UI Execution (Direct)

```
User clicks "Play"
    ↓
MainController.executePipeline()
    ↓
DirectToolExecutor.executePipeline(tools)
    ↓
For each tool:
    ├─ DirectToolExecutor.executeTool(tool)
    ├─ OpenCVImageProcessor.[toolMethod](params)
    ├─ Store result in IntermediateResultCache
    ├─ Update ToolInstance status
    └─ Progress callback → UI update
    ↓
Platform.runLater() → Update UI
    ├─ Status indicators
    ├─ Progress bar
    └─ ImagePreviewPanel (thumbnails)
```

**Performance:** Direct Java method calls, no serialization, ~1ms overhead

### Flow 2: External MCP Client Execution

```
External Client (e.g., Claude Desktop)
    ↓
POST http://localhost:8082/mcp
    ↓
Jetty Server receives request
    ↓
ServerLauncher.handleRequest()
    ↓
Parse MCP JSON-RPC request
    ↓
Extract tool name and arguments
    ↓
OpenCVImageProcessor.[toolMethod](params)
    ↓
Store result in IntermediateResultCache (if output_key)
    ↓
Format response as MCP JSON-RPC
    ↓
HTTP Response → External Client
```

**Performance:** JSON serialization + HTTP, ~10-20ms overhead

## 🔗 Resource Sharing

### Shared Processing Engine

```java
// Created once in MainController.initializeEmbeddedServer()
OpenCVImageProcessor processor = new OpenCVImageProcessor();

// Shared by UI executor
DirectToolExecutor directExecutor = new DirectToolExecutor(processor, cache);

// Shared by MCP server
ServerLauncher serverLauncher = new ServerLauncher(config, processor, cache);
```

**Benefits:**
- No duplication of processing logic
- Consistent behavior across UI and API
- Memory efficient (one OpenCV context)

### Shared Result Cache

```java
// Thread-safe cache using ConcurrentHashMap
IntermediateResultCache cache = new IntermediateResultCache();

// UI stores results
cache.put("resized", resizedImage);

// External client retrieves
Mat image = cache.get("resized");
```

**Benefits:**
- UI and external clients can share intermediate results
- Efficient pipeline execution (no re-processing)
- Thread-safe concurrent access

## 🧵 Threading Model

### Thread Allocation

```
JavaFX Application Thread
├─ UI events and updates
├─ Button clicks
├─ Status updates
└─ Image preview rendering

Background Executor Thread Pool
├─ DirectToolExecutor operations
├─ Image loading/processing
└─ Cache operations

MCP Server Thread Pool (Jetty)
├─ HTTP request handling
├─ MCP protocol parsing
└─ Tool execution for external clients

OpenCV Native Threads
└─ Image processing operations
```

### Thread Safety Measures

1. **UI Updates:** Always use `Platform.runLater()`
```java
CompletableFuture.runAsync(() -> {
    // Background work
    Mat result = processor.processImage(image);

    Platform.runLater(() -> {
        // Update UI on JavaFX thread
        updatePreview(result);
    });
});
```

2. **Cache Access:** Thread-safe ConcurrentHashMap
```java
public class IntermediateResultCache {
    private final ConcurrentHashMap<String, Mat> cache = new ConcurrentHashMap<>();

    public void put(String key, Mat value) {
        cache.put(key, value.clone()); // Thread-safe
    }
}
```

3. **Tool Execution:** ExecutorService with single thread (sequential)
```java
private final ExecutorService executor = Executors.newSingleThreadExecutor();
// Ensures tools execute one at a time
```

## 🚀 Startup Sequence

### Application Initialization

```
1. ImageProcessingApp.main()
       ↓
2. ImageProcessingApp.start(Stage)
       ↓
3. Create MainController
       ↓
4. Build UI components
       ↓
5. Show stage
       ↓
6. MainController.initializeEmbeddedServer()
       ↓
   ├─ nu.pattern.OpenCV.loadLocally()
   ├─ new OpenCVImageProcessor()
   ├─ new IntermediateResultCache()
   ├─ new DirectToolExecutor(processor, cache)
   ├─ imagePreviewPanel.setCache(cache)
   └─ ServerLauncher.startAsync()
           ↓
       ┌───┴────────────────────────────┐
       │ Background Thread              │
       ├─ Start Jetty server            │
       ├─ Register MCP tool handlers    │
       ├─ Bind to port 8082             │
       └─ Update UI: "Server running"   │
           ↓
7. Application ready
   ├─ UI interactive
   └─ MCP endpoint available
```

### Startup Time Breakdown

- JavaFX initialization: ~500ms
- OpenCV loading: ~2000ms (native libraries)
- MCP server startup: ~1000ms (Jetty)
- **Total: ~3.5 seconds**

## 🔒 Error Handling Strategy

### UI Execution Errors

```java
try {
    tool.setStatus(Status.RUNNING);
    Object result = processor.processImage(params);
    tool.setStatus(Status.COMPLETED);
    tool.setResult(result);
} catch (Exception e) {
    tool.setStatus(Status.ERROR);
    tool.setResult("Error: " + e.getMessage());
    // UI shows red error indicator
}
```

### MCP Server Errors

```java
try {
    Object result = processor.processImage(params);
    return McpResponse.success(result);
} catch (Exception e) {
    return McpResponse.error(-32603, e.getMessage());
    // Standard MCP error response
}
```

### Server Startup Errors

```java
serverLauncher.startAsync().thenAccept(success -> {
    if (success) {
        statusBar.showSuccess("Server running");
    } else {
        statusBar.showError("Server failed to start");
        // UI still functional, just no external access
    }
});
```

## 📊 Performance Characteristics

### UI Execution (Direct Calls)

| Metric | Value |
|--------|-------|
| Method Call Overhead | ~0.001ms |
| Parameter Passing | Zero copy (direct reference) |
| Result Handling | Direct object return |
| **Total Overhead** | **~1ms** |

### External Client Execution (MCP/HTTP)

| Metric | Value |
|--------|-------|
| Network Latency | ~1-2ms (localhost) |
| JSON Serialization | ~5-10ms |
| HTTP Overhead | ~2-3ms |
| MCP Protocol Parsing | ~1-2ms |
| **Total Overhead** | **~10-20ms** |

### Actual Processing Time

- Load Image (1920x1080): ~50ms
- Resize: ~30ms
- Grayscale: ~20ms
- Gaussian Blur: ~100ms
- Segment (Otsu): ~80ms
- Detect Contours: ~150ms

**Conclusion:** Overhead is negligible compared to processing time.

## 🎓 Design Patterns Used

### 1. Facade Pattern
`DirectToolExecutor` provides a simple interface to complex OpenCV operations.

### 2. Observer Pattern
UI components listen to tool status changes via callbacks.

### 3. Singleton Pattern
Single instances of `OpenCVImageProcessor` and `IntermediateResultCache`.

### 4. Builder Pattern
`McpConfig.builder()` for flexible configuration.

### 5. Strategy Pattern
Different execution strategies (direct vs. MCP) for same tools.

## 🔍 Key Design Decisions

### Why Embedded Server?

**Alternative: Separate Process**
```
JavaFX UI → HTTP → Separate MCP Server Process
```
❌ Process management complexity
❌ Inter-process communication overhead
❌ Duplicate processing engine
❌ Cache synchronization issues

**Chosen: Embedded Server**
```
JavaFX UI + MCP Server (same process)
```
✅ Simple deployment (one executable)
✅ Zero overhead for UI
✅ Shared resources
✅ Easy debugging

### Why Direct Execution for UI?

**Alternative: UI → HTTP → Embedded Server**
```
UI calls its own HTTP endpoint
```
❌ Unnecessary serialization
❌ HTTP overhead for local calls
❌ Complex error handling

**Chosen: Direct Method Calls**
```
UI → DirectToolExecutor → OpenCVImageProcessor
```
✅ Zero serialization overhead
✅ Direct object passing
✅ Immediate error feedback
✅ Simpler code

### Why Shared Cache?

**Alternative: Separate Caches**
```
UI Cache + Server Cache (duplicated)
```
❌ Memory waste
❌ Synchronization complexity
❌ Inconsistent state

**Chosen: Single Shared Cache**
```
IntermediateResultCache (thread-safe)
```
✅ Memory efficient
✅ Consistent view
✅ Cross-client sharing
✅ Simple implementation

## 🏆 Architecture Benefits

### For Users
- **Fast UI** - No HTTP overhead
- **Visual Feedback** - See results immediately
- **External Access** - Share tools with AI agents

### For Developers
- **Simple Deployment** - One JAR file
- **Easy Debugging** - Single process to debug
- **Clean Code** - Clear separation of concerns
- **Extensible** - Easy to add new tools

### For Teaching
- **Clear Example** - Shows how to integrate MCP
- **Best Practices** - Thread safety, error handling
- **Complete** - Full working application
- **Documented** - Extensive inline comments

## 🔮 Future Enhancements

### Potential Improvements

1. **Tool Plugins** - Load tools dynamically from JAR files
2. **Workflow Serialization** - Save/load pipelines as JSON
3. **Parallel Execution** - Execute independent tools concurrently
4. **Remote Servers** - Connect to external MCP servers
5. **Batch Processing** - Process multiple images
6. **History Tracking** - Undo/redo functionality
7. **Performance Metrics** - Track execution times

### Scaling Considerations

**Current:** Single-threaded tool execution (sequential)
**Future:** Thread pool for parallel execution of independent tools

**Current:** All results in memory (IntermediateResultCache)
**Future:** Disk-backed cache with LRU eviction

**Current:** localhost-only MCP server
**Future:** Remote access with authentication

## 📝 Summary

This embedded MCP architecture provides:

✅ **Simplicity** - Single process, straightforward code
✅ **Performance** - Zero overhead for UI operations
✅ **Flexibility** - Both UI and API access
✅ **Maintainability** - Shared logic, no duplication
✅ **Educational Value** - Clear example for teaching

Perfect for desktop applications that need:
- Rich graphical interfaces
- External programmability via MCP
- Direct user interaction
- AI agent integration

This pattern is ideal for tools, utilities, and applications where you want the best of both worlds: a powerful UI for humans and a clean API for machines.
