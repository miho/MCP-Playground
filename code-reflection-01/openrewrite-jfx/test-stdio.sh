#!/bin/bash

# Test script for STDIO mode (Claude Code)
echo "Testing OpenRewrite MCP Server in STDIO mode..."
echo "=============================================="

# Create test input
cat > test-input.json << 'EOF'
{"jsonrpc":"2.0","method":"initialize","params":{"protocolVersion":"2024-11-05","clientInfo":{"name":"test-client","version":"1.0.0"},"capabilities":{}},"id":1}
{"jsonrpc":"2.0","method":"tools/list","params":{},"id":2}
{"jsonrpc":"2.0","method":"tools/call","params":{"name":"listRecipes","arguments":{}},"id":3}
EOF

echo "Sending test commands to stdio server..."
echo "Output:"
echo "-------"
cat test-input.json | java -jar build/libs/openrewrite-mcp-server.jar 2>/dev/null | jq '.'

# Cleanup
rm -f test-input.json

echo ""
echo "Test complete!"