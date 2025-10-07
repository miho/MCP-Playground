package examples.java;

import java.util.List;
import java.util.ArrayList;

/**
 * Example class demonstrating string operations that can be improved.
 * Recipes to try:
 * - org.openrewrite.java.cleanup.UseStringReplace
 * - org.openrewrite.java.cleanup.ReplaceStringBuilderWithString
 * - org.openrewrite.java.cleanup.CombineStringLiterals
 */
public class StringOperations {

    // Using replaceAll with plain text (should use replace)
    public String cleanText(String text) {
        return text.replaceAll(" ", "_").replaceAll("-", "_");
    }

    // Inefficient string concatenation in loop
    public String buildMessage(List<String> items) {
        String result = "";
        for (String item : items) {
            result = result + item + ", ";
        }
        return result;
    }

    // StringBuilder for simple concatenation
    public String getFullName(String first, String last) {
        StringBuilder sb = new StringBuilder();
        sb.append(first);
        sb.append(" ");
        sb.append(last);
        return sb.toString();
    }

    // String.valueOf on string
    public String convertToString(String value) {
        return String.valueOf(value);
    }

    // Concatenating literals
    public String getHeader() {
        return "=== " + "Header" + " " + "Text" + " ===";
    }

    // Using String.format for simple concatenation
    public String formatSimple(String name) {
        return String.format("%s", name);
    }

    // Checking empty with length
    public boolean isEmpty(String str) {
        return str.length() == 0;
    }

    // Using equals with literal (should be reversed)
    public boolean isValid(String input) {
        return input.equals("valid");
    }
}