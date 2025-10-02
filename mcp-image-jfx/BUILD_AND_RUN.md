# Image Processing MCP Server - Build and Run Guide

## Project Overview

This project provides a comprehensive Model Context Protocol (MCP) server for image processing using OpenCV and JavaFX. It includes 10 powerful image processing tools accessible via both stdio and HTTP transports.

## Architecture

```
/mnt/c/Dev/tmp/mcp-image-jfx/
├── src/main/java/com/imageprocessing/
│   └── server/
│       ├── ImageProcessingMcpServer.java   # Main MCP server with 10 tools
│       ├── OpenCVImageProcessor.java       # OpenCV operations wrapper
│       └── IntermediateResultCache.java    # Thread-safe result cache
├── build.gradle                            # Gradle build configuration
├── EXAMPLES.json                           # Tool invocation examples
└── BUILD_AND_RUN.md                        # This file
```

## Key Features

### 10 Image Processing Tools

1. **load_image** - Load images from path/URL/base64, store in cache
2. **resize_image** - Resize with configurable interpolation
3. **segment_image** - Threshold-based segmentation (including Otsu)
4. **color_to_grayscale** - Color to grayscale conversion
5. **filter_image** - Apply filters (Gaussian, Median, Bilateral)
6. **denoise_image** - Non-Local Means denoising
7. **blur_image** - Various blur effects (Gaussian, Motion, Box)
8. **detect_contours** - Detect and filter contours
9. **output_segmented** - Extract and save segmented regions
10. **display_image** - Convert to base64 PNG for MCP client display

### Infrastructure

- **Intermediate Result Cache**: Thread-safe in-memory cache for storing processed images between tool calls
- **Dual Transport Support**: Both stdio (for CLI integration) and HTTP (for web services)
- **Flexible Input**: Accept images from file paths, URLs, base64 data, or cached results
- **Rich Error Handling**: Detailed error messages with proper exception handling
- **OpenCV Integration**: Full OpenCV 4.9.0 support with automatic native library loading

## Prerequisites

- Java 17 or higher (tested with Java 21)
- Gradle 7.0+ (tested with Gradle 9.0.0)
- Linux/WSL2 or macOS (Windows may require additional setup)

## Building the Project

### 1. Build the Fat JAR

```bash
cd /mnt/c/Dev/tmp/mcp-image-jfx
gradle shadowJar --no-daemon
```

This creates: `build/libs/image-processing-mcp-server.jar` (~121 MB, includes all dependencies and OpenCV natives)

### 2. Verify Build

```bash
ls -lh build/libs/
```

Expected output:
```
-rwxrwxrwx 1 user user 121M Oct  1 17:22 image-processing-mcp-server.jar
```

## Running the Server

### Stdio Mode (Default)

For MCP clients that communicate via stdin/stdout:

```bash
java -jar build/libs/image-processing-mcp-server.jar
```

Expected startup output (on stderr):
```
Initializing OpenCV...
OpenCV loaded successfully: 4.9.0
Image Processing MCP Server started (stdio mode)
Version: 1.0.0
Ready for connections...
```

### HTTP Mode

For REST API access:

```bash
java -jar build/libs/image-processing-mcp-server.jar --http [port]
```

Example (port 8082):
```bash
java -jar build/libs/image-processing-mcp-server.jar --http 8082
```

Expected startup output:
```
Initializing OpenCV...
OpenCV loaded successfully: 4.9.0
[main] INFO org.eclipse.jetty.server.Server - jetty-11.0.20...
Image Processing MCP Server started on HTTP port 8082
Version: 1.0.0
MCP endpoint: http://localhost:8082/mcp
```

## Tool Usage Examples

### Basic Tool Invocation

#### 1. Load an Image

```json
{
  "tool": "load_image",
  "arguments": {
    "image_path": "/path/to/image.jpg",
    "result_key": "original_image"
  }
}
```

Response:
```
Image loaded successfully!
Image Information:
- Width: 1920 pixels
- Height: 1080 pixels
- Channels: 3
- Depth: 8-bit unsigned
- Type: 8-bit unsigned C3
- Cached with key: original_image
```

#### 2. Resize Image

```json
{
  "tool": "resize_image",
  "arguments": {
    "result_key": "original_image",
    "width": 800,
    "height": 600,
    "interpolation": "CUBIC",
    "output_key": "resized_image"
  }
}
```

#### 3. Segment with Otsu Thresholding

```json
{
  "tool": "segment_image",
  "arguments": {
    "result_key": "original_image",
    "threshold": 0,
    "threshold_type": "OTSU",
    "output_key": "segmented_image"
  }
}
```

#### 4. Detect Contours

```json
{
  "tool": "detect_contours",
  "arguments": {
    "result_key": "segmented_image",
    "min_area": 100.0,
    "max_area": 10000.0,
    "min_circularity": 0.7,
    "output_path": "/tmp/contours.jpg"
  }
}
```

### Complete Processing Pipeline

```
1. Load → 2. Denoise → 3. Grayscale → 4. Segment → 5. Detect Contours → 6. Display
```

See `EXAMPLES.json` for complete workflow examples.

## Tool Parameters Reference

### Common Parameters

All tools accept one of these input sources:
- `image_path` (string): Local file path
- `image_url` (string): HTTP/HTTPS URL
- `image_data` (string): Base64 encoded image
- `result_key` (string): Key from cache

All tools accept these optional outputs:
- `output_path` (string): Save result to file
- `output_key` (string): Store result in cache

### Interpolation Methods (resize_image)

- `NEAREST`: Fastest, lowest quality
- `LINEAR`: Default, good quality
- `CUBIC`: High quality, slower
- `AREA`: Best for downsampling
- `LANCZOS4`: Highest quality, slowest

### Threshold Types (segment_image)

- `BINARY`: value > threshold → 255, else 0
- `BINARY_INV`: Inverse of BINARY
- `TRUNC`: value > threshold → threshold, else unchanged
- `TOZERO`: value > threshold → unchanged, else 0
- `TOZERO_INV`: Inverse of TOZERO
- `OTSU`: Automatic threshold calculation

### Filter Types (filter_image)

- `GAUSSIAN`: Gaussian blur (configurable sigma)
- `MEDIAN`: Median filter (good for salt-and-pepper noise)
- `BILATERAL`: Edge-preserving smoothing

### Blur Types (blur_image)

- `GAUSSIAN`: Standard Gaussian blur
- `MOTION`: Directional motion blur (with angle parameter)
- `BOX` / `AVERAGE`: Simple averaging filter

## Key Design Decisions

### 1. Intermediate Result Cache

**Purpose**: Avoid repeated file I/O and enable efficient pipelines

**Thread Safety**: Uses `ConcurrentHashMap` for concurrent access

**Memory Management**: Automatically releases Mat objects on removal/replacement

**Usage Pattern**:
```
load_image(result_key="img1") → resize(result_key="img1", output_key="img2") → display(result_key="img2")
```

### 2. OpenCV Native Library Loading

**Method**: `nu.pattern.OpenCV.loadLocally()`

**Why**: The openpnp:opencv dependency bundles native libraries for multiple platforms and handles extraction/loading automatically

**Verification**: Server logs "OpenCV loaded successfully: 4.9.0" on startup

### 3. Dual Transport Support

**Async (Stdio)**: Uses `McpServer.async()` with `Mono<CallToolResult>` for reactive programming

**Sync (HTTP)**: Uses `McpServer.sync()` with blocking calls for stateless REST API

**Implementation**: Helper method `createSyncToolFromAsync()` converts async handlers by calling `.block()`

### 4. Error Handling

**Philosophy**: Return `CallToolResult` with `isError=true` rather than throwing exceptions

**Benefits**: MCP clients receive structured error responses with clear messages

**Example**:
```java
return Mono.just(new McpSchema.CallToolResult.Builder()
    .content(List.of(new McpSchema.TextContent("Error: " + e.getMessage())))
    .isError(true)
    .build());
```

### 5. Helper Classes

**OpenCVImageProcessor**: Encapsulates all OpenCV operations, handles Mat↔BufferedImage conversions

**IntermediateResultCache**: Separate concern for result storage

**ToolFactory**: Nested class organizing tool creation logic

## Challenges Encountered

### 1. Gradle Shadow Plugin Compatibility

**Issue**: Shadow plugin 8.1.1 incompatible with Gradle 9.0.0

**Solution**: Upgraded to `com.gradleup.shadow` version 8.3.0 (successor to johnrengelman plugin)

### 2. BiFunction Method Name

**Issue**: `handle()` vs `apply()` for BiFunction interface

**Solution**: Changed `asyncHandler.handle(null, request)` to `asyncHandler.apply(null, request)`

### 3. Build File Deprecation

**Issue**: Using `$buildDir` in Gradle 9.0

**Solution**: Already using proper syntax, works correctly

## Testing Verification

### Compilation Test
```bash
gradle shadowJar --no-daemon
```
Result: ✅ BUILD SUCCESSFUL in 1m 9s

### Stdio Mode Test
```bash
timeout 3 java -jar build/libs/image-processing-mcp-server.jar 2>&1
```
Result: ✅ OpenCV loaded successfully: 4.9.0

### HTTP Mode Test
```bash
timeout 3 java -jar build/libs/image-processing-mcp-server.jar --http 8082 2>&1
```
Result: ✅ Server started on port 8082, MCP endpoint available

## MCP Client Configuration

### Claude Desktop Configuration

Add to `claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "image-processing": {
      "command": "java",
      "args": [
        "-jar",
        "/mnt/c/Dev/tmp/mcp-image-jfx/build/libs/image-processing-mcp-server.jar"
      ]
    }
  }
}
```

### Cline/Continue Configuration

Similar configuration for stdio transport.

## Performance Notes

- **JAR Size**: 121 MB (includes OpenCV natives for Linux, macOS, Windows)
- **Startup Time**: ~2 seconds (including OpenCV initialization)
- **Memory Usage**: Base ~200 MB, scales with cached images
- **Cache Strategy**: Manual cache management (user controls result_key/output_key)

## Future Enhancements

1. **JavaFX GUI Application**: Separate visual interface for interactive processing
2. **Additional Tools**: Histogram equalization, morphological operations, color space conversions
3. **Batch Processing**: Process multiple images in parallel
4. **Progress Callbacks**: For long-running operations
5. **Cache TTL**: Automatic expiration of cached results
6. **Image Format Validation**: Pre-validate before OpenCV processing

## Troubleshooting

### OpenCV Not Loading

**Symptom**: "Error: OpenCV library not loaded"

**Solution**: Ensure openpnp:opencv dependency is in classpath (should be automatic with shadowJar)

### Out of Memory

**Symptom**: `OutOfMemoryError` with large images

**Solution**: Increase JVM heap: `java -Xmx2G -jar image-processing-mcp-server.jar`

### File Not Found

**Symptom**: "Error: Failed to load image from provided source"

**Solution**: Use absolute paths, verify file exists, check permissions

## Additional Resources

- OpenCV Documentation: https://docs.opencv.org/4.9.0/
- MCP Specification: https://modelcontextprotocol.io/
- Project Repository: /mnt/c/Dev/tmp/mcp-image-jfx/

## Contact

For issues or questions about this implementation, refer to the code comments and examples in this repository.
