package com.openrewrite.ui;

import com.openrewrite.server.McpConfig;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenRewriteApp extends Application {
    private static final Logger logger = LoggerFactory.getLogger(OpenRewriteApp.class);
    private static final String APP_TITLE = "OpenRewrite Code Transformer";
    private static final double DEFAULT_WIDTH = 1400;
    private static final double DEFAULT_HEIGHT = 900;

    private MainController controller;
    private Scene scene;
    private boolean isDarkTheme = true;
    private static McpCliOptions cliOptions;

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
                    logger.info("MCP Configuration from CLI: {}", mcpConfig);
                } else {
                    mcpConfig = null; // MCP disabled
                    logger.info("MCP server disabled via CLI");
                }
            } else {
                // Default HTTP configuration
                mcpConfig = McpConfig.defaultHttp();
                logger.info("Using default MCP configuration: {}", mcpConfig);
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
            primaryStage.setMinWidth(1000);
            primaryStage.setMinHeight(700);

            // Setup shutdown hook
            primaryStage.setOnCloseRequest(e -> {
                logger.info("Application closing, cleaning up...");
                if (controller != null) {
                    controller.shutdown();
                }
            });

            // Show stage
            primaryStage.show();

            // Initialize MCP connection after UI is ready (if enabled)
            if (mcpEnabled && mcpConfig != null) {
                controller.initializeMcpConnection();
            }

        } catch (Exception e) {
            logger.error("Failed to start application", e);
            showErrorDialog("Startup Error", "Failed to start application: " + e.getMessage());
        }
    }

    private BorderPane buildLayout() {
        BorderPane root = new BorderPane();

        // Top: Menu bar and toolbar
        VBox topContainer = new VBox();
        topContainer.getChildren().addAll(
            createMenuBar(),
            controller.getToolBar()
        );
        root.setTop(topContainer);

        // Center: Main content area
        SplitPane mainContent = createMainContent();
        root.setCenter(mainContent);

        // Bottom: Status bar
        root.setBottom(controller.getStatusBar());

        return root;
    }

    private MenuBar createMenuBar() {
        MenuBar menuBar = new MenuBar();

        // File menu
        Menu fileMenu = new Menu("File");
        MenuItem openItem = new MenuItem("Open File...");
        MenuItem saveItem = new MenuItem("Save Transformed Code...");
        MenuItem exitItem = new MenuItem("Exit");

        openItem.setOnAction(e -> controller.openFile());
        saveItem.setOnAction(e -> controller.saveTransformedCode());
        exitItem.setOnAction(e -> System.exit(0));

        fileMenu.getItems().addAll(openItem, saveItem, new SeparatorMenuItem(), exitItem);

        // Edit menu
        Menu editMenu = new Menu("Edit");
        MenuItem undoItem = new MenuItem("Undo");
        MenuItem redoItem = new MenuItem("Redo");
        MenuItem copyItem = new MenuItem("Copy");
        MenuItem pasteItem = new MenuItem("Paste");

        editMenu.getItems().addAll(undoItem, redoItem, new SeparatorMenuItem(), copyItem, pasteItem);

        // View menu
        Menu viewMenu = new Menu("View");
        CheckMenuItem darkThemeItem = new CheckMenuItem("Dark Theme");
        darkThemeItem.setSelected(isDarkTheme);
        darkThemeItem.setOnAction(e -> toggleTheme());

        MenuItem refreshRecipesItem = new MenuItem("Refresh Recipes");
        refreshRecipesItem.setOnAction(e -> controller.refreshRecipes());

        viewMenu.getItems().addAll(darkThemeItem, new SeparatorMenuItem(), refreshRecipesItem);

        // Tools menu
        Menu toolsMenu = new Menu("Tools");
        MenuItem analyzeCodeItem = new MenuItem("Analyze Code");
        MenuItem mcpSettingsItem = new MenuItem("MCP Settings...");

        analyzeCodeItem.setOnAction(e -> controller.analyzeCurrentCode());
        mcpSettingsItem.setOnAction(e -> controller.showMcpSettings());

        toolsMenu.getItems().addAll(analyzeCodeItem, new SeparatorMenuItem(), mcpSettingsItem);

        // Help menu
        Menu helpMenu = new Menu("Help");
        MenuItem aboutItem = new MenuItem("About");
        MenuItem documentationItem = new MenuItem("OpenRewrite Documentation");

        aboutItem.setOnAction(e -> showAboutDialog());
        documentationItem.setOnAction(e -> controller.openDocumentation());

        helpMenu.getItems().addAll(documentationItem, new SeparatorMenuItem(), aboutItem);

        menuBar.getMenus().addAll(fileMenu, editMenu, viewMenu, toolsMenu, helpMenu);
        return menuBar;
    }

    private SplitPane createMainContent() {
        // Left panel: Recipe selection and configuration
        VBox leftPanel = new VBox(10);
        leftPanel.setPadding(new Insets(10));
        leftPanel.getChildren().addAll(
            new Label("Available Recipes"),
            controller.getRecipeListView(),
            new Separator(),
            new Label("Recipe Details"),
            controller.getRecipeDetailsPane()
        );

        ScrollPane leftScroll = new ScrollPane(leftPanel);
        leftScroll.setFitToWidth(true);

        // Center panel: Code editor (original code)
        VBox centerPanel = new VBox(5);
        centerPanel.setPadding(new Insets(10));
        Label originalLabel = new Label("Original Code");
        originalLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        centerPanel.getChildren().addAll(
            originalLabel,
            controller.getSourceCodeEditor()
        );
        VBox.setVgrow(controller.getSourceCodeEditor(), Priority.ALWAYS);

        // Right panel: Transformed code and diff view
        VBox rightPanel = new VBox(5);
        rightPanel.setPadding(new Insets(10));

        // Tab pane for transformed code and diff view
        TabPane resultTabs = new TabPane();
        resultTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab transformedTab = new Tab("Transformed Code");
        transformedTab.setContent(controller.getTransformedCodeEditor());

        Tab diffTab = new Tab("Diff View");
        diffTab.setContent(controller.getDiffViewer());

        resultTabs.getTabs().addAll(transformedTab, diffTab);

        Label resultLabel = new Label("Transformation Result");
        resultLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        rightPanel.getChildren().addAll(resultLabel, resultTabs);
        VBox.setVgrow(resultTabs, Priority.ALWAYS);

        // Create split pane with three panels
        SplitPane splitPane = new SplitPane();
        splitPane.getItems().addAll(leftScroll, centerPanel, rightPanel);
        splitPane.setDividerPositions(0.25, 0.5);

        return splitPane;
    }

    private void toggleTheme() {
        isDarkTheme = !isDarkTheme;
        applyTheme();
    }

    private void applyTheme() {
        if (scene != null && scene.getRoot() != null) {
            if (isDarkTheme) {
                scene.getRoot().getStyleClass().remove("light-theme");
                scene.getRoot().getStyleClass().add("dark-theme");
            } else {
                scene.getRoot().getStyleClass().remove("dark-theme");
                scene.getRoot().getStyleClass().add("light-theme");
            }
        }
    }

    private void showAboutDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About");
        alert.setHeaderText("OpenRewrite Code Transformer");
        alert.setContentText(
            "Version 1.0.0\n\n" +
            "A JavaFX application for applying OpenRewrite recipes\n" +
            "to transform source code via MCP (Model Context Protocol).\n\n" +
            "Built with OpenRewrite, JavaFX, and MCP SDK"
        );
        alert.showAndWait();
    }

    private void showErrorDialog(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(content);
        alert.showAndWait();
    }

    @Override
    public void stop() {
        logger.info("Application shutting down...");
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