package com.trafficsim.mcp;

import com.trafficsim.engine.IntersectionSimulator;
import com.trafficsim.server.IntersectionMcpServer;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpAsyncServer;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

/**
 * Simple wrapper to start MCP server in background thread.
 * This enables external MCP clients to connect to the embedded server
 * while the JavaFX UI uses DirectToolExecutor for internal operations.
 */
public class ServerLauncher {

    private static final Logger logger = LoggerFactory.getLogger(ServerLauncher.class);

    private final McpConfig config;
    private final IntersectionSimulator simulator;
    private java.util.Map<String, Double> currentArrivals;

    private McpAsyncServer asyncServer;
    private McpStatelessSyncServer syncServer;
    private Server jettyServer;
    private Thread serverThread;
    private CountDownLatch stdioLatch;
    private volatile boolean running = false;

    public ServerLauncher(McpConfig config, IntersectionSimulator simulator) {
        this.config = config;
        this.simulator = simulator;
        // Initialize with default arrivals
        this.currentArrivals = new java.util.HashMap<>();
        this.currentArrivals.put("N", 10.0);
        this.currentArrivals.put("S", 10.0);
        this.currentArrivals.put("E", 10.0);
        this.currentArrivals.put("W", 10.0);
    }

    /**
     * Start MCP server in background thread.
     * Returns CompletableFuture that completes when server is ready.
     */
    public CompletableFuture<Boolean> startAsync() {
        if (running) {
            logger.warn("Server is already running");
            return CompletableFuture.completedFuture(true);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                if (config.getTransportMode() == McpConfig.TransportMode.HTTP) {
                    startHttpServer();
                } else {
                    startStdioServer();
                }
                running = true;
                logger.info("MCP Server started successfully in {} mode", config.getTransportMode());
                return true;
            } catch (Exception e) {
                logger.error("Failed to start MCP server", e);
                running = false;
                return false;
            }
        });
    }

    /**
     * Start HTTP server in current thread (called from background thread).
     */
    private void startHttpServer() throws Exception {
        logger.info("Starting HTTP server on port {}", config.getHttpPort());

        HttpServletStatelessServerTransport transport = HttpServletStatelessServerTransport.builder()
                .jsonMapper(McpJsonMapper.createDefault())
                .messageEndpoint(config.getHttpEndpoint())
                .build();

        // Create stateless sync tools using the simulator reference
        var tools = IntersectionMcpServer.ToolFactory.createAllStatelessTools(simulator);

        syncServer = McpServer.sync(transport)
                .serverInfo("intersection-optimizer-server", getVersion())
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .tools(tools.toArray(new io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification[0]))
                .build();

        jettyServer = new Server(config.getHttpPort());
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        jettyServer.setHandler(context);

        ServletHolder servletHolder = new ServletHolder(transport);
        context.addServlet(servletHolder, config.getHttpEndpoint());

        jettyServer.start();
        logger.info("HTTP server started: {}", getEndpointUrl());
    }

    /**
     * Start stdio server in background thread.
     */
    private void startStdioServer() throws InterruptedException {
        logger.info("Starting stdio server with shared simulator");

        // Create async server with shared simulator using current arrival rates
        asyncServer = IntersectionMcpServer.createStdioServer(simulator, currentArrivals, 12345);

        stdioLatch = new CountDownLatch(1);
        serverThread = new Thread(() -> {
            try {
                logger.info("Stdio server thread running...");
                // Keep the thread alive until shutdown
                stdioLatch.await();
            } catch (InterruptedException e) {
                logger.info("Stdio server thread interrupted");
            } catch (Exception e) {
                logger.error("Server thread error", e);
                running = false;
            }
        }, "MCP-Server-Thread");

        serverThread.setDaemon(true);
        serverThread.start();

        // Give the server a moment to start
        Thread.sleep(500);
        logger.info("Stdio server started with shared simulator");
    }

    /**
     * Shutdown the server cleanly.
     */
    public void shutdown() {
        if (!running) {
            return;
        }

        logger.info("Shutting down MCP server...");
        running = false;

        try {
            if (asyncServer != null) {
                asyncServer.close();
                asyncServer = null;
            }

            if (syncServer != null) {
                syncServer.close();
                syncServer = null;
            }

            if (jettyServer != null) {
                jettyServer.stop();
                jettyServer = null;
            }

            if (stdioLatch != null) {
                stdioLatch.countDown();
            }

            if (serverThread != null && serverThread.isAlive()) {
                serverThread.interrupt();
                serverThread.join(2000);
            }

            logger.info("MCP server shutdown complete");
        } catch (Exception e) {
            logger.error("Error during server shutdown", e);
        }
    }

    /**
     * Check if server is currently running.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Get the endpoint URL or description.
     */
    public String getEndpointUrl() {
        if (config.getTransportMode() == McpConfig.TransportMode.HTTP) {
            return config.getHttpUrl();
        } else {
            return "stdio (standard input/output)";
        }
    }

    /**
     * Get server version.
     */
    private String getVersion() {
        return "1.0.0";
    }

    /**
     * Update arrival rates in the MCP server.
     * This keeps the server in sync with UI slider changes.
     */
    public void updateArrivals(java.util.Map<String, Double> arrivals) {
        // Update local copy
        this.currentArrivals.putAll(arrivals);
        // Update the MCP server's copy
        IntersectionMcpServer.updateCurrentArrivals(arrivals);
    }
}
