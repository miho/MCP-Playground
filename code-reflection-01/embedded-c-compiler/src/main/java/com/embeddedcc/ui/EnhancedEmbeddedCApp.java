package com.embeddedcc.ui;

import com.embeddedcc.analysis.CacheConfiguration;
import com.embeddedcc.analysis.CacheEvent;
import com.embeddedcc.analysis.CacheSummary;
import com.embeddedcc.analysis.CacheInsights;
import com.embeddedcc.analysis.RunResultPersister;
import com.embeddedcc.analysis.ProgramService;
import com.embeddedcc.compiler.RunResult;
import com.embeddedcc.instrumentation.ArrayAccess;
import com.embeddedcc.instrumentation.InstrumentationPoint;
import com.embeddedcc.instrumentation.InstrumentedProgram;
import com.embeddedcc.instrumentation.ProgramAnalysis;
import com.embeddedcc.ui.components.*;
import com.embeddedcc.ui.dialogs.ServerSettingsDialog;
import com.embeddedcc.ui.server.McpServerManager;
import com.embeddedcc.ui.server.ServerConfig;
import com.embeddedcc.util.ResourceHelper;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.SplitPane;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Enhanced version of the main application with modern UI components,
 * beautiful visualizations, and improved user experience.
 */
public class EnhancedEmbeddedCApp extends Application {

    private final ProgramService programService = new ProgramService();
    private final EnhancedCodeView codeView = new EnhancedCodeView();
    private final PerformanceDashboard dashboard = new PerformanceDashboard();
    private final HotspotVisualization hotspotViz = new HotspotVisualization();

    private final ObservableList<CandidateRow> candidates = FXCollections.observableArrayList();
    private final TableView<CandidateRow> candidateTable = new TableView<>(candidates);
    private final TextArea outputArea = new TextArea();
    private final TextArea instrumentedArea = new TextArea();
    private final Label statusLabel = new Label("Ready");
    private final Label runInfoLabel = new Label("No run executed yet");
    private final ComboBox<String> sampleSelector = new ComboBox<>();
    private final Map<String, String> sampleFiles = new HashMap<>();

    private Button runButton;
    private ServerControlBar controlBar;
    private McpServerManager serverManager;
    private ServerConfig serverConfig = ServerConfig.defaultConfig();
    private Theme currentTheme = Theme.DARK;
    private Scene mainScene;
    private final RunResultPersister resultPersister = new RunResultPersister();
    private final RunResultPersister.RunResultListener runResultListener = this::handlePersistedResult;

    private Spinner<Integer> cacheSetBitsSpinner;
    private Spinner<Integer> cacheLinesPerSetSpinner;
    private Spinner<Integer> cacheBlockBitsSpinner;
    private Spinner<Integer> sweepStartSpinner;
    private Spinner<Integer> sweepEndSpinner;
    private Spinner<Integer> sweepStepSpinner;
    private final TextField sweepMacroField = new TextField("BLOCK_SIZE");
    private Button sweepButton;
    private final ObservableList<BlockSweepRow> sweepRows = FXCollections.observableArrayList();
    private final TableView<BlockSweepRow> sweepTable = new TableView<>(sweepRows);

    private ProgramAnalysis currentAnalysis = new ProgramAnalysis(List.of(), List.of());
    private String currentSourceName = "program.c";
    private CacheSummary lastSummary = CacheSummary.empty();

    @Override
    public void start(Stage primaryStage) throws Exception {
        sampleFiles.put("Matrix Multiply", "csamples/matrix_multiply.c");
        sampleFiles.put("Blocked Transpose", "csamples/transpose_blocking.c");

        serverManager = new McpServerManager();
        RunResultPersister.addListener(runResultListener);

        // Build main layout
        BorderPane leftPane = buildLeftPane();
        VBox rightPane = buildRightPane();

        SplitPane mainSplit = new SplitPane();
        mainSplit.getItems().addAll(leftPane, rightPane);
        mainSplit.setDividerPositions(0.60);
        SplitPane.setResizableWithParent(leftPane, true);
        SplitPane.setResizableWithParent(rightPane, true);

        controlBar = buildControlBar();

        BorderPane root = new BorderPane();
        root.setTop(controlBar);
        root.setCenter(mainSplit);

        Scene scene = new Scene(root, 1600, 1000);
        this.mainScene = scene;
        applyTheme(currentTheme);

        primaryStage.setTitle("Cache Analysis Studio - Enhanced UI");
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(event -> {
            serverManager.stop();
            RunResultPersister.removeListener(runResultListener);
        });
        primaryStage.show();

        loadSample("Matrix Multiply");
    }

    private BorderPane buildLeftPane() {
        BorderPane pane = new BorderPane();
        pane.setPadding(new Insets(16));

        // Title for code section
        Label title = new Label("Source Code");
        title.getStyleClass().add("section-title");
        title.setPadding(new Insets(0, 0, 12, 0));

        codeView.setEditable(true);

        VBox container = new VBox(8, title, codeView);
        VBox.setVgrow(codeView, Priority.ALWAYS);
        pane.setCenter(container);

        return pane;
    }

    private ServerControlBar buildControlBar() {
        ServerControlBar bar = new ServerControlBar();
        bar.setMode(serverConfig.mode());
        bar.setDarkTheme(currentTheme == Theme.DARK);
        bar.setServerRunning(serverManager.isRunning(), serverManager.statusMessageProperty().get());

        bar.setOnLaunch(this::handleLaunchServer);
        bar.setOnStop(this::handleStopServer);
        bar.setOnSettings(this::showServerSettingsDialog);
        bar.setOnThemeToggle(this::toggleTheme);

        serverManager.runningProperty().addListener((obs, oldVal, newVal) ->
                Platform.runLater(() -> bar.setServerRunning(newVal, serverManager.statusMessageProperty().get())));
        serverManager.statusMessageProperty().addListener((obs, oldVal, newVal) ->
                Platform.runLater(() -> bar.setServerRunning(serverManager.isRunning(), newVal)));

        return bar;
    }

    private VBox buildRightPane() {
        VBox container = new VBox(16);
        container.setPadding(new Insets(16));
        container.setPrefWidth(600);

        // Controls section
        VBox controlsSection = buildControlsSection();

        // Create tabbed interface for different views
        TabPane mainTabs = new TabPane();
        mainTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // Analysis tab
        Tab analysisTab = new Tab("Analysis");
        analysisTab.setContent(buildAnalysisContent());

        // Performance tab with dashboard
        Tab performanceTab = new Tab("Performance");
        performanceTab.setContent(buildPerformanceContent());

        // Results tab
        Tab resultsTab = new Tab("Results");
        resultsTab.setContent(buildResultsContent());

        // Block Sweep tab
        Tab sweepTab = new Tab("Block Sweep");
        sweepTab.setContent(buildSweepContent());

        mainTabs.getTabs().addAll(analysisTab, performanceTab, resultsTab, sweepTab);

        container.getChildren().addAll(controlsSection, mainTabs);
        VBox.setVgrow(mainTabs, Priority.ALWAYS);

        return container;
    }

    private VBox buildControlsSection() {
        VBox section = new VBox(12);

        // Sample selector
        HBox sampleBar = new HBox(8);
        sampleBar.setAlignment(Pos.CENTER_LEFT);

        Label sampleLabel = new Label("Sample:");
        sampleLabel.getStyleClass().add("control-label");

        sampleSelector.getItems().addAll(sampleFiles.keySet());
        sampleSelector.setPrefWidth(200);

        Button loadButton = new Button("Load");
        loadButton.getStyleClass().add("secondary-button");
        loadButton.setOnAction(e -> {
            String key = sampleSelector.getValue();
            if (key != null) {
                loadSample(key);
            }
        });

        Button analyzeButton = new Button("Analyze");
        analyzeButton.getStyleClass().add("secondary-button");
        analyzeButton.setOnAction(e -> refreshAnalysis());

        sampleBar.getChildren().addAll(sampleLabel, sampleSelector, loadButton, analyzeButton);

        // Cache configuration
        HBox cacheConfig = buildCacheConfigSection();

        // Action buttons
        HBox actionButtons = new HBox(8);
        actionButtons.setAlignment(Pos.CENTER_LEFT);

        runButton = new Button("Instrument & Run Analysis");
        runButton.getStyleClass().add("primary-button");
        runButton.setDefaultButton(true);
        runButton.setOnAction(e -> runPipeline());

        Button selectAllBtn = new Button("Select All");
        selectAllBtn.getStyleClass().add("secondary-button");
        selectAllBtn.setOnAction(e -> candidates.forEach(row -> row.setSelected(true)));

        Button clearBtn = new Button("Clear");
        clearBtn.getStyleClass().add("secondary-button");
        clearBtn.setOnAction(e -> candidates.forEach(row -> row.setSelected(false)));

        actionButtons.getChildren().addAll(runButton, selectAllBtn, clearBtn);

        // Status
        statusLabel.getStyleClass().add("status-label");
        HBox statusBox = new HBox(statusLabel);
        statusBox.setAlignment(Pos.CENTER_LEFT);

        section.getChildren().addAll(sampleBar, cacheConfig, actionButtons, statusBox);
        return section;
    }

    private HBox buildCacheConfigSection() {
        HBox section = new HBox(12);
        section.setAlignment(Pos.CENTER_LEFT);

        if (cacheSetBitsSpinner == null) {
            cacheSetBitsSpinner = createSpinner(1, 12, 5, 1);
            cacheLinesPerSetSpinner = createSpinner(1, 16, 1, 1);
            cacheBlockBitsSpinner = createSpinner(1, 10, 5, 1);
        }

        Label configLabel = new Label("Cache Config:");
        configLabel.getStyleClass().add("control-label");

        section.getChildren().addAll(
            configLabel,
            createLabeledControl("Set bits (s)", cacheSetBitsSpinner),
            createLabeledControl("Lines/set (E)", cacheLinesPerSetSpinner),
            createLabeledControl("Block bits (b)", cacheBlockBitsSpinner)
        );

        return section;
    }

    private VBox buildAnalysisContent() {
        VBox content = new VBox(12);
        content.setPadding(new Insets(16));

        Label title = new Label("Instrumentation Candidates");
        title.getStyleClass().add("section-title");

        Label countLabel = new Label();
        countLabel.textProperty().bind(Bindings.size(candidates).asString("Detected %d array accesses"));

        buildInstrumentationTable();

        content.getChildren().addAll(title, countLabel, candidateTable);
        VBox.setVgrow(candidateTable, Priority.ALWAYS);

        return content;
    }

    private VBox buildPerformanceContent() {
        VBox content = new VBox(16);
        content.setPadding(new Insets(16));

        // Run info
        Label runInfoTitle = new Label("Run Information");
        runInfoTitle.getStyleClass().add("section-title");
        runInfoLabel.setWrapText(true);
        runInfoLabel.setPadding(new Insets(8));
        runInfoLabel.getStyleClass().add("info-label");

        // Dashboard
        dashboard.setPrefHeight(500);

        // Hotspot visualization
        hotspotViz.setOnHotspotClick(line -> codeView.focusLine(line));

        content.getChildren().addAll(runInfoTitle, runInfoLabel, dashboard, hotspotViz);
        VBox.setVgrow(hotspotViz, Priority.ALWAYS);

        return content;
    }

    private VBox buildResultsContent() {
        VBox content = new VBox(12);
        content.setPadding(new Insets(16));

        TabPane resultTabs = new TabPane();
        resultTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        outputArea.setEditable(false);
        outputArea.setWrapText(true);
        outputArea.getStyleClass().add("output-area");

        instrumentedArea.setEditable(false);
        instrumentedArea.setWrapText(true);
        instrumentedArea.getStyleClass().add("output-area");

        Tab outputTab = new Tab("Program Output", new ScrollPane(outputArea));
        Tab instrumentedTab = new Tab("Instrumented Code", new ScrollPane(instrumentedArea));

        resultTabs.getTabs().addAll(outputTab, instrumentedTab);

        content.getChildren().add(resultTabs);
        VBox.setVgrow(resultTabs, Priority.ALWAYS);

        return content;
    }

    private VBox buildSweepContent() {
        VBox content = new VBox(12);
        content.setPadding(new Insets(16));

        if (sweepStartSpinner == null) {
            sweepStartSpinner = createSpinner(2, 256, 4, 2);
            sweepEndSpinner = createSpinner(2, 256, 64, 2);
            sweepStepSpinner = createSpinner(1, 128, 4, 1);
        }

        sweepMacroField.setPrefWidth(120);
        sweepMacroField.setPromptText("Macro name");

        sweepButton = new Button("Run Block Size Sweep");
        sweepButton.getStyleClass().add("success-button");
        sweepButton.setOnAction(e -> runBlockSweep());

        HBox controls = new HBox(12,
                createLabeledControl("Macro", sweepMacroField),
                createLabeledControl("Start", sweepStartSpinner),
                createLabeledControl("End", sweepEndSpinner),
                createLabeledControl("Step", sweepStepSpinner),
                sweepButton
        );
        controls.setAlignment(Pos.CENTER_LEFT);

        buildSweepTable();

        content.getChildren().addAll(controls, sweepTable);
        VBox.setVgrow(sweepTable, Priority.ALWAYS);

        return content;
    }

    private void buildInstrumentationTable() {
        candidateTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        candidateTable.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        candidateTable.setEditable(true);
        candidateTable.setPrefHeight(400);

        TableColumn<CandidateRow, Boolean> selectCol = new TableColumn<>("Instrument");
        selectCol.setCellValueFactory(data -> data.getValue().selectedProperty());
        selectCol.setCellFactory(CheckBoxTableCell.forTableColumn(selectCol));
        selectCol.setPrefWidth(90);

        TableColumn<CandidateRow, Number> lineCol = new TableColumn<>("Line");
        lineCol.setCellValueFactory(data -> data.getValue().lineProperty());
        lineCol.setPrefWidth(70);

        TableColumn<CandidateRow, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(data -> data.getValue().typeProperty());
        typeCol.setPrefWidth(80);

        TableColumn<CandidateRow, String> exprCol = new TableColumn<>("Expression");
        exprCol.setCellValueFactory(data -> data.getValue().expressionProperty());

        candidateTable.getColumns().addAll(selectCol, lineCol, typeCol, exprCol);

        candidateTable.getSelectionModel().selectedItemProperty().addListener((obs, old, val) -> {
            if (val != null) {
                codeView.focusLine(val.getLine());
            }
        });
    }

    private void buildSweepTable() {
        sweepTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        TableColumn<BlockSweepRow, Number> blockCol = new TableColumn<>("Block Size");
        blockCol.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getBlockSize()));
        blockCol.setPrefWidth(100);

        TableColumn<BlockSweepRow, Number> missCol = new TableColumn<>("Misses");
        missCol.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getMisses()));

        TableColumn<BlockSweepRow, Number> hitCol = new TableColumn<>("Hits");
        hitCol.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getHits()));

        TableColumn<BlockSweepRow, Number> evictionCol = new TableColumn<>("Evictions");
        evictionCol.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getEvictions()));

        TableColumn<BlockSweepRow, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getStatus()));

        sweepTable.getColumns().addAll(blockCol, missCol, hitCol, evictionCol, statusCol);
        sweepTable.setPlaceholder(new Label("Run sweep to compare block sizes"));

        sweepTable.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(BlockSweepRow item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("best-row");
                if (!empty && item != null && item.isBest()) {
                    if (!getStyleClass().contains("best-row")) {
                        getStyleClass().add("best-row");
                    }
                }
            }
        });
    }

    private Spinner<Integer> createSpinner(int min, int max, int initial, int step) {
        Spinner<Integer> spinner = new Spinner<>(min, max, initial, step);
        spinner.setEditable(true);
        spinner.setPrefWidth(100);
        return spinner;
    }

    private VBox createLabeledControl(String labelText, Control control) {
        VBox box = new VBox(4);
        Label label = new Label(labelText);
        label.getStyleClass().add("control-label");
        box.getChildren().addAll(label, control);
        return box;
    }

    private void loadSample(String key) {
        String resource = sampleFiles.get(key);
        if (resource == null) {
            return;
        }
        sampleSelector.setValue(key);
        try {
            String code = ResourceHelper.readText(resource);
            currentSourceName = resource.substring(resource.lastIndexOf('/') + 1);
            codeView.setCode(code);
            refreshAnalysis();
            statusLabel.setText("Loaded sample: " + key);
        } catch (IOException e) {
            showError("Failed to load sample: " + e.getMessage());
        }
    }

    private void refreshAnalysis() {
        String code = codeView.getCode();
        currentAnalysis = programService.analyze(code);
        candidates.clear();
        for (int i = 0; i < currentAnalysis.arrayAccesses().size(); i++) {
            ArrayAccess access = currentAnalysis.arrayAccesses().get(i);
            candidates.add(new CandidateRow(i, access.getExpression(), access.getAccessType().name(), access.getLine()));
        }
        statusLabel.setText("Analysis updated - " + candidates.size() + " array accesses found");
        codeView.clearHighlights();
        sweepRows.clear();
        dashboard.reset();
        hotspotViz.clear();
    }

    private void runPipeline() {
        List<InstrumentationPoint> points = buildInstrumentationPoints();
        if (points.isEmpty()) {
            showError("Please select at least one instrumentation point");
            return;
        }

        String codeSnapshot = codeView.getCode();
        runButton.setDisable(true);
        statusLabel.setText("Running instrumentation and analysis...");
        CacheConfiguration cacheConfig = currentCacheConfiguration();
        runInfoLabel.setText("Executing...");

        Task<RunOutcome> task = new Task<>() {
            @Override
            protected RunOutcome call() throws Exception {
                InstrumentedProgram program = programService.instrument(codeSnapshot, points);
                RunResult result = programService.compileAndRun(currentSourceName, program);
                CacheSummary summary = result.isCompiled()
                        ? programService.summarizeCache(result, cacheConfig)
                        : CacheSummary.empty();

                RunResultPersister.RunRecord record = null;
                String persistError = null;
                if (result.isCompiled()) {
                    try {
                        Map<String, Object> metadata = Map.of(
                                "tool", "enhanced_ui_pipeline",
                                "defines", List.of()
                        );
                        record = resultPersister.persist(codeSnapshot, program.getSourceCode(),
                                result, summary, cacheConfig, points, List.of(), metadata);
                    } catch (IOException e) {
                        persistError = e.getMessage();
                    }
                }

                return new RunOutcome(program, result, summary, record, persistError);
            }
        };

        task.setOnSucceeded(event -> {
            RunOutcome outcome = task.getValue();
            displayResults(outcome.result(), outcome.program());

            if (outcome.result().isCompiled()) {
                lastSummary = outcome.summary();
                List<Map<String, Object>> hotspotData = CacheInsights.hotspots(outcome.summary(),
                        outcome.program().getIdLookup(), 20);
                updateCacheView(outcome.summary(), cacheConfig, hotspotData);
            } else {
                dashboard.reset();
                hotspotViz.clear();
                codeView.clearHighlights();
                runInfoLabel.setText("Compilation failed - check results tab");
            }

            if (outcome.record() != null) {
                runInfoLabel.setText(String.format("Run ID: %s%nResult file: %s",
                        outcome.record().runId(), outcome.record().path().toAbsolutePath()));
            } else {
                runInfoLabel.setText(outcome.persistError() != null
                        ? "Result persistence failed: " + outcome.persistError()
                        : "No run artefact generated");
            }

            statusLabel.setText("Analysis complete");
            runButton.setDisable(false);
        });

        task.setOnFailed(event -> {
            Throwable error = task.getException();
            showError(error != null ? error.getMessage() : "Execution failed");
            runButton.setDisable(false);
            statusLabel.setText("Execution failed");
        });

        Thread thread = new Thread(task, "analysis-task");
        thread.setDaemon(true);
        thread.start();
    }

    private void runBlockSweep() {
        List<Integer> blockSizes = collectBlockSizes();
        if (blockSizes.isEmpty()) {
            showError("Invalid block size sweep configuration");
            return;
        }

        List<InstrumentationPoint> points = buildInstrumentationPoints();
        if (points.isEmpty()) {
            showError("Select at least one instrumentation candidate before sweeping block sizes.");
            return;
        }

        String macroValue = sweepMacroField.getText().trim();
        if (macroValue.isBlank()) {
            macroValue = "BLOCK_SIZE";
            sweepMacroField.setText(macroValue);
        }

        final String macro = macroValue;
        String codeSnapshot = codeView.getCode();
        CacheConfiguration cacheConfig = currentCacheConfiguration();

        sweepButton.setDisable(true);
        statusLabel.setText("Running block sweep...");

        Task<List<BlockSweepRow>> task = new Task<>() {
            @Override
            protected List<BlockSweepRow> call() throws Exception {
                InstrumentedProgram program = programService.instrument(codeSnapshot, points);
                List<BlockSweepRow> rows = new ArrayList<>();

                for (int size : blockSizes) {
                    updateMessage("Testing block size: " + size);
                    List<String> flags = List.of("-D" + macro + "=" + size);
                    List<String> definesForStorage = List.of(macro + "=" + size);
                    RunResult result = programService.compileAndRun(currentSourceName, program, flags);
                    CacheSummary summary = result.isCompiled()
                            ? programService.summarizeCache(result, cacheConfig)
                            : CacheSummary.empty();

                    RunResultPersister.RunRecord record = null;
                    if (result.isCompiled()) {
                        try {
                            Map<String, Object> metadata = Map.of(
                                    "tool", "enhanced_ui_block_sweep",
                                    "block_macro", macro,
                                    "block_size", size
                            );
                            record = resultPersister.persist(codeSnapshot, program.getSourceCode(),
                                    result, summary, cacheConfig, points, definesForStorage, metadata);
                        } catch (IOException ignored) {
                        }
                    }
                    rows.add(BlockSweepRow.from(size, result, summary, record));
                }
                return rows;
            }
        };

        task.messageProperty().addListener((obs, old, msg) -> statusLabel.setText(msg));

        task.setOnSucceeded(event -> {
            List<BlockSweepRow> rows = task.getValue();
            markBestBlockSize(rows);
            sweepRows.setAll(rows);
            sweepTable.sort();
            sweepButton.setDisable(false);

            if (rows.stream().anyMatch(BlockSweepRow::isSuccessful)) {
                BlockSweepRow best = rows.stream()
                        .filter(BlockSweepRow::isSuccessful)
                        .min(Comparator.comparingInt(BlockSweepRow::getMisses))
                        .orElse(null);
                if (best != null) {
                    statusLabel.setText(String.format("Sweep complete. Best: %d (%,d misses)",
                            best.getBlockSize(), best.getMisses()));
                } else {
                    statusLabel.setText("Sweep complete");
                }
            } else {
                statusLabel.setText("Sweep complete (no successful runs)");
            }
        });

        task.setOnFailed(event -> {
            sweepButton.setDisable(false);
            Throwable error = task.getException();
            showError(error != null ? error.getMessage() : "Block sweep failed");
            statusLabel.setText("Sweep failed");
        });

        Thread thread = new Thread(task, "block-sweep-task");
        thread.setDaemon(true);
        thread.start();
    }

    private List<InstrumentationPoint> buildInstrumentationPoints() {
        List<ArrayAccess> accesses = currentAnalysis.arrayAccesses();
        List<InstrumentationPoint> points = new ArrayList<>();
        AtomicInteger idCounter = new AtomicInteger(1);

        for (CandidateRow row : candidates) {
            if (row.isSelected()) {
                if (row.getIndex() >= 0 && row.getIndex() < accesses.size()) {
                    points.add(new InstrumentationPoint(idCounter.getAndIncrement(), accesses.get(row.getIndex())));
                }
            }
        }
        return points;
    }

    private void displayResults(RunResult result, InstrumentedProgram program) {
        instrumentedArea.setText(program.getSourceCode());

        StringBuilder builder = new StringBuilder();
        builder.append("=== Compilation ===\n");
        builder.append("Exit code: ").append(result.getCompileExitCode()).append("\n");
        builder.append(result.getCompileStdout());
        if (!result.getCompileStderr().isBlank()) {
            builder.append(result.getCompileStderr()).append("\n");
        }

        if (result.isCompiled()) {
            builder.append("\n=== Execution ===\n");
            builder.append("Exit code: ").append(result.getExecutionExitCode()).append("\n");
            builder.append(result.getExecutionStdout());
            if (!result.getExecutionStderr().isBlank()) {
                builder.append(result.getExecutionStderr()).append("\n");
            }
        }

        outputArea.setText(builder.toString());
    }

    private void updateCacheView(CacheSummary summary, CacheConfiguration config,
                                  List<Map<String, Object>> hotspotData) {
        // Update dashboard with metrics
        dashboard.updateMetrics(summary);

        // Update hotspot visualization
        List<HotspotVisualization.HotspotItem> hotspotItems = hotspotData.stream()
                .map(map -> new HotspotVisualization.HotspotItem(
                        ((Number) map.getOrDefault("id", -1)).intValue(),
                        map.containsKey("line") ? ((Number) map.get("line")).intValue() : 0,
                        (String) map.getOrDefault("expression", (String) map.getOrDefault("label", "-")),
                        ((Number) map.getOrDefault("misses", 0)).intValue(),
                        ((Number) map.getOrDefault("evictions", 0)).intValue(),
                        ((Number) map.getOrDefault("score", 0)).intValue()
                ))
                .collect(Collectors.toList());

        hotspotViz.setHotspots(hotspotItems);

        // Update code view with enhanced metrics
        Map<Integer, EnhancedCodeView.HotspotMetrics> metricsMap = new HashMap<>();
        int maxScore = hotspotData.stream()
                .mapToInt(m -> ((Number) m.getOrDefault("score", 0)).intValue())
                .max()
                .orElse(1);

        for (Map<String, Object> data : hotspotData) {
            if (data.containsKey("line")) {
                int line = ((Number) data.get("line")).intValue();
                int misses = ((Number) data.getOrDefault("misses", 0)).intValue();
                int evictions = ((Number) data.getOrDefault("evictions", 0)).intValue();
                int score = ((Number) data.getOrDefault("score", 0)).intValue();
                String expression = (String) data.getOrDefault("expression",
                        (String) data.getOrDefault("label", ""));

                double severity = maxScore > 0 ? (double) score / maxScore : 0.0;

                metricsMap.put(line, new EnhancedCodeView.HotspotMetrics(
                        line, misses, evictions, severity, expression
                ));
            }
        }

        codeView.highlightHotspotsWithMetrics(metricsMap);
    }

    private void showError(String message) {
        statusLabel.setText("Error: " + message);
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.showAndWait();
    }

    private CacheConfiguration currentCacheConfiguration() {
        if (cacheSetBitsSpinner == null || cacheLinesPerSetSpinner == null || cacheBlockBitsSpinner == null) {
            return CacheConfiguration.defaultConfig();
        }
        return new CacheConfiguration(
                cacheSetBitsSpinner.getValue(),
                cacheLinesPerSetSpinner.getValue(),
                cacheBlockBitsSpinner.getValue()
        );
    }

    private List<Integer> collectBlockSizes() {
        if (sweepStartSpinner == null || sweepEndSpinner == null || sweepStepSpinner == null) {
            return List.of();
        }
        int start = sweepStartSpinner.getValue();
        int end = sweepEndSpinner.getValue();
        int step = sweepStepSpinner.getValue();
        if (step <= 0) {
            return List.of();
        }

        List<Integer> values = new ArrayList<>();
        if (start <= end) {
            for (int v = start; v <= end; v += step) {
                values.add(v);
            }
        } else {
            for (int v = start; v >= end; v -= step) {
                values.add(v);
            }
        }
        return values;
    }

    private void markBestBlockSize(List<BlockSweepRow> rows) {
        rows.forEach(row -> row.setBest(false));
        rows.stream()
                .filter(BlockSweepRow::isSuccessful)
                .min(Comparator.comparingInt(BlockSweepRow::getMisses))
                .ifPresent(best -> best.setBest(true));
    }

    private void handlePersistedResult(RunResultPersister.PersistedResult persisted) {
        Map<String, Object> metadata = persisted.metadata();
        String tool = metadata != null ? String.valueOf(metadata.getOrDefault("tool", "")) : "";

        if ("enhanced_ui_pipeline".equals(tool) || "enhanced_ui_block_sweep".equals(tool)) {
            return;
        }

        Platform.runLater(() -> applyPersistedResult(persisted));
    }

    private void applyPersistedResult(RunResultPersister.PersistedResult persisted) {
        CacheSummary summary = persisted.summary();
        CacheConfiguration config = persisted.cacheConfiguration();

        List<Map<String, Object>> hotspots = persisted.hotspots().stream()
                .limit(20)
                .collect(Collectors.toList());

        updateCacheView(summary, config, hotspots);
        lastSummary = summary;

        String originalCode = persisted.originalCode();
        if (originalCode != null && !originalCode.isEmpty()) {
            codeView.setCode(originalCode);
            refreshAnalysis();
        }

        String instrumentedCode = persisted.instrumentedCode();
        if (instrumentedCode != null && !instrumentedCode.isEmpty()) {
            instrumentedArea.setText(instrumentedCode);
        }

        String runId = persisted.record().runId();
        String path = persisted.record().path().toAbsolutePath().toString();
        Map<String, Object> metadata = persisted.metadata();
        String tool = metadata != null ? String.valueOf(metadata.getOrDefault("tool", "MCP")) : "MCP";
        String defines = persisted.defines().isEmpty() ? "-" : String.join(", ", persisted.defines());

        runInfoLabel.setText(String.format("Run ID: %s%nResult file: %s%nSource: %s%nDefines: %s",
                runId, path, tool, defines));
        statusLabel.setText("Loaded results from " + tool);
    }

    private void handleLaunchServer() {
        try {
            serverManager.start(serverConfig);
            statusLabel.setText("Launching MCP server...");
        } catch (IllegalStateException e) {
            showError("Server already running");
        } catch (IOException e) {
            showError("Failed to launch MCP server: " + e.getMessage());
        }
    }

    private void handleStopServer() {
        serverManager.stop();
        statusLabel.setText("Stopping MCP server...");
    }

    private void showServerSettingsDialog() {
        ServerSettingsDialog dialog = new ServerSettingsDialog(serverConfig);
        dialog.initOwner(mainScene.getWindow());
        Optional<ServerConfig> result = dialog.showAndWait();
        result.ifPresent(config -> {
            serverConfig = config;
            controlBar.setMode(config.mode());
            if (serverManager.isRunning()) {
                statusLabel.setText("Server configuration updated. Restart to apply changes.");
            }
        });
    }

    private void toggleTheme() {
        currentTheme = currentTheme == Theme.DARK ? Theme.LIGHT : Theme.DARK;
        applyTheme(currentTheme);
        if (controlBar != null) {
            controlBar.setDarkTheme(currentTheme == Theme.DARK);
        }
    }

    private void applyTheme(Theme theme) {
        if (mainScene == null) {
            return;
        }
        List<String> stylesheets = mainScene.getStylesheets();
        stylesheets.clear();

        // Apply base styles first
        stylesheets.add(getClass().getResource("/ui/styles.css").toExternalForm());

        // Then enhanced styles
        stylesheets.add(getClass().getResource("/ui/enhanced-styles.css").toExternalForm());

        // Finally theme-specific styles
        String themeSheet = theme == Theme.DARK ? "/ui/dark-theme.css" : "/ui/light-theme.css";
        stylesheets.add(getClass().getResource(themeSheet).toExternalForm());

        codeView.setDarkTheme(theme == Theme.DARK);
    }

    private record RunOutcome(InstrumentedProgram program,
                              RunResult result,
                              CacheSummary summary,
                              RunResultPersister.RunRecord record,
                              String persistError) {
    }

    private enum Theme {
        DARK,
        LIGHT
    }

    public static void main(String[] args) {
        launch(args);
    }
}
