package com.embeddedcc.ui.components;

import javafx.animation.*;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.List;
import java.util.function.Consumer;

/**
 * Interactive visualization of cache hotspots with click-to-navigate functionality.
 * Displays hotspots as colored bars with severity indicators.
 */
public class HotspotVisualization extends VBox {

    private final TableView<HotspotItem> hotspotTable = new TableView<>();
    private Consumer<Integer> onHotspotClick;

    public HotspotVisualization() {
        setSpacing(8);
        setPadding(new Insets(12));
        getStyleClass().add("hotspot-visualization");

        Label title = new Label("Performance Hotspots");
        title.getStyleClass().add("section-title");

        setupTable();

        getChildren().addAll(title, hotspotTable);
        VBox.setVgrow(hotspotTable, Priority.ALWAYS);
    }

    private void setupTable() {
        hotspotTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        hotspotTable.getStyleClass().add("hotspot-table");
        hotspotTable.setPrefHeight(300);

        // Severity indicator column (visual heat map)
        TableColumn<HotspotItem, Number> severityCol = new TableColumn<>("");
        severityCol.setPrefWidth(60);
        severityCol.setCellFactory(col -> new SeverityBarCell());
        severityCol.setCellValueFactory(data -> data.getValue().scoreProperty());
        severityCol.setSortable(false);

        // ID column
        TableColumn<HotspotItem, Number> idCol = new TableColumn<>("ID");
        idCol.setPrefWidth(50);
        idCol.setCellValueFactory(data -> data.getValue().idProperty());

        // Line number column
        TableColumn<HotspotItem, Number> lineCol = new TableColumn<>("Line");
        lineCol.setPrefWidth(60);
        lineCol.setCellValueFactory(data -> data.getValue().lineProperty());

        // Expression column
        TableColumn<HotspotItem, String> exprCol = new TableColumn<>("Expression");
        exprCol.setCellValueFactory(data -> data.getValue().expressionProperty());
        exprCol.setPrefWidth(200);

        // Misses column with custom cell factory for highlighting
        TableColumn<HotspotItem, Number> missesCol = new TableColumn<>("Misses");
        missesCol.setPrefWidth(80);
        missesCol.setCellValueFactory(data -> data.getValue().missesProperty());
        missesCol.setCellFactory(col -> new ColoredNumberCell());

        // Evictions column
        TableColumn<HotspotItem, Number> evictionsCol = new TableColumn<>("Evictions");
        evictionsCol.setPrefWidth(80);
        evictionsCol.setCellValueFactory(data -> data.getValue().evictionsProperty());
        evictionsCol.setCellFactory(col -> new ColoredNumberCell());

        // Score column
        TableColumn<HotspotItem, Number> scoreCol = new TableColumn<>("Score");
        scoreCol.setPrefWidth(80);
        scoreCol.setCellValueFactory(data -> data.getValue().scoreProperty());
        scoreCol.setCellFactory(col -> new ScoreCell());
        scoreCol.setSortType(TableColumn.SortType.DESCENDING);

        hotspotTable.getColumns().addAll(severityCol, idCol, lineCol, exprCol, missesCol, evictionsCol, scoreCol);
        hotspotTable.getSortOrder().add(scoreCol);

        // Custom row factory for hover effects and click handling
        hotspotTable.setRowFactory(tv -> {
            TableRow<HotspotItem> row = new TableRow<>() {
                @Override
                protected void updateItem(HotspotItem item, boolean empty) {
                    super.updateItem(item, empty);
                    getStyleClass().removeAll("hotspot-row-high", "hotspot-row-medium", "hotspot-row-low");

                    if (!empty && item != null) {
                        int score = item.getScore();
                        int maxScore = hotspotTable.getItems().stream()
                                .mapToInt(HotspotItem::getScore)
                                .max()
                                .orElse(1);

                        double ratio = maxScore > 0 ? (double) score / maxScore : 0;

                        if (ratio >= 0.66) {
                            getStyleClass().add("hotspot-row-high");
                        } else if (ratio >= 0.33) {
                            getStyleClass().add("hotspot-row-medium");
                        } else {
                            getStyleClass().add("hotspot-row-low");
                        }

                        // Add tooltip
                        setTooltip(new Tooltip(String.format(
                                "Click to jump to line %d\n%s\nMisses: %d | Evictions: %d | Score: %d",
                                item.getLine(),
                                item.getExpression(),
                                item.getMisses(),
                                item.getEvictions(),
                                item.getScore()
                        )));
                    }
                }
            };

            // Click handler
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && onHotspotClick != null) {
                    HotspotItem item = row.getItem();
                    if (item != null && item.getLine() > 0) {
                        // Pulse animation on click
                        pulseRow(row);
                        onHotspotClick.accept(item.getLine());
                    }
                }
            });

            return row;
        });

        hotspotTable.setPlaceholder(new Label("No hotspots detected. Run analysis to see results."));
    }

    /**
     * Pulse animation when clicking a row
     */
    private void pulseRow(TableRow<HotspotItem> row) {
        ScaleTransition st = new ScaleTransition(Duration.millis(150), row);
        st.setFromX(1.0);
        st.setFromY(1.0);
        st.setToX(1.03);
        st.setToY(1.03);
        st.setAutoReverse(true);
        st.setCycleCount(2);
        st.play();
    }

    /**
     * Custom cell for severity visualization (colored bar)
     */
    private static class SeverityBarCell extends TableCell<HotspotItem, Number> {
        private final StackPane container = new StackPane();
        private final Rectangle bar = new Rectangle();

        public SeverityBarCell() {
            container.setAlignment(Pos.CENTER_LEFT);
            container.setPadding(new Insets(2));

            bar.setHeight(16);
            bar.setArcWidth(4);
            bar.setArcHeight(4);

            container.getChildren().add(bar);
            setGraphic(container);
        }

        @Override
        protected void updateItem(Number score, boolean empty) {
            super.updateItem(score, empty);

            if (empty || score == null) {
                bar.setWidth(0);
                return;
            }

            TableView<HotspotItem> table = getTableView();
            int maxScore = table.getItems().stream()
                    .mapToInt(HotspotItem::getScore)
                    .max()
                    .orElse(1);

            double ratio = maxScore > 0 ? score.doubleValue() / maxScore : 0;

            // Animate bar width
            double targetWidth = 40 * ratio;
            Timeline widthAnimation = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(bar.widthProperty(), bar.getWidth())),
                    new KeyFrame(Duration.millis(500), new KeyValue(bar.widthProperty(), targetWidth))
            );
            widthAnimation.play();

            // Color based on severity
            bar.getStyleClass().removeAll("severity-bar-high", "severity-bar-medium", "severity-bar-low");
            if (ratio >= 0.66) {
                bar.getStyleClass().add("severity-bar-high");
            } else if (ratio >= 0.33) {
                bar.getStyleClass().add("severity-bar-medium");
            } else {
                bar.getStyleClass().add("severity-bar-low");
            }
        }
    }

    /**
     * Custom cell for colored numbers
     */
    private static class ColoredNumberCell extends TableCell<HotspotItem, Number> {
        @Override
        protected void updateItem(Number value, boolean empty) {
            super.updateItem(value, empty);

            if (empty || value == null) {
                setText(null);
                setStyle("");
                return;
            }

            setText(String.format("%,d", value.intValue()));

            // Color intensity based on value
            if (value.intValue() > 1000) {
                setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;");
            } else if (value.intValue() > 500) {
                setStyle("-fx-text-fill: #ea580c; -fx-font-weight: bold;");
            } else if (value.intValue() > 100) {
                setStyle("-fx-text-fill: #f59e0b;");
            } else {
                setStyle("");
            }
        }
    }

    /**
     * Custom cell for score with badge-like appearance
     */
    private static class ScoreCell extends TableCell<HotspotItem, Number> {
        private final Label badge = new Label();

        public ScoreCell() {
            badge.getStyleClass().add("score-badge");
            setGraphic(badge);
        }

        @Override
        protected void updateItem(Number score, boolean empty) {
            super.updateItem(score, empty);

            if (empty || score == null) {
                badge.setText("");
                badge.getStyleClass().removeAll("score-high", "score-medium", "score-low");
                return;
            }

            badge.setText(String.format("%,d", score.intValue()));

            TableView<HotspotItem> table = getTableView();
            int maxScore = table.getItems().stream()
                    .mapToInt(HotspotItem::getScore)
                    .max()
                    .orElse(1);

            double ratio = maxScore > 0 ? score.doubleValue() / maxScore : 0;

            badge.getStyleClass().removeAll("score-high", "score-medium", "score-low");
            if (ratio >= 0.66) {
                badge.getStyleClass().add("score-high");
            } else if (ratio >= 0.33) {
                badge.getStyleClass().add("score-medium");
            } else {
                badge.getStyleClass().add("score-low");
            }
        }
    }

    /**
     * Sets the hotspot data to display
     */
    public void setHotspots(List<HotspotItem> hotspots) {
        hotspotTable.getItems().setAll(hotspots);
        hotspotTable.sort();
    }

    /**
     * Sets the callback for when a hotspot is clicked
     */
    public void setOnHotspotClick(Consumer<Integer> callback) {
        this.onHotspotClick = callback;
    }

    /**
     * Clears all hotspots
     */
    public void clear() {
        hotspotTable.getItems().clear();
    }

    /**
     * Data class for hotspot items
     */
    public static class HotspotItem {
        private final SimpleIntegerProperty id;
        private final SimpleIntegerProperty line;
        private final SimpleStringProperty expression;
        private final SimpleIntegerProperty misses;
        private final SimpleIntegerProperty evictions;
        private final SimpleIntegerProperty score;

        public HotspotItem(int id, int line, String expression, int misses, int evictions, int score) {
            this.id = new SimpleIntegerProperty(id);
            this.line = new SimpleIntegerProperty(line);
            this.expression = new SimpleStringProperty(expression);
            this.misses = new SimpleIntegerProperty(misses);
            this.evictions = new SimpleIntegerProperty(evictions);
            this.score = new SimpleIntegerProperty(score);
        }

        public int getId() { return id.get(); }
        public SimpleIntegerProperty idProperty() { return id; }

        public int getLine() { return line.get(); }
        public SimpleIntegerProperty lineProperty() { return line; }

        public String getExpression() { return expression.get(); }
        public SimpleStringProperty expressionProperty() { return expression; }

        public int getMisses() { return misses.get(); }
        public SimpleIntegerProperty missesProperty() { return misses; }

        public int getEvictions() { return evictions.get(); }
        public SimpleIntegerProperty evictionsProperty() { return evictions; }

        public int getScore() { return score.get(); }
        public SimpleIntegerProperty scoreProperty() { return score; }
    }
}
