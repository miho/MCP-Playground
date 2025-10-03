package com.imageprocessing.ui.model;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Registry of all available image processing tools.
 * Provides tool metadata based on the MCP server implementation.
 */
public class ToolRegistry {

    private static final List<ToolMetadata> TOOLS = new ArrayList<>();

    static {
        // Initialize all available tools from the MCP server
        TOOLS.add(createLoadImageTool());
        TOOLS.add(createResizeImageTool());
        TOOLS.add(createSegmentImageTool());
        TOOLS.add(createColorToGrayscaleTool());
        TOOLS.add(createFilterImageTool());
        TOOLS.add(createDenoiseImageTool());
        TOOLS.add(createBlurImageTool());
        TOOLS.add(createDetectContoursTool());
        TOOLS.add(createOutputSegmentedTool());
        TOOLS.add(createDisplayImageTool());
    }

    /**
     * Get all available tools.
     */
    public static List<ToolMetadata> getAllTools() {
        return Collections.unmodifiableList(TOOLS);
    }

    /**
     * Get tools by category.
     */
    public static List<ToolMetadata> getToolsByCategory(ToolMetadata.Category category) {
        return TOOLS.stream()
                .filter(tool -> tool.getCategory() == category)
                .collect(Collectors.toList());
    }

    /**
     * Get tool by name.
     */
    public static Optional<ToolMetadata> getToolByName(String name) {
        return TOOLS.stream()
                .filter(tool -> tool.getName().equals(name))
                .findFirst();
    }

    /**
     * Search tools by query string.
     */
    public static List<ToolMetadata> searchTools(String query) {
        if (query == null || query.trim().isEmpty()) {
            return getAllTools();
        }

        String lowerQuery = query.toLowerCase();
        return TOOLS.stream()
                .filter(tool -> tool.getName().toLowerCase().contains(lowerQuery) ||
                               tool.getDescription().toLowerCase().contains(lowerQuery))
                .collect(Collectors.toList());
    }

    // Tool factory methods based on MCP server definitions

    private static ToolMetadata createLoadImageTool() {
        List<ToolMetadata.ParameterDefinition> params = Arrays.asList(
            new ToolMetadata.ParameterDefinition("image_path", "Path to the image file",
                ToolMetadata.ParameterDefinition.Type.IMAGE_PATH, false, null),
            new ToolMetadata.ParameterDefinition("image_url", "URL to the image",
                ToolMetadata.ParameterDefinition.Type.STRING, false, null),
            new ToolMetadata.ParameterDefinition("image_data", "Base64 encoded image data",
                ToolMetadata.ParameterDefinition.Type.STRING, false, null),
            new ToolMetadata.ParameterDefinition("output_key", "Key to store the loaded image in cache",
                ToolMetadata.ParameterDefinition.Type.OUTPUT_KEY, false, null)
        );
        return new ToolMetadata("load_image",
            "Load an image from path, URL, or base64 data",
            ToolMetadata.Category.LOAD, params);
    }

    private static ToolMetadata createResizeImageTool() {
        List<ToolMetadata.ParameterDefinition> params = Arrays.asList(
            new ToolMetadata.ParameterDefinition("image_path", "Path to the image file",
                ToolMetadata.ParameterDefinition.Type.IMAGE_PATH, false, null),
            new ToolMetadata.ParameterDefinition("image_url", "URL to the image",
                ToolMetadata.ParameterDefinition.Type.STRING, false, null),
            new ToolMetadata.ParameterDefinition("image_data", "Base64 encoded image data",
                ToolMetadata.ParameterDefinition.Type.STRING, false, null),
            new ToolMetadata.ParameterDefinition("input_key", "Key to retrieve cached image",
                ToolMetadata.ParameterDefinition.Type.INPUT_KEY, false, null),
            new ToolMetadata.ParameterDefinition("width", "Target width in pixels",
                ToolMetadata.ParameterDefinition.Type.INTEGER, true, 800),
            new ToolMetadata.ParameterDefinition("height", "Target height in pixels",
                ToolMetadata.ParameterDefinition.Type.INTEGER, true, 600),
            new ToolMetadata.ParameterDefinition("interpolation", "Interpolation method",
                ToolMetadata.ParameterDefinition.Type.ENUM, false, "LINEAR",
                Arrays.asList("NEAREST", "LINEAR", "CUBIC", "AREA", "LANCZOS4")),
            new ToolMetadata.ParameterDefinition("output_path", "Path to save resized image",
                ToolMetadata.ParameterDefinition.Type.OUTPUT_PATH, false, null),
            new ToolMetadata.ParameterDefinition("output_key", "Key to store result in cache",
                ToolMetadata.ParameterDefinition.Type.OUTPUT_KEY, false, null)
        );
        return new ToolMetadata("resize_image",
            "Resize an image to specified dimensions",
            ToolMetadata.Category.TRANSFORM, params);
    }

    private static ToolMetadata createSegmentImageTool() {
        List<ToolMetadata.ParameterDefinition> params = Arrays.asList(
            new ToolMetadata.ParameterDefinition("image_path", "Path to the image file",
                ToolMetadata.ParameterDefinition.Type.IMAGE_PATH, false, null),
            new ToolMetadata.ParameterDefinition("image_url", "URL to the image",
                ToolMetadata.ParameterDefinition.Type.STRING, false, null),
            new ToolMetadata.ParameterDefinition("image_data", "Base64 encoded image data",
                ToolMetadata.ParameterDefinition.Type.STRING, false, null),
            new ToolMetadata.ParameterDefinition("input_key", "Key to retrieve cached image",
                ToolMetadata.ParameterDefinition.Type.INPUT_KEY, false, null),
            new ToolMetadata.ParameterDefinition("threshold", "Threshold value (0-255), 0 for Otsu",
                ToolMetadata.ParameterDefinition.Type.DOUBLE, false, 127.0),
            new ToolMetadata.ParameterDefinition("threshold_type", "Threshold type",
                ToolMetadata.ParameterDefinition.Type.ENUM, false, "BINARY",
                Arrays.asList("BINARY", "BINARY_INV", "TRUNC", "TOZERO", "TOZERO_INV", "OTSU")),
            new ToolMetadata.ParameterDefinition("invert", "Invert the segmentation result",
                ToolMetadata.ParameterDefinition.Type.BOOLEAN, false, false),
            new ToolMetadata.ParameterDefinition("output_path", "Path to save segmented image",
                ToolMetadata.ParameterDefinition.Type.OUTPUT_PATH, false, null),
            new ToolMetadata.ParameterDefinition("output_key", "Key to store result in cache",
                ToolMetadata.ParameterDefinition.Type.OUTPUT_KEY, false, null)
        );
        return new ToolMetadata("segment_image",
            "Segment an image using threshold-based methods",
            ToolMetadata.Category.ANALYSIS, params);
    }

    private static ToolMetadata createColorToGrayscaleTool() {
        List<ToolMetadata.ParameterDefinition> params = Arrays.asList(
            new ToolMetadata.ParameterDefinition("image_path", "Path to the image file",
                ToolMetadata.ParameterDefinition.Type.IMAGE_PATH, false, null),
            new ToolMetadata.ParameterDefinition("image_url", "URL to the image",
                ToolMetadata.ParameterDefinition.Type.STRING, false, null),
            new ToolMetadata.ParameterDefinition("image_data", "Base64 encoded image data",
                ToolMetadata.ParameterDefinition.Type.STRING, false, null),
            new ToolMetadata.ParameterDefinition("input_key", "Key to retrieve cached image",
                ToolMetadata.ParameterDefinition.Type.INPUT_KEY, false, null),
            new ToolMetadata.ParameterDefinition("output_path", "Path to save grayscale image",
                ToolMetadata.ParameterDefinition.Type.OUTPUT_PATH, false, null),
            new ToolMetadata.ParameterDefinition("output_key", "Key to store result in cache",
                ToolMetadata.ParameterDefinition.Type.OUTPUT_KEY, false, null)
        );
        return new ToolMetadata("color_to_grayscale",
            "Convert a color image to grayscale",
            ToolMetadata.Category.TRANSFORM, params);
    }

    private static ToolMetadata createFilterImageTool() {
        List<ToolMetadata.ParameterDefinition> params = Arrays.asList(
            new ToolMetadata.ParameterDefinition("image_path", "Path to the image file",
                ToolMetadata.ParameterDefinition.Type.IMAGE_PATH, false, null),
            new ToolMetadata.ParameterDefinition("image_url", "URL to the image",
                ToolMetadata.ParameterDefinition.Type.STRING, false, null),
            new ToolMetadata.ParameterDefinition("image_data", "Base64 encoded image data",
                ToolMetadata.ParameterDefinition.Type.STRING, false, null),
            new ToolMetadata.ParameterDefinition("input_key", "Key to retrieve cached image",
                ToolMetadata.ParameterDefinition.Type.INPUT_KEY, false, null),
            new ToolMetadata.ParameterDefinition("filter_type", "Filter type",
                ToolMetadata.ParameterDefinition.Type.ENUM, false, "GAUSSIAN",
                Arrays.asList("GAUSSIAN", "MEDIAN", "BILATERAL")),
            new ToolMetadata.ParameterDefinition("kernel_size", "Kernel size (must be odd)",
                ToolMetadata.ParameterDefinition.Type.INTEGER, false, 5),
            new ToolMetadata.ParameterDefinition("sigma_x", "Sigma X for Gaussian/Bilateral",
                ToolMetadata.ParameterDefinition.Type.DOUBLE, false, 1.0),
            new ToolMetadata.ParameterDefinition("sigma_color", "Sigma Color for Bilateral",
                ToolMetadata.ParameterDefinition.Type.DOUBLE, false, 75.0),
            new ToolMetadata.ParameterDefinition("sigma_space", "Sigma Space for Bilateral",
                ToolMetadata.ParameterDefinition.Type.DOUBLE, false, 75.0),
            new ToolMetadata.ParameterDefinition("output_path", "Path to save filtered image",
                ToolMetadata.ParameterDefinition.Type.OUTPUT_PATH, false, null),
            new ToolMetadata.ParameterDefinition("output_key", "Key to store result in cache",
                ToolMetadata.ParameterDefinition.Type.OUTPUT_KEY, false, null)
        );
        return new ToolMetadata("filter_image",
            "Apply various filters to an image",
            ToolMetadata.Category.FILTER, params);
    }

    private static ToolMetadata createDenoiseImageTool() {
        List<ToolMetadata.ParameterDefinition> params = Arrays.asList(
            new ToolMetadata.ParameterDefinition("image_path", "Path to the image file",
                ToolMetadata.ParameterDefinition.Type.IMAGE_PATH, false, null),
            new ToolMetadata.ParameterDefinition("image_url", "URL to the image",
                ToolMetadata.ParameterDefinition.Type.STRING, false, null),
            new ToolMetadata.ParameterDefinition("image_data", "Base64 encoded image data",
                ToolMetadata.ParameterDefinition.Type.STRING, false, null),
            new ToolMetadata.ParameterDefinition("input_key", "Key to retrieve cached image",
                ToolMetadata.ParameterDefinition.Type.INPUT_KEY, false, null),
            new ToolMetadata.ParameterDefinition("h", "Filter strength",
                ToolMetadata.ParameterDefinition.Type.FLOAT, false, 10.0f),
            new ToolMetadata.ParameterDefinition("template_window_size", "Template patch size (odd)",
                ToolMetadata.ParameterDefinition.Type.INTEGER, false, 7),
            new ToolMetadata.ParameterDefinition("search_window_size", "Search area size (odd)",
                ToolMetadata.ParameterDefinition.Type.INTEGER, false, 21),
            new ToolMetadata.ParameterDefinition("output_path", "Path to save denoised image",
                ToolMetadata.ParameterDefinition.Type.OUTPUT_PATH, false, null),
            new ToolMetadata.ParameterDefinition("output_key", "Key to store result in cache",
                ToolMetadata.ParameterDefinition.Type.OUTPUT_KEY, false, null)
        );
        return new ToolMetadata("denoise_image",
            "Denoise an image using Non-Local Means",
            ToolMetadata.Category.FILTER, params);
    }

    private static ToolMetadata createBlurImageTool() {
        List<ToolMetadata.ParameterDefinition> params = Arrays.asList(
            new ToolMetadata.ParameterDefinition("image_path", "Path to the image file",
                ToolMetadata.ParameterDefinition.Type.IMAGE_PATH, false, null),
            new ToolMetadata.ParameterDefinition("image_url", "URL to the image",
                ToolMetadata.ParameterDefinition.Type.STRING, false, null),
            new ToolMetadata.ParameterDefinition("image_data", "Base64 encoded image data",
                ToolMetadata.ParameterDefinition.Type.STRING, false, null),
            new ToolMetadata.ParameterDefinition("input_key", "Key to retrieve cached image",
                ToolMetadata.ParameterDefinition.Type.INPUT_KEY, false, null),
            new ToolMetadata.ParameterDefinition("blur_type", "Blur type",
                ToolMetadata.ParameterDefinition.Type.ENUM, false, "GAUSSIAN",
                Arrays.asList("GAUSSIAN", "MOTION", "BOX", "AVERAGE")),
            new ToolMetadata.ParameterDefinition("kernel_size", "Kernel size (must be odd)",
                ToolMetadata.ParameterDefinition.Type.INTEGER, false, 5),
            new ToolMetadata.ParameterDefinition("angle", "Angle for motion blur (degrees)",
                ToolMetadata.ParameterDefinition.Type.DOUBLE, false, 0.0),
            new ToolMetadata.ParameterDefinition("output_path", "Path to save blurred image",
                ToolMetadata.ParameterDefinition.Type.OUTPUT_PATH, false, null),
            new ToolMetadata.ParameterDefinition("output_key", "Key to store result in cache",
                ToolMetadata.ParameterDefinition.Type.OUTPUT_KEY, false, null)
        );
        return new ToolMetadata("blur_image",
            "Apply various blur effects to an image",
            ToolMetadata.Category.FILTER, params);
    }

    private static ToolMetadata createDetectContoursTool() {
        List<ToolMetadata.ParameterDefinition> params = Arrays.asList(
            new ToolMetadata.ParameterDefinition("image_path", "Path to the image file",
                ToolMetadata.ParameterDefinition.Type.IMAGE_PATH, false, null),
            new ToolMetadata.ParameterDefinition("image_url", "URL to the image",
                ToolMetadata.ParameterDefinition.Type.STRING, false, null),
            new ToolMetadata.ParameterDefinition("image_data", "Base64 encoded image data",
                ToolMetadata.ParameterDefinition.Type.STRING, false, null),
            new ToolMetadata.ParameterDefinition("input_key", "Key to retrieve cached image",
                ToolMetadata.ParameterDefinition.Type.INPUT_KEY, false, null),
            new ToolMetadata.ParameterDefinition("min_area", "Minimum contour area",
                ToolMetadata.ParameterDefinition.Type.DOUBLE, false, 100.0),
            new ToolMetadata.ParameterDefinition("max_area", "Maximum contour area (-1 for no limit)",
                ToolMetadata.ParameterDefinition.Type.DOUBLE, false, -1.0),
            new ToolMetadata.ParameterDefinition("min_circularity", "Minimum circularity 0-1",
                ToolMetadata.ParameterDefinition.Type.DOUBLE, false, 0.0),
            new ToolMetadata.ParameterDefinition("max_circularity", "Maximum circularity 0-1",
                ToolMetadata.ParameterDefinition.Type.DOUBLE, false, 1.0),
            new ToolMetadata.ParameterDefinition("output_path", "Save visualization image",
                ToolMetadata.ParameterDefinition.Type.OUTPUT_PATH, false, null),
            new ToolMetadata.ParameterDefinition("output_key", "Store visualization in cache",
                ToolMetadata.ParameterDefinition.Type.OUTPUT_KEY, false, null),
            new ToolMetadata.ParameterDefinition("output_csv_path", "Save blob data as CSV file",
                ToolMetadata.ParameterDefinition.Type.OUTPUT_PATH, false, null),
            new ToolMetadata.ParameterDefinition("output_csv_key", "Store blob CSV in cache",
                ToolMetadata.ParameterDefinition.Type.OUTPUT_KEY, false, null)
        );
        return new ToolMetadata("detect_contours",
            "Detect and filter contours by size and shape",
            ToolMetadata.Category.ANALYSIS, params);
    }

    private static ToolMetadata createOutputSegmentedTool() {
        List<ToolMetadata.ParameterDefinition> params = Arrays.asList(
            new ToolMetadata.ParameterDefinition("image_path", "Source image path",
                ToolMetadata.ParameterDefinition.Type.IMAGE_PATH, false, null),
            new ToolMetadata.ParameterDefinition("image_url", "URL to the image",
                ToolMetadata.ParameterDefinition.Type.STRING, false, null),
            new ToolMetadata.ParameterDefinition("image_data", "Base64 encoded image data",
                ToolMetadata.ParameterDefinition.Type.STRING, false, null),
            new ToolMetadata.ParameterDefinition("source_key", "Cached source image key",
                ToolMetadata.ParameterDefinition.Type.INPUT_KEY, false, null),
            new ToolMetadata.ParameterDefinition("mask_path", "Binary mask image path",
                ToolMetadata.ParameterDefinition.Type.IMAGE_PATH, false, null),
            new ToolMetadata.ParameterDefinition("mask_key", "Cached mask key",
                ToolMetadata.ParameterDefinition.Type.INPUT_KEY, false, null),
            new ToolMetadata.ParameterDefinition("output_path", "Base path for output files",
                ToolMetadata.ParameterDefinition.Type.OUTPUT_PATH, true, "./segmented_output.png")
        );
        return new ToolMetadata("output_segmented",
            "Extract and save segmented regions from an image",
            ToolMetadata.Category.OUTPUT, params);
    }

    private static ToolMetadata createDisplayImageTool() {
        List<ToolMetadata.ParameterDefinition> params = Arrays.asList(
            new ToolMetadata.ParameterDefinition("image_path", "Path to the image file",
                ToolMetadata.ParameterDefinition.Type.IMAGE_PATH, false, null),
            new ToolMetadata.ParameterDefinition("image_url", "URL to the image",
                ToolMetadata.ParameterDefinition.Type.STRING, false, null),
            new ToolMetadata.ParameterDefinition("image_data", "Base64 encoded image data",
                ToolMetadata.ParameterDefinition.Type.STRING, false, null),
            new ToolMetadata.ParameterDefinition("input_key", "Key to retrieve cached image",
                ToolMetadata.ParameterDefinition.Type.INPUT_KEY, false, null)
        );
        return new ToolMetadata("display_image",
            "Display an image as base64 PNG",
            ToolMetadata.Category.OUTPUT, params);
    }
}
