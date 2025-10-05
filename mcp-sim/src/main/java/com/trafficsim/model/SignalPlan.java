package com.trafficsim.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a complete signal timing plan for the intersection.
 */
public class SignalPlan {
    private final double cycleSeconds;
    private final List<Phase> phases;
    private final double yellowSeconds;
    private final double allRedSeconds;

    public SignalPlan(double cycleSeconds, List<Phase> phases, double yellowSeconds, double allRedSeconds) {
        if (cycleSeconds < 30 || cycleSeconds > 180) {
            throw new IllegalArgumentException("Cycle length must be between 30 and 180 seconds");
        }
        if (phases == null || phases.isEmpty()) {
            throw new IllegalArgumentException("At least one phase is required");
        }
        this.cycleSeconds = cycleSeconds;
        this.phases = new ArrayList<>(phases);
        this.yellowSeconds = yellowSeconds;
        this.allRedSeconds = allRedSeconds;
    }

    public double getCycleSeconds() {
        return cycleSeconds;
    }

    public List<Phase> getPhases() {
        return new ArrayList<>(phases);
    }

    public double getYellowSeconds() {
        return yellowSeconds;
    }

    public double getAllRedSeconds() {
        return allRedSeconds;
    }

    public double getTotalPhaseTime() {
        return phases.stream()
                .mapToDouble(Phase::getGreenSeconds)
                .sum() + (phases.size() * (yellowSeconds + allRedSeconds));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SignalPlan that = (SignalPlan) o;
        return Double.compare(that.cycleSeconds, cycleSeconds) == 0 &&
                Double.compare(that.yellowSeconds, yellowSeconds) == 0 &&
                Double.compare(that.allRedSeconds, allRedSeconds) == 0 &&
                Objects.equals(phases, that.phases);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cycleSeconds, phases, yellowSeconds, allRedSeconds);
    }

    @Override
    public String toString() {
        return "SignalPlan{cycleSeconds=" + cycleSeconds +
                ", phases=" + phases +
                ", yellowSeconds=" + yellowSeconds +
                ", allRedSeconds=" + allRedSeconds + "}";
    }
}
