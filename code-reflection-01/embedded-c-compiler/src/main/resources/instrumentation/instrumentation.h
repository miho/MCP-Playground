#ifndef EMBEDDED_CC_INSTRUMENTATION_H
#define EMBEDDED_CC_INSTRUMENTATION_H

#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

void instrumentation_trace(char op, void* address, size_t size, int id, int line, const char* expr);

#define TRACE_LOAD(ptr, size, id, line, expr) instrumentation_trace('L', (void*)(ptr), (size), (id), (line), (expr))
#define TRACE_STORE(ptr, size, id, line, expr) instrumentation_trace('S', (void*)(ptr), (size), (id), (line), (expr))

#ifdef __cplusplus
}
#endif

#endif /* EMBEDDED_CC_INSTRUMENTATION_H */

