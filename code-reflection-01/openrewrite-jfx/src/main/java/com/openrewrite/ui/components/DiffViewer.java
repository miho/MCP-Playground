package com.openrewrite.ui.components;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Component to display side-by-side or unified diff view of code changes.
 * Shows original and transformed code with highlighted differences.
 */
public class DiffViewer extends BorderPane {

    private final VBox diffContainer;
    private final Label headerLabel;
    private final ScrollPane scrollPane;
    private DiffMode diffMode = DiffMode.UNIFIED;

    public enum DiffMode {
        UNIFIED,    // Single column with +/- indicators
        SIDE_BY_SIDE // Two columns showing before/after
    }

    public DiffViewer() {
        getStyleClass().add("diff-viewer");

        // Header
        headerLabel = new Label("No differences to display");
        headerLabel.getStyleClass().add("diff-header");
        headerLabel.setPadding(new Insets(10));
        headerLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        // Diff container
        diffContainer = new VBox();
        diffContainer.getStyleClass().add("diff-container");
        diffContainer.setPadding(new Insets(10));

        // Scroll pane
        scrollPane = new ScrollPane(diffContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.getStyleClass().add("diff-scroll");

        setTop(headerLabel);
        setCenter(scrollPane);
    }

    /**
     * Show diff between original and transformed code.
     */
    public void showDiff(String original, String transformed) {
        diffContainer.getChildren().clear();

        if (original == null || transformed == null) {
            headerLabel.setText("No differences to display");
            return;
        }

        if (original.equals(transformed)) {
            headerLabel.setText("No changes detected");
            Label noChanges = new Label("The original and transformed code are identical.");
            noChanges.setStyle("-fx-text-fill: #7f8c8d; -fx-font-style: italic;");
            diffContainer.getChildren().add(noChanges);
            return;
        }

        List<DiffLine> diffLines = computeDiff(original, transformed);
        int additions = (int) diffLines.stream().filter(d -> d.type == DiffType.ADDITION).count();
        int deletions = (int) diffLines.stream().filter(d -> d.type == DiffType.DELETION).count();

        headerLabel.setText(String.format("Changes: +%d additions, -%d deletions", additions, deletions));

        if (diffMode == DiffMode.UNIFIED) {
            renderUnifiedDiff(diffLines);
        } else {
            renderSideBySideDiff(original, transformed);
        }
    }

    /**
     * Compute line-by-line diff between two texts.
     */
    private List<DiffLine> computeDiff(String original, String transformed) {
        List<String> originalLines = Arrays.asList(original.split("\n", -1));
        List<String> transformedLines = Arrays.asList(transformed.split("\n", -1));
        List<DiffLine> diffLines = new ArrayList<>();

        // Simple Myers diff-like algorithm
        int[][] dp = new int[originalLines.size() + 1][transformedLines.size() + 1];

        // Fill DP table
        for (int i = 0; i <= originalLines.size(); i++) {
            for (int j = 0; j <= transformedLines.size(); j++) {
                if (i == 0) {
                    dp[i][j] = j;
                } else if (j == 0) {
                    dp[i][j] = i;
                } else if (originalLines.get(i - 1).equals(transformedLines.get(j - 1))) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = 1 + Math.min(dp[i - 1][j], Math.min(dp[i][j - 1], dp[i - 1][j - 1]));
                }
            }
        }

        // Backtrack to find diff
        int i = originalLines.size();
        int j = transformedLines.size();

        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && originalLines.get(i - 1).equals(transformedLines.get(j - 1))) {
                diffLines.add(0, new DiffLine(DiffType.CONTEXT, originalLines.get(i - 1), i));
                i--;
                j--;
            } else if (j > 0 && (i == 0 || dp[i][j - 1] <= dp[i - 1][j])) {
                diffLines.add(0, new DiffLine(DiffType.ADDITION, transformedLines.get(j - 1), j));
                j--;
            } else if (i > 0) {
                diffLines.add(0, new DiffLine(DiffType.DELETION, originalLines.get(i - 1), i));
                i--;
            }
        }

        return diffLines;
    }

    /**
     * Render unified diff view (single column with +/- indicators).
     */
    private void renderUnifiedDiff(List<DiffLine> diffLines) {
        Font monoFont = Font.font("Consolas, Monaco, Courier New", 13);

        for (DiffLine line : diffLines) {
            HBox lineBox = new HBox(5);
            lineBox.setPadding(new Insets(2, 5, 2, 5));

            // Line number
            Label lineNumber = new Label(String.valueOf(line.lineNumber));
            lineNumber.setMinWidth(40);
            lineNumber.setFont(Font.font("Consolas", 12));
            lineNumber.setStyle("-fx-text-fill: #7f8c8d;");

            // Indicator
            Label indicator = new Label(line.type.symbol);
            indicator.setMinWidth(20);
            indicator.setFont(monoFont);

            // Content
            Text content = new Text(line.content);
            content.setFont(monoFont);

            // Apply styling based on diff type
            switch (line.type) {
                case ADDITION:
                    lineBox.setStyle("-fx-background-color: #d4edda; " +
                            "-fx-border-color: #c3e6cb; " +
                            "-fx-border-width: 0px 0px 0px 3px; " +
                            "-fx-border-insets: 0px;");
                    indicator.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                    content.setFill(Color.web("#155724"));
                    break;
                case DELETION:
                    lineBox.setStyle("-fx-background-color: #f8d7da; " +
                            "-fx-border-color: #f5c6cb; " +
                            "-fx-border-width: 0px 0px 0px 3px; " +
                            "-fx-border-insets: 0px;");
                    indicator.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
                    content.setFill(Color.web("#721c24"));
                    break;
                case CONTEXT:
                    lineBox.setStyle("-fx-background-color: transparent;");
                    indicator.setStyle("-fx-text-fill: #95a5a6;");
                    content.setFill(Color.web("#2c3e50"));
                    break;
            }

            lineBox.getChildren().addAll(lineNumber, indicator, content);
            diffContainer.getChildren().add(lineBox);
        }
    }

    /**
     * Render side-by-side diff view.
     */
    private void renderSideBySideDiff(String original, String transformed) {
        String[] originalLines = original.split("\n", -1);
        String[] transformedLines = transformed.split("\n", -1);
        Font monoFont = Font.font("Consolas, Monaco, Courier New", 13);

        GridPane grid = new GridPane();
        grid.setHgap(10);

        // Column constraints
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(50);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPercentWidth(50);
        grid.getColumnConstraints().addAll(col1, col2);

        // Headers
        Label originalHeader = new Label("Original");
        originalHeader.setStyle("-fx-font-weight: bold; -fx-padding: 5;");
        Label transformedHeader = new Label("Transformed");
        transformedHeader.setStyle("-fx-font-weight: bold; -fx-padding: 5;");

        grid.add(originalHeader, 0, 0);
        grid.add(transformedHeader, 1, 0);

        // Add lines
        int maxLines = Math.max(originalLines.length, transformedLines.length);
        for (int i = 0; i < maxLines; i++) {
            TextFlow originalFlow = new TextFlow();
            TextFlow transformedFlow = new TextFlow();

            if (i < originalLines.length) {
                Text text = new Text(originalLines[i]);
                text.setFont(monoFont);
                originalFlow.getChildren().add(text);
                originalFlow.setStyle("-fx-padding: 2 5 2 5; -fx-background-color: #f8f9fa;");
            }

            if (i < transformedLines.length) {
                Text text = new Text(transformedLines[i]);
                text.setFont(monoFont);
                transformedFlow.getChildren().add(text);
                transformedFlow.setStyle("-fx-padding: 2 5 2 5; -fx-background-color: #f8f9fa;");
            }

            // Highlight differences
            if (i < originalLines.length && i < transformedLines.length) {
                if (!originalLines[i].equals(transformedLines[i])) {
                    originalFlow.setStyle(originalFlow.getStyle() + "; -fx-background-color: #f8d7da;");
                    transformedFlow.setStyle(transformedFlow.getStyle() + "; -fx-background-color: #d4edda;");
                }
            }

            grid.add(originalFlow, 0, i + 1);
            grid.add(transformedFlow, 1, i + 1);
        }

        diffContainer.getChildren().add(grid);
    }

    /**
     * Clear the diff viewer.
     */
    public void clear() {
        diffContainer.getChildren().clear();
        headerLabel.setText("No differences to display");
    }

    /**
     * Set the diff display mode.
     */
    public void setDiffMode(DiffMode mode) {
        this.diffMode = mode;
    }

    /**
     * Diff line types.
     */
    private enum DiffType {
        ADDITION("+"),
        DELETION("-"),
        CONTEXT(" ");

        final String symbol;

        DiffType(String symbol) {
            this.symbol = symbol;
        }
    }

    /**
     * Represents a single line in the diff.
     */
    private static class DiffLine {
        final DiffType type;
        final String content;
        final int lineNumber;

        DiffLine(DiffType type, String content, int lineNumber) {
            this.type = type;
            this.content = content;
            this.lineNumber = lineNumber;
        }
    }
}
