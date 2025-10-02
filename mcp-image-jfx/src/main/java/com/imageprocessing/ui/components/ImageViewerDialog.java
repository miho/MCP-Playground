package com.imageprocessing.ui.components;

import com.imageprocessing.server.IntermediateResultCache;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Priority;
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
 * Dialog for viewing full-size images with metadata information.
 * Displays images from cache or file system with scrollable viewing area.
 */
public class ImageViewerDialog extends Dialog<Void> {

    private final ImageView imageView;
    private final Label statusLabel;

    public ImageViewerDialog(String key, String imagePath, IntermediateResultCache cache) {
        setTitle("Image Viewer - " + key);
        setResizable(true);

        // Create image view
        imageView = new ImageView();
        imageView.setPreserveRatio(true);

        // Status label for loading feedback
        statusLabel = new Label("Loading image...");
        statusLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #7f8c8d;");

        // Stack pane to hold either image or status
        StackPane contentPane = new StackPane(statusLabel);
        contentPane.setStyle("-fx-background-color: #2c3e50;");

        // Scroll pane for large images
        ScrollPane scrollPane = new ScrollPane(contentPane);
        scrollPane.setPrefSize(800, 600);
        scrollPane.setStyle("-fx-background: #2c3e50;");
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);

        // Info panel
        VBox infoPanel = new VBox(5);
        infoPanel.setPadding(new Insets(10));
        infoPanel.setStyle("-fx-background-color: #ecf0f1;");

        Label keyLabel = new Label("Key: " + key);
        keyLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");

        Label pathLabel = new Label("Path: " + (imagePath != null ? imagePath : "N/A"));
        pathLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #7f8c8d;");
        pathLabel.setWrapText(true);

        infoPanel.getChildren().addAll(keyLabel, pathLabel);

        // Layout
        VBox content = new VBox(scrollPane, infoPanel);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        getDialogPane().setContent(content);
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        // Set min size
        getDialogPane().setMinSize(600, 500);

        // Load image asynchronously
        loadImageAsync(contentPane, imagePath, cache, key);
    }

    /**
     * Load full-size image asynchronously from cache or file.
     */
    private void loadImageAsync(StackPane contentPane, String imagePath,
                                IntermediateResultCache cache, String key) {
        CompletableFuture.runAsync(() -> {
            try {
                Mat mat = cache != null ? cache.get(key) : null;
                BufferedImage bufferedImage;

                if (mat != null && !mat.empty()) {
                    bufferedImage = matToBufferedImage(mat);
                } else if (imagePath != null && !imagePath.isEmpty()) {
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

                Platform.runLater(() -> {
                    Image fxImage = SwingFXUtils.toFXImage(bufferedImage, null);
                    imageView.setImage(fxImage);
                    contentPane.getChildren().clear();
                    contentPane.getChildren().add(imageView);
                    statusLabel.setText(String.format("Image loaded: %d x %d px",
                        bufferedImage.getWidth(), bufferedImage.getHeight()));
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    Label errorLabel = new Label("Failed to load image:\n" + e.getMessage());
                    errorLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 14px; -fx-padding: 20;");
                    errorLabel.setWrapText(true);
                    contentPane.getChildren().clear();
                    contentPane.getChildren().add(errorLabel);
                });
                System.err.println("Failed to load image for " + key + ": " + e.getMessage());
                e.printStackTrace();
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
}
