package com.embeddedcc.compiler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

final class ProcessStreams {

    private final String stdout;
    private final String stderr;

    private ProcessStreams(String stdout, String stderr) {
        this.stdout = stdout;
        this.stderr = stderr;
    }

    public String stdout() {
        return stdout;
    }

    public String stderr() {
        return stderr;
    }

    static ProcessStreams collect(Process process) throws IOException {
        CompletableFuture<String> outFuture = readAsync(process.getInputStream());
        CompletableFuture<String> errFuture = readAsync(process.getErrorStream());

        try {
            return new ProcessStreams(outFuture.get(), errFuture.get());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while collecting process output", e);
        } catch (ExecutionException e) {
            throw new IOException("Failed to collect process output", e);
        }
    }

    private static CompletableFuture<String> readAsync(java.io.InputStream stream) {
        return CompletableFuture.supplyAsync(() -> {
            StringBuilder builder = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line).append(System.lineSeparator());
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return builder.toString();
        });
    }
}

