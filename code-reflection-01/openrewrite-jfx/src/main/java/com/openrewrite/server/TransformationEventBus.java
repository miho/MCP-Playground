package com.openrewrite.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Event bus for publishing and subscribing to transformation events.
 * This enables decoupled communication between the MCP server and the JavaFX UI.
 *
 * Thread-Safety: This class is thread-safe and can be used from multiple threads.
 * Event listeners are notified asynchronously on a dedicated executor thread.
 */
public class TransformationEventBus {

    private static final Logger logger = LoggerFactory.getLogger(TransformationEventBus.class);

    // Singleton instance
    private static final TransformationEventBus INSTANCE = new TransformationEventBus();

    // Thread-safe list of subscribers
    private final List<Consumer<TransformationEvent>> subscribers;

    // Executor for async event delivery
    private final ExecutorService eventExecutor;

    /**
     * Private constructor for singleton pattern.
     */
    private TransformationEventBus() {
        this.subscribers = new CopyOnWriteArrayList<>();
        this.eventExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "TransformationEventBus-Thread");
            thread.setDaemon(true);
            return thread;
        });
        logger.info("TransformationEventBus initialized");
    }

    /**
     * Get the singleton instance of the event bus.
     */
    public static TransformationEventBus getInstance() {
        return INSTANCE;
    }

    /**
     * Subscribe to transformation events.
     * The listener will be called asynchronously when events are published.
     *
     * @param listener the event listener
     * @return this event bus for chaining
     */
    public TransformationEventBus subscribe(Consumer<TransformationEvent> listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Listener must not be null");
        }
        subscribers.add(listener);
        logger.debug("Added subscriber, total subscribers: {}", subscribers.size());
        return this;
    }

    /**
     * Unsubscribe from transformation events.
     *
     * @param listener the event listener to remove
     * @return this event bus for chaining
     */
    public TransformationEventBus unsubscribe(Consumer<TransformationEvent> listener) {
        if (listener == null) {
            return this;
        }
        subscribers.remove(listener);
        logger.debug("Removed subscriber, total subscribers: {}", subscribers.size());
        return this;
    }

    /**
     * Publish a transformation event to all subscribers.
     * Events are delivered asynchronously on a dedicated thread.
     *
     * @param event the event to publish
     */
    public void publish(TransformationEvent event) {
        if (event == null) {
            logger.warn("Attempted to publish null event, ignoring");
            return;
        }

        logger.info("Publishing event: {}", event);

        // Submit event delivery to executor for async processing
        eventExecutor.submit(() -> {
            for (Consumer<TransformationEvent> subscriber : subscribers) {
                try {
                    subscriber.accept(event);
                } catch (Exception e) {
                    logger.error("Error delivering event to subscriber", e);
                    // Continue delivering to other subscribers even if one fails
                }
            }
        });
    }

    /**
     * Publish a transformation started event.
     * Convenience method to avoid creating events manually.
     *
     * @param recipeName the name of the recipe being applied
     * @param sourceCode the source code being transformed
     * @param language the programming language
     */
    public void publishStarted(String recipeName, String sourceCode, String language) {
        publish(TransformationEvent.started(recipeName, sourceCode, language));
    }

    /**
     * Publish a transformation completed event.
     * Convenience method to avoid creating events manually.
     *
     * @param recipeName the name of the recipe that was applied
     * @param recipeDisplayName the display name of the recipe
     * @param sourceCode the original source code
     * @param transformedCode the transformed source code
     * @param language the programming language
     * @param diff the diff between original and transformed code
     */
    public void publishCompleted(String recipeName, String recipeDisplayName,
                                  String sourceCode, String transformedCode,
                                  String language, String diff) {
        publish(TransformationEvent.completed(recipeName, recipeDisplayName,
                sourceCode, transformedCode, language, diff));
    }

    /**
     * Publish a transformation failed event.
     * Convenience method to avoid creating events manually.
     *
     * @param recipeName the name of the recipe that failed
     * @param sourceCode the source code that was being transformed
     * @param language the programming language
     * @param errorMessage the error message
     */
    public void publishFailed(String recipeName, String sourceCode, String language, String errorMessage) {
        publish(TransformationEvent.failed(recipeName, sourceCode, language, errorMessage));
    }

    /**
     * Get the number of active subscribers.
     */
    public int getSubscriberCount() {
        return subscribers.size();
    }

    /**
     * Clear all subscribers.
     * This is useful for testing or cleanup.
     */
    public void clearSubscribers() {
        subscribers.clear();
        logger.info("Cleared all subscribers");
    }

    /**
     * Shutdown the event bus and its executor.
     * After shutdown, no more events can be published.
     */
    public void shutdown() {
        logger.info("Shutting down TransformationEventBus");
        eventExecutor.shutdown();
        subscribers.clear();
    }
}
