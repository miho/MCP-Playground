package com.imageprocessing.server;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe cache for storing text/CSV results.
 * Similar to IntermediateResultCache but for text data.
 */
public class TextResultCache {

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    /**
     * Store text result with a key.
     */
    public void put(String key, String text) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Key cannot be null or blank");
        }
        cache.put(key, text);
    }

    /**
     * Retrieve text result by key.
     */
    public String get(String key) {
        return cache.get(key);
    }

    /**
     * Check if key exists in cache.
     */
    public boolean containsKey(String key) {
        return cache.containsKey(key);
    }

    /**
     * Remove text result by key.
     */
    public void remove(String key) {
        cache.remove(key);
    }

    /**
     * Clear all cached text results.
     */
    public void clear() {
        cache.clear();
    }

    /**
     * Get number of cached text results.
     */
    public int size() {
        return cache.size();
    }
}
