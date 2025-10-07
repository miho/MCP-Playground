package com.embeddedcc.ui;

import com.embeddedcc.analysis.CacheSummary;
import com.embeddedcc.compiler.RunResult;

final class BlockSweepRow {

    private final int blockSize;
    private final int hits;
    private final int misses;
    private final int evictions;
    private final String status;
    private final boolean compileSuccess;
    private final boolean executionSuccess;
    private boolean best;

    private BlockSweepRow(int blockSize,
                          int hits,
                          int misses,
                          int evictions,
                          String status,
                          boolean compileSuccess,
                          boolean executionSuccess) {
        this.blockSize = blockSize;
        this.hits = hits;
        this.misses = misses;
        this.evictions = evictions;
        this.status = status;
        this.compileSuccess = compileSuccess;
        this.executionSuccess = executionSuccess;
    }

    static BlockSweepRow from(int blockSize, RunResult result, CacheSummary summary) {
        boolean compiled = result.isCompiled();
        boolean executed = compiled && result.getExecutionExitCode() == 0;
        int hits = compiled ? summary.getHits() : 0;
        int misses = compiled ? summary.getMisses() : 0;
        int evictions = compiled ? summary.getEvictions() : 0;

        String status;
        if (!compiled) {
            status = "Compile failed (" + result.getCompileExitCode() + ")";
        } else if (!executed) {
            status = "Exit code " + result.getExecutionExitCode();
        } else {
            status = "OK";
        }

        return new BlockSweepRow(blockSize, hits, misses, evictions, status, compiled, executed);
    }

    int getBlockSize() {
        return blockSize;
    }

    int getHits() {
        return hits;
    }

    int getMisses() {
        return misses;
    }

    int getEvictions() {
        return evictions;
    }

    String getStatus() {
        return status;
    }

    boolean isSuccessful() {
        return compileSuccess && executionSuccess;
    }

    boolean isBest() {
        return best;
    }

    void setBest(boolean best) {
        this.best = best;
    }
}

