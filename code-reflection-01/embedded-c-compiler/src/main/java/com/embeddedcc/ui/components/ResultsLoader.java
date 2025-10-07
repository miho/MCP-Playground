package com.embeddedcc.ui.components;

import com.embeddedcc.analysis.CacheConfiguration;
import com.embeddedcc.analysis.CacheEvent;
import com.embeddedcc.analysis.CacheEventType;
import com.embeddedcc.analysis.CacheSummary;
import com.embeddedcc.analysis.RunResultPersister;
import com.embeddedcc.instrumentation.InstrumentationPoint;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Utility class for loading and parsing run results from JSON files.
 * Provides both automatic detection and manual file selection.
 */
public class ResultsLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    // Match the path used by RunResultPersister
    private static final Path DEFAULT_RESULTS_DIR = Paths.get(System.getProperty("user.home"), ".embeddedcc", "runs");

    /**
     * Load result from a specific file path
     */
    public static LoadedResult loadFromFile(Path filePath) throws IOException {
        if (!Files.exists(filePath)) {
            throw new IOException("File not found: " + filePath);
        }

        Map<String, Object> data = MAPPER.readValue(filePath.toFile(), Map.class);
        return parseResult(data, filePath);
    }

    /**
     * Show file chooser dialog for manual result selection
     */
    public static Optional<LoadedResult> showLoadDialog(Window owner) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Load Cache Analysis Result");

        // Set initial directory
        if (Files.exists(DEFAULT_RESULTS_DIR)) {
            fileChooser.setInitialDirectory(DEFAULT_RESULTS_DIR.toFile());
        } else {
            fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));
        }

        // Add filters
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("JSON Result Files", "*.json"),
            new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        File selectedFile = fileChooser.showOpenDialog(owner);
        if (selectedFile != null) {
            try {
                return Optional.of(loadFromFile(selectedFile.toPath()));
            } catch (IOException e) {
                throw new RuntimeException("Failed to load result: " + e.getMessage(), e);
            }
        }

        return Optional.empty();
    }

    /**
     * Find and list recent result files
     */
    public static List<ResultFile> findRecentResults(int limit) {
        List<ResultFile> results = new ArrayList<>();

        if (Files.exists(DEFAULT_RESULTS_DIR)) {
            try (Stream<Path> paths = Files.walk(DEFAULT_RESULTS_DIR, 1)) {
                results = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".json"))
                    .map(p -> {
                        try {
                            return new ResultFile(
                                p,
                                Files.getLastModifiedTime(p).toInstant(),
                                extractRunId(p)
                            );
                        } catch (IOException e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(ResultFile::timestamp).reversed())
                    .limit(limit)
                    .collect(Collectors.toList());
            } catch (IOException e) {
                // Ignore, return empty list
            }
        }

        return results;
    }

    private static String extractRunId(Path path) {
        String filename = path.getFileName().toString();
        if (filename.endsWith(".json")) {
            return filename.substring(0, filename.length() - 5);
        }
        return filename;
    }

    private static LoadedResult parseResult(Map<String, Object> data, Path filePath) {
        // Extract basic info
        String runId = (String) data.getOrDefault("run_id", "unknown");
        String timestamp = (String) data.getOrDefault("timestamp", "");
        String originalCode = (String) data.getOrDefault("original_code", "");
        String instrumentedCode = (String) data.getOrDefault("instrumented_code", "");

        // Parse cache configuration
        Map<String, Object> cacheData = (Map<String, Object>) data.getOrDefault("cache", new HashMap<>());
        CacheConfiguration cacheConfig = new CacheConfiguration(
            ((Number) cacheData.getOrDefault("set_bits", 5)).intValue(),
            ((Number) cacheData.getOrDefault("lines_per_set", 1)).intValue(),
            ((Number) cacheData.getOrDefault("block_bits", 5)).intValue()
        );

        // Parse cache summary
        Map<String, Object> summaryData = (Map<String, Object>) data.getOrDefault("cache_summary", new HashMap<>());
        int hits = ((Number) summaryData.getOrDefault("hits", 0)).intValue();
        int misses = ((Number) summaryData.getOrDefault("misses", 0)).intValue();
        int evictions = ((Number) summaryData.getOrDefault("evictions", 0)).intValue();

        // Parse events
        List<CacheEvent> events = new ArrayList<>();
        List<Map<String, Object>> eventsList = (List<Map<String, Object>>) data.getOrDefault("events", new ArrayList<>());
        for (Map<String, Object> eventData : eventsList) {
            try {
                String typeStr = (String) eventData.getOrDefault("type", "HIT");
                CacheEventType type = CacheEventType.valueOf(typeStr);
                int id = ((Number) eventData.getOrDefault("id", -1)).intValue();
                int line = ((Number) eventData.getOrDefault("line", 0)).intValue();
                String label = (String) eventData.getOrDefault("label", "");

                // For loaded events, we don't have the original InstrumentationPoint
                events.add(new CacheEvent(type, id, line, label, null));
            } catch (Exception e) {
                // Skip invalid events
            }
        }

        CacheSummary summary = CacheSummary.of(hits, misses, evictions, events);

        // Parse hotspots
        List<Map<String, Object>> hotspots = (List<Map<String, Object>>) data.getOrDefault("hotspots", new ArrayList<>());

        // Parse instrumented points
        List<Map<String, Object>> instrumentedPoints = (List<Map<String, Object>>)
            data.getOrDefault("instrumented_points", new ArrayList<>());

        // Parse metadata
        Map<String, Object> metadata = (Map<String, Object>) data.getOrDefault("metadata", new HashMap<>());

        // Parse defines
        List<String> defines = (List<String>) data.getOrDefault("defines", new ArrayList<>());

        // Parse compile info
        Map<String, Object> compileInfo = (Map<String, Object>) data.getOrDefault("compile", new HashMap<>());

        // Parse execution info
        Map<String, Object> executionInfo = (Map<String, Object>) data.getOrDefault("execution", new HashMap<>());

        return new LoadedResult(
            runId,
            timestamp,
            filePath,
            originalCode,
            instrumentedCode,
            cacheConfig,
            summary,
            hotspots,
            instrumentedPoints,
            metadata,
            defines,
            compileInfo,
            executionInfo
        );
    }

    /**
     * Represents a result file with metadata
     */
    public static record ResultFile(
        Path path,
        java.time.Instant timestamp,
        String runId
    ) {}

    /**
     * Represents a fully loaded result
     */
    public static record LoadedResult(
        String runId,
        String timestamp,
        Path filePath,
        String originalCode,
        String instrumentedCode,
        CacheConfiguration cacheConfiguration,
        CacheSummary summary,
        List<Map<String, Object>> hotspots,
        List<Map<String, Object>> instrumentedPoints,
        Map<String, Object> metadata,
        List<String> defines,
        Map<String, Object> compileInfo,
        Map<String, Object> executionInfo
    ) {
        /**
         * Convert to PersistedResult format for UI consumption
         */
        public RunResultPersister.PersistedResult toPersistedResult() {
            return new RunResultPersister.PersistedResult(
                new RunResultPersister.RunRecord(runId, filePath, cacheConfiguration),
                summary,
                hotspots,
                instrumentedPoints,
                cacheConfiguration,
                defines,
                metadata,
                originalCode,
                instrumentedCode
            );
        }
    }
}