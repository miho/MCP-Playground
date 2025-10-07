#!/bin/bash

# Create multiple test result files with complete data for testing

echo "Creating test result files for UI testing"
echo "========================================="

# Create results directory (matching RunResultPersister's path)
mkdir -p ~/.embeddedcc/runs

# Test Result 1: Matrix Multiplication
cat > ~/.embeddedcc/runs/test-matrix-001.json << 'EOF'
{
  "run_id": "test-matrix-001",
  "timestamp": "2024-01-15T10:30:00Z",
  "original_code": "#include <stdio.h>\n#define N 100\n\nint main() {\n    int matrix[N][N];\n    int result[N][N];\n    \n    // Initialize matrices\n    for(int i = 0; i < N; i++) {\n        for(int j = 0; j < N; j++) {\n            matrix[i][j] = i + j;\n            result[i][j] = 0;\n        }\n    }\n    \n    // Matrix multiplication (simplified)\n    for(int i = 0; i < N; i++) {\n        for(int j = 0; j < N; j++) {\n            for(int k = 0; k < N; k++) {\n                result[i][j] += matrix[i][k] * matrix[k][j];\n            }\n        }\n    }\n    \n    printf(\"Result[0][0] = %d\\n\", result[0][0]);\n    return 0;\n}",
  "instrumented_code": "#include <stdio.h>\n#define N 100\nvoid TRACE(int id, int line, void* addr) { /* cache tracking */ }\n\nint main() {\n    int matrix[N][N];\n    int result[N][N];\n    \n    // Initialize matrices\n    for(int i = 0; i < N; i++) {\n        for(int j = 0; j < N; j++) {\n            TRACE(1, 11, &matrix[i][j]);\n            matrix[i][j] = i + j;\n            TRACE(2, 12, &result[i][j]);\n            result[i][j] = 0;\n        }\n    }\n    \n    // Matrix multiplication (simplified)\n    for(int i = 0; i < N; i++) {\n        for(int j = 0; j < N; j++) {\n            for(int k = 0; k < N; k++) {\n                TRACE(3, 20, &result[i][j]);\n                TRACE(4, 20, &matrix[i][k]);\n                TRACE(5, 20, &matrix[k][j]);\n                result[i][j] += matrix[i][k] * matrix[k][j];\n            }\n        }\n    }\n    \n    printf(\"Result[0][0] = %d\\n\", result[0][0]);\n    return 0;\n}",
  "cache": {
    "set_bits": 5,
    "lines_per_set": 1,
    "block_bits": 5
  },
  "cache_summary": {
    "hits": 850000,
    "misses": 150000,
    "evictions": 75000
  },
  "events": [
    {"type": "MISS", "id": 1, "line": 11, "label": "matrix[0][0]"},
    {"type": "HIT", "id": 1, "line": 11, "label": "matrix[0][1]"},
    {"type": "HIT", "id": 1, "line": 11, "label": "matrix[0][2]"},
    {"type": "MISS", "id": 2, "line": 12, "label": "result[0][0]"},
    {"type": "EVICTION", "id": 3, "line": 20, "label": "result[50][50]"},
    {"type": "MISS", "id": 4, "line": 20, "label": "matrix[50][0]"},
    {"type": "HIT", "id": 5, "line": 20, "label": "matrix[0][50]"}
  ],
  "hotspots": [
    {
      "id": 3,
      "line": 20,
      "expression": "result[i][j]",
      "misses": 50000,
      "evictions": 25000,
      "score": 75000
    },
    {
      "id": 4,
      "line": 20,
      "expression": "matrix[i][k]",
      "misses": 40000,
      "evictions": 20000,
      "score": 60000
    },
    {
      "id": 5,
      "line": 20,
      "expression": "matrix[k][j]",
      "misses": 40000,
      "evictions": 20000,
      "score": 60000
    },
    {
      "id": 1,
      "line": 11,
      "expression": "matrix[i][j]",
      "misses": 10000,
      "evictions": 5000,
      "score": 15000
    },
    {
      "id": 2,
      "line": 12,
      "expression": "result[i][j]",
      "misses": 10000,
      "evictions": 5000,
      "score": 15000
    }
  ],
  "instrumented_points": [
    {"id": 1, "line": 11, "expression": "matrix[i][j]", "type": "WRITE"},
    {"id": 2, "line": 12, "expression": "result[i][j]", "type": "WRITE"},
    {"id": 3, "line": 20, "expression": "result[i][j]", "type": "READ_WRITE"},
    {"id": 4, "line": 20, "expression": "matrix[i][k]", "type": "READ"},
    {"id": 5, "line": 20, "expression": "matrix[k][j]", "type": "READ"}
  ],
  "compile": {
    "exit_code": 0,
    "stdout": "Compilation successful",
    "stderr": ""
  },
  "execution": {
    "exit_code": 0,
    "stdout": "Result[0][0] = 328350\nProgram executed successfully",
    "stderr": ""
  },
  "metadata": {
    "tool": "test_generator",
    "version": "1.0",
    "analysis": "matrix_multiplication"
  },
  "defines": []
}
EOF

# Test Result 2: Array Transpose with Blocking
cat > ~/.embeddedcc/runs/test-transpose-002.json << 'EOF'
{
  "run_id": "test-transpose-002",
  "timestamp": "2024-01-15T11:00:00Z",
  "original_code": "#include <stdio.h>\n#define N 512\n#define BLOCK_SIZE 16\n\nint main() {\n    int src[N][N];\n    int dst[N][N];\n    \n    // Initialize source matrix\n    for(int i = 0; i < N; i++) {\n        for(int j = 0; j < N; j++) {\n            src[i][j] = i * N + j;\n        }\n    }\n    \n    // Blocked transpose\n    for(int i = 0; i < N; i += BLOCK_SIZE) {\n        for(int j = 0; j < N; j += BLOCK_SIZE) {\n            for(int ii = i; ii < i + BLOCK_SIZE && ii < N; ii++) {\n                for(int jj = j; jj < j + BLOCK_SIZE && jj < N; jj++) {\n                    dst[jj][ii] = src[ii][jj];\n                }\n            }\n        }\n    }\n    \n    printf(\"Transpose complete\\n\");\n    return 0;\n}",
  "instrumented_code": "#include <stdio.h>\n#define N 512\n#define BLOCK_SIZE 16\nvoid TRACE(int id, int line, void* addr) { /* tracking */ }\n\nint main() {\n    int src[N][N];\n    int dst[N][N];\n    \n    // Initialize source matrix\n    for(int i = 0; i < N; i++) {\n        for(int j = 0; j < N; j++) {\n            TRACE(1, 12, &src[i][j]);\n            src[i][j] = i * N + j;\n        }\n    }\n    \n    // Blocked transpose\n    for(int i = 0; i < N; i += BLOCK_SIZE) {\n        for(int j = 0; j < N; j += BLOCK_SIZE) {\n            for(int ii = i; ii < i + BLOCK_SIZE && ii < N; ii++) {\n                for(int jj = j; jj < j + BLOCK_SIZE && jj < N; jj++) {\n                    TRACE(2, 21, &src[ii][jj]);\n                    TRACE(3, 21, &dst[jj][ii]);\n                    dst[jj][ii] = src[ii][jj];\n                }\n            }\n        }\n    }\n    \n    printf(\"Transpose complete\\n\");\n    return 0;\n}",
  "cache": {
    "set_bits": 6,
    "lines_per_set": 2,
    "block_bits": 6
  },
  "cache_summary": {
    "hits": 200000,
    "misses": 62144,
    "evictions": 31072
  },
  "events": [
    {"type": "MISS", "id": 1, "line": 12, "label": "src[0][0]"},
    {"type": "HIT", "id": 1, "line": 12, "label": "src[0][1]"},
    {"type": "MISS", "id": 2, "line": 21, "label": "src[0][0]"},
    {"type": "MISS", "id": 3, "line": 21, "label": "dst[0][0]"},
    {"type": "EVICTION", "id": 3, "line": 21, "label": "dst[256][256]"}
  ],
  "hotspots": [
    {
      "id": 3,
      "line": 21,
      "expression": "dst[jj][ii]",
      "misses": 32768,
      "evictions": 16384,
      "score": 49152
    },
    {
      "id": 2,
      "line": 21,
      "expression": "src[ii][jj]",
      "misses": 16384,
      "evictions": 8192,
      "score": 24576
    },
    {
      "id": 1,
      "line": 12,
      "expression": "src[i][j]",
      "misses": 12992,
      "evictions": 6496,
      "score": 19488
    }
  ],
  "instrumented_points": [
    {"id": 1, "line": 12, "expression": "src[i][j]", "type": "WRITE"},
    {"id": 2, "line": 21, "expression": "src[ii][jj]", "type": "READ"},
    {"id": 3, "line": 21, "expression": "dst[jj][ii]", "type": "WRITE"}
  ],
  "compile": {
    "exit_code": 0,
    "stdout": "Compilation successful with -DBLOCK_SIZE=16",
    "stderr": ""
  },
  "execution": {
    "exit_code": 0,
    "stdout": "Transpose complete",
    "stderr": ""
  },
  "metadata": {
    "tool": "test_generator",
    "version": "1.0",
    "analysis": "blocked_transpose"
  },
  "defines": ["BLOCK_SIZE=16"]
}
EOF

echo "✓ Created test result files:"
echo "  - ~/.embeddedcc/runs/test-matrix-001.json (Matrix multiplication)"
echo "  - ~/.embeddedcc/runs/test-transpose-002.json (Blocked transpose)"
echo ""
echo "Testing Instructions:"
echo "===================="
echo "1. Run the application:"
echo "   ./gradlew run"
echo ""
echo "2. Test manual load:"
echo "   - Click 'Load Results' button"
echo "   - Navigate to ~/.embeddedcc/runs/"
echo "   - Select either test file"
echo ""
echo "3. Test recent results:"
echo "   - Use File > Load Recent Results"
echo "   - Both test files should appear in the list"
echo ""
echo "4. Verify the following loads correctly:"
echo "   ✓ Original C code appears in main editor"
echo "   ✓ Instrumented code appears in 'Instrumented Code' tab"
echo "   ✓ Cache events show in the events list"
echo "   ✓ Hotspots table shows problem areas with scores"
echo "   ✓ Cache summary shows hit/miss/eviction counts"
echo "   ✓ Status bar confirms successful load"
echo ""
echo "5. After loading, you can:"
echo "   - Click on hotspots to jump to code lines"
echo "   - Re-analyze the code with 'Analyze' button"
echo "   - Run new instrumentation with different selections"