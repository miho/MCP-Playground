# Embedded C Compiler Playground

Interactive JavaFX workbench that instruments C programs, compiles them via the embedded toolchain, and analyses cache behaviour using a lightweight simulator. The project also exposes an MCP server so language models can orchestrate compilation, execution, and analysis remotely.

## Features

- **C Compiler Integration** – Generates instrumented sources, compiles them with `gcc`, and executes the resulting binary directly from Java.
- **Memory Access Instrumentation** – Detects array accesses, lets you choose which to trace, and injects `TRACE_LOAD`/`TRACE_STORE` calls automatically.
- **Cache Simulation** – Converts runtime traces into cache hit/miss/eviction summaries using an embedded LRU simulator.
- **JavaFX UI** – Left pane displays editable source code, right pane provides instrumentation controls, execution logs, and cache insights.
- **MCP Server** – Tools `analyze_c_code` and `compile_and_run_c` expose the same workflow to an LLM via the Model Context Protocol (HTTP or stdio).

## Getting Started

> **Note:** If Gradle cannot download its distribution because of network restrictions, point `GRADLE_USER_HOME` at a writable folder that already contains the required version.

### Run the JavaFX Application

```bash
GRADLE_USER_HOME=./.gradle ./gradlew run
```

1. Pick a sample (matrix multiply or blocked transpose) from the dropdown.
2. Click **Analyze** to refresh instrumentation candidates after editing code.
3. Select memory accesses to trace and press **Instrument & Run**.
4. Review compilation output, instrumented source, and cache summary.
5. Cache misses and evictions are highlighted directly in the code view.

### Launch the MCP Server

Expose the workflow to an LLM client via HTTP on port 8085:

```bash
GRADLE_USER_HOME=./.gradle ./gradlew runServer --args="--http 8085"
```

Or run in stdio mode (default) for local MCP clients:

```bash
GRADLE_USER_HOME=./.gradle ./gradlew runServer
```

Available tools:

- `analyze_c_code` – Returns functions and array access candidates.
- `compile_and_run_c` – Instruments selected IDs, compiles, executes, and replies with program output plus cache statistics.

### Configure the C Compiler

The tool assumes `gcc` is available on the system `PATH`. If you need to point to a different compiler (e.g., `clang` or a custom MinGW installation), set an override before launching:

```bash
# Bash / PowerShell
export EMBEDDED_CC_COMPILER="/path/to/clang"
GRADLE_USER_HOME=./.gradle ./gradlew run
```

You can also pass `-Dembeddedcc.compiler="C:\\msys64\\usr\\bin\\gcc.exe"` on the JVM command line. Multi-part commands are allowed; quotes keep arguments with spaces together.

## Project Layout

```
src/main/java/com/embeddedcc/
  analysis/         Cache simulator wiring and orchestration service
  cache/            Adapted cache simulator implementation
  compiler/         C compiler & process execution helpers
  instrumentation/  Source analysis and instrumentation utilities
  mcp/              MCP server entry point and tool definitions
  ui/               JavaFX application and UI helpers
src/main/resources/
  csamples/         Example C programs
  instrumentation/  Runtime support (TRACE_LOAD/TRACE_STORE)
  ui/styles.css     Styling for the JavaFX client
```

## Testing

Unit tests live under `src/test/java`. Execute them with:

```bash
GRADLE_USER_HOME=./.gradle ./gradlew test
```

If you cannot fetch the Gradle distribution due to sandbox restrictions, run the tests from an environment where Gradle is available or copy an existing distribution into `./.gradle` before executing the commands above.

## Notes

- The instrumentation runtime writes cache traces to `TRACE_OUTPUT_PATH` (defaults to `trace.log` inside the temporary build folder).
- The simulator defaults to 32-byte blocks, one line per set, and 32 sets (5/1/5 configuration). Adjust the JSON payload sent to `compile_and_run_c` to experiment with other cache shapes.
- When compiling on Windows, ensure a POSIX-compatible toolchain (e.g., MSYS2 or WSL) is available so `gcc` can be invoked successfully.
