package com.embeddedcc.mcp;

import com.embeddedcc.analysis.CacheAnalyzer;
import com.embeddedcc.analysis.CacheConfiguration;
import com.embeddedcc.analysis.CacheInsights;
import com.embeddedcc.analysis.CacheSummary;
import com.embeddedcc.analysis.RunResultPersister;
import com.embeddedcc.compiler.CCompilerRunner;
import com.embeddedcc.compiler.RunResult;
import com.embeddedcc.instrumentation.ArrayAccess;
import com.embeddedcc.instrumentation.ArrayAccessDetector;
import com.embeddedcc.instrumentation.CodeInstrumenter;
import com.embeddedcc.instrumentation.CodeStructureAnalyzer;
import com.embeddedcc.instrumentation.InstrumentationPoint;
import com.embeddedcc.instrumentation.InstrumentedProgram;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

final class ToolFactory {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final RunResultPersister RESULT_PERSISTER = new RunResultPersister();

    private ToolFactory() {
    }

    static McpServerFeatures.AsyncToolSpecification createAnalyzeTool() {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "code": {"type": "string"},
                    "filename": {"type": "string"}
                  },
                  "required": ["code"]
                }
                """;

        var tool = McpSchema.Tool.builder()
                .name("analyze_c_code")
                .description("Parse C source to list functions and array access candidates for instrumentation.")
                .inputSchema(io.modelcontextprotocol.json.McpJsonMapper.createDefault(), schema)
                .build();

        return new McpServerFeatures.AsyncToolSpecification.Builder()
                .tool(tool)
                .callHandler((context, request) -> {
                    Map<String, Object> args = request.arguments();
                    String code = getString(args, "code");
                    if (code == null) {
                        return Mono.just(error("Missing 'code' argument"));
                    }

                    ArrayAccessDetector detector = new ArrayAccessDetector();
                    List<ArrayAccess> accesses = detector.detect(code);

                    List<Map<String, Object>> accessPayload = new ArrayList<>();
                    for (int i = 0; i < accesses.size(); i++) {
                        ArrayAccess access = accesses.get(i);
                        Map<String, Object> entry = new HashMap<>();
                        entry.put("id", i);
                        entry.put("expression", access.getExpression());
                        entry.put("line", access.getLine());
                        entry.put("column", access.getColumn());
                        entry.put("type", access.getAccessType().name());
                        accessPayload.add(entry);
                    }

                    CodeStructureAnalyzer analyzer = new CodeStructureAnalyzer();
                    List<Map<String, Object>> functionPayload = new ArrayList<>();
                    analyzer.findFunctions(code).forEach(fn -> {
                        Map<String, Object> entry = new HashMap<>();
                        entry.put("name", fn.name());
                        entry.put("line", fn.line());
                        functionPayload.add(entry);
                    });

                    Map<String, Object> result = new HashMap<>();
                    result.put("array_accesses", accessPayload);
                    result.put("functions", functionPayload);
                    result.put("count", accesses.size());

                    return Mono.just(success(toJson(result)));
                })
                .build();
    }

    static McpServerFeatures.AsyncToolSpecification createCompileTool() {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "code": {"type": "string"},
                    "filename": {"type": "string"},
                    "instrument_ids": {
                      "type": "array",
                      "items": {"type": "integer"}
                    },
                    "cache": {
                      "type": "object",
                      "properties": {
                        "set_bits": {"type": "integer"},
                        "lines_per_set": {"type": "integer"},
                        "block_bits": {"type": "integer"}
                      }
                    },
                    "defines": {
                      "type": "array",
                      "items": {"type": "string"},
                      "description": "Additional compiler definitions, e.g. BLOCK_SIZE=8"
                    },
                    "max_hotspots": {
                      "type": "integer",
                      "minimum": 1,
                      "description": "Maximum hotspot entries to return"
                    },
                    "max_events": {
                      "type": "integer",
                      "minimum": 0,
                      "description": "Limit the number of cache events included in the response"
                    },
                    "return_trace_path": {
                      "type": "boolean",
                      "description": "Include the trace file path in the response"
                    },
                    "save_trace_to": {
                      "type": "string",
                      "description": "Copy the trace file to this path and return the location"
                    }
                  },
                  "required": ["code"]
                }
                """;

        var tool = McpSchema.Tool.builder()
                .name("compile_and_run_c")
                .description("Instrument specified memory accesses, compile the C code, execute it, and analyse cache behaviour.")
                .inputSchema(io.modelcontextprotocol.json.McpJsonMapper.createDefault(), schema)
                .build();

        return new McpServerFeatures.AsyncToolSpecification.Builder()
                .tool(tool)
                .callHandler((context, request) -> {
                    Map<String, Object> args = request.arguments();
                    String code = getString(args, "code");
                    if (code == null) {
                        return Mono.just(error("Missing 'code' argument"));
                    }

                    String fileName = getString(args, "filename");
                    List<Integer> instrumentIds = getIntegerList(args.get("instrument_ids"));
                    CacheConfiguration cacheConfig = parseCacheConfig(args.get("cache"));
                    List<String> defines = getStringList(args.get("defines"));

                    int maxHotspots = Math.max(1, getInt(args, "max_hotspots", 10));
                    int maxEvents = Math.max(0, getInt(args, "max_events", 200));
                    boolean returnTracePath = getBoolean(args, "return_trace_path", false);
                    String saveTraceTo = getString(args, "save_trace_to");
                    String saveResultsTo = getString(args, "results_path");

                    CompileWorkflow workflow = new CompileWorkflow(code, instrumentIds, fileName, cacheConfig,
                            defines, maxHotspots, maxEvents, returnTracePath, saveTraceTo, saveResultsTo);

                    try {
                        Map<String, Object> response = workflow.execute();
                        return Mono.just(success(toJson(response)));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return Mono.just(error("Execution interrupted"));
                    } catch (IOException e) {
                        return Mono.just(error("Execution failed: " + e.getMessage()));
                    }
                })
                .build();
    }

    static McpServerFeatures.AsyncToolSpecification createBlockSweepTool() {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "code": {"type": "string"},
                    "filename": {"type": "string"},
                    "instrument_ids": {
                      "type": "array",
                      "items": {"type": "integer"}
                    },
                    "cache": {
                      "type": "object",
                      "properties": {
                        "set_bits": {"type": "integer"},
                        "lines_per_set": {"type": "integer"},
                        "block_bits": {"type": "integer"}
                      }
                    },
                    "block_macro": {"type": "string", "default": "BLOCK_SIZE"},
                    "start": {"type": "integer"},
                    "end": {"type": "integer"},
                    "step": {"type": "integer", "minimum": 1},
                    "defines": {
                      "type": "array",
                      "items": {"type": "string"},
                      "description": "Additional compiler definitions applied to every sweep iteration"
                    }
                  },
                  "required": ["code", "start", "end", "step"]
                }
                """;

        var tool = McpSchema.Tool.builder()
                .name("sweep_block_sizes")
                .description("Instrument code once and evaluate cache performance across a range of block sizes.")
                .inputSchema(io.modelcontextprotocol.json.McpJsonMapper.createDefault(), schema)
                .build();

        return new McpServerFeatures.AsyncToolSpecification.Builder()
                .tool(tool)
                .callHandler((context, request) -> {
                    Map<String, Object> args = request.arguments();
                    String code = getString(args, "code");
                    if (code == null) {
                        return Mono.just(error("Missing 'code' argument"));
                    }

                    int start = getInt(args, "start", 0);
                    int end = getInt(args, "end", 0);
                    int step = getInt(args, "step", 0);
                    if (step <= 0) {
                        return Mono.just(error("'step' must be > 0"));
                    }

                    String macro = getString(args, "block_macro");
                    if (macro == null || macro.isBlank()) {
                        macro = "BLOCK_SIZE";
                    }

                    List<Integer> blockSizes = computeBlockSizes(start, end, step);
                    if (blockSizes.isEmpty()) {
                        return Mono.just(error("No block sizes generated from start/end/step"));
                    }

                    String fileName = getString(args, "filename");
                    List<Integer> instrumentIds = getIntegerList(args.get("instrument_ids"));
                    CacheConfiguration cacheConfig = parseCacheConfig(args.get("cache"));
                    List<String> defines = getStringList(args.get("defines"));

                    BlockSweepWorkflow workflow = new BlockSweepWorkflow(code, instrumentIds, fileName,
                            cacheConfig, defines, macro, blockSizes);

                    try {
                        Map<String, Object> response = workflow.execute();
                        return Mono.just(success(toJson(response)));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return Mono.just(error("Execution interrupted"));
                    } catch (IOException e) {
                        return Mono.just(error("Execution failed: " + e.getMessage()));
                    }
                })
                .build();
    }

    static McpServerFeatures.AsyncToolSpecification createGetRunResultTool() {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "run_id": {"type": "string"},
                    "path": {"type": "string"},
                    "sections": {
                      "type": "array",
                      "items": {"type": "string"},
                      "description": "Subset of result sections to return (summary, hotspots, events_sample, metadata)"
                    },
                    "max_events": {"type": "integer", "minimum": 0},
                    "max_hotspots": {"type": "integer", "minimum": 1}
                  },
                  "anyOf": [
                    {"required": ["run_id"]},
                    {"required": ["path"]}
                  ]
                }
                """;

        var tool = McpSchema.Tool.builder()
                .name("get_run_result")
                .description("Retrieve persisted cache-analysis results for a previous run.")
                .inputSchema(io.modelcontextprotocol.json.McpJsonMapper.createDefault(), schema)
                .build();

        return new McpServerFeatures.AsyncToolSpecification.Builder()
                .tool(tool)
                .callHandler((context, request) -> {
                    Map<String, Object> args = request.arguments();
                    String runId = getString(args, "run_id");
                    String providedPath = getString(args, "path");
                    int maxEvents = Math.max(0, getInt(args, "max_events", 200));
                    int maxHotspots = Math.max(1, getInt(args, "max_hotspots", 20));

                    List<String> sections = getStringList(args.get("sections"));
                    if (sections.isEmpty()) {
                        sections = List.of("summary", "hotspots", "events_sample", "paths");
                    }

                    Path resultPath;
                    if (providedPath != null && !providedPath.isBlank()) {
                        resultPath = Path.of(providedPath);
                    } else {
                        if (runId == null || runId.isBlank()) {
                            return Mono.just(error("run_id or path must be provided"));
                        }
                        if (!RESULT_PERSISTER.exists(runId)) {
                            return Mono.just(error("Unknown run_id: " + runId));
                        }
                        resultPath = RESULT_PERSISTER.resolve(runId);
                    }

                    if (!Files.exists(resultPath)) {
                        if (!resultPath.isAbsolute()) {
                            Path alt = RESULT_PERSISTER.getStorageDir().resolve(resultPath).normalize();
                            if (Files.exists(alt)) {
                                resultPath = alt;
                            } else {
                                return Mono.just(error("Result path not found: " + resultPath));
                            }
                        } else {
                            return Mono.just(error("Result path not found: " + resultPath));
                        }
                    }
                    resultPath = resultPath.toAbsolutePath();

                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> stored = MAPPER.readValue(resultPath.toFile(), Map.class);
                        Map<String, Object> response = new HashMap<>();
                        response.put("run_id", stored.getOrDefault("run_id", runId));
                        response.put("result_path", resultPath.toAbsolutePath().toString());

                        if (sections.contains("summary")) {
                            Map<String, Object> summary = new HashMap<>();
                            summary.put("timestamp", stored.get("timestamp"));
                            summary.put("cache", stored.get("cache"));
                            summary.put("compile", stored.get("compile"));
                            summary.put("execution", stored.get("execution"));
                            summary.put("defines", stored.get("defines"));
                            summary.put("instrumented_points", stored.get("instrumented_points"));
                            response.put("summary", summary);
                        }

                        if (sections.contains("hotspots")) {
                            @SuppressWarnings("unchecked")
                            List<Object> hotspots = (List<Object>) stored.getOrDefault("hotspots", List.of());
                            response.put("hotspots", hotspots.stream()
                                    .limit(maxHotspots)
                                    .collect(Collectors.toList()));
                        }

                        if (sections.contains("events_sample")) {
                            @SuppressWarnings("unchecked")
                            List<Object> events = (List<Object>) stored.getOrDefault("events", List.of());
                            response.put("events_sample", events.stream()
                                    .limit(maxEvents < 0 ? events.size() : maxEvents)
                                    .collect(Collectors.toList()));
                            response.put("events_total", events.size());
                        }

                        if (sections.contains("metadata")) {
                            response.put("metadata", stored.get("metadata"));
                        }

                        if (sections.contains("paths")) {
                            Map<String, Object> paths = new HashMap<>();
                            if (stored.containsKey("trace_path")) {
                                paths.put("trace_path", stored.get("trace_path"));
                            }
                            if (stored.containsKey("working_directory")) {
                                paths.put("working_directory", stored.get("working_directory"));
                            }
                            response.put("paths", paths);
                        }

                        return Mono.just(success(toJson(response)));
                    } catch (IOException e) {
                        return Mono.just(error("Failed to read run result: " + e.getMessage()));
                    }
                })
                .build();
    }

    static McpStatelessServerFeatures.SyncToolSpecification toSync(
            McpServerFeatures.AsyncToolSpecification asyncTool) {
        var handler = asyncTool.callHandler();
        var tool = asyncTool.tool();

        return new McpStatelessServerFeatures.SyncToolSpecification.Builder()
                .tool(tool)
                .callHandler((ctx, request) -> handler.apply(null, request).block())
                .build();
    }

    private static McpSchema.CallToolResult success(String text) {
        return new McpSchema.CallToolResult.Builder()
                .content(List.of(new McpSchema.TextContent(text)))
                .isError(false)
                .build();
    }

    private static McpSchema.CallToolResult error(String message) {
        return new McpSchema.CallToolResult.Builder()
                .content(List.of(new McpSchema.TextContent(message)))
                .isError(true)
                .build();
    }

    private static String getString(Map<String, Object> args, String key) {
        Object value = args.get(key);
        return value instanceof String str ? str : null;
    }

    private static boolean getBoolean(Map<String, Object> args, String key, boolean defaultValue) {
        Object value = args.get(key);
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String str) {
            return Boolean.parseBoolean(str.trim());
        }
        return defaultValue;
    }

    private static int getInt(Map<String, Object> args, String key, int defaultValue) {
        Object value = args.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String str) {
            try {
                return Integer.parseInt(str.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    private static List<Integer> getIntegerList(Object value) {
        if (value instanceof List<?> list) {
            List<Integer> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Number number) {
                    result.add(number.intValue());
                }
            }
            return result;
        }
        return List.of();
    }

    private static CacheConfiguration parseCacheConfig(Object value) {
        if (value instanceof Map<?, ?> map) {
            int setBits = asInt(map, "set_bits", 5);
            int lines = asInt(map, "lines_per_set", 1);
            int blockBits = asInt(map, "block_bits", 5);
            return new CacheConfiguration(setBits, lines, blockBits);
        }
        return CacheConfiguration.defaultConfig();
    }

    private static List<String> getStringList(Object value) {
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof String str && !str.isBlank()) {
                    result.add(str.trim());
                }
            }
            return result;
        }
        return List.of();
    }

    private static List<Integer> computeBlockSizes(int start, int end, int step) {
        List<Integer> values = new ArrayList<>();
        if (step <= 0) {
            return values;
        }
        if (start <= end) {
            for (int v = start; v <= end; v += step) {
                values.add(v);
            }
        } else {
            for (int v = start; v >= end; v -= step) {
                values.add(v);
            }
        }
        return values;
    }

    private static int asInt(Map<?, ?> map, String key, int defaultValue) {
        Object raw = map.get(key);
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw instanceof String str) {
            try {
                return Integer.parseInt(str.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    private static String toJson(Object value) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private static final class CompileWorkflow {

        private final String code;
        private final List<Integer> instrumentIds;
        private final String fileName;
        private final CacheConfiguration cacheConfiguration;
        private final List<String> compileDefines;
        private final int maxHotspots;
        private final int maxEvents;
        private final boolean returnTracePath;
        private final String saveTraceTo;
        private final String resultsPath;

        private CompileWorkflow(String code,
                                List<Integer> instrumentIds,
                                String fileName,
                                CacheConfiguration cacheConfiguration,
                                List<String> compileDefines,
                                int maxHotspots,
                                int maxEvents,
                                boolean returnTracePath,
                                String saveTraceTo,
                                String resultsPath) {
            this.code = code;
            this.instrumentIds = instrumentIds;
            this.fileName = fileName;
            this.cacheConfiguration = cacheConfiguration;
            this.compileDefines = compileDefines == null ? List.of() : List.copyOf(compileDefines);
            this.maxHotspots = maxHotspots;
            this.maxEvents = maxEvents;
            this.returnTracePath = returnTracePath;
            this.saveTraceTo = saveTraceTo;
            this.resultsPath = resultsPath;
        }

        Map<String, Object> execute() throws IOException, InterruptedException {
            ArrayAccessDetector detector = new ArrayAccessDetector();
            List<ArrayAccess> accesses = detector.detect(code);

            Set<Integer> selectedIds = Set.copyOf(instrumentIds);
            List<InstrumentationPoint> points = new ArrayList<>();
            AtomicInteger counter = new AtomicInteger(1);

            for (int i = 0; i < accesses.size(); i++) {
                if (selectedIds.contains(i)) {
                    points.add(new InstrumentationPoint(counter.getAndIncrement(), accesses.get(i)));
                }
            }

            CodeInstrumenter instrumenter = new CodeInstrumenter();
            InstrumentedProgram program = instrumenter.instrument(code, points);

            CCompilerRunner runner = new CCompilerRunner();
            List<String> compileFlags = compileDefines.stream()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(define -> define.startsWith("-D") ? define : "-D" + define)
                    .collect(Collectors.toList());
            RunResult runResult = runner.compileAndRun(fileName, program, compileFlags);

            Map<String, Object> response = new HashMap<>();
            response.put("instrumented_code", program.getSourceCode());
            response.put("instrumented_points", points.size());
            if (!compileDefines.isEmpty()) {
                response.put("defines", compileDefines);
            }

            Map<String, Object> compileInfo = new HashMap<>();
            compileInfo.put("exit_code", runResult.getCompileExitCode());
            compileInfo.put("stdout", runResult.getCompileStdout());
            compileInfo.put("stderr", runResult.getCompileStderr());
            response.put("compile", compileInfo);

            if (!runResult.isCompiled()) {
                response.put("status", "compile_failed");
                return response;
            }

            Map<String, Object> execInfo = new HashMap<>();
            execInfo.put("exit_code", runResult.getExecutionExitCode());
            execInfo.put("stdout", runResult.getExecutionStdout());
            execInfo.put("stderr", runResult.getExecutionStderr());
            response.put("execution", execInfo);
            response.put("status", "executed");
            response.put("trace_bytes", runResult.getTrace() != null ? runResult.getTrace().length() : 0);

            if (runResult.getTrace() != null && !runResult.getTrace().isBlank()) {
                CacheAnalyzer analyzer = new CacheAnalyzer();
                CacheSummary summary = analyzer.analyze(runResult.getTrace(), cacheConfiguration,
                        runResult.getInstrumentedPoints());

                Map<String, Object> cacheInfo = new HashMap<>();
                cacheInfo.put("hits", summary.getHits());
                cacheInfo.put("misses", summary.getMisses());
                cacheInfo.put("evictions", summary.getEvictions());
                cacheInfo.put("hotspots", CacheInsights.hotspots(summary, runResult.getInstrumentedPoints(), maxHotspots));
                cacheInfo.put("hotspot_metric", "misses+evictions");
                cacheInfo.put("total_events", summary.getEvents().size());
                List<Map<String, Object>> eventSample = CacheInsights.eventSample(summary, maxEvents);
                cacheInfo.put("events_sample", eventSample);
                cacheInfo.put("events_sample_count", eventSample.size());
                response.put("cache", cacheInfo);

                handleTracePersistence(runResult, response);

                Map<String, Object> recordMetadata = new HashMap<>();
                recordMetadata.put("tool", "compile_and_run_c");
                recordMetadata.put("defines", compileDefines);

                RunResultPersister.RunRecord record = RESULT_PERSISTER.persist(
                        code,
                        runResult,
                        summary,
                        cacheConfiguration,
                        points,
                        compileDefines,
                        recordMetadata
                );

                Map<String, Object> runMeta = new HashMap<>();
                runMeta.put("run_id", record.runId());
                runMeta.put("result_path", record.path().toAbsolutePath().toString());
                runMeta.put("storage_dir", RESULT_PERSISTER.getStorageDir().toString());
                if (resultsPath != null && !resultsPath.isBlank()) {
                    String trimmedPath = resultsPath.trim();
                    Path target = Path.of(trimmedPath).toAbsolutePath();
                    Path destination;
                    try {
                        boolean treatAsDirectory = Files.exists(target) && Files.isDirectory(target)
                                || trimmedPath.endsWith("/") || trimmedPath.endsWith("\\");

                        if (treatAsDirectory) {
                            Files.createDirectories(target);
                            destination = target.resolve(record.path().getFileName());
                        } else {
                            destination = target;
                            Path parent = destination.getParent();
                            if (parent != null) {
                                Files.createDirectories(parent);
                            }
                        }
                        Files.copy(record.path(), destination, StandardCopyOption.REPLACE_EXISTING);
                        runMeta.put("saved_to", destination.toString());
                    } catch (IOException e) {
                        runMeta.put("save_error", e.getMessage());
                    }
                }
                response.put("run_result", runMeta);
                response.put("run_id", record.runId());
                response.put("result_path", runMeta.get("result_path"));
            }

            return response;
        }

        private void handleTracePersistence(RunResult runResult, Map<String, Object> response) {
            Path workingDir = runResult.getWorkingDirectory();
            Path tracePath = workingDir != null ? workingDir.resolve("trace.log") : null;
            if (tracePath == null || !Files.exists(tracePath)) {
                return;
            }

            Map<String, Object> traceInfo = new HashMap<>();

            if (returnTracePath) {
                traceInfo.put("trace_path", tracePath.toAbsolutePath().toString());
            }

            if (saveTraceTo != null && !saveTraceTo.isBlank()) {
                try {
                    Path target = Path.of(saveTraceTo).toAbsolutePath();
                    Path parent = target.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    Files.copy(tracePath, target, StandardCopyOption.REPLACE_EXISTING);
                    traceInfo.put("trace_saved_to", target.toString());
                } catch (IOException e) {
                    traceInfo.put("trace_save_error", e.getMessage());
                }
            }

            if (!traceInfo.isEmpty()) {
                response.put("trace", traceInfo);
            }
        }
    }

    private static final class BlockSweepWorkflow {
        private final String code;
        private final List<Integer> instrumentIds;
        private final String fileName;
        private final CacheConfiguration cacheConfiguration;
        private final List<String> compileDefines;
        private final String macro;
        private final List<Integer> blockSizes;

        private BlockSweepWorkflow(String code,
                                    List<Integer> instrumentIds,
                                    String fileName,
                                    CacheConfiguration cacheConfiguration,
                                    List<String> compileDefines,
                                    String macro,
                                    List<Integer> blockSizes) {
            this.code = code;
            this.instrumentIds = instrumentIds;
            this.fileName = fileName;
            this.cacheConfiguration = cacheConfiguration;
            this.compileDefines = compileDefines == null ? List.of() : List.copyOf(compileDefines);
            this.macro = macro.trim();
            this.blockSizes = List.copyOf(blockSizes);
        }

        Map<String, Object> execute() throws IOException, InterruptedException {
            ArrayAccessDetector detector = new ArrayAccessDetector();
            List<ArrayAccess> accesses = detector.detect(code);

            Set<Integer> selectedIds = instrumentIds.isEmpty() ? Set.of() : Set.copyOf(instrumentIds);
            List<InstrumentationPoint> points = new ArrayList<>();
            AtomicInteger counter = new AtomicInteger(1);
            for (int i = 0; i < accesses.size(); i++) {
                if (selectedIds.isEmpty() || selectedIds.contains(i)) {
                    points.add(new InstrumentationPoint(counter.getAndIncrement(), accesses.get(i)));
                }
            }

            CodeInstrumenter instrumenter = new CodeInstrumenter();
            InstrumentedProgram program = instrumenter.instrument(code, points);

            CCompilerRunner runner = new CCompilerRunner();
            CacheAnalyzer analyzer = new CacheAnalyzer();
            List<Map<String, Object>> results = new ArrayList<>();

            int bestBlock = -1;
            int bestScore = Integer.MAX_VALUE;

            for (int size : blockSizes) {
                List<String> flags = new ArrayList<>();
                for (String define : compileDefines) {
                    String trimmed = define.trim();
                    if (trimmed.isEmpty()) {
                        continue;
                    }
                    flags.add(trimmed.startsWith("-D") ? trimmed : "-D" + trimmed);
                }
                flags.add("-D" + macro + "=" + size);

                RunResult result = runner.compileAndRun(fileName, program, flags);
                Map<String, Object> entry = new HashMap<>();
                entry.put("block_size", size);
                entry.put("compile_exit", result.getCompileExitCode());
                entry.put("execution_exit", result.getExecutionExitCode());

                if (!result.isCompiled()) {
                    entry.put("status", "compile_failed");
                } else if (result.getExecutionExitCode() != 0) {
                    entry.put("status", "execution_failed");
                } else {
                    entry.put("status", "ok");
                }

                if (result.isCompiled()) {
                    CacheSummary summary = analyzer.analyze(result.getTrace(), cacheConfiguration,
                            result.getInstrumentedPoints());
                    entry.put("hits", summary.getHits());
                    entry.put("misses", summary.getMisses());
                    entry.put("evictions", summary.getEvictions());
                    entry.put("hotspots", CacheInsights.hotspots(summary, result.getInstrumentedPoints(), 5));

                    int score = summary.getMisses() + summary.getEvictions();
                    if (result.getExecutionExitCode() == 0 && score < bestScore) {
                        bestScore = score;
                        bestBlock = size;
                    }

                    Map<String, Object> recordMetadata = new HashMap<>();
                    recordMetadata.put("tool", "sweep_block_sizes");
                    recordMetadata.put("block_macro", macro);
                    recordMetadata.put("block_size", size);
                    recordMetadata.put("defines", compileDefines);

                    RunResultPersister.RunRecord record = RESULT_PERSISTER.persist(
                            code,
                            result,
                            summary,
                            cacheConfiguration,
                            points,
                            compileDefines,
                            recordMetadata
                    );
                    entry.put("run_id", record.runId());
                    entry.put("result_path", record.path().toAbsolutePath().toString());
                    entry.put("storage_dir", RESULT_PERSISTER.getStorageDir().toString());
                }

                results.add(entry);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("block_macro", macro);
            response.put("instrumented_points", points.size());
            response.put("instrumented_code", program.getSourceCode());
            response.put("defines", compileDefines);
            response.put("block_sizes", blockSizes);
            response.put("cache", Map.of(
                    "set_bits", cacheConfiguration.setBits(),
                    "lines_per_set", cacheConfiguration.linesPerSet(),
                    "block_bits", cacheConfiguration.blockBits()
            ));
            response.put("results", results);
            response.put("best_block_size", bestBlock);
            response.put("best_score", bestBlock >= 0 ? bestScore : null);
            return response;
        }
    }
}
