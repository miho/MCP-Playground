package com.devicesim.data;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages CSV file state and notifies listeners when CSV operations occur.
 * This allows the UI to stay in sync with MCP operations.
 *
 * @since 1.0.0
 */
public class CsvStateManager {

    private static CsvStateManager instance;

    private final List<CsvStateListener> listeners = new CopyOnWriteArrayList<>();
    private volatile String currentFilePath;
    private volatile List<String> currentHeaders;

    private CsvStateManager() {
        this.currentFilePath = null;
        this.currentHeaders = new ArrayList<>();
    }

    /**
     * Get the singleton instance.
     *
     * @return the CSV state manager instance
     */
    public static synchronized CsvStateManager getInstance() {
        if (instance == null) {
            instance = new CsvStateManager();
        }
        return instance;
    }

    /**
     * Add a listener for CSV state changes.
     *
     * @param listener the listener to add
     */
    public void addListener(CsvStateListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * Remove a listener.
     *
     * @param listener the listener to remove
     */
    public void removeListener(CsvStateListener listener) {
        listeners.remove(listener);
    }

    /**
     * Notify that headers were read from a CSV file.
     *
     * @param filePath the file path
     * @param headers the headers that were read
     */
    public void notifyHeadersRead(String filePath, List<String> headers) {
        this.currentFilePath = filePath;
        this.currentHeaders = new ArrayList<>(headers);

        for (CsvStateListener listener : listeners) {
            try {
                listener.onHeadersRead(filePath, headers);
            } catch (Exception e) {
                System.err.println("Error notifying CSV listener: " + e.getMessage());
            }
        }
    }

    /**
     * Notify that locations were queried from a CSV file.
     *
     * @param filePath the file path
     * @param xColumn the X column name
     * @param yColumn the Y column name
     * @param locationCount the number of locations found
     */
    public void notifyLocationsQueried(String filePath, String xColumn, String yColumn, int locationCount) {
        this.currentFilePath = filePath;

        for (CsvStateListener listener : listeners) {
            try {
                listener.onLocationsQueried(filePath, xColumn, yColumn, locationCount);
            } catch (Exception e) {
                System.err.println("Error notifying CSV listener: " + e.getMessage());
            }
        }
    }

    /**
     * Get the current file path.
     *
     * @return the current file path, or null if none
     */
    public String getCurrentFilePath() {
        return currentFilePath;
    }

    /**
     * Get the current headers.
     *
     * @return the current headers
     */
    public List<String> getCurrentHeaders() {
        return new ArrayList<>(currentHeaders);
    }

    /**
     * Listener interface for CSV state changes.
     */
    public interface CsvStateListener {
        /**
         * Called when headers are read from a CSV file.
         *
         * @param filePath the file path
         * @param headers the headers
         */
        void onHeadersRead(String filePath, List<String> headers);

        /**
         * Called when locations are queried from a CSV file.
         *
         * @param filePath the file path
         * @param xColumn the X column name
         * @param yColumn the Y column name
         * @param locationCount the number of locations found
         */
        void onLocationsQueried(String filePath, String xColumn, String yColumn, int locationCount);
    }
}
