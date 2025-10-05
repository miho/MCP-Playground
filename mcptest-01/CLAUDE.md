# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Model Context Protocol (MCP) server implementation in Java, providing two independent MCP servers:

1. **DateTimeServer** - Provides current date/time with customizable formatting
2. **ImageProcessingServer** - Provides image manipulation tools (info, resize, grayscale)

Both servers use the MCP Java SDK (v0.13.1) with stdio transport and Project Reactor for async operations.

## Architecture

### Server Pattern
Both servers follow the same architectural pattern:
- Use `StdioServerTransportProvider` for stdin/stdout communication
- Build with `McpServer.async()` for reactive/non-blocking operations
- Use `CountDownLatch` + shutdown hook to keep server running until interrupted
- Tools are defined as `AsyncToolSpecification` with `Mono<CallToolResult>` handlers
- All error handling returns `CallToolResult` with error text and `isError=true`

### Multi-Server Build System
The project uses Gradle Shadow plugin to create two separate executable JARs:
- Default application builds `datetime-server.jar` (mainClass: `DateTimeServer`)
- Custom task `imageServerJar` builds `image-processing-server.jar` (mainClass: `ImageProcessingServer`)

Both servers share the same source set and dependencies but produce independent executables.

## Build Commands

```bash
# Build both servers
./gradlew build

# Build DateTimeServer JAR only (default)
./gradlew shadowJar

# Build ImageProcessingServer JAR only
./gradlew imageServerJar

# Clean build artifacts
./gradlew clean
```

## Running Servers

```bash
# Run DateTimeServer (default)
./gradlew run

# Run from JAR
java -jar build/libs/datetime-server.jar

# Run ImageProcessingServer from JAR
java -jar build/libs/image-processing-server.jar
```

## Tool Schemas

### DateTimeServer Tools
- `get_datetime` - Returns formatted current date/time
  - Optional `format` parameter (default: "yyyy-MM-dd HH:mm:ss")

### ImageProcessingServer Tools
- `get_image_info` - Returns image dimensions, format, and color model
- `resize_image` - Resizes image with bilinear interpolation
- `convert_to_grayscale` - Converts image to grayscale

All image tools accept either:
- `image_path` - File path or HTTP(S) URL
- `image_data` - Base64 encoded image data

## Key Dependencies

- MCP Java SDK 0.13.1 (with BOM for version management)
- Project Reactor (via MCP SDK transitive dependency)
- Jackson (via MCP SDK for JSON)
- Java 17+ required
