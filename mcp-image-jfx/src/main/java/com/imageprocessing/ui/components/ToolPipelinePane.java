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

import java.util.function.Consumer;

/**
 * Center panel displaying the tool execution pipeline.
 * Supports drag-and-drop reordering and execution control.
 */
public class ToolPipelinePane extends VBox {

    private final WorkflowModel workflowModel;
    private final ListView<ToolInstance> pipelineList;
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

        // Layout
        this.setPadding(new Insets(10));
        this.setSpacing(10);
        this.getChildren().addAll(header, pipelineList);
        this.getStyleClass().add("tool-pipeline-pane");
    }

    private HBox createHeader() {
        Label title = new Label("Execution Pipeline");
        title.getStyleClass().add("pipeline-title");

        Button clearButton = new Button("Clear All");
        clearButton.setOnAction(e -> clearPipeline());
        clearButton.getStyleClass().add("secondary-button");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(10, title, spacer, clearButton);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
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
        private final Label paramSummary;
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

            // Parameter summary
            paramSummary = new Label();
            paramSummary.getStyleClass().add("param-summary");
            paramSummary.setWrapText(true);

            // Error message label
            errorLabel = new Label();
            errorLabel.getStyleClass().add("error-message");
            errorLabel.setWrapText(true);
            errorLabel.setManaged(false);
            errorLabel.setVisible(false);

            // Container
            container = new VBox(5, headerBox, paramSummary, errorLabel);
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
                paramSummary.setText(tool.getParameterSummary());

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
