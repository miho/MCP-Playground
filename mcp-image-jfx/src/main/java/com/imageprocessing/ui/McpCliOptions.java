package com.imageprocessing.ui;

import com.imageprocessing.server.McpConfig;
import picocli.CommandLine;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Command-line options for configuring the embedded MCP server.
 * Uses picocli for parsing and validation.
 */
@CommandLine.Command(
    name = "image-processing-ui",
    description = "Image Processing Pipeline Builder with embedded MCP server",
    mixinStandardHelpOptions = true,
    version = "1.0.0"
)
public class McpCliOptions {

    @CommandLine.Option(
        names = {"--mcp-enabled"},
        description = "Enable embedded MCP server (default: ${DEFAULT-VALUE})",
        defaultValue = "true"
    )
    private boolean mcpEnabled;

    @CommandLine.Option(
        names = {"--mcp-mode"},
        description = "MCP transport mode: stdio or http (default: ${DEFAULT-VALUE})",
        defaultValue = "http"
    )
    private String mcpMode;

    @CommandLine.Option(
        names = {"--mcp-port"},
        description = "HTTP port for MCP server (default: ${DEFAULT-VALUE})",
        defaultValue = "8082"
    )
    private int mcpPort;

    @CommandLine.Option(
        names = {"--mcp-host"},
        description = "HTTP host for MCP server (default: ${DEFAULT-VALUE})",
        defaultValue = "localhost"
    )
    private String mcpHost;

    @CommandLine.Option(
        names = {"--mcp-endpoint"},
        description = "HTTP endpoint path for MCP server (default: ${DEFAULT-VALUE})",
        defaultValue = "/mcp"
    )
    private String mcpEndpoint;

    @CommandLine.Option(
        names = {"--mcp-log-enabled"},
        description = "Enable MCP server logging (default: ${DEFAULT-VALUE})",
        defaultValue = "true"
    )
    private boolean mcpLogEnabled;

    @CommandLine.Option(
        names = {"--mcp-log-dir"},
        description = "Directory for MCP server logs (default: ${DEFAULT-VALUE})",
        defaultValue = "./logs"
    )
    private String mcpLogDir;

    /**
     * Parse command-line arguments and return CLI options.
     * Returns null if help/version was requested or parsing failed.
     */
    public static McpCliOptions parse(String[] args) {
        McpCliOptions options = new McpCliOptions();
        CommandLine cmd = new CommandLine(options);

        try {
            cmd.parseArgs(args);

            // If help or version was requested, print it and return null
            if (cmd.isUsageHelpRequested()) {
                cmd.usage(System.out);
                return null;
            }

            if (cmd.isVersionHelpRequested()) {
                cmd.printVersionHelp(System.out);
                return null;
            }

            return options;
        } catch (CommandLine.ParameterException e) {
            System.err.println("Error: " + e.getMessage());
            cmd.usage(System.err);
            return null;
        }
    }

    /**
     * Build McpConfig from parsed CLI options.
     */
    public McpConfig buildMcpConfig() {
        McpConfig.Builder builder = McpConfig.builder();

        // Set transport mode
        McpConfig.TransportMode transportMode;
        if ("stdio".equalsIgnoreCase(mcpMode)) {
            transportMode = McpConfig.TransportMode.STDIO;
        } else if ("http".equalsIgnoreCase(mcpMode)) {
            transportMode = McpConfig.TransportMode.HTTP;
        } else {
            throw new IllegalArgumentException("Invalid MCP mode: " + mcpMode + ". Must be 'stdio' or 'http'");
        }

        builder.transportMode(transportMode);

        // Set HTTP configuration (used only in HTTP mode)
        builder.httpHost(mcpHost);
        builder.httpPort(mcpPort);
        builder.httpEndpoint(mcpEndpoint);

        // Set logging configuration
        builder.captureServerLogs(mcpLogEnabled);
        builder.logDirectory(Paths.get(mcpLogDir));

        return builder.build();
    }

    // Getters

    public boolean isMcpEnabled() {
        return mcpEnabled;
    }

    public String getMcpMode() {
        return mcpMode;
    }

    public int getMcpPort() {
        return mcpPort;
    }

    public String getMcpHost() {
        return mcpHost;
    }

    public String getMcpEndpoint() {
        return mcpEndpoint;
    }

    public boolean isMcpLogEnabled() {
        return mcpLogEnabled;
    }

    public String getMcpLogDir() {
        return mcpLogDir;
    }

    @Override
    public String toString() {
        return String.format("McpCliOptions{enabled=%s, mode=%s, host=%s, port=%d, endpoint=%s}",
            mcpEnabled, mcpMode, mcpHost, mcpPort, mcpEndpoint);
    }
}
