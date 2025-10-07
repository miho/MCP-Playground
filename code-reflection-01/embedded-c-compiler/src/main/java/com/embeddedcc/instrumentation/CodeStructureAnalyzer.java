package com.embeddedcc.instrumentation;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CodeStructureAnalyzer {

    private static final Pattern FUNCTION_PATTERN =
            Pattern.compile("(?m)^\\s*([\\w\\s\\*]+?)\\s+(\\w+)\\s*\\([^;]*\\)\\s*\\{");

    public List<CodeFunction> findFunctions(String source) {
        List<CodeFunction> functions = new ArrayList<>();
        if (source == null || source.isBlank()) {
            return functions;
        }

        Matcher matcher = FUNCTION_PATTERN.matcher(source);
        while (matcher.find()) {
            String functionName = matcher.group(2);
            int start = matcher.start();
            int lineNumber = lineOf(source, start);
            functions.add(new CodeFunction(functionName, lineNumber));
        }

        return functions;
    }

    private static int lineOf(String text, int index) {
        int line = 1;
        for (int i = 0; i < index && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
}
