package com.openrewrite.ui;

import com.openrewrite.server.McpConfig;
import com.openrewrite.server.ServerLauncher;
import com.openrewrite.ui.components.*;
import com.openrewrite.ui.model.McpClient;
import com.openrewrite.ui.model.Recipe;
import com.openrewrite.ui.model.TransformationResult;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class MainController {
    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    // UI Components
    private final AppToolBar toolBar;
    private final StatusBar statusBar;
    private final CodeEditor sourceCodeEditor;
    private final CodeEditor transformedCodeEditor;
    private final DiffViewer diffViewer;
    private final ListView<Recipe> recipeListView;
    private final RecipeDetailsPane recipeDetailsPane;

    // Data
    private final ObservableList<Recipe> recipes;
    private McpClient mcpClient;

    // Embedded server components
    private McpConfig mcpConfig;
    private ServerLauncher serverLauncher;
    private boolean mcpServerRunning = false;
    private boolean mcpServerEnabled = true;

    /**
     * Create MainController with default HTTP MCP configuration.
     * @deprecated Use MainController(McpConfig, boolean) instead to specify configuration
     */
    @Deprecated
    public MainController() {
        this(McpConfig.defaultHttp(), true);
    }

    /**
     * Create MainController with specified MCP configuration.
     * @param mcpConfig MCP server configuration (null if MCP disabled)
     * @param mcpEnabled whether to start the embedded MCP server
     */
    public MainController(McpConfig mcpConfig, boolean mcpEnabled) {
        this.mcpConfig = mcpConfig;
        this.mcpServerEnabled = mcpEnabled;

        // Initialize UI components
        this.toolBar = new AppToolBar();
        this.statusBar = new StatusBar();
        this.sourceCodeEditor = new CodeEditor();
        this.transformedCodeEditor = new CodeEditor();
        this.transformedCodeEditor.setEditable(false);
        this.diffViewer = new DiffViewer();
        this.recipes = FXCollections.observableArrayList();
        this.recipeListView = new ListView<>(recipes);
        this.recipeDetailsPane = new RecipeDetailsPane();

        setupUI();
        setupEventHandlers();
    }

    private void setupUI() {
        // Configure recipe list view
        recipeListView.setCellFactory(lv -> new ListCell<Recipe>() {
            @Override
            protected void updateItem(Recipe recipe, boolean empty) {
                super.updateItem(recipe, empty);
                if (empty || recipe == null) {
                    setText(null);
                    setTooltip(null);
                } else {
                    setText(recipe.getName());
                    if (recipe.getDescription() != null && !recipe.getDescription().isEmpty()) {
                        setTooltip(new Tooltip(recipe.getDescription()));
                    }
                }
            }
        });

        // Set default source code
        sourceCodeEditor.setText(getDefaultSourceCode());

        // Setup toolbar buttons
        toolBar.addTransformButton(e -> applySelectedRecipe());
        toolBar.addAnalyzeButton(e -> analyzeCurrentCode());
        toolBar.addRefreshButton(e -> refreshRecipes());
        toolBar.addClearButton(e -> clearEditors());
    }

    private void setupEventHandlers() {
        // Recipe selection handler
        recipeListView.getSelectionModel().selectedItemProperty().addListener(
            (obs, oldRecipe, newRecipe) -> {
                if (newRecipe != null) {
                    recipeDetailsPane.showRecipe(newRecipe);
                }
            }
        );
    }

    /**
     * Initialize embedded MCP server.
     * Called by OpenRewriteApp after UI is ready.
     */
    public void initializeMcpConnection() {
        try {
            // Only start MCP server if enabled and config is provided
            if (!mcpServerEnabled) {
                statusBar.setStatus("MCP server disabled");
                statusBar.setConnectionStatus(false);
                toolBar.setServerRunning(false);
                return;
            }

            if (mcpConfig == null) {
                statusBar.showError("MCP config is null, cannot start server");
                statusBar.setConnectionStatus(false);
                toolBar.setServerRunning(false);
                return;
            }

            statusBar.setStatus("Starting MCP server...");

            // Start embedded MCP server in background
            this.serverLauncher = new ServerLauncher(mcpConfig);
            serverLauncher.startAsync().thenAccept(success -> {
                Platform.runLater(() -> {
                    if (success) {
                        mcpServerRunning = true;
                        toolBar.setServerRunning(true);
                        statusBar.setConnectionStatus(true);
                        statusBar.showSuccess("MCP Server running: " + serverLauncher.getEndpointUrl());

                        // For HTTP mode, initialize client and load recipes
                        if (mcpConfig.getTransportMode() == McpConfig.TransportMode.HTTP) {
                            initializeHttpClient();
                        } else {
                            statusBar.setStatus("MCP Server ready (stdio mode)");
                        }

                        resetStatusAfterDelay(3000);
                    } else {
                        statusBar.showError("Failed to start embedded MCP server");
                        statusBar.setConnectionStatus(false);
                        resetStatusAfterDelay(3000);
                    }
                });
            });

        } catch (Exception e) {
            statusBar.showError("Initialization failed: " + e.getMessage());
            logger.error("Failed to initialize MCP server", e);
        }
    }

    /**
     * Initialize HTTP client for recipe loading (HTTP mode only).
     */
    private void initializeHttpClient() {
        // Delay connection attempt to allow server to fully start
        CompletableFuture.runAsync(() -> {
            try {
                // Wait a bit for server to be ready
                Thread.sleep(3000);

                mcpClient = new McpClient(serverLauncher.getEndpointUrl());

                // Retry connection with exponential backoff
                int maxRetries = 5;
                for (int i = 0; i < maxRetries; i++) {
                    try {
                        mcpClient.connect();
                        if (mcpClient.isConnected()) {
                            Platform.runLater(() -> {
                                statusBar.setStatus("Connected to MCP server");
                                statusBar.setConnectionStatus(true);
                                refreshRecipes();
                            });
                            return;
                        }
                    } catch (Exception connectEx) {
                        if (i < maxRetries - 1) {
                            logger.warn("Connection attempt {} failed, retrying...", i + 1);
                            Thread.sleep(1000 * (i + 1)); // Exponential backoff
                        }
                    }
                }

                // All retries failed
                Platform.runLater(() -> {
                    statusBar.showWarning("MCP client failed to connect after retries");
                    statusBar.setConnectionStatus(false);
                });

            } catch (Exception e) {
                logger.error("Failed to initialize HTTP client", e);
                Platform.runLater(() -> {
                    statusBar.showError("Failed to connect MCP client: " + e.getMessage());
                    statusBar.setConnectionStatus(false);
                });
            }
        });
    }

    /**
     * Reset status message after a delay.
     */
    private void resetStatusAfterDelay(int delayMs) {
        new Thread(() -> {
            try {
                Thread.sleep(delayMs);
                Platform.runLater(() -> {
                    statusBar.setStatus("Ready");
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    public void refreshRecipes() {
        if (mcpClient == null) {
            statusBar.setStatus("MCP client not connected");
            return;
        }

        statusBar.setStatus("Loading recipes...");
        statusBar.showIndeterminateProgress();

        CompletableFuture<List<Recipe>> future = mcpClient.listRecipes();
        future.thenAccept(recipeList -> {
            Platform.runLater(() -> {
                recipes.clear();
                recipes.addAll(recipeList);
                statusBar.setRecipeCount(recipeList.size());
                statusBar.setStatus("Loaded " + recipeList.size() + " recipes");
                statusBar.hideProgress();
            });
        }).exceptionally(ex -> {
            Platform.runLater(() -> {
                statusBar.showError("Failed to load recipes");
                statusBar.hideProgress();
                logger.error("Error loading recipes", ex);
            });
            return null;
        });
    }

    public void applySelectedRecipe() {
        Recipe selectedRecipe = recipeListView.getSelectionModel().getSelectedItem();
        if (selectedRecipe == null) {
            showAlert("No Recipe Selected", "Please select a recipe to apply.");
            return;
        }

        String sourceCode = sourceCodeEditor.getText();
        if (sourceCode == null || sourceCode.trim().isEmpty()) {
            showAlert("No Source Code", "Please enter source code to transform.");
            return;
        }

        statusBar.setStatus("Applying recipe: " + selectedRecipe.getName());

        CompletableFuture<TransformationResult> future = mcpClient.applyRecipe(
            sourceCode,
            selectedRecipe.getName(),
            "java"
        );

        future.thenAccept(result -> {
            Platform.runLater(() -> {
                transformedCodeEditor.setText(result.getTransformedCode());
                diffViewer.showDiff(sourceCode, result.getTransformedCode());

                if (result.hasChanges()) {
                    statusBar.setStatus("Recipe applied successfully - changes made");
                } else {
                    statusBar.setStatus("Recipe applied - no changes needed");
                }
            });
        }).exceptionally(ex -> {
            Platform.runLater(() -> {
                statusBar.setStatus("Failed to apply recipe");
                logger.error("Error applying recipe", ex);
                showAlert("Transformation Error", "Failed to apply recipe: " + ex.getMessage());
            });
            return null;
        });
    }

    public void analyzeCurrentCode() {
        String sourceCode = sourceCodeEditor.getText();
        if (sourceCode == null || sourceCode.trim().isEmpty()) {
            showAlert("No Source Code", "Please enter source code to analyze.");
            return;
        }

        statusBar.setStatus("Analyzing code...");

        CompletableFuture<List<Recipe>> future = mcpClient.analyzeCode(sourceCode, "java");
        future.thenAccept(suggestions -> {
            Platform.runLater(() -> {
                if (suggestions.isEmpty()) {
                    statusBar.setStatus("No recipe suggestions found");
                    showAlert("Analysis Complete", "No applicable recipes found for this code.");
                } else {
                    statusBar.setStatus("Found " + suggestions.size() + " applicable recipes");
                    showSuggestions(suggestions);
                }
            });
        }).exceptionally(ex -> {
            Platform.runLater(() -> {
                statusBar.setStatus("Failed to analyze code");
                logger.error("Error analyzing code", ex);
            });
            return null;
        });
    }

    private void showSuggestions(List<Recipe> suggestions) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Recipe Suggestions");
        alert.setHeaderText("The following recipes can be applied to your code:");

        VBox content = new VBox(5);
        for (Recipe recipe : suggestions) {
            Label label = new Label("• " + recipe.getName());
            if (recipe.getDescription() != null) {
                label.setTooltip(new Tooltip(recipe.getDescription()));
            }
            content.getChildren().add(label);
        }

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setPrefHeight(200);
        alert.getDialogPane().setContent(scrollPane);
        alert.showAndWait();
    }

    public void openFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Source File");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Java Files", "*.java"),
            new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            try {
                String content = Files.readString(file.toPath());
                sourceCodeEditor.setText(content);
                statusBar.setStatus("Loaded: " + file.getName());
            } catch (IOException e) {
                logger.error("Error reading file", e);
                showAlert("File Error", "Failed to read file: " + e.getMessage());
            }
        }
    }

    public void saveTransformedCode() {
        String transformedCode = transformedCodeEditor.getText();
        if (transformedCode == null || transformedCode.trim().isEmpty()) {
            showAlert("No Transformed Code", "No transformed code to save.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Transformed Code");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Java Files", "*.java"),
            new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        File file = fileChooser.showSaveDialog(null);
        if (file != null) {
            try {
                Files.writeString(file.toPath(), transformedCode);
                statusBar.setStatus("Saved: " + file.getName());
            } catch (IOException e) {
                logger.error("Error saving file", e);
                showAlert("Save Error", "Failed to save file: " + e.getMessage());
            }
        }
    }

    public void clearEditors() {
        sourceCodeEditor.clear();
        transformedCodeEditor.clear();
        diffViewer.clear();
        statusBar.setStatus("Editors cleared");
    }

    public void openDocumentation() {
        // TODO: Open OpenRewrite documentation
        showAlert("Documentation", "Opening documentation not yet implemented");
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private String getDefaultSourceCode() {
        return """
            import java.util.*;

            public class Example {
                private String name;
                private int value = 0; // Explicit initialization to default

                public void process() {
                    List list = new ArrayList(); // Raw type usage

                    String text = "Hello";
                    text = text.replace("H", "h"); // Can use String.replace instead of replaceAll

                    if (true == (1 == 1)) { // Can be simplified
                        System.out.println(text);
                    }

                    int unused = 5; // Unused variable

                    if (list.size() > 0) { // Can be list.isEmpty()
                        // Empty block
                    }
                }
            }
            """;
    }

    public void showMcpSettings() {
        Alert info = new Alert(Alert.AlertType.INFORMATION);
        info.setTitle("Server Settings");
        info.setHeaderText("Embedded Server Configuration");

        StringBuilder content = new StringBuilder();
        content.append("Server Status: ").append(mcpServerEnabled ? "Enabled" : "Disabled").append("\n\n");

        if (mcpConfig != null) {
            content.append("Current Configuration:\n");
            content.append("Transport Mode: ").append(mcpConfig.getTransportMode()).append("\n");

            if (mcpConfig.getTransportMode() == McpConfig.TransportMode.HTTP) {
                content.append("HTTP Host: ").append(mcpConfig.getHttpHost()).append("\n");
                content.append("HTTP Port: ").append(mcpConfig.getHttpPort()).append("\n");
                content.append("HTTP Endpoint: ").append(mcpConfig.getHttpEndpoint()).append("\n");
                content.append("Full URL: ").append(mcpConfig.getHttpUrl()).append("\n");
            } else {
                content.append("Mode: Standard input/output\n");
            }

            content.append("\nLogging: ").append(mcpConfig.isCaptureServerLogs() ? "Enabled" : "Disabled").append("\n");
            if (mcpConfig.isCaptureServerLogs()) {
                content.append("Log Directory: ").append(mcpConfig.getLogDirectory()).append("\n");
            }
        } else {
            content.append("No configuration available\n");
        }

        content.append("\nNote: Configuration can be set via CLI arguments.\n");
        content.append("Use --help to see available options.\n");
        content.append("Configuration changes require restarting the application.");

        info.setContentText(content.toString());
        info.showAndWait();
    }

    public void shutdown() {
        if (mcpClient != null) {
            mcpClient.disconnect();
        }
        if (serverLauncher != null) {
            serverLauncher.shutdown();
        }
    }

    // Getters for UI components
    public AppToolBar getToolBar() { return toolBar; }
    public StatusBar getStatusBar() { return statusBar; }
    public CodeEditor getSourceCodeEditor() { return sourceCodeEditor; }
    public CodeEditor getTransformedCodeEditor() { return transformedCodeEditor; }
    public DiffViewer getDiffViewer() { return diffViewer; }
    public ListView<Recipe> getRecipeListView() { return recipeListView; }
    public RecipeDetailsPane getRecipeDetailsPane() { return recipeDetailsPane; }
}