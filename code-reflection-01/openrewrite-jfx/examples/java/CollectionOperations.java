package examples.java;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Example class demonstrating collection operations that can be modernized.
 * Recipes to try:
 * - org.openrewrite.java.cleanup.UseCollectionInterfaces
 * - org.openrewrite.java.cleanup.PreferJavaUtilCollections
 * - org.openrewrite.java.migrate.util.UseMapOf
 */
public class CollectionOperations {

    // Using concrete types instead of interfaces
    public ArrayList<String> getNames() {
        ArrayList<String> names = new ArrayList<String>();
        names.add("Alice");
        names.add("Bob");
        return names;
    }

    // Manual initialization instead of List.of()
    public List<Integer> getNumbers() {
        List<Integer> numbers = new ArrayList<>();
        numbers.add(1);
        numbers.add(2);
        numbers.add(3);
        return numbers;
    }

    // Old-style map initialization
    public Map<String, String> getConfig() {
        HashMap<String, String> config = new HashMap<String, String>();
        config.put("host", "localhost");
        config.put("port", "8080");
        return config;
    }

    // Using Collections.EMPTY_LIST
    public List<String> getEmptyList() {
        return Collections.EMPTY_LIST;
    }

    // Manual contains check before add
    public void addUnique(Set<String> set, String value) {
        if (!set.contains(value)) {
            set.add(value);
        }
    }

    // Using iterator instead of enhanced for loop
    public void processItems(List<String> items) {
        Iterator<String> iter = items.iterator();
        while (iter.hasNext()) {
            String item = iter.next();
            System.out.println(item);
        }
    }

    // Could use Stream API
    public List<String> filterAndTransform(List<String> input) {
        List<String> result = new ArrayList<>();
        for (String s : input) {
            if (s != null && s.length() > 3) {
                result.add(s.toUpperCase());
            }
        }
        return result;
    }

    // Using size() == 0 instead of isEmpty()
    public boolean hasItems(Collection<?> collection) {
        return collection.size() != 0;
    }
}