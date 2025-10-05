package com.devicesim;

import com.devicesim.data.CsvDataReader;
import com.devicesim.model.Location;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CSV data reading functionality.
 */
class CsvDataReaderTest {

    @TempDir
    Path tempDir;

    @Test
    void testReadHeaders() throws IOException {
        Path csvFile = createTestCsvFile();
        CsvDataReader reader = new CsvDataReader();

        List<String> headers = reader.getHeaders(csvFile.toString());

        assertEquals(6, headers.size());
        assertTrue(headers.contains("x"));
        assertTrue(headers.contains("y"));
        assertTrue(headers.contains("area"));
        assertTrue(headers.contains("circularity"));
    }

    @Test
    void testReadLocationsWithoutFilter() throws IOException {
        Path csvFile = createTestCsvFile();
        CsvDataReader reader = new CsvDataReader();

        List<Location> locations = reader.readLocations(
            csvFile.toString(), "x", "y", null
        );

        assertEquals(3, locations.size());

        Location firstLoc = locations.get(0);
        assertEquals(10.5, firstLoc.getX(), 0.01);
        assertEquals(20.3, firstLoc.getY(), 0.01);
        assertEquals(150.2, (Double) firstLoc.getProperty("area"), 0.01);
        assertEquals(0.85, (Double) firstLoc.getProperty("circularity"), 0.01);
    }

    @Test
    void testReadLocationsWithRangeFilter() throws IOException {
        Path csvFile = createTestCsvFile();
        CsvDataReader reader = new CsvDataReader();

        Map<String, CsvDataReader.FilterCriteria> filters = new HashMap<>();
        filters.put("area", new CsvDataReader.FilterCriteria("area", 150.0, 200.0));

        List<Location> locations = reader.readLocations(
            csvFile.toString(), "x", "y", filters
        );

        assertEquals(1, locations.size());
        // Should include only row with area 150.2 (in range [150, 200])
        // Excludes: 200.8 (>200) and 120.5 (<150)
        assertEquals(150.2, (Double) locations.get(0).getProperty("area"), 0.01);
    }

    @Test
    void testReadLocationsWithEqualsFilter() throws IOException {
        Path csvFile = createTestCsvFile();
        CsvDataReader reader = new CsvDataReader();

        Map<String, CsvDataReader.FilterCriteria> filters = new HashMap<>();
        filters.put("label", new CsvDataReader.FilterCriteria("label", "cell_2"));

        List<Location> locations = reader.readLocations(
            csvFile.toString(), "x", "y", filters
        );

        assertEquals(1, locations.size());
        assertEquals(25.7, locations.get(0).getX(), 0.01);
    }

    @Test
    void testReadLocationsWithMultipleFilters() throws IOException {
        Path csvFile = createTestCsvFile();
        CsvDataReader reader = new CsvDataReader();

        Map<String, CsvDataReader.FilterCriteria> filters = new HashMap<>();
        filters.put("area", new CsvDataReader.FilterCriteria("area", 100.0, 180.0));
        filters.put("circularity", new CsvDataReader.FilterCriteria("circularity", 0.80, 0.90));

        List<Location> locations = reader.readLocations(
            csvFile.toString(), "x", "y", filters
        );

        // Should filter based on both criteria
        assertTrue(locations.size() <= 3);
        for (Location loc : locations) {
            double area = (Double) loc.getProperty("area");
            double circ = (Double) loc.getProperty("circularity");
            assertTrue(area >= 100.0 && area <= 180.0);
            assertTrue(circ >= 0.80 && circ <= 0.90);
        }
    }

    @Test
    void testInvalidFilePath() {
        CsvDataReader reader = new CsvDataReader();

        assertThrows(IllegalArgumentException.class, () ->
            reader.getHeaders("nonexistent.csv")
        );
    }

    @Test
    void testInvalidColumnName() throws IOException {
        Path csvFile = createTestCsvFile();
        CsvDataReader reader = new CsvDataReader();

        assertThrows(IllegalArgumentException.class, () ->
            reader.readLocations(csvFile.toString(), "invalid_column", "y", null)
        );
    }

    @Test
    void testEmptyFilePath() {
        CsvDataReader reader = new CsvDataReader();

        assertThrows(IllegalArgumentException.class, () ->
            reader.getHeaders("")
        );

        assertThrows(IllegalArgumentException.class, () ->
            reader.readLocations("", "x", "y", null)
        );
    }

    @Test
    void testFilterCriteriaValidation() {
        assertThrows(IllegalArgumentException.class, () ->
            new CsvDataReader.FilterCriteria(null, 0.0, 100.0)
        );

        assertThrows(IllegalArgumentException.class, () ->
            new CsvDataReader.FilterCriteria("", 0.0, 100.0)
        );

        assertThrows(IllegalArgumentException.class, () ->
            new CsvDataReader.FilterCriteria("column", null)
        );
    }

    private Path createTestCsvFile() throws IOException {
        Path csvFile = tempDir.resolve("test.csv");
        String csvContent = """
            x,y,area,circularity,intensity,label
            10.5,20.3,150.2,0.85,128.5,cell_1
            25.7,30.1,200.8,0.92,145.3,cell_2
            40.2,15.6,120.5,0.78,110.2,cell_3
            """;
        Files.writeString(csvFile, csvContent);
        return csvFile;
    }
}
