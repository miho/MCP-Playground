package com.imageprocessing.server;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.server.McpAsyncServer;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.json.McpJsonMapper;
import reactor.core.publisher.Mono;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.opencv.core.Mat;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

/**
 * MCP Server providing comprehensive image processing capabilities using OpenCV.
 * Supports both stdio and HTTP transports.
 */
public class ImageProcessingMcpServer {

    private static final IntermediateResultCache cache = new IntermediateResultCache();
    private static final TextResultCache textCache = new TextResultCache();

    public static void main(String[] args) {
        try {
            // Initialize OpenCV
            System.err.println("Initializing OpenCV...");
            nu.pattern.OpenCV.loadLocally();
            System.err.println("OpenCV loaded successfully: " + org.opencv.core.Core.VERSION);

            // Parse command-line arguments
            boolean useHttp = false;
            int port = 8082;

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

        // Create all 10 tools
        var loadImageTool = ToolFactory.createLoadImageTool();
        var resizeImageTool = ToolFactory.createResizeImageTool();
        var segmentImageTool = ToolFactory.createSegmentImageTool();
        var colorToGrayscaleTool = ToolFactory.createColorToGrayscaleTool();
        var filterImageTool = ToolFactory.createFilterImageTool();
        var denoiseImageTool = ToolFactory.createDenoiseImageTool();
        var blurImageTool = ToolFactory.createBlurImageTool();
        var detectContoursTool = ToolFactory.createDetectContoursTool();
        var outputSegmentedTool = ToolFactory.createOutputSegmentedTool();
        var displayImageTool = ToolFactory.createDisplayImageTool();

        McpAsyncServer server = McpServer.async(transportProvider)
                .serverInfo("image-processing-mcp-server", getVersion())
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .tools(loadImageTool, resizeImageTool, segmentImageTool, colorToGrayscaleTool,
                       filterImageTool, denoiseImageTool, blurImageTool, detectContoursTool,
                       outputSegmentedTool, displayImageTool)
                .build();

        System.err.println("Image Processing MCP Server started (stdio mode)");
        System.err.println("Version: " + getVersion());
        System.err.println("Ready for connections...");

        CountDownLatch latch = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.err.println("Shutting down server...");
            cache.clear();
            server.close();
            latch.countDown();
        }));

        latch.await();
    }

    private static void startHttpServer(int port) throws Exception {
        HttpServletStatelessServerTransport transport = HttpServletStatelessServerTransport.builder()
                .jsonMapper(McpJsonMapper.createDefault())
                .messageEndpoint("/mcp")
                .build();

        // Create stateless versions of all 10 tools
        var loadImageTool = ToolFactory.createStatelessLoadImageTool();
        var resizeImageTool = ToolFactory.createStatelessResizeImageTool();
        var segmentImageTool = ToolFactory.createStatelessSegmentImageTool();
        var colorToGrayscaleTool = ToolFactory.createStatelessColorToGrayscaleTool();
        var filterImageTool = ToolFactory.createStatelessFilterImageTool();
        var denoiseImageTool = ToolFactory.createStatelessDenoiseImageTool();
        var blurImageTool = ToolFactory.createStatelessBlurImageTool();
        var detectContoursTool = ToolFactory.createStatelessDetectContoursTool();
        var outputSegmentedTool = ToolFactory.createStatelessOutputSegmentedTool();
        var displayImageTool = ToolFactory.createStatelessDisplayImageTool();

        McpStatelessSyncServer mcpServer = McpServer.sync(transport)
                .serverInfo("image-processing-mcp-server", getVersion())
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .tools(loadImageTool, resizeImageTool, segmentImageTool, colorToGrayscaleTool,
                       filterImageTool, denoiseImageTool, blurImageTool, detectContoursTool,
                       outputSegmentedTool, displayImageTool)
                .build();

        Server jettyServer = new Server(port);
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        jettyServer.setHandler(context);

        ServletHolder servletHolder = new ServletHolder(transport);
        context.addServlet(servletHolder, "/mcp");

        jettyServer.start();
        System.err.println("Image Processing MCP Server started on HTTP port " + port);
        System.err.println("Version: " + getVersion());
        System.err.println("MCP endpoint: http://localhost:" + port + "/mcp");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.err.println("Shutting down server...");
            try {
                cache.clear();
                mcpServer.close();
                jettyServer.stop();
            } catch (Exception e) {
                System.err.println("Error during shutdown: " + e.getMessage());
            }
        }));

        jettyServer.join();
    }

    private static String getVersion() {
        try (InputStream input = ImageProcessingMcpServer.class.getClassLoader()
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

    /**
     * Factory class for creating MCP tool specifications.
     * Separates async (stdio) and stateless sync (HTTP) tool implementations.
     */
    static class ToolFactory {

        /**
         * Helper to create tool result with both text message and base64 image.
         */
        private static McpSchema.CallToolResult createImageResult(String message, Mat outputImage) throws Exception {
            String base64Image = OpenCVImageProcessor.matToBase64Png(outputImage);
            String imageData = "data:image/png;base64," + base64Image;

            return new McpSchema.CallToolResult.Builder()
                    .content(List.of(
                        new McpSchema.TextContent(message),
                        new McpSchema.TextContent(imageData)
                    ))
                    .isError(false)
                    .build();
        }

        // ==================== TOOL LIST CREATORS ====================

        /**
         * Create all async tools for stdio mode (including workflow management).
         */
        public static java.util.List<io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification>
                createAllAsyncTools(IntermediateResultCache sharedCache, TextResultCache sharedTextCache) {
            return java.util.List.of(
                createLoadImageTool(),
                createResizeImageTool(),
                createSegmentImageTool(),
                createColorToGrayscaleTool(),
                createFilterImageTool(),
                createDenoiseImageTool(),
                createBlurImageTool(),
                createDetectContoursTool(),
                createOutputSegmentedTool(),
                createDisplayImageTool(),
                WorkflowMcpTools.createAsyncAddToWorkflow(),
                WorkflowMcpTools.createAsyncClearWorkflow(),
                WorkflowMcpTools.createAsyncGetWorkflowStatus(),
                WorkflowMcpTools.createAsyncExecuteWorkflow()
            );
        }

        /**
         * Create all stateless sync tools for HTTP mode (including workflow management).
         */
        public static java.util.List<io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification>
                createAllStatelessTools(IntermediateResultCache sharedCache, TextResultCache sharedTextCache) {
            return java.util.List.of(
                createStatelessLoadImageTool(),
                createStatelessResizeImageTool(),
                createStatelessSegmentImageTool(),
                createStatelessColorToGrayscaleTool(),
                createStatelessFilterImageTool(),
                createStatelessDenoiseImageTool(),
                createStatelessBlurImageTool(),
                createStatelessDetectContoursTool(),
                createStatelessOutputSegmentedTool(),
                createStatelessDisplayImageTool(),
                WorkflowMcpTools.createStatelessAddToWorkflow(),
                WorkflowMcpTools.createStatelessClearWorkflow(),
                WorkflowMcpTools.createStatelessGetWorkflowStatus(),
                WorkflowMcpTools.createStatelessExecuteWorkflow()
            );
        }

        // ==================== ASYNC TOOLS (STDIO) ====================

        static McpServerFeatures.AsyncToolSpecification createLoadImageTool() {
            String schema = """
                {
                  "type": "object",
                  "properties": {
                    "image_path": {
                      "type": "string",
                      "description": "Path to the image file"
                    },
                    "image_url": {
                      "type": "string",
                      "description": "URL to the image"
                    },
                    "image_data": {
                      "type": "string",
                      "description": "Base64 encoded image data"
                    },
                    "output_key": {
                      "type": "string",
                      "description": "Key to store the loaded image in cache for later use"
                    }
                  },
                  "description": "At least one of image_path, image_url, or image_data must be provided"
                }
                """;

            return new McpServerFeatures.AsyncToolSpecification.Builder().tool(
                    McpSchema.Tool.builder()
                            .name("load_image")
                            .description("Load an image from path, URL, or base64 data and optionally store in cache")
                            .inputSchema(McpJsonMapper.createDefault(), schema)
                            .build())
                    .callHandler((exchange, request) -> {
                        try {
                            var args = request.arguments();
                            String imagePath = getStringArg(args, "image_path");
                            String imageUrl = getStringArg(args, "image_url");
                            String imageData = getStringArg(args, "image_data");
                            String outputKey = getStringArg(args, "output_key");

                            Mat mat = OpenCVImageProcessor.loadImage(imagePath, imageUrl, imageData);

                            if (outputKey != null && !outputKey.isBlank()) {
                                cache.put(outputKey, mat);
                            }

                            String info = OpenCVImageProcessor.getImageInfo(mat);
                            String message = String.format("Image loaded successfully!\n%s", info);

                            if (outputKey != null && !outputKey.isBlank()) {
                                message += "\n- Cached with key: " + outputKey;
                            }

                            // Convert image to base64 for client
                            String base64Image = OpenCVImageProcessor.matToBase64Png(mat);
                            String imageDataUri = "data:image/png;base64," + base64Image;

                            if (outputKey == null || outputKey.isBlank()) {
                                mat.release();
                            }

                            // Return both text info and base64 image
                            return Mono.just(new McpSchema.CallToolResult.Builder()
                                    .content(List.of(
                                        new McpSchema.TextContent(message),
                                        new McpSchema.TextContent(imageDataUri)
                                    ))
                                    .isError(false)
                                    .build());

                        } catch (Exception e) {
                            return Mono.just(new McpSchema.CallToolResult.Builder()
                                    .content(List.of(new McpSchema.TextContent("Error: " + e.getMessage())))
                                    .isError(true)
                                    .build());
                        }
                    })
                    .build();
        }

        static McpServerFeatures.AsyncToolSpecification createResizeImageTool() {
            String schema = """
                {
                  "type": "object",
                  "properties": {
                    "image_path": {"type": "string", "description": "Path to the image file"},
                    "image_url": {"type": "string", "description": "URL to the image"},
                    "image_data": {"type": "string", "description": "Base64 encoded image data"},
                    "input_key": {"type": "string", "description": "Key to retrieve cached image"},
                    "width": {"type": "integer", "description": "Target width in pixels"},
                    "height": {"type": "integer", "description": "Target height in pixels"},
                    "interpolation": {
                      "type": "string",
                      "description": "Interpolation method: NEAREST, LINEAR, CUBIC, AREA, LANCZOS4",
                      "default": "LINEAR"
                    },
                    "output_path": {"type": "string", "description": "Path to save resized image (optional)"},
                    "output_key": {"type": "string", "description": "Key to store result in cache (optional)"}
                  },
                  "required": ["width", "height"]
                }
                """;

            return new McpServerFeatures.AsyncToolSpecification.Builder().tool(
                    McpSchema.Tool.builder()
                            .name("resize_image")
                            .description("Resize an image to specified dimensions with various interpolation methods")
                            .inputSchema(McpJsonMapper.createDefault(), schema)
                            .build())
                    .callHandler((exchange, request) -> {
                        try {
                            var args = request.arguments();
                            Mat src = loadImageFromArgs(args);

                            int width = getIntArg(args, "width");
                            int height = getIntArg(args, "height");
                            String interpolation = getStringArg(args, "interpolation", "LINEAR");

                            Mat resized = OpenCVImageProcessor.resize(src, width, height, interpolation);

                            String result = handleOutput(args, resized,
                                String.format("Image resized successfully!\n- Original size: %dx%d\n- New size: %dx%d\n- Interpolation: %s",
                                    src.cols(), src.rows(), width, height, interpolation));

                            // Convert to base64 for client
                            String base64Image = OpenCVImageProcessor.matToBase64Png(resized);
                            String imageData = "data:image/png;base64," + base64Image;

                            if (!cache.containsKey(getStringArg(args, "input_key"))) {
                                src.release();
                            }

                            return Mono.just(new McpSchema.CallToolResult.Builder()
                                    .content(List.of(
                                        new McpSchema.TextContent(result),
                                        new McpSchema.TextContent(imageData)
                                    ))
                                    .isError(false)
                                    .build());

                        } catch (Exception e) {
                            return Mono.just(new McpSchema.CallToolResult.Builder()
                                    .content(List.of(new McpSchema.TextContent("Error: " + e.getMessage())))
                                    .isError(true)
                                    .build());
                        }
                    })
                    .build();
        }

        static McpServerFeatures.AsyncToolSpecification createSegmentImageTool() {
            String schema = """
                {
                  "type": "object",
                  "properties": {
                    "image_path": {"type": "string"},
                    "image_url": {"type": "string"},
                    "image_data": {"type": "string"},
                    "result_key": {"type": "string"},
                    "threshold": {
                      "type": "number",
                      "description": "Threshold value (0-255). Use 0 for Otsu auto-thresholding",
                      "default": 127
                    },
                    "threshold_type": {
                      "type": "string",
                      "description": "Type: BINARY, BINARY_INV, TRUNC, TOZERO, TOZERO_INV, OTSU",
                      "default": "BINARY"
                    },
                    "invert": {
                      "type": "boolean",
                      "description": "Invert the segmentation result (apply bitwise NOT)",
                      "default": false
                    },
                    "output_path": {"type": "string"},
                    "output_key": {"type": "string"}
                  }
                }
                """;

            return new McpServerFeatures.AsyncToolSpecification.Builder().tool(
                    McpSchema.Tool.builder()
                            .name("segment_image")
                            .description("Segment an image using threshold-based methods including Otsu auto-thresholding")
                            .inputSchema(McpJsonMapper.createDefault(), schema)
                            .build())
                    .callHandler((exchange, request) -> {
                        try {
                            var args = request.arguments();
                            Mat src = loadImageFromArgs(args);

                            double threshold = getDoubleArg(args, "threshold", 127.0);
                            String thresholdType = getStringArg(args, "threshold_type", "BINARY");
                            boolean invert = getBooleanArg(args, "invert", false);

                            Mat segmented = OpenCVImageProcessor.segment(src, threshold, thresholdType, invert);

                            String invertStr = invert ? ", inverted" : "";
                            String result = handleOutput(args, segmented,
                                String.format("Image segmented successfully!\n- Threshold: %.1f\n- Type: %s%s",
                                    threshold, thresholdType, invertStr));

                            // Convert to base64 for client
                            String base64Image = OpenCVImageProcessor.matToBase64Png(segmented);
                            String imageData = "data:image/png;base64," + base64Image;

                            if (!cache.containsKey(getStringArg(args, "input_key"))) {
                                src.release();
                            }

                            return Mono.just(new McpSchema.CallToolResult.Builder()
                                    .content(List.of(
                                        new McpSchema.TextContent(result),
                                        new McpSchema.TextContent(imageData)
                                    ))
                                    .isError(false)
                                    .build());

                        } catch (Exception e) {
                            return Mono.just(new McpSchema.CallToolResult.Builder()
                                    .content(List.of(new McpSchema.TextContent("Error: " + e.getMessage())))
                                    .isError(true)
                                    .build());
                        }
                    })
                    .build();
        }

        static McpServerFeatures.AsyncToolSpecification createColorToGrayscaleTool() {
            String schema = """
                {
                  "type": "object",
                  "properties": {
                    "image_path": {"type": "string"},
                    "image_url": {"type": "string"},
                    "image_data": {"type": "string"},
                    "result_key": {"type": "string"},
                    "output_path": {"type": "string"},
                    "output_key": {"type": "string"}
                  }
                }
                """;

            return new McpServerFeatures.AsyncToolSpecification.Builder().tool(
                    McpSchema.Tool.builder()
                            .name("color_to_grayscale")
                            .description("Convert a color image to grayscale")
                            .inputSchema(McpJsonMapper.createDefault(), schema)
                            .build())
                    .callHandler((exchange, request) -> {
                        try {
                            var args = request.arguments();
                            Mat src = loadImageFromArgs(args);

                            Mat gray = OpenCVImageProcessor.colorToGrayscale(src);

                            String result = handleOutput(args, gray,
                                String.format("Image converted to grayscale!\n- Original channels: %d\n- New channels: 1",
                                    src.channels()));

                            // Convert to base64 for client
                            String base64Image = OpenCVImageProcessor.matToBase64Png(gray);
                            String imageData = "data:image/png;base64," + base64Image;

                            if (!cache.containsKey(getStringArg(args, "input_key"))) {
                                src.release();
                            }

                            return Mono.just(new McpSchema.CallToolResult.Builder()
                                    .content(List.of(
                                        new McpSchema.TextContent(result),
                                        new McpSchema.TextContent(imageData)
                                    ))
                                    .isError(false)
                                    .build());

                        } catch (Exception e) {
                            return Mono.just(new McpSchema.CallToolResult.Builder()
                                    .content(List.of(new McpSchema.TextContent("Error: " + e.getMessage())))
                                    .isError(true)
                                    .build());
                        }
                    })
                    .build();
        }

        static McpServerFeatures.AsyncToolSpecification createFilterImageTool() {
            String schema = """
                {
                  "type": "object",
                  "properties": {
                    "image_path": {"type": "string"},
                    "image_url": {"type": "string"},
                    "image_data": {"type": "string"},
                    "result_key": {"type": "string"},
                    "filter_type": {
                      "type": "string",
                      "description": "Filter type: GAUSSIAN, MEDIAN, BILATERAL",
                      "default": "GAUSSIAN"
                    },
                    "kernel_size": {"type": "integer", "description": "Kernel size (must be odd)", "default": 5},
                    "sigma_x": {"type": "number", "description": "Sigma X for Gaussian/Bilateral", "default": 1.0},
                    "sigma_color": {"type": "number", "description": "Sigma Color for Bilateral", "default": 75.0},
                    "sigma_space": {"type": "number", "description": "Sigma Space for Bilateral", "default": 75.0},
                    "output_path": {"type": "string"},
                    "output_key": {"type": "string"}
                  }
                }
                """;

            return new McpServerFeatures.AsyncToolSpecification.Builder().tool(
                    McpSchema.Tool.builder()
                            .name("filter_image")
                            .description("Apply various filters (Gaussian, Median, Bilateral) to an image")
                            .inputSchema(McpJsonMapper.createDefault(), schema)
                            .build())
                    .callHandler((exchange, request) -> {
                        try {
                            var args = request.arguments();
                            Mat src = loadImageFromArgs(args);

                            String filterType = getStringArg(args, "filter_type", "GAUSSIAN");
                            int kernelSize = getIntArg(args, "kernel_size", 5);
                            double sigmaX = getDoubleArg(args, "sigma_x", 1.0);
                            double sigmaColor = getDoubleArg(args, "sigma_color", 75.0);
                            double sigmaSpace = getDoubleArg(args, "sigma_space", 75.0);

                            Mat filtered = OpenCVImageProcessor.filter(src, filterType, kernelSize,
                                sigmaX, sigmaColor, sigmaSpace);

                            String result = handleOutput(args, filtered,
                                String.format("Filter applied successfully!\n- Filter type: %s\n- Kernel size: %d",
                                    filterType, kernelSize));

                            // Convert to base64 for client
                            String base64Image = OpenCVImageProcessor.matToBase64Png(filtered);
                            String imageData = "data:image/png;base64," + base64Image;

                            if (!cache.containsKey(getStringArg(args, "input_key"))) {
                                src.release();
                            }

                            return Mono.just(new McpSchema.CallToolResult.Builder()
                                    .content(List.of(
                                        new McpSchema.TextContent(result),
                                        new McpSchema.TextContent(imageData)
                                    ))
                                    .isError(false)
                                    .build());

                        } catch (Exception e) {
                            return Mono.just(new McpSchema.CallToolResult.Builder()
                                    .content(List.of(new McpSchema.TextContent("Error: " + e.getMessage())))
                                    .isError(true)
                                    .build());
                        }
                    })
                    .build();
        }

        static McpServerFeatures.AsyncToolSpecification createDenoiseImageTool() {
            String schema = """
                {
                  "type": "object",
                  "properties": {
                    "image_path": {"type": "string"},
                    "image_url": {"type": "string"},
                    "image_data": {"type": "string"},
                    "result_key": {"type": "string"},
                    "h": {
                      "type": "number",
                      "description": "Filter strength (higher = more denoising, but slower)",
                      "default": 10.0
                    },
                    "template_window_size": {
                      "type": "integer",
                      "description": "Size of template patch (should be odd)",
                      "default": 7
                    },
                    "search_window_size": {
                      "type": "integer",
                      "description": "Size of search area (should be odd)",
                      "default": 21
                    },
                    "output_path": {"type": "string"},
                    "output_key": {"type": "string"}
                  }
                }
                """;

            return new McpServerFeatures.AsyncToolSpecification.Builder().tool(
                    McpSchema.Tool.builder()
                            .name("denoise_image")
                            .description("Denoise an image using Non-Local Means Denoising algorithm")
                            .inputSchema(McpJsonMapper.createDefault(), schema)
                            .build())
                    .callHandler((exchange, request) -> {
                        try {
                            var args = request.arguments();
                            Mat src = loadImageFromArgs(args);

                            float h = getFloatArg(args, "h", 10.0f);
                            int templateWindowSize = getIntArg(args, "template_window_size", 7);
                            int searchWindowSize = getIntArg(args, "search_window_size", 21);

                            Mat denoised = OpenCVImageProcessor.denoise(src, h, templateWindowSize, searchWindowSize);

                            String result = handleOutput(args, denoised,
                                String.format("Image denoised successfully!\n- Filter strength: %.1f\n- Template window: %d\n- Search window: %d",
                                    h, templateWindowSize, searchWindowSize));

                            // Convert to base64 for client
                            String base64Image = OpenCVImageProcessor.matToBase64Png(denoised);
                            String imageData = "data:image/png;base64," + base64Image;

                            if (!cache.containsKey(getStringArg(args, "input_key"))) {
                                src.release();
                            }

                            return Mono.just(new McpSchema.CallToolResult.Builder()
                                    .content(List.of(
                                        new McpSchema.TextContent(result),
                                        new McpSchema.TextContent(imageData)
                                    ))
                                    .isError(false)
                                    .build());

                        } catch (Exception e) {
                            return Mono.just(new McpSchema.CallToolResult.Builder()
                                    .content(List.of(new McpSchema.TextContent("Error: " + e.getMessage())))
                                    .isError(true)
                                    .build());
                        }
                    })
                    .build();
        }

        static McpServerFeatures.AsyncToolSpecification createBlurImageTool() {
            String schema = """
                {
                  "type": "object",
                  "properties": {
                    "image_path": {"type": "string"},
                    "image_url": {"type": "string"},
                    "image_data": {"type": "string"},
                    "result_key": {"type": "string"},
                    "blur_type": {
                      "type": "string",
                      "description": "Blur type: GAUSSIAN, MOTION, BOX, AVERAGE",
                      "default": "GAUSSIAN"
                    },
                    "kernel_size": {"type": "integer", "description": "Kernel size (must be odd)", "default": 5},
                    "angle": {
                      "type": "number",
                      "description": "Angle for motion blur in degrees",
                      "default": 0.0
                    },
                    "output_path": {"type": "string"},
                    "output_key": {"type": "string"}
                  }
                }
                """;

            return new McpServerFeatures.AsyncToolSpecification.Builder().tool(
                    McpSchema.Tool.builder()
                            .name("blur_image")
                            .description("Apply various blur effects (Gaussian, Motion, Box) to an image")
                            .inputSchema(McpJsonMapper.createDefault(), schema)
                            .build())
                    .callHandler((exchange, request) -> {
                        try {
                            var args = request.arguments();
                            Mat src = loadImageFromArgs(args);

                            String blurType = getStringArg(args, "blur_type", "GAUSSIAN");
                            int kernelSize = getIntArg(args, "kernel_size", 5);
                            double angle = getDoubleArg(args, "angle", 0.0);

                            Mat blurred = OpenCVImageProcessor.blur(src, blurType, kernelSize, angle);

                            String result = handleOutput(args, blurred,
                                String.format("Blur applied successfully!\n- Blur type: %s\n- Kernel size: %d\n- Angle: %.1f°",
                                    blurType, kernelSize, angle));

                            // Convert to base64 for client
                            String base64Image = OpenCVImageProcessor.matToBase64Png(blurred);
                            String imageData = "data:image/png;base64," + base64Image;

                            if (!cache.containsKey(getStringArg(args, "input_key"))) {
                                src.release();
                            }

                            return Mono.just(new McpSchema.CallToolResult.Builder()
                                    .content(List.of(
                                        new McpSchema.TextContent(result),
                                        new McpSchema.TextContent(imageData)
                                    ))
                                    .isError(false)
                                    .build());

                        } catch (Exception e) {
                            return Mono.just(new McpSchema.CallToolResult.Builder()
                                    .content(List.of(new McpSchema.TextContent("Error: " + e.getMessage())))
                                    .isError(true)
                                    .build());
                        }
                    })
                    .build();
        }

        static McpServerFeatures.AsyncToolSpecification createDetectContoursTool() {
            String schema = """
                {
                  "type": "object",
                  "properties": {
                    "image_path": {"type": "string"},
                    "image_url": {"type": "string"},
                    "image_data": {"type": "string"},
                    "result_key": {"type": "string"},
                    "min_area": {
                      "type": "number",
                      "description": "Minimum contour area to include",
                      "default": 100.0
                    },
                    "max_area": {
                      "type": "number",
                      "description": "Maximum contour area (-1 for no limit)",
                      "default": -1.0
                    },
                    "min_circularity": {
                      "type": "number",
                      "description": "Minimum circularity 0-1 (-1 for no filter)",
                      "default": 0.0
                    },
                    "max_circularity": {
                      "type": "number",
                      "description": "Maximum circularity 0-1 (-1 for no filter)",
                      "default": 1.0
                    },
                    "output_path": {"type": "string", "description": "Save visualization image"},
                    "output_key": {"type": "string", "description": "Store visualization in cache"},
                    "output_csv_path": {"type": "string", "description": "Save blob data as CSV file"},
                    "output_csv_key": {"type": "string", "description": "Store blob CSV data in cache"}
                  }
                }
                """;

            return new McpServerFeatures.AsyncToolSpecification.Builder().tool(
                    McpSchema.Tool.builder()
                            .name("detect_contours")
                            .description("Detect and filter contours by size and circularity")
                            .inputSchema(McpJsonMapper.createDefault(), schema)
                            .build())
                    .callHandler((exchange, request) -> {
                        try {
                            var args = request.arguments();
                            Mat src = loadImageFromArgs(args);

                            double minArea = getDoubleArg(args, "min_area", 100.0);
                            double maxArea = getDoubleArg(args, "max_area", -1.0);
                            double minCircularity = getDoubleArg(args, "min_circularity", 0.0);
                            double maxCircularity = getDoubleArg(args, "max_circularity", 1.0);

                            Map<String, Object> contourResult = OpenCVImageProcessor.detectContours(
                                src, minArea, maxArea, minCircularity, maxCircularity);

                            Mat visualization = (Mat) contourResult.get("visualization");
                            int count = (int) contourResult.get("count");
                            @SuppressWarnings("unchecked")
                            List<BlobInfo> blobs = (List<BlobInfo>) contourResult.get("blobs");

                            String result = handleOutput(args, visualization,
                                String.format("Contours detected successfully!\n- Count: %d\n- Min area: %.1f\n- Max area: %.1f\n- Circularity: %.2f-%.2f",
                                    count, minArea, maxArea, minCircularity, maxCircularity));

                            // Generate CSV from blob data
                            StringBuilder csv = new StringBuilder();
                            csv.append(BlobInfo.getCsvHeader()).append("\n");
                            for (BlobInfo blob : blobs) {
                                csv.append(blob.toCsvRow()).append("\n");
                            }
                            String csvData = csv.toString();

                            // Save CSV to file or cache
                            String csvPath = getStringArg(args, "output_csv_path");
                            String csvKey = getStringArg(args, "output_csv_key");

                            if (csvPath != null && !csvPath.isBlank()) {
                                java.nio.file.Files.writeString(java.nio.file.Path.of(csvPath), csvData);
                                result += "\n- CSV saved to: " + csvPath;
                            }

                            if (csvKey != null && !csvKey.isBlank()) {
                                textCache.put(csvKey, csvData);
                                result += "\n- CSV cached with key: " + csvKey;
                            }

                            // Convert to base64 for client
                            String base64Image = OpenCVImageProcessor.matToBase64Png(visualization);
                            String imageData = "data:image/png;base64," + base64Image;

                            if (!cache.containsKey(getStringArg(args, "input_key"))) {
                                src.release();
                            }

                            return Mono.just(new McpSchema.CallToolResult.Builder()
                                    .content(List.of(
                                        new McpSchema.TextContent(result),
                                        new McpSchema.TextContent(imageData)
                                    ))
                                    .isError(false)
                                    .build());

                        } catch (Exception e) {
                            return Mono.just(new McpSchema.CallToolResult.Builder()
                                    .content(List.of(new McpSchema.TextContent("Error: " + e.getMessage())))
                                    .isError(true)
                                    .build());
                        }
                    })
                    .build();
        }

        static McpServerFeatures.AsyncToolSpecification createOutputSegmentedTool() {
            String schema = """
                {
                  "type": "object",
                  "properties": {
                    "image_path": {"type": "string", "description": "Source image path"},
                    "image_url": {"type": "string"},
                    "image_data": {"type": "string"},
                    "source_key": {"type": "string", "description": "Cached source image key"},
                    "mask_path": {"type": "string", "description": "Binary mask image path"},
                    "mask_key": {"type": "string", "description": "Cached mask key"},
                    "output_path": {
                      "type": "string",
                      "description": "Base path for output files (regions will be numbered)",
                      "default": "./segmented_output.png"
                    }
                  },
                  "required": ["output_path"]
                }
                """;

            return new McpServerFeatures.AsyncToolSpecification.Builder().tool(
                    McpSchema.Tool.builder()
                            .name("output_segmented")
                            .description("Extract and save segmented regions from an image using a binary mask")
                            .inputSchema(McpJsonMapper.createDefault(), schema)
                            .build())
                    .callHandler((exchange, request) -> {
                        try {
                            var args = request.arguments();

                            // Load source image
                            Mat src = loadImageFromArgs(args, "image_path", "image_url", "image_data", "source_key");

                            // Load mask image
                            Mat mask;
                            String maskKey = getStringArg(args, "mask_key");
                            if (maskKey != null && !maskKey.isBlank() && cache.containsKey(maskKey)) {
                                mask = cache.get(maskKey);
                            } else {
                                String maskPath = getStringArg(args, "mask_path");
                                if (maskPath == null || maskPath.isBlank()) {
                                    throw new IllegalArgumentException("Must provide mask_path or mask_key");
                                }
                                mask = OpenCVImageProcessor.loadImage(maskPath, null, null);
                            }

                            String outputPath = getStringArg(args, "output_path", "./segmented_output.png");

                            List<String> savedFiles = OpenCVImageProcessor.outputSegmented(src, mask, outputPath);

                            StringBuilder result = new StringBuilder("Segmented regions extracted successfully!\n");
                            result.append(String.format("- Total regions: %d\n", savedFiles.size()));
                            result.append("- Saved files:\n");
                            for (String file : savedFiles) {
                                result.append("  - ").append(file).append("\n");
                            }

                            if (!cache.containsKey(getStringArg(args, "source_key"))) {
                                src.release();
                            }
                            if (maskKey == null || maskKey.isBlank() || !cache.containsKey(maskKey)) {
                                mask.release();
                            }

                            return Mono.just(new McpSchema.CallToolResult.Builder()
                                    .content(List.of(new McpSchema.TextContent(result.toString())))
                                    .isError(false)
                                    .build());

                        } catch (Exception e) {
                            return Mono.just(new McpSchema.CallToolResult.Builder()
                                    .content(List.of(new McpSchema.TextContent("Error: " + e.getMessage())))
                                    .isError(true)
                                    .build());
                        }
                    })
                    .build();
        }

        static McpServerFeatures.AsyncToolSpecification createDisplayImageTool() {
            String schema = """
                {
                  "type": "object",
                  "properties": {
                    "image_path": {"type": "string"},
                    "image_url": {"type": "string"},
                    "image_data": {"type": "string"},
                    "input_key": {"type": "string"}
                  }
                }
                """;

            return new McpServerFeatures.AsyncToolSpecification.Builder().tool(
                    McpSchema.Tool.builder()
                            .name("display_image")
                            .description("Load and display an image in the MCP client as base64 PNG")
                            .inputSchema(McpJsonMapper.createDefault(), schema)
                            .build())
                    .callHandler((exchange, request) -> {
                        try {
                            var args = request.arguments();
                            Mat mat = loadImageFromArgs(args);

                            String base64Image = OpenCVImageProcessor.matToBase64Png(mat);

                            if (!cache.containsKey(getStringArg(args, "input_key"))) {
                                mat.release();
                            }

                            return Mono.just(new McpSchema.CallToolResult.Builder()
                                    .content(List.of(new McpSchema.ImageContent(null, base64Image, "image/png")))
                                    .isError(false)
                                    .build());

                        } catch (Exception e) {
                            return Mono.just(new McpSchema.CallToolResult.Builder()
                                    .content(List.of(new McpSchema.TextContent("Error: " + e.getMessage())))
                                    .isError(true)
                                    .build());
                        }
                    })
                    .build();
        }

        // ==================== STATELESS SYNC TOOLS (HTTP) ====================
        // Implementation omitted for brevity - similar pattern to async tools
        // but using McpStatelessServerFeatures.SyncToolSpecification and returning CallToolResult directly

        static McpStatelessServerFeatures.SyncToolSpecification createStatelessLoadImageTool() {
            // Similar implementation to async version but synchronous
            return createSyncToolFromAsync(createLoadImageTool());
        }

        static McpStatelessServerFeatures.SyncToolSpecification createStatelessResizeImageTool() {
            return createSyncToolFromAsync(createResizeImageTool());
        }

        static McpStatelessServerFeatures.SyncToolSpecification createStatelessSegmentImageTool() {
            return createSyncToolFromAsync(createSegmentImageTool());
        }

        static McpStatelessServerFeatures.SyncToolSpecification createStatelessColorToGrayscaleTool() {
            return createSyncToolFromAsync(createColorToGrayscaleTool());
        }

        static McpStatelessServerFeatures.SyncToolSpecification createStatelessFilterImageTool() {
            return createSyncToolFromAsync(createFilterImageTool());
        }

        static McpStatelessServerFeatures.SyncToolSpecification createStatelessDenoiseImageTool() {
            return createSyncToolFromAsync(createDenoiseImageTool());
        }

        static McpStatelessServerFeatures.SyncToolSpecification createStatelessBlurImageTool() {
            return createSyncToolFromAsync(createBlurImageTool());
        }

        static McpStatelessServerFeatures.SyncToolSpecification createStatelessDetectContoursTool() {
            return createSyncToolFromAsync(createDetectContoursTool());
        }

        static McpStatelessServerFeatures.SyncToolSpecification createStatelessOutputSegmentedTool() {
            return createSyncToolFromAsync(createOutputSegmentedTool());
        }

        static McpStatelessServerFeatures.SyncToolSpecification createStatelessDisplayImageTool() {
            return createSyncToolFromAsync(createDisplayImageTool());
        }

        /**
         * Helper method to convert async tool handler to sync tool handler.
         * This wraps the async Mono response and blocks to get the result.
         */
        private static McpStatelessServerFeatures.SyncToolSpecification createSyncToolFromAsync(
                McpServerFeatures.AsyncToolSpecification asyncTool) {

            var asyncHandler = asyncTool.callHandler();
            var tool = asyncTool.tool();

            return new McpStatelessServerFeatures.SyncToolSpecification.Builder()
                    .tool(tool)
                    .callHandler((transportContext, request) -> {
                        // Block on the async handler to get the result synchronously
                        return asyncHandler.apply(null, request).block();
                    })
                    .build();
        }

        // ==================== HELPER METHODS ====================

        private static Mat loadImageFromArgs(Map<String, Object> args) throws Exception {
            return loadImageFromArgs(args, "image_path", "image_url", "image_data", "input_key");
        }

        private static Mat loadImageFromArgs(Map<String, Object> args, String pathKey,
                                            String urlKey, String dataKey, String cacheKey) throws Exception {
            String inputKey = getStringArg(args, cacheKey);
            if (inputKey != null && !inputKey.isBlank() && cache.containsKey(inputKey)) {
                return cache.get(inputKey);
            }

            String imagePath = getStringArg(args, pathKey);
            String imageUrl = getStringArg(args, urlKey);
            String imageData = getStringArg(args, dataKey);

            return OpenCVImageProcessor.loadImage(imagePath, imageUrl, imageData);
        }

        private static String handleOutput(Map<String, Object> args, Mat mat, String message) throws Exception {
            String outputPath = getStringArg(args, "output_path");
            String outputKey = getStringArg(args, "output_key");

            if (outputPath != null && !outputPath.isBlank()) {
                OpenCVImageProcessor.saveImage(mat, outputPath);
                message += "\n- Saved to: " + outputPath;
            }

            if (outputKey != null && !outputKey.isBlank()) {
                cache.put(outputKey, mat);
                message += "\n- Cached with key: " + outputKey;
            } else if (outputPath == null || outputPath.isBlank()) {
                mat.release();
            }

            return message;
        }

        private static String getStringArg(Map<String, Object> args, String key) {
            return getStringArg(args, key, null);
        }

        private static String getStringArg(Map<String, Object> args, String key, String defaultValue) {
            Object value = args.get(key);
            if (value == null) return defaultValue;
            return value.toString();
        }

        private static int getIntArg(Map<String, Object> args, String key) {
            return getIntArg(args, key, 0);
        }

        private static int getIntArg(Map<String, Object> args, String key, int defaultValue) {
            Object value = args.get(key);
            if (value == null) return defaultValue;
            if (value instanceof Number) return ((Number) value).intValue();
            return Integer.parseInt(value.toString());
        }

        private static double getDoubleArg(Map<String, Object> args, String key, double defaultValue) {
            Object value = args.get(key);
            if (value == null) return defaultValue;
            if (value instanceof Number) return ((Number) value).doubleValue();
            return Double.parseDouble(value.toString());
        }

        private static float getFloatArg(Map<String, Object> args, String key, float defaultValue) {
            Object value = args.get(key);
            if (value == null) return defaultValue;
            if (value instanceof Number) return ((Number) value).floatValue();
            return Float.parseFloat(value.toString());
        }

        private static boolean getBooleanArg(Map<String, Object> args, String key, boolean defaultValue) {
            Object value = args.get(key);
            if (value == null) return defaultValue;
            if (value instanceof Boolean) return (Boolean) value;
            String str = value.toString().toLowerCase();
            return "true".equals(str) || "1".equals(str) || "yes".equals(str);
        }
    }
}
