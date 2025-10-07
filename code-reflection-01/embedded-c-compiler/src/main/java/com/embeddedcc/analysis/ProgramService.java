package com.embeddedcc.analysis;

import com.embeddedcc.compiler.CCompilerRunner;
import com.embeddedcc.compiler.RunResult;
import com.embeddedcc.instrumentation.ArrayAccess;
import com.embeddedcc.instrumentation.ArrayAccessDetector;
import com.embeddedcc.instrumentation.CodeInstrumenter;
import com.embeddedcc.instrumentation.CodeStructureAnalyzer;
import com.embeddedcc.instrumentation.InstrumentationPoint;
import com.embeddedcc.instrumentation.InstrumentedProgram;
import com.embeddedcc.instrumentation.ProgramAnalysis;

import java.io.IOException;
import java.util.List;

public class ProgramService {

    private final ArrayAccessDetector arrayAccessDetector = new ArrayAccessDetector();
    private final CodeStructureAnalyzer structureAnalyzer = new CodeStructureAnalyzer();
    private final CodeInstrumenter instrumenter = new CodeInstrumenter();
    private final CCompilerRunner compilerRunner = new CCompilerRunner();
    private final CacheAnalyzer cacheAnalyzer = new CacheAnalyzer();

    public ProgramAnalysis analyze(String source) {
        List<ArrayAccess> accesses = arrayAccessDetector.detect(source);
        return new ProgramAnalysis(accesses, structureAnalyzer.findFunctions(source));
    }

    public InstrumentedProgram instrument(String source,
                                          List<InstrumentationPoint> points) {
        return instrumenter.instrument(source, points);
    }

    public RunResult compileAndRun(String fileName,
                                   InstrumentedProgram program) throws IOException, InterruptedException {
        return compileAndRun(fileName, program, List.of());
    }

    public RunResult compileAndRun(String fileName,
                                   InstrumentedProgram program,
                                   List<String> extraCompileFlags) throws IOException, InterruptedException {
        return compilerRunner.compileAndRun(fileName, program, extraCompileFlags);
    }

    public CacheSummary summarizeCache(RunResult runResult,
                                       CacheConfiguration configuration) {
        return cacheAnalyzer.analyze(runResult.getTrace(), configuration, runResult.getInstrumentedPoints());
    }
}
