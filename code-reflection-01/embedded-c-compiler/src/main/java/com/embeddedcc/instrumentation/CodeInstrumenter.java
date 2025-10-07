package com.embeddedcc.instrumentation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Injects TRACE_LOAD/TRACE_STORE statements into C source code.
 */
public class CodeInstrumenter {

    public InstrumentedProgram instrument(String source,
                                          List<InstrumentationPoint> points) {
        if (points == null || points.isEmpty()) {
            return new InstrumentedProgram(source, List.of(), Map.of());
        }

        Map<Integer, List<InstrumentationPoint>> groupedByLine = new HashMap<>();
        Map<Integer, InstrumentationPoint> idLookup = new HashMap<>();

        for (InstrumentationPoint point : points) {
            groupedByLine
                    .computeIfAbsent(point.getAccess().getLine(), key -> new ArrayList<>())
                    .add(point);
            idLookup.put(point.getId(), point);
        }

        String[] lines = source.split("\\R", -1);
        String lineSeparator = detectSeparator(source);
        List<String> outputLines = new ArrayList<>(lines.length + points.size());

        for (int i = 0; i < lines.length; i++) {
            int lineNumber = i + 1;
            String line = lines[i];
            List<InstrumentationPoint> insertions = groupedByLine.get(lineNumber);

            if (insertions != null) {
                String indent = leadingWhitespace(line);
                for (InstrumentationPoint insertion : insertions) {
                    outputLines.add(indent + buildInstrumentationStatement(insertion));
                }
            }

            outputLines.add(line);
        }

        String instrumented = String.join(lineSeparator, outputLines);
        instrumented = ensureInclude(instrumented);

        return new InstrumentedProgram(instrumented, points, idLookup);
    }

    private static String buildInstrumentationStatement(InstrumentationPoint point) {
        ArrayAccess access = point.getAccess();
        String macro = switch (access.getAccessType()) {
            case STORE -> "TRACE_STORE";
            case LOAD, UNKNOWN -> "TRACE_LOAD";
        };

        String expression = access.getExpression();
        String pointerExpr = "&(" + expression + ")";
        String sizeExpr = "sizeof(" + expression + ")";
        String label = escapeForCString(expression);

        return macro + "(" + pointerExpr + ", " + sizeExpr + ", "
                + point.getId() + ", " + access.getLine() + ", \"" + label + "\");";
    }

    private static String leadingWhitespace(String line) {
        int i = 0;
        while (i < line.length() && Character.isWhitespace(line.charAt(i))) {
            i++;
        }
        return line.substring(0, i);
    }

    private static String detectSeparator(String source) {
        int index = source.indexOf('\n');
        if (index > 0 && source.charAt(index - 1) == '\r') {
            return "\r\n";
        }
        return "\n";
    }

    private static String ensureInclude(String source) {
        if (source.contains("instrumentation.h")) {
            return source;
        }

        String[] lines = source.split("\\R", -1);
        String separator = detectSeparator(source);
        List<String> updatedLines = new ArrayList<>();
        boolean inserted = false;

        int insertPos = -1;
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (trimmed.startsWith("#include")) {
                insertPos = i;
            } else if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                break;
            }
        }

        if (insertPos >= 0) {
            for (int i = 0; i <= insertPos; i++) {
                updatedLines.add(lines[i]);
            }
            updatedLines.add("#include \"instrumentation.h\"");
            inserted = true;
            for (int i = insertPos + 1; i < lines.length; i++) {
                updatedLines.add(lines[i]);
            }
        }

        if (!inserted) {
            updatedLines.add("#include \"instrumentation.h\"");
            for (String line : lines) {
                updatedLines.add(line);
            }
        }

        return String.join(separator, updatedLines);
    }

    private static String escapeForCString(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') {
                sb.append('\\');
            }
            if (c == '\n' || c == '\r') {
                sb.append(' ');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}

