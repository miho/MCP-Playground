package com.imageprocessing.server;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.photo.Photo;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URL;
import java.util.*;

/**
 * OpenCV-based image processing operations.
 * Handles conversion between BufferedImage and Mat, and provides
 * various image processing operations.
 */
public class OpenCVImageProcessor {

    /**
     * Load an image from various sources (path, URL, base64).
     * @param imagePath File path to image (optional)
     * @param imageUrl URL to image (optional)
     * @param imageData Base64 encoded image data (optional)
     * @return OpenCV Mat containing the image
     * @throws Exception if image cannot be loaded
     */
    public static Mat loadImage(String imagePath, String imageUrl, String imageData) throws Exception {
        BufferedImage bufferedImage = null;

        if (imageData != null && !imageData.isBlank()) {
            // Load from base64 data
            byte[] imageBytes = Base64.getDecoder().decode(imageData);
            bufferedImage = ImageIO.read(new ByteArrayInputStream(imageBytes));
        } else if (imageUrl != null && !imageUrl.isBlank()) {
            // Load from URL
            bufferedImage = ImageIO.read(new URL(imageUrl));
        } else if (imagePath != null && !imagePath.isBlank()) {
            // Try OpenCV first for better format support
            Mat mat = Imgcodecs.imread(imagePath, Imgcodecs.IMREAD_UNCHANGED);
            if (!mat.empty()) {
                return mat;
            }
            // Fall back to ImageIO
            bufferedImage = ImageIO.read(new File(imagePath));
        } else {
            throw new IllegalArgumentException("No image source provided (path, url, or data)");
        }

        if (bufferedImage == null) {
            throw new IllegalArgumentException("Failed to load image from provided source");
        }

        return bufferedImageToMat(bufferedImage);
    }

    /**
     * Convert BufferedImage to OpenCV Mat.
     */
    public static Mat bufferedImageToMat(BufferedImage image) {
        // Convert to TYPE_3BYTE_BGR if needed
        BufferedImage convertedImage = image;
        if (image.getType() != BufferedImage.TYPE_3BYTE_BGR &&
            image.getType() != BufferedImage.TYPE_BYTE_GRAY) {
            convertedImage = new BufferedImage(image.getWidth(), image.getHeight(),
                                               BufferedImage.TYPE_3BYTE_BGR);
            java.awt.Graphics2D g = convertedImage.createGraphics();
            g.drawImage(image, 0, 0, null);
            g.dispose();
        }

        byte[] pixels = ((DataBufferByte) convertedImage.getRaster().getDataBuffer()).getData();
        Mat mat;

        if (convertedImage.getType() == BufferedImage.TYPE_BYTE_GRAY) {
            mat = new Mat(convertedImage.getHeight(), convertedImage.getWidth(), CvType.CV_8UC1);
        } else {
            mat = new Mat(convertedImage.getHeight(), convertedImage.getWidth(), CvType.CV_8UC3);
        }

        mat.put(0, 0, pixels);
        return mat;
    }

    /**
     * Convert OpenCV Mat to BufferedImage.
     */
    public static BufferedImage matToBufferedImage(Mat mat) {
        int type = BufferedImage.TYPE_BYTE_GRAY;
        if (mat.channels() > 1) {
            type = BufferedImage.TYPE_3BYTE_BGR;
        }

        int bufferSize = mat.channels() * mat.cols() * mat.rows();
        byte[] buffer = new byte[bufferSize];
        mat.get(0, 0, buffer);

        BufferedImage image = new BufferedImage(mat.cols(), mat.rows(), type);
        final byte[] targetPixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        System.arraycopy(buffer, 0, targetPixels, 0, buffer.length);

        return image;
    }

    /**
     * Resize an image to specified dimensions.
     * @param src Source image
     * @param width Target width
     * @param height Target height
     * @param interpolation Interpolation method (NEAREST, LINEAR, CUBIC, AREA, LANCZOS4)
     * @return Resized image
     */
    public static Mat resize(Mat src, int width, int height, String interpolation) {
        Mat dst = new Mat();
        Size size = new Size(width, height);

        int interpMethod = switch (interpolation.toUpperCase()) {
            case "NEAREST" -> Imgproc.INTER_NEAREST;
            case "LINEAR" -> Imgproc.INTER_LINEAR;
            case "CUBIC" -> Imgproc.INTER_CUBIC;
            case "AREA" -> Imgproc.INTER_AREA;
            case "LANCZOS4" -> Imgproc.INTER_LANCZOS4;
            default -> Imgproc.INTER_LINEAR;
        };

        Imgproc.resize(src, dst, size, 0, 0, interpMethod);
        return dst;
    }

    /**
     * Segment an image using thresholding.
     * @param src Source image (should be grayscale)
     * @param threshold Threshold value (0-255), or -1 for Otsu
     * @param thresholdType Type: BINARY, BINARY_INV, TRUNC, TOZERO, TOZERO_INV, OTSU
     * @param invert Invert the segmentation result (apply bitwise NOT)
     * @return Segmented image
     */
    public static Mat segment(Mat src, double threshold, String thresholdType, boolean invert) {
        Mat gray = src;
        if (src.channels() > 1) {
            gray = new Mat();
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY);
        }

        Mat dst = new Mat();
        int type = switch (thresholdType.toUpperCase()) {
            case "BINARY" -> Imgproc.THRESH_BINARY;
            case "BINARY_INV" -> Imgproc.THRESH_BINARY_INV;
            case "TRUNC" -> Imgproc.THRESH_TRUNC;
            case "TOZERO" -> Imgproc.THRESH_TOZERO;
            case "TOZERO_INV" -> Imgproc.THRESH_TOZERO_INV;
            case "OTSU" -> Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU;
            default -> Imgproc.THRESH_BINARY;
        };

        if (thresholdType.equalsIgnoreCase("OTSU")) {
            Imgproc.threshold(gray, dst, 0, 255, type);
        } else {
            Imgproc.threshold(gray, dst, threshold, 255, type);
        }

        // Apply inversion if requested
        if (invert) {
            Core.bitwise_not(dst, dst);
        }

        if (src.channels() > 1 && gray != src) {
            gray.release();
        }

        return dst;
    }

    /**
     * Convert color image to grayscale.
     * @param src Source image
     * @return Grayscale image
     */
    public static Mat colorToGrayscale(Mat src) {
        if (src.channels() == 1) {
            return src.clone();
        }

        Mat gray = new Mat();
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY);
        return gray;
    }

    /**
     * Apply various filters to an image.
     * @param src Source image
     * @param filterType Type: GAUSSIAN, MEDIAN, BILATERAL
     * @param kernelSize Kernel size (must be odd)
     * @param sigmaX Sigma X (for Gaussian and Bilateral)
     * @param sigmaColor Sigma Color (for Bilateral)
     * @param sigmaSpace Sigma Space (for Bilateral)
     * @return Filtered image
     */
    public static Mat filter(Mat src, String filterType, int kernelSize,
                            double sigmaX, double sigmaColor, double sigmaSpace) {
        Mat dst = new Mat();

        // Ensure kernel size is odd
        if (kernelSize % 2 == 0) {
            kernelSize++;
        }

        switch (filterType.toUpperCase()) {
            case "GAUSSIAN" -> Imgproc.GaussianBlur(src, dst, new Size(kernelSize, kernelSize), sigmaX);
            case "MEDIAN" -> Imgproc.medianBlur(src, dst, kernelSize);
            case "BILATERAL" -> Imgproc.bilateralFilter(src, dst, kernelSize, sigmaColor, sigmaSpace);
            default -> throw new IllegalArgumentException("Unknown filter type: " + filterType);
        }

        return dst;
    }

    /**
     * Denoise an image using Non-Local Means Denoising.
     * @param src Source image
     * @param h Filter strength (higher = more denoising, but slower)
     * @param templateWindowSize Size of template patch (should be odd)
     * @param searchWindowSize Size of search area (should be odd)
     * @return Denoised image
     */
    public static Mat denoise(Mat src, float h, int templateWindowSize, int searchWindowSize) {
        Mat dst = new Mat();

        if (src.channels() == 1) {
            Photo.fastNlMeansDenoising(src, dst, h, templateWindowSize, searchWindowSize);
        } else {
            Photo.fastNlMeansDenoisingColored(src, dst, h, h, templateWindowSize, searchWindowSize);
        }

        return dst;
    }

    /**
     * Apply various blur effects to an image.
     * @param src Source image
     * @param blurType Type: GAUSSIAN, MOTION, BOX, AVERAGE
     * @param kernelSize Kernel size
     * @param angle Angle for motion blur (in degrees)
     * @return Blurred image
     */
    public static Mat blur(Mat src, String blurType, int kernelSize, double angle) {
        Mat dst = new Mat();

        // Ensure kernel size is odd
        if (kernelSize % 2 == 0) {
            kernelSize++;
        }

        switch (blurType.toUpperCase()) {
            case "GAUSSIAN" -> Imgproc.GaussianBlur(src, dst, new Size(kernelSize, kernelSize), 0);
            case "BOX", "AVERAGE" -> Imgproc.blur(src, dst, new Size(kernelSize, kernelSize));
            case "MOTION" -> {
                Mat kernel = getMotionBlurKernel(kernelSize, angle);
                Imgproc.filter2D(src, dst, -1, kernel);
                kernel.release();
            }
            default -> throw new IllegalArgumentException("Unknown blur type: " + blurType);
        }

        return dst;
    }

    /**
     * Create a motion blur kernel.
     */
    private static Mat getMotionBlurKernel(int size, double angle) {
        Mat kernel = Mat.zeros(size, size, CvType.CV_32F);
        int center = size / 2;
        double angleRad = Math.toRadians(angle);
        double cos = Math.cos(angleRad);
        double sin = Math.sin(angleRad);

        for (int i = -center; i <= center; i++) {
            int x = center + (int) Math.round(i * cos);
            int y = center + (int) Math.round(i * sin);
            if (x >= 0 && x < size && y >= 0 && y < size) {
                kernel.put(y, x, 1.0);
            }
        }

        Scalar sum = Core.sumElems(kernel);
        if (sum.val[0] > 0) {
            Core.divide(kernel, sum, kernel);
        }

        return kernel;
    }

    /**
     * Detect contours in an image.
     * @param src Source image (should be binary/grayscale)
     * @param minArea Minimum contour area to include
     * @param maxArea Maximum contour area to include (use -1 for no limit)
     * @param minCircularity Minimum circularity (0-1, use -1 for no filter)
     * @param maxCircularity Maximum circularity (0-1, use -1 for no filter)
     * @return Map containing contours list, blob info, and visualization image
     */
    public static Map<String, Object> detectContours(Mat src, double minArea, double maxArea,
                                                      double minCircularity, double maxCircularity) {
        Mat gray = src;
        if (src.channels() > 1) {
            gray = new Mat();
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY);
        }

        // Ensure binary image
        Mat binary = new Mat();
        if (gray.channels() == 1) {
            Imgproc.threshold(gray, binary, 127, 255, Imgproc.THRESH_BINARY);
        } else {
            binary = gray.clone();
        }

        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(binary, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        // Filter contours and collect blob info
        List<MatOfPoint> filteredContours = new ArrayList<>();
        List<BlobInfo> blobInfoList = new ArrayList<>();

        for (MatOfPoint contour : contours) {
            double area = Imgproc.contourArea(contour);

            // Check area constraints
            if (area < minArea) continue;
            if (maxArea > 0 && area > maxArea) continue;

            // Calculate circularity
            double perimeter = Imgproc.arcLength(new MatOfPoint2f(contour.toArray()), true);
            double circularity = 4 * Math.PI * area / (perimeter * perimeter);

            // Check circularity constraints
            if (minCircularity >= 0 && circularity < minCircularity) continue;
            if (maxCircularity >= 0 && circularity > maxCircularity) continue;

            // Calculate center and bounding box
            var moments = Imgproc.moments(contour);
            Point center = new Point(moments.get_m10() / moments.get_m00(), moments.get_m01() / moments.get_m00());
            Rect boundingBox = Imgproc.boundingRect(contour);

            // Create blob info
            BlobInfo blobInfo = new BlobInfo(center, circularity, boundingBox, area);
            blobInfoList.add(blobInfo);

            filteredContours.add(contour);
        }

        // Create visualization
        Mat visualization = new Mat();
        if (src.channels() == 1) {
            Imgproc.cvtColor(src, visualization, Imgproc.COLOR_GRAY2BGR);
        } else {
            visualization = src.clone();
        }

        // Draw contours and blob info
        Imgproc.drawContours(visualization, filteredContours, -1, new Scalar(0, 255, 0), 2);
        for (BlobInfo blob : blobInfoList) {
            // Draw center point
            Imgproc.circle(visualization, blob.getCenter(), 5, new Scalar(255, 0, 0), -1);
            // Draw bounding box
            Imgproc.rectangle(visualization, blob.getBoundingBox(), new Scalar(0, 0, 255), 2);
        }

        // Cleanup
        binary.release();
        hierarchy.release();
        if (gray != src) {
            gray.release();
        }

        Map<String, Object> result = new HashMap<>();
        result.put("contours", filteredContours);
        result.put("blobs", blobInfoList);
        result.put("visualization", visualization);
        result.put("count", filteredContours.size());

        return result;
    }

    /**
     * Extract and save segmented regions from an image.
     * @param src Source image
     * @param mask Binary mask indicating segmented regions
     * @param outputPath Base path for output files
     * @return List of saved file paths
     */
    public static List<String> outputSegmented(Mat src, Mat mask, String outputPath) throws Exception {
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(mask.clone(), contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        List<String> savedFiles = new ArrayList<>();
        File baseFile = new File(outputPath);
        String baseName = baseFile.getName().replaceFirst("[.][^.]+$", "");
        String extension = baseFile.getName().substring(baseFile.getName().lastIndexOf('.'));
        String directory = baseFile.getParent() != null ? baseFile.getParent() : ".";

        for (int i = 0; i < contours.size(); i++) {
            MatOfPoint contour = contours.get(i);
            Rect boundingRect = Imgproc.boundingRect(contour);

            // Extract region
            Mat region = new Mat(src, boundingRect);

            // Create output filename
            String filename = String.format("%s/%s_region_%03d%s", directory, baseName, i, extension);

            // Save region
            Imgcodecs.imwrite(filename, region);
            savedFiles.add(filename);

            region.release();
        }

        hierarchy.release();
        return savedFiles;
    }

    /**
     * Save a Mat to a file.
     * @param mat The Mat to save
     * @param outputPath Output file path
     */
    public static void saveImage(Mat mat, String outputPath) {
        Imgcodecs.imwrite(outputPath, mat);
    }

    /**
     * Convert a Mat to a base64 PNG string.
     * @param mat The Mat to convert
     * @return Base64 encoded PNG image
     */
    public static String matToBase64Png(Mat mat) throws Exception {
        BufferedImage bufferedImage = matToBufferedImage(mat);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(bufferedImage, "png", baos);
        byte[] imageBytes = baos.toByteArray();
        return Base64.getEncoder().encodeToString(imageBytes);
    }

    /**
     * Get image information as a formatted string.
     */
    public static String getImageInfo(Mat mat) {
        return String.format(
            "Image Information:\n" +
            "- Width: %d pixels\n" +
            "- Height: %d pixels\n" +
            "- Channels: %d\n" +
            "- Depth: %s\n" +
            "- Type: %s",
            mat.cols(),
            mat.rows(),
            mat.channels(),
            depthToString(mat.depth()),
            typeToString(mat.type())
        );
    }

    private static String depthToString(int depth) {
        return switch (depth) {
            case CvType.CV_8U -> "8-bit unsigned";
            case CvType.CV_8S -> "8-bit signed";
            case CvType.CV_16U -> "16-bit unsigned";
            case CvType.CV_16S -> "16-bit signed";
            case CvType.CV_32S -> "32-bit signed";
            case CvType.CV_32F -> "32-bit float";
            case CvType.CV_64F -> "64-bit float";
            default -> "Unknown";
        };
    }

    private static String typeToString(int type) {
        int channels = CvType.channels(type);
        int depth = CvType.depth(type);
        return String.format("%s C%d", depthToString(depth), channels);
    }
}
