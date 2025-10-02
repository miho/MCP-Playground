package com.imageprocessing.server;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpAsyncServer;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.json.McpJsonMapper;
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
    private final OpenCVImageProcessor processor;
    private final IntermediateResultCache cache;
    private final TextResultCache textCache;

    private McpAsyncServer asyncServer;
    private McpStatelessSyncServer syncServer;
    private Server jettyServer;
    private Thread serverThread;
    private volatile boolean running = false;

    public ServerLauncher(McpConfig config, OpenCVImageProcessor processor, IntermediateResultCache cache, TextResultCache textCache) {
        this.config = config;
        this.processor = processor;
        this.cache = cache;
        this.textCache = textCache;
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

        // Create all 10 stateless tools using the shared processor and cache
        var tools = ImageProcessingMcpServer.ToolFactory.createAllStatelessTools(cache, textCache);

        syncServer = McpServer.sync(transport)
                .serverInfo("image-processing-mcp-server", getVersion())
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
     * Start stdio server in current thread (called from background thread).
     */
    private void startStdioServer() throws InterruptedException {
        logger.info("Starting stdio server");

        StdioServerTransportProvider transportProvider =
                new StdioServerTransportProvider(McpJsonMapper.createDefault());

        // Create all 10 async tools using the shared processor and cache
        var tools = ImageProcessingMcpServer.ToolFactory.createAllAsyncTools(cache, textCache);

        asyncServer = McpServer.async(transportProvider)
                .serverInfo("image-processing-mcp-server", getVersion())
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .tools(tools.toArray(new io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification[0]))
                .build();

        logger.info("Stdio server started");

        // Keep server running
        CountDownLatch latch = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(latch::countDown));
        latch.await();
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
        try (var input = ServerLauncher.class.getClassLoader()
                .getResourceAsStream("build-info.properties")) {
            if (input != null) {
                var props = new java.util.Properties();
                props.load(input);
                return props.getProperty("version", "1.0.0");
            }
        } catch (Exception e) {
            // Ignore
        }
        return "1.0.0";
    }
}
