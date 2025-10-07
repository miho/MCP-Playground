package com.embeddedcc.compiler;

import com.embeddedcc.instrumentation.InstrumentedProgram;
import com.embeddedcc.util.ResourceHelper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Compiles instrumented C programs and executes them while collecting traces.
 */
public class CCompilerRunner {

    private static final String COMPILER_OVERRIDE_ENV = "EMBEDDED_CC_COMPILER";
    private static final String COMPILER_OVERRIDE_PROPERTY = "embeddedcc.compiler";

    private final List<String> compilerCommand;
    private final List<String> compileFlags;
    private final List<String> linkFlags;

    public CCompilerRunner() {
        this(resolveCompilerCommand(),
                List.of("-std=c11", "-O0", "-g", "-Wall", "-Wextra"),
                List.of());
    }

    public CCompilerRunner(String compilerCommand,
                           List<String> compileFlags,
                           List<String> linkFlags) {
        this(parseCommandString(compilerCommand), compileFlags, linkFlags);
    }

    private CCompilerRunner(List<String> compilerCommand,
                            List<String> compileFlags,
                            List<String> linkFlags) {
        if (compilerCommand == null || compilerCommand.isEmpty()) {
            throw new IllegalArgumentException("compilerCommand must not be empty");
        }
        this.compilerCommand = List.copyOf(compilerCommand);
        this.compileFlags = List.copyOf(compileFlags);
        this.linkFlags = List.copyOf(linkFlags);
    }

    public RunResult compileAndRun(String fileName,
                                   InstrumentedProgram program) throws IOException, InterruptedException {
        Path tempDir = Files.createTempDirectory("embedded-c");
        String normalizedName = fileName == null || fileName.isBlank() ? "program.c" : fileName;
        Path sourcePath = tempDir.resolve(normalizedName);

        Files.writeString(sourcePath, program.getSourceCode(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        ResourceHelper.copyToPath("instrumentation/instrumentation.h", tempDir.resolve("instrumentation.h"));
        ResourceHelper.copyToPath("instrumentation/instrumentation.c", tempDir.resolve("instrumentation.c"));

        List<String> compileCommand = new ArrayList<>();
        compileCommand.addAll(compilerCommand);
        compileCommand.addAll(compileFlags);
        compileCommand.add(sourcePath.getFileName().toString());
        compileCommand.add("instrumentation.c");
        compileCommand.addAll(linkFlags);
        compileCommand.add("-o");
        String executableName = isWindows() ? "program.exe" : "program";
        compileCommand.add(executableName);

        ProcessBuilder compileBuilder = new ProcessBuilder(compileCommand);
        compileBuilder.directory(tempDir.toFile());

        Process compileProcess;
        try {
            compileProcess = compileBuilder.start();
        } catch (IOException e) {
            throw new IOException(buildCompilerLaunchErrorMessage(), e);
        }
        ProcessStreams compileStreams = ProcessStreams.collect(compileProcess);
        int compileCode = compileProcess.waitFor();

        if (compileCode != 0) {
            return RunResult.compileFailure(tempDir, compileCode, compileStreams);
        }

        ProcessBuilder runBuilder = new ProcessBuilder(
                isWindows() ? ("." + java.io.File.separator + executableName) : "./" + executableName);
        runBuilder.directory(tempDir.toFile());
        runBuilder.environment().put("TRACE_OUTPUT_PATH", tempDir.resolve("trace.log").toString());

        Process runProcess = runBuilder.start();
        ProcessStreams runStreams = ProcessStreams.collect(runProcess);
        int runCode = runProcess.waitFor();

        String trace = "";
        Path tracePath = tempDir.resolve("trace.log");
        if (Files.exists(tracePath)) {
            trace = Files.readString(tracePath, StandardCharsets.UTF_8);
        }

        return RunResult.success(tempDir, compileStreams, runStreams, runCode, trace, program.getIdLookup());
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name");
        return os != null && os.toLowerCase().contains("win");
    }

    private static List<String> resolveCompilerCommand() {
        String override = System.getProperty(COMPILER_OVERRIDE_PROPERTY);
        if (override == null || override.isBlank()) {
            override = System.getenv(COMPILER_OVERRIDE_ENV);
        }
        if (override == null || override.isBlank()) {
            override = System.getenv("CC");
        }
        if (override != null && !override.isBlank()) {
            return parseCommandString(override);
        }
        if (isWindows()) {
            return List.of("gcc");
        }
        return List.of("gcc");
    }

    private static List<String> parseCommandString(String command) {
        if (command == null) {
            return List.of();
        }
        command = command.trim();
        if (command.isEmpty()) {
            return List.of();
        }

        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
                continue;
            }
            if (Character.isWhitespace(c) && !inQuotes) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }

        if (current.length() > 0) {
            tokens.add(current.toString());
        }

        return tokens;
    }

    private String buildCompilerLaunchErrorMessage() {
        String commandString = String.join(" ", compilerCommand);
        return "Failed to launch C compiler '" + commandString + "'. "
                + "Make sure the compiler is installed and on the PATH, "
                + "or set the '" + COMPILER_OVERRIDE_ENV + "' environment variable "
                + "or '-D" + COMPILER_OVERRIDE_PROPERTY + "' system property to point to the compiler command.";
    }
}
