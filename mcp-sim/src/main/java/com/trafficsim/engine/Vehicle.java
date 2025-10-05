package com.trafficsim.engine;

import com.trafficsim.model.Direction;

/**
 * Represents a single vehicle in the simulation.
 */
class Vehicle {
    private final Direction direction;
    private final double arrivalTime;
    private double departureTime;
    private int stops;

    public Vehicle(Direction direction, double arrivalTime) {
        this.direction = direction;
        this.arrivalTime = arrivalTime;
        this.departureTime = -1;
        this.stops = 0;
    }

    public Direction getDirection() {
        return direction;
    }

    public double getArrivalTime() {
        return arrivalTime;
    }

    public double getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(double departureTime) {
        this.departureTime = departureTime;
    }

    public int getStops() {
        return stops;
    }

    public void incrementStops() {
        this.stops++;
    }

    public double getDelay() {
        if (departureTime < 0) return 0;
        return departureTime - arrivalTime;
    }
}
