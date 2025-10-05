# Distribution Guide

## Creating a Runtime Distribution

The project includes a `createRuntime` task that uses jpackage to create a self-contained application with bundled JRE and JavaFX.

### Build the Runtime

```bash
./gradlew createRuntime
```

This creates a runtime image in `build/jpackage/mcp-sim/` with:
- Native launcher: `bin/mcp-sim`
- Bundled JRE with JavaFX modules
- All application JARs in `lib/app/`
- Platform-specific natives

### Distribution Structure

```
mcp-sim/
├── bin/
│   └── mcp-sim              # Native launcher
└── lib/
    ├── app/                  # Application JARs
    │   ├── mcp-sim-1.0.0.jar
    │   ├── javafx-*.jar
    │   └── ... (all dependencies)
    ├── libapplauncher.so
    └── runtime/              # Bundled JRE
        └── lib/              # JRE native libraries
```

### Using the Runtime

1. **Extract the distribution** (if packaged as tar.gz/zip)
2. **Run the application:**
   ```bash
   ./mcp-sim/bin/mcp-sim --mcp-mode stdio
   ```

### For Claude Desktop

Add to `claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "traffic-sim-ui": {
      "command": "/path/to/mcp-sim/bin/mcp-sim",
      "args": ["--mcp-mode", "stdio"]
    }
  }
}
```

**Important:** Use the **absolute path** to the `mcp-sim` executable.

### Platform Support

The runtime is **platform-specific**:
- Linux: Creates ELF executable with `.so` libraries
- Windows: Would create `.exe` with `.dll` libraries
- macOS: Would create Mach-O executable with `.dylib` libraries

To create distributions for other platforms, build on that platform or use cross-compilation tools.

### Packaging for Distribution

Create a compressed archive:

**Linux/macOS:**
```bash
cd build/jpackage
tar czf mcp-sim-linux.tar.gz mcp-sim/
```

**Windows:**
```bash
cd build\jpackage
tar czf mcp-sim-windows.zip mcp-sim\
```

### Benefits of jpackage Distribution

✅ **Self-contained** - includes JRE, no Java installation required
✅ **Native launcher** - platform-specific executable
✅ **JavaFX included** - all runtime components bundled
✅ **Portable** - extract and run anywhere
✅ **Single directory** - easy to install/uninstall

### Comparison with Other Approaches

| Approach | Pros | Cons |
|----------|------|------|
| **jpackage (this)** | Self-contained, native, portable | Platform-specific build required |
| **Gradle run** | Simple, works everywhere | Requires Gradle + Java installed |
| **Fat JAR** | Single file | Missing JavaFX natives |
| **jlink** | Smaller size | Complex module configuration |

### Building for Multiple Platforms

To create distributions for all platforms:

1. **Linux**: Build on Linux machine
   ```bash
   ./gradlew createRuntime
   cd build/jpackage && tar czf mcp-sim-linux.tar.gz mcp-sim/
   ```

2. **Windows**: Build on Windows machine
   ```bash
   gradlew createRuntime
   cd build\jpackage
   tar czf mcp-sim-windows.zip mcp-sim\
   ```

3. **macOS**: Build on Mac
   ```bash
   ./gradlew createRuntime
   cd build/jpackage && tar czf mcp-sim-macos.tar.gz mcp-sim/
   ```

### Troubleshooting

**"No such file or directory"**
- Ensure you're using an absolute path in Claude Desktop config
- Check file permissions: `chmod +x mcp-sim/bin/mcp-sim`

**"Error while loading shared libraries"**
- The bundled JRE should have all libraries
- Check if `lib/runtime/lib/*.so` files exist

**JavaFX not found**
- Verify `javafx-*.jar` files are in `lib/app/`
- The jpackage build should include JavaFX modules automatically

### Development vs Production

**Development** (easiest):
```json
{
  "command": "./gradlew",
  "args": ["run", "--args=--mcp-mode stdio", "--console=plain"],
  "cwd": "/path/to/mcp-sim"
}
```

**Production** (self-contained):
```json
{
  "command": "/opt/mcp-sim/bin/mcp-sim",
  "args": ["--mcp-mode", "stdio"]
}
```

## Creating Installers

To create platform installers (`.msi`, `.dmg`, `.deb`):

Modify the `createRuntime` task to change `--type app-image` to:
- `--type msi` (Windows)
- `--type dmg` (macOS)
- `--type deb` (Linux)

This will create a native installer package instead of just an app image.
