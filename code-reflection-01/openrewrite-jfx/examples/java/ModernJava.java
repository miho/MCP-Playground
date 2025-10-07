package examples.java;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Example class demonstrating code that can be migrated to modern Java features.
 * Recipes to try:
 * - org.openrewrite.java.migrate.UpgradeToJava11
 * - org.openrewrite.java.migrate.UpgradeToJava17
 * - org.openrewrite.java.cleanup.UseDiamondOperator
 * - org.openrewrite.java.cleanup.UseTextBlocks
 */
public class ModernJava {

    // Not using diamond operator
    private List<String> items = new ArrayList<String>();

    // Old-style try-finally for resource management
    public String readFile(String filename) throws IOException {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(filename));
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            return content.toString();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    // Ignored
                }
            }
        }
    }

    // Could use text blocks (Java 15+)
    public String getJsonTemplate() {
        return "{\n" +
               "  \"name\": \"example\",\n" +
               "  \"version\": \"1.0\",\n" +
               "  \"features\": [\n" +
               "    \"feature1\",\n" +
               "    \"feature2\"\n" +
               "  ]\n" +
               "}";
    }

    // Could use var (Java 10+)
    public void processData() {
        ArrayList<String> dataList = new ArrayList<String>();
        dataList.add("item1");
        dataList.add("item2");

        for (int i = 0; i < dataList.size(); i++) {
            String item = dataList.get(i);
            System.out.println(item);
        }
    }

    // Could use switch expressions (Java 14+)
    public String getDayType(String day) {
        String type;
        switch (day) {
            case "Monday":
            case "Tuesday":
            case "Wednesday":
            case "Thursday":
            case "Friday":
                type = "Weekday";
                break;
            case "Saturday":
            case "Sunday":
                type = "Weekend";
                break;
            default:
                type = "Unknown";
                break;
        }
        return type;
    }

    // Anonymous class that could be a lambda
    public void setupListener() {
        Runnable task = new Runnable() {
            @Override
            public void run() {
                System.out.println("Task executed");
            }
        };
        task.run();
    }

    // Could use method references
    public List<String> transformList(List<String> input) {
        List<String> result = new ArrayList<>();
        for (String s : input) {
            result.add(s.toUpperCase());
        }
        return result;
    }
}