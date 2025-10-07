package com.embeddedcc.analysis;

import java.util.Collections;
import java.util.List;

public final class CacheSummary {

    private final int hits;
    private final int misses;
    private final int evictions;
    private final List<CacheEvent> events;

    private CacheSummary(int hits, int misses, int evictions, List<CacheEvent> events) {
        this.hits = hits;
        this.misses = misses;
        this.evictions = evictions;
        this.events = List.copyOf(events);
    }

    public static CacheSummary empty() {
        return new CacheSummary(0, 0, 0, List.of());
    }

    public static CacheSummary of(int hits, int misses, int evictions, List<CacheEvent> events) {
        return new CacheSummary(hits, misses, evictions, events);
    }

    public int getHits() {
        return hits;
    }

    public int getMisses() {
        return misses;
    }

    public int getEvictions() {
        return evictions;
    }

    public List<CacheEvent> getEvents() {
        return Collections.unmodifiableList(events);
    }
}

