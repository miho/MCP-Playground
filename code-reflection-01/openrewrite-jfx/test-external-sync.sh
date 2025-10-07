#!/bin/bash
#
# Test script for external MCP synchronization
# This script sends transformation requests to the MCP server
# and demonstrates the automatic UI synchronization feature
#

set -e

# Configuration
MCP_URL="http://localhost:3001/mcp/call"
CONTENT_TYPE="Content-Type: application/json"

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}OpenRewrite MCP External Sync Test${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Check if server is running
echo -e "${YELLOW}Checking if MCP server is running...${NC}"
if ! curl -s -f "$MCP_URL" > /dev/null 2>&1; then
    echo -e "${RED}Error: MCP server is not running at $MCP_URL${NC}"
    echo -e "${YELLOW}Please start the JavaFX application first:${NC}"
    echo -e "  ./gradlew run"
    exit 1
fi
echo -e "${GREEN}✓ MCP server is running${NC}"
echo ""

# Test 1: Remove Unnecessary Parentheses
echo -e "${BLUE}Test 1: Remove Unnecessary Parentheses${NC}"
echo -e "${YELLOW}Watch the JavaFX UI update automatically!${NC}"
echo ""

SOURCE_CODE_1='public class Example {
    int x = (5);
    int y = (10 + 20);
    String text = ("hello");
    boolean flag = (true);
}'

echo "Sending transformation request..."
RESPONSE_1=$(curl -s -X POST "$MCP_URL" \
  -H "$CONTENT_TYPE" \
  -d "{
    \"jsonrpc\": \"2.0\",
    \"id\": 1,
    \"method\": \"tools/call\",
    \"params\": {
      \"name\": \"apply_recipe\",
      \"arguments\": {
        \"sourceCode\": $(echo "$SOURCE_CODE_1" | jq -Rs .),
        \"recipeName\": \"org.openrewrite.java.cleanup.UnnecessaryParentheses\",
        \"language\": \"java\"
      }
    }
  }")

if echo "$RESPONSE_1" | jq -e '.result.content[0].text | fromjson | .hasChanges' > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Transformation completed${NC}"
    HAS_CHANGES=$(echo "$RESPONSE_1" | jq -r '.result.content[0].text | fromjson | .hasChanges')
    if [ "$HAS_CHANGES" = "true" ]; then
        echo -e "${GREEN}  Changes were made to the code${NC}"
    else
        echo -e "${YELLOW}  No changes were needed${NC}"
    fi
else
    echo -e "${RED}✗ Transformation failed${NC}"
    echo "$RESPONSE_1" | jq '.'
fi
echo ""
sleep 2

# Test 2: Simplify Boolean Expression
echo -e "${BLUE}Test 2: Simplify Boolean Expression${NC}"
echo -e "${YELLOW}Check the UI - it should update with new code!${NC}"
echo ""

SOURCE_CODE_2='public class BooleanTest {
    public boolean check(int value) {
        if (value == 5 == true) {
            return true;
        }
        return false;
    }
}'

echo "Sending transformation request..."
RESPONSE_2=$(curl -s -X POST "$MCP_URL" \
  -H "$CONTENT_TYPE" \
  -d "{
    \"jsonrpc\": \"2.0\",
    \"id\": 2,
    \"method\": \"tools/call\",
    \"params\": {
      \"name\": \"apply_recipe\",
      \"arguments\": {
        \"sourceCode\": $(echo "$SOURCE_CODE_2" | jq -Rs .),
        \"recipeName\": \"org.openrewrite.java.cleanup.SimplifyBooleanExpression\",
        \"language\": \"java\"
      }
    }
  }")

if echo "$RESPONSE_2" | jq -e '.result.content[0].text | fromjson | .hasChanges' > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Transformation completed${NC}"
    HAS_CHANGES=$(echo "$RESPONSE_2" | jq -r '.result.content[0].text | fromjson | .hasChanges')
    if [ "$HAS_CHANGES" = "true" ]; then
        echo -e "${GREEN}  Changes were made to the code${NC}"
    else
        echo -e "${YELLOW}  No changes were needed${NC}"
    fi
else
    echo -e "${RED}✗ Transformation failed${NC}"
    echo "$RESPONSE_2" | jq '.'
fi
echo ""
sleep 2

# Test 3: Remove Redundant Modifiers
echo -e "${BLUE}Test 3: Remove Redundant Modifiers${NC}"
echo -e "${YELLOW}UI should show the diff view with changes!${NC}"
echo ""

SOURCE_CODE_3='public interface Constants {
    public static final int MAX_VALUE = 100;
    public abstract void doSomething();
}'

echo "Sending transformation request..."
RESPONSE_3=$(curl -s -X POST "$MCP_URL" \
  -H "$CONTENT_TYPE" \
  -d "{
    \"jsonrpc\": \"2.0\",
    \"id\": 3,
    \"method\": \"tools/call\",
    \"params\": {
      \"name\": \"apply_recipe\",
      \"arguments\": {
        \"sourceCode\": $(echo "$SOURCE_CODE_3" | jq -Rs .),
        \"recipeName\": \"org.openrewrite.staticanalysis.RedundantModifier\",
        \"language\": \"java\"
      }
    }
  }")

if echo "$RESPONSE_3" | jq -e '.result.content[0].text | fromjson | .hasChanges' > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Transformation completed${NC}"
    HAS_CHANGES=$(echo "$RESPONSE_3" | jq -r '.result.content[0].text | fromjson | .hasChanges')
    if [ "$HAS_CHANGES" = "true" ]; then
        echo -e "${GREEN}  Changes were made to the code${NC}"
    else
        echo -e "${YELLOW}  No changes were needed${NC}"
    fi
else
    echo -e "${RED}✗ Transformation failed${NC}"
    echo "$RESPONSE_3" | jq '.'
fi
echo ""

# Test 4: Test error handling with invalid recipe
echo -e "${BLUE}Test 4: Error Handling (Invalid Recipe)${NC}"
echo -e "${YELLOW}UI should show an error dialog!${NC}"
echo ""

echo "Sending request with invalid recipe..."
RESPONSE_4=$(curl -s -X POST "$MCP_URL" \
  -H "$CONTENT_TYPE" \
  -d '{
    "jsonrpc": "2.0",
    "id": 4,
    "method": "tools/call",
    "params": {
      "name": "apply_recipe",
      "arguments": {
        "sourceCode": "public class Test {}",
        "recipeName": "org.openrewrite.InvalidRecipeName",
        "language": "java"
      }
    }
  }')

if echo "$RESPONSE_4" | jq -e '.result.content[0].text | fromjson | .error' > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Error handled correctly${NC}"
    ERROR_MSG=$(echo "$RESPONSE_4" | jq -r '.result.content[0].text | fromjson | .error')
    echo -e "${YELLOW}  Error: $ERROR_MSG${NC}"
else
    echo -e "${RED}✗ Unexpected response${NC}"
    echo "$RESPONSE_4" | jq '.'
fi
echo ""

# Summary
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Test Summary${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo -e "${GREEN}All tests completed!${NC}"
echo ""
echo -e "${YELLOW}What you should have seen in the UI:${NC}"
echo "1. Source code editor updated with each test's code"
echo "2. Transformed code editor showing the results"
echo "3. Diff view highlighting the changes"
echo "4. Status bar showing transformation progress and results"
echo "5. Error dialog for the invalid recipe test"
echo ""
echo -e "${BLUE}Try running this script again while watching the UI!${NC}"
echo ""
