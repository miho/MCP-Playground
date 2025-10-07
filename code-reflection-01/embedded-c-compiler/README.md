# Embedded C Compiler Playground

Interactive JavaFX workbench that instruments C programs, compiles them via the embedded toolchain, and analyses cache behaviour using a lightweight simulator. The project also exposes an MCP server so language models can orchestrate compilation, execution, and analysis remotely.

## Features

- **C Compiler Integration** – Generates instrumented sources, compiles them with `gcc`, and executes the resulting binary directly from Java.
- **Memory Access Instrumentation** – Detects array accesses, lets you choose which to trace, and injects `TRACE_LOAD`/`TRACE_STORE` calls automatically.
- **Cache Simulation** – Converts runtime traces into cache hit/miss/eviction summaries using an embedded LRU simulator.
- **JavaFX UI** – Left pane displays syntax-highlighted source code, right pane provides instrumentation controls, execution logs, cache insights, and a block-size sweep tool.
- **Built-in MCP Server Controls** – Launch/stop the MCP server directly from the UI, configure HTTP/stdio modes, and monitor status.
- **Theme Toggle** – Switch instantly between dark and light themes to match your environment.
- **MCP Server** – Tools `analyze_c_code` and `compile_and_run_c` expose the same workflow to an LLM via the Model Context Protocol (HTTP or stdio).

## Getting Started

> **Note:** If Gradle cannot download its distribution because of network restrictions, point `GRADLE_USER_HOME` at a writable folder that already contains the required version.

### Run the JavaFX Application

```bash
GRADLE_USER_HOME=./.gradle ./gradlew run
```

1. Pick a sample (matrix multiply or blocked transpose) from the dropdown.
2. Click **Analyze** to refresh instrumentation candidates after editing code.
3. Tune the cache geometry (set bits, lines/set, block bits).
4. Select memory accesses to trace and press **Instrument & Run**.
5. Review compilation output, instrumented source (with syntax highlighting), and cache summary.
6. Cache misses and evictions are highlighted directly in the code view.

### Block Size Sweep Example

1. Load the **Blocked Transpose** sample (now parameterised via `BLOCK_SIZE`).
2. Select the `A[i][j]` and `B[j][i]` accesses for instrumentation.
3. Configure the cache to match your target hardware (e.g., `s=5`, `E=1`, `b=5`).
4. Choose a sweep range, e.g. start `4`, end `64`, step `4`, and click **Sweep Block Sizes**.
5. The results table ranks each block size by cache misses; the best configuration is highlighted in green.

Both the UI and MCP endpoints report the `hotspots` array (sorted by misses + evictions) so you can focus on the hottest memory accesses without streaming the full trace. Include `max_hotspots` or `max_events` when calling the MCP tool to tune the response size, and set `return_trace_path`/`save_trace_to` to retain the raw trace on disk if deeper offline analysis is required. Full run artefacts (summary, hotspots, every cache event, trace location) are persisted under `~/.embeddedcc/runs` and can be revisited later via the `get_run_result` tool.

### MCP Server Controls in the UI

- Use the control bar at the top of the window to launch or stop the embedded MCP server.
- Click **Settings** to switch between stdio and HTTP transport or adjust the HTTP port.
- The status indicator mirrors the server log stream; green means the process is live.
- The ☾/☼ button toggles between dark and light themes.
- The right-hand panel now lists the hottest instrumented lines with a heat-map colour scale and shows the run artefact path saved on disk.

### Retrieve Past Run Results

Every call to `compile_and_run_c` (and the UI’s local run button) writes a JSON artefact to disk. You can reopen it later without rerunning the program:

```json
{
  "type": "get_run_result",
  "run_id": "run-2025-10-07T14-43-19Z-383cc803",
  "sections": ["summary", "hotspots", "events_sample"],
  "max_hotspots": 5,
  "max_events": 50
}
```

Include a `path` instead of `run_id` if you have copied the file elsewhere. Use `results_path` in `compile_and_run_c` to copy the JSON alongside the default `~/.embeddedcc/runs` location.

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
- `compile_and_run_c` – Instruments selected IDs, compiles, executes, and replies with program output plus cache statistics and hotspot rankings. Options: `defines`, `max_hotspots`, `max_events`, `return_trace_path`, `save_trace_to`, `results_path`.
- `sweep_block_sizes` – Runs multiple compilations with varying block sizes (via `BLOCK_SIZE` or a custom macro) and returns cache statistics plus hotspot summaries for each run.
- `get_run_result` – Fetches persisted analysis for a previous run. Provide `run_id` (or a direct `path`) and optional `sections`, `max_hotspots`, `max_events` to control the payload size.

### Configure the C Compiler

The tool assumes `gcc` is available on the system `PATH`. If you need to point to a different compiler (e.g., `clang` or a custom MinGW installation), set an override before launching:

```bash
# Bash
export EMBEDDED_CC_COMPILER="/path/to/clang"
GRADLE_USER_HOME=./.gradle ./gradlew run

# PowerShell (current session)
$env:EMBEDDED_CC_COMPILER = "C:\\msys64\\mingw64\\bin\\gcc.exe"
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
- `transpose_blocking.c` honours the `BLOCK_SIZE` macro, enabling automated sweeps.
- The JavaFX editor applies basic C syntax highlighting; customize colours by editing the theme CSS under `src/main/resources/ui/`.
- Complete run artefacts are saved to `~/.embeddedcc/runs`. Use the `get_run_result` MCP tool—or open the files directly—to inspect full hotspot/event breakdowns without rerunning the program.
- Set the environment variable `EMBEDDED_CC_RUNS_DIR` (or JVM property `-Dembeddedcc.runs.dir=...`) to override where run artefacts are stored.
