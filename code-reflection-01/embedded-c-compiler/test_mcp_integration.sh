#!/bin/bash

# Test script to verify MCP integration with UI

echo "Testing MCP Server Integration with UI"
echo "======================================"

# Create results directory
mkdir -p ~/.embedded-c-cache/results

# Create a sample result file for testing
cat > ~/.embedded-c-cache/results/test-result-001.json << 'EOF'
{
  "run_id": "test-result-001",
  "timestamp": "2024-01-01T12:00:00Z",
  "original_code": "#include <stdio.h>\n\nint main() {\n    int array[100];\n    for(int i = 0; i < 100; i++) {\n        array[i] = i * 2;\n    }\n    return 0;\n}",
  "instrumented_code": "#include <stdio.h>\nvoid TRACE(int id, int line, void* addr) { /* tracking */ }\n\nint main() {\n    int array[100];\n    for(int i = 0; i < 100; i++) {\n        TRACE(1, 6, &array[i]);\n        array[i] = i * 2;\n    }\n    return 0;\n}",
  "cache": {
    "set_bits": 5,
    "lines_per_set": 1,
    "block_bits": 5
  },
  "cache_summary": {
    "hits": 75,
    "misses": 25,
    "evictions": 10
  },
  "events": [
    {"type": "HIT", "id": 1, "line": 6, "label": "array[0]"},
    {"type": "MISS", "id": 1, "line": 6, "label": "array[1]"},
    {"type": "HIT", "id": 1, "line": 6, "label": "array[2]"},
    {"type": "EVICTION", "id": 1, "line": 6, "label": "array[32]"}
  ],
  "hotspots": [
    {
      "id": 1,
      "line": 6,
      "expression": "array[i]",
      "misses": 25,
      "evictions": 10,
      "score": 35
    }
  ],
  "instrumented_points": [
    {
      "id": 1,
      "line": 6,
      "expression": "array[i]",
      "type": "WRITE"
    }
  ],
  "compile": {
    "exit_code": 0,
    "stdout": "Compilation successful",
    "stderr": ""
  },
  "execution": {
    "exit_code": 0,
    "stdout": "Program executed successfully",
    "stderr": ""
  },
  "metadata": {
    "tool": "test_generator",
    "version": "1.0"
  },
  "defines": []
}
EOF

echo "✓ Created test result file at: ~/.embedded-c-cache/results/test-result-001.json"
echo ""
echo "Instructions to test:"
echo "1. Run the application: ./gradlew run"
echo "2. Click 'Load Results' button or use File > Load Result..."
echo "3. Navigate to ~/.embedded-c-cache/results/ and select test-result-001.json"
echo "4. You should see:"
echo "   - Original code in the main editor"
echo "   - Instrumented code in the instrumented tab"
echo "   - Cache events showing hits/misses/evictions"
echo "   - Hotspot at line 6 with score 35"
echo ""
echo "Alternative: Use File > Load Recent Results to see this test result"