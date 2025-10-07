package com.embeddedcc.analysis;

import com.embeddedcc.instrumentation.InstrumentationPoint;

import java.util.*;
import java.util.stream.Collectors;

public final class CacheInsights {

    private CacheInsights() {
    }

    public static List<Map<String, Object>> hotspots(CacheSummary summary,
                                                     Map<Integer, InstrumentationPoint> points,
                                                     int limit) {
        Map<Integer, HotspotAccumulator> accumulators = new HashMap<>();
        for (CacheEvent event : summary.getEvents()) {
            HotspotAccumulator acc = accumulators.computeIfAbsent(event.id(),
                    key -> new HotspotAccumulator(points.get(key), event));
            switch (event.type()) {
                case HIT -> acc.hits++;
                case MISS -> acc.misses++;
                case EVICTION -> acc.evictions++;
            }
        }

        return accumulators.values().stream()
                .sorted(Comparator.comparingInt(HotspotAccumulator::score).reversed())
                .limit(limit)
                .map(HotspotAccumulator::toMap)
                .collect(Collectors.toList());
    }

    public static List<Map<String, Object>> eventSample(CacheSummary summary, int maxEvents) {
        if (maxEvents == 0) {
            return List.of();
        }
        return summary.getEvents().stream()
                .limit(maxEvents < 0 ? Long.MAX_VALUE : maxEvents)
                .map(CacheInsights::eventToMap)
                .collect(Collectors.toList());
    }

    public static List<Map<String, Object>> allEvents(CacheSummary summary) {
        return summary.getEvents().stream()
                .map(CacheInsights::eventToMap)
                .collect(Collectors.toList());
    }

    private static Map<String, Object> eventToMap(CacheEvent event) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("id", event.id());
        entry.put("line", event.line());
        entry.put("type", event.type().name());
        entry.put("label", event.label());
        InstrumentationPoint point = event.source();
        if (point != null) {
            entry.put("expression", point.getAccess().getExpression());
            entry.put("access_type", point.getAccess().getAccessType().name());
        }
        return entry;
    }

    private static final class HotspotAccumulator {
        private final int id;
        private final Integer line;
        private final String expression;
        private final String accessType;
        private final String label;
        private int hits;
        private int misses;
        private int evictions;

        private HotspotAccumulator(InstrumentationPoint point, CacheEvent event) {
            this.id = event.id();
            InstrumentationPoint source = point != null ? point : event.source();
            if (source != null) {
                this.line = source.getAccess().getLine();
                this.expression = source.getAccess().getExpression();
            } else {
                this.line = event.line();
                this.expression = null;
            }
            this.accessType = source != null ? source.getAccess().getAccessType().name() : null;
            this.label = event.label();
        }

        private int score() {
            return misses + evictions;
        }

        private Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("id", id);
            if (line != null) {
                map.put("line", line);
            }
            if (expression != null) {
                map.put("expression", expression);
            }
            if (accessType != null) {
                map.put("access_type", accessType);
            }
            map.put("hits", hits);
            map.put("misses", misses);
            map.put("evictions", evictions);
            map.put("score", score());
            map.put("label", label);
            return map;
        }
    }
}

