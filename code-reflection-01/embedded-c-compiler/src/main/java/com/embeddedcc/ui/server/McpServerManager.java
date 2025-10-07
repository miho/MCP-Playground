package com.embeddedcc.ui.server;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages a child process running the EmbeddedC MCP server.
 */
public class McpServerManager {

    private final ReadOnlyBooleanWrapper running = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyStringWrapper statusMessage = new ReadOnlyStringWrapper("Server stopped");

    private Process process;
    private ServerConfig activeConfig = ServerConfig.defaultConfig();

    public ReadOnlyBooleanProperty runningProperty() {
        return running.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty statusMessageProperty() {
        return statusMessage.getReadOnlyProperty();
    }

    public ServerConfig getActiveConfig() {
        return activeConfig;
    }

    public boolean isRunning() {
        return running.get();
    }

    public void start(ServerConfig config) throws IOException {
        if (isRunning()) {
            throw new IllegalStateException("Server already running");
        }
        this.activeConfig = config;

        List<String> command = buildCommand(config);
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);

        process = builder.start();
        running.set(true);
        statusMessage.set("Server starting...");

        Thread logReader = new Thread(() -> consumeOutput(process));
        logReader.setDaemon(true);
        logReader.start();

        Thread waiter = new Thread(() -> awaitTermination(process));
        waiter.setDaemon(true);
        waiter.start();
    }

    public void stop() {
        if (!isRunning()) {
            return;
        }
        process.destroy();
        statusMessage.set("Stopping server...");
    }

    private List<String> buildCommand(ServerConfig config) {
        List<String> command = new ArrayList<>();
        String javaExec = resolveJavaCommand();
        command.add(javaExec);
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add("com.embeddedcc.mcp.EmbeddedCMcpServer");
        if (config.mode() == ServerMode.HTTP) {
            command.add("--http");
            command.add(Integer.toString(config.port()));
        }
        return command;
    }

    private String resolveJavaCommand() {
        String javaHome = System.getProperty("java.home");
        Path javaPath = Path.of(javaHome, "bin", isWindows() ? "java.exe" : "java");
        return javaPath.toFile().exists() ? javaPath.toString() : "java";
    }

    private void consumeOutput(Process process) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                final String message = line;
                Platform.runLater(() -> statusMessage.set(message));
            }
        } catch (IOException ignored) {
        }
    }

    private void awaitTermination(Process process) {
        try {
            int exit = process.waitFor();
            Platform.runLater(() -> {
                running.set(false);
                statusMessage.set("Server stopped (exit " + exit + ")");
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Platform.runLater(() -> {
                running.set(false);
                statusMessage.set("Server interrupted");
            });
        }
    }

    private boolean isWindows() {
        String os = System.getProperty("os.name");
        return os != null && os.toLowerCase().contains("win");
    }
}

