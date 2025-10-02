package com.imageprocessing.ui.components;

import com.imageprocessing.server.IntermediateResultCache;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Panel for displaying image processing results as thumbnail previews.
 * Provides a scrollable grid of thumbnails with zoom functionality.
 */
public class ImagePreviewPanel extends VBox {

    private final ScrollPane scrollPane;
    private final FlowPane thumbnailGrid;
    private final Label titleLabel;

    // Cache reference for loading images
    private IntermediateResultCache cache;

    public ImagePreviewPanel() {
        // Title
        titleLabel = new Label("Image Results");
        titleLabel.getStyleClass().add("preview-title");

        // Thumbnail grid
        thumbnailGrid = new FlowPane();
        thumbnailGrid.setHgap(10);
        thumbnailGrid.setVgap(10);
        thumbnailGrid.setPadding(new Insets(10));
        thumbnailGrid.getStyleClass().add("thumbnail-grid");

        // Scroll pane
        scrollPane = new ScrollPane(thumbnailGrid);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("preview-scroll");

        // Layout
        this.getChildren().addAll(titleLabel, scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        this.getStyleClass().add("image-preview-panel");
    }

    /**
     * Set the cache for loading images.
     * @param cache The IntermediateResultCache instance
     */
    public void setCache(IntermediateResultCache cache) {
        this.cache = cache;
    }

    /**
     * Add an image result to the preview panel.
     * @param key The cache key or name for the image
     * @param imagePath Optional file path to the image (can be null if loading from cache)
     */
    public void addImageResult(String key, String imagePath) {
        // Create thumbnail card
        ImageThumbnail thumbnail = new ImageThumbnail(key, imagePath, cache);
        thumbnail.setOnDoubleClick(() -> showFullSizeImage(key, imagePath));
        thumbnailGrid.getChildren().add(thumbnail);
    }

    /**
     * Clear all thumbnails from the preview panel.
     */
    public void clear() {
        thumbnailGrid.getChildren().clear();
    }

    /**
     * Update the title label text.
     * @param title The new title text
     */
    public void setTitle(String title) {
        titleLabel.setText(title);
    }

    /**
     * Get the number of thumbnails currently displayed.
     * @return The thumbnail count
     */
    public int getThumbnailCount() {
        return thumbnailGrid.getChildren().size();
    }

    /**
     * Show full-size image viewer dialog.
     */
    private void showFullSizeImage(String key, String imagePath) {
        ImageViewerDialog dialog = new ImageViewerDialog(key, imagePath, cache);
        dialog.showAndWait();
    }
}
