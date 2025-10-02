package com.imageprocessing.ui;

import com.imageprocessing.server.McpConfig;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.control.SplitPane;
import javafx.stage.Stage;

/**
 * Main JavaFX application for the Image Processing UI.
 * Provides a visual interface for building and executing image processing pipelines.
 */
public class ImageProcessingApp extends Application {

    private static final String APP_TITLE = "Image Processing Pipeline Builder";
    private static final double DEFAULT_WIDTH = 1200;
    private static final double DEFAULT_HEIGHT = 800;

    private MainController controller;
    private Scene scene;
    private boolean isDarkTheme = true; // Default to dark theme
    private static McpCliOptions cliOptions; // Parsed CLI options

    @Override
    public void start(Stage primaryStage) {
        try {
            // Build MCP config from CLI options or use default
            McpConfig mcpConfig;
            boolean mcpEnabled = true;

            if (cliOptions != null) {
                mcpEnabled = cliOptions.isMcpEnabled();
                if (mcpEnabled) {
                    mcpConfig = cliOptions.buildMcpConfig();
                    System.out.println("MCP Configuration from CLI: " + mcpConfig);
                } else {
                    mcpConfig = null; // MCP disabled
                    System.out.println("MCP server disabled via CLI");
                }
            } else {
                // Default HTTP configuration
                mcpConfig = McpConfig.defaultHttp();
                System.out.println("Using default MCP configuration: " + mcpConfig);
            }

            // Initialize controller with MCP config
            controller = new MainController(mcpConfig, mcpEnabled);

            // Build main layout
            BorderPane root = buildLayout();

            // Create scene with stylesheet
            scene = new Scene(root, DEFAULT_WIDTH, DEFAULT_HEIGHT);
            scene.getStylesheets().add(
                getClass().getResource("/css/application.css").toExternalForm()
            );

            // Apply default dark theme
            applyTheme();

            // Configure stage
            primaryStage.setTitle(APP_TITLE);
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(800);
            primaryStage.setMinHeight(600);

            // Setup shutdown hook
            primaryStage.setOnCloseRequest(e -> {
                System.out.println("Application closing, cleaning up...");
                if (controller != null) {
                    controller.shutdown();
                }
            });

            // Show stage
            primaryStage.show();

            // Setup theme toggle
            controller.getControlBar().setOnThemeToggleAction(this::toggleTheme);
            controller.getControlBar().updateThemeIcon(isDarkTheme);

            // Initialize embedded MCP server after UI is ready (if enabled)
            if (mcpEnabled && mcpConfig != null) {
                controller.initializeEmbeddedServer();
            }

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to start application: " + e.getMessage());
        }
    }

    /**
     * Build the main application layout.
     */
    private BorderPane buildLayout() {
        BorderPane root = new BorderPane();

        // Top: Control Bar
        root.setTop(controller.getControlBar());

        // Center: Three-panel layout (Tool Collection | Pipeline | Parameter Editor)
        SplitPane centerPane = buildCenterPane();

        // Image preview panel (bottom section)
        VBox imagePreview = controller.getImagePreviewPanel();
        imagePreview.setMaxHeight(200);
        imagePreview.setMinHeight(150);

        // Combine center pane and image preview in vertical split
        SplitPane mainSplitPane = new SplitPane();
        mainSplitPane.setOrientation(javafx.geometry.Orientation.VERTICAL);
        mainSplitPane.getItems().addAll(centerPane, imagePreview);
        mainSplitPane.setDividerPositions(0.75);

        root.setCenter(mainSplitPane);

        // Bottom: Status Bar
        root.setBottom(controller.getStatusBar());

        return root;
    }

    /**
     * Build the three-panel center layout.
     */
    private SplitPane buildCenterPane() {
        // Left panel: Tool Collection
        VBox leftPanel = new VBox(controller.getToolCollectionPane());
        VBox.setVgrow(controller.getToolCollectionPane(), Priority.ALWAYS);

        // Center panel: Tool Pipeline
        VBox centerPanel = new VBox(controller.getToolPipelinePane());
        VBox.setVgrow(controller.getToolPipelinePane(), Priority.ALWAYS);

        // Right panel: Parameter Editor
        VBox rightPanel = new VBox(controller.getParameterEditorPane());
        VBox.setVgrow(controller.getParameterEditorPane(), Priority.ALWAYS);

        // Split pane with three sections
        SplitPane splitPane = new SplitPane();
        splitPane.getItems().addAll(leftPanel, centerPanel, rightPanel);

        // Set divider positions (20% | 50% | 30%)
        splitPane.setDividerPositions(0.2, 0.7);

        return splitPane;
    }

    /**
     * Toggle between dark and light themes.
     */
    private void toggleTheme() {
        isDarkTheme = !isDarkTheme;
        applyTheme();
        controller.getControlBar().updateThemeIcon(isDarkTheme);
    }

    /**
     * Apply the current theme to the scene.
     */
    private void applyTheme() {
        if (scene != null && scene.getRoot() != null) {
            if (isDarkTheme) {
                scene.getRoot().getStyleClass().remove("light-theme");
            } else {
                if (!scene.getRoot().getStyleClass().contains("light-theme")) {
                    scene.getRoot().getStyleClass().add("light-theme");
                }
            }
        }
    }

    @Override
    public void stop() {
        // Cleanup resources
        System.out.println("Application shutting down...");
        if (controller != null) {
            controller.shutdown();
        }
    }

    public static void main(String[] args) {
        // Parse CLI options before launching JavaFX
        cliOptions = McpCliOptions.parse(args);

        // If parsing failed or help was requested, exit
        if (cliOptions == null && args.length > 0) {
            // Help or version was shown, or parsing failed
            return;
        }

        // Launch JavaFX application
        launch(args);
    }
}
