package com.embeddedcc.analysis;

public record CacheConfiguration(int setBits, int linesPerSet, int blockBits) {
    public static CacheConfiguration defaultConfig() {
        return new CacheConfiguration(5, 1, 5);
    }
}

