#!/bin/bash

# Test script for HTTP mode (LM-Studio)
echo "Testing OpenRewrite MCP Server in HTTP mode..."
echo "=========================================="

# Start server in HTTP mode
echo "Starting server on http://localhost:3001/mcp"
java -jar build/libs/openrewrite-mcp-server.jar -t http -p 3001 &
SERVER_PID=$!

# Wait for server to start
sleep 3

# Test server health
echo "Testing server health..."
curl -s http://localhost:3001/mcp \
  -X POST \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "method": "initialize",
    "params": {
      "protocolVersion": "2024-11-05",
      "clientInfo": {
        "name": "test-client",
        "version": "1.0.0"
      },
      "capabilities": {}
    },
    "id": 1
  }' | jq '.'

echo ""
echo "Testing list recipes..."
curl -s http://localhost:3001/mcp \
  -X POST \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{
    "jsonrpc": "2.0",
    "method": "tools/call",
    "params": {
      "name": "listRecipes",
      "arguments": {}
    },
    "id": 2
  }' | python3 -m json.tool 2>/dev/null || echo "Response received (install jq for better formatting)"

# Cleanup
echo ""
echo "Shutting down server..."
kill $SERVER_PID

echo "Test complete!"