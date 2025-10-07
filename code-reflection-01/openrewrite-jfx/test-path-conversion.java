public class TestPath {
    public static void main(String[] args) {
        String[] testPaths = {
            "C:\\\\Dev\\\\repos\\\\MCP-Playground\\\\code-reflection-01\\\\openrewrite-jfx\\\\examples\\\\java\\\\ModernJava.java",
            "C:\\Dev\\repos\\MCP-Playground\\code-reflection-01\\openrewrite-jfx\\examples\\java\\ModernJava.java",
            "C:/Dev/repos/MCP-Playground/code-reflection-01/openrewrite-jfx/examples/java/ModernJava.java"
        };
        
        for (String path : testPaths) {
            System.out.println("Original: " + path);
            String converted = convertWindowsPathToWSL(path);
            System.out.println("Converted: " + converted);
            System.out.println();
        }
    }
    
    private static String convertWindowsPathToWSL(String path) {
        if (path == null) return path;
        
        // First, handle escaped backslashes (\\) by replacing them with single backslashes
        String normalizedPath = path.replace("\\\\", "\\");
        
        // Check if it's a Windows path (starts with drive letter)
        if (normalizedPath.matches("^[A-Za-z]:[\\\\/].*")) {
            // Extract drive letter and convert to lowercase
            char driveLetter = Character.toLowerCase(normalizedPath.charAt(0));
            // Remove drive letter and colon, replace backslashes with forward slashes
            String pathPart = normalizedPath.substring(2).replace('\\', '/');
            // Construct WSL path
            String wslPath = "/mnt/" + driveLetter + pathPart;
            return wslPath;
        }
        
        return path;
    }
}
