package com.imageprocessing.ui;

import com.imageprocessing.server.McpConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for McpCliOptions to verify CLI argument parsing and config building.
 */
class McpCliOptionsTest {

    @Test
    void testDefaultConfiguration() {
        // Test with no arguments
        McpCliOptions options = McpCliOptions.parse(new String[]{});
        assertNotNull(options, "Options should not be null with no arguments");

        assertTrue(options.isMcpEnabled(), "MCP should be enabled by default");
        assertEquals("http", options.getMcpMode(), "Default mode should be http");
        assertEquals(8082, options.getMcpPort(), "Default port should be 8082");
        assertEquals("localhost", options.getMcpHost(), "Default host should be localhost");
        assertEquals("/mcp", options.getMcpEndpoint(), "Default endpoint should be /mcp");

        McpConfig config = options.buildMcpConfig();
        assertNotNull(config, "Config should not be null");
        assertEquals(McpConfig.TransportMode.HTTP, config.getTransportMode(), "Default transport should be HTTP");
        assertEquals(8082, config.getHttpPort(), "Config port should match");
    }

    @Test
    void testStdioMode() {
        McpCliOptions options = McpCliOptions.parse(new String[]{"--mcp-mode=stdio"});
        assertNotNull(options, "Options should not be null");

        assertEquals("stdio", options.getMcpMode(), "Mode should be stdio");

        McpConfig config = options.buildMcpConfig();
        assertEquals(McpConfig.TransportMode.STDIO, config.getTransportMode(), "Transport should be STDIO");
    }

    @Test
    void testHttpMode() {
        McpCliOptions options = McpCliOptions.parse(new String[]{"--mcp-mode=http"});
        assertNotNull(options, "Options should not be null");

        assertEquals("http", options.getMcpMode(), "Mode should be http");

        McpConfig config = options.buildMcpConfig();
        assertEquals(McpConfig.TransportMode.HTTP, config.getTransportMode(), "Transport should be HTTP");
    }

    @Test
    void testCustomPort() {
        McpCliOptions options = McpCliOptions.parse(new String[]{"--mcp-port=9000"});
        assertNotNull(options, "Options should not be null");

        assertEquals(9000, options.getMcpPort(), "Port should be 9000");

        McpConfig config = options.buildMcpConfig();
        assertEquals(9000, config.getHttpPort(), "Config port should be 9000");
    }

    @Test
    void testCustomHost() {
        McpCliOptions options = McpCliOptions.parse(new String[]{"--mcp-host=0.0.0.0"});
        assertNotNull(options, "Options should not be null");

        assertEquals("0.0.0.0", options.getMcpHost(), "Host should be 0.0.0.0");

        McpConfig config = options.buildMcpConfig();
        assertEquals("0.0.0.0", config.getHttpHost(), "Config host should be 0.0.0.0");
    }

    @Test
    void testDisabledServer() {
        McpCliOptions options = McpCliOptions.parse(new String[]{"--mcp-enabled=false"});
        assertNotNull(options, "Options should not be null");

        assertFalse(options.isMcpEnabled(), "MCP should be disabled");
    }

    @Test
    void testMultipleOptions() {
        String[] args = {
            "--mcp-mode=http",
            "--mcp-port=8090",
            "--mcp-host=0.0.0.0",
            "--mcp-endpoint=/api/mcp",
            "--mcp-log-dir=/var/log/mcp"
        };

        McpCliOptions options = McpCliOptions.parse(args);
        assertNotNull(options, "Options should not be null");

        assertEquals("http", options.getMcpMode(), "Mode should be http");
        assertEquals(8090, options.getMcpPort(), "Port should be 8090");
        assertEquals("0.0.0.0", options.getMcpHost(), "Host should be 0.0.0.0");
        assertEquals("/api/mcp", options.getMcpEndpoint(), "Endpoint should be /api/mcp");
        assertEquals("/var/log/mcp", options.getMcpLogDir(), "Log dir should be /var/log/mcp");

        McpConfig config = options.buildMcpConfig();
        assertEquals(McpConfig.TransportMode.HTTP, config.getTransportMode(), "Transport should be HTTP");
        assertEquals(8090, config.getHttpPort(), "Config port should be 8090");
        assertEquals("0.0.0.0", config.getHttpHost(), "Config host should be 0.0.0.0");
        assertEquals("/api/mcp", config.getHttpEndpoint(), "Config endpoint should be /api/mcp");
    }

    @Test
    void testInvalidMode() {
        McpCliOptions options = McpCliOptions.parse(new String[]{"--mcp-mode=invalid"});
        assertNotNull(options, "Options should not be null even with invalid mode");

        // The invalid mode should throw an exception when building config
        assertThrows(IllegalArgumentException.class, () -> {
            options.buildMcpConfig();
        }, "Should throw exception for invalid mode");
    }

    @Test
    void testHelpRequest() {
        // Help request should return null
        McpCliOptions options = McpCliOptions.parse(new String[]{"--help"});
        assertNull(options, "Options should be null when help is requested");
    }

    @Test
    void testVersionRequest() {
        // Version request should return null
        McpCliOptions options = McpCliOptions.parse(new String[]{"--version"});
        assertNull(options, "Options should be null when version is requested");
    }

    @Test
    void testToString() {
        McpCliOptions options = McpCliOptions.parse(new String[]{"--mcp-mode=http", "--mcp-port=8090"});
        assertNotNull(options, "Options should not be null");

        String str = options.toString();
        assertTrue(str.contains("http"), "toString should contain mode");
        assertTrue(str.contains("8090"), "toString should contain port");
    }
}
