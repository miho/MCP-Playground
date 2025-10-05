#!/bin/bash

# Test script to verify HTTP MCP server is working
# This script sends a simple MCP request to the server

PORT=8083
ENDPOINT="/mcp"
URL="http://localhost:${PORT}${ENDPOINT}"

echo "Testing HTTP MCP Server at ${URL}"
echo "=========================================="
echo ""

# Check if the server is reachable
echo "1. Testing server connectivity..."
if curl -s -o /dev/null -w "%{http_code}" "${URL}" > /dev/null 2>&1; then
    echo "   ✓ Server is reachable"
else
    echo "   ✗ Server is not reachable"
    echo ""
    echo "Please make sure the UI is running:"
    echo "  ./gradlew run"
    exit 1
fi

echo ""
echo "2. Sending MCP initialize request..."

# Send an MCP initialize request
RESPONSE=$(curl -s -X POST "${URL}" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "initialize",
    "params": {
      "protocolVersion": "2024-11-05",
      "capabilities": {},
      "clientInfo": {
        "name": "test-client",
        "version": "1.0.0"
      }
    }
  }')

if echo "${RESPONSE}" | grep -q '"serverInfo"'; then
    echo "   ✓ Server responded with server info"
    echo ""
    echo "Server Info:"
    echo "${RESPONSE}" | python3 -m json.tool 2>/dev/null || echo "${RESPONSE}"
else
    echo "   ✗ Unexpected response"
    echo ""
    echo "Response:"
    echo "${RESPONSE}"
fi

echo ""
echo "=========================================="
echo "Test complete!"
echo ""
echo "If the server is working correctly, you can now configure Claude Desktop:"
echo "See CLAUDE_DESKTOP_CONFIG.md for instructions"
