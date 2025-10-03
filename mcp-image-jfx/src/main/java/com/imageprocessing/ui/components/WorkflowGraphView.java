package com.imageprocessing.ui.components;

import com.imageprocessing.ui.model.ToolInstance;
import com.imageprocessing.ui.model.WorkflowModel;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import java.util.*;
import java.util.function.Consumer;

/**
 * Visual dependency graph view for workflow pipeline.
 * Shows tools as nodes and their dependencies based on input_key/output_key connections.
 */
public class WorkflowGraphView extends VBox {

    private static final double NODE_WIDTH = 180;
    private static final double NODE_HEIGHT = 80;
    private static final double HORIZONTAL_GAP = 120;
    private static final double VERTICAL_GAP = 100;
    private static final double PADDING = 50;

    private WorkflowModel workflowModel;
    private Canvas canvas;
    private ScrollPane scrollPane;
    private Label placeholderLabel;
    private StackPane canvasContainer;

    // Graph layout data
    private Map<ToolInstance, GraphNode> nodePositions;
    private List<GraphEdge> edges;
    private ToolInstance selectedTool;

    // Theme-aware colors
    private boolean isDarkTheme = true;

    // Selection callback
    private Consumer<ToolInstance> onToolSelected;

    // Track status listeners for cleanup
    private Map<ToolInstance, javafx.beans.value.ChangeListener<ToolInstance.Status>> statusListeners;

    public WorkflowGraphView() {
        super();
        getStyleClass().add("workflow-graph-view");
        setPadding(new Insets(10));

        // Initialize data structures
        nodePositions = new HashMap<>();
        edges = new ArrayList<>();
        statusListeners = new HashMap<>();

        // Create UI
        buildUI();
    }

    private void buildUI() {
        // Title
        Label title = new Label("Workflow Dependency Graph");
        title.getStyleClass().add("graph-title");
        title.setFont(Font.font("System", FontWeight.BOLD, 16));

        // Placeholder for empty workflow
        placeholderLabel = new Label("No workflow to display\nAdd tools to the pipeline to see the dependency graph");
        placeholderLabel.getStyleClass().add("graph-placeholder");
        placeholderLabel.setStyle("-fx-text-alignment: center;");

        // Canvas for drawing graph
        canvas = new Canvas(800, 600);
        canvas.setOnMouseClicked(e -> handleCanvasClick(e.getX(), e.getY()));

        // Container for canvas with centering
        canvasContainer = new StackPane(canvas);
        canvasContainer.setStyle("-fx-background-color: transparent;");

        // ScrollPane for large graphs
        scrollPane = new ScrollPane(canvasContainer);
        scrollPane.getStyleClass().add("graph-scroll");
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setPannable(true);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        // Add components
        getChildren().addAll(title, scrollPane);

        // Show placeholder initially
        showPlaceholder();
    }

    /**
     * Set the workflow model to visualize.
     */
    public void setWorkflowModel(WorkflowModel model) {
        if (this.workflowModel != null) {
            // Remove old listener
            this.workflowModel.getToolInstances().removeListener(workflowChangeListener);
            // Remove all status listeners
            removeAllStatusListeners();
        }

        this.workflowModel = model;

        if (model != null) {
            // Add listener for workflow changes
            model.getToolInstances().addListener(workflowChangeListener);

            // Add status listeners to all existing tools
            for (ToolInstance tool : model.getToolInstances()) {
                addStatusListener(tool);
            }
        }

        // Redraw graph
        redrawGraph();
    }

    /**
     * Set the selected tool for highlighting.
     */
    public void setSelectedTool(ToolInstance tool) {
        this.selectedTool = tool;
        redrawGraph();
    }

    /**
     * Update theme colors.
     */
    public void setDarkTheme(boolean dark) {
        this.isDarkTheme = dark;
        redrawGraph();
    }

    /**
     * Set callback for when a tool is selected in the graph.
     */
    public void setOnToolSelected(Consumer<ToolInstance> callback) {
        this.onToolSelected = callback;
    }

    /**
     * Listener for workflow changes.
     */
    private final ListChangeListener<ToolInstance> workflowChangeListener = change -> {
        while (change.next()) {
            // Add status listeners to newly added tools
            for (ToolInstance tool : change.getAddedSubList()) {
                addStatusListener(tool);
            }
            // Remove status listeners from removed tools
            for (ToolInstance tool : change.getRemoved()) {
                removeStatusListener(tool);
            }
        }
        Platform.runLater(this::redrawGraph);
    };

    /**
     * Main method to redraw the entire graph.
     */
    private void redrawGraph() {
        if (workflowModel == null || workflowModel.isEmpty()) {
            showPlaceholder();
            return;
        }

        hidePlaceholder();

        // Build graph structure
        buildGraphStructure();

        // Layout nodes
        layoutNodes();

        // Calculate canvas size
        double maxX = 0, maxY = 0;
        for (GraphNode node : nodePositions.values()) {
            maxX = Math.max(maxX, node.x + NODE_WIDTH);
            maxY = Math.max(maxY, node.y + NODE_HEIGHT);
        }

        // Resize canvas with padding
        double canvasWidth = Math.max(800, maxX + 2 * PADDING);
        double canvasHeight = Math.max(600, maxY + 2 * PADDING);
        canvas.setWidth(canvasWidth);
        canvas.setHeight(canvasHeight);

        // Draw graph
        drawGraph();
    }

    /**
     * Build graph structure by analyzing input_key and output_key parameters.
     */
    private void buildGraphStructure() {
        nodePositions.clear();
        edges.clear();

        List<ToolInstance> tools = workflowModel.getToolInstances();

        // Create nodes
        for (ToolInstance tool : tools) {
            nodePositions.put(tool, new GraphNode(tool));
        }

        // Create edges based on key dependencies
        Map<String, ToolInstance> outputKeyMap = new HashMap<>();

        // First pass: build output_key map
        for (ToolInstance tool : tools) {
            String outputKey = (String) tool.getParameter("output_key");
            if (outputKey != null && !outputKey.isBlank()) {
                outputKeyMap.put(outputKey, tool);
            }
        }

        // Second pass: create edges
        for (ToolInstance tool : tools) {
            String inputKey = (String) tool.getParameter("input_key");
            if (inputKey != null && !inputKey.isBlank()) {
                ToolInstance sourceTool = outputKeyMap.get(inputKey);
                if (sourceTool != null) {
                    edges.add(new GraphEdge(sourceTool, tool, inputKey));
                }
            }
        }
    }

    /**
     * Layout nodes using a simple layered approach.
     * Tools are arranged left-to-right based on their dependency depth.
     */
    private void layoutNodes() {
        if (nodePositions.isEmpty()) return;

        // Calculate depth (layer) for each node
        Map<ToolInstance, Integer> depths = calculateDepths();

        // Group nodes by depth
        Map<Integer, List<GraphNode>> layers = new TreeMap<>();
        for (Map.Entry<ToolInstance, GraphNode> entry : nodePositions.entrySet()) {
            int depth = depths.getOrDefault(entry.getKey(), 0);
            layers.computeIfAbsent(depth, k -> new ArrayList<>()).add(entry.getValue());
        }

        // Position nodes layer by layer
        int layerIndex = 0;
        for (Map.Entry<Integer, List<GraphNode>> entry : layers.entrySet()) {
            List<GraphNode> layerNodes = entry.getValue();

            // Calculate x position for this layer
            double x = PADDING + layerIndex * (NODE_WIDTH + HORIZONTAL_GAP);

            // Calculate y positions to center nodes vertically
            double totalHeight = layerNodes.size() * NODE_HEIGHT + (layerNodes.size() - 1) * VERTICAL_GAP;
            double startY = PADDING;

            for (int i = 0; i < layerNodes.size(); i++) {
                GraphNode node = layerNodes.get(i);
                node.x = x;
                node.y = startY + i * (NODE_HEIGHT + VERTICAL_GAP);
            }

            layerIndex++;
        }
    }

    /**
     * Calculate depth of each tool in the dependency tree.
     * Depth 0 = no dependencies (root nodes)
     * Depth N = depends on nodes at depth N-1
     */
    private Map<ToolInstance, Integer> calculateDepths() {
        Map<ToolInstance, Integer> depths = new HashMap<>();
        Map<ToolInstance, List<ToolInstance>> dependencies = new HashMap<>();

        // Build dependency map
        for (GraphEdge edge : edges) {
            dependencies.computeIfAbsent(edge.target, k -> new ArrayList<>()).add(edge.source);
        }

        // Calculate depths using topological sort approach
        Set<ToolInstance> visited = new HashSet<>();
        for (ToolInstance tool : nodePositions.keySet()) {
            calculateDepth(tool, dependencies, depths, visited);
        }

        return depths;
    }

    /**
     * Recursive depth calculation with memoization.
     */
    private int calculateDepth(ToolInstance tool, Map<ToolInstance, List<ToolInstance>> deps,
                               Map<ToolInstance, Integer> depths, Set<ToolInstance> visited) {
        if (depths.containsKey(tool)) {
            return depths.get(tool);
        }

        if (visited.contains(tool)) {
            // Circular dependency detected, return 0
            return 0;
        }

        visited.add(tool);

        List<ToolInstance> dependencies = deps.get(tool);
        if (dependencies == null || dependencies.isEmpty()) {
            depths.put(tool, 0);
            return 0;
        }

        int maxDepth = 0;
        for (ToolInstance dep : dependencies) {
            int depDepth = calculateDepth(dep, deps, depths, visited);
            maxDepth = Math.max(maxDepth, depDepth + 1);
        }

        depths.put(tool, maxDepth);
        return maxDepth;
    }

    /**
     * Draw the graph on canvas.
     */
    private void drawGraph() {
        GraphicsContext gc = canvas.getGraphicsContext2D();

        // Clear canvas
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        // Set background
        if (isDarkTheme) {
            gc.setFill(Color.web("#1e1e1e"));
        } else {
            gc.setFill(Color.web("#ffffff"));
        }
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        // Draw edges first (so they appear behind nodes)
        for (GraphEdge edge : edges) {
            drawEdge(gc, edge);
        }

        // Draw nodes
        for (GraphNode node : nodePositions.values()) {
            drawNode(gc, node);
        }
    }

    /**
     * Draw a single node.
     */
    private void drawNode(GraphicsContext gc, GraphNode node) {
        ToolInstance tool = node.tool;
        boolean isSelected = tool == selectedTool;

        // Determine colors based on status and theme
        Color bgColor, borderColor, textColor, statusColor;

        if (isDarkTheme) {
            bgColor = Color.web("#2d2d2d");
            borderColor = isSelected ? Color.web("#3b82f6") : Color.web("#3f3f3f");
            textColor = Color.web("#f3f4f6");
        } else {
            bgColor = Color.web("#ffffff");
            borderColor = isSelected ? Color.web("#3b82f6") : Color.web("#e5e7eb");
            textColor = Color.web("#111827");
        }

        // Status color
        statusColor = Color.web(tool.getStatus().getColor());

        // Draw shadow for depth
        if (isSelected) {
            gc.setFill(Color.rgb(59, 130, 246, 0.2));
            gc.fillRoundRect(node.x + 2, node.y + 2, NODE_WIDTH, NODE_HEIGHT, 10, 10);
        }

        // Draw background
        gc.setFill(bgColor);
        gc.fillRoundRect(node.x, node.y, NODE_WIDTH, NODE_HEIGHT, 10, 10);

        // Draw border
        gc.setStroke(borderColor);
        gc.setLineWidth(isSelected ? 3 : 2);
        gc.strokeRoundRect(node.x, node.y, NODE_WIDTH, NODE_HEIGHT, 10, 10);

        // Draw status indicator (colored arc on left side)
        gc.setFill(statusColor);
        gc.fillRoundRect(node.x + 5, node.y + 5, 4, NODE_HEIGHT - 10, 2, 2);

        // Draw tool name
        gc.setFill(textColor);
        gc.setFont(Font.font("System", FontWeight.BOLD, 13));
        String toolName = tool.getName();
        if (toolName.length() > 20) {
            toolName = toolName.substring(0, 17) + "...";
        }
        gc.fillText(toolName, node.x + 15, node.y + 25);

        // Draw key information
        gc.setFont(Font.font("System", FontWeight.NORMAL, 10));
        String inputKey = (String) tool.getParameter("input_key");
        String outputKey = (String) tool.getParameter("output_key");

        if (isDarkTheme) {
            gc.setFill(Color.web("#9ca3af"));
        } else {
            gc.setFill(Color.web("#6b7280"));
        }

        int textY = (int) (node.y + 42);
        if (inputKey != null && !inputKey.isBlank()) {
            String inText = "in: " + (inputKey.length() > 15 ? inputKey.substring(0, 12) + "..." : inputKey);
            gc.fillText(inText, node.x + 15, textY);
            textY += 14;
        }

        if (outputKey != null && !outputKey.isBlank()) {
            String outText = "out: " + (outputKey.length() > 15 ? outputKey.substring(0, 12) + "..." : outputKey);
            gc.fillText(outText, node.x + 15, textY);
        }
    }

    /**
     * Draw an edge (connection between two nodes).
     */
    private void drawEdge(GraphicsContext gc, GraphEdge edge) {
        GraphNode sourceNode = nodePositions.get(edge.source);
        GraphNode targetNode = nodePositions.get(edge.target);

        if (sourceNode == null || targetNode == null) return;

        // Calculate connection points
        double x1 = sourceNode.x + NODE_WIDTH;  // Right edge of source
        double y1 = sourceNode.y + NODE_HEIGHT / 2;  // Middle of source
        double x2 = targetNode.x;  // Left edge of target
        double y2 = targetNode.y + NODE_HEIGHT / 2;  // Middle of target

        // Draw curved line (bezier curve)
        Color lineColor;
        if (isDarkTheme) {
            lineColor = Color.web("#3b82f6", 0.7);
        } else {
            lineColor = Color.web("#3b82f6", 0.8);
        }

        gc.setStroke(lineColor);
        gc.setLineWidth(2);

        // Calculate control points for curve
        double controlX1 = x1 + (x2 - x1) * 0.5;
        double controlY1 = y1;
        double controlX2 = x1 + (x2 - x1) * 0.5;
        double controlY2 = y2;

        // Draw cubic curve
        gc.beginPath();
        gc.moveTo(x1, y1);
        gc.bezierCurveTo(controlX1, controlY1, controlX2, controlY2, x2, y2);
        gc.stroke();

        // Draw arrowhead
        drawArrowHead(gc, controlX2, controlY2, x2, y2, lineColor);

        // Draw label (key name) on the edge
        if (edge.keyName != null && !edge.keyName.isBlank()) {
            double labelX = (x1 + x2) / 2;
            double labelY = (y1 + y2) / 2 - 5;

            // Draw background for label
            gc.setFont(Font.font("System", FontWeight.NORMAL, 10));
            Text text = new Text(edge.keyName);
            text.setFont(gc.getFont());
            double textWidth = text.getLayoutBounds().getWidth();

            if (isDarkTheme) {
                gc.setFill(Color.web("#1e1e1e", 0.9));
            } else {
                gc.setFill(Color.web("#ffffff", 0.9));
            }
            gc.fillRoundRect(labelX - textWidth / 2 - 4, labelY - 12, textWidth + 8, 16, 4, 4);

            // Draw label text
            if (isDarkTheme) {
                gc.setFill(Color.web("#e5e7eb"));
            } else {
                gc.setFill(Color.web("#1f2937"));
            }
            gc.fillText(edge.keyName, labelX - textWidth / 2, labelY);
        }
    }

    /**
     * Draw an arrowhead at the end of an edge.
     */
    private void drawArrowHead(GraphicsContext gc, double x1, double y1, double x2, double y2, Color color) {
        double arrowLength = 10;
        double arrowWidth = 6;

        // Calculate angle
        double angle = Math.atan2(y2 - y1, x2 - x1);

        // Calculate arrow points
        double x3 = x2 - arrowLength * Math.cos(angle - Math.PI / 6);
        double y3 = y2 - arrowLength * Math.sin(angle - Math.PI / 6);
        double x4 = x2 - arrowLength * Math.cos(angle + Math.PI / 6);
        double y4 = y2 - arrowLength * Math.sin(angle + Math.PI / 6);

        // Draw filled triangle
        gc.setFill(color);
        gc.fillPolygon(new double[]{x2, x3, x4}, new double[]{y2, y3, y4}, 3);
    }

    /**
     * Handle click on canvas to select nodes.
     */
    private void handleCanvasClick(double x, double y) {
        ToolInstance clickedTool = null;

        // Check if click is inside any node
        for (Map.Entry<ToolInstance, GraphNode> entry : nodePositions.entrySet()) {
            GraphNode node = entry.getValue();
            if (x >= node.x && x <= node.x + NODE_WIDTH &&
                y >= node.y && y <= node.y + NODE_HEIGHT) {
                clickedTool = entry.getKey();
                break;
            }
        }

        if (clickedTool != null) {
            setSelectedTool(clickedTool);
            // Notify callback listeners
            if (onToolSelected != null) {
                onToolSelected.accept(clickedTool);
            }
        }
    }

    private void showPlaceholder() {
        scrollPane.setContent(placeholderLabel);
    }

    private void hidePlaceholder() {
        scrollPane.setContent(canvasContainer);
    }

    /**
     * Add status listener to a tool instance.
     */
    private void addStatusListener(ToolInstance tool) {
        // Remove existing listener if present
        removeStatusListener(tool);

        // Create and store new listener
        javafx.beans.value.ChangeListener<ToolInstance.Status> listener =
            (obs, oldStatus, newStatus) -> Platform.runLater(this::redrawGraph);

        statusListeners.put(tool, listener);
        tool.statusProperty().addListener(listener);
    }

    /**
     * Remove status listener from a tool instance.
     */
    private void removeStatusListener(ToolInstance tool) {
        javafx.beans.value.ChangeListener<ToolInstance.Status> listener = statusListeners.remove(tool);
        if (listener != null) {
            tool.statusProperty().removeListener(listener);
        }
    }

    /**
     * Remove all status listeners.
     */
    private void removeAllStatusListeners() {
        for (Map.Entry<ToolInstance, javafx.beans.value.ChangeListener<ToolInstance.Status>> entry :
             statusListeners.entrySet()) {
            entry.getKey().statusProperty().removeListener(entry.getValue());
        }
        statusListeners.clear();
    }

    /**
     * Node in the graph.
     */
    private static class GraphNode {
        ToolInstance tool;
        double x, y;

        GraphNode(ToolInstance tool) {
            this.tool = tool;
        }
    }

    /**
     * Edge (connection) in the graph.
     */
    private static class GraphEdge {
        ToolInstance source;  // Tool that produces the key
        ToolInstance target;  // Tool that consumes the key
        String keyName;       // The key name connecting them

        GraphEdge(ToolInstance source, ToolInstance target, String keyName) {
            this.source = source;
            this.target = target;
            this.keyName = keyName;
        }
    }
}
