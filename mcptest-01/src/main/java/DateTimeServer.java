import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.server.McpAsyncServer;  // Not used but included for completeness
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.json.McpJsonMapper;
import reactor.core.publisher.Mono;                    // necessary for async handling
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DateTimeServer {

    public static void main(String[] args) {
        try {
            // Parse command-line arguments
            boolean useHttp = false;
            int port = 8080;

            for (int i = 0; i < args.length; i++) {
                if ("--http".equals(args[i])) {
                    useHttp = true;
                    if (i + 1 < args.length && args[i + 1].matches("\\d+")) {
                        port = Integer.parseInt(args[i + 1]);
                        i++;
                    }
                }
            }

            if (useHttp) {
                startHttpServer(port);
            } else {
                startStdioServer();
            }

        } catch (Exception e) {
            System.err.println("Server error: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void startStdioServer() throws InterruptedException {
        // Create STDIO transport provider
        StdioServerTransportProvider transportProvider =
                new StdioServerTransportProvider(McpJsonMapper.createDefault());

        // Define the tool schema for date/time parameters
        String schema = """
            {
              "type": "object",
              "properties": {
                "format": {
                  "type": "string",
                  "description": "Optional date/time format pattern (e.g., 'yyyy-MM-dd HH:mm:ss')",
                  "default": "yyyy-MM-dd HH:mm:ss"
                }
              }
            }
            """;

        // Create the date/time tool specification (using async)
        var dateTimeTool = new McpServerFeatures.SyncToolSpecification.Builder().tool(
                McpSchema.Tool.builder()
                        .name("get_datetime")
                        .description("Returns the current date and time")
                        .inputSchema(McpJsonMapper.createDefault(), schema)
                        .build())
                .callHandler(
                (exchange, request) -> {
                    try {
                        // Get format from arguments or use default
                        String format = request.arguments().containsKey("format")
                                ? request.arguments().get("format").toString()
                                : "yyyy-MM-dd HH:mm:ss";

                        // Get the current date/time
                        LocalDateTime now = LocalDateTime.now();
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
                        String formattedDateTime = now.format(formatter);

                        // Return the result
                        return new McpSchema.CallToolResult.Builder()
                                .content(List.of(new McpSchema.TextContent(formattedDateTime)))
                                .isError(false)
                                .build();

                    } catch (Exception e) {
                        return new McpSchema.CallToolResult.Builder()
                                .content(List.of(new McpSchema.TextContent("Error: " + e.getMessage())))
                                .isError(true)
                                .build();
                    }
                }
                ).build();

        // Create and configure the MCP sync server
        McpSyncServer server = McpServer.sync(transportProvider)
                .serverInfo("datetime-server", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)  // Enable tool support
                        .build())
                .tools(dateTimeTool)  // Add tools during build
                .build();

        System.err.println("DateTime MCP Server started and ready for connections...");

        // Keep the server running until interrupted
        CompletableFuture<Void> f = new CompletableFuture<>();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.err.println("Shutting down server...");
            server.close();
            f.complete(null);
        }));

        f.join();
    }

    private static void startHttpServer(int port) throws Exception {
        // Create HTTP servlet transport
        HttpServletStatelessServerTransport transport = HttpServletStatelessServerTransport.builder()
                .jsonMapper(McpJsonMapper.createDefault())
                .messageEndpoint("/mcp")
                .build();

        // Define the tool schema
        String schema = """
            {
              "type": "object",
              "properties": {
                "format": {
                  "type": "string",
                  "description": "Optional date/time format pattern (e.g., 'yyyy-MM-dd HH:mm:ss')",
                  "default": "yyyy-MM-dd HH:mm:ss"
                }
              }
            }
            """;

        // Create the date/time tool specification (stateless sync)
        var dateTimeTool = new McpStatelessServerFeatures.SyncToolSpecification.Builder().tool(
                McpSchema.Tool.builder()
                        .name("get_datetime")
                        .description("Returns the current date and time")
                        .inputSchema(McpJsonMapper.createDefault(), schema)
                        .build())
                .callHandler(
                (transportContext, request) -> {
                    try {
                        var args = request.arguments();
                        String format = args.containsKey("format")
                                ? args.get("format").toString()
                                : "yyyy-MM-dd HH:mm:ss";

                        LocalDateTime now = LocalDateTime.now();
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
                        String formattedDateTime = now.format(formatter);

                        return new McpSchema.CallToolResult.Builder()
                                .content(List.of(new McpSchema.TextContent(formattedDateTime)))
                                .isError(false)
                                .build();

                    } catch (Exception e) {
                        return new McpSchema.CallToolResult.Builder()
                                .content(List.of(new McpSchema.TextContent("Error: " + e.getMessage())))
                                .isError(true)
                                .build();
                    }
                }
                ).build();

        // Create MCP server with HTTP transport
        McpStatelessSyncServer mcpServer = McpServer.sync(transport)
                .serverInfo("datetime-server", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .tools(dateTimeTool)
                .build();

        // Create and configure Jetty server
        Server jettyServer = new Server(port);
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        jettyServer.setHandler(context);

        // Add the MCP servlet
        ServletHolder servletHolder = new ServletHolder(transport);
        context.addServlet(servletHolder, "/mcp");

        jettyServer.start();
        System.err.println("DateTime MCP Server started on HTTP port " + port);
        System.err.println("MCP endpoint: http://localhost:" + port + "/mcp");

        // Keep running until interrupted
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
}
