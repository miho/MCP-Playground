package com.embeddedcc.mcp;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.CountDownLatch;

public final class EmbeddedCMcpServer {

    private EmbeddedCMcpServer() {
    }

    public static void main(String[] args) throws InterruptedException {
        boolean useHttp = false;
        int port = 8085;

        for (int i = 0; i < args.length; i++) {
            if ("--http".equals(args[i]) && i + 1 < args.length) {
                useHttp = true;
                port = Integer.parseInt(args[++i]);
            } else if ("--http".equals(args[i])) {
                useHttp = true;
            }
        }

        if (useHttp) {
            startHttp(port);
        } else {
            startStdio();
        }
    }

    private static void startStdio() throws InterruptedException {
        StdioServerTransportProvider transportProvider =
                new StdioServerTransportProvider(McpJsonMapper.createDefault());

        var analyzeTool = ToolFactory.createAnalyzeTool();
        var compileTool = ToolFactory.createCompileTool();
        var sweepTool = ToolFactory.createBlockSweepTool();
        var getRunResultTool = ToolFactory.createGetRunResultTool();

        var server = McpServer.async(transportProvider)
                .serverInfo("embedded-c-compiler", "0.1.0")
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .tools(analyzeTool, compileTool, sweepTool, getRunResultTool)
                .build();

        CountDownLatch latch = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.close();
            latch.countDown();
        }));
        latch.await();
    }

    private static void startHttp(int port) {
        HttpServletStatelessServerTransport transport = HttpServletStatelessServerTransport.builder()
                .jsonMapper(McpJsonMapper.createDefault())
                .messageEndpoint("/mcp")
                .build();

        McpStatelessServerFeatures.SyncToolSpecification analyzeTool =
                ToolFactory.toSync(ToolFactory.createAnalyzeTool());

        McpStatelessServerFeatures.SyncToolSpecification compileTool =
                ToolFactory.toSync(ToolFactory.createCompileTool());

        McpStatelessServerFeatures.SyncToolSpecification sweepTool =
                ToolFactory.toSync(ToolFactory.createBlockSweepTool());

        McpStatelessServerFeatures.SyncToolSpecification getRunTool =
                ToolFactory.toSync(ToolFactory.createGetRunResultTool());

        var server = McpServer.sync(transport)
                .serverInfo("embedded-c-compiler", "0.1.0")
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .tools(analyzeTool, compileTool, sweepTool, getRunTool)
                .build();

        var jetty = new org.eclipse.jetty.server.Server(port);
        var context = new org.eclipse.jetty.servlet.ServletContextHandler(org.eclipse.jetty.servlet.ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        jetty.setHandler(context);

        context.addServlet(new org.eclipse.jetty.servlet.ServletHolder(transport), "/mcp");

        try {
            jetty.start();
            System.err.printf("Embedded C MCP server running on http://localhost:%d/mcp%n", port);
            jetty.join();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            try {
                server.close();
                jetty.stop();
            } catch (Exception ignored) {
            }
        }
    }
}
