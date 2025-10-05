package com.devicesim.engine;

import com.devicesim.model.DeviceState;
import com.devicesim.model.Location;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * The main simulation engine for device movement.
 * Manages device position, movement, and target locations with smooth acceleration and deceleration.
 *
 * <p>This class is thread-safe and can be safely accessed from multiple threads.</p>
 *
 * <p>Movement algorithm:</p>
 * <ul>
 *   <li>Accelerates smoothly up to max speed when starting movement</li>
 *   <li>Decelerates when approaching target to stop precisely</li>
 *   <li>Automatically advances to next target when current target is reached (within 0.5 units)</li>
 *   <li>Stops at each target and waits for explicit command to move to next</li>
 * </ul>
 *
 * @since 1.0.0
 */
public class DeviceSimulator {

    private static final double ARRIVAL_THRESHOLD = 0.5; // Distance threshold for reaching target
    private static final double DEFAULT_MAX_SPEED = 10.0; // units per second
    private static final double DEFAULT_ACCELERATION = 5.0; // units per second squared

    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final List<Location> locations = new ArrayList<>();

    private DeviceState currentState;
    private int currentTargetIndex = -1;
    private boolean autoAdvance = false;

    /**
     * Constructs a new DeviceSimulator with default starting position (0, 0).
     */
    public DeviceSimulator() {
        this(0.0, 0.0);
    }

    /**
     * Constructs a new DeviceSimulator with specified starting position.
     *
     * @param startX the starting x-coordinate
     * @param startY the starting y-coordinate
     */
    public DeviceSimulator(double startX, double startY) {
        this.currentState = new DeviceState(startX, startY, DEFAULT_MAX_SPEED);
        this.currentState = currentState.withAcceleration(DEFAULT_ACCELERATION);
    }

    /**
     * Sets the list of target locations for the device to visit.
     * Resets the simulation to the first location.
     *
     * @param locations the list of locations to visit (must not be null)
     * @throws IllegalArgumentException if locations is null
     */
    public void setTargetLocations(List<Location> locations) {
        if (locations == null) {
            throw new IllegalArgumentException("Locations list cannot be null");
        }

        lock.writeLock().lock();
        try {
            this.locations.clear();
            this.locations.addAll(locations);
            this.currentTargetIndex = locations.isEmpty() ? -1 : 0;

            // Reset visited status
            for (Location loc : this.locations) {
                loc.setVisited(false);
            }

            // Set first target if available
            if (!locations.isEmpty()) {
                Location firstTarget = locations.get(0);
                currentState = currentState.withTarget(firstTarget.getX(), firstTarget.getY());
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Updates the simulation by the specified time delta.
     * Handles smooth movement with acceleration and deceleration.
     *
     * @param deltaTime the time elapsed in seconds (must be positive)
     * @throws IllegalArgumentException if deltaTime is negative
     */
    public void update(double deltaTime) {
        if (deltaTime < 0) {
            throw new IllegalArgumentException("Delta time cannot be negative: " + deltaTime);
        }

        if (deltaTime == 0) {
            return;
        }

        lock.writeLock().lock();
        try {
            if (!currentState.isMoving() || currentTargetIndex < 0 || currentTargetIndex >= locations.size()) {
                return;
            }

            Location target = locations.get(currentTargetIndex);
            double dx = target.getX() - currentState.getX();
            double dy = target.getY() - currentState.getY();
            double distanceToTarget = Math.sqrt(dx * dx + dy * dy);

            // Check if we've reached the target
            if (distanceToTarget <= ARRIVAL_THRESHOLD) {
                // Stop at target
                currentState = currentState
                        .withPosition(target.getX(), target.getY())
                        .withSpeed(0.0)
                        .withMoving(false);

                target.setVisited(true);

                // Auto-advance to next target if enabled
                if (autoAdvance && currentTargetIndex < locations.size() - 1) {
                    moveToNextTarget();
                }

                return;
            }

            // Calculate direction vector
            double dirX = dx / distanceToTarget;
            double dirY = dy / distanceToTarget;

            // Calculate deceleration distance (distance needed to stop from current speed)
            double decelDistance = (currentState.getSpeed() * currentState.getSpeed()) /
                                  (2 * currentState.getAcceleration());

            double newSpeed;
            if (distanceToTarget <= decelDistance) {
                // Decelerate
                newSpeed = Math.max(0, currentState.getSpeed() - currentState.getAcceleration() * deltaTime);
            } else {
                // Accelerate up to max speed
                newSpeed = Math.min(currentState.getMaxSpeed(),
                                   currentState.getSpeed() + currentState.getAcceleration() * deltaTime);
            }

            // Use average speed for this time step for smoother movement
            double avgSpeed = (currentState.getSpeed() + newSpeed) / 2.0;
            double distanceToMove = avgSpeed * deltaTime;

            // Don't overshoot the target
            if (distanceToMove >= distanceToTarget) {
                currentState = currentState
                        .withPosition(target.getX(), target.getY())
                        .withSpeed(0.0)
                        .withMoving(false);

                target.setVisited(true);

                if (autoAdvance && currentTargetIndex < locations.size() - 1) {
                    moveToNextTarget();
                }
            } else {
                // Move toward target
                double newX = currentState.getX() + dirX * distanceToMove;
                double newY = currentState.getY() + dirY * distanceToMove;

                currentState = currentState
                        .withPosition(newX, newY)
                        .withSpeed(newSpeed);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Sets the maximum speed for the device.
     *
     * @param maxSpeed the maximum speed in units per second (must be positive)
     * @throws IllegalArgumentException if maxSpeed is not positive
     */
    public void setSpeed(double maxSpeed) {
        if (maxSpeed <= 0) {
            throw new IllegalArgumentException("Max speed must be positive: " + maxSpeed);
        }

        lock.writeLock().lock();
        try {
            currentState = currentState.withMaxSpeed(maxSpeed);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Sets the acceleration for the device.
     *
     * @param acceleration the acceleration in units per second squared (must be positive)
     * @throws IllegalArgumentException if acceleration is not positive
     */
    public void setAcceleration(double acceleration) {
        if (acceleration <= 0) {
            throw new IllegalArgumentException("Acceleration must be positive: " + acceleration);
        }

        lock.writeLock().lock();
        try {
            currentState = currentState.withAcceleration(acceleration);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Returns the current device state.
     * The returned state is a snapshot and will not reflect future changes.
     *
     * @return the current device state
     */
    public DeviceState getState() {
        lock.readLock().lock();
        try {
            return currentState;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns the current target location, or null if no target is set.
     *
     * @return the current target location, or null
     */
    public Location getCurrentTarget() {
        lock.readLock().lock();
        try {
            if (currentTargetIndex >= 0 && currentTargetIndex < locations.size()) {
                return locations.get(currentTargetIndex);
            }
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns an unmodifiable view of all locations.
     *
     * @return unmodifiable list of all locations
     */
    public List<Location> getAllLocations() {
        lock.readLock().lock();
        try {
            return Collections.unmodifiableList(new ArrayList<>(locations));
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Marks the current target as visited and moves to the next target.
     * If already at the last target or no targets exist, this method does nothing.
     */
    public void markCurrentAsVisited() {
        lock.writeLock().lock();
        try {
            if (currentTargetIndex >= 0 && currentTargetIndex < locations.size()) {
                locations.get(currentTargetIndex).setVisited(true);
                moveToNextTarget();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Starts movement toward the current target.
     * If no target is set or already at target, this method does nothing.
     */
    public void startMovement() {
        lock.writeLock().lock();
        try {
            if (currentTargetIndex >= 0 && currentTargetIndex < locations.size()) {
                currentState = currentState.withMoving(true);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Stops all movement immediately.
     * The device will remain at its current position with zero speed.
     */
    public void stopMovement() {
        lock.writeLock().lock();
        try {
            currentState = currentState
                    .withMoving(false)
                    .withSpeed(0.0);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Sets whether the device should automatically advance to the next target
     * after reaching the current target.
     *
     * @param autoAdvance true to enable auto-advance, false otherwise
     */
    public void setAutoAdvance(boolean autoAdvance) {
        lock.writeLock().lock();
        try {
            this.autoAdvance = autoAdvance;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Returns whether auto-advance is enabled.
     *
     * @return true if auto-advance is enabled, false otherwise
     */
    public boolean isAutoAdvance() {
        lock.readLock().lock();
        try {
            return autoAdvance;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns the index of the current target.
     *
     * @return the current target index, or -1 if no target is set
     */
    public int getCurrentTargetIndex() {
        lock.readLock().lock();
        try {
            return currentTargetIndex;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Moves to the next target in the list.
     * This method should be called while holding the write lock.
     */
    private void moveToNextTarget() {
        if (currentTargetIndex < locations.size() - 1) {
            currentTargetIndex++;
            Location nextTarget = locations.get(currentTargetIndex);
            currentState = currentState
                    .withTarget(nextTarget.getX(), nextTarget.getY())
                    .withSpeed(0.0)
                    .withMoving(autoAdvance);
        }
    }

    /**
     * Resets the simulation to the initial state.
     * All locations are marked as not visited, and the device returns to origin (0, 0).
     * The first target is set but the device remains at origin.
     */
    public void reset() {
        lock.writeLock().lock();
        try {
            currentTargetIndex = locations.isEmpty() ? -1 : 0;

            for (Location loc : locations) {
                loc.setVisited(false);
            }

            if (!locations.isEmpty()) {
                Location firstTarget = locations.get(0);
                currentState = currentState
                        .withPosition(0.0, 0.0)
                        .withTarget(firstTarget.getX(), firstTarget.getY())
                        .withSpeed(0.0)
                        .withMoving(false);
            } else {
                // No locations, just reset to origin
                currentState = currentState
                        .withPosition(0.0, 0.0)
                        .withTarget(0.0, 0.0)
                        .withSpeed(0.0)
                        .withMoving(false);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
}
