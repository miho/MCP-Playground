package com.openrewrite.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for TransformationEventBus.
 * Verifies thread-safety, pub-sub functionality, and event delivery.
 */
class TransformationEventBusTest {

    private TransformationEventBus eventBus;

    @BeforeEach
    void setUp() {
        eventBus = TransformationEventBus.getInstance();
        // Clear any existing subscribers from previous tests
        eventBus.clearSubscribers();
    }

    @AfterEach
    void tearDown() {
        eventBus.clearSubscribers();
    }

    @Test
    void testSingletonInstance() {
        TransformationEventBus instance1 = TransformationEventBus.getInstance();
        TransformationEventBus instance2 = TransformationEventBus.getInstance();
        assertSame(instance1, instance2, "Should return the same singleton instance");
    }

    @Test
    @Timeout(5)
    void testSubscribeAndPublish() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        List<TransformationEvent> receivedEvents = new ArrayList<>();

        Consumer<TransformationEvent> listener = event -> {
            receivedEvents.add(event);
            latch.countDown();
        };

        eventBus.subscribe(listener);

        TransformationEvent event = TransformationEvent.started(
                "org.openrewrite.java.format.AutoFormat",
                "public class Test {}",
                "java"
        );

        eventBus.publish(event);

        // Wait for event to be delivered
        assertTrue(latch.await(3, TimeUnit.SECONDS), "Event should be delivered within timeout");
        assertEquals(1, receivedEvents.size(), "Should receive exactly one event");
        assertEquals(event, receivedEvents.get(0), "Should receive the published event");
    }

    @Test
    @Timeout(5)
    void testMultipleSubscribers() throws InterruptedException {
        int subscriberCount = 5;
        CountDownLatch latch = new CountDownLatch(subscriberCount);
        AtomicInteger eventCount = new AtomicInteger(0);

        for (int i = 0; i < subscriberCount; i++) {
            eventBus.subscribe(event -> {
                eventCount.incrementAndGet();
                latch.countDown();
            });
        }

        TransformationEvent event = TransformationEvent.completed(
                "org.openrewrite.java.cleanup.UnnecessaryParentheses",
                "Remove Unnecessary Parentheses",
                "public class Test { int x = (5); }",
                "public class Test { int x = 5; }",
                "java",
                "- int x = (5);\n+ int x = 5;"
        );

        eventBus.publish(event);

        assertTrue(latch.await(3, TimeUnit.SECONDS), "All subscribers should receive the event");
        assertEquals(subscriberCount, eventCount.get(), "All subscribers should be notified");
    }

    @Test
    @Timeout(5)
    void testUnsubscribe() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger callCount = new AtomicInteger(0);

        Consumer<TransformationEvent> listener = event -> {
            callCount.incrementAndGet();
            latch.countDown();
        };

        eventBus.subscribe(listener);

        // Publish first event
        TransformationEvent event1 = TransformationEvent.started("recipe1", "code1", "java");
        eventBus.publish(event1);

        assertTrue(latch.await(3, TimeUnit.SECONDS), "First event should be delivered");
        assertEquals(1, callCount.get(), "Listener should be called once");

        // Unsubscribe
        eventBus.unsubscribe(listener);

        // Publish second event after unsubscribe
        TransformationEvent event2 = TransformationEvent.started("recipe2", "code2", "java");
        eventBus.publish(event2);

        // Wait a bit to ensure no additional calls
        Thread.sleep(500);

        assertEquals(1, callCount.get(), "Listener should not be called after unsubscribe");
    }

    @Test
    @Timeout(5)
    void testPublishConvenienceMethods() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(3);
        List<TransformationEvent> events = new ArrayList<>();

        eventBus.subscribe(event -> {
            events.add(event);
            latch.countDown();
        });

        // Test publishStarted
        eventBus.publishStarted("recipe1", "code1", "java");

        // Test publishCompleted
        eventBus.publishCompleted("recipe2", "Recipe 2", "code2", "code2-transformed", "java", "diff");

        // Test publishFailed
        eventBus.publishFailed("recipe3", "code3", "java", "error message");

        assertTrue(latch.await(3, TimeUnit.SECONDS), "All events should be delivered");
        assertEquals(3, events.size(), "Should receive all three events");

        assertEquals(TransformationEvent.Type.TRANSFORMATION_STARTED, events.get(0).getType());
        assertEquals(TransformationEvent.Type.TRANSFORMATION_COMPLETED, events.get(1).getType());
        assertEquals(TransformationEvent.Type.TRANSFORMATION_FAILED, events.get(2).getType());
    }

    @Test
    @Timeout(5)
    void testThreadSafety() throws InterruptedException {
        int threadCount = 10;
        int eventsPerThread = 100;
        CountDownLatch latch = new CountDownLatch(threadCount * eventsPerThread);
        AtomicInteger eventCount = new AtomicInteger(0);

        eventBus.subscribe(event -> {
            eventCount.incrementAndGet();
            latch.countDown();
        });

        // Create multiple threads publishing events concurrently
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            Thread thread = new Thread(() -> {
                for (int j = 0; j < eventsPerThread; j++) {
                    TransformationEvent event = TransformationEvent.started(
                            "recipe-" + threadId + "-" + j,
                            "code",
                            "java"
                    );
                    eventBus.publish(event);
                }
            });
            threads.add(thread);
            thread.start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // Wait for all events to be delivered
        assertTrue(latch.await(10, TimeUnit.SECONDS), "All events should be delivered");
        assertEquals(threadCount * eventsPerThread, eventCount.get(),
                "Should receive all published events");
    }

    @Test
    void testExceptionInSubscriberDoesNotAffectOthers() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        List<String> successfulSubscribers = new ArrayList<>();

        // Subscriber that throws exception
        eventBus.subscribe(event -> {
            throw new RuntimeException("Test exception");
        });

        // Subscriber that succeeds
        eventBus.subscribe(event -> {
            successfulSubscribers.add("subscriber1");
            latch.countDown();
        });

        // Another subscriber that succeeds
        eventBus.subscribe(event -> {
            successfulSubscribers.add("subscriber2");
            latch.countDown();
        });

        TransformationEvent event = TransformationEvent.started("recipe", "code", "java");
        eventBus.publish(event);

        assertTrue(latch.await(3, TimeUnit.SECONDS), "Successful subscribers should be notified");
        assertEquals(2, successfulSubscribers.size(), "Both successful subscribers should be called");
    }

    @Test
    void testGetSubscriberCount() {
        assertEquals(0, eventBus.getSubscriberCount(), "Should start with 0 subscribers");

        Consumer<TransformationEvent> listener1 = event -> {};
        Consumer<TransformationEvent> listener2 = event -> {};

        eventBus.subscribe(listener1);
        assertEquals(1, eventBus.getSubscriberCount(), "Should have 1 subscriber");

        eventBus.subscribe(listener2);
        assertEquals(2, eventBus.getSubscriberCount(), "Should have 2 subscribers");

        eventBus.unsubscribe(listener1);
        assertEquals(1, eventBus.getSubscriberCount(), "Should have 1 subscriber after unsubscribe");

        eventBus.clearSubscribers();
        assertEquals(0, eventBus.getSubscriberCount(), "Should have 0 subscribers after clear");
    }

    @Test
    void testPublishNullEvent() throws InterruptedException {
        AtomicInteger callCount = new AtomicInteger(0);

        eventBus.subscribe(event -> callCount.incrementAndGet());

        // Publishing null should not crash and should not notify subscribers
        eventBus.publish(null);

        // Wait a bit
        Thread.sleep(200);

        assertEquals(0, callCount.get(), "Subscribers should not be called for null event");
    }

    @Test
    void testEventEquality() {
        TransformationEvent event1 = TransformationEvent.started("recipe", "code", "java");
        TransformationEvent event2 = TransformationEvent.started("recipe", "code", "java");

        // Events should not be equal because they have different request IDs and timestamps
        assertNotEquals(event1, event2, "Events with different request IDs should not be equal");
    }
}
