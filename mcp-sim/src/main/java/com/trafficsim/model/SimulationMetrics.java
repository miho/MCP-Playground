package com.trafficsim.model;

/**
 * Performance metrics from a simulation run.
 */
public class SimulationMetrics {
    private final double avgDelaySec;
    private final double queueP95;
    private final double throughputVph;
    private final double stopsPerVeh;

    public SimulationMetrics(double avgDelaySec, double queueP95, double throughputVph, double stopsPerVeh) {
        this.avgDelaySec = avgDelaySec;
        this.queueP95 = queueP95;
        this.throughputVph = throughputVph;
        this.stopsPerVeh = stopsPerVeh;
    }

    public double getAvgDelaySec() {
        return avgDelaySec;
    }

    public double getQueueP95() {
        return queueP95;
    }

    public double getThroughputVph() {
        return throughputVph;
    }

    public double getStopsPerVeh() {
        return stopsPerVeh;
    }

    @Override
    public String toString() {
        return String.format("Metrics{avgDelay=%.1fs, queueP95=%.1f, throughput=%.0fvph, stops/veh=%.2f}",
                avgDelaySec, queueP95, throughputVph, stopsPerVeh);
    }
}
