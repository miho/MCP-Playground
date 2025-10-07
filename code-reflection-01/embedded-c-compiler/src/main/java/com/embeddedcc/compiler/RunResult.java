package com.embeddedcc.compiler;

import com.embeddedcc.instrumentation.InstrumentationPoint;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

public final class RunResult {

    private final boolean compiled;
    private final Path workingDirectory;
    private final int compileExitCode;
    private final String compileStdout;
    private final String compileStderr;

    private final boolean executed;
    private final int executionExitCode;
    private final String executionStdout;
    private final String executionStderr;
    private final String trace;
    private final Map<Integer, InstrumentationPoint> instrumentedPoints;

    private RunResult(boolean compiled,
                      Path workingDirectory,
                      int compileExitCode,
                      String compileStdout,
                      String compileStderr,
                      boolean executed,
                      int executionExitCode,
                      String executionStdout,
                      String executionStderr,
                      String trace,
                      Map<Integer, InstrumentationPoint> instrumentedPoints) {
        this.compiled = compiled;
        this.workingDirectory = workingDirectory;
        this.compileExitCode = compileExitCode;
        this.compileStdout = compileStdout;
        this.compileStderr = compileStderr;
        this.executed = executed;
        this.executionExitCode = executionExitCode;
        this.executionStdout = executionStdout;
        this.executionStderr = executionStderr;
        this.trace = trace;
        this.instrumentedPoints = instrumentedPoints == null ? Map.of() : Map.copyOf(instrumentedPoints);
    }

    public static RunResult compileFailure(Path workingDirectory,
                                           int exitCode,
                                           ProcessStreams compileStreams) {
        return new RunResult(false,
                workingDirectory,
                exitCode,
                compileStreams.stdout(),
                compileStreams.stderr(),
                false,
                0,
                "",
                "",
                "",
                Map.of());
    }

    public static RunResult success(Path workingDirectory,
                                    ProcessStreams compileStreams,
                                    ProcessStreams executionStreams,
                                    int executionExitCode,
                                    String trace,
                                    Map<Integer, InstrumentationPoint> points) {
        return new RunResult(true,
                workingDirectory,
                0,
                compileStreams.stdout(),
                compileStreams.stderr(),
                true,
                executionExitCode,
                executionStreams.stdout(),
                executionStreams.stderr(),
                trace,
                points);
    }

    public boolean isCompiled() {
        return compiled;
    }

    public Path getWorkingDirectory() {
        return workingDirectory;
    }

    public int getCompileExitCode() {
        return compileExitCode;
    }

    public String getCompileStdout() {
        return compileStdout;
    }

    public String getCompileStderr() {
        return compileStderr;
    }

    public boolean isExecuted() {
        return executed;
    }

    public int getExecutionExitCode() {
        return executionExitCode;
    }

    public String getExecutionStdout() {
        return executionStdout;
    }

    public String getExecutionStderr() {
        return executionStderr;
    }

    public String getTrace() {
        return trace;
    }

    public Map<Integer, InstrumentationPoint> getInstrumentedPoints() {
        return Collections.unmodifiableMap(instrumentedPoints);
    }
}

