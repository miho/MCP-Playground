package com.embeddedcc.instrumentation;

/**
 * Represents an array access detected in C source code.
 */
public final class ArrayAccess {

    public enum AccessType {
        LOAD,
        STORE,
        UNKNOWN
    }

    private final String expression;
    private final int line;
    private final int column;
    private final AccessType accessType;

    public ArrayAccess(String expression, int line, int column, AccessType accessType) {
        this.expression = expression;
        this.line = line;
        this.column = column;
        this.accessType = accessType;
    }

    public String getExpression() {
        return expression;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    public AccessType getAccessType() {
        return accessType;
    }

    public String getKey() {
        return expression + ":" + line + ":" + column;
    }

    @Override
    public String toString() {
        return "ArrayAccess{" +
                "expression='" + expression + '\'' +
                ", line=" + line +
                ", column=" + column +
                ", accessType=" + accessType +
                '}';
    }
}

