# External MCP Synchronization Guide

## Overview

The OpenRewrite JavaFX application now supports **automatic UI synchronization** when transformations are triggered by external MCP clients (like Claude Code, LM Studio, or other AI assistants). When an external client calls the `apply_recipe` tool, the UI automatically updates to show:

- The source code being transformed
- The transformation result
- The diff view
- Real-time status updates

## Architecture

### Components

1. **TransformationEvent** (`com.openrewrite.server.TransformationEvent`)
   - Immutable event object containing transformation data
   - Three event types: `TRANSFORMATION_STARTED`, `TRANSFORMATION_COMPLETED`, `TRANSFORMATION_FAILED`
   - Includes source code, transformed code, recipe name, diff, and metadata

2. **TransformationEventBus** (`com.openrewrite.server.TransformationEventBus`)
   - Thread-safe singleton event bus for pub-sub messaging
   - Asynchronous event delivery using dedicated executor thread
   - Supports multiple concurrent subscribers
   - Robust error handling (one subscriber's exception doesn't affect others)

3. **ToolFactory** (Modified)
   - Publishes events when `apply_recipe` is called
   - Emits events before, during, and after transformation
   - Works with both stdio and HTTP MCP transports

4. **MainController** (Modified)
   - Subscribes to transformation events on initialization
   - Handles events and marshals UI updates to JavaFX Application Thread
   - Updates source editor, transformed editor, diff viewer, and status bar

### Event Flow

```
External MCP Client (Claude, LM Studio, etc.)
    ↓
    ↓ HTTP/stdio MCP call: apply_recipe
    ↓
ToolFactory.createApplyRecipeTool()
    ↓
    ↓ Publishes: TRANSFORMATION_STARTED
    ↓
RewriteEngine.applyRecipe()
    ↓
    ↓ Publishes: TRANSFORMATION_COMPLETED or TRANSFORMATION_FAILED
    ↓
TransformationEventBus (async executor thread)
    ↓
MainController.handleTransformationEvent()
    ↓
    ↓ Platform.runLater() - marshals to JavaFX thread
    ↓
UI Updates:
    - Source code editor
    - Transformed code editor
    - Diff viewer
    - Status bar
```

## Thread Safety

The implementation is **fully thread-safe**:

1. **Event Bus**: Uses `CopyOnWriteArrayList` for thread-safe subscriber management
2. **Async Delivery**: Events are delivered on a dedicated single-thread executor
3. **UI Marshalling**: All UI updates use `Platform.runLater()` to execute on JavaFX Application Thread
4. **No Blocking**: Event publishing is non-blocking and returns immediately

## Usage Examples

### Example 1: Using Claude Code

1. Start the OpenRewrite JavaFX application:
```bash
./gradlew run
```

2. Configure Claude Code to connect to the MCP server (default: `http://localhost:3001`)

3. In Claude Code, use the MCP tools:
```
Please apply the recipe "org.openrewrite.java.cleanup.UnnecessaryParentheses"
to this Java code:

public class Example {
    int x = (5);
    String text = ("hello");
}
```

4. The JavaFX UI will automatically update to show:
   - The original code in the source editor
   - The transformed code in the transformed editor
   - The diff highlighting the changes
   - Status: "External transformation completed: Remove Unnecessary Parentheses - changes detected"

### Example 2: Using LM Studio

1. Configure LM Studio with the MCP server configuration from `lm-studio-config.json`

2. In LM Studio, ask the AI to transform code:
```
Use OpenRewrite to upgrade this code to Java 17:

public class Example {
    public void process() {
        List list = new ArrayList(); // Raw type
    }
}
```

3. The UI automatically updates as the transformation is applied

### Example 3: Direct HTTP API Call

You can also test with curl:

```bash
curl -X POST http://localhost:3001/mcp/call \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": {
      "name": "apply_recipe",
      "arguments": {
        "sourceCode": "public class Test { int x = (5); }",
        "recipeName": "org.openrewrite.java.cleanup.UnnecessaryParentheses",
        "language": "java"
      }
    }
  }'
```

Watch the JavaFX UI update in real-time!

## API Reference

### TransformationEvent

```java
// Factory methods
TransformationEvent.started(String recipeName, String sourceCode, String language)
TransformationEvent.completed(String recipeName, String displayName,
                              String sourceCode, String transformedCode,
                              String language, String diff)
TransformationEvent.failed(String recipeName, String sourceCode,
                          String language, String errorMessage)

// Builder pattern
new TransformationEvent.Builder()
    .type(Type.TRANSFORMATION_COMPLETED)
    .recipeName("recipe.name")
    .sourceCode("...")
    .transformedCode("...")
    .hasChanges(true)
    .build()

// Properties
event.getType()                 // TRANSFORMATION_STARTED, COMPLETED, or FAILED
event.getSourceCode()           // Original source code
event.getTransformedCode()      // Transformed code (if completed)
event.getRecipeName()           // Full recipe name
event.getRecipeDisplayName()    // Human-readable display name
event.getLanguage()             // Programming language
event.hasChanges()              // Boolean: did transformation make changes?
event.getDiff()                 // Unified diff format
event.getErrorMessage()         // Error message (if failed)
event.getTimestamp()            // When event was created
event.getRequestId()            // Unique request identifier
event.isSuccess()               // Boolean: was transformation successful?
```

### TransformationEventBus

```java
// Get singleton instance
TransformationEventBus eventBus = TransformationEventBus.getInstance();

// Subscribe to events
eventBus.subscribe(event -> {
    System.out.println("Received: " + event);
});

// Unsubscribe
eventBus.unsubscribe(listener);

// Publish events
eventBus.publish(event);

// Convenience methods
eventBus.publishStarted("recipe", "code", "java");
eventBus.publishCompleted("recipe", "Recipe Display", "code", "transformed", "java", "diff");
eventBus.publishFailed("recipe", "code", "java", "error");

// Utility methods
int count = eventBus.getSubscriberCount();
eventBus.clearSubscribers();
eventBus.shutdown();
```

## Extending the System

### Adding Custom Event Listeners

You can add your own event listeners to react to transformations:

```java
import com.openrewrite.server.TransformationEventBus;
import com.openrewrite.server.TransformationEvent;

public class TransformationLogger {
    public void initialize() {
        TransformationEventBus.getInstance().subscribe(this::logEvent);
    }

    private void logEvent(TransformationEvent event) {
        switch (event.getType()) {
            case TRANSFORMATION_STARTED:
                System.out.println("Starting: " + event.getRecipeName());
                break;
            case TRANSFORMATION_COMPLETED:
                System.out.println("Completed: " + event.getRecipeName() +
                                 " (changes: " + event.hasChanges() + ")");
                break;
            case TRANSFORMATION_FAILED:
                System.err.println("Failed: " + event.getRecipeName() +
                                 " - " + event.getErrorMessage());
                break;
        }
    }
}
```

### Adding More Event Types

To add custom event types for analyze_code or other tools:

1. Add new event type to `TransformationEvent.Type` enum
2. Create factory method in `TransformationEvent` (e.g., `analysisCompleted()`)
3. Publish events from the appropriate tool in `ToolFactory`
4. Handle the new event type in `MainController.handleTransformationEvent()`

Example:

```java
// In TransformationEvent.Type enum
ANALYSIS_COMPLETED

// Factory method
public static TransformationEvent analysisCompleted(
    String sourceCode, List<String> suggestions) {
    return new Builder()
        .type(Type.ANALYSIS_COMPLETED)
        .sourceCode(sourceCode)
        .recipeName("code_analysis")
        // ... other properties
        .build();
}

// In ToolFactory.createAnalyzeCodeTool()
eventBus.publish(TransformationEvent.analysisCompleted(sourceCode, suggestions));

// In MainController.handleTransformationEvent()
case ANALYSIS_COMPLETED:
    handleAnalysisCompleted(event);
    break;
```

## Performance Considerations

1. **Event Delivery**: Events are delivered asynchronously on a dedicated thread, so publishing is non-blocking
2. **UI Updates**: UI updates are batched using `Platform.runLater()`, which is efficient
3. **Memory**: Events contain full source code, so avoid keeping references to old events
4. **Concurrency**: The single-threaded executor ensures events are processed in order

## Troubleshooting

### Events Not Received

1. Check that MCP server is running: Look for "MCP Server running" in status bar
2. Verify event bus subscription: Check logs for "Subscribed to TransformationEventBus"
3. Check for exceptions: Look for errors in the logs

### UI Not Updating

1. Verify `Platform.runLater()` is being used for all UI updates
2. Check JavaFX Application Thread: UI updates must be on this thread
3. Look for exceptions in `handleTransformationEvent()` method

### Multiple Updates

1. Events are delivered to all subscribers
2. Check subscriber count: `eventBus.getSubscriberCount()`
3. Ensure proper unsubscribe in `shutdown()`

## Testing

Run the comprehensive test suite:

```bash
./gradlew test --tests TransformationEventBusTest
```

Tests cover:
- Singleton pattern
- Subscribe/unsubscribe
- Multiple subscribers
- Thread safety
- Exception handling
- Event delivery
- Convenience methods

## Best Practices

1. **Always use Platform.runLater()** for UI updates when handling events
2. **Unsubscribe in shutdown()** to prevent memory leaks
3. **Handle exceptions** in event listeners to prevent affecting other subscribers
4. **Keep event handlers fast** - offload heavy work to background threads
5. **Don't block** in event handlers - events are delivered sequentially
6. **Log events** for debugging and monitoring

## Future Enhancements

Potential improvements for future versions:

1. **Event filtering**: Allow subscribers to filter events by recipe name or type
2. **Event replay**: Store recent events for new subscribers
3. **Metrics**: Add metrics for event throughput, latency, subscriber count
4. **Persistence**: Optionally persist events for audit trail
5. **Batching**: Batch multiple rapid transformations to reduce UI churn
6. **Priority**: Support prioritized event delivery
7. **Acknowledgments**: Add ack/nack mechanism for reliable delivery

## License

Same as the main OpenRewrite JavaFX application.

## Support

For issues or questions, please check:
- Main README.md for general application information
- README-MCP.md for MCP server configuration
- Application logs in the logs/ directory (if enabled)
