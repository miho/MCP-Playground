package com.openrewrite.ui;

import com.openrewrite.server.McpConfig;
import com.openrewrite.ui.model.Recipe;
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

    private static final String DARK_THEME_CSS = "/css/dark-theme.css";
    private static final String LIGHT_THEME_CSS = "/css/light-theme.css";

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

            // Create scene
            scene = new Scene(root, DEFAULT_WIDTH, DEFAULT_HEIGHT);

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
        openItem.setAccelerator(javafx.scene.input.KeyCombination.keyCombination("Ctrl+O"));
        MenuItem saveItem = new MenuItem("Save Transformed Code...");
        saveItem.setAccelerator(javafx.scene.input.KeyCombination.keyCombination("Ctrl+S"));
        MenuItem exitItem = new MenuItem("Exit");
        exitItem.setAccelerator(javafx.scene.input.KeyCombination.keyCombination("Ctrl+Q"));

        openItem.setOnAction(e -> controller.openFile());
        saveItem.setOnAction(e -> controller.saveTransformedCode());
        exitItem.setOnAction(e -> System.exit(0));

        fileMenu.getItems().addAll(openItem, saveItem, new SeparatorMenuItem(), exitItem);

        // Edit menu
        Menu editMenu = new Menu("Edit");
        MenuItem clearItem = new MenuItem("Clear Editors");
        clearItem.setAccelerator(javafx.scene.input.KeyCombination.keyCombination("Ctrl+Shift+C"));
        clearItem.setOnAction(e -> controller.clearEditors());

        editMenu.getItems().addAll(clearItem);

        // View menu
        Menu viewMenu = new Menu("View");
        CheckMenuItem darkThemeItem = new CheckMenuItem("Dark Theme");
        darkThemeItem.setSelected(isDarkTheme);
        darkThemeItem.setAccelerator(javafx.scene.input.KeyCombination.keyCombination("Ctrl+T"));
        darkThemeItem.setOnAction(e -> toggleTheme());

        MenuItem refreshRecipesItem = new MenuItem("Refresh Recipes");
        refreshRecipesItem.setAccelerator(javafx.scene.input.KeyCombination.keyCombination("F5"));
        refreshRecipesItem.setOnAction(e -> controller.refreshRecipes());

        viewMenu.getItems().addAll(darkThemeItem, new SeparatorMenuItem(), refreshRecipesItem);

        // Tools menu
        Menu toolsMenu = new Menu("Tools");
        MenuItem applyRecipeItem = new MenuItem("Apply Recipe");
        applyRecipeItem.setAccelerator(javafx.scene.input.KeyCombination.keyCombination("Ctrl+R"));
        applyRecipeItem.setOnAction(e -> controller.applySelectedRecipe());

        MenuItem analyzeCodeItem = new MenuItem("Analyze Code");
        analyzeCodeItem.setAccelerator(javafx.scene.input.KeyCombination.keyCombination("Ctrl+A"));
        analyzeCodeItem.setOnAction(e -> controller.analyzeCurrentCode());

        MenuItem mcpSettingsItem = new MenuItem("MCP Settings...");

        mcpSettingsItem.setOnAction(e -> controller.showMcpSettings());

        toolsMenu.getItems().addAll(applyRecipeItem, analyzeCodeItem, new SeparatorMenuItem(), mcpSettingsItem);

        // Help menu
        Menu helpMenu = new Menu("Help");
        MenuItem aboutItem = new MenuItem("About");
        aboutItem.setAccelerator(javafx.scene.input.KeyCombination.keyCombination("F1"));
        MenuItem documentationItem = new MenuItem("OpenRewrite Documentation");

        aboutItem.setOnAction(e -> showAboutDialog());
        documentationItem.setOnAction(e -> controller.openDocumentation());

        helpMenu.getItems().addAll(documentationItem, new SeparatorMenuItem(), aboutItem);

        menuBar.getMenus().addAll(fileMenu, editMenu, viewMenu, toolsMenu, helpMenu);
        return menuBar;
    }

    private SplitPane createMainContent() {
        // Left panel: Recipe selection and configuration
        VBox leftPanel = new VBox(12);
        leftPanel.setPadding(new Insets(15));

        // Recipe section header
        Label recipesLabel = new Label("Available Recipes");
        recipesLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 15px;");

        // Recipe search field
        TextField searchField = controller.getRecipeSearchField();

        // Recipe list view
        ListView<Recipe> recipeList = controller.getRecipeListView();
        VBox.setVgrow(recipeList, Priority.ALWAYS);

        // Recipe details section header
        Label detailsLabel = new Label("Recipe Details");
        detailsLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        leftPanel.getChildren().addAll(
            recipesLabel,
            searchField,
            recipeList,
            new Separator(),
            detailsLabel,
            controller.getRecipeDetailsPane()
        );

        ScrollPane leftScroll = new ScrollPane(leftPanel);
        leftScroll.setFitToWidth(true);
        leftScroll.setFitToHeight(true);

        // Center panel: Code editor (original code)
        VBox centerPanel = new VBox(8);
        centerPanel.setPadding(new Insets(15));
        Label originalLabel = new Label("Original Code");
        originalLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 15px;");

        // Add editor container with border
        VBox editorContainer = new VBox(controller.getSourceCodeEditor());
        editorContainer.setStyle("-fx-border-color: -fx-border-default; -fx-border-width: 1; -fx-border-radius: 6;");
        VBox.setVgrow(editorContainer, Priority.ALWAYS);

        centerPanel.getChildren().addAll(
            originalLabel,
            editorContainer
        );
        VBox.setVgrow(controller.getSourceCodeEditor(), Priority.ALWAYS);

        // Right panel: Transformed code and diff view
        VBox rightPanel = new VBox(8);
        rightPanel.setPadding(new Insets(15));

        // Tab pane for transformed code and diff view
        TabPane resultTabs = new TabPane();
        resultTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab transformedTab = new Tab("Transformed Code");
        VBox transformedContainer = new VBox(controller.getTransformedCodeEditor());
        transformedContainer.setStyle("-fx-border-color: -fx-border-default; -fx-border-width: 1; -fx-border-radius: 6;");
        VBox.setVgrow(controller.getTransformedCodeEditor(), Priority.ALWAYS);
        VBox.setVgrow(transformedContainer, Priority.ALWAYS);
        transformedTab.setContent(transformedContainer);

        Tab diffTab = new Tab("Diff View");
        VBox diffContainer = new VBox(controller.getDiffViewer());
        VBox.setVgrow(controller.getDiffViewer(), Priority.ALWAYS);
        VBox.setVgrow(diffContainer, Priority.ALWAYS);
        diffTab.setContent(diffContainer);

        resultTabs.getTabs().addAll(transformedTab, diffTab);

        Label resultLabel = new Label("Transformation Result");
        resultLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 15px;");
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
        if (scene != null) {
            // Clear existing stylesheets
            scene.getStylesheets().clear();

            // Apply the appropriate theme
            String themeResource = isDarkTheme ? DARK_THEME_CSS : LIGHT_THEME_CSS;
            try {
                String themePath = getClass().getResource(themeResource).toExternalForm();
                scene.getStylesheets().add(themePath);
                logger.info("Applied theme: {}", isDarkTheme ? "Dark" : "Light");
            } catch (Exception e) {
                logger.error("Failed to load theme: {}", themeResource, e);
                // Fallback to old application.css if theme files are not found
                try {
                    scene.getStylesheets().add(
                        getClass().getResource("/css/application.css").toExternalForm()
                    );
                } catch (Exception ex) {
                    logger.error("Failed to load fallback stylesheet", ex);
                }
            }
        }
    }

    private void showAboutDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About OpenRewrite Code Transformer");
        alert.setHeaderText("OpenRewrite Code Transformer");

        VBox content = new VBox(10);
        content.setPadding(new Insets(10));

        Label versionLabel = new Label("Version 1.0.0");
        versionLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");

        Label descLabel = new Label(
            "A modern JavaFX application for applying OpenRewrite recipes\n" +
            "to transform source code via MCP (Model Context Protocol)."
        );
        descLabel.setWrapText(true);
        descLabel.setStyle("-fx-font-size: 12px;");

        Label techLabel = new Label("Built with:");
        techLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-padding: 10 0 5 0;");

        Label techListLabel = new Label(
            "  - OpenRewrite (Code transformation engine)\n" +
            "  - JavaFX (Modern UI framework)\n" +
            "  - MCP SDK (Model Context Protocol)\n" +
            "  - RichTextFX (Syntax highlighting)"
        );
        techListLabel.setStyle("-fx-font-size: 11px;");

        content.getChildren().addAll(versionLabel, descLabel, techLabel, techListLabel);

        alert.getDialogPane().setContent(content);
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