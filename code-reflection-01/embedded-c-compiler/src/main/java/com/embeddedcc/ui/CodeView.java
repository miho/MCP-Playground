package com.embeddedcc.ui;

import javafx.scene.layout.BorderPane;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CodeView extends BorderPane {

    private final CodeArea codeArea = new CodeArea();
    private Set<Integer> highlightedLines = Set.of();
    private final Set<Integer> hotspotLines = new HashSet<>();
    private final java.util.Map<Integer, String> hotspotStyles = new java.util.HashMap<>();

    private static final String[] KEYWORDS = {
            "if", "else", "for", "while", "do", "switch", "case", "default", "break",
            "continue", "return", "sizeof", "struct", "typedef", "static", "const",
            "volatile", "enum", "goto"
    };

    private static final String[] TYPES = {
            "int", "long", "short", "float", "double", "char", "void", "bool", "_Bool",
            "unsigned", "signed", "size_t"
    };

    private static final Pattern HIGHLIGHT_PATTERN = Pattern.compile(
            "(?<KEYWORD>\\b(" + String.join("|", KEYWORDS) + ")\\b)"
                    + "|(?<TYPE>\\b(" + String.join("|", TYPES) + ")\\b)"
                    + "|(?<PREPROCESSOR>^\\s*#\\w+)"
                    + "|(?<NUMBER>\\b\\d+(?:\\.\\d+)?\\b)"
                    + "|(?<STRING>\"([^\\\"\\n]|\\\\.)*\")"
                    + "|(?<CHAR>'([^\\'\\n]|\\\\.)*')"
                    + "|(?<COMMENT>//[^\\n]*|/\\*(.|\\R)*?\\*/)",
            Pattern.MULTILINE
    );

    public CodeView() {
        codeArea.setWrapText(false);
        codeArea.getStyleClass().add("code-area");
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        codeArea.textProperty().addListener((obs, oldText, newText) -> applyHighlight(newText));
        setCenter(new VirtualizedScrollPane<>(codeArea));
    }

    public void setCode(String code) {
        String text = code == null ? "" : code;
        codeArea.replaceText(text);
        applyHighlight(text);
    }

    public String getCode() {
        return codeArea.getText();
    }

    public void highlightLines(Set<Integer> lines) {
        if (lines == null) {
            lines = Collections.emptySet();
        }
        highlightedLines = new HashSet<>(lines);
        applyStyles();
    }

    public void focusLine(int line) {
        if (line < 1) {
            return;
        }
        int targetParagraph = Math.min(line - 1, Math.max(codeArea.getParagraphs().size() - 1, 0));
        codeArea.moveTo(targetParagraph, 0);
        codeArea.requestFollowCaret();
    }

    public void setEditable(boolean editable) {
        codeArea.setEditable(editable);
    }

    public CodeArea getCodeArea() {
        return codeArea;
    }

    public void setDarkTheme(boolean darkTheme) {
        codeArea.getStyleClass().removeAll("code-area-dark", "code-area-light");
        codeArea.getStyleClass().add(darkTheme ? "code-area-dark" : "code-area-light");
    }

    public void highlightHotspots(Map<Integer, String> severityMap) {
        hotspotLines.clear();
        hotspotStyles.clear();
        if (severityMap != null) {
            severityMap.forEach((line, style) -> {
                if (line != null && line > 0) {
                    hotspotLines.add(line);
                    hotspotStyles.put(line, style);
                }
            });
        }
        applyStyles();
    }

    public void clearHighlights() {
        highlightedLines = Set.of();
        hotspotLines.clear();
        hotspotStyles.clear();
        applyStyles();
    }

    private void applyStyles() {
        int paragraphCount = codeArea.getParagraphs().size();
        for (int i = 0; i < paragraphCount; i++) {
            int lineNumber = i + 1;
            List<String> styles = new ArrayList<>();
            if (hotspotLines.contains(lineNumber)) {
                String styleClass = hotspotStyles.getOrDefault(lineNumber, "hotspot-low");
                styles.add("hotspot");
                styles.add(styleClass);
            } else if (highlightedLines.contains(lineNumber)) {
                styles.add("miss-line");
            }
            codeArea.setParagraphStyle(i, styles);
        }
    }

    private void applyHighlight(String text) {
        StyleSpans<Collection<String>> spans = computeHighlighting(text);
        codeArea.setStyleSpans(0, spans);
        applyStyles();
    }

    private StyleSpans<Collection<String>> computeHighlighting(String text) {
        Matcher matcher = HIGHLIGHT_PATTERN.matcher(text);
        int lastKwEnd = 0;
        int totalLength = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        while (matcher.find()) {
            int gapLength = matcher.start() - lastKwEnd;
            if (gapLength > 0) {
                spansBuilder.add(Collections.emptyList(), gapLength);
                totalLength += gapLength;
            }
            Collection<String> styleClass;
            if (matcher.group("KEYWORD") != null) {
                styleClass = Collections.singleton("keyword");
            } else if (matcher.group("TYPE") != null) {
                styleClass = Collections.singleton("type");
            } else if (matcher.group("PREPROCESSOR") != null) {
                styleClass = Collections.singleton("preprocessor");
            } else if (matcher.group("NUMBER") != null) {
                styleClass = Collections.singleton("number");
            } else if (matcher.group("STRING") != null) {
                styleClass = Collections.singleton("string");
            } else if (matcher.group("CHAR") != null) {
                styleClass = Collections.singleton("char");
            } else if (matcher.group("COMMENT") != null) {
                styleClass = Collections.singleton("comment");
            } else {
                styleClass = Collections.emptyList();
            }
            int matchLength = matcher.end() - matcher.start();
            if (matchLength > 0) {
                spansBuilder.add(styleClass, matchLength);
                totalLength += matchLength;
            }
            lastKwEnd = matcher.end();
        }
        int remaining = text.length() - lastKwEnd;
        if (remaining > 0) {
            spansBuilder.add(Collections.emptyList(), remaining);
            totalLength += remaining;
        }
        if (totalLength == 0) {
            return StyleSpans.singleton(Collections.emptyList(), 0);
        }
        return spansBuilder.create();
    }
}
