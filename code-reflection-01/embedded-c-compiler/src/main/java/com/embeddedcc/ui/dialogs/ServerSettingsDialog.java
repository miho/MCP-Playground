package com.embeddedcc.ui.dialogs;

import com.embeddedcc.ui.server.ServerConfig;
import com.embeddedcc.ui.server.ServerMode;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

/**
 * Simple dialog for configuring MCP server settings.
 */
public class ServerSettingsDialog extends Dialog<ServerConfig> {

    private final ToggleGroup modeGroup = new ToggleGroup();
    private final TextField portField = new TextField("8085");
    private ServerConfig pendingResult;

    public ServerSettingsDialog(ServerConfig currentConfig) {
        setTitle("MCP Server Settings");
        setHeaderText("Configure embedded MCP server");

        ButtonType applyButtonType = new ButtonType("Apply", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(applyButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setAlignment(Pos.CENTER_LEFT);
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(15));

        RadioButton stdioButton = new RadioButton("STDIO Mode");
        stdioButton.setToggleGroup(modeGroup);
        stdioButton.setUserData(ServerMode.STDIO);

        RadioButton httpButton = new RadioButton("HTTP Mode");
        httpButton.setToggleGroup(modeGroup);
        httpButton.setUserData(ServerMode.HTTP);

        if (currentConfig.mode() == ServerMode.HTTP) {
            modeGroup.selectToggle(httpButton);
        } else {
            modeGroup.selectToggle(stdioButton);
        }

        portField.setText(Integer.toString(currentConfig.port()));

        grid.add(new Label("Transport:"), 0, 0);
        grid.add(stdioButton, 1, 0);
        grid.add(httpButton, 1, 1);
        grid.add(new Label("HTTP Port:"), 0, 2);
        grid.add(portField, 1, 2);

        getDialogPane().setContent(grid);

        Button applyButton = (Button) getDialogPane().lookupButton(applyButtonType);
        applyButton.disableProperty().bind(modeGroup.selectedToggleProperty().isNull());

        modeGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle != null) {
                ServerMode mode = (ServerMode) newToggle.getUserData();
                portField.setDisable(mode != ServerMode.HTTP);
            }
        });
        portField.setDisable(modeGroup.getSelectedToggle() != null
                && modeGroup.getSelectedToggle().getUserData() != ServerMode.HTTP);

        applyButton.addEventFilter(ActionEvent.ACTION, event -> {
            pendingResult = null;
            ServerMode mode = (ServerMode) modeGroup.getSelectedToggle().getUserData();
            int port = currentConfig.port();
            if (mode == ServerMode.HTTP) {
                try {
                    port = Integer.parseInt(portField.getText().trim());
                } catch (NumberFormatException e) {
                    showError("Invalid port", "Please enter a valid integer between 1 and 65535.");
                    event.consume();
                    return;
                }
            }
            try {
                pendingResult = new ServerConfig(mode, port);
            } catch (IllegalArgumentException e) {
                showError("Invalid configuration", e.getMessage());
                event.consume();
            }
        });

        setResultConverter(button -> button == applyButtonType ? pendingResult : null);
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.showAndWait();
    }
}
