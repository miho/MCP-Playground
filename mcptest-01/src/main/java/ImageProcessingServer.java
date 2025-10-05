import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.server.McpAsyncServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.json.McpJsonMapper;
import reactor.core.publisher.Mono;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

public class ImageProcessingServer {

    public static void main(String[] args) {
        try {
            // Parse command-line arguments
            boolean useHttp = false;
            int port = 8081;

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
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void startStdioServer() throws InterruptedException {
        StdioServerTransportProvider transportProvider =
                new StdioServerTransportProvider(McpJsonMapper.createDefault());

        // Create image processing tools
        var imageInfoTool = createImageInfoTool();
        var imageResizeTool = createImageResizeTool();
        var imageGrayscaleTool = createImageGrayscaleTool();
        var versionTool = createVersionTool();
        var displayImageTool = createDisplayImageTool();

        McpAsyncServer server = McpServer.async(transportProvider)
                .serverInfo("image-processing-server", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .tools(imageInfoTool, imageResizeTool, imageGrayscaleTool, versionTool, displayImageTool)
                .build();

        System.err.println("Image Processing MCP Server started...");

        CountDownLatch latch = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.err.println("Shutting down server...");
            server.close();
            latch.countDown();
        }));

        latch.await();
    }

    private static void startHttpServer(int port) throws Exception {
        // Create HTTP servlet transport
        HttpServletStatelessServerTransport transport = HttpServletStatelessServerTransport.builder()
                .jsonMapper(McpJsonMapper.createDefault())
                .messageEndpoint("/mcp")
                .build();

        // Create image processing tools (stateless versions)
        var imageInfoTool = createStatelessImageInfoTool();
        var imageResizeTool = createStatelessImageResizeTool();
        var imageGrayscaleTool = createStatelessImageGrayscaleTool();
        var versionTool = createStatelessVersionTool();
        var displayImageTool = createStatelessDisplayImageTool();

        // Create MCP server with HTTP transport
        McpStatelessSyncServer mcpServer = McpServer.sync(transport)
                .serverInfo("image-processing-server", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .tools(imageInfoTool, imageResizeTool, imageGrayscaleTool, versionTool, displayImageTool)
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
        System.err.println("Image Processing MCP Server started on HTTP port " + port);
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

    private static McpServerFeatures.AsyncToolSpecification createImageInfoTool() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "image_path": {
                  "type": "string",
                  "description": "Path to the image file or URL"
                },
                "image_data": {
                  "type": "string",
                  "description": "Base64 encoded image data (alternative to image_path)"
                }
              }
            }
            """;

        return new McpServerFeatures.AsyncToolSpecification.Builder().tool(
                McpSchema.Tool.builder()
                        .name("get_image_info")
                        .description("Gets information about an image (dimensions, format, size)")
                        .inputSchema(McpJsonMapper.createDefault(), schema)
                        .build())
                .callHandler(
                (exchange, request) -> {
                    try {
                        BufferedImage image = loadImage(request.arguments());

                        if (image == null) {
                            return Mono.just(new McpSchema.CallToolResult.Builder()
                                    .content(List.of(new McpSchema.TextContent("Error: Could not load image")))
                                    .isError(true)
                                    .build()
                            );
                        }

                        String info = String.format(
                                "Image Information:\n" +
                                        "- Width: %d pixels\n" +
                                        "- Height: %d pixels\n" +
                                        "- Type: %s\n" +
                                        "- Color Model: %s\n" +
                                        "- Has Alpha: %s",
                                image.getWidth(),
                                image.getHeight(),
                                getImageType(image.getType()),
                                image.getColorModel().getClass().getSimpleName(),
                                image.getColorModel().hasAlpha()
                        );

                        return Mono.just(new McpSchema.CallToolResult.Builder()
                                .content(List.of(new McpSchema.TextContent(info)))
                                .isError(false)
                                .build()
                        );

                    } catch (Exception e) {
                        return Mono.just(new McpSchema.CallToolResult.Builder()
                                .content(List.of(new McpSchema.TextContent("Error: " + e.getMessage())))
                                .isError(true)
                                .build()
                        );
                    }
                }
                ).build();
    }

    private static McpServerFeatures.AsyncToolSpecification createImageResizeTool() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "image_path": {
                  "type": "string",
                  "description": "Path to the image file or URL"
                },
                "image_data": {
                  "type": "string",
                  "description": "Base64 encoded image data (alternative to image_path)"
                },
                "width": {
                  "type": "integer",
                  "description": "Target width in pixels"
                },
                "height": {
                  "type": "integer",
                  "description": "Target height in pixels"
                },
                "output_path": {
                  "type": "string",
                  "description": "Path where to save the resized image"
                }
              },
              "required": ["width", "height", "output_path"]
            }
            """;

        return new McpServerFeatures.AsyncToolSpecification.Builder().tool(
                McpSchema.Tool.builder()
                        .name("resize_image")
                        .description("Resizes an image to the specified dimensions")
                        .inputSchema(McpJsonMapper.createDefault(), schema)
                        .build())
                .callHandler(
                (exchange, request) -> {
                    try {
                        var args = request.arguments();
                        BufferedImage original = loadImage(args);

                        if (original == null) {
                            return Mono.just(new McpSchema.CallToolResult.Builder()
                                    .content(List.of(new McpSchema.TextContent("Error: Could not load image")))
                                    .isError(true)
                                    .build()
                            );
                        }

                        int targetWidth = ((Number) args.get("width")).intValue();
                        int targetHeight = ((Number) args.get("height")).intValue();
                        String outputPath = args.get("output_path").toString();

                        BufferedImage resized = new BufferedImage(
                                targetWidth,
                                targetHeight,
                                BufferedImage.TYPE_INT_ARGB
                        );

                        var g = resized.createGraphics();
                        g.setRenderingHint(
                                java.awt.RenderingHints.KEY_INTERPOLATION,
                                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR
                        );
                        g.drawImage(original, 0, 0, targetWidth, targetHeight, null);
                        g.dispose();

                        File outputFile = new File(outputPath);
                        String format = getFormatFromPath(outputPath);
                        ImageIO.write(resized, format, outputFile);

                        String result = String.format(
                                "Image resized successfully!\n" +
                                        "- Original size: %dx%d\n" +
                                        "- New size: %dx%d\n" +
                                        "- Saved to: %s",
                                original.getWidth(), original.getHeight(),
                                targetWidth, targetHeight,
                                outputFile.getAbsolutePath()
                        );

                        return Mono.just(new McpSchema.CallToolResult.Builder()
                                .content(List.of(new McpSchema.TextContent(result)))
                                .isError(false)
                                .build()
                        );

                    } catch (Exception e) {
                        return Mono.just(new McpSchema.CallToolResult.Builder()
                                .content(List.of(new McpSchema.TextContent("Error: " + e.getMessage())))
                                .isError(true)
                                .build()
                        );
                    }
                }
                ).build();
    }

    private static McpServerFeatures.AsyncToolSpecification createImageGrayscaleTool() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "image_path": {
                  "type": "string",
                  "description": "Path to the image file or URL"
                },
                "image_data": {
                  "type": "string",
                  "description": "Base64 encoded image data (alternative to image_path)"
                },
                "output_path": {
                  "type": "string",
                  "description": "Path where to save the grayscale image"
                }
              },
              "required": ["output_path"]
            }
            """;

        return new McpServerFeatures.AsyncToolSpecification.Builder().tool(
                McpSchema.Tool.builder()
                        .name("convert_to_grayscale")
                        .description("Converts an image to grayscale")
                        .inputSchema(McpJsonMapper.createDefault(), schema)
                        .build())
                .callHandler(
                (exchange, request) -> {
                    try {
                        var args = request.arguments();
                        BufferedImage original = loadImage(args);

                        if (original == null) {
                            return Mono.just(new McpSchema.CallToolResult.Builder()
                                    .content(List.of(new McpSchema.TextContent("Error: Could not load image")))
                                    .isError(true)
                                    .build()
                            );
                        }

                        String outputPath = args.get("output_path").toString();

                        // Convert to grayscale
                        BufferedImage grayscale = new BufferedImage(
                                original.getWidth(),
                                original.getHeight(),
                                BufferedImage.TYPE_BYTE_GRAY
                        );

                        var g = grayscale.createGraphics();
                        g.drawImage(original, 0, 0, null);
                        g.dispose();

                        File outputFile = new File(outputPath);
                        String format = getFormatFromPath(outputPath);
                        ImageIO.write(grayscale, format, outputFile);

                        String result = String.format(
                                "Image converted to grayscale successfully!\n" +
                                        "- Original size: %dx%d\n" +
                                        "- Saved to: %s",
                                original.getWidth(), original.getHeight(),
                                outputFile.getAbsolutePath()
                        );

                        return Mono.just(new McpSchema.CallToolResult.Builder()
                                .content(List.of(new McpSchema.TextContent(result)))
                                .isError(false)
                                .build()
                        );

                    } catch (Exception e) {
                        return Mono.just(new McpSchema.CallToolResult.Builder()
                                .content(List.of(new McpSchema.TextContent("Error: " + e.getMessage())))
                                .isError(true)
                                .build()
                        );
                    }
                }
                ).build();
    }

    private static BufferedImage loadImage(java.util.Map<String, Object> arguments) throws Exception {
        if (arguments.containsKey("image_data")) {
            // Load from base64 data
            String base64Data = arguments.get("image_data").toString();
            byte[] imageBytes = Base64.getDecoder().decode(base64Data);
            return ImageIO.read(new ByteArrayInputStream(imageBytes));
        } else if (arguments.containsKey("image_path")) {
            String path = arguments.get("image_path").toString();
            // Check if it's a URL
            if (path.startsWith("http://") || path.startsWith("https://")) {
                return ImageIO.read(new URL(path));
            } else {
                return ImageIO.read(new File(path));
            }
        }
        return null;
    }

    private static String getImageType(int type) {
        return switch (type) {
            case BufferedImage.TYPE_INT_RGB -> "RGB";
            case BufferedImage.TYPE_INT_ARGB -> "ARGB";
            case BufferedImage.TYPE_BYTE_GRAY -> "Grayscale";
            case BufferedImage.TYPE_INT_BGR -> "BGR";
            case BufferedImage.TYPE_3BYTE_BGR -> "3-Byte BGR";
            case BufferedImage.TYPE_4BYTE_ABGR -> "4-Byte ABGR";
            default -> "Unknown (" + type + ")";
        };
    }

    private static String getFormatFromPath(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".png")) return "png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "jpg";
        if (lower.endsWith(".gif")) return "gif";
        if (lower.endsWith(".bmp")) return "bmp";
        return "png"; // default
    }

    private static McpServerFeatures.AsyncToolSpecification createVersionTool() {
        String schema = """
            {
              "type": "object",
              "properties": {}
            }
            """;

        return new McpServerFeatures.AsyncToolSpecification.Builder().tool(
                McpSchema.Tool.builder()
                        .name("get_version")
                        .description("Gets the build version/timestamp of the image processing server")
                        .inputSchema(McpJsonMapper.createDefault(), schema)
                        .build())
                .callHandler(
                (exchange, request) -> {
                    try {
                        // Load build info from properties file
                        String buildTimestamp = "unknown";
                        try (InputStream input = ImageProcessingServer.class.getClassLoader()
                                .getResourceAsStream("build-info.properties")) {
                            if (input != null) {
                                Properties props = new Properties();
                                props.load(input);
                                buildTimestamp = props.getProperty("build.timestamp", "unknown");
                            }
                        }

                        String versionInfo = String.format(
                                "Image Processing Server\n" +
                                        "Version: 1.0.0\n" +
                                        "Build Timestamp: %s",
                                buildTimestamp
                        );

                        return Mono.just(new McpSchema.CallToolResult.Builder()
                                .content(List.of(new McpSchema.TextContent(versionInfo)))
                                .isError(false)
                                .build()
                        );

                    } catch (Exception e) {
                        return Mono.just(new McpSchema.CallToolResult.Builder()
                                .content(List.of(new McpSchema.TextContent("Error: " + e.getMessage())))
                                .isError(true)
                                .build()
                        );
                    }
                }
                ).build();
    }

    private static McpServerFeatures.AsyncToolSpecification createDisplayImageTool() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "image_path": {
                  "type": "string",
                  "description": "Path to the image file or URL"
                },
                "image_data": {
                  "type": "string",
                  "description": "Base64 encoded image data (alternative to image_path)"
                }
              }
            }
            """;

        return new McpServerFeatures.AsyncToolSpecification.Builder().tool(
                McpSchema.Tool.builder()
                        .name("display_image")
                        .description("Loads and displays an image in the MCP client chat")
                        .inputSchema(McpJsonMapper.createDefault(), schema)
                        .build())
                .callHandler(
                (exchange, request) -> {
                    try {
                        BufferedImage image = loadImage(request.arguments());

                        if (image == null) {
                            return Mono.just(new McpSchema.CallToolResult.Builder()
                                    .content(List.of(new McpSchema.TextContent("Error: Could not load image")))
                                    .isError(true)
                                    .build()
                            );
                        }

                        // Convert image to PNG and encode as base64
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        ImageIO.write(image, "png", baos);
                        byte[] imageBytes = baos.toByteArray();
                        String base64Image = Base64.getEncoder().encodeToString(imageBytes);

                        // Return as ImageContent which MCP clients can display
                        return Mono.just(new McpSchema.CallToolResult.Builder()
                                .content(List.of(new McpSchema.ImageContent(
                                        null,  // annotations
                                        base64Image,
                                        "image/png"
                                )))
                                .isError(false)
                                .build()
                        );

                    } catch (Exception e) {
                        return Mono.just(new McpSchema.CallToolResult.Builder()
                                .content(List.of(new McpSchema.TextContent("Error: " + e.getMessage())))
                                .isError(true)
                                .build()
                        );
                    }
                }
                ).build();
    }

    // Stateless versions of tools for HTTP transport
    private static McpStatelessServerFeatures.SyncToolSpecification createStatelessImageInfoTool() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "image_path": {
                  "type": "string",
                  "description": "Path to the image file or URL"
                },
                "image_data": {
                  "type": "string",
                  "description": "Base64 encoded image data (alternative to image_path)"
                }
              }
            }
            """;

        return new McpStatelessServerFeatures.SyncToolSpecification.Builder().tool(
                McpSchema.Tool.builder()
                        .name("get_image_info")
                        .description("Gets information about an image (dimensions, format, size)")
                        .inputSchema(McpJsonMapper.createDefault(), schema)
                        .build())
                .callHandler(
                (transportContext, request) -> {
                    try {
                        BufferedImage image = loadImage(request.arguments());
                        if (image == null) {
                            return new McpSchema.CallToolResult.Builder()
                                    .content(List.of(new McpSchema.TextContent("Error: Could not load image")))
                                    .isError(true)
                                    .build();
                        }

                        String info = String.format(
                                "Image Information:\n- Width: %d pixels\n- Height: %d pixels\n- Type: %s\n- Color Model: %s\n- Has Alpha: %s",
                                image.getWidth(), image.getHeight(), getImageType(image.getType()),
                                image.getColorModel().getClass().getSimpleName(), image.getColorModel().hasAlpha()
                        );

                        return new McpSchema.CallToolResult.Builder()
                                .content(List.of(new McpSchema.TextContent(info)))
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
    }

    private static McpStatelessServerFeatures.SyncToolSpecification createStatelessImageResizeTool() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "image_path": {
                  "type": "string",
                  "description": "Path to the image file or URL"
                },
                "image_data": {
                  "type": "string",
                  "description": "Base64 encoded image data (alternative to image_path)"
                },
                "width": {
                  "type": "integer",
                  "description": "Target width in pixels"
                },
                "height": {
                  "type": "integer",
                  "description": "Target height in pixels"
                },
                "output_path": {
                  "type": "string",
                  "description": "Path where to save the resized image"
                }
              },
              "required": ["width", "height", "output_path"]
            }
            """;

        return new McpStatelessServerFeatures.SyncToolSpecification.Builder().tool(
                McpSchema.Tool.builder()
                        .name("resize_image")
                        .description("Resizes an image to the specified dimensions")
                        .inputSchema(McpJsonMapper.createDefault(), schema)
                        .build())
                .callHandler(
                (transportContext, request) -> {
                    try {
                        var args = request.arguments();
                        BufferedImage original = loadImage(args);
                        if (original == null) {
                            return new McpSchema.CallToolResult.Builder()
                                    .content(List.of(new McpSchema.TextContent("Error: Could not load image")))
                                    .isError(true)
                                    .build();
                        }

                        int targetWidth = ((Number) args.get("width")).intValue();
                        int targetHeight = ((Number) args.get("height")).intValue();
                        String outputPath = args.get("output_path").toString();

                        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
                        var g = resized.createGraphics();
                        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                        g.drawImage(original, 0, 0, targetWidth, targetHeight, null);
                        g.dispose();

                        File outputFile = new File(outputPath);
                        String format = getFormatFromPath(outputPath);
                        ImageIO.write(resized, format, outputFile);

                        String result = String.format(
                                "Image resized successfully!\n- Original size: %dx%d\n- New size: %dx%d\n- Saved to: %s",
                                original.getWidth(), original.getHeight(), targetWidth, targetHeight, outputFile.getAbsolutePath()
                        );

                        return new McpSchema.CallToolResult.Builder()
                                .content(List.of(new McpSchema.TextContent(result)))
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
    }

    private static McpStatelessServerFeatures.SyncToolSpecification createStatelessImageGrayscaleTool() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "image_path": {
                  "type": "string",
                  "description": "Path to the image file or URL"
                },
                "image_data": {
                  "type": "string",
                  "description": "Base64 encoded image data (alternative to image_path)"
                },
                "output_path": {
                  "type": "string",
                  "description": "Path where to save the grayscale image"
                }
              },
              "required": ["output_path"]
            }
            """;

        return new McpStatelessServerFeatures.SyncToolSpecification.Builder().tool(
                McpSchema.Tool.builder()
                        .name("convert_to_grayscale")
                        .description("Converts an image to grayscale")
                        .inputSchema(McpJsonMapper.createDefault(), schema)
                        .build())
                .callHandler(
                (transportContext, request) -> {
                    try {
                        var args = request.arguments();
                        BufferedImage original = loadImage(args);
                        if (original == null) {
                            return new McpSchema.CallToolResult.Builder()
                                    .content(List.of(new McpSchema.TextContent("Error: Could not load image")))
                                    .isError(true)
                                    .build();
                        }

                        String outputPath = args.get("output_path").toString();

                        BufferedImage grayscale = new BufferedImage(original.getWidth(), original.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
                        var g = grayscale.createGraphics();
                        g.drawImage(original, 0, 0, null);
                        g.dispose();

                        File outputFile = new File(outputPath);
                        String format = getFormatFromPath(outputPath);
                        ImageIO.write(grayscale, format, outputFile);

                        String result = String.format(
                                "Image converted to grayscale successfully!\n- Original size: %dx%d\n- Saved to: %s",
                                original.getWidth(), original.getHeight(), outputFile.getAbsolutePath()
                        );

                        return new McpSchema.CallToolResult.Builder()
                                .content(List.of(new McpSchema.TextContent(result)))
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
    }

    private static McpStatelessServerFeatures.SyncToolSpecification createStatelessVersionTool() {
        String schema = """
            {
              "type": "object",
              "properties": {}
            }
            """;

        return new McpStatelessServerFeatures.SyncToolSpecification.Builder().tool(
                McpSchema.Tool.builder()
                        .name("get_version")
                        .description("Gets the build version/timestamp of the image processing server")
                        .inputSchema(McpJsonMapper.createDefault(), schema)
                        .build())
                .callHandler(
                (transportContext, request) -> {
                    try {
                        String buildTimestamp = "unknown";
                        try (InputStream input = ImageProcessingServer.class.getClassLoader()
                                .getResourceAsStream("build-info.properties")) {
                            if (input != null) {
                                Properties props = new Properties();
                                props.load(input);
                                buildTimestamp = props.getProperty("build.timestamp", "unknown");
                            }
                        }

                        String versionInfo = String.format(
                                "Image Processing Server\nVersion: 1.0.0\nBuild Timestamp: %s",
                                buildTimestamp
                        );

                        return new McpSchema.CallToolResult.Builder()
                                .content(List.of(new McpSchema.TextContent(versionInfo)))
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
    }

    private static McpStatelessServerFeatures.SyncToolSpecification createStatelessDisplayImageTool() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "image_path": {
                  "type": "string",
                  "description": "Path to the image file or URL"
                },
                "image_data": {
                  "type": "string",
                  "description": "Base64 encoded image data (alternative to image_path)"
                }
              }
            }
            """;

        return new McpStatelessServerFeatures.SyncToolSpecification.Builder().tool(
                McpSchema.Tool.builder()
                        .name("display_image")
                        .description("Loads and displays an image in the MCP client chat")
                        .inputSchema(McpJsonMapper.createDefault(), schema)
                        .build())
                .callHandler(
                (transportContext, request) -> {
                    try {
                        BufferedImage image = loadImage(request.arguments());
                        if (image == null) {
                            return new McpSchema.CallToolResult.Builder()
                                    .content(List.of(new McpSchema.TextContent("Error: Could not load image")))
                                    .isError(true)
                                    .build();
                        }

                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        ImageIO.write(image, "png", baos);
                        byte[] imageBytes = baos.toByteArray();
                        String base64Image = Base64.getEncoder().encodeToString(imageBytes);

                        return new McpSchema.CallToolResult.Builder()
                                .content(List.of(new McpSchema.ImageContent(null, base64Image, "image/png")))
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
    }
}
