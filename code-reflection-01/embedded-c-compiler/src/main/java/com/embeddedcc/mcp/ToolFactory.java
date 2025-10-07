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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

final class ToolFactory {

    private static final ObjectMapper MAPPER = new ObjectMapper();

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

                    CompileWorkflow workflow = new CompileWorkflow(code, instrumentIds, fileName, cacheConfig);

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

        private CompileWorkflow(String code,
                                List<Integer> instrumentIds,
                                String fileName,
                                CacheConfiguration cacheConfiguration) {
            this.code = code;
            this.instrumentIds = instrumentIds;
            this.fileName = fileName;
            this.cacheConfiguration = cacheConfiguration;
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
            RunResult runResult = runner.compileAndRun(fileName, program);

            Map<String, Object> response = new HashMap<>();
            response.put("instrumented_code", program.getSourceCode());
            response.put("instrumented_points", points.size());

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

                List<Map<String, Object>> eventPayload = new ArrayList<>();
                for (CacheEvent event : summary.getEvents()) {
                    InstrumentationPoint point = event.source();
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("id", event.id());
                    entry.put("line", event.line());
                    entry.put("type", event.type().name());
                    entry.put("label", event.label());
                    if (point != null) {
                        entry.put("expression", point.getAccess().getExpression());
                        entry.put("access_type", point.getAccess().getAccessType().name());
                    }
                    eventPayload.add(entry);
                }

                cacheInfo.put("events", eventPayload);
                response.put("cache", cacheInfo);
            }

            return response;
        }
    }
}
