package com.embeddedcc.ui.components;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.*;
import java.util.function.IntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enhanced CodeView with modern visual indicators, heat map gutter, and smooth animations.
 * Provides a professional code viewing experience with performance hotspot visualization.
 */
public class EnhancedCodeView extends BorderPane {

    private final CodeArea codeArea = new CodeArea();
    private Set<Integer> highlightedLines = Set.of();
    private final Set<Integer> hotspotLines = new HashSet<>();
    private final Map<Integer, String> hotspotStyles = new HashMap<>();
    private final Map<Integer, HotspotMetrics> hotspotMetrics = new HashMap<>();
    private final Map<Integer, Timeline> pulseAnimations = new HashMap<>();

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

    public EnhancedCodeView() {
        codeArea.setWrapText(false);
        codeArea.getStyleClass().add("code-area");

        // Enhanced line number factory with heat map gutter
        codeArea.setParagraphGraphicFactory(createEnhancedLineNumberFactory());

        codeArea.textProperty().addListener((obs, oldText, newText) -> applyHighlight(newText));

        VirtualizedScrollPane<CodeArea> scrollPane = new VirtualizedScrollPane<>(codeArea);
        scrollPane.getStyleClass().add("enhanced-code-scroll");
        setCenter(scrollPane);
    }

    /**
     * Creates an enhanced line number factory with heat map indicators
     */
    private IntFunction<Node> createEnhancedLineNumberFactory() {
        IntFunction<Node> baseFactory = LineNumberFactory.get(codeArea);

        return line -> {
            HBox container = new HBox(4);
            container.setAlignment(Pos.CENTER_RIGHT);
            container.setPadding(new Insets(0, 8, 0, 4));
            container.getStyleClass().add("line-number-container");

            // Heat map indicator (left side)
            Rectangle heatIndicator = new Rectangle(6, 14);
            heatIndicator.getStyleClass().add("heat-indicator");
            heatIndicator.setArcWidth(3);
            heatIndicator.setArcHeight(3);

            int lineNumber = line + 1;

            // Configure heat indicator based on hotspot data
            if (hotspotMetrics.containsKey(lineNumber)) {
                HotspotMetrics metrics = hotspotMetrics.get(lineNumber);
                heatIndicator.setFill(getSeverityColor(metrics.severity));
                heatIndicator.setOpacity(0.9);

                // Add tooltip with detailed metrics
                Tooltip tooltip = createMetricsTooltip(metrics);
                Tooltip.install(heatIndicator, tooltip);

                // Add pulse animation for high severity
                if (metrics.severity >= 0.66) {
                    addPulseAnimation(heatIndicator, lineNumber);
                }
            } else {
                heatIndicator.setFill(Color.TRANSPARENT);
            }

            // Line number label
            Node lineNumberLabel = baseFactory.apply(line);
            if (lineNumberLabel instanceof Label) {
                ((Label) lineNumberLabel).setMinWidth(40);
            }

            // Hotspot severity indicator (right side - small circle)
            Circle severityDot = new Circle(3);
            severityDot.getStyleClass().add("severity-dot");

            if (hotspotStyles.containsKey(lineNumber)) {
                String severity = hotspotStyles.get(lineNumber);
                severityDot.getStyleClass().add(severity + "-dot");
                severityDot.setOpacity(0.8);
            } else {
                severityDot.setOpacity(0);
            }

            container.getChildren().addAll(heatIndicator, lineNumberLabel, severityDot);
            return container;
        };
    }

    /**
     * Creates a detailed tooltip showing cache performance metrics
     */
    private Tooltip createMetricsTooltip(HotspotMetrics metrics) {
        StringBuilder sb = new StringBuilder();
        sb.append("Line ").append(metrics.line).append(" Performance\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━\n");
        sb.append(String.format("Cache Misses: %,d\n", metrics.misses));
        sb.append(String.format("Evictions: %,d\n", metrics.evictions));
        sb.append(String.format("Severity Score: %.1f%%\n", metrics.severity * 100));
        if (metrics.expression != null && !metrics.expression.isEmpty()) {
            sb.append("\nExpression: ").append(metrics.expression);
        }

        Tooltip tooltip = new Tooltip(sb.toString());
        tooltip.setShowDelay(Duration.millis(300));
        tooltip.getStyleClass().add("metrics-tooltip");
        return tooltip;
    }

    /**
     * Adds a subtle pulse animation to high-severity indicators
     */
    private void addPulseAnimation(Rectangle indicator, int lineNumber) {
        Timeline timeline = pulseAnimations.get(lineNumber);
        if (timeline != null) {
            timeline.stop();
        }

        timeline = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(indicator.opacityProperty(), 0.9, Interpolator.EASE_BOTH)),
            new KeyFrame(Duration.seconds(1.5),
                new KeyValue(indicator.opacityProperty(), 0.4, Interpolator.EASE_BOTH))
        );
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.setAutoReverse(true);
        timeline.play();

        pulseAnimations.put(lineNumber, timeline);
    }

    /**
     * Returns color gradient based on severity (0.0 - 1.0)
     */
    private Color getSeverityColor(double severity) {
        if (severity >= 0.8) {
            // Critical - Deep Red
            return Color.rgb(220, 38, 38);
        } else if (severity >= 0.6) {
            // High - Orange Red
            return Color.rgb(234, 88, 12);
        } else if (severity >= 0.4) {
            // Medium - Orange
            return Color.rgb(245, 158, 11);
        } else if (severity >= 0.2) {
            // Low - Yellow
            return Color.rgb(250, 204, 21);
        } else {
            // Minimal - Green
            return Color.rgb(34, 197, 94);
        }
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

    /**
     * Enhanced hotspot highlighting with metrics data
     */
    public void highlightHotspotsWithMetrics(Map<Integer, HotspotMetrics> metricsMap) {
        hotspotLines.clear();
        hotspotStyles.clear();
        hotspotMetrics.clear();

        // Stop all existing animations
        pulseAnimations.values().forEach(Timeline::stop);
        pulseAnimations.clear();

        if (metricsMap != null) {
            metricsMap.forEach((line, metrics) -> {
                if (line != null && line > 0) {
                    hotspotLines.add(line);
                    hotspotMetrics.put(line, metrics);

                    // Determine style class based on severity
                    String styleClass = classifySeverity(metrics.severity);
                    hotspotStyles.put(line, styleClass);
                }
            });
        }

        applyStyles();

        // Force refresh of paragraph graphics to update heat indicators
        Platform.runLater(() -> {
            codeArea.setParagraphGraphicFactory(createEnhancedLineNumberFactory());
        });
    }

    /**
     * Backward compatible hotspot highlighting
     */
    public void highlightHotspots(Map<Integer, String> severityMap) {
        Map<Integer, HotspotMetrics> metricsMap = new HashMap<>();

        if (severityMap != null) {
            severityMap.forEach((line, styleClass) -> {
                double severity = parseSeverityFromStyleClass(styleClass);
                metricsMap.put(line, new HotspotMetrics(line, 0, 0, severity, ""));
            });
        }

        highlightHotspotsWithMetrics(metricsMap);
    }

    private double parseSeverityFromStyleClass(String styleClass) {
        return switch (styleClass) {
            case "hotspot-high" -> 0.8;
            case "hotspot-medium" -> 0.5;
            case "hotspot-low" -> 0.2;
            default -> 0.0;
        };
    }

    private String classifySeverity(double severity) {
        if (severity >= 0.66) {
            return "hotspot-high";
        } else if (severity >= 0.33) {
            return "hotspot-medium";
        }
        return "hotspot-low";
    }

    /**
     * Animates focus to a specific line with smooth scrolling
     */
    public void focusLine(int line) {
        if (line < 1) {
            return;
        }

        int targetParagraph = Math.min(line - 1, Math.max(codeArea.getParagraphs().size() - 1, 0));

        // Smooth scroll animation
        codeArea.moveTo(targetParagraph, 0);
        codeArea.requestFollowCaret();

        // Highlight the focused line temporarily
        Platform.runLater(() -> {
            highlightFocusedLineTemporarily(targetParagraph);
        });
    }

    /**
     * Temporarily highlights a line when jumping to it
     */
    private void highlightFocusedLineTemporarily(int paragraph) {
        // Store original style
        Collection<String> originalStyles = codeArea.getParagraph(paragraph).getParagraphStyle();

        // Create new styles with focus
        Collection<String> focusStyles = new ArrayList<>();
        if (originalStyles != null) {
            focusStyles.addAll(originalStyles);
        }
        focusStyles.add("focused-line");

        codeArea.setParagraphStyle(paragraph, focusStyles);

        // Remove focus highlight after 2 seconds
        Timeline fadeOut = new Timeline(
            new KeyFrame(Duration.seconds(2), e -> {
                codeArea.setParagraphStyle(paragraph, originalStyles != null ? originalStyles : Collections.emptyList());
            })
        );
        fadeOut.play();
    }

    public void clearHighlights() {
        highlightedLines = Set.of();
        hotspotLines.clear();
        hotspotStyles.clear();
        hotspotMetrics.clear();

        // Stop all animations
        pulseAnimations.values().forEach(Timeline::stop);
        pulseAnimations.clear();

        applyStyles();

        // Reset gutter
        Platform.runLater(() -> {
            codeArea.setParagraphGraphicFactory(createEnhancedLineNumberFactory());
        });
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

    /**
     * Data class for hotspot performance metrics
     */
    public static class HotspotMetrics {
        public final int line;
        public final int misses;
        public final int evictions;
        public final double severity; // 0.0 to 1.0
        public final String expression;

        public HotspotMetrics(int line, int misses, int evictions, double severity, String expression) {
            this.line = line;
            this.misses = misses;
            this.evictions = evictions;
            this.severity = Math.max(0.0, Math.min(1.0, severity)); // Clamp to [0, 1]
            this.expression = expression;
        }
    }
}
