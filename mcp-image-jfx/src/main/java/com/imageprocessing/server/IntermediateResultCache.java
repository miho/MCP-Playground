package com.imageprocessing.server;

import org.opencv.core.Mat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe cache for storing intermediate image processing results.
 * Stores OpenCV Mat objects keyed by user-provided string identifiers.
 */
public class IntermediateResultCache {
    private final Map<String, Mat> cache = new ConcurrentHashMap<>();

    /**
     * Store an image result in the cache.
     * @param key The unique identifier for this result
     * @param mat The OpenCV Mat to store
     */
    public void put(String key, Mat mat) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Cache key cannot be null or empty");
        }
        if (mat == null || mat.empty()) {
            throw new IllegalArgumentException("Cannot store null or empty Mat");
        }

        // Release existing Mat if key already exists to prevent memory leaks
        Mat existing = cache.get(key);
        if (existing != null) {
            existing.release();
        }

        cache.put(key, mat);
    }

    /**
     * Retrieve an image result from the cache.
     * @param key The unique identifier for the result
     * @return The cached Mat, or null if not found
     */
    public Mat get(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return cache.get(key);
    }

    /**
     * Check if a key exists in the cache.
     * @param key The key to check
     * @return true if the key exists and has a valid Mat
     */
    public boolean containsKey(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        Mat mat = cache.get(key);
        return mat != null && !mat.empty();
    }

    /**
     * Remove a result from the cache.
     * @param key The key to remove
     * @return true if the key was found and removed
     */
    public boolean remove(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        Mat mat = cache.remove(key);
        if (mat != null) {
            mat.release();
            return true;
        }
        return false;
    }

    /**
     * Clear all cached results and release their memory.
     */
    public void clear() {
        cache.values().forEach(Mat::release);
        cache.clear();
    }

    /**
     * Get the number of cached results.
     * @return The cache size
     */
    public int size() {
        return cache.size();
    }

    /**
     * Get a clone of the cached Mat (caller is responsible for releasing it).
     * @param key The key to retrieve
     * @return A clone of the cached Mat, or null if not found
     */
    public Mat getClone(String key) {
        Mat mat = get(key);
        return mat != null ? mat.clone() : null;
    }
}
