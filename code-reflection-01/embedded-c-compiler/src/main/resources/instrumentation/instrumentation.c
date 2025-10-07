#include "instrumentation.h"

#include <errno.h>
#include <inttypes.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static FILE* trace_file = NULL;

static void instrumentation_close(void);

static void ensure_trace_file(void) {
    if (trace_file) {
        return;
    }

    const char* path = getenv("TRACE_OUTPUT_PATH");
    if (path == NULL || path[0] == '\0') {
        path = "trace.log";
    }

    trace_file = fopen(path, "w");
    if (!trace_file) {
        fprintf(stderr, "instrumentation: failed to open trace file '%s': %s\n",
                path, strerror(errno));
        trace_file = stderr;
    } else {
        setvbuf(trace_file, NULL, _IONBF, 0);
        if (atexit(instrumentation_close) != 0) {
            fprintf(stderr, "instrumentation: failed to register atexit handler\n");
        }
    }
}

static void instrumentation_close(void) {
    if (trace_file && trace_file != stderr) {
        fclose(trace_file);
    }
    trace_file = NULL;
}

void instrumentation_trace(char op, void* address, size_t size, int id, int line, const char* expr) {
    ensure_trace_file();
    if (!trace_file) {
        return;
    }

    uintptr_t addr_value = (uintptr_t)address;
    unsigned long long addr = (unsigned long long)addr_value;
    const char* expr_label = expr ? expr : "unknown";

    fprintf(trace_file,
            "%c %016llx,%zu,\"!id=(%d) location=(%d,%d) expr=%s!\"\n",
            op,
            addr,
            size,
            id,
            line,
            line,
            expr_label);

    fflush(trace_file);
}

