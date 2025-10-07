package com.embeddedcc.instrumentation;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Holds the result of instrumenting C source code.
 */
public final class InstrumentedProgram {

    private final String sourceCode;
    private final List<InstrumentationPoint> points;
    private final Map<Integer, InstrumentationPoint> idLookup;

    public InstrumentedProgram(String sourceCode,
                               List<InstrumentationPoint> points,
                               Map<Integer, InstrumentationPoint> idLookup) {
        this.sourceCode = sourceCode;
        this.points = List.copyOf(points);
        this.idLookup = Map.copyOf(idLookup);
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public List<InstrumentationPoint> getPoints() {
        return points;
    }

    public Map<Integer, InstrumentationPoint> getIdLookup() {
        return idLookup;
    }
}

