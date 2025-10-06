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
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

@Command(
    name = "openrewrite-mcp-server",
    mixinStandardHelpOptions = true,
    version = "1.0.0",
    description = "MCP server for OpenRewrite code transformations"
)
public class OpenRewriteMcpServer implements Callable<Integer> {
    private static final Logger logger = LoggerFactory.getLogger(OpenRewriteMcpServer.class);

    @Option(names = {"-t", "--transport"},
        description = "Transport type: stdio or http (default: ${DEFAULT-VALUE})",
        defaultValue = "stdio")
    private String transport;

    @Option(names = {"-p", "--port"},
        description = "HTTP port (default: ${DEFAULT-VALUE})",
        defaultValue = "3001")
    private int port;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new OpenRewriteMcpServer()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        logger.info("Starting OpenRewrite MCP Server...");

        if ("stdio".equalsIgnoreCase(transport)) {
            startStdioServer();
        } else {
            startHttpServer(port);
        }

        return 0;
    }

    private void startStdioServer() throws InterruptedException {
        StdioServerTransportProvider transportProvider =
                new StdioServerTransportProvider(McpJsonMapper.createDefault());

        // Create all tools using ToolFactory
        var listRecipesTool = ToolFactory.createListRecipesTool();
        var getRecipeDescriptionTool = ToolFactory.createGetRecipeDescriptionTool();
        var applyRecipeTool = ToolFactory.createApplyRecipeTool();
        var analyzeCodeTool = ToolFactory.createAnalyzeCodeTool();
        var createCustomRecipeTool = ToolFactory.createCustomRecipeTool();

        McpAsyncServer server = McpServer.async(transportProvider)
                .serverInfo("openrewrite-mcp-server", getVersion())
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .tools(listRecipesTool, getRecipeDescriptionTool, applyRecipeTool,
                       analyzeCodeTool, createCustomRecipeTool)
                .build();

        System.err.println("OpenRewrite MCP Server started (stdio mode)");
        System.err.println("Version: " + getVersion());
        System.err.println("Ready for connections...");

        CountDownLatch latch = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.err.println("Shutting down server...");
            server.close();
            latch.countDown();
        }));

        latch.await();
    }

    private void startHttpServer(int port) throws Exception {
        HttpServletStatelessServerTransport transport = HttpServletStatelessServerTransport.builder()
                .jsonMapper(McpJsonMapper.createDefault())
                .messageEndpoint("/mcp")
                .build();

        // Create stateless versions of all tools
        var listRecipesTool = ToolFactory.createStatelessListRecipesTool();
        var getRecipeDescriptionTool = ToolFactory.createStatelessGetRecipeDescriptionTool();
        var applyRecipeTool = ToolFactory.createStatelessApplyRecipeTool();
        var analyzeCodeTool = ToolFactory.createStatelessAnalyzeCodeTool();
        var createCustomRecipeTool = ToolFactory.createStatelessCustomRecipeTool();

        McpStatelessSyncServer mcpServer = McpServer.sync(transport)
                .serverInfo("openrewrite-mcp-server", getVersion())
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .tools(listRecipesTool, getRecipeDescriptionTool, applyRecipeTool,
                       analyzeCodeTool, createCustomRecipeTool)
                .build();

        Server jettyServer = new Server(port);
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        jettyServer.setHandler(context);

        ServletHolder servletHolder = new ServletHolder(transport);
        context.addServlet(servletHolder, "/mcp");

        jettyServer.start();
        System.err.println("OpenRewrite MCP Server started on HTTP port " + port);
        System.err.println("Version: " + getVersion());
        System.err.println("MCP endpoint: http://localhost:" + port + "/mcp");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.err.println("Shutting down server...");
            try {
                mcpServer.close();
                jettyServer.stop();
            } catch (Exception e) {
                System.err.println("Error during shutdown: " + e.getMessage());
            }
        }));

        jettyServer.join();
    }

    private static String getVersion() {
        try (InputStream input = OpenRewriteMcpServer.class.getClassLoader()
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
