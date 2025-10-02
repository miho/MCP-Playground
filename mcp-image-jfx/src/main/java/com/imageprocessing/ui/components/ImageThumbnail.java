package com.imageprocessing.ui.components;

import com.imageprocessing.server.IntermediateResultCache;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.opencv.core.Mat;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * Thumbnail view for displaying image previews with metadata.
 * Supports async loading, hover effects, and double-click to zoom.
 */
public class ImageThumbnail extends VBox {

    private static final int THUMBNAIL_SIZE = 150;

    private final ImageView imageView;
    private final Label nameLabel;
    private final Label infoLabel;
    private Runnable onDoubleClick;

    public ImageThumbnail(String key, String imagePath, IntermediateResultCache cache) {
        // Image view
        imageView = new ImageView();
        imageView.setFitWidth(THUMBNAIL_SIZE);
        imageView.setFitHeight(THUMBNAIL_SIZE);
        imageView.setPreserveRatio(true);
        imageView.getStyleClass().add("thumbnail-image");

        // Border around image
        StackPane imageContainer = new StackPane(imageView);
        imageContainer.getStyleClass().add("thumbnail-container");
        imageContainer.setMaxSize(THUMBNAIL_SIZE, THUMBNAIL_SIZE);
        imageContainer.setMinSize(THUMBNAIL_SIZE, THUMBNAIL_SIZE);

        // Labels
        nameLabel = new Label(key);
        nameLabel.getStyleClass().add("thumbnail-name");
        nameLabel.setMaxWidth(THUMBNAIL_SIZE);
        nameLabel.setWrapText(false);
        nameLabel.setStyle("-fx-text-overflow: ellipsis;");

        infoLabel = new Label("Loading...");
        infoLabel.getStyleClass().add("thumbnail-info");

        // Layout
        this.getChildren().addAll(imageContainer, nameLabel, infoLabel);
        this.setAlignment(Pos.CENTER);
        this.setSpacing(5);
        this.getStyleClass().add("image-thumbnail");

        // Load image asynchronously
        loadThumbnailAsync(imagePath, cache, key);

        // Double-click handler
        imageContainer.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && onDoubleClick != null) {
                onDoubleClick.run();
            }
        });

        // Hover effect
        imageContainer.setOnMouseEntered(e ->
            imageContainer.setStyle("-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.4), 8, 0, 0, 2);")
        );
        imageContainer.setOnMouseExited(e ->
            imageContainer.setStyle("")
        );
    }

    /**
     * Load thumbnail image asynchronously from cache or file.
     */
    private void loadThumbnailAsync(String imagePath, IntermediateResultCache cache, String key) {
        CompletableFuture.runAsync(() -> {
            try {
                // Try loading from cache first
                Mat mat = cache != null ? cache.get(key) : null;

                BufferedImage bufferedImage;
                if (mat != null && !mat.empty()) {
                    // Convert Mat to BufferedImage
                    bufferedImage = matToBufferedImage(mat);
                } else if (imagePath != null && !imagePath.isEmpty()) {
                    // Load from file
                    File file = new File(imagePath);
                    if (file.exists()) {
                        bufferedImage = ImageIO.read(file);
                    } else {
                        throw new IOException("File not found: " + imagePath);
                    }
                } else {
                    throw new IOException("No image source available");
                }

                if (bufferedImage == null) {
                    throw new IOException("Failed to load image");
                }

                // Update UI on JavaFX thread
                final int width = bufferedImage.getWidth();
                final int height = bufferedImage.getHeight();

                Platform.runLater(() -> {
                    Image fxImage = SwingFXUtils.toFXImage(bufferedImage, null);
                    imageView.setImage(fxImage);
                    infoLabel.setText(String.format("%d x %d px", width, height));
                    infoLabel.setStyle("");
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    infoLabel.setText("Error loading");
                    infoLabel.setStyle("-fx-text-fill: #e74c3c;");
                    System.err.println("Failed to load thumbnail for " + key + ": " + e.getMessage());
                });
            }
        });
    }

    /**
     * Convert OpenCV Mat to BufferedImage.
     */
    private BufferedImage matToBufferedImage(Mat mat) {
        int type;
        if (mat.channels() == 1) {
            type = BufferedImage.TYPE_BYTE_GRAY;
        } else if (mat.channels() == 3) {
            type = BufferedImage.TYPE_3BYTE_BGR;
        } else if (mat.channels() == 4) {
            type = BufferedImage.TYPE_4BYTE_ABGR;
        } else {
            throw new IllegalArgumentException("Unsupported number of channels: " + mat.channels());
        }

        BufferedImage image = new BufferedImage(mat.width(), mat.height(), type);
        byte[] data = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        mat.get(0, 0, data);

        return image;
    }

    /**
     * Set the double-click handler for this thumbnail.
     * @param handler The action to perform on double-click
     */
    public void setOnDoubleClick(Runnable handler) {
        this.onDoubleClick = handler;
    }
}
