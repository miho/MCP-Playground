@echo off
REM Start the OpenRewrite MCP Server in HTTP mode
REM This script starts the server that LM Studio can connect to

echo Starting OpenRewrite MCP Server on port 3001...
echo Endpoint: http://localhost:3001/mcp

REM Use Gradle to run the server directly
echo Running MCP server using Gradle...
call gradlew.bat runServer