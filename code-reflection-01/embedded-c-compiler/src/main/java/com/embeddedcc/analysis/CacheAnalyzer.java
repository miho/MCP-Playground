package com.embeddedcc.analysis;

import com.embeddedcc.cache.CacheSim;
import com.embeddedcc.instrumentation.InstrumentationPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CacheAnalyzer {

    private final CacheSim simulator = new CacheSim();

    public CacheSummary analyze(String trace,
                                CacheConfiguration configuration,
                                Map<Integer, InstrumentationPoint> instrumentationPoints) {
        if (trace == null || trace.isBlank()) {
            return CacheSummary.empty();
        }

        CacheSim.SimulationResult result =
                simulator.execute(configuration.setBits(), configuration.linesPerSet(),
                        configuration.blockBits(), trace);

        List<CacheEvent> events = new ArrayList<>();
        result.getTrace().forEach(entry -> {
            CacheEventType type = switch (entry.getType()) {
                case HIT -> CacheEventType.HIT;
                case MISS -> CacheEventType.MISS;
                case EVICTION -> CacheEventType.EVICTION;
            };
            InstrumentationPoint point = instrumentationPoints.get(entry.getId());
            events.add(new CacheEvent(type, entry.getId(), entry.getFrom(), entry.getLabel(), point));
        });

        return CacheSummary.of(result.getHits().size(),
                result.getMisses().size(),
                result.getEvictions().size(),
                events);
    }
}
