package com.devicesim.ui;

import com.devicesim.model.Location;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.List;

/**
 * Right side panel showing all target locations in a table.
 * Displays location ID, coordinates, and status (Pending/Current/Visited).
 *
 * @since 1.0.0
 */
public class LocationListPanel extends VBox {

    private final TableView<LocationRow> tableView;
    private final ObservableList<LocationRow> data;
    private final Label summaryLabel;

    private int currentTargetIndex = -1;

    public LocationListPanel() {
        this.data = FXCollections.observableArrayList();
        this.tableView = new TableView<>(data);
        this.summaryLabel = new Label("Total: 0 | Visited: 0 | Remaining: 0");

        setupUI();
    }

    private void setupUI() {
        setSpacing(10);
        setPadding(new Insets(10));
        setStyle("-fx-background-color: #2b2b2b;");
        setPrefWidth(250);

        // Title
        Label title = new Label("Locations");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: white;");

        // Configure table
        tableView.setStyle("-fx-background-color: #1a1a1a;");
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // ID Column
        TableColumn<LocationRow, String> idColumn = new TableColumn<>("ID");
        idColumn.setCellValueFactory(data -> data.getValue().idProperty());
        idColumn.setPrefWidth(60);

        // X Column
        TableColumn<LocationRow, String> xColumn = new TableColumn<>("X");
        xColumn.setCellValueFactory(data -> data.getValue().xProperty());
        xColumn.setPrefWidth(60);

        // Y Column
        TableColumn<LocationRow, String> yColumn = new TableColumn<>("Y");
        yColumn.setCellValueFactory(data -> data.getValue().yProperty());
        yColumn.setPrefWidth(60);

        // Status Column
        TableColumn<LocationRow, String> statusColumn = new TableColumn<>("Status");
        statusColumn.setCellValueFactory(data -> data.getValue().statusProperty());
        statusColumn.setPrefWidth(80);

        tableView.getColumns().addAll(idColumn, xColumn, yColumn, statusColumn);

        // Row factory for color coding
        tableView.setRowFactory(tv -> new TableRow<LocationRow>() {
            @Override
            protected void updateItem(LocationRow item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setStyle("");
                } else {
                    switch (item.getStatus()) {
                        case "Current":
                            setStyle("-fx-background-color: rgba(78, 205, 196, 0.3);");
                            break;
                        case "Visited":
                            setStyle("-fx-background-color: rgba(39, 174, 96, 0.3);");
                            break;
                        default:
                            setStyle("-fx-background-color: rgba(60, 60, 60, 0.3);");
                            break;
                    }
                }
            }
        });

        // Summary section
        VBox summaryBox = new VBox(5);
        summaryBox.setPadding(new Insets(10));
        summaryBox.setStyle("-fx-background-color: #3a3a3a; -fx-background-radius: 5;");

        Label summaryTitle = new Label("Summary");
        summaryTitle.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: white;");

        summaryLabel.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11px;");

        summaryBox.getChildren().addAll(summaryTitle, summaryLabel);

        // Add to panel
        VBox.setVgrow(tableView, javafx.scene.layout.Priority.ALWAYS);
        getChildren().addAll(title, tableView, summaryBox);
    }

    /**
     * Update the location list.
     */
    public void updateLocations(List<Location> locations, int currentIndex) {
        Platform.runLater(() -> {
            this.currentTargetIndex = currentIndex;
            data.clear();

            for (int i = 0; i < locations.size(); i++) {
                Location loc = locations.get(i);
                String status;

                if (i == currentIndex) {
                    status = "Current";
                } else if (loc.isVisited()) {
                    status = "Visited";
                } else {
                    status = "Pending";
                }

                String id = extractLabelFromId(loc.getId());
                data.add(new LocationRow(id, loc.getX(), loc.getY(), status));
            }

            updateSummary(locations);

            // Auto-scroll to current target
            if (currentIndex >= 0 && currentIndex < data.size()) {
                tableView.scrollTo(currentIndex);
                tableView.getSelectionModel().select(currentIndex);
            }
        });
    }

    /**
     * Update summary statistics.
     */
    private void updateSummary(List<Location> locations) {
        int total = locations.size();
        long visited = locations.stream().filter(Location::isVisited).count();
        int remaining = total - (int) visited;

        summaryLabel.setText(String.format("Total: %d | Visited: %d | Remaining: %d",
                total, visited, remaining));
    }

    /**
     * Extract a simple label from location ID.
     */
    private String extractLabelFromId(String id) {
        if (id == null) {
            return "?";
        }

        // Extract row number from ID like "loc_2_12.34_56.78" -> "2"
        if (id.startsWith("loc_")) {
            String[] parts = id.split("_");
            if (parts.length > 1) {
                return parts[1];
            }
        }

        return id.length() > 8 ? id.substring(0, 8) : id;
    }

    /**
     * Inner class representing a row in the table.
     */
    public static class LocationRow {
        private final SimpleStringProperty id;
        private final SimpleStringProperty x;
        private final SimpleStringProperty y;
        private final SimpleStringProperty status;

        public LocationRow(String id, double x, double y, String status) {
            this.id = new SimpleStringProperty(id);
            this.x = new SimpleStringProperty(String.format("%.2f", x));
            this.y = new SimpleStringProperty(String.format("%.2f", y));
            this.status = new SimpleStringProperty(status);
        }

        public String getId() {
            return id.get();
        }

        public SimpleStringProperty idProperty() {
            return id;
        }

        public String getX() {
            return x.get();
        }

        public SimpleStringProperty xProperty() {
            return x;
        }

        public String getY() {
            return y.get();
        }

        public SimpleStringProperty yProperty() {
            return y;
        }

        public String getStatus() {
            return status.get();
        }

        public SimpleStringProperty statusProperty() {
            return status;
        }
    }
}
