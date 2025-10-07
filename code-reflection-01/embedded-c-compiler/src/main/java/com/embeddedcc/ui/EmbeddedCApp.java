package com.embeddedcc.ui;

import com.embeddedcc.analysis.CacheConfiguration;
import com.embeddedcc.analysis.CacheEvent;
import com.embeddedcc.analysis.CacheSummary;
import com.embeddedcc.analysis.ProgramService;
import com.embeddedcc.compiler.RunResult;
import com.embeddedcc.instrumentation.ArrayAccess;
import com.embeddedcc.instrumentation.InstrumentationPoint;
import com.embeddedcc.instrumentation.InstrumentedProgram;
import com.embeddedcc.instrumentation.ProgramAnalysis;
import com.embeddedcc.util.ResourceHelper;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.scene.control.cell.CheckBoxTableCell;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class EmbeddedCApp extends Application {

    private final ProgramService programService = new ProgramService();
    private final CodeView codeView = new CodeView();
    private final ObservableList<CandidateRow> candidates = FXCollections.observableArrayList();
    private final TableView<CandidateRow> candidateTable = new TableView<>(candidates);
    private final ListView<CacheEventRow> cacheList = new ListView<>();
    private final TextArea outputArea = new TextArea();
    private final TextArea instrumentedArea = new TextArea();
    private final Label cacheSummaryLabel = new Label("Cache summary: n/a");
    private final Label statusLabel = new Label("Ready");
    private final ComboBox<String> sampleSelector = new ComboBox<>();
    private final Map<String, String> sampleFiles = new HashMap<>();
    private Button runButton;

    private ProgramAnalysis currentAnalysis = new ProgramAnalysis(List.of(), List.of());
    private String currentSourceName = "program.c";
    private CacheSummary lastSummary = CacheSummary.empty();

    @Override
    public void start(Stage primaryStage) throws Exception {
        sampleFiles.put("Matrix Multiply", "csamples/matrix_multiply.c");
        sampleFiles.put("Blocked Transpose", "csamples/transpose_blocking.c");

        BorderPane root = new BorderPane();
        root.setCenter(buildLeftPane());
        root.setRight(buildRightPane());

        Scene scene = new Scene(root, 1400, 900);
        scene.getStylesheets().add(getClass().getResource("/ui/styles.css").toExternalForm());

        primaryStage.setTitle("Embedded C Instrumentation Playground");
        primaryStage.setScene(scene);
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

    private VBox buildRightPane() {
        VBox container = new VBox(12);
        container.setPadding(new Insets(12));
        container.setPrefWidth(480);

        container.getChildren().add(buildSampleBar());
        container.getChildren().add(buildAnalysisControls());
        container.getChildren().add(buildInstrumentationTable());
        container.getChildren().add(buildActionButtons());
        container.getChildren().add(buildStatusBar());
        container.getChildren().add(buildOutputTabs());
        container.getChildren().add(buildCachePanel());

        VBox.setVgrow(candidateTable, Priority.ALWAYS);
        VBox.setVgrow(cacheList, Priority.SOMETIMES);

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

        HBox box = new HBox(8, selectAll, clear, runButton);
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
        tabs.setPrefHeight(220);

        return tabs;
    }

    private VBox buildCachePanel() {
        cacheList.setPrefHeight(180);
        cacheList.getSelectionModel().selectedItemProperty().addListener((obs, old, val) -> {
            if (val != null) {
                CacheEvent event = val.getEvent();
                cacheList.setTooltip(new Tooltip(event.label()));
                codeView.focusLine(event.line());
            }
        });

        VBox box = new VBox(6);
        box.getChildren().addAll(new Label("Cache Analysis"), cacheSummaryLabel, cacheList);
        VBox.setVgrow(cacheList, Priority.ALWAYS);
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
        codeView.highlightLines(Set.of());
    }

    private void runPipeline() {
        List<InstrumentationPoint> points = buildInstrumentationPoints();
        String codeSnapshot = codeView.getCode();
        runButton.setDisable(true);
        statusLabel.setText("Running instrumentation...");

        Task<RunOutcome> task = new Task<>() {
            @Override
            protected RunOutcome call() throws Exception {
                InstrumentedProgram program = programService.instrument(codeSnapshot, points);
                RunResult result = programService.compileAndRun(currentSourceName, program);
                CacheSummary summary = result.isCompiled()
                        ? programService.summarizeCache(result, CacheConfiguration.defaultConfig())
                        : CacheSummary.empty();
                return new RunOutcome(program, result, summary);
            }
        };

        task.setOnSucceeded(event -> {
            RunOutcome outcome = task.getValue();
            displayResults(outcome.result(), outcome.program());
            if (outcome.result().isCompiled()) {
                lastSummary = outcome.summary();
                updateCacheView(outcome.summary());
            } else {
                cacheSummaryLabel.setText("Cache summary: compile failed");
                cacheList.getItems().clear();
                codeView.highlightLines(Set.of());
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

    private void updateCacheView(CacheSummary summary) {
        cacheSummaryLabel.setText(String.format("Cache summary: %d hits, %d misses, %d evictions",
                summary.getHits(), summary.getMisses(), summary.getEvictions()));

        List<CacheEventRow> rows = summary.getEvents().stream()
                .map(CacheEventRow::new)
                .collect(Collectors.toList());
        cacheList.getItems().setAll(rows);

        Set<Integer> linesToHighlight = summary.getEvents().stream()
                .filter(event -> switch (event.type()) {
                    case MISS, EVICTION -> true;
                    default -> false;
                })
                .map(CacheEvent::line)
                .collect(Collectors.toSet());

        codeView.highlightLines(linesToHighlight);
    }

    private void showError(String message) {
        statusLabel.setText(message);
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.showAndWait();
    }

    private record RunOutcome(InstrumentedProgram program,
                              RunResult result,
                              CacheSummary summary) {
    }

    public static void main(String[] args) {
        launch(args);
    }
}
