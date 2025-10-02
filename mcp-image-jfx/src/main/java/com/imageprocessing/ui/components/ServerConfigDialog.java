package com.imageprocessing.ui.components;

import com.imageprocessing.server.McpConfig;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/**
 * Dialog for configuring embedded MCP server settings.
 * Note: In the new architecture, the server is embedded and starts automatically.
 * This dialog now only shows current configuration information.
 */
public class ServerConfigDialog extends Dialog<McpConfig> {

    private final RadioButton httpModeRadio;
    private final RadioButton stdioModeRadio;
    private final Spinner<Integer> portSpinner;

    public ServerConfigDialog(McpConfig currentConfig) {
        setTitle("MCP Server Configuration");
        setHeaderText("Configure MCP Server Launch Settings");

        // Create the dialog layout
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        // Transport mode selection
        Label modeLabel = new Label("Transport Mode:");
        ToggleGroup modeGroup = new ToggleGroup();

        httpModeRadio = new RadioButton("HTTP (Recommended)");
        httpModeRadio.setToggleGroup(modeGroup);
        httpModeRadio.setSelected(currentConfig.getTransportMode() == McpConfig.TransportMode.HTTP);

        stdioModeRadio = new RadioButton("Stdio");
        stdioModeRadio.setToggleGroup(modeGroup);
        stdioModeRadio.setSelected(currentConfig.getTransportMode() == McpConfig.TransportMode.STDIO);

        VBox modeBox = new VBox(5, httpModeRadio, stdioModeRadio);
        grid.add(modeLabel, 0, 0);
        grid.add(modeBox, 1, 0);

        // HTTP port configuration
        Label portLabel = new Label("HTTP Port:");
        portSpinner = new Spinner<>(1024, 65535, currentConfig.getHttpPort());
        portSpinner.setEditable(true);
        portSpinner.disableProperty().bind(stdioModeRadio.selectedProperty());
        grid.add(portLabel, 0, 1);
        grid.add(portSpinner, 1, 1);

        // Help text
        Label helpText = new Label(
            "HTTP Mode: Easier debugging, better error messages\n" +
            "Stdio Mode: Standard MCP transport for production\n\n" +
            "Note: Server is embedded and starts automatically with the application.\n" +
            "Configuration changes require restarting the application."
        );
        helpText.setStyle("-fx-font-size: 11px; -fx-text-fill: #7f8c8d;");
        helpText.setWrapText(true);
        helpText.setPadding(new Insets(10, 0, 0, 0));
        grid.add(helpText, 0, 2, 2, 1);

        getDialogPane().setContent(grid);

        // Add buttons
        ButtonType okButton = ButtonType.OK;
        ButtonType cancelButton = ButtonType.CANCEL;
        getDialogPane().getButtonTypes().addAll(okButton, cancelButton);

        // Convert result to McpConfig
        setResultConverter(dialogButton -> {
            if (dialogButton == okButton) {
                McpConfig.TransportMode mode = httpModeRadio.isSelected()
                    ? McpConfig.TransportMode.HTTP
                    : McpConfig.TransportMode.STDIO;

                return McpConfig.builder()
                    .transportMode(mode)
                    .httpPort(portSpinner.getValue())
                    .captureServerLogs(true)
                    .build();
            }
            return null;
        });
    }
}
