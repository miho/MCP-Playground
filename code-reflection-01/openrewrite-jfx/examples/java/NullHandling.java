package examples.java;

import java.util.Optional;

/**
 * Example class demonstrating null handling that can be improved.
 * Recipes to try:
 * - org.openrewrite.java.cleanup.UseObjectEquals
 * - org.openrewrite.java.cleanup.EqualsAvoidsNull
 * - org.openrewrite.staticanalysis.NeedBraces
 */
public class NullHandling {

    // Manual null checking
    public String processString(String input) {
        if (input != null)
            return input.trim();
        else
            return "";
    }

    // Comparing with null on wrong side
    public boolean isNull(Object obj) {
        return null == obj;
    }

    // Using == for string comparison
    public boolean isEqual(String a, String b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a == b;  // Should use equals()
    }

    // Not using Objects.equals
    public boolean compareStrings(String str1, String str2) {
        if (str1 == null) {
            return str2 == null;
        } else {
            return str1.equals(str2);
        }
    }

    // Could use Optional
    public String findValue(String key) {
        String value = lookupValue(key);
        if (value != null) {
            return value;
        } else {
            return "default";
        }
    }

    // Missing braces
    public void validateAndProcess(String data) {
        if (data == null)
            throw new IllegalArgumentException("Data cannot be null");

        if (data.isEmpty())
            System.out.println("Warning: empty data");
        else
            System.out.println("Processing: " + data);
    }

    // Nested null checks
    public String getNestedValue(Container container) {
        if (container != null) {
            if (container.getData() != null) {
                if (container.getData().getValue() != null) {
                    return container.getData().getValue();
                }
            }
        }
        return "";
    }

    private String lookupValue(String key) {
        // Stub implementation
        return null;
    }

    private static class Container {
        private Data data;

        public Data getData() {
            return data;
        }
    }

    private static class Data {
        private String value;

        public String getValue() {
            return value;
        }
    }
}