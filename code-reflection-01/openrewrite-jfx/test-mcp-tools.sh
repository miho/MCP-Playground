#!/bin/bash
# Test script to verify MCP tools are available

echo "Testing MCP server tools..."

# Function to send MCP request
send_mcp_request() {
    local request=$1
    curl -s -X POST http://localhost:3001/mcp \
        -H "Content-Type: application/json" \
        -H "Accept: application/json, text/event-stream" \
        -d "$request"
}

# Test initialize
echo "1. Testing initialize..."
INIT_REQUEST='{
    "jsonrpc": "2.0",
    "id": "init-1",
    "method": "initialize",
    "params": {
        "protocolVersion": "0.1.0",
        "capabilities": {},
        "clientInfo": {
            "name": "test-client",
            "version": "1.0.0"
        }
    }
}'

RESPONSE=$(send_mcp_request "$INIT_REQUEST")
echo "Initialize response: $RESPONSE"
echo

# Test tools/list
echo "2. Testing tools/list..."
TOOLS_REQUEST='{
    "jsonrpc": "2.0",
    "id": "tools-1",
    "method": "tools/list",
    "params": {}
}'

RESPONSE=$(send_mcp_request "$TOOLS_REQUEST")
echo "Tools list response:"
echo "$RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$RESPONSE"
echo

# Extract and display tool names
echo "3. Available tools:"
echo "$RESPONSE" | python3 -c "
import json
import sys
try:
    data = json.load(sys.stdin)
    if 'result' in data and 'tools' in data['result']:
        for tool in data['result']['tools']:
            print(f\"  - {tool.get('name', 'unknown')}: {tool.get('description', '')[:60]}...\")
    else:
        print('  Could not parse tools from response')
except:
    print('  Error parsing JSON response')
" 2>/dev/null

echo
echo "4. Checking for new tools:"
for tool_name in "apply_recipe_to_file" "analyze_file_structure" "list_instrumentation_recipes"; do
    if echo "$RESPONSE" | grep -q "\"$tool_name\""; then
        echo "  ✓ $tool_name found"
    else
        echo "  ✗ $tool_name NOT FOUND"
    fi
done

echo
echo "Test complete."