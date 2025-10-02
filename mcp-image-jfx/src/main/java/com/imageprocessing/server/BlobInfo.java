package com.imageprocessing.server;

import org.opencv.core.Point;
import org.opencv.core.Rect;

/**
 * Information about a detected blob/contour.
 */
public class BlobInfo {
    private final Point center;
    private final double circularity;
    private final Rect boundingBox;
    private final double area;

    public BlobInfo(Point center, double circularity, Rect boundingBox, double area) {
        this.center = center;
        this.circularity = circularity;
        this.boundingBox = boundingBox;
        this.area = area;
    }

    public Point getCenter() {
        return center;
    }

    public double getCircularity() {
        return circularity;
    }

    public Rect getBoundingBox() {
        return boundingBox;
    }

    public double getArea() {
        return area;
    }

    /**
     * Convert to CSV row format.
     * Format: center_x,center_y,circularity,bbox_x,bbox_y,bbox_width,bbox_height,area
     */
    public String toCsvRow() {
        return String.format("%.2f,%.2f,%.4f,%d,%d,%d,%d,%.2f",
                center.x, center.y, circularity,
                boundingBox.x, boundingBox.y, boundingBox.width, boundingBox.height,
                area);
    }

    /**
     * Get CSV header.
     */
    public static String getCsvHeader() {
        return "center_x,center_y,circularity,bbox_x,bbox_y,bbox_width,bbox_height,area";
    }

    @Override
    public String toString() {
        return String.format("BlobInfo{center=(%.1f, %.1f), circularity=%.3f, bbox=%s, area=%.1f}",
                center.x, center.y, circularity, boundingBox, area);
    }
}
