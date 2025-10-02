# Quick Start Guide - Image Processing UI with MCP Server

This guide will help you quickly get the complete application running with the MCP server integration.

## Prerequisites

- Java 17 or higher
- Gradle 7.0 or higher
- Internet connection (for downloading dependencies)

## Step 1: Build the Project

Build both the server and UI components:

```bash
# Build everything
./gradlew build

# Build server fat JAR
./gradlew shadowJar

# Build UI fat JAR
./gradlew uiJar
```

After building, you should have:
- `build/libs/image-processing-mcp-server.jar` - MCP server executable
- `build/libs/image-processing-ui.jar` - JavaFX UI executable

## Step 2: Run the Application

### Option A: Using Gradle (Recommended for Development)

```bash
# Run the UI application (either command works)
./gradlew run
# OR
./gradlew runUI
```

The UI will start and be ready to launch the MCP server.

**Note:** You can also run the MCP server standalone:
```bash
./gradlew runServer --args="--http 8082"
```

### Option B: Using JAR Files

```bash
# Run the UI application
java -jar build/libs/image-processing-ui.jar
```

## Step 3: Launch MCP Server

1. In the UI application, click the **"Launch MCP Server"** button in the top toolbar
2. Wait for the status bar to show "MCP server connected" (usually takes 2-5 seconds)
3. The server indicator in the toolbar will turn green

**What happens behind the scenes:**
- UI spawns server process: `java -jar build/libs/image-processing-mcp-server.jar --http 8082`
- Server initializes OpenCV and starts HTTP endpoint at `http://localhost:8082/mcp`
- UI tests connection and creates executor services
- Server logs are captured to `./logs/mcp-server-YYYYMMDD_HHMMSS.log`

## Step 4: Build Your First Pipeline

### Example: Resize and Segment an Image

1. **Add Load Image Tool**:
   - Drag "load_image" from the left panel to the pipeline (center)
   - Click on the tool card to edit parameters
   - Set `image_path` to your test image (e.g., `/path/to/test.jpg`)
   - Set `result_key` to `original_image`

2. **Add Resize Tool**:
   - Drag "resize_image" to the pipeline
   - Set `result_key` to `original_image` (loads from cache)
   - Set `width` to `800`
   - Set `height` to `600`
   - Set `output_key` to `resized_image`

3. **Add Segment Tool**:
   - Drag "segment_image" to the pipeline
   - Set `result_key` to `resized_image`
   - Set `threshold` to `127`
   - Set `output_path` to `/tmp/segmented.png`

4. **Execute Pipeline**:
   - Click the **Play** button in the toolbar
   - Watch the progress in the status bar
   - Tool cards will update with RUNNING → COMPLETED/ERROR status
   - Check the result in each tool card

## Step 5: View Results

- **Tool Cards**: Show execution status and result messages
- **Status Bar**: Shows overall progress and final summary
- **Output Files**: Check the paths you specified for saved images
- **Cached Results**: Available in dropdown menus for subsequent tools

## Common Workflows

### Basic Image Resize

```
load_image → resize_image → (output_path)
```

### Grayscale Conversion and Blur

```
load_image → color_to_grayscale → blur_image → (output_path)
```

### Complete Segmentation Pipeline

```
load_image → resize_image → color_to_grayscale → segment_image → output_segmented
```

### Filter and Denoise

```
load_image → denoise_image → filter_image → (output_path)
```

### Contour Detection

```
load_image → color_to_grayscale → detect_contours → (output_path)
```

## Troubleshooting

### Server Won't Start

**Error**: "Failed to start MCP server"

**Check**:
```bash
# Verify JAR exists
ls -lh build/libs/image-processing-mcp-server.jar

# Test server manually
java -jar build/libs/image-processing-mcp-server.jar --http 8082
# Press Ctrl+C to stop after confirming it starts
```

**Fix**: Rebuild with `./gradlew shadowJar`

### Tool Execution Fails

**Error**: "Tool execution failed: ..."

**Common Causes**:
1. **Invalid file path**: Use absolute paths, not relative
2. **Missing parameters**: Check required parameters are set
3. **Cached key not found**: Ensure previous tool set output_key
4. **Image format unsupported**: Use PNG, JPG, JPEG, BMP

**Debug**:
```bash
# Check server logs
tail -f logs/mcp-server-*.log
```

## Example Session

```bash
# Build and run UI
./gradlew clean build shadowJar
./gradlew runUI

# In UI:
# 1. Click "Launch MCP Server"
# 2. Add load_image (image_path=/path/to/cat.jpg, result_key=cat)
# 3. Add resize_image (result_key=cat, width=400, height=300, output_key=cat_small)
# 4. Add blur_image (result_key=cat_small, blur_type=GAUSSIAN, kernel_size=11, output_path=/tmp/cat_blurred.png)
# 5. Click "Play"
# 6. Wait for completion
# 7. Check /tmp/cat_blurred.png
```

For detailed architecture and implementation information, see `INTEGRATION.md`.
