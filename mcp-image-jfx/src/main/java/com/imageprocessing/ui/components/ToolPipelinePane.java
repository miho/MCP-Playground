package com.imageprocessing.ui.components;

import com.imageprocessing.ui.model.ToolInstance;
import com.imageprocessing.ui.model.WorkflowModel;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.util.*;
import java.util.function.Consumer;

/**
 * Center panel displaying the tool execution pipeline.
 * Supports drag-and-drop reordering and execution control.
 */
public class ToolPipelinePane extends VBox {

    private final WorkflowModel workflowModel;
    private final ListView<ToolInstance> pipelineList;
    private final WorkflowGraphView graphView;
    private final StackPane contentPane;
    private Button viewToggleButton;
    private boolean showingGraphView = false;
    private Consumer<ToolInstance> onToolSelected;
    private Runnable onPipelineChanged;

    public ToolPipelinePane(WorkflowModel workflowModel) {
        this.workflowModel = workflowModel;

        // Header with controls
        HBox header = createHeader();

        // Pipeline list
        pipelineList = new ListView<>(workflowModel.getToolInstances());
        pipelineList.setCellFactory(lv -> new ToolCard());
        pipelineList.getStyleClass().add("pipeline-list");
        VBox.setVgrow(pipelineList, Priority.ALWAYS);

        // Selection handler
        pipelineList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && onToolSelected != null) {
                onToolSelected.accept(newVal);
            }
        });

        // Enable drag and drop
        setupDragAndDrop();

        // Placeholder
        Label placeholder = new Label("Drag tools here to build your pipeline");
        placeholder.getStyleClass().add("pipeline-placeholder");
        pipelineList.setPlaceholder(placeholder);

        // Graph view
        graphView = new WorkflowGraphView();
        graphView.setWorkflowModel(workflowModel);
        VBox.setVgrow(graphView, Priority.ALWAYS);

        // Wire up graph view selection to parameter editor
        graphView.setOnToolSelected(tool -> {
            if (onToolSelected != null) {
                onToolSelected.accept(tool);
            }
        });

        // Content pane (switches between list and graph)
        contentPane = new StackPane();
        contentPane.getChildren().add(pipelineList);
        VBox.setVgrow(contentPane, Priority.ALWAYS);

        // Layout
        this.setPadding(new Insets(10));
        this.setSpacing(10);
        this.getChildren().addAll(header, contentPane);
        this.getStyleClass().add("tool-pipeline-pane");
    }

    private HBox createHeader() {
        Label title = new Label("Execution Pipeline");
        title.getStyleClass().add("pipeline-title");

        // View toggle button
        viewToggleButton = new Button("Graph View");
        viewToggleButton.setOnAction(e -> toggleView());
        viewToggleButton.getStyleClass().add("graph-toggle-button");

        Button clearButton = new Button("Clear All");
        clearButton.setOnAction(e -> clearPipeline());
        clearButton.getStyleClass().add("secondary-button");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(10, title, spacer, viewToggleButton, clearButton);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    /**
     * Toggle between list and graph view.
     */
    private void toggleView() {
        if (showingGraphView) {
            // Switch to list view
            contentPane.getChildren().clear();
            contentPane.getChildren().add(pipelineList);
            viewToggleButton.setText("Graph View");
            showingGraphView = false;
        } else {
            // Switch to graph view
            contentPane.getChildren().clear();
            contentPane.getChildren().add(graphView);
            viewToggleButton.setText("List View");
            showingGraphView = true;

            // Update selected tool in graph
            ToolInstance selected = pipelineList.getSelectionModel().getSelectedItem();
            graphView.setSelectedTool(selected);
        }
    }

    /**
     * Update theme for graph view.
     */
    public void setDarkTheme(boolean dark) {
        graphView.setDarkTheme(dark);
    }

    private void setupDragAndDrop() {
        // Accept drops from tool collection
        pipelineList.setOnDragOver(event -> {
            if (event.getGestureSource() != pipelineList &&
                event.getDragboard().hasString()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });

        pipelineList.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasString()) {
                String toolNamesStr = db.getString();
                // Handle multiple tools separated by "|"
                String[] toolNames = toolNamesStr.split("\\|");
                for (String toolName : toolNames) {
                    if (!toolName.trim().isEmpty()) {
                        addToolByName(toolName.trim());
                    }
                }
                success = true;
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    private void addToolByName(String toolName) {
        com.imageprocessing.ui.model.ToolRegistry.getToolByName(toolName)
            .ifPresent(metadata -> {
                workflowModel.addTool(metadata);
                if (onPipelineChanged != null) {
                    onPipelineChanged.run();
                }
            });
    }

    private void clearPipeline() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Clear Pipeline");
        alert.setHeaderText("Clear all tools from pipeline?");
        alert.setContentText("This action cannot be undone.");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                workflowModel.clear();
                if (onPipelineChanged != null) {
                    onPipelineChanged.run();
                }
            }
        });
    }

    public void setOnToolSelected(Consumer<ToolInstance> handler) {
        this.onToolSelected = handler;
    }

    public void setOnPipelineChanged(Runnable handler) {
        this.onPipelineChanged = handler;
    }

    public ToolInstance getSelectedTool() {
        return pipelineList.getSelectionModel().getSelectedItem();
    }

    /**
     * Custom list cell displaying tool as a card with status indicator.
     */
    private class ToolCard extends ListCell<ToolInstance> {

        private final VBox container;
        private final HBox headerBox;
        private final Label nameLabel;
        private final Circle statusIndicator;
        private final Button removeButton;
        private final VBox paramContainer;
        private final Label errorLabel;

        public ToolCard() {
            // Status indicator
            statusIndicator = new Circle(6);
            statusIndicator.getStyleClass().add("status-indicator");

            // Tool name
            nameLabel = new Label();
            nameLabel.getStyleClass().add("tool-name");

            // Remove button
            removeButton = new Button("X");
            removeButton.getStyleClass().add("remove-button");
            removeButton.setOnAction(e -> {
                ToolInstance tool = getItem();
                if (tool != null) {
                    workflowModel.removeTool(tool);
                    if (onPipelineChanged != null) {
                        onPipelineChanged.run();
                    }
                }
            });

            // Spacer to push remove button to the right
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            // Header
            headerBox = new HBox(8, statusIndicator, nameLabel, spacer, removeButton);
            headerBox.setAlignment(Pos.CENTER_LEFT);

            // Parameter container (will hold individual parameter labels)
            paramContainer = new VBox(2);
            paramContainer.getStyleClass().add("param-container");

            // Error message label
            errorLabel = new Label();
            errorLabel.getStyleClass().add("error-message");
            errorLabel.setWrapText(true);
            errorLabel.setManaged(false);
            errorLabel.setVisible(false);

            // Container
            container = new VBox(5, headerBox, paramContainer, errorLabel);
            container.setPadding(new Insets(8));
            container.getStyleClass().add("tool-card");

            // Enable drag for reordering
            setupCardDragAndDrop();
        }

        private void setupCardDragAndDrop() {
            setOnDragDetected(event -> {
                if (getItem() == null) return;

                Dragboard db = startDragAndDrop(TransferMode.MOVE);
                ClipboardContent content = new ClipboardContent();
                // Use special prefix to identify reordering drags
                content.putString("REORDER:" + getIndex());
                db.setContent(content);
                event.consume();
            });

            setOnDragOver(event -> {
                if (event.getDragboard().hasString()) {
                    String data = event.getDragboard().getString();
                    // Only accept reordering drags (not drags from tool collection)
                    if (data.startsWith("REORDER:")) {
                        event.acceptTransferModes(TransferMode.MOVE);
                    }
                }
                event.consume();
            });

            setOnDragDropped(event -> {
                Dragboard db = event.getDragboard();
                boolean success = false;
                if (db.hasString()) {
                    String data = db.getString();
                    // Only handle reordering drags
                    if (data.startsWith("REORDER:")) {
                        int draggedIdx = Integer.parseInt(data.substring("REORDER:".length()));
                        int thisIdx = getIndex();
                        workflowModel.moveTool(draggedIdx, thisIdx);
                        success = true;
                        if (onPipelineChanged != null) {
                            onPipelineChanged.run();
                        }
                    }
                }
                event.setDropCompleted(success);
                event.consume();
            });
        }

        @Override
        protected void updateItem(ToolInstance tool, boolean empty) {
            super.updateItem(tool, empty);

            if (empty || tool == null) {
                setGraphic(null);
            } else {
                nameLabel.setText(tool.getName());

                // Build readable parameter display
                buildParameterDisplay(tool);

                // Update status indicator and error message
                updateStatusIndicator(tool.getStatus());
                updateErrorMessage(tool);

                // Listen to status changes
                tool.statusProperty().addListener((obs, oldVal, newVal) -> {
                    updateStatusIndicator(newVal);
                    updateErrorMessage(tool);
                });

                // Listen to error message changes
                tool.errorMessageProperty().addListener((obs, oldVal, newVal) ->
                    updateErrorMessage(tool));

                setGraphic(container);
            }
        }

        /**
         * Build a clean, readable display of tool parameters.
         */
        private void buildParameterDisplay(ToolInstance tool) {
            paramContainer.getChildren().clear();

            Map<String, Object> params = tool.getParameterValues();
            if (params.isEmpty()) {
                Label noParams = new Label("No parameters set");
                noParams.getStyleClass().add("param-text-muted");
                paramContainer.getChildren().add(noParams);
                return;
            }

            // Group related parameters for better display
            List<String> displayLines = new ArrayList<>();

            // Show input/output keys first (most important)
            String inputKey = getParamString(params, "input_key");
            String outputKey = getParamString(params, "output_key");

            if (inputKey != null) {
                displayLines.add("input_key: " + inputKey);
            }
            if (outputKey != null) {
                displayLines.add("output_key: " + outputKey);
            }

            // Show other parameters (excluding input_key and output_key)
            List<String> otherParams = new ArrayList<>();
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                // Skip if null, empty, or already shown
                if (value == null || value.toString().isEmpty()) continue;
                if (key.equals("input_key") || key.equals("output_key")) continue;

                // Format the parameter nicely
                String formattedValue = formatParameterValue(value);
                otherParams.add(key + ": " + formattedValue);
            }

            // Show other params grouped if there are multiple
            if (!otherParams.isEmpty()) {
                if (otherParams.size() <= 3) {
                    // Show all if few
                    displayLines.addAll(otherParams);
                } else {
                    // Show first 2 and count
                    displayLines.addAll(otherParams.subList(0, 2));
                    displayLines.add("... +" + (otherParams.size() - 2) + " more");
                }
            }

            // Create labels for each line
            for (String line : displayLines) {
                Label paramLabel = new Label(line);
                paramLabel.getStyleClass().add("param-text");
                paramContainer.getChildren().add(paramLabel);
            }

            // If no parameters were shown
            if (displayLines.isEmpty()) {
                Label noParams = new Label("No parameters set");
                noParams.getStyleClass().add("param-text-muted");
                paramContainer.getChildren().add(noParams);
            }
        }

        /**
         * Get parameter as string, or null if empty.
         */
        private String getParamString(Map<String, Object> params, String key) {
            Object value = params.get(key);
            if (value == null || value.toString().isEmpty()) {
                return null;
            }
            return value.toString();
        }

        /**
         * Format parameter value for display (truncate long strings).
         */
        private String formatParameterValue(Object value) {
            String str = value.toString();

            // Truncate file paths to just filename
            if (str.contains("/") || str.contains("\\")) {
                int lastSlash = Math.max(str.lastIndexOf('/'), str.lastIndexOf('\\'));
                if (lastSlash >= 0 && lastSlash < str.length() - 1) {
                    return "..." + str.substring(lastSlash);
                }
            }

            // Truncate very long values
            if (str.length() > 40) {
                return str.substring(0, 37) + "...";
            }

            return str;
        }

        private void updateStatusIndicator(ToolInstance.Status status) {
            statusIndicator.setFill(Color.web(status.getColor()));
            statusIndicator.setStroke(Color.web(status.getColor()).darker());
        }

        private void updateErrorMessage(ToolInstance tool) {
            if (tool.getStatus() == ToolInstance.Status.ERROR &&
                tool.getErrorMessage() != null &&
                !tool.getErrorMessage().isEmpty()) {
                errorLabel.setText(tool.getErrorMessage());
                errorLabel.setVisible(true);
                errorLabel.setManaged(true);
            } else {
                errorLabel.setText("");
                errorLabel.setVisible(false);
                errorLabel.setManaged(false);
            }
        }
    }
}
