# JavaFX + MCP: Image Processing Application

A comprehensive teaching example demonstrating how to **embed MCP (Model Context Protocol) capabilities into JavaFX applications**.

## 🎯 What This Project Teaches

This project demonstrates the **embedded MCP architecture** - running an MCP server directly within a JavaFX application process, allowing:

1. **Internal UI access** - JavaFX UI calls image processing tools directly (no HTTP overhead)
2. **External client access** - Remote MCP clients (like Claude Desktop) can connect via HTTP/stdio
3. **Shared resources** - Both UI and external clients use the same processing engine and cache
4. **Single process** - Everything runs in one JVM for simplicity

## 🏗️ Architecture Overview

```
Single JavaFX Application Process
├── JavaFX UI
│   ├── Tool Pipeline Builder (manual execution)
│   ├── Parameter Editor
│   ├── Image Preview Panel (thumbnails + zoom)
│   └── DirectToolExecutor (direct method calls)
│
└── Embedded MCP Server (background thread)
    ├── HTTP endpoint: localhost:8082/mcp
    ├── Exposes 10 image processing tools
    └── Accepts external MCP client connections

Both share:
├── OpenCVImageProcessor (image processing engine)
└── IntermediateResultCache (result storage)
```

### Key Architectural Benefits

✅ **Zero HTTP overhead for UI** - Direct Java method calls
✅ **External MCP access** - Standard MCP protocol via HTTP/stdio
✅ **Shared cache** - Results accessible from both UI and external clients
✅ **Simple deployment** - Single executable JAR
✅ **Teaching-friendly** - Clear, understandable code structure

## 🚀 Quick Start

### Prerequisites
- Java 17 or higher
- Gradle 7.0+ (wrapper included)

### Build & Run

```bash
# 1. Build the application
./gradlew clean build

# 2. Run the JavaFX application
./gradlew run

# The embedded MCP server starts automatically!
# UI displays: "MCP Server running: http://localhost:8082/mcp"
```

### Using the Application

1. **Build a Pipeline** - Drag tools from left panel to center
2. **Configure Parameters** - Click a tool to edit its parameters
3. **Execute** - Click Play ▶️ button
4. **View Results** - Thumbnails appear in bottom preview panel
5. **Zoom Images** - Double-click any thumbnail for full view

### Connect External MCP Clients

While the application is running, external MCP clients can connect:

```bash
# Claude Desktop config (~/.config/Claude/claude_desktop_config.json)
{
  "mcpServers": {
    "image-processing": {
      "url": "http://localhost:8082/mcp"
    }
  }
}
```

## 🛠️ Features

### 10 Image Processing Tools

1. **load_image** - Load from path/URL/base64
2. **resize_image** - Resize with 5 interpolation methods
3. **segment_image** - Threshold-based segmentation (6 types)
4. **color_to_grayscale** - RGB to grayscale conversion
5. **filter_image** - Gaussian, Median, Bilateral filters
6. **denoise_image** - Advanced noise reduction
7. **blur_image** - Various blur effects
8. **detect_contours** - Find and filter contours
9. **output_segmented** - Extract segmented regions
10. **display_image** - Return base64 for MCP client display

### JavaFX UI Features

- **Drag & Drop** - Intuitive pipeline creation
- **Dynamic Parameters** - Type-specific editors (int, float, path, enum)
- **Image Previews** - Automatic thumbnail generation
- **Visual Feedback** - Color-coded status indicators
- **Result Cache** - Chain tools using intermediate results
- **Workflow Management** - Save/load pipelines (coming soon)

## 📂 Project Structure

```
src/main/java/com/imageprocessing/
├── server/
│   ├── ImageProcessingMcpServer.java     # MCP server implementation
│   ├── ServerLauncher.java               # Embedded server wrapper
│   ├── OpenCVImageProcessor.java         # Image processing engine (shared)
│   ├── IntermediateResultCache.java      # Result cache (shared)
│   └── McpConfig.java                    # Server configuration
├── execution/
│   └── DirectToolExecutor.java           # Direct tool execution (UI)
└── ui/
    ├── ImageProcessingApp.java           # JavaFX application entry point
    ├── MainController.java               # Main UI controller
    ├── model/                            # Data models
    └── components/                       # UI components
        ├── ToolCollectionPane.java
        ├── ToolPipelinePane.java
        ├── ParameterEditorPane.java
        ├── ImagePreviewPanel.java        # Image preview with zoom
        ├── ControlBar.java
        └── StatusBar.java
```

## 🎓 Learning Guide

### How to Embed MCP in Your JavaFX App

This project serves as a template for adding MCP capabilities to existing JavaFX applications:

#### Step 1: Add MCP Dependencies

```gradle
dependencies {
    implementation platform('io.modelcontextprotocol.sdk:mcp-bom:0.13.1')
    implementation 'io.modelcontextprotocol.sdk:mcp:0.13.1'
    implementation 'org.eclipse.jetty:jetty-server:11.0.20'
    implementation 'org.eclipse.jetty:jetty-servlet:11.0.20'
}
```

#### Step 2: Create ServerLauncher

Wrap your MCP server in a background launcher:

```java
public class ServerLauncher {
    private McpAsyncServer mcpServer;

    public CompletableFuture<Boolean> startAsync() {
        return CompletableFuture.supplyAsync(() -> {
            // Start MCP server in background thread
            // Return true if successful
        });
    }
}
```

#### Step 3: Initialize in JavaFX Application

```java
@Override
public void start(Stage primaryStage) {
    // Build UI
    MainController controller = new MainController();

    // Start embedded MCP server
    controller.initializeEmbeddedServer();

    // Show stage
    primaryStage.show();
}
```

#### Step 4: Create Direct Executor

Allow your UI to call tools directly:

```java
public class DirectToolExecutor {
    private final YourProcessorClass processor;

    public CompletableFuture<Result> executeTool(ToolInstance tool) {
        return CompletableFuture.supplyAsync(() -> {
            // Direct method call - no HTTP!
            return processor.processImage(tool.getParams());
        });
    }
}
```

#### Step 5: Share Resources

Both UI and MCP server use the same processor:

```java
// Shared by UI and MCP server
OpenCVImageProcessor processor = new OpenCVImageProcessor();
IntermediateResultCache cache = new IntermediateResultCache();

// UI uses direct execution
DirectToolExecutor uiExecutor = new DirectToolExecutor(processor, cache);

// MCP server uses same instances
ServerLauncher server = new ServerLauncher(config, processor, cache);
```

## 🔬 Example Workflows

### Example 1: Simple Resize
```
1. load_image → image_path: "/path/to/image.jpg", result_key: "original"
2. resize_image → result_key: "original", width: 800, height: 600, output_path: "/tmp/resized.jpg"
```

### Example 2: Advanced Segmentation
```
1. load_image → image_path: "/path/to/image.jpg", result_key: "original"
2. denoise_image → result_key: "original", output_key: "clean"
3. color_to_grayscale → result_key: "clean", output_key: "gray"
4. segment_image → result_key: "gray", threshold_type: "OTSU", output_key: "segmented"
5. detect_contours → result_key: "segmented", min_area: 100, output_path: "/tmp/contours.jpg"
```

### Example 3: Artistic Effect
```
1. load_image → image_url: "https://example.com/photo.jpg", result_key: "photo"
2. blur_image → result_key: "photo", blur_type: "MOTION", angle: 45, output_path: "/tmp/blur.jpg"
```

## 🧪 Testing

### Run Unit Tests
```bash
./gradlew test
```

### Test UI Manually
1. Launch application: `./gradlew run`
2. Verify "MCP Server running" message
3. Build a test pipeline
4. Execute and verify results appear in preview panel

### Test External Client Access
1. Keep application running
2. Connect via curl:
```bash
curl -X POST http://localhost:8082/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "method": "tools/call",
    "params": {
      "name": "get_image_info",
      "arguments": {"image_path": "/path/to/image.jpg"}
    },
    "id": 1
  }'
```

## 📦 Building Distributions

### Development Build
```bash
./gradlew build
```

### Create Runnable JAR
```bash
./gradlew uiJar
java -jar build/libs/image-processing-ui.jar
```

## 🎨 Customization

### Add Your Own Tools

1. **Add tool to OpenCVImageProcessor:**
```java
public String yourCustomTool(Mat image, Map<String, Object> params) {
    // Your processing logic
    return "Success";
}
```

2. **Add tool specification to ServerLauncher:**
```java
var tool = createTool("your_tool", "Description",
    schema, (exchange, request) -> {
        // Call processor.yourCustomTool()
    });
```

3. **Add to DirectToolExecutor:**
```java
case "your_tool" -> executeYourTool(params);
```

4. **Add to ToolRegistry:**
```java
new ToolMetadata("your_tool", "Description", Category.CUSTOM, ...);
```

### Customize UI

- **Colors**: Edit `src/main/resources/css/application.css`
- **Layout**: Modify `ImageProcessingApp.java`
- **Components**: Extend classes in `ui/components/`

## 🐛 Troubleshooting

### OpenCV Not Loading
```bash
# Verify OpenCV dependency
./gradlew dependencies | grep opencv

# Should see: org.openpnp:opencv:4.9.0-0
```

### MCP Server Won't Start
- Check port 8082 is available: `netstat -an | grep 8082`
- Check logs in `./logs/` directory
- Verify server JAR was built: `ls -lh build/libs/*.jar`

### UI Not Responding
- Check JavaFX modules are loaded
- Verify Java 17+ is being used: `java -version`
- Check for exceptions in console output

## 📚 Additional Resources

- [MCP Specification](https://modelcontextprotocol.io)
- [OpenCV Documentation](https://opencv.org)
- [JavaFX Documentation](https://openjfx.io)

## 🤝 Contributing

This is a teaching example. Feel free to fork and adapt for your own projects!

## 📄 License

MIT License - See LICENSE file for details

---

**Built with ❤️ to demonstrate JavaFX + MCP integration**

This project shows how to create rich desktop applications that are both:
- **Powerful UIs** for human users (JavaFX)
- **Programmable APIs** for AI agents (MCP)

The embedded architecture provides the best of both worlds! 🎉
