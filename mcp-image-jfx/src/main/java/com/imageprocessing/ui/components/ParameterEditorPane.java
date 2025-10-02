package com.imageprocessing.ui.components;

import com.imageprocessing.ui.model.ToolInstance;
import com.imageprocessing.ui.model.ToolMetadata;
import com.imageprocessing.ui.model.WorkflowModel;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.*;

/**
 * Right panel for editing parameters of the selected tool.
 * Dynamically builds form based on tool parameter definitions.
 */
public class ParameterEditorPane extends VBox {

    private ToolInstance currentTool;
    private final ScrollPane scrollPane;
    private final VBox formContainer;
    private final Map<String, Control> parameterControls;
    private WorkflowModel workflowModel; // Reference to workflow for scanning output_keys

    public ParameterEditorPane() {
        this.parameterControls = new HashMap<>();

        // Header
        Label title = new Label("Parameter Editor");
        title.getStyleClass().add("parameter-title");

        // Form container
        formContainer = new VBox(10);
        formContainer.setPadding(new Insets(10));

        // Scroll pane
        scrollPane = new ScrollPane(formContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("parameter-scroll");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        // Initial placeholder
        Label placeholder = new Label("Select a tool to edit its parameters");
        placeholder.getStyleClass().add("parameter-placeholder");
        formContainer.getChildren().add(placeholder);

        // Layout
        this.setPadding(new Insets(10));
        this.setSpacing(10);
        this.getChildren().addAll(title, scrollPane);
        this.getStyleClass().add("parameter-editor-pane");
    }

    /**
     * Display parameters for the given tool instance.
     */
    public void editTool(ToolInstance tool) {
        this.currentTool = tool;
        parameterControls.clear();
        formContainer.getChildren().clear();

        if (tool == null) {
            Label placeholder = new Label("Select a tool to edit its parameters");
            placeholder.getStyleClass().add("parameter-placeholder");
            formContainer.getChildren().add(placeholder);
            return;
        }

        // Tool description
        Label description = new Label(tool.getDescription());
        description.setWrapText(true);
        description.getStyleClass().add("tool-description");
        formContainer.getChildren().add(description);

        // Separator
        formContainer.getChildren().add(new Separator());

        // Build parameter form
        for (ToolMetadata.ParameterDefinition param : tool.getMetadata().getParameters()) {
            Node paramControl = createParameterControl(param, tool);
            formContainer.getChildren().add(paramControl);
        }

        // Action buttons
        HBox buttons = createActionButtons();
        formContainer.getChildren().add(buttons);
    }

    private Node createParameterControl(ToolMetadata.ParameterDefinition param, ToolInstance tool) {
        VBox container = new VBox(5);
        container.getStyleClass().add("parameter-group");

        // Label
        Label label = new Label(param.getName() + (param.isRequired() ? " *" : ""));
        label.getStyleClass().add("parameter-label");

        // Description
        if (param.getDescription() != null && !param.getDescription().isEmpty()) {
            Label desc = new Label(param.getDescription());
            desc.getStyleClass().add("parameter-description");
            desc.setWrapText(true);
            container.getChildren().add(desc);
        }

        container.getChildren().add(label);

        // Control based on type
        Node controlNode = null;
        Object currentValue = tool.getParameter(param.getName());

        switch (param.getType()) {
            case STRING:
                controlNode = createTextField(param, currentValue);
                break;
            case INTEGER:
                controlNode = createIntegerField(param, currentValue);
                break;
            case DOUBLE:
            case FLOAT:
                controlNode = createDoubleField(param, currentValue);
                break;
            case BOOLEAN:
                controlNode = createCheckBox(param, currentValue);
                break;
            case IMAGE_PATH:
                controlNode = createImagePathField(param, currentValue);
                break;
            case RESULT_KEY:
                controlNode = createResultKeyComboBox(param, currentValue);
                break;
            case OUTPUT_PATH:
                controlNode = createOutputPathField(param, currentValue);
                break;
            case OUTPUT_KEY:
                controlNode = createOutputKeyField(param, currentValue);
                break;
            case ENUM:
                controlNode = createEnumComboBox(param, currentValue);
                break;
        }

        if (controlNode != null) {
            // Store the control for later access (extract from container if needed)
            if (controlNode instanceof Control) {
                parameterControls.put(param.getName(), (Control) controlNode);
            }
            container.getChildren().add(controlNode);
        }

        return container;
    }

    private TextField createTextField(ToolMetadata.ParameterDefinition param, Object currentValue) {
        TextField field = new TextField();
        if (currentValue != null) {
            field.setText(currentValue.toString());
        } else if (param.getDefaultValue() != null) {
            field.setText(param.getDefaultValue().toString());
        }
        field.setPromptText(param.getDescription());
        field.textProperty().addListener((obs, oldVal, newVal) -> {
            if (currentTool != null) {
                currentTool.setParameter(param.getName(), newVal.isEmpty() ? null : newVal);
            }
        });
        return field;
    }

    private HBox createIntegerField(ToolMetadata.ParameterDefinition param, Object currentValue) {
        Spinner<Integer> spinner = new Spinner<>(-99999, 99999, 0, 1);
        spinner.setEditable(true);
        if (currentValue instanceof Number) {
            spinner.getValueFactory().setValue(((Number) currentValue).intValue());
        } else if (param.getDefaultValue() instanceof Number) {
            spinner.getValueFactory().setValue(((Number) param.getDefaultValue()).intValue());
        }
        spinner.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (currentTool != null) {
                currentTool.setParameter(param.getName(), newVal);
            }
        });
        HBox.setHgrow(spinner, Priority.ALWAYS);
        return new HBox(spinner);
    }

    private TextField createDoubleField(ToolMetadata.ParameterDefinition param, Object currentValue) {
        TextField field = new TextField();
        if (currentValue != null) {
            field.setText(currentValue.toString());
        } else if (param.getDefaultValue() != null) {
            field.setText(param.getDefaultValue().toString());
        }
        field.setPromptText("0.0");

        // Validation
        field.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.isEmpty()) {
                try {
                    double value = Double.parseDouble(newVal);
                    field.setStyle("");
                    if (currentTool != null) {
                        currentTool.setParameter(param.getName(), value);
                    }
                } catch (NumberFormatException e) {
                    field.setStyle("-fx-border-color: red;");
                }
            } else {
                if (currentTool != null) {
                    currentTool.setParameter(param.getName(), null);
                }
            }
        });
        return field;
    }

    private CheckBox createCheckBox(ToolMetadata.ParameterDefinition param, Object currentValue) {
        CheckBox checkBox = new CheckBox();
        if (currentValue instanceof Boolean) {
            checkBox.setSelected((Boolean) currentValue);
        } else if (param.getDefaultValue() instanceof Boolean) {
            checkBox.setSelected((Boolean) param.getDefaultValue());
        }
        checkBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (currentTool != null) {
                currentTool.setParameter(param.getName(), newVal);
            }
        });
        return checkBox;
    }

    private HBox createImagePathField(ToolMetadata.ParameterDefinition param, Object currentValue) {
        TextField field = new TextField();
        if (currentValue != null) {
            field.setText(currentValue.toString());
        }
        field.setPromptText("Select image file...");
        HBox.setHgrow(field, Priority.ALWAYS);

        Button browseButton = new Button("Browse...");
        browseButton.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select Image");
            fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
            );
            File file = fileChooser.showOpenDialog(this.getScene().getWindow());
            if (file != null) {
                field.setText(file.getAbsolutePath());
                if (currentTool != null) {
                    currentTool.setParameter(param.getName(), file.getAbsolutePath());
                }
            }
        });

        field.textProperty().addListener((obs, oldVal, newVal) -> {
            if (currentTool != null) {
                currentTool.setParameter(param.getName(), newVal.isEmpty() ? null : newVal);
            }
        });

        return new HBox(5, field, browseButton);
    }

    private ComboBox<String> createResultKeyComboBox(ToolMetadata.ParameterDefinition param, Object currentValue) {
        ComboBox<String> comboBox = new ComboBox<>();
        comboBox.setEditable(true);
        comboBox.setPromptText("Select or enter cached result key");

        // Populate with output_key values from tools in the workflow
        Set<String> availableKeys = collectAvailableOutputKeys();
        comboBox.getItems().addAll(availableKeys);

        if (currentValue != null) {
            comboBox.setValue(currentValue.toString());
        }

        comboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (currentTool != null) {
                currentTool.setParameter(param.getName(), newVal);
            }
        });

        return comboBox;
    }

    private HBox createOutputPathField(ToolMetadata.ParameterDefinition param, Object currentValue) {
        TextField field = new TextField();
        if (currentValue != null) {
            field.setText(currentValue.toString());
        } else if (param.getDefaultValue() != null) {
            field.setText(param.getDefaultValue().toString());
        }
        field.setPromptText("Select output path...");
        HBox.setHgrow(field, Priority.ALWAYS);

        Button saveButton = new Button("Save As...");
        saveButton.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save Image As");
            fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("PNG Files", "*.png"),
                new FileChooser.ExtensionFilter("JPEG Files", "*.jpg", "*.jpeg"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
            );
            File file = fileChooser.showSaveDialog(this.getScene().getWindow());
            if (file != null) {
                field.setText(file.getAbsolutePath());
                if (currentTool != null) {
                    currentTool.setParameter(param.getName(), file.getAbsolutePath());
                }
            }
        });

        field.textProperty().addListener((obs, oldVal, newVal) -> {
            if (currentTool != null) {
                currentTool.setParameter(param.getName(), newVal.isEmpty() ? null : newVal);
            }
        });

        return new HBox(5, field, saveButton);
    }

    private TextField createOutputKeyField(ToolMetadata.ParameterDefinition param, Object currentValue) {
        TextField field = new TextField();
        if (currentValue != null) {
            field.setText(currentValue.toString());
        }
        field.setPromptText("Enter key to cache result");
        field.textProperty().addListener((obs, oldVal, newVal) -> {
            if (currentTool != null) {
                currentTool.setParameter(param.getName(), newVal.isEmpty() ? null : newVal);
                // Refresh result_key ComboBoxes when output_key changes
                refreshResultKeyComboBoxes();
            }
        });
        return field;
    }

    private ComboBox<String> createEnumComboBox(ToolMetadata.ParameterDefinition param, Object currentValue) {
        ComboBox<String> comboBox = new ComboBox<>();
        comboBox.getItems().addAll(param.getEnumValues());

        if (currentValue != null) {
            comboBox.setValue(currentValue.toString());
        } else if (param.getDefaultValue() != null) {
            comboBox.setValue(param.getDefaultValue().toString());
        }

        comboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (currentTool != null) {
                currentTool.setParameter(param.getName(), newVal);
            }
        });

        return comboBox;
    }

    private HBox createActionButtons() {
        Button applyButton = new Button("Apply");
        applyButton.setOnAction(e -> applyChanges());
        applyButton.getStyleClass().add("primary-button");

        Button resetButton = new Button("Reset");
        resetButton.setOnAction(e -> resetParameters());
        resetButton.getStyleClass().add("secondary-button");

        HBox buttons = new HBox(10, applyButton, resetButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.setPadding(new Insets(10, 0, 0, 0));
        return buttons;
    }

    private void applyChanges() {
        // Parameters are already updated via listeners
        // No confirmation dialog needed
    }

    private void resetParameters() {
        if (currentTool != null) {
            // Reset to default values
            for (ToolMetadata.ParameterDefinition param : currentTool.getMetadata().getParameters()) {
                currentTool.setParameter(param.getName(), param.getDefaultValue());
            }
            // Refresh the form
            editTool(currentTool);
        }
    }

    /**
     * Set the workflow model reference for scanning output_keys.
     */
    public void setWorkflowModel(WorkflowModel workflowModel) {
        this.workflowModel = workflowModel;
    }

    /**
     * Refresh available keys before editing a tool.
     * Called before editTool() to ensure ComboBoxes have latest data.
     */
    public void refreshAvailableKeys() {
        // This is called before editTool() to ensure the form is built with current data
        // The actual population happens in collectAvailableOutputKeys() when creating controls
    }

    /**
     * Collect all output keys from tools in the workflow.
     * This includes both 'output_key' and 'result_key' parameters (load_image uses result_key as output).
     */
    private Set<String> collectAvailableOutputKeys() {
        Set<String> keys = new HashSet<>();

        if (workflowModel != null) {
            for (ToolInstance tool : workflowModel.getToolInstances()) {
                // Check for output_key (most processing tools)
                Object outputKey = tool.getParameter("output_key");
                if (outputKey != null && !outputKey.toString().isEmpty()) {
                    keys.add(outputKey.toString());
                }

                // Check for result_key from load_image (it's both input and output depending on tool)
                // For load_image, result_key is the output
                if ("load_image".equals(tool.getName())) {
                    Object resultKey = tool.getParameter("result_key");
                    if (resultKey != null && !resultKey.toString().isEmpty()) {
                        keys.add(resultKey.toString());
                    }
                }
            }
        }

        return keys;
    }

    /**
     * Refresh all result_key ComboBoxes with current output_keys from workflow.
     */
    private void refreshResultKeyComboBoxes() {
        Set<String> availableKeys = collectAvailableOutputKeys();

        for (Map.Entry<String, Control> entry : parameterControls.entrySet()) {
            if (entry.getValue() instanceof ComboBox) {
                @SuppressWarnings("unchecked")
                ComboBox<String> comboBox = (ComboBox<String>) entry.getValue();
                // Only update result_key combo boxes (they are editable)
                if (comboBox.isEditable()) {
                    String currentValue = comboBox.getValue();
                    comboBox.getItems().clear();
                    comboBox.getItems().addAll(availableKeys);
                    // Restore current value
                    if (currentValue != null) {
                        comboBox.setValue(currentValue);
                    }
                }
            }
        }
    }
}
