package com.devicesim.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a target location in the device simulation system.
 * Each location has coordinates, a unique identifier, and additional properties
 * that can store metadata from CSV files (e.g., circularity, area, etc.).
 *
 * <p>This class is thread-safe for read operations, but external synchronization
 * is required if multiple threads modify the same instance.</p>
 *
 * @since 1.0.0
 */
public class Location {

    private final String id;
    private final double x;
    private final double y;
    private final Map<String, Object> properties;
    private volatile boolean visited;

    /**
     * Constructs a new Location with the specified coordinates and identifier.
     *
     * @param id the unique identifier for this location (must not be null)
     * @param x the x-coordinate
     * @param y the y-coordinate
     * @throws IllegalArgumentException if id is null or empty
     */
    public Location(String id, double x, double y) {
        this(id, x, y, new HashMap<>());
    }

    /**
     * Constructs a new Location with coordinates, identifier, and properties.
     *
     * @param id the unique identifier for this location (must not be null)
     * @param x the x-coordinate
     * @param y the y-coordinate
     * @param properties additional properties associated with this location
     * @throws IllegalArgumentException if id is null or empty, or if properties is null
     */
    public Location(String id, double x, double y, Map<String, Object> properties) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Location ID cannot be null or empty");
        }
        if (properties == null) {
            throw new IllegalArgumentException("Properties map cannot be null");
        }

        this.id = id;
        this.x = x;
        this.y = y;
        this.properties = new HashMap<>(properties);
        this.visited = false;
    }

    /**
     * Returns the unique identifier of this location.
     *
     * @return the location ID
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the x-coordinate of this location.
     *
     * @return the x-coordinate
     */
    public double getX() {
        return x;
    }

    /**
     * Returns the y-coordinate of this location.
     *
     * @return the y-coordinate
     */
    public double getY() {
        return y;
    }

    /**
     * Returns an unmodifiable view of the properties map.
     * To modify properties, use {@link #setProperty(String, Object)} or {@link #removeProperty(String)}.
     *
     * @return unmodifiable map of properties
     */
    public Map<String, Object> getProperties() {
        return Collections.unmodifiableMap(properties);
    }

    /**
     * Returns the value of a specific property.
     *
     * @param key the property key
     * @return the property value, or null if not found
     */
    public Object getProperty(String key) {
        return properties.get(key);
    }

    /**
     * Sets a property value.
     *
     * @param key the property key (must not be null)
     * @param value the property value
     * @throws IllegalArgumentException if key is null
     */
    public void setProperty(String key, Object value) {
        if (key == null) {
            throw new IllegalArgumentException("Property key cannot be null");
        }
        properties.put(key, value);
    }

    /**
     * Removes a property.
     *
     * @param key the property key to remove
     * @return the previous value associated with the key, or null if there was no mapping
     */
    public Object removeProperty(String key) {
        return properties.remove(key);
    }

    /**
     * Returns whether this location has been visited.
     *
     * @return true if visited, false otherwise
     */
    public boolean isVisited() {
        return visited;
    }

    /**
     * Sets the visited status of this location.
     *
     * @param visited true to mark as visited, false otherwise
     */
    public void setVisited(boolean visited) {
        this.visited = visited;
    }

    /**
     * Calculates the Euclidean distance to another location.
     *
     * @param other the other location
     * @return the distance to the other location
     * @throws IllegalArgumentException if other is null
     */
    public double distanceTo(Location other) {
        if (other == null) {
            throw new IllegalArgumentException("Other location cannot be null");
        }
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Calculates the Euclidean distance to a point.
     *
     * @param x the x-coordinate of the point
     * @param y the y-coordinate of the point
     * @return the distance to the point
     */
    public double distanceTo(double x, double y) {
        double dx = this.x - x;
        double dy = this.y - y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Location location = (Location) o;
        return Double.compare(location.x, x) == 0 &&
               Double.compare(location.y, y) == 0 &&
               Objects.equals(id, location.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, x, y);
    }

    @Override
    public String toString() {
        return String.format("Location{id='%s', x=%.2f, y=%.2f, visited=%s, properties=%s}",
                id, x, y, visited, properties);
    }
}
