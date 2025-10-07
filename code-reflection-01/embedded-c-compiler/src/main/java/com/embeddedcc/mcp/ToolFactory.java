package com.embeddedcc.mcp;

import com.embeddedcc.analysis.CacheAnalyzer;
import com.embeddedcc.analysis.CacheConfiguration;
import com.embeddedcc.analysis.CacheEvent;
import com.embeddedcc.analysis.CacheSummary;
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

    private ToolFactory() {
    }

    private static List<Map<String, Object>> buildHotspots(CacheSummary summary,
                                                           Map<Integer, InstrumentationPoint> points,
                                                           int limit) {
        Map<Integer, HotspotAccumulator> accum = new HashMap<>();
        for (CacheEvent event : summary.getEvents()) {
            HotspotAccumulator acc = accum.computeIfAbsent(event.id(), id -> new HotspotAccumulator(points.get(id), event));
            switch (event.type()) {
                case HIT -> acc.hits++;
                case MISS -> acc.misses++;
                case EVICTION -> acc.evictions++;
            }
        }

        return accum.values().stream()
                .sorted((a, b) -> Integer.compare(b.missLike(), a.missLike()))
                .limit(limit)
                .map(HotspotAccumulator::toMap)
                .collect(Collectors.toList());
    }

    private static List<Map<String, Object>> buildEventSample(CacheSummary summary, int maxEvents) {
        if (maxEvents == 0) {
            return List.of();
        }
        return summary.getEvents().stream()
                .limit(maxEvents < 0 ? Long.MAX_VALUE : maxEvents)
                .map(event -> {
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("id", event.id());
                    entry.put("line", event.line());
                    entry.put("type", event.type().name());
                    entry.put("label", event.label());
                    InstrumentationPoint point = event.source();
                    if (point != null) {
                        entry.put("expression", point.getAccess().getExpression());
                        entry.put("access_type", point.getAccess().getAccessType().name());
                    }
                    return entry;
                })
                .collect(Collectors.toList());
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

                    CompileWorkflow workflow = new CompileWorkflow(code, instrumentIds, fileName, cacheConfig,
                            defines, maxHotspots, maxEvents, returnTracePath, saveTraceTo);

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

        private CompileWorkflow(String code,
                                List<Integer> instrumentIds,
                                String fileName,
                                CacheConfiguration cacheConfiguration,
                                List<String> compileDefines,
                                int maxHotspots,
                                int maxEvents,
                                boolean returnTracePath,
                                String saveTraceTo) {
            this.code = code;
            this.instrumentIds = instrumentIds;
            this.fileName = fileName;
            this.cacheConfiguration = cacheConfiguration;
            this.compileDefines = compileDefines == null ? List.of() : List.copyOf(compileDefines);
            this.maxHotspots = maxHotspots;
            this.maxEvents = maxEvents;
            this.returnTracePath = returnTracePath;
            this.saveTraceTo = saveTraceTo;
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
                cacheInfo.put("hotspots", buildHotspots(summary, runResult.getInstrumentedPoints(), maxHotspots));
                cacheInfo.put("hotspot_metric", "misses+evictions");
                cacheInfo.put("total_events", summary.getEvents().size());
                List<Map<String, Object>> eventSample = buildEventSample(summary, maxEvents);
                cacheInfo.put("events_sample", eventSample);
                cacheInfo.put("events_sample_count", eventSample.size());
                response.put("cache", cacheInfo);

                handleTracePersistence(runResult, response);
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
                    entry.put("hotspots", buildHotspots(summary, result.getInstrumentedPoints(), 5));

                    int score = summary.getMisses() + summary.getEvictions();
                    if (result.getExecutionExitCode() == 0 && score < bestScore) {
                        bestScore = score;
                        bestBlock = size;
                    }
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

    private static final class HotspotAccumulator {
        private final int id;
        private final Integer line;
        private final String expression;
        private final String accessType;
        private final String label;
        private int hits;
        private int misses;
        private int evictions;

        private HotspotAccumulator(InstrumentationPoint point, CacheEvent event) {
            this.id = event.id();
            InstrumentationPoint source = point != null ? point : event.source();
            if (source != null) {
                this.line = source.getAccess().getLine();
                this.expression = source.getAccess().getExpression();
                this.accessType = source.getAccess().getAccessType().name();
            } else {
                this.line = event.line();
                this.expression = null;
                this.accessType = null;
            }
            this.label = event.label();
        }

        private int missLike() {
            return misses + evictions;
        }

        private Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("id", id);
            if (line != null) {
                map.put("line", line);
            }
            map.put("hits", hits);
            map.put("misses", misses);
            map.put("evictions", evictions);
            map.put("score", missLike());
            if (expression != null) {
                map.put("expression", expression);
            }
            if (accessType != null) {
                map.put("access_type", accessType);
            }
            map.put("label", label);
            return map;
        }
    }
}
