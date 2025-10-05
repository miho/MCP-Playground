package com.devicesim.data;

import com.devicesim.model.Location;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * CSV file reader for loading and filtering location data.
 * Uses OpenCSV library for robust CSV parsing.
 *
 * <p>This class is thread-safe for concurrent read operations on different files,
 * but not designed for concurrent modifications.</p>
 *
 * <p>Example usage:</p>
 * <pre>
 * CsvDataReader reader = new CsvDataReader();
 * List&lt;String&gt; headers = reader.getHeaders("data.csv");
 *
 * Map&lt;String, FilterCriteria&gt; filters = new HashMap&lt;&gt;();
 * filters.put("area", new FilterCriteria("area", 100.0, 500.0));
 *
 * List&lt;Location&gt; locations = reader.readLocations("data.csv", "x", "y", filters);
 * </pre>
 *
 * @since 1.0.0
 */
public class CsvDataReader {

    /**
     * Reads the header row from a CSV file.
     *
     * @param filePath the path to the CSV file (must not be null)
     * @return list of column headers
     * @throws IOException if the file cannot be read
     * @throws IllegalArgumentException if filePath is null or empty, or if file doesn't exist
     * @throws CsvReadException if CSV parsing fails
     */
    public List<String> getHeaders(String filePath) throws IOException {
        validateFilePath(filePath);

        try (CSVReader reader = new CSVReader(new FileReader(filePath))) {
            String[] headers = reader.readNext();
            if (headers == null || headers.length == 0) {
                throw new CsvReadException("CSV file is empty or has no headers: " + filePath);
            }
            return Arrays.stream(headers)
                    .map(String::trim)
                    .collect(Collectors.toList());
        } catch (CsvException e) {
            throw new CsvReadException("Failed to parse CSV headers from: " + filePath, e);
        }
    }

    /**
     * Reads locations from a CSV file with optional filtering.
     *
     * @param filePath the path to the CSV file (must not be null)
     * @param xColumn the name of the column containing x-coordinates (must not be null)
     * @param yColumn the name of the column containing y-coordinates (must not be null)
     * @param filters optional map of column names to filter criteria (can be null or empty)
     * @return list of Location objects that pass all filters
     * @throws IOException if the file cannot be read
     * @throws IllegalArgumentException if required parameters are null/empty, or if columns don't exist
     * @throws CsvReadException if CSV parsing fails
     */
    public List<Location> readLocations(String filePath, String xColumn, String yColumn,
                                       Map<String, FilterCriteria> filters) throws IOException {
        validateFilePath(filePath);
        validateColumnName(xColumn, "X column");
        validateColumnName(yColumn, "Y column");

        Map<String, FilterCriteria> filterMap = filters != null ? filters : Collections.emptyMap();

        try (CSVReader reader = new CSVReader(new FileReader(filePath))) {
            List<String[]> allRows = reader.readAll();

            if (allRows.isEmpty()) {
                throw new CsvReadException("CSV file is empty: " + filePath);
            }

            // Parse headers
            String[] headers = allRows.get(0);
            Map<String, Integer> headerIndexMap = buildHeaderIndexMap(headers);

            // Validate required columns exist
            int xIndex = getColumnIndex(headerIndexMap, xColumn, "X coordinate");
            int yIndex = getColumnIndex(headerIndexMap, yColumn, "Y coordinate");

            // Validate filter columns exist
            for (String filterColumn : filterMap.keySet()) {
                if (!headerIndexMap.containsKey(filterColumn.trim())) {
                    throw new IllegalArgumentException(
                            "Filter column not found in CSV: " + filterColumn);
                }
            }

            // Process data rows
            List<Location> locations = new ArrayList<>();
            int rowNumber = 1; // Start from 1 (0 is header)

            for (int i = 1; i < allRows.size(); i++) {
                String[] row = allRows.get(i);
                rowNumber = i + 1; // For error messages (1-based)

                try {
                    // Skip empty rows
                    if (isEmptyRow(row)) {
                        continue;
                    }

                    // Parse coordinates
                    double x = parseDouble(row, xIndex, xColumn, rowNumber);
                    double y = parseDouble(row, yIndex, yColumn, rowNumber);

                    // Build properties map
                    Map<String, Object> properties = new HashMap<>();
                    for (int j = 0; j < headers.length && j < row.length; j++) {
                        String header = headers[j].trim();
                        String value = row[j].trim();

                        if (!value.isEmpty()) {
                            // Try to parse as number, otherwise store as string
                            try {
                                properties.put(header, Double.parseDouble(value));
                            } catch (NumberFormatException e) {
                                properties.put(header, value);
                            }
                        }
                    }

                    // Apply filters
                    if (passesFilters(properties, filterMap)) {
                        String id = generateLocationId(rowNumber, x, y);
                        Location location = new Location(id, x, y, properties);
                        locations.add(location);
                    }

                } catch (NumberFormatException e) {
                    // Log warning and skip invalid row
                    System.err.printf("Warning: Skipping row %d due to invalid number format: %s%n",
                            rowNumber, e.getMessage());
                } catch (ArrayIndexOutOfBoundsException e) {
                    System.err.printf("Warning: Skipping row %d due to insufficient columns%n", rowNumber);
                }
            }

            return locations;

        } catch (CsvException e) {
            throw new CsvReadException("Failed to parse CSV file: " + filePath, e);
        }
    }

    /**
     * Checks if a row passes all filter criteria.
     *
     * @param properties the properties map for the row
     * @param filters the filter criteria to apply
     * @return true if the row passes all filters, false otherwise
     */
    private boolean passesFilters(Map<String, Object> properties, Map<String, FilterCriteria> filters) {
        for (FilterCriteria filter : filters.values()) {
            if (!filter.passes(properties)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Builds a map of header names to column indices.
     *
     * @param headers the header array
     * @return map of header names to indices
     */
    private Map<String, Integer> buildHeaderIndexMap(String[] headers) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            map.put(headers[i].trim(), i);
        }
        return map;
    }

    /**
     * Gets the column index for a given column name.
     *
     * @param headerMap the header index map
     * @param columnName the column name
     * @param description description for error messages
     * @return the column index
     * @throws IllegalArgumentException if column not found
     */
    private int getColumnIndex(Map<String, Integer> headerMap, String columnName, String description) {
        Integer index = headerMap.get(columnName.trim());
        if (index == null) {
            throw new IllegalArgumentException(
                    description + " column not found: " + columnName +
                    ". Available columns: " + headerMap.keySet());
        }
        return index;
    }

    /**
     * Parses a double value from a CSV row.
     *
     * @param row the CSV row
     * @param index the column index
     * @param columnName the column name (for error messages)
     * @param rowNumber the row number (for error messages)
     * @return the parsed double value
     * @throws NumberFormatException if parsing fails
     */
    private double parseDouble(String[] row, int index, String columnName, int rowNumber) {
        if (index >= row.length) {
            throw new NumberFormatException(
                    String.format("Column '%s' not found in row %d", columnName, rowNumber));
        }
        String value = row[index].trim();
        if (value.isEmpty()) {
            throw new NumberFormatException(
                    String.format("Empty value for column '%s' in row %d", columnName, rowNumber));
        }
        return Double.parseDouble(value);
    }

    /**
     * Checks if a row is empty (all cells are empty or whitespace).
     *
     * @param row the CSV row
     * @return true if empty, false otherwise
     */
    private boolean isEmptyRow(String[] row) {
        if (row == null || row.length == 0) {
            return true;
        }
        for (String cell : row) {
            if (cell != null && !cell.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Generates a unique ID for a location.
     *
     * @param rowNumber the row number in the CSV
     * @param x the x-coordinate
     * @param y the y-coordinate
     * @return a unique location ID
     */
    private String generateLocationId(int rowNumber, double x, double y) {
        return String.format("loc_%d_%.2f_%.2f", rowNumber, x, y);
    }

    /**
     * Validates a file path.
     *
     * @param filePath the file path to validate
     * @throws IllegalArgumentException if path is invalid
     */
    private void validateFilePath(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IllegalArgumentException("File path cannot be null or empty");
        }

        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("File does not exist: " + filePath);
        }
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Path is not a regular file: " + filePath);
        }
        if (!Files.isReadable(path)) {
            throw new IllegalArgumentException("File is not readable: " + filePath);
        }
    }

    /**
     * Validates a column name.
     *
     * @param columnName the column name to validate
     * @param description description for error messages
     * @throws IllegalArgumentException if column name is invalid
     */
    private void validateColumnName(String columnName, String description) {
        if (columnName == null || columnName.trim().isEmpty()) {
            throw new IllegalArgumentException(description + " cannot be null or empty");
        }
    }

    /**
     * Filter criteria for CSV data rows.
     * Supports range filtering (min/max) and exact value matching.
     */
    public static class FilterCriteria {
        private final String columnName;
        private final Double minValue;
        private final Double maxValue;
        private final Object equalsValue;

        /**
         * Creates a range filter.
         *
         * @param columnName the column to filter (must not be null)
         * @param minValue minimum value (inclusive), or null for no minimum
         * @param maxValue maximum value (inclusive), or null for no maximum
         * @throws IllegalArgumentException if columnName is null or empty
         */
        public FilterCriteria(String columnName, Double minValue, Double maxValue) {
            if (columnName == null || columnName.trim().isEmpty()) {
                throw new IllegalArgumentException("Column name cannot be null or empty");
            }
            this.columnName = columnName.trim();
            this.minValue = minValue;
            this.maxValue = maxValue;
            this.equalsValue = null;
        }

        /**
         * Creates an equality filter.
         *
         * @param columnName the column to filter (must not be null)
         * @param equalsValue the value to match (must not be null)
         * @throws IllegalArgumentException if columnName or equalsValue is null
         */
        public FilterCriteria(String columnName, Object equalsValue) {
            if (columnName == null || columnName.trim().isEmpty()) {
                throw new IllegalArgumentException("Column name cannot be null or empty");
            }
            if (equalsValue == null) {
                throw new IllegalArgumentException("Equals value cannot be null");
            }
            this.columnName = columnName.trim();
            this.minValue = null;
            this.maxValue = null;
            this.equalsValue = equalsValue;
        }

        /**
         * Checks if a row's properties pass this filter.
         *
         * @param properties the row properties
         * @return true if passes filter, false otherwise
         */
        public boolean passes(Map<String, Object> properties) {
            Object value = properties.get(columnName);

            if (value == null) {
                return false;
            }

            // Equality filter
            if (equalsValue != null) {
                return equalsValue.equals(value);
            }

            // Range filter (requires numeric value)
            if (!(value instanceof Number)) {
                return false;
            }

            double numValue = ((Number) value).doubleValue();

            if (minValue != null && numValue < minValue) {
                return false;
            }

            if (maxValue != null && numValue > maxValue) {
                return false;
            }

            return true;
        }

        public String getColumnName() {
            return columnName;
        }

        public Double getMinValue() {
            return minValue;
        }

        public Double getMaxValue() {
            return maxValue;
        }

        public Object getEqualsValue() {
            return equalsValue;
        }

        @Override
        public String toString() {
            if (equalsValue != null) {
                return String.format("FilterCriteria{%s == %s}", columnName, equalsValue);
            } else {
                return String.format("FilterCriteria{%s: [%s, %s]}", columnName, minValue, maxValue);
            }
        }
    }

    /**
     * Exception thrown when CSV reading or parsing fails.
     */
    public static class CsvReadException extends RuntimeException {
        public CsvReadException(String message) {
            super(message);
        }

        public CsvReadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
