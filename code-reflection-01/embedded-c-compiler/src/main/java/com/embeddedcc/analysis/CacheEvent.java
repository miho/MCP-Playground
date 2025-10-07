package com.embeddedcc.analysis;

import com.embeddedcc.instrumentation.InstrumentationPoint;

public record CacheEvent(CacheEventType type,
                         int id,
                         int line,
                         String label,
                         InstrumentationPoint source) {
}

