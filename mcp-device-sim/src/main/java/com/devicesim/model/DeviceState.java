package com.devicesim.model;

import java.util.Objects;

/**
 * Represents the current state of a simulated device.
 * This includes position, target, speed, acceleration, and movement status.
 *
 * <p>This class is immutable and thread-safe. Use the builder pattern or
 * constructors to create instances, and create new instances for state changes.</p>
 *
 * @since 1.0.0
 */
public class DeviceState {

    private final double x;
    private final double y;
    private final double targetX;
    private final double targetY;
    private final double speed;
    private final double acceleration;
    private final double maxSpeed;
    private final boolean isMoving;

    /**
     * Constructs a new DeviceState with default values (all zeros, not moving).
     */
    public DeviceState() {
        this(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 10.0, false);
    }

    /**
     * Constructs a new DeviceState with specified position.
     *
     * @param x the current x-coordinate
     * @param y the current y-coordinate
     */
    public DeviceState(double x, double y) {
        this(x, y, x, y, 0.0, 0.0, 10.0, false);
    }

    /**
     * Constructs a new DeviceState with specified position and max speed.
     *
     * @param x the current x-coordinate
     * @param y the current y-coordinate
     * @param maxSpeed the maximum speed in units per second
     * @throws IllegalArgumentException if maxSpeed is negative
     */
    public DeviceState(double x, double y, double maxSpeed) {
        this(x, y, x, y, 0.0, 0.0, maxSpeed, false);
    }

    /**
     * Constructs a new DeviceState with all parameters.
     *
     * @param x the current x-coordinate
     * @param y the current y-coordinate
     * @param targetX the target x-coordinate
     * @param targetY the target y-coordinate
     * @param speed the current speed in units per second
     * @param acceleration the acceleration in units per second squared
     * @param maxSpeed the maximum speed in units per second
     * @param isMoving whether the device is currently moving
     * @throws IllegalArgumentException if maxSpeed, speed, or acceleration is negative
     */
    public DeviceState(double x, double y, double targetX, double targetY,
                       double speed, double acceleration, double maxSpeed, boolean isMoving) {
        if (maxSpeed < 0) {
            throw new IllegalArgumentException("Max speed cannot be negative: " + maxSpeed);
        }
        if (speed < 0) {
            throw new IllegalArgumentException("Speed cannot be negative: " + speed);
        }
        if (acceleration < 0) {
            throw new IllegalArgumentException("Acceleration cannot be negative: " + acceleration);
        }

        this.x = x;
        this.y = y;
        this.targetX = targetX;
        this.targetY = targetY;
        this.speed = speed;
        this.acceleration = acceleration;
        this.maxSpeed = maxSpeed;
        this.isMoving = isMoving;
    }

    /**
     * Returns the current x-coordinate.
     *
     * @return the x-coordinate
     */
    public double getX() {
        return x;
    }

    /**
     * Returns the current y-coordinate.
     *
     * @return the y-coordinate
     */
    public double getY() {
        return y;
    }

    /**
     * Returns the target x-coordinate.
     *
     * @return the target x-coordinate
     */
    public double getTargetX() {
        return targetX;
    }

    /**
     * Returns the target y-coordinate.
     *
     * @return the target y-coordinate
     */
    public double getTargetY() {
        return targetY;
    }

    /**
     * Returns the current speed in units per second.
     *
     * @return the current speed
     */
    public double getSpeed() {
        return speed;
    }

    /**
     * Returns the acceleration in units per second squared.
     *
     * @return the acceleration
     */
    public double getAcceleration() {
        return acceleration;
    }

    /**
     * Returns the maximum speed in units per second.
     *
     * @return the maximum speed
     */
    public double getMaxSpeed() {
        return maxSpeed;
    }

    /**
     * Returns whether the device is currently moving.
     *
     * @return true if moving, false otherwise
     */
    public boolean isMoving() {
        return isMoving;
    }

    /**
     * Calculates the distance to the current target.
     *
     * @return the distance to the target
     */
    public double distanceToTarget() {
        double dx = targetX - x;
        double dy = targetY - y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Creates a new DeviceState with updated position.
     *
     * @param newX the new x-coordinate
     * @param newY the new y-coordinate
     * @return a new DeviceState instance
     */
    public DeviceState withPosition(double newX, double newY) {
        return new DeviceState(newX, newY, targetX, targetY, speed, acceleration, maxSpeed, isMoving);
    }

    /**
     * Creates a new DeviceState with updated target.
     *
     * @param newTargetX the new target x-coordinate
     * @param newTargetY the new target y-coordinate
     * @return a new DeviceState instance
     */
    public DeviceState withTarget(double newTargetX, double newTargetY) {
        return new DeviceState(x, y, newTargetX, newTargetY, speed, acceleration, maxSpeed, isMoving);
    }

    /**
     * Creates a new DeviceState with updated speed.
     *
     * @param newSpeed the new speed
     * @return a new DeviceState instance
     * @throws IllegalArgumentException if newSpeed is negative
     */
    public DeviceState withSpeed(double newSpeed) {
        return new DeviceState(x, y, targetX, targetY, newSpeed, acceleration, maxSpeed, isMoving);
    }

    /**
     * Creates a new DeviceState with updated max speed.
     *
     * @param newMaxSpeed the new maximum speed
     * @return a new DeviceState instance
     * @throws IllegalArgumentException if newMaxSpeed is negative
     */
    public DeviceState withMaxSpeed(double newMaxSpeed) {
        return new DeviceState(x, y, targetX, targetY, speed, acceleration, newMaxSpeed, isMoving);
    }

    /**
     * Creates a new DeviceState with updated acceleration.
     *
     * @param newAcceleration the new acceleration
     * @return a new DeviceState instance
     * @throws IllegalArgumentException if newAcceleration is negative
     */
    public DeviceState withAcceleration(double newAcceleration) {
        return new DeviceState(x, y, targetX, targetY, speed, newAcceleration, maxSpeed, isMoving);
    }

    /**
     * Creates a new DeviceState with updated movement status.
     *
     * @param newIsMoving the new movement status
     * @return a new DeviceState instance
     */
    public DeviceState withMoving(boolean newIsMoving) {
        return new DeviceState(x, y, targetX, targetY, speed, acceleration, maxSpeed, newIsMoving);
    }

    /**
     * Creates a builder for constructing DeviceState instances.
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a builder initialized with this state's values.
     *
     * @return a new Builder instance with current values
     */
    public Builder toBuilder() {
        return new Builder()
                .x(x)
                .y(y)
                .targetX(targetX)
                .targetY(targetY)
                .speed(speed)
                .acceleration(acceleration)
                .maxSpeed(maxSpeed)
                .isMoving(isMoving);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DeviceState that = (DeviceState) o;
        return Double.compare(that.x, x) == 0 &&
               Double.compare(that.y, y) == 0 &&
               Double.compare(that.targetX, targetX) == 0 &&
               Double.compare(that.targetY, targetY) == 0 &&
               Double.compare(that.speed, speed) == 0 &&
               Double.compare(that.acceleration, acceleration) == 0 &&
               Double.compare(that.maxSpeed, maxSpeed) == 0 &&
               isMoving == that.isMoving;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, targetX, targetY, speed, acceleration, maxSpeed, isMoving);
    }

    @Override
    public String toString() {
        return String.format(
                "DeviceState{pos=(%.2f, %.2f), target=(%.2f, %.2f), speed=%.2f/%.2f, accel=%.2f, moving=%s}",
                x, y, targetX, targetY, speed, maxSpeed, acceleration, isMoving
        );
    }

    /**
     * Builder for constructing DeviceState instances.
     */
    public static class Builder {
        private double x = 0.0;
        private double y = 0.0;
        private double targetX = 0.0;
        private double targetY = 0.0;
        private double speed = 0.0;
        private double acceleration = 0.0;
        private double maxSpeed = 10.0;
        private boolean isMoving = false;

        public Builder x(double x) {
            this.x = x;
            return this;
        }

        public Builder y(double y) {
            this.y = y;
            return this;
        }

        public Builder targetX(double targetX) {
            this.targetX = targetX;
            return this;
        }

        public Builder targetY(double targetY) {
            this.targetY = targetY;
            return this;
        }

        public Builder speed(double speed) {
            this.speed = speed;
            return this;
        }

        public Builder acceleration(double acceleration) {
            this.acceleration = acceleration;
            return this;
        }

        public Builder maxSpeed(double maxSpeed) {
            this.maxSpeed = maxSpeed;
            return this;
        }

        public Builder isMoving(boolean isMoving) {
            this.isMoving = isMoving;
            return this;
        }

        public DeviceState build() {
            return new DeviceState(x, y, targetX, targetY, speed, acceleration, maxSpeed, isMoving);
        }
    }
}
