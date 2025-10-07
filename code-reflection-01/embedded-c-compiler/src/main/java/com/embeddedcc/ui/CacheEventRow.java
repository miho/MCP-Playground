package com.embeddedcc.ui;

import com.embeddedcc.analysis.CacheEvent;
import com.embeddedcc.analysis.CacheEventType;

final class CacheEventRow {
    private final CacheEvent event;

    CacheEventRow(CacheEvent event) {
        this.event = event;
    }

    CacheEvent getEvent() {
        return event;
    }

    String describe() {
        String type = event.type().name();
        String label = event.label();
        if (event.source() != null) {
            label = event.source().getAccess().getExpression();
        }
        return type + " - line " + event.line() + " - " + label;
    }

    CacheEventType getType() {
        return event.type();
    }

    @Override
    public String toString() {
        return describe();
    }
}
