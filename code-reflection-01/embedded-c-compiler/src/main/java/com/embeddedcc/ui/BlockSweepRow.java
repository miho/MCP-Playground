package com.embeddedcc.ui;

import com.embeddedcc.analysis.CacheSummary;
import com.embeddedcc.analysis.RunResultPersister;
import com.embeddedcc.compiler.RunResult;

final class BlockSweepRow {

    private final int blockSize;
    private final int hits;
    private final int misses;
    private final int evictions;
    private final String status;
    private final boolean compileSuccess;
    private final boolean executionSuccess;
    private final String runId;
    private final String resultPath;
    private boolean best;

    private BlockSweepRow(int blockSize,
                          int hits,
                          int misses,
                          int evictions,
                          String status,
                          boolean compileSuccess,
                          boolean executionSuccess,
                          String runId,
                          String resultPath) {
        this.blockSize = blockSize;
        this.hits = hits;
        this.misses = misses;
        this.evictions = evictions;
        this.status = status;
        this.compileSuccess = compileSuccess;
        this.executionSuccess = executionSuccess;
        this.runId = runId;
        this.resultPath = resultPath;
    }

    static BlockSweepRow from(int blockSize, RunResult result, CacheSummary summary, RunResultPersister.RunRecord record) {
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

        String runId = record != null ? record.runId() : null;
        String resultPath = record != null ? record.path().toAbsolutePath().toString() : null;
        return new BlockSweepRow(blockSize, hits, misses, evictions, status, compiled, executed, runId, resultPath);
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

    String getRunId() {
        return runId;
    }

    String getResultPath() {
        return resultPath;
    }
}
