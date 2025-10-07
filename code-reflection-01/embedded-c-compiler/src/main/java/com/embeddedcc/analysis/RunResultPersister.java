package com.embeddedcc.analysis;

import com.embeddedcc.compiler.RunResult;
import com.embeddedcc.instrumentation.InstrumentationPoint;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class RunResultPersister {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final CopyOnWriteArrayList<RunResultListener> LISTENERS = new CopyOnWriteArrayList<>();
    private final Path storageDir;

    public RunResultPersister() {
        this(defaultStorageDir());
    }

    public RunResultPersister(Path storageDir) {
        this.storageDir = storageDir;
    }

    public static void addListener(RunResultListener listener) {
        if (listener != null) {
            LISTENERS.addIfAbsent(listener);
        }
    }

    public static void removeListener(RunResultListener listener) {
        if (listener != null) {
            LISTENERS.remove(listener);
        }
    }

    public RunRecord persist(String originalCode,
                             String instrumentedCode,
                             RunResult runResult,
                             CacheSummary summary,
                             CacheConfiguration cacheConfiguration,
                             List<InstrumentationPoint> points,
                             List<String> defines,
                             Map<String, Object> metadata) throws IOException {
        Files.createDirectories(storageDir);

        String runId = "run-" + DateTimeFormatter.ISO_INSTANT.format(Instant.now()) + "-"
                + UUID.randomUUID().toString().substring(0, 8);
        runId = runId.replace(":", "-");
        Path destination = storageDir.resolve(runId + ".json");

        Map<String, Object> root = new HashMap<>();
        root.put("run_id", runId);
        root.put("timestamp", Instant.now().toString());
        root.put("original_code", originalCode != null ? originalCode : "");
        root.put("code_length", originalCode != null ? originalCode.length() : 0);
        if (instrumentedCode != null && !instrumentedCode.isEmpty()) {
            root.put("instrumented_code", instrumentedCode);
        }
        if (metadata != null && !metadata.isEmpty()) {
            root.put("metadata", metadata);
        }

        Map<String, Object> cacheInfo = new HashMap<>();
        cacheInfo.put("set_bits", cacheConfiguration.setBits());
        cacheInfo.put("lines_per_set", cacheConfiguration.linesPerSet());
        cacheInfo.put("block_bits", cacheConfiguration.blockBits());
        root.put("cache", cacheInfo);

        Map<String, Object> compileInfo = new HashMap<>();
        compileInfo.put("exit_code", runResult.getCompileExitCode());
        compileInfo.put("stdout", runResult.getCompileStdout());
        compileInfo.put("stderr", runResult.getCompileStderr());
        root.put("compile", compileInfo);

        Map<String, Object> execInfo = new HashMap<>();
        execInfo.put("executed", runResult.isExecuted());
        execInfo.put("exit_code", runResult.getExecutionExitCode());
        execInfo.put("stdout", runResult.getExecutionStdout());
        execInfo.put("stderr", runResult.getExecutionStderr());
        root.put("execution", execInfo);

        root.put("defines", defines);

        if (runResult.getWorkingDirectory() != null) {
            root.put("working_directory", runResult.getWorkingDirectory().toAbsolutePath().toString());
        }
        if (runResult.getTrace() != null) {
            root.put("trace_length", runResult.getTrace().length());
        }

        if (runResult.getWorkingDirectory() != null) {
            Path tracePath = runResult.getWorkingDirectory().resolve("trace.log");
            if (Files.exists(tracePath)) {
                root.put("trace_path", tracePath.toAbsolutePath().toString());
            }
        }

        Map<Integer, InstrumentationPoint> pointLookup = runResult.getInstrumentedPoints();

        List<Map<String, Object>> hotspotList = CacheInsights.hotspots(summary, pointLookup, Integer.MAX_VALUE);
        root.put("hotspots", hotspotList);
        root.put("events", CacheInsights.allEvents(summary));

        List<Map<String, Object>> pointDescriptions = points.stream()
                .map(ip -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", ip.getId());
                    map.put("line", ip.getAccess().getLine());
                    map.put("expression", ip.getAccess().getExpression());
                    map.put("access_type", ip.getAccess().getAccessType().name());
                    return map;
                })
                .toList();
        root.put("instrumented_points", pointDescriptions);

        MAPPER.writerWithDefaultPrettyPrinter().writeValue(destination.toFile(), root);

        RunRecord record = new RunRecord(runId, destination, cacheConfiguration);
        notifyListeners(new PersistedResult(
                record,
                summary,
                hotspotList,
                pointDescriptions,
                cacheConfiguration,
                defines == null ? List.of() : List.copyOf(defines),
                metadata == null ? Map.of() : Map.copyOf(metadata),
                originalCode,
                instrumentedCode
        ));

        return record;
    }

    public String readRunAsString(String runId) throws IOException {
        Path path = storageDir.resolve(runId + ".json");
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    public boolean exists(String runId) {
        return Files.exists(storageDir.resolve(runId + ".json"));
    }

    public Path resolve(String runId) {
        return storageDir.resolve(runId + ".json");
    }

    public Path getStorageDir() {
        return storageDir;
    }

    private static Path defaultStorageDir() {
        String override = System.getProperty("embeddedcc.runs.dir");
        if (override == null || override.isBlank()) {
            override = System.getenv("EMBEDDED_CC_RUNS_DIR");
        }
        Path path;
        if (override != null && !override.isBlank()) {
            path = Path.of(override.trim());
        } else {
            String home = System.getProperty("user.home", ".");
            path = Path.of(home, ".embeddedcc", "runs");
        }
        return path.toAbsolutePath();
    }

    public record RunRecord(String runId, Path path, CacheConfiguration cacheConfiguration) {
    }

    public record PersistedResult(RunRecord record,
                                  CacheSummary summary,
                                  List<Map<String, Object>> hotspots,
                                  List<Map<String, Object>> instrumentedPoints,
                                  CacheConfiguration cacheConfiguration,
                                  List<String> defines,
                                  Map<String, Object> metadata,
                                  String originalCode,
                                  String instrumentedCode) {
    }

    @FunctionalInterface
    public interface RunResultListener {
        void onPersisted(PersistedResult result);
    }

    private void notifyListeners(PersistedResult result) {
        for (RunResultListener listener : LISTENERS) {
            try {
                listener.onPersisted(result);
            } catch (Exception ignored) {
            }
        }
    }
}
