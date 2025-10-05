package com.trafficsim.model;

import java.util.Objects;

/**
 * Represents a signal phase with a name and green duration.
 */
public class Phase {
    private final String name;
    private final double greenSeconds;

    public Phase(String name, double greenSeconds) {
        if (greenSeconds < 6 || greenSeconds > 120) {
            throw new IllegalArgumentException("Green duration must be between 6 and 120 seconds");
        }
        this.name = name;
        this.greenSeconds = greenSeconds;
    }

    public String getName() {
        return name;
    }

    public double getGreenSeconds() {
        return greenSeconds;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Phase phase = (Phase) o;
        return Double.compare(phase.greenSeconds, greenSeconds) == 0 &&
                Objects.equals(name, phase.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, greenSeconds);
    }

    @Override
    public String toString() {
        return "Phase{name='" + name + "', greenSeconds=" + greenSeconds + "}";
    }
}
