package com.openrewrite.server;

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

import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

/**
 * Manages the lifecycle of an embedded MCP server.
 * Supports both stdio and HTTP transport modes.
 */
public class ServerLauncher {
    private static final Logger logger = LoggerFactory.getLogger(ServerLauncher.class);

    private final McpConfig config;
    private McpAsyncServer asyncServer;
    private McpStatelessSyncServer syncServer;
    private Server jettyServer;
    private Thread serverThread;
    private volatile boolean running = false;
    private volatile int actualPort = -1;

    public ServerLauncher(McpConfig config) {
        this.config = config;
    }

    /**
     * Start the server asynchronously.
     * @return CompletableFuture that completes when server is ready
     */
    public CompletableFuture<Boolean> startAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                start();
                return true;
            } catch (Exception e) {
                logger.error("Failed to start MCP server", e);
                return false;
            }
        });
    }

    /**
     * Start the server based on configuration.
     */
    public void start() throws Exception {
        if (running) {
            logger.warn("Server is already running");
            return;
        }

        logger.info("Starting MCP server with config: {}", config);

        if (config.getTransportMode() == McpConfig.TransportMode.STDIO) {
            startStdioServer();
        } else {
            startHttpServer();
        }

        running = true;
        logger.info("MCP server started successfully");
    }

    /**
     * Start server in stdio mode (for Claude Code).
     */
    private void startStdioServer() throws Exception {
        StdioServerTransportProvider transportProvider =
                new StdioServerTransportProvider(McpJsonMapper.createDefault());

        // Create all tools using ToolFactory
        var listRecipesTool = ToolFactory.createListRecipesTool();
        var getRecipeDescriptionTool = ToolFactory.createGetRecipeDescriptionTool();
        var applyRecipeTool = ToolFactory.createApplyRecipeTool();
        var analyzeCodeTool = ToolFactory.createAnalyzeCodeTool();
        var createCustomRecipeTool = ToolFactory.createCustomRecipeTool();

        asyncServer = McpServer.async(transportProvider)
                .serverInfo("openrewrite-mcp-server", getVersion())
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .tools(listRecipesTool, getRecipeDescriptionTool, applyRecipeTool,
                       analyzeCodeTool, createCustomRecipeTool)
                .build();

        logger.info("OpenRewrite MCP Server started (stdio mode)");
        logger.info("Version: {}", getVersion());
        logger.info("Ready for connections...");

        // Start server in a separate thread
        serverThread = new Thread(() -> {
            try {
                CountDownLatch latch = new CountDownLatch(1);
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    logger.info("Shutting down stdio server...");
                    if (asyncServer != null) {
                        asyncServer.close();
                    }
                    latch.countDown();
                }));
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Server thread interrupted");
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();
    }

    /**
     * Start server in HTTP mode (for LM-Studio and other clients).
     */
    private void startHttpServer() throws Exception {
        HttpServletStatelessServerTransport transport = HttpServletStatelessServerTransport.builder()
                .jsonMapper(McpJsonMapper.createDefault())
                .messageEndpoint(config.getHttpEndpoint())
                .build();

        // Create stateless versions of all tools
        var listRecipesTool = ToolFactory.createStatelessListRecipesTool();
        var getRecipeDescriptionTool = ToolFactory.createStatelessGetRecipeDescriptionTool();
        var applyRecipeTool = ToolFactory.createStatelessApplyRecipeTool();
        var analyzeCodeTool = ToolFactory.createStatelessAnalyzeCodeTool();
        var createCustomRecipeTool = ToolFactory.createStatelessCustomRecipeTool();

        syncServer = McpServer.sync(transport)
                .serverInfo("openrewrite-mcp-server", getVersion())
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .tools(listRecipesTool, getRecipeDescriptionTool, applyRecipeTool,
                       analyzeCodeTool, createCustomRecipeTool)
                .build();

        // Try to bind to port with retries if it's in use
        int maxRetries = 3;
        int currentPort = config.getHttpPort();
        Exception lastException = null;

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                jettyServer = new Server(currentPort);
                ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
                context.setContextPath("/");
                jettyServer.setHandler(context);

                ServletHolder servletHolder = new ServletHolder(transport);
                context.addServlet(servletHolder, config.getHttpEndpoint());

                // Try to start the server
                jettyServer.start();
                actualPort = currentPort;

                logger.info("OpenRewrite MCP Server started on HTTP port {}", currentPort);
                logger.info("Version: {}", getVersion());
                logger.info("MCP endpoint: http://{}:{}{}",
                    config.getHttpHost(), currentPort, config.getHttpEndpoint());

                // Success - start the server thread
                final int finalPort = currentPort;
                serverThread = new Thread(() -> {
                    try {
                        jettyServer.join();
                    } catch (Exception e) {
                        logger.error("HTTP server error", e);
                    }
                });
                serverThread.setDaemon(true);
                serverThread.start();

                // Update config with actual port if different
                if (currentPort != config.getHttpPort()) {
                    logger.info("Note: Server started on port {} instead of requested port {}",
                        currentPort, config.getHttpPort());
                }

                // Wait for server to be fully ready
                Thread.sleep(2000);
                return;

            } catch (Exception e) {
                lastException = e;
                if (jettyServer != null) {
                    try {
                        jettyServer.stop();
                        jettyServer.destroy();
                    } catch (Exception stopEx) {
                        // Ignore
                    }
                    jettyServer = null;
                }

                // If port is in use, try next port
                if (e.getMessage() != null && e.getMessage().contains("Address already in use")) {
                    currentPort++;
                    logger.warn("Port {} is in use, trying port {}", currentPort - 1, currentPort);
                    Thread.sleep(500);
                } else {
                    throw e;
                }
            }
        }

        // If we get here, all retries failed
        throw new Exception("Failed to start HTTP server after " + maxRetries + " attempts", lastException);
    }

    /**
     * Stop the server.
     */
    public void shutdown() {
        if (!running) {
            return;
        }

        logger.info("Shutting down MCP server...");

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

            if (serverThread != null && serverThread.isAlive()) {
                serverThread.interrupt();
                serverThread = null;
            }

            running = false;
            logger.info("MCP server shut down successfully");
        } catch (Exception e) {
            logger.error("Error during shutdown", e);
        }
    }

    /**
     * Check if server is running.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Get the server endpoint URL (for HTTP mode).
     */
    public String getEndpointUrl() {
        if (config.getTransportMode() == McpConfig.TransportMode.HTTP) {
            // Use actual port if available, otherwise use configured port
            int port = actualPort > 0 ? actualPort : config.getHttpPort();
            return String.format("http://%s:%d%s",
                config.getHttpHost(), port, config.getHttpEndpoint());
        }
        return "stdio";
    }

    /**
     * Get server version.
     */
    private static String getVersion() {
        try (InputStream input = ServerLauncher.class.getClassLoader()
                .getResourceAsStream("build-info.properties")) {
            if (input != null) {
                Properties props = new Properties();
                props.load(input);
                return props.getProperty("version", "1.0.0");
            }
        } catch (Exception e) {
            // Ignore
        }
        return "1.0.0";
    }
}
