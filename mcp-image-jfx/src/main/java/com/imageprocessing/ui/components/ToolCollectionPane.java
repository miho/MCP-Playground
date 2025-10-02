package com.imageprocessing.ui.components;

import com.imageprocessing.ui.model.ToolMetadata;
import com.imageprocessing.ui.model.ToolRegistry;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;

import java.util.List;

/**
 * Left panel displaying searchable collection of available tools.
 * Supports drag-and-drop to pipeline.
 */
public class ToolCollectionPane extends VBox {

    private final TextField searchField;
    private final TreeView<ToolItem> toolTree;
    private final ObservableList<ToolMetadata> allTools;

    public ToolCollectionPane() {
        this.allTools = FXCollections.observableArrayList(ToolRegistry.getAllTools());

        // Search field
        searchField = new TextField();
        searchField.setPromptText("Search tools...");
        searchField.getStyleClass().add("search-field");
        searchField.textProperty().addListener((obs, oldVal, newVal) -> filterTools(newVal));

        // Tool tree
        toolTree = new TreeView<>();
        toolTree.setShowRoot(false);
        toolTree.getStyleClass().add("tool-tree");
        VBox.setVgrow(toolTree, Priority.ALWAYS);

        // Build tree
        buildToolTree();

        // Enable drag and drop
        setupDragAndDrop();

        // Layout
        this.setPadding(new Insets(10));
        this.setSpacing(10);
        this.getChildren().addAll(
            new Label("Tool Collection"),
            searchField,
            toolTree
        );

        this.getStyleClass().add("tool-collection-pane");
    }

    private void buildToolTree() {
        TreeItem<ToolItem> root = new TreeItem<>();

        // Group by category
        for (ToolMetadata.Category category : ToolMetadata.Category.values()) {
            List<ToolMetadata> categoryTools = ToolRegistry.getToolsByCategory(category);
            if (!categoryTools.isEmpty()) {
                TreeItem<ToolItem> categoryItem = new TreeItem<>(new ToolItem(category));
                categoryItem.setExpanded(true);

                for (ToolMetadata tool : categoryTools) {
                    TreeItem<ToolItem> toolItem = new TreeItem<>(new ToolItem(tool));
                    categoryItem.getChildren().add(toolItem);
                }

                root.getChildren().add(categoryItem);
            }
        }

        toolTree.setRoot(root);

        // Enable multiple selection
        toolTree.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // Custom cell factory for better display
        toolTree.setCellFactory(tv -> new ToolTreeCell());
    }

    private void filterTools(String query) {
        if (query == null || query.trim().isEmpty()) {
            buildToolTree();
            return;
        }

        TreeItem<ToolItem> root = new TreeItem<>();
        List<ToolMetadata> results = ToolRegistry.searchTools(query);

        if (!results.isEmpty()) {
            TreeItem<ToolItem> searchResults = new TreeItem<>(new ToolItem("Search Results"));
            searchResults.setExpanded(true);

            for (ToolMetadata tool : results) {
                TreeItem<ToolItem> toolItem = new TreeItem<>(new ToolItem(tool));
                searchResults.getChildren().add(toolItem);
            }

            root.getChildren().add(searchResults);
        }

        toolTree.setRoot(root);
    }

    private void setupDragAndDrop() {
        toolTree.setOnDragDetected(event -> {
            List<TreeItem<ToolItem>> selectedItems = toolTree.getSelectionModel().getSelectedItems();
            if (selectedItems != null && !selectedItems.isEmpty()) {
                // Filter out category items and get only tool items
                List<String> toolNames = selectedItems.stream()
                    .filter(item -> item.getValue().getTool() != null)
                    .map(item -> item.getValue().getTool().getName())
                    .collect(java.util.stream.Collectors.toList());

                if (!toolNames.isEmpty()) {
                    Dragboard db = toolTree.startDragAndDrop(TransferMode.COPY);
                    ClipboardContent content = new ClipboardContent();
                    // Join multiple tool names with a delimiter
                    content.putString(String.join("|", toolNames));
                    db.setContent(content);
                    event.consume();
                }
            }
        });
    }

    /**
     * Item wrapper for tree view (can be category or tool).
     */
    private static class ToolItem {
        private final String label;
        private final ToolMetadata tool;
        private final ToolMetadata.Category category;

        public ToolItem(ToolMetadata tool) {
            this.tool = tool;
            this.category = null;
            this.label = tool.getName();
        }

        public ToolItem(ToolMetadata.Category category) {
            this.tool = null;
            this.category = category;
            this.label = category.getDisplayName();
        }

        public ToolItem(String label) {
            this.tool = null;
            this.category = null;
            this.label = label;
        }

        public String getLabel() { return label; }
        public ToolMetadata getTool() { return tool; }
        public ToolMetadata.Category getCategory() { return category; }

        @Override
        public String toString() { return label; }
    }

    /**
     * Custom tree cell with tooltip and styling.
     */
    private static class ToolTreeCell extends TreeCell<ToolItem> {
        @Override
        protected void updateItem(ToolItem item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
                setTooltip(null);
                setGraphic(null);
                getStyleClass().removeAll("tool-cell", "category-cell");
            } else {
                setText(item.getLabel());

                if (item.getTool() != null) {
                    // Tool cell
                    setTooltip(new Tooltip(item.getTool().getDescription()));
                    getStyleClass().removeAll("category-cell");
                    if (!getStyleClass().contains("tool-cell")) {
                        getStyleClass().add("tool-cell");
                    }
                } else if (item.getCategory() != null) {
                    // Category cell
                    setTooltip(new Tooltip(item.getCategory().getDescription()));
                    getStyleClass().removeAll("tool-cell");
                    if (!getStyleClass().contains("category-cell")) {
                        getStyleClass().add("category-cell");
                    }
                } else {
                    setTooltip(null);
                    getStyleClass().removeAll("tool-cell", "category-cell");
                }
            }
        }
    }
}
