package com.imageprocessing.execution;

import com.imageprocessing.server.OpenCVImageProcessor;
import com.imageprocessing.server.IntermediateResultCache;
import com.imageprocessing.server.TextResultCache;
import com.imageprocessing.server.BlobInfo;
import com.imageprocessing.ui.model.ToolInstance;
import javafx.collections.ObservableList;
import org.opencv.core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Execute tools directly by calling OpenCVImageProcessor (no HTTP/RPC overhead).
 * This is used by the JavaFX UI for internal tool execution.
 * External MCP clients still use the embedded MCP server for remote access.
 */
public class DirectToolExecutor {

    private static final Logger logger = LoggerFactory.getLogger(DirectToolExecutor.class);

    private final OpenCVImageProcessor processor;
    private final IntermediateResultCache cache;
    private final ExecutorService executor;
    private volatile boolean cancelled = false;
    private Consumer<String> displayImageCallback;

    public DirectToolExecutor(OpenCVImageProcessor processor, IntermediateResultCache cache) {
        this.processor = processor;
        this.cache = cache;
        // Use fixed thread pool with 2 threads to avoid deadlock
        // One thread for pipeline orchestration, one for tool execution
        this.executor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "DirectToolExecutor");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Set callback for displaying images.
     * The callback receives the cache key of the image to display.
     */
    public void setDisplayImageCallback(Consumer<String> callback) {
        this.displayImageCallback = callback;
    }

    /**
     * Execute a single tool directly.
     */
    public CompletableFuture<ToolResult> executeTool(ToolInstance toolInstance) {
        return CompletableFuture.supplyAsync(() -> {
            if (cancelled) {
                logger.debug("Tool execution cancelled before start: {}", toolInstance.getMetadata().getName());
                toolInstance.setStatus(ToolInstance.Status.ERROR);
                toolInstance.setResult("Cancelled");
                return ToolResult.cancelled();
            }

            String toolName = toolInstance.getMetadata().getName();
            Map<String, Object> params = toolInstance.getParameterValues();

            try {
                toolInstance.setStatus(ToolInstance.Status.RUNNING);
                logger.info("Executing tool: {} with params: {}", toolName, params);

                Object result = executeTool(toolName, params);

                // Check cancellation after tool execution
                if (cancelled) {
                    logger.info("Tool execution cancelled after completion: {}", toolName);
                    toolInstance.setStatus(ToolInstance.Status.ERROR);
                    toolInstance.setResult("Cancelled");
                    return ToolResult.cancelled();
                }

                toolInstance.setStatus(ToolInstance.Status.COMPLETED);
                String resultMessage = result != null ? result.toString() : "Success";
                toolInstance.setResult(resultMessage);

                logger.info("Tool completed successfully: {} - {}", toolName, resultMessage);
                return ToolResult.success(resultMessage);

            } catch (Exception e) {
                logger.error("Tool execution failed: " + toolName + " - " + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
                toolInstance.setStatus(ToolInstance.Status.ERROR);
                String errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                toolInstance.setErrorMessage(errorMessage);
                toolInstance.setResult("Error: " + errorMessage);
                return ToolResult.error(errorMessage);
            }
        }, executor);
    }

    /**
     * Execute entire pipeline sequentially.
     */
    public CompletableFuture<PipelineResult> executePipeline(
            ObservableList<ToolInstance> tools,
            Consumer<ToolInstance> progressCallback
    ) {
        logger.info("DirectToolExecutor.executePipeline() called with {} tools", tools.size());
        return CompletableFuture.supplyAsync(() -> {
            cancelled = false;
            PipelineResult result = new PipelineResult();

            logger.info("Starting pipeline execution loop");
            for (int i = 0; i < tools.size(); i++) {
                // Check cancellation at the start of each iteration
                if (cancelled) {
                    logger.info("Pipeline cancelled at tool {}", (i + 1));
                    result.setCancelled(true);
                    break;
                }

                ToolInstance tool = tools.get(i);
                logger.info("Executing tool {}/{}: {}", (i + 1), tools.size(), tool.getName());

                try {
                    // Execute tool synchronously and wait for completion
                    executeTool(tool).thenAccept(toolResult -> {
                        logger.info("Tool {} completed with status: {}", tool.getName(), tool.getStatus());
                        if (progressCallback != null) {
                            progressCallback.accept(tool);
                        }
                    }).join(); // Wait for each tool to complete
                } catch (Exception e) {
                    logger.error("Exception during tool execution: " + tool.getName(), e);
                    tool.setStatus(ToolInstance.Status.ERROR);
                    tool.setErrorMessage("Execution exception: " + e.getMessage());
                    result.addError(tool.getName() + ": " + e.getMessage());
                }

                // Check cancellation immediately after tool completion
                if (cancelled) {
                    logger.info("Pipeline cancelled after tool {}", tool.getName());
                    result.setCancelled(true);
                    break;
                }

                if (tool.getStatus() == ToolInstance.Status.ERROR) {
                    logger.warn("Tool {} failed: {}", tool.getName(), tool.getErrorMessage());
                    result.addError(tool.getName() + ": " + tool.getErrorMessage());
                    // Don't break on error - let it continue or check if we should stop on first error
                }
            }

            logger.info("Pipeline execution completed. Total tools: {}, Errors: {}, Cancelled: {}",
                    tools.size(), result.hasErrors(), result.isCancelled());
            return result;
        }, executor);
    }

    /**
     * Cancel ongoing pipeline execution.
     */
    public void cancel() {
        cancelled = true;
    }

    /**
     * Shutdown the executor service.
     */
    public void shutdown() {
        executor.shutdown();
    }

    /**
     * Route tool execution to the appropriate processor method.
     */
    private Object executeTool(String toolName, Map<String, Object> params) throws Exception {
        return switch (toolName) {
            case "load_image" -> executeLoadImage(params);
            case "resize_image" -> executeResize(params);
            case "segment_image" -> executeSegment(params);
            case "color_to_grayscale" -> executeColorToGrayscale(params);
            case "filter_image" -> executeFilter(params);
            case "denoise_image" -> executeDenoise(params);
            case "blur_image" -> executeBlur(params);
            case "detect_contours" -> executeDetectContours(params);
            case "output_segmented" -> executeOutputSegmented(params);
            case "display_image" -> executeDisplayImage(params);
            default -> throw new IllegalArgumentException("Unknown tool: " + toolName);
        };
    }

    // ==================== TOOL IMPLEMENTATIONS ====================

    private Object executeLoadImage(Map<String, Object> params) throws Exception {
        String imagePath = getStringParam(params, "image_path");
        String imageUrl = getStringParam(params, "image_url");
        String imageData = getStringParam(params, "image_data");
        String resultKey = getStringParam(params, "result_key");

        Mat image = OpenCVImageProcessor.loadImage(imagePath, imageUrl, imageData);

        if (resultKey != null && !resultKey.isBlank()) {
            cache.put(resultKey, image);
        } else {
            image.release();
        }

        return String.format("Image loaded: %dx%d, channels: %d",
                image.cols(), image.rows(), image.channels());
    }

    private Object executeResize(Map<String, Object> params) throws Exception {
        Mat image = getInputImage(params);
        int width = getIntParam(params, "width");
        int height = getIntParam(params, "height");
        String interpolation = getStringParam(params, "interpolation", "LINEAR");

        Mat resized = OpenCVImageProcessor.resize(image, width, height, interpolation);

        storeOutput(params, resized);
        releaseInputIfNotCached(params, image);

        return String.format("Resized to %dx%d using %s interpolation", width, height, interpolation);
    }

    private Object executeSegment(Map<String, Object> params) throws Exception {
        Mat image = getInputImage(params);
        double threshold = getDoubleParam(params, "threshold", 127.0);
        String thresholdType = getStringParam(params, "threshold_type", "BINARY");
        boolean invert = getBooleanParam(params, "invert", false);

        Mat segmented = OpenCVImageProcessor.segment(image, threshold, thresholdType, invert);

        storeOutput(params, segmented);
        releaseInputIfNotCached(params, image);

        String invertStr = invert ? ", inverted" : "";
        return String.format("Segmented using threshold %.1f, type: %s%s", threshold, thresholdType, invertStr);
    }

    private Object executeColorToGrayscale(Map<String, Object> params) throws Exception {
        Mat image = getInputImage(params);
        Mat gray = OpenCVImageProcessor.colorToGrayscale(image);

        storeOutput(params, gray);
        releaseInputIfNotCached(params, image);

        return String.format("Converted to grayscale (channels: %d -> 1)", image.channels());
    }

    private Object executeFilter(Map<String, Object> params) throws Exception {
        Mat image = getInputImage(params);
        String filterType = getStringParam(params, "filter_type", "GAUSSIAN");
        int kernelSize = getIntParam(params, "kernel_size", 5);
        double sigmaX = getDoubleParam(params, "sigma_x", 1.0);
        double sigmaColor = getDoubleParam(params, "sigma_color", 75.0);
        double sigmaSpace = getDoubleParam(params, "sigma_space", 75.0);

        Mat filtered = OpenCVImageProcessor.filter(image, filterType, kernelSize,
                sigmaX, sigmaColor, sigmaSpace);

        storeOutput(params, filtered);
        releaseInputIfNotCached(params, image);

        return String.format("Applied %s filter with kernel size %d", filterType, kernelSize);
    }

    private Object executeDenoise(Map<String, Object> params) throws Exception {
        Mat image = getInputImage(params);
        float h = getFloatParam(params, "h", 10.0f);
        int templateWindowSize = getIntParam(params, "template_window_size", 7);
        int searchWindowSize = getIntParam(params, "search_window_size", 21);

        Mat denoised = OpenCVImageProcessor.denoise(image, h, templateWindowSize, searchWindowSize);

        storeOutput(params, denoised);
        releaseInputIfNotCached(params, image);

        return String.format("Denoised with h=%.1f, template=%d, search=%d",
                h, templateWindowSize, searchWindowSize);
    }

    private Object executeBlur(Map<String, Object> params) throws Exception {
        Mat image = getInputImage(params);
        String blurType = getStringParam(params, "blur_type", "GAUSSIAN");
        int kernelSize = getIntParam(params, "kernel_size", 5);
        double angle = getDoubleParam(params, "angle", 0.0);

        Mat blurred = OpenCVImageProcessor.blur(image, blurType, kernelSize, angle);

        storeOutput(params, blurred);
        releaseInputIfNotCached(params, image);

        return String.format("Applied %s blur with kernel size %d", blurType, kernelSize);
    }

    private Object executeDetectContours(Map<String, Object> params) throws Exception {
        Mat image = getInputImage(params);
        double minArea = getDoubleParam(params, "min_area", 100.0);
        double maxArea = getDoubleParam(params, "max_area", -1.0);
        double minCircularity = getDoubleParam(params, "min_circularity", 0.0);
        double maxCircularity = getDoubleParam(params, "max_circularity", 1.0);

        Map<String, Object> result = OpenCVImageProcessor.detectContours(
                image, minArea, maxArea, minCircularity, maxCircularity);

        Mat visualization = (Mat) result.get("visualization");
        int count = (int) result.get("count");
        @SuppressWarnings("unchecked")
        List<BlobInfo> blobs = (List<BlobInfo>) result.get("blobs");

        // Generate CSV from blob data
        StringBuilder csv = new StringBuilder();
        csv.append(BlobInfo.getCsvHeader()).append("\n");
        for (BlobInfo blob : blobs) {
            csv.append(blob.toCsvRow()).append("\n");
        }
        String csvData = csv.toString();

        // Save CSV to file if requested
        String csvPath = getStringParam(params, "output_csv_path");
        if (csvPath != null && !csvPath.isBlank()) {
            java.nio.file.Files.writeString(java.nio.file.Path.of(csvPath), csvData);
        }

        storeOutput(params, visualization);
        releaseInputIfNotCached(params, image);

        return String.format("Detected %d contours (min area: %.1f, circularity: %.2f-%.2f)",
                count, minArea, minCircularity, maxCircularity);
    }

    private Object executeOutputSegmented(Map<String, Object> params) throws Exception {
        Mat sourceImage = getInputImageWithKey(params, "source_key", "image_path", "image_url", "image_data");
        Mat mask = getInputImageWithKey(params, "mask_key", "mask_path", null, null);
        String outputPath = getStringParam(params, "output_path", "./segmented_output.png");

        List<String> savedFiles = OpenCVImageProcessor.outputSegmented(sourceImage, mask, outputPath);

        releaseInputIfNotCached(params, sourceImage, "source_key");
        releaseInputIfNotCached(params, mask, "mask_key");

        return String.format("Extracted %d regions, saved to %s", savedFiles.size(), outputPath);
    }

    private Object executeDisplayImage(Map<String, Object> params) throws Exception {
        Mat image = getInputImage(params);

        // Get the cache key if the image is cached
        String cacheKey = getStringParam(params, "result_key");

        // If we have a callback and cache key, trigger the display
        if (displayImageCallback != null && cacheKey != null && !cacheKey.isBlank()) {
            displayImageCallback.accept(cacheKey);
            logger.debug("Display image callback invoked for key: {}", cacheKey);
        } else if (displayImageCallback == null) {
            logger.warn("Display image callback not set - image will not be displayed in UI");
        } else {
            logger.warn("No cache key provided for display_image - cannot display image");
        }

        releaseInputIfNotCached(params, image);

        return String.format("Image displayed: %dx%d", image.cols(), image.rows());
    }

    // ==================== HELPER METHODS ====================

    private Mat getInputImage(Map<String, Object> params) throws Exception {
        return getInputImageWithKey(params, "result_key", "image_path", "image_url", "image_data");
    }

    private Mat getInputImageWithKey(Map<String, Object> params,
                                     String cacheKey,
                                     String pathKey,
                                     String urlKey,
                                     String dataKey) throws Exception {
        // Try cache first
        String key = getStringParam(params, cacheKey);
        if (key != null && !key.isBlank() && cache.containsKey(key)) {
            return cache.get(key);
        }

        // Try loading from path/url/data
        String imagePath = pathKey != null ? getStringParam(params, pathKey) : null;
        String imageUrl = urlKey != null ? getStringParam(params, urlKey) : null;
        String imageData = dataKey != null ? getStringParam(params, dataKey) : null;

        if (imagePath != null || imageUrl != null || imageData != null) {
            return OpenCVImageProcessor.loadImage(imagePath, imageUrl, imageData);
        }

        throw new IllegalArgumentException("No input image specified (cache key, path, url, or data)");
    }

    private void storeOutput(Map<String, Object> params, Mat result) throws Exception {
        String outputPath = getStringParam(params, "output_path");
        String outputKey = getStringParam(params, "output_key");

        if (outputPath != null && !outputPath.isBlank()) {
            OpenCVImageProcessor.saveImage(result, outputPath);
        }

        if (outputKey != null && !outputKey.isBlank()) {
            cache.put(outputKey, result);
        } else if (outputPath == null || outputPath.isBlank()) {
            // No output storage requested, release the Mat
            result.release();
        }
    }

    private void releaseInputIfNotCached(Map<String, Object> params, Mat image) {
        releaseInputIfNotCached(params, image, "result_key");
    }

    private void releaseInputIfNotCached(Map<String, Object> params, Mat image, String cacheKey) {
        String key = getStringParam(params, cacheKey);
        if (key == null || key.isBlank() || !cache.containsKey(key)) {
            if (image != null) {
                image.release();
            }
        }
    }

    private String getStringParam(Map<String, Object> params, String key) {
        return getStringParam(params, key, null);
    }

    private String getStringParam(Map<String, Object> params, String key, String defaultValue) {
        Object value = params.get(key);
        if (value == null) return defaultValue;
        String str = value.toString();
        return str.isBlank() ? defaultValue : str;
    }

    private int getIntParam(Map<String, Object> params, String key) {
        return getIntParam(params, key, 0);
    }

    private int getIntParam(Map<String, Object> params, String key, int defaultValue) {
        Object value = params.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private double getDoubleParam(Map<String, Object> params, String key, double defaultValue) {
        Object value = params.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private float getFloatParam(Map<String, Object> params, String key, float defaultValue) {
        Object value = params.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).floatValue();
        try {
            return Float.parseFloat(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private boolean getBooleanParam(Map<String, Object> params, String key, boolean defaultValue) {
        Object value = params.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Boolean) return (Boolean) value;
        String str = value.toString().toLowerCase();
        return "true".equals(str) || "1".equals(str) || "yes".equals(str);
    }

    // ==================== RESULT CLASSES ====================

    /**
     * Result of executing a single tool.
     */
    public static class ToolResult {
        private final boolean success;
        private final boolean cancelled;
        private final String message;

        private ToolResult(boolean success, boolean cancelled, String message) {
            this.success = success;
            this.cancelled = cancelled;
            this.message = message;
        }

        public static ToolResult success(String message) {
            return new ToolResult(true, false, message);
        }

        public static ToolResult error(String message) {
            return new ToolResult(false, false, message);
        }

        public static ToolResult cancelled() {
            return new ToolResult(false, true, "Cancelled");
        }

        public boolean isSuccess() {
            return success;
        }

        public boolean isCancelled() {
            return cancelled;
        }

        public String getMessage() {
            return message;
        }
    }

    /**
     * Result of executing a pipeline.
     */
}
