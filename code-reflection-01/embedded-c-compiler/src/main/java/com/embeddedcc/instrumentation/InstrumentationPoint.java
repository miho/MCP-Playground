package com.embeddedcc.instrumentation;

/**
 * Represents a concrete instrumentation statement injected into the C program.
 */
public final class InstrumentationPoint {

    private final int id;
    private final ArrayAccess access;

    public InstrumentationPoint(int id, ArrayAccess access) {
        this.id = id;
        this.access = access;
    }

    public int getId() {
        return id;
    }

    public ArrayAccess getAccess() {
        return access;
    }

    @Override
    public String toString() {
        return "InstrumentationPoint{" +
                "id=" + id +
                ", access=" + access +
                '}';
    }
}

