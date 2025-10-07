package com.embeddedcc.instrumentation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Performs lightweight parsing to find array access expressions in C code.
 */
public class ArrayAccessDetector {

    private static final Pattern ARRAY_PATTERN = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)(\\s*(\\[[^\\]]+])+)");
    private static final java.util.Set<String> TYPE_TOKENS = java.util.Set.of(
            "const", "volatile", "unsigned", "signed", "short", "long",
            "int", "float", "double", "char", "bool", "_Bool", "size_t",
            "struct", "enum", "typedef", "static", "extern", "register",
            "auto", "void"
    );

    public List<ArrayAccess> detect(String code) {
        if (code == null || code.isEmpty()) {
            return Collections.emptyList();
        }

        String sanitized = stripComments(code);
        sanitized = maskStringLiterals(sanitized);
        String[] lines = sanitized.split("\\R", -1);
        String[] originalLines = code.split("\\R", -1);

        List<ArrayAccess> accesses = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int assignmentIndex = findAssignmentIndex(line);

            Matcher matcher = ARRAY_PATTERN.matcher(line);

            while (matcher.find()) {
                String originalLine = i < originalLines.length ? originalLines[i] : line;
                int start = matcher.start(1);
                int end = matcher.end();
                if (start < 0 || end > originalLine.length()) {
                    continue;
                }
                String expression = originalLine.substring(start, end).trim();
                int column = matcher.start(1);
                ArrayAccess.AccessType type = ArrayAccess.AccessType.UNKNOWN;

                if (assignmentIndex >= 0) {
                    if (matcher.end() <= assignmentIndex) {
                        type = ArrayAccess.AccessType.STORE;
                    } else {
                        type = ArrayAccess.AccessType.LOAD;
                    }
                } else {
                    type = ArrayAccess.AccessType.LOAD;
                }

                if (isTypeContext(originalLine, start)) {
                    continue;
                }

                accesses.add(new ArrayAccess(expression, i + 1, column + 1, type));
            }
        }

        return accesses;
    }

    private static int findAssignmentIndex(String line) {
        if (line == null || line.isEmpty()) {
            return -1;
        }

        boolean inString = false;
        boolean inChar = false;
        boolean escape = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (escape) {
                escape = false;
                continue;
            }
            if (c == '\\') {
                escape = true;
                continue;
            }
            if (c == '"' && !inChar) {
                inString = !inString;
                continue;
            }
            if (c == '\'' && !inString) {
                inChar = !inChar;
                continue;
            }

            if (inString || inChar) {
                continue;
            }

            if (c == '=') {
                char prev = i > 0 ? line.charAt(i - 1) : '\0';
                char next = i + 1 < line.length() ? line.charAt(i + 1) : '\0';

                if (prev == '=' || prev == '<' || prev == '>' || prev == '!' || next == '=') {
                    continue;
                }

                return i;
            }
        }

        return -1;
    }

    private static String stripComments(String code) {
        StringBuilder result = new StringBuilder(code.length());

        boolean inBlockComment = false;
        boolean inLineComment = false;
        boolean inString = false;
        boolean inChar = false;
        boolean escape = false;

        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            char next = i + 1 < code.length() ? code.charAt(i + 1) : '\0';

            if (inLineComment) {
                if (c == '\n') {
                    inLineComment = false;
                    result.append(c);
                } else {
                    result.append(' ');
                }
                continue;
            }

            if (inBlockComment) {
                if (c == '*' && next == '/') {
                    inBlockComment = false;
                    result.append(' ');
                    result.append(' ');
                    i++; // Skip '/'
                } else if (c == '\n') {
                    result.append('\n');
                } else {
                    result.append(' ');
                }
                continue;
            }

            if (escape) {
                result.append(c);
                escape = false;
                continue;
            }

            if (c == '\\') {
                escape = true;
                result.append(c);
                continue;
            }

            if (c == '"' && !inChar) {
                inString = !inString;
                result.append(c);
                continue;
            }

            if (c == '\'' && !inString) {
                inChar = !inChar;
                result.append(c);
                continue;
            }

            if (!inString && !inChar) {
                if (c == '/' && next == '/') {
                    inLineComment = true;
                    result.append(' ');
                    result.append(' ');
                    i++;
                    continue;
                }
                if (c == '/' && next == '*') {
                    inBlockComment = true;
                    result.append(' ');
                    result.append(' ');
                    i++;
                    continue;
                }
            }

            result.append(c);
        }

        return result.toString();
    }

    private static String maskStringLiterals(String code) {
        StringBuilder result = new StringBuilder(code.length());
        boolean inString = false;
        boolean inChar = false;
        boolean escape = false;

        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);

            if (escape) {
                if (c == '\n') {
                    result.append('\n');
                } else {
                    result.append(' ');
                }
                escape = false;
                continue;
            }

            if (inString) {
                if (c == '\\') {
                    escape = true;
                    result.append(' ');
                } else if (c == '"') {
                    inString = false;
                    result.append('"');
                } else if (c == '\n') {
                    inString = false;
                    result.append('\n');
                } else {
                    result.append(' ');
                }
                continue;
            }

            if (inChar) {
                if (c == '\\') {
                    escape = true;
                    result.append(' ');
                } else if (c == '\'') {
                    inChar = false;
                    result.append('\'');
                } else if (c == '\n') {
                    inChar = false;
                    result.append('\n');
                } else {
                    result.append(' ');
                }
                continue;
            }

            if (c == '"') {
                inString = true;
                result.append('"');
                continue;
            }

            if (c == '\'') {
                inChar = true;
                result.append('\'');
                continue;
            }

            result.append(c);
        }

        return result.toString();
    }

    private static boolean isTypeContext(String line, int expressionStart) {
        int contextStart = Math.max(line.lastIndexOf('(', expressionStart),
                line.lastIndexOf(',', expressionStart));
        contextStart = Math.max(contextStart, line.lastIndexOf(';', expressionStart));
        contextStart = Math.max(contextStart, line.lastIndexOf('{', expressionStart));
        contextStart = Math.max(contextStart, line.lastIndexOf('}', expressionStart));

        String context;
        if (contextStart >= 0) {
            context = line.substring(contextStart + 1, expressionStart);
        } else {
            context = line.substring(0, expressionStart);
        }
        if (context.indexOf('=') >= 0 || context.contains("return") || context.contains("case")) {
            return false;
        }

        String normalized = context.replace('*', ' ').trim();
        if (normalized.isEmpty()) {
            return false;
        }

        String[] tokens = normalized.split("\\s+");
        boolean hasTypeToken = false;
        for (String token : tokens) {
            if (token.isEmpty()) {
                continue;
            }
            if (!TYPE_TOKENS.contains(token)) {
                return false;
            }
            if (!("const".equals(token) || "volatile".equals(token))) {
                hasTypeToken = true;
            }
        }

        return hasTypeToken;
    }
}
