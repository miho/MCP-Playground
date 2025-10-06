package com.openrewrite.ui.components;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Syntax-highlighted code editor using RichTextFX.
 * Provides Java syntax highlighting and basic editing features.
 */
public class CodeEditor extends CodeArea {

    private static final String[] KEYWORDS = new String[] {
        "abstract", "assert", "boolean", "break", "byte",
        "case", "catch", "char", "class", "const",
        "continue", "default", "do", "double", "else",
        "enum", "extends", "final", "finally", "float",
        "for", "goto", "if", "implements", "import",
        "instanceof", "int", "interface", "long", "native",
        "new", "package", "private", "protected", "public",
        "return", "short", "static", "strictfp", "super",
        "switch", "synchronized", "this", "throw", "throws",
        "transient", "try", "void", "volatile", "while",
        "var", "record", "sealed", "permits", "non-sealed"
    };

    private static final String KEYWORD_PATTERN = "\\b(" + String.join("|", KEYWORDS) + ")\\b";
    private static final String PAREN_PATTERN = "\\(|\\)";
    private static final String BRACE_PATTERN = "\\{|\\}";
    private static final String BRACKET_PATTERN = "\\[|\\]";
    private static final String SEMICOLON_PATTERN = "\\;";
    private static final String STRING_PATTERN = "\"([^\"\\\\]|\\\\.)*\"";
    private static final String COMMENT_PATTERN = "//[^\n]*" + "|" + "/\\*(.|\\R)*?\\*/";
    private static final String ANNOTATION_PATTERN = "@\\w+";
    private static final String NUMBER_PATTERN = "\\b\\d+(\\.\\d+)?([eE][+-]?\\d+)?[fFdDlL]?\\b";

    private static final Pattern PATTERN = Pattern.compile(
        "(?<KEYWORD>" + KEYWORD_PATTERN + ")"
        + "|(?<PAREN>" + PAREN_PATTERN + ")"
        + "|(?<BRACE>" + BRACE_PATTERN + ")"
        + "|(?<BRACKET>" + BRACKET_PATTERN + ")"
        + "|(?<SEMICOLON>" + SEMICOLON_PATTERN + ")"
        + "|(?<STRING>" + STRING_PATTERN + ")"
        + "|(?<COMMENT>" + COMMENT_PATTERN + ")"
        + "|(?<ANNOTATION>" + ANNOTATION_PATTERN + ")"
        + "|(?<NUMBER>" + NUMBER_PATTERN + ")"
    );

    private final BooleanProperty editableProperty;

    public CodeEditor() {
        super();
        this.editableProperty = new SimpleBooleanProperty(true);

        // Configure editor
        setParagraphGraphicFactory(LineNumberFactory.get(this));
        getStyleClass().add("code-editor");
        setStyle("-fx-font-family: 'Consolas', 'Monaco', 'Courier New', monospace; -fx-font-size: 13px;");

        // Enable syntax highlighting with debouncing
        multiPlainChanges()
            .successionEnds(Duration.ofMillis(100))
            .subscribe(ignore -> {
                setStyleSpans(0, computeHighlighting(getText()));
            });

        // Bind editable property
        editableProperty.addListener((obs, oldVal, newVal) -> {
            setEditable(newVal);
            if (!newVal) {
                setStyle(getStyle() + "; -fx-opacity: 0.9;");
            } else {
                setStyle(getStyle().replace("; -fx-opacity: 0.9;", ""));
            }
        });

        // Add keyboard shortcuts
        addEventFilter(KeyEvent.KEY_PRESSED, this::handleKeyPress);

        // Apply initial highlighting
        setText("");
    }

    /**
     * Set whether the editor is editable.
     */
    public void setEditableMode(boolean editable) {
        editableProperty.set(editable);
    }

    public BooleanProperty editableModeProperty() {
        return editableProperty;
    }

    /**
     * Handle keyboard shortcuts.
     */
    private void handleKeyPress(KeyEvent event) {
        if (event.isControlDown()) {
            switch (event.getCode()) {
                case A:
                    selectAll();
                    event.consume();
                    break;
                case Z:
                    if (!event.isShiftDown()) {
                        undo();
                        event.consume();
                    }
                    break;
                case Y:
                    redo();
                    event.consume();
                    break;
                case SLASH:
                    // Toggle line comment
                    toggleLineComment();
                    event.consume();
                    break;
                default:
                    break;
            }
        } else if (event.getCode() == KeyCode.TAB) {
            if (!event.isShiftDown()) {
                insertText(getCaretPosition(), "    ");
                event.consume();
            }
        }
    }

    /**
     * Toggle line comment for current line.
     */
    private void toggleLineComment() {
        int paragraph = getCurrentParagraph();
        String line = getText(paragraph);

        if (line.trim().startsWith("//")) {
            // Remove comment
            String newLine = line.replaceFirst("\\s*//\\s?", "");
            replaceText(paragraph, 0, paragraph, line.length(), newLine);
        } else {
            // Add comment
            int firstNonWhitespace = 0;
            while (firstNonWhitespace < line.length() &&
                   Character.isWhitespace(line.charAt(firstNonWhitespace))) {
                firstNonWhitespace++;
            }
            insertText(paragraph, firstNonWhitespace, "// ");
        }
    }

    /**
     * Compute syntax highlighting for the given text.
     */
    private StyleSpans<Collection<String>> computeHighlighting(String text) {
        Matcher matcher = PATTERN.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();

        while (matcher.find()) {
            String styleClass =
                matcher.group("KEYWORD") != null ? "keyword" :
                matcher.group("PAREN") != null ? "paren" :
                matcher.group("BRACE") != null ? "brace" :
                matcher.group("BRACKET") != null ? "bracket" :
                matcher.group("SEMICOLON") != null ? "semicolon" :
                matcher.group("STRING") != null ? "string" :
                matcher.group("COMMENT") != null ? "comment" :
                matcher.group("ANNOTATION") != null ? "annotation" :
                matcher.group("NUMBER") != null ? "number" :
                null;

            assert styleClass != null;
            spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
            spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
            lastKwEnd = matcher.end();
        }

        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
        return spansBuilder.create();
    }

    /**
     * Set the editor text and apply syntax highlighting.
     */
    public void setText(String text) {
        replaceText(text);
        setStyleSpans(0, computeHighlighting(text));
    }

    /**
     * Get the current text content.
     */
    public String getText() {
        return super.getText();
    }

    /**
     * Clear the editor content.
     */
    @Override
    public void clear() {
        replaceText("");
    }
}
