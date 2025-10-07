package examples.java;

/**
 * Example class demonstrating complex boolean expressions that can be simplified.
 * Recipes to try:
 * - org.openrewrite.java.cleanup.SimplifyBooleanExpression
 * - org.openrewrite.java.cleanup.SimplifyBooleanReturn
 */
public class BooleanSimplification {

    // Redundant boolean comparisons
    public boolean isValidUser(String username, boolean isActive) {
        if (username != null == true) {
            if (isActive == true) {
                return true;
            } else {
                return false;
            }
        }
        return false;
    }

    // Unnecessary ternary operators
    public boolean checkAge(int age) {
        return age >= 18 ? true : false;
    }

    // Complex negations
    public boolean isNotInvalid(String value) {
        if (!(value == null || value.isEmpty())) {
            return true;
        }
        return false;
    }

    // Double negation
    public boolean hasPermission(boolean isAdmin, boolean isOwner) {
        return !(!isAdmin && !isOwner);
    }

    // Redundant if-else
    public boolean validateInput(String input) {
        if (input != null && input.length() > 0) {
            if (input.startsWith("valid")) {
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }
}