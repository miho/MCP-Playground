#!/bin/bash

# Start the OpenRewrite MCP Server in HTTP mode
# This script starts the server that LM Studio can connect to

echo "Starting OpenRewrite MCP Server on port 3001..."
echo "Endpoint: http://localhost:3001/mcp"

# Use Gradle to run the server directly
echo "Running MCP server using Gradle..."
./gradlew runServer