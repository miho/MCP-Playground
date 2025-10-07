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
import com.embeddedcc.ui.components.ResultsLoader;
import com.embeddedcc.ui.components.ServerControlBar;
import com.embeddedcc.ui.dialogs.ServerSettingsDialog;
import com.embeddedcc.ui.server.McpServerManager;
import com.embeddedcc.ui.server.ServerConfig;
import com.embeddedcc.ui.server.ServerMode;
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
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class EmbeddedCApp extends Application {

    private final ProgramService programService = new ProgramService();
    private final CodeView codeView = new CodeView();
    private final ObservableList<CandidateRow> candidates = FXCollections.observableArrayList();
    private final TableView<CandidateRow> candidateTable = new TableView<>(candidates);
    private final ListView<CacheEventRow> cacheList = new ListView<>();
    private final ObservableList<HotspotRow> hotspotRows = FXCollections.observableArrayList();
    private final TableView<HotspotRow> hotspotTable = new TableView<>(hotspotRows);
    private final TextArea outputArea = new TextArea();
    private final TextArea instrumentedArea = new TextArea();
    private final Label cacheSummaryLabel = new Label("Cache summary: n/a");
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

        BorderPane leftPane = buildLeftPane();
        VBox rightPane = buildRightPane();

        SplitPane mainSplit = new SplitPane();
        mainSplit.getItems().addAll(leftPane, rightPane);
        mainSplit.setDividerPositions(0.63);
        SplitPane.setResizableWithParent(leftPane, true);
        SplitPane.setResizableWithParent(rightPane, true);

        controlBar = buildControlBar();
        MenuBar menuBar = buildMenuBar();

        VBox topContainer = new VBox();
        topContainer.getChildren().addAll(menuBar, controlBar);

        BorderPane root = new BorderPane();
        root.setTop(topContainer);
        root.setCenter(mainSplit);

        Scene scene = new Scene(root, 1400, 900);
        this.mainScene = scene;
        applyTheme(currentTheme);

        primaryStage.setTitle("Embedded C Instrumentation Playground");
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
        pane.setPadding(new Insets(10));
        codeView.setEditable(true);
        pane.setCenter(codeView);
        return pane;
    }

    private MenuBar buildMenuBar() {
        MenuBar menuBar = new MenuBar();

        // File menu
        Menu fileMenu = new Menu("File");

        MenuItem loadResultItem = new MenuItem("Load Result...");
        loadResultItem.setOnAction(e -> loadResultsManually());

        MenuItem loadRecentItem = new MenuItem("Load Recent Results");
        loadRecentItem.setOnAction(e -> showRecentResults());

        MenuItem exportItem = new MenuItem("Export Current Result...");
        exportItem.setOnAction(e -> exportCurrentResult());

        fileMenu.getItems().addAll(
            loadResultItem,
            loadRecentItem,
            new SeparatorMenuItem(),
            exportItem
        );

        // View menu
        Menu viewMenu = new Menu("View");

        CheckMenuItem darkModeItem = new CheckMenuItem("Dark Mode");
        darkModeItem.setSelected(currentTheme == Theme.DARK);
        darkModeItem.setOnAction(e -> toggleTheme());

        MenuItem refreshItem = new MenuItem("Refresh Analysis");
        refreshItem.setOnAction(e -> refreshAnalysis());

        viewMenu.getItems().addAll(darkModeItem, refreshItem);

        // Help menu
        Menu helpMenu = new Menu("Help");

        MenuItem aboutItem = new MenuItem("About");
        aboutItem.setOnAction(e -> showAbout());

        helpMenu.getItems().add(aboutItem);

        menuBar.getMenus().addAll(fileMenu, viewMenu, helpMenu);
        return menuBar;
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
        VBox container = new VBox(12);
        container.setPadding(new Insets(12));
        container.setPrefWidth(520);

        HBox sampleBar = buildSampleBar();
        VBox analysisControls = buildAnalysisControls();
        HBox actionButtons = buildActionButtons();
        HBox statusBar = buildStatusBar();

        VBox cacheSection = buildCacheSection();
        VBox instrumentationSection = buildInstrumentationTable();
        TabPane outputTabs = buildOutputTabs();
        VBox sweepSection = buildSweepSection();

        VBox controls = new VBox(12, sampleBar, analysisControls, actionButtons, statusBar);
        controls.setFillWidth(true);

        SplitPane resizableContent = new SplitPane();
        resizableContent.setOrientation(Orientation.VERTICAL);
        cacheSection.setMinHeight(140);
        instrumentationSection.setMinHeight(180);
        outputTabs.setMinHeight(200);
        sweepSection.setMinHeight(160);
        resizableContent.getItems().addAll(cacheSection, instrumentationSection, outputTabs, sweepSection);
        resizableContent.setDividerPositions(0.25, 0.55, 0.8);
        SplitPane.setResizableWithParent(cacheSection, true);
        SplitPane.setResizableWithParent(instrumentationSection, true);
        SplitPane.setResizableWithParent(outputTabs, true);
        SplitPane.setResizableWithParent(sweepSection, true);

        container.getChildren().addAll(controls, resizableContent);
        VBox.setVgrow(resizableContent, Priority.ALWAYS);

        return container;
    }

    private HBox buildSampleBar() {
        HBox box = new HBox(8);
        box.setAlignment(Pos.CENTER_LEFT);

        sampleSelector.getItems().addAll(sampleFiles.keySet());
        sampleSelector.setPrefWidth(220);

        Button loadButton = new Button("Load");
        loadButton.setOnAction(e -> {
            String key = sampleSelector.getValue();
            if (key != null) {
                loadSample(key);
            }
        });

        Button analyzeButton = new Button("Analyze");
        analyzeButton.setOnAction(e -> refreshAnalysis());

        box.getChildren().addAll(new Label("Samples:"), sampleSelector, loadButton, analyzeButton);

        return box;
    }

    private VBox buildAnalysisControls() {
        VBox box = new VBox(4);
        Label instrumentationLabel = new Label("Instrumentation Candidates");
        Label functionsLabel = new Label();
        functionsLabel.textProperty().bind(Bindings.size(candidates).asString("Detected %d array accesses"));

        box.getChildren().addAll(instrumentationLabel, functionsLabel);
        return box;
    }

    private VBox buildInstrumentationTable() {
        candidateTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        candidateTable.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

        candidateTable.setEditable(true);

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
        candidateTable.setMinHeight(240);

        candidateTable.getSelectionModel().selectedItemProperty().addListener((obs, old, val) -> {
            if (val != null) {
                codeView.focusLine(val.getLine());
            }
        });

        VBox box = new VBox(candidateTable);
        VBox.setVgrow(candidateTable, Priority.ALWAYS);
        return box;
    }

    private HBox buildActionButtons() {
        Button selectAll = new Button("Select All");
        selectAll.setOnAction(e -> candidates.forEach(row -> row.setSelected(true)));

        Button clear = new Button("Clear");
        clear.setOnAction(e -> candidates.forEach(row -> row.setSelected(false)));

        runButton = new Button("Instrument & Run");
        runButton.setDefaultButton(true);
        runButton.setOnAction(e -> runPipeline());

        Button loadResultsButton = new Button("Load Results");
        loadResultsButton.setOnAction(e -> loadResultsManually());

        HBox box = new HBox(8, selectAll, clear, runButton, loadResultsButton);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private HBox buildStatusBar() {
        statusLabel.getStyleClass().add("status-label");
        HBox box = new HBox(statusLabel);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private TabPane buildOutputTabs() {
        outputArea.setEditable(false);
        outputArea.setPrefRowCount(8);

        instrumentedArea.setEditable(false);
        instrumentedArea.setPrefRowCount(8);

        Tab outputTab = new Tab("Program Output", new ScrollPane(outputArea));
        outputTab.setClosable(false);

        Tab instrumentedTab = new Tab("Instrumented Code", new ScrollPane(instrumentedArea));
        instrumentedTab.setClosable(false);

        TabPane tabs = new TabPane(outputTab, instrumentedTab);

        return tabs;
    }

    private VBox buildCacheSection() {
        if (cacheSetBitsSpinner == null) {
            cacheSetBitsSpinner = createSpinner(1, 12, 5, 1);
            cacheLinesPerSetSpinner = createSpinner(1, 16, 1, 1);
            cacheBlockBitsSpinner = createSpinner(1, 10, 5, 1);
        }

        cacheList.setPrefHeight(160);
        cacheList.setMaxHeight(Double.MAX_VALUE);
        cacheList.getSelectionModel().selectedItemProperty().addListener((obs, old, val) -> {
            if (val != null) {
                CacheEvent event = val.getEvent();
                cacheList.setTooltip(new Tooltip(event.label()));
                codeView.focusLine(event.line());
            }
        });

        runInfoLabel.setWrapText(true);

        HBox configRow = new HBox(8,
                createLabeledControl("Set bits (s)", cacheSetBitsSpinner),
                createLabeledControl("Lines/set (E)", cacheLinesPerSetSpinner),
                createLabeledControl("Block bits (b)", cacheBlockBitsSpinner)
        );
        configRow.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(6);
        box.getChildren().addAll(
                new Label("Cache Configuration"),
                configRow,
                new Separator(),
                new Label("Run Info"),
                runInfoLabel,
                new Label("Hotspots"),
                buildHotspotTable(),
                new Label("Cache Events"),
                cacheSummaryLabel,
                cacheList
        );
        VBox.setVgrow(cacheList, Priority.ALWAYS);
        return box;
    }

    private Spinner<Integer> createSpinner(int min, int max, int initial, int step) {
        Spinner<Integer> spinner = new Spinner<>(min, max, initial, step);
        spinner.setEditable(true);
        spinner.setPrefWidth(100);
        return spinner;
    }

    private VBox createLabeledControl(String labelText, Control control) {
        VBox box = new VBox(2);
        Label label = new Label(labelText);
        box.getChildren().addAll(label, control);
        return box;
    }

    private TableView<HotspotRow> buildHotspotTable() {
        if (hotspotTable.getColumns().isEmpty()) {
            hotspotTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
            hotspotTable.setPrefHeight(120);

            TableColumn<HotspotRow, Number> idCol = new TableColumn<>("ID");
            idCol.setCellValueFactory(data -> data.getValue().idProperty());
            idCol.setPrefWidth(60);

            TableColumn<HotspotRow, Number> lineCol = new TableColumn<>("Line");
            lineCol.setCellValueFactory(data -> data.getValue().lineProperty());
            lineCol.setPrefWidth(70);

            TableColumn<HotspotRow, String> exprCol = new TableColumn<>("Expression");
            exprCol.setCellValueFactory(data -> data.getValue().expressionProperty());

            TableColumn<HotspotRow, Number> missCol = new TableColumn<>("Misses");
            missCol.setCellValueFactory(data -> data.getValue().missesProperty());

            TableColumn<HotspotRow, Number> evictCol = new TableColumn<>("Evictions");
            evictCol.setCellValueFactory(data -> data.getValue().evictionsProperty());

            TableColumn<HotspotRow, Number> scoreCol = new TableColumn<>("Score");
            scoreCol.setCellValueFactory(data -> data.getValue().scoreProperty());

            hotspotTable.getColumns().addAll(idCol, lineCol, exprCol, missCol, evictCol, scoreCol);
            hotspotTable.setPlaceholder(new Label("Run instrumentation to see hotspots"));
            scoreCol.setSortType(TableColumn.SortType.DESCENDING);
            hotspotTable.getSortOrder().add(scoreCol);
            hotspotTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, val) -> {
                if (val != null && val.getLine() > 0) {
                    codeView.focusLine(val.getLine());
                }
            });
        }

        return hotspotTable;
    }

    private VBox buildSweepSection() {
        if (sweepStartSpinner == null) {
            sweepStartSpinner = createSpinner(2, 256, 4, 2);
            sweepEndSpinner = createSpinner(2, 256, 64, 2);
            sweepStepSpinner = createSpinner(1, 128, 4, 1);
        }

        sweepMacroField.setPrefWidth(120);
        sweepMacroField.setPromptText("Macro name");

        if (sweepButton == null) {
            sweepButton = new Button("Sweep Block Sizes");
            sweepButton.setOnAction(e -> runBlockSweep());
        }

        HBox controls = new HBox(8,
                createLabeledControl("Macro", sweepMacroField),
                createLabeledControl("Start", sweepStartSpinner),
                createLabeledControl("End", sweepEndSpinner),
                createLabeledControl("Step", sweepStepSpinner),
                sweepButton
        );
        controls.setAlignment(Pos.CENTER_LEFT);

        if (sweepTable.getColumns().isEmpty()) {
            sweepTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
            sweepTable.setMaxHeight(Double.MAX_VALUE);

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

            TableColumn<BlockSweepRow, String> runCol = new TableColumn<>("Run ID");
            runCol.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(
                    Optional.ofNullable(data.getValue().getRunId()).orElse("-")));

            sweepTable.getColumns().addAll(blockCol, missCol, hitCol, evictionCol, statusCol, runCol);
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
                    if (!empty && item != null && item.getResultPath() != null) {
                        setTooltip(new Tooltip(item.getResultPath()));
                    } else {
                        setTooltip(null);
                    }
                }
            });
        }

        VBox box = new VBox(6);
        box.getChildren().addAll(new Label("Block Size Sweep"), controls, sweepTable);
        VBox.setVgrow(sweepTable, Priority.ALWAYS);
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
        statusLabel.setText("Analysis updated");
        codeView.clearHighlights();
        sweepRows.clear();
        sweepTable.refresh();
    }

    private void runPipeline() {
        List<InstrumentationPoint> points = buildInstrumentationPoints();
        String codeSnapshot = codeView.getCode();
        runButton.setDisable(true);
        statusLabel.setText("Running instrumentation...");
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
                                "tool", "ui_pipeline",
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
                List<Map<String, Object>> hotspots = CacheInsights.hotspots(outcome.summary(),
                        outcome.program().getIdLookup(), 10);
                updateCacheView(outcome.summary(), cacheConfig, hotspots);
            } else {
                cacheSummaryLabel.setText(String.format(
                        "Cache summary (s=%d, E=%d, b=%d): compile failed",
                        cacheConfig.setBits(), cacheConfig.linesPerSet(), cacheConfig.blockBits()));
                cacheList.getItems().clear();
                codeView.clearHighlights();
                hotspotRows.clear();
                runInfoLabel.setText("No run artefact (compile failed)");
            }

            if (outcome.record() != null) {
                runInfoLabel.setText(String.format("Run ID: %s%nResult file: %s",
                        outcome.record().runId(), outcome.record().path().toAbsolutePath()));
            } else {
                runInfoLabel.setText(outcome.persistError() != null
                        ? "Result persistence failed: " + outcome.persistError()
                        : "No run artefact generated");
            }

            if (outcome.persistError() != null) {
                statusLabel.setText("Execution finished (persistence warning)");
            }
            runButton.setDisable(false);
        });

        task.setOnFailed(event -> {
            Throwable error = task.getException();
            showError(error != null ? error.getMessage() : "Execution failed");
            runButton.setDisable(false);
        });

        Thread thread = new Thread(task, "compile-run-task");
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
                                    "tool", "ui_block_sweep",
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

        task.setOnSucceeded(event -> {
            List<BlockSweepRow> rows = task.getValue();
            markBestBlockSize(rows);
            sweepRows.setAll(rows);
            sweepTable.refresh();
            sweepTable.sort();
            sweepButton.setDisable(false);
            if (rows.stream().anyMatch(BlockSweepRow::isSuccessful)) {
                BlockSweepRow best = rows.stream()
                        .filter(BlockSweepRow::isSuccessful)
                        .min(Comparator.comparingInt(BlockSweepRow::getMisses))
                        .orElse(null);
                if (best != null) {
                    statusLabel.setText("Sweep complete. Best block size: " + best.getBlockSize()
                            + " (" + best.getMisses() + " misses)");
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
            statusLabel.setText("Execution finished");
        } else {
            statusLabel.setText("Compilation failed");
        }
        outputArea.setText(builder.toString());
    }

    private void updateCacheView(CacheSummary summary, CacheConfiguration config, List<Map<String, Object>> hotspotData) {
        cacheSummaryLabel.setText(String.format(
                "Cache summary (s=%d, E=%d, b=%d): %d hits, %d misses, %d evictions",
                config.setBits(), config.linesPerSet(), config.blockBits(),
                summary.getHits(), summary.getMisses(), summary.getEvictions()));

        List<CacheEventRow> rows = summary.getEvents().stream()
                .map(CacheEventRow::new)
                .collect(Collectors.toList());
        cacheList.getItems().setAll(rows);

        List<Map<String, Object>> hotspots = hotspotData != null ? hotspotData : List.of();
        hotspotRows.setAll(hotspots.stream()
                .map(map -> new HotspotRow(
                        ((Number) map.getOrDefault("id", -1)).intValue(),
                        map.containsKey("line") ? ((Number) map.get("line")).intValue() : null,
                        (String) map.getOrDefault("expression", (String) map.getOrDefault("label", "-")),
                        ((Number) map.getOrDefault("misses", 0)).intValue(),
                        ((Number) map.getOrDefault("evictions", 0)).intValue(),
                        ((Number) map.getOrDefault("score", 0)).intValue()
                ))
                .collect(Collectors.toList()));
        hotspotTable.sort();

        Map<Integer, String> severityMap = new HashMap<>();
        int maxScore = hotspotRows.stream().mapToInt(HotspotRow::getScore).max().orElse(0);
        for (HotspotRow row : hotspotRows) {
            if (row.getLine() > 0) {
                severityMap.put(row.getLine(), classifyScore(row.getScore(), maxScore));
            }
        }

        Set<Integer> linesToHighlight = summary.getEvents().stream()
                .filter(event -> switch (event.type()) {
                    case MISS, EVICTION -> true;
                    default -> false;
                })
                .map(CacheEvent::line)
                .collect(Collectors.toSet());

        for (Integer line : linesToHighlight) {
            severityMap.putIfAbsent(line, "hotspot-low");
        }

        codeView.highlightHotspots(severityMap);
    }

    private void showError(String message) {
        statusLabel.setText(message);
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.showAndWait();
    }

    private record RunOutcome(InstrumentedProgram program,
                              RunResult result,
                              CacheSummary summary,
                              RunResultPersister.RunRecord record,
                              String persistError) {
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

    private String classifyScore(int score, int maxScore) {
        if (maxScore <= 0) {
            return "hotspot-low";
        }
        double ratio = score / (double) maxScore;
        if (ratio >= 0.66) {
            return "hotspot-high";
        } else if (ratio >= 0.33) {
            return "hotspot-medium";
        }
        return "hotspot-low";
    }

    private void handlePersistedResult(RunResultPersister.PersistedResult persisted) {
        Map<String, Object> metadata = persisted.metadata();
        String tool = metadata != null ? String.valueOf(metadata.getOrDefault("tool", "")) : "";
        if ("ui_pipeline".equals(tool) || "ui_block_sweep".equals(tool)) {
            return;
        }
        if (!tool.isEmpty() && !"compile_and_run_c".equals(tool)) {
            return;
        }
        Platform.runLater(() -> applyPersistedResult(persisted));
    }

    private void applyPersistedResult(RunResultPersister.PersistedResult persisted) {
        try {
            // Load cache summary and configuration
            CacheSummary summary = persisted.summary();
            CacheConfiguration config = persisted.cacheConfiguration();

            // Load and display original code FIRST (before updateCacheView)
            String originalCode = persisted.originalCode();
            if (originalCode != null && !originalCode.isEmpty()) {
                codeView.setCode(originalCode);
                // Update the current source name for consistency
                currentSourceName = "loaded_result.c";

                // Refresh analysis to update instrumentation candidates
                refreshAnalysis();
            } else {
                codeView.setCode("// No original code available in this result file\n");
            }

            // Update cache view with results AFTER setting code
            // This ensures highlights are applied to the loaded code
            if (summary != null && config != null) {
                List<Map<String, Object>> hotspots = persisted.hotspots() != null
                    ? persisted.hotspots().stream().limit(10).collect(Collectors.toList())
                    : List.of();
                updateCacheView(summary, config, hotspots);
                lastSummary = summary;
            }

            // Load and display instrumented code
            String instrumentedCode = persisted.instrumentedCode();
            if (instrumentedCode != null && !instrumentedCode.isEmpty()) {
                instrumentedArea.setText(instrumentedCode);
            } else {
                instrumentedArea.setText("// No instrumented code available\n// Run instrumentation to generate");
            }

            // Update run information display
            String runId = persisted.record() != null ? persisted.record().runId() : "unknown";
            String path = persisted.record() != null && persisted.record().path() != null
                ? persisted.record().path().toAbsolutePath().toString()
                : "N/A";

            Map<String, Object> metadata = persisted.metadata();
            String tool = metadata != null ? String.valueOf(metadata.getOrDefault("tool", "Manual")) : "Manual";
            String defines = persisted.defines() != null && !persisted.defines().isEmpty()
                ? String.join(", ", persisted.defines())
                : "none";

            // Update info labels
            runInfoLabel.setText(String.format(
                "Run ID: %s%nFile: %s%nSource: %s%nDefines: %s%nCode loaded: %s",
                runId,
                path.substring(Math.max(0, path.lastIndexOf('/') + 1)),
                tool,
                defines,
                originalCode != null && !originalCode.isEmpty() ? "Yes" : "No"
            ));

            // Update status with success message
            statusLabel.setText(String.format(
                "✓ Loaded %s (%d events, %d hotspots)",
                runId,
                summary != null ? summary.getEvents().size() : 0,
                persisted.hotspots() != null ? persisted.hotspots().size() : 0
            ));

            // Switch to output tab to show results
            outputArea.setText(String.format(
                "=== Loaded Result ===\n" +
                "Run ID: %s\n" +
                "Tool: %s\n" +
                "Cache Configuration: s=%d, E=%d, b=%d\n" +
                "Performance: %d hits, %d misses, %d evictions\n" +
                "Hit Rate: %.1f%%\n",
                runId, tool,
                config != null ? config.setBits() : 0,
                config != null ? config.linesPerSet() : 0,
                config != null ? config.blockBits() : 0,
                summary != null ? summary.getHits() : 0,
                summary != null ? summary.getMisses() : 0,
                summary != null ? summary.getEvictions() : 0,
                summary != null && (summary.getHits() + summary.getMisses()) > 0
                    ? (100.0 * summary.getHits()) / (summary.getHits() + summary.getMisses())
                    : 0.0
            ));

        } catch (Exception e) {
            showError("Error applying results: " + e.getMessage());
            e.printStackTrace();
        }
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

    private void loadResultsManually() {
        try {
            Optional<ResultsLoader.LoadedResult> result = ResultsLoader.showLoadDialog(mainScene.getWindow());
            if (result.isPresent()) {
                ResultsLoader.LoadedResult loaded = result.get();
                RunResultPersister.PersistedResult persisted = loaded.toPersistedResult();
                applyPersistedResult(persisted);
                statusLabel.setText("Loaded results from file: " + loaded.runId());
            }
        } catch (Exception e) {
            showError("Failed to load results: " + e.getMessage());
        }
    }

    private void showRecentResults() {
        List<ResultsLoader.ResultFile> recent = ResultsLoader.findRecentResults(10);
        if (recent.isEmpty()) {
            showInfo("No recent results found in ~/.embedded-c-cache/results/");
            return;
        }

        // Create list of choices with formatted strings
        List<String> choices = recent.stream()
            .map(f -> f.runId() + " - " + f.timestamp().toString())
            .collect(Collectors.toList());

        ChoiceDialog<String> dialog = new ChoiceDialog<>(choices.get(0), choices);
        dialog.setTitle("Load Recent Result");
        dialog.setHeaderText("Select a recent result to load:");
        dialog.setContentText("Result:");

        Optional<String> result = dialog.showAndWait();
        if (result.isPresent()) {
            // Find the corresponding file
            int index = choices.indexOf(result.get());
            if (index >= 0 && index < recent.size()) {
                ResultsLoader.ResultFile selectedFile = recent.get(index);
                try {
                    ResultsLoader.LoadedResult loaded = ResultsLoader.loadFromFile(selectedFile.path());
                    applyPersistedResult(loaded.toPersistedResult());
                    statusLabel.setText("Loaded recent result: " + loaded.runId());
                } catch (IOException e) {
                    showError("Failed to load result: " + e.getMessage());
                }
            }
        }
    }

    private void exportCurrentResult() {
        if (lastSummary == null || lastSummary.equals(CacheSummary.empty())) {
            showInfo("No result to export. Run analysis first.");
            return;
        }
        // TODO: Implement export functionality
        showInfo("Export functionality coming soon");
    }

    private void showAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About");
        alert.setHeaderText("Embedded C Cache Analysis Tool");
        alert.setContentText("Version 1.0\n\nAnalyze cache performance of embedded C programs\nwith instrumentation and visualization.");
        alert.showAndWait();
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.showAndWait();
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
        stylesheets.add(getClass().getResource("/ui/styles.css").toExternalForm());
        String themeSheet = theme == Theme.DARK ? "/ui/dark-theme.css" : "/ui/light-theme.css";
        stylesheets.add(getClass().getResource(themeSheet).toExternalForm());
        codeView.setDarkTheme(theme == Theme.DARK);
    }

    public static void main(String[] args) {
        launch(args);
    }

    private enum Theme {
        DARK,
        LIGHT
    }
}
