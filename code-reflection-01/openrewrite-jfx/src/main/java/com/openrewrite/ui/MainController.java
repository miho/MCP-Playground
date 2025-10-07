package com.openrewrite.ui;

import com.openrewrite.server.McpConfig;
import com.openrewrite.server.ServerLauncher;
import com.openrewrite.server.TransformationEvent;
import com.openrewrite.server.TransformationEventBus;
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
import java.util.function.Consumer;

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
    private final TextField recipeSearchField;

    // Data
    private final ObservableList<Recipe> recipes;
    private final ObservableList<Recipe> filteredRecipes;
    private McpClient mcpClient;

    // Embedded server components
    private McpConfig mcpConfig;
    private ServerLauncher serverLauncher;
    private boolean mcpServerRunning = false;
    private boolean mcpServerEnabled = true;

    // Event bus integration
    private final TransformationEventBus eventBus;
    private final Consumer<TransformationEvent> eventListener;

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
        this.filteredRecipes = FXCollections.observableArrayList();
        this.recipeListView = new ListView<>(filteredRecipes);
        this.recipeDetailsPane = new RecipeDetailsPane();
        this.recipeSearchField = new TextField();

        // Initialize event bus integration
        this.eventBus = TransformationEventBus.getInstance();
        this.eventListener = this::handleTransformationEvent;

        setupUI();
        setupEventHandlers();
        setupEventBusSubscription();
    }

    private void setupUI() {
        // Configure recipe search field
        recipeSearchField.setPromptText("Search recipes by name or description...");
        recipeSearchField.getStyleClass().add("search-field");

        // Configure recipe list view
        recipeListView.setCellFactory(lv -> new ListCell<Recipe>() {
            @Override
            protected void updateItem(Recipe recipe, boolean empty) {
                super.updateItem(recipe, empty);
                if (empty || recipe == null) {
                    setText(null);
                    setTooltip(null);
                } else {
                    setText(recipe.getDisplayName());
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

        // Recipe search/filter handler
        recipeSearchField.textProperty().addListener((obs, oldText, newText) -> {
            filterRecipes(newText);
        });
    }

    /**
     * Setup event bus subscription to listen for external transformation events.
     */
    private void setupEventBusSubscription() {
        eventBus.subscribe(eventListener);
        logger.info("Subscribed to TransformationEventBus");
    }

    /**
     * Handle transformation events from the event bus.
     * This method is called from the event bus thread, so UI updates must be
     * marshalled to the JavaFX Application Thread using Platform.runLater().
     *
     * @param event the transformation event
     */
    private void handleTransformationEvent(TransformationEvent event) {
        logger.info("Received transformation event: {}", event);

        // Marshal UI updates to JavaFX Application Thread
        Platform.runLater(() -> {
            try {
                switch (event.getType()) {
                    case TRANSFORMATION_STARTED:
                        handleTransformationStarted(event);
                        break;

                    case TRANSFORMATION_COMPLETED:
                        handleTransformationCompleted(event);
                        break;

                    case TRANSFORMATION_FAILED:
                        handleTransformationFailed(event);
                        break;

                    default:
                        logger.warn("Unknown event type: {}", event.getType());
                }
            } catch (Exception e) {
                logger.error("Error handling transformation event", e);
                statusBar.showError("Error processing external transformation: " + e.getMessage());
            }
        });
    }

    /**
     * Handle transformation started event - update UI to show that a transformation is in progress.
     */
    private void handleTransformationStarted(TransformationEvent event) {
        statusBar.setStatus("External transformation started: " + event.getRecipeName());
        statusBar.showIndeterminateProgress();

        // Update source code editor if it's different from current content
        String currentSource = sourceCodeEditor.getText();
        if (event.getSourceCode() != null && !event.getSourceCode().equals(currentSource)) {
            sourceCodeEditor.setText(event.getSourceCode());
        }

        logger.debug("UI updated for transformation started: {}", event.getRecipeName());
    }

    /**
     * Handle transformation completed event - update UI with the results.
     */
    private void handleTransformationCompleted(TransformationEvent event) {
        // Update source code editor
        if (event.getSourceCode() != null) {
            sourceCodeEditor.setText(event.getSourceCode());
        }

        // Update transformed code editor
        if (event.getTransformedCode() != null) {
            transformedCodeEditor.setText(event.getTransformedCode());
        }

        // Update diff view
        if (event.getSourceCode() != null && event.getTransformedCode() != null) {
            diffViewer.showDiff(event.getSourceCode(), event.getTransformedCode());
        }

        // Update status bar
        statusBar.hideProgress();
        if (event.hasChanges()) {
            statusBar.showSuccess("External transformation completed: " +
                    event.getRecipeDisplayName() + " - changes detected");
        } else {
            statusBar.setStatus("External transformation completed: " +
                    event.getRecipeDisplayName() + " - no changes needed");
        }

        logger.info("UI updated for completed transformation: {}", event.getRecipeName());
    }

    /**
     * Handle transformation failed event - update UI to show the error.
     */
    private void handleTransformationFailed(TransformationEvent event) {
        statusBar.hideProgress();
        statusBar.showError("External transformation failed: " + event.getRecipeName());

        // Update source code editor if provided
        if (event.getSourceCode() != null) {
            String currentSource = sourceCodeEditor.getText();
            if (!event.getSourceCode().equals(currentSource)) {
                sourceCodeEditor.setText(event.getSourceCode());
            }
        }

        // Show error dialog with details
        String errorMsg = event.getErrorMessage() != null ?
                event.getErrorMessage() : "Unknown error";

        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Transformation Failed");
        alert.setHeaderText("External transformation failed");
        alert.setContentText("Recipe: " + event.getRecipeName() + "\n\nError: " + errorMsg);
        alert.showAndWait();

        logger.error("Transformation failed for recipe: {}, error: {}",
                event.getRecipeName(), errorMsg);
    }

    /**
     * Filter recipes based on search text.
     */
    private void filterRecipes(String searchText) {
        filteredRecipes.clear();

        if (searchText == null || searchText.trim().isEmpty()) {
            // Show all recipes when search is empty
            filteredRecipes.addAll(recipes);
        } else {
            String lowerCaseSearch = searchText.toLowerCase().trim();

            // Filter recipes by name, displayName, description, or tags
            for (Recipe recipe : recipes) {
                boolean matches = false;

                // Check name
                if (recipe.getName() != null && recipe.getName().toLowerCase().contains(lowerCaseSearch)) {
                    matches = true;
                }

                // Check display name
                if (!matches && recipe.getDisplayName() != null &&
                    recipe.getDisplayName().toLowerCase().contains(lowerCaseSearch)) {
                    matches = true;
                }

                // Check description
                if (!matches && recipe.getDescription() != null &&
                    recipe.getDescription().toLowerCase().contains(lowerCaseSearch)) {
                    matches = true;
                }

                // Check tags
                if (!matches && recipe.getTags() != null) {
                    for (String tag : recipe.getTags()) {
                        if (tag.toLowerCase().contains(lowerCaseSearch)) {
                            matches = true;
                            break;
                        }
                    }
                }

                if (matches) {
                    filteredRecipes.add(recipe);
                }
            }
        }

        // Update status bar with filter info
        if (searchText != null && !searchText.trim().isEmpty()) {
            statusBar.setRecipeCount(filteredRecipes.size() + " of " + recipes.size());
        } else {
            statusBar.setRecipeCount(recipes.size());
        }
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
                filteredRecipes.clear();
                filteredRecipes.addAll(recipeList);
                statusBar.setRecipeCount(recipeList.size());
                statusBar.setStatus("Loaded " + recipeList.size() + " recipes");
                statusBar.hideProgress();
                recipeSearchField.clear();
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

        // Validate recipe options if any are required
        RecipeDetailsPane.ValidationResult validation = recipeDetailsPane.validateOptions();
        if (!validation.isValid()) {
            showAlert("Invalid Options", validation.getErrorMessage());
            return;
        }

        // Get option values from the details pane
        Map<String, Object> options = recipeDetailsPane.getOptionValues();

        statusBar.setStatus("Applying recipe: " + selectedRecipe.getName());
        if (!options.isEmpty()) {
            logger.info("Applying recipe with {} option(s)", options.size());
        }

        CompletableFuture<TransformationResult> future = mcpClient.applyRecipe(
            sourceCode,
            selectedRecipe.getName(),
            "java",
            options
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

        // Use TextArea to make content selectable and copyable
        TextArea textArea = new TextArea();
        textArea.setEditable(false);
        textArea.setWrapText(true);

        StringBuilder content = new StringBuilder();
        for (Recipe recipe : suggestions) {
            content.append("• ").append(recipe.getName());
            if (recipe.getDescription() != null) {
                content.append("\n  ").append(recipe.getDescription());
            }
            content.append("\n\n");
        }

        textArea.setText(content.toString());
        textArea.setPrefHeight(300);
        textArea.setPrefWidth(600);

        // Allow the dialog to be resizable
        alert.getDialogPane().setContent(textArea);
        alert.setResizable(true);
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
        // Unsubscribe from event bus
        if (eventListener != null) {
            eventBus.unsubscribe(eventListener);
            logger.info("Unsubscribed from TransformationEventBus");
        }

        // Shutdown MCP client and server
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
    public TextField getRecipeSearchField() { return recipeSearchField; }
}