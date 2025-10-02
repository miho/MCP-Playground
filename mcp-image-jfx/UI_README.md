# Image Processing UI - User Guide

## Overview

A modern JavaFX-based graphical user interface for building and executing image processing pipelines using the MCP (Model Context Protocol) server backend.

## Features

### Visual Pipeline Builder
- **Drag-and-Drop Interface**: Drag tools from the collection into the pipeline
- **Reorderable Pipeline**: Reorder tools by dragging within the pipeline
- **Real-time Status**: Visual indicators show tool execution status (pending, running, completed, error)
- **Parameter Editor**: Dynamic form-based parameter editing with validation

### Tool Categories
- **Load**: Image loading from file, URL, or base64 data
- **Transform**: Resize, color conversion operations
- **Filter**: Gaussian, median, bilateral, denoising, blur
- **Analysis**: Segmentation, contour detection
- **Output**: Save results, display images

### Execution Control
- **Play/Stop**: Execute entire pipeline or stop mid-execution
- **Server Management**: Launch and monitor MCP server connection
- **Workflow Persistence**: Save and load workflow configurations
- **Progress Tracking**: Visual progress bar and status updates

## Architecture

### Component Structure

```
ImageProcessingApp (Main Application)
  └── MainController (Coordinates all components)
      ├── ToolCollectionPane (Left Panel)
      │   └── Searchable TreeView of tools by category
      ├── ToolPipelinePane (Center Panel)
      │   └── ListView of tool instances with status
      ├── ParameterEditorPane (Right Panel)
      │   └── Dynamic form for selected tool parameters
      ├── ControlBar (Top)
      │   └── Execution controls and server status
      └── StatusBar (Bottom)
          └── Status messages and execution statistics
```

### Model Classes

- **ToolMetadata**: Describes available tools and their parameters
- **ToolInstance**: Represents a tool in the pipeline with parameter values and execution state
- **WorkflowModel**: Manages the pipeline of tool instances
- **ToolRegistry**: Central registry of all available tools

## Running the Application

### Using Gradle

```bash
# Run the UI application
./gradlew runUI

# Build standalone UI JAR
./gradlew uiJar

# Run the standalone JAR
java -jar build/libs/image-processing-ui.jar
```

### System Requirements
- Java 17 or higher
- JavaFX 21 (included in dependencies)
- OpenCV 4.9.0 (included in dependencies)

## User Interface Guide

### 1. Tool Collection (Left Panel)

**Search**: Type in the search field to filter tools by name or description

**Categories**:
- Load: load_image
- Transform: resize_image, color_to_grayscale
- Filter: filter_image, denoise_image, blur_image
- Analysis: segment_image, detect_contours
- Output: output_segmented, display_image

**Adding Tools**: Drag a tool from the collection to the pipeline area

### 2. Execution Pipeline (Center Panel)

**Tool Cards**: Each tool in the pipeline is displayed as a card showing:
- Status indicator (colored circle)
- Tool name
- Parameter summary
- Remove button (X)

**Status Colors**:
- Blue: Pending
- Orange: Running
- Green: Completed
- Red: Error

**Reordering**: Drag tool cards to reorder the pipeline

**Removing**: Click the X button to remove a tool from the pipeline

### 3. Parameter Editor (Right Panel)

Select a tool in the pipeline to edit its parameters.

**Parameter Types**:

- **Image Path**: Text field with Browse button for file selection
- **Result Key**: ComboBox showing cached intermediate results
- **Integer**: Spinner for numeric input
- **Double/Float**: Text field with validation
- **Enum**: Dropdown with predefined values
- **Output Path**: Text field with Save As dialog
- **Output Key**: Text field for naming cached results

**Required Parameters**: Marked with asterisk (*)

**Buttons**:
- Apply: Confirm parameter changes
- Reset: Reset to default values

### 4. Control Bar (Top)

**Play Button**: Execute the entire pipeline sequentially

**Stop Button**: Abort pipeline execution (enabled during execution)

**Server Status**: Visual indicator showing MCP server connection state
- Red = Disconnected
- Green = Connected

**Launch MCP Server**: Start the backend server (required for execution)

**Save/Load Workflow**: Persist pipeline configurations to JSON files

### 5. Status Bar (Bottom)

Displays:
- Current operation status
- Progress bar during execution
- Tool statistics (total, completed, running, errors, pending)

## Workflow Example

1. **Launch MCP Server**: Click "Launch MCP Server" in the control bar
2. **Build Pipeline**:
   - Drag "load_image" to pipeline
   - Configure image_path parameter
   - Set result_key to "original"
3. **Add Processing**:
   - Drag "resize_image" to pipeline
   - Set result_key to "original" (use cached image)
   - Set width: 800, height: 600
   - Set output_key to "resized"
4. **Add Filter**:
   - Drag "blur_image" to pipeline
   - Set result_key to "resized"
   - Set blur_type: GAUSSIAN
   - Set output_path to save result
5. **Execute**: Click Play button
6. **Monitor**: Watch status indicators change as tools execute
7. **Save Workflow**: Click "Save Workflow" to persist the pipeline

## Design Decisions

### 1. Three-Panel Layout
- **Separation of Concerns**: Tools, pipeline, and parameters are visually distinct
- **Responsive**: SplitPane allows users to resize panels
- **Optimal Proportions**: 20% | 50% | 30% default split

### 2. Drag-and-Drop Paradigm
- **Intuitive**: Natural metaphor for building pipelines
- **Visual Feedback**: Drag events provide clear affordances
- **Flexible**: Supports both adding and reordering

### 3. Observable Properties
- **Reactive UI**: Property bindings automatically update UI elements
- **Loose Coupling**: Components communicate through observable models
- **Thread-Safe**: Platform.runLater ensures UI updates on JavaFX thread

### 4. Status Indicators
- **Visual Clarity**: Color-coded circles show status at a glance
- **Real-time Updates**: Status changes are immediately visible
- **Error Handling**: Red indicators highlight failures

### 5. Dynamic Parameter Forms
- **Type-Specific Controls**: Each parameter type gets appropriate input widget
- **Validation**: Real-time validation with visual feedback
- **File Dialogs**: Native file choosers for path selection
- **Result Keys**: ComboBox populated with cached results for chaining

## Component Interaction Flow

```
User Action: Drag tool to pipeline
  └─> ToolCollectionPane.onDragDetected
      └─> ToolPipelinePane.onDragDropped
          └─> WorkflowModel.addTool
              └─> ListView updates (ObservableList)
                  └─> StatusBar.updateStats

User Action: Select tool in pipeline
  └─> ToolPipelinePane.onToolSelected
      └─> ParameterEditorPane.editTool
          └─> Build dynamic parameter form

User Action: Click Play
  └─> ControlBar.onPlayAction
      └─> MainController.executePipeline
          └─> For each ToolInstance:
              ├─> Set status to RUNNING
              ├─> Execute tool (via MCP)
              ├─> Set status to COMPLETED or ERROR
              └─> Update cached results

User Action: Launch Server
  └─> ControlBar.onLaunchServerAction
      └─> MainController.launchMcpServer
          └─> Start server process
              └─> Update server status indicator
```

## Next Steps for MCP Integration

The UI is prepared for backend integration with these interfaces:

1. **McpClient Class** (to be implemented):
   - Connect to MCP server (stdio or HTTP)
   - Call tools with parameters
   - Handle responses and errors
   - Manage cache synchronization

2. **Tool Execution** (in MainController):
   - Replace simulateExecution() with actual MCP calls
   - Map ToolInstance parameters to MCP request format
   - Parse MCP responses to update ToolInstance status
   - Handle async execution with callbacks

3. **Server Management**:
   - Launch server as subprocess
   - Monitor server health
   - Handle server crashes/restarts
   - Parse server logs for status

4. **Workflow Serialization**:
   - Save WorkflowModel to JSON
   - Load WorkflowModel from JSON
   - Version compatibility checks

5. **Image Preview**:
   - Add preview pane for loaded images
   - Display intermediate results
   - Thumbnail generation for cached results

6. **Advanced Features**:
   - Batch processing multiple images
   - Parallel execution of independent tools
   - Workflow templates
   - Tool history and undo/redo

## Styling Customization

The application uses a modular CSS file (`/css/application.css`) with:
- CSS variables for easy color customization
- Dark mode support (add `.dark-mode` class to root)
- Responsive media queries
- Hover and focus states for accessibility

### Changing Theme Colors

Edit `/src/main/resources/css/application.css`:

```css
.root {
    -fx-accent: #3498db;  /* Primary color */
    -fx-status-completed: #27ae60;  /* Success color */
    -fx-status-error: #e74c3c;  /* Error color */
}
```

## Troubleshooting

**UI doesn't launch**:
- Check Java version: `java -version` (must be 17+)
- Verify JavaFX installation: `./gradlew dependencies --configuration runtimeClasspath`

**CSS not loading**:
- Ensure `application.css` exists in `/src/main/resources/css/`
- Check file path in ImageProcessingApp.java

**Tools not draggable**:
- Check console for exceptions
- Verify ToolRegistry has tools loaded

**Parameters not saving**:
- Check parameter change listeners in ParameterEditorPane
- Verify ToolInstance.setParameter is called

## Contributing

When adding new tools:
1. Add tool definition to ToolRegistry
2. Define parameters with appropriate types
3. Update tool categories if needed
4. Test parameter editor rendering
5. Ensure MCP server supports the tool

## License

Part of the Image Processing MCP Server project.
