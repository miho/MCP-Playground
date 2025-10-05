package com.devicesim.engine;

import com.devicesim.model.Location;

import java.util.List;

/**
 * Listener interface for simulator state changes.
 * Enables UI and other components to react to state changes made by MCP or other sources.
 *
 * @since 1.0.0
 */
public interface SimulatorStateListener {

    /**
     * Called when target locations are set or updated.
     *
     * @param locations the new list of locations
     */
    void onLocationsChanged(List<Location> locations);

    /**
     * Called when the current target changes (e.g., when advancing to next target).
     *
     * @param targetIndex the new target index
     * @param target the new target location, or null if no target
     */
    void onTargetChanged(int targetIndex, Location target);

    /**
     * Called when a location is marked as visited.
     *
     * @param location the location that was visited
     * @param index the index of the visited location
     */
    void onLocationVisited(Location location, int index);

    /**
     * Called when the device speed is changed.
     *
     * @param maxSpeed the new maximum speed
     */
    void onSpeedChanged(double maxSpeed);

    /**
     * Called when the device acceleration is changed.
     *
     * @param acceleration the new acceleration
     */
    void onAccelerationChanged(double acceleration);

    /**
     * Called when the simulator is reset.
     */
    void onReset();

    /**
     * Called when movement starts or stops.
     *
     * @param moving true if movement started, false if stopped
     */
    void onMovementStateChanged(boolean moving);
}
