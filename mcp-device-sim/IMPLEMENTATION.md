# Device Simulation Core Classes - Implementation Documentation

This document describes the core Java backend classes for the device simulation system.

## Overview

The device simulation system consists of four main components:

1. **Model Classes** - Data structures representing locations and device state
2. **Simulation Engine** - Core logic for device movement and physics
3. **Data Layer** - CSV file reading and filtering
4. **Tests & Examples** - Comprehensive test coverage and usage examples

## Architecture

```
com.devicesim
├── model/
│   ├── Location.java          - Represents target locations
│   └── DeviceState.java       - Represents device state (immutable)
├── engine/
│   └── DeviceSimulator.java   - Main simulation engine
└── data/
    └── CsvDataReader.java     - CSV file reading and filtering
```

## Core Classes

### 1. Location.java (`com.devicesim.model`)

**Purpose**: Represents a target location with coordinates and metadata.

**Key Features**:
- Thread-safe for read operations
- Stores x, y coordinates
- Unique identifier (ID)
- Properties map for storing CSV column data
- Visited flag tracking
- Distance calculation utilities

**Example Usage**:
```java
Location loc = new Location("cell_1", 10.5, 20.3);
loc.setProperty("area", 150.2);
loc.setProperty("circularity", 0.85);

double distance = loc.distanceTo(0, 0);
loc.setVisited(true);
```

**Design Decisions**:
- Properties stored as `Map<String, Object>` for flexibility
- `volatile boolean visited` for thread-safe status updates
- Immutable coordinates and ID for consistency
- Utility methods for distance calculations

---

### 2. DeviceState.java (`com.devicesim.model`)

**Purpose**: Represents the current state of the simulated device.

**Key Features**:
- **Immutable** - Thread-safe by design
- Builder pattern for construction
- `with*()` methods for creating modified copies
- Current position (x, y)
- Target position (targetX, targetY)
- Speed and acceleration
- Movement status

**Example Usage**:
```java
// Create initial state
DeviceState state = new DeviceState(0, 0, 10.0);

// Create modified state
DeviceState newState = state
    .withPosition(5.0, 5.0)
    .withSpeed(8.0)
    .withMoving(true);

// Use builder
DeviceState builderState = DeviceState.builder()
    .x(10).y(20)
    .maxSpeed(15.0)
    .acceleration(5.0)
    .build();
```

**Design Decisions**:
- Immutability ensures thread-safety without locks
- Builder pattern for complex construction
- `with*()` methods return new instances
- Validation in constructor (no negative values)

---

### 3. DeviceSimulator.java (`com.devicesim.engine`)

**Purpose**: Main simulation engine managing device movement and physics.

**Key Features**:
- Thread-safe with ReentrantReadWriteLock
- Smooth acceleration and deceleration
- Automatic target advancement (optional)
- Configurable speed and acceleration
- Precise arrival detection (0.5 unit threshold)

**Movement Algorithm**:
1. **Acceleration Phase**: Device accelerates from 0 to max speed
2. **Cruise Phase**: Maintains max speed
3. **Deceleration Phase**: Calculates stopping distance and decelerates smoothly
4. **Arrival**: Stops within 0.5 units of target

**Example Usage**:
```java
DeviceSimulator simulator = new DeviceSimulator(0, 0);
simulator.setTargetLocations(locations);
simulator.setSpeed(15.0);          // units per second
simulator.setAcceleration(8.0);     // units per second²
simulator.setAutoAdvance(true);     // Auto-move to next target

simulator.startMovement();

// Update loop
while (simulator.getState().isMoving()) {
    simulator.update(0.05); // 50ms time step
    Thread.sleep(50);
}
```

**Physics Implementation**:
```java
// Deceleration distance calculation
decelDistance = (currentSpeed² ) / (2 * acceleration)

// If within decel distance, slow down
if (distanceToTarget <= decelDistance) {
    newSpeed = max(0, currentSpeed - acceleration * deltaTime)
} else {
    // Accelerate to max speed
    newSpeed = min(maxSpeed, currentSpeed + acceleration * deltaTime)
}

// Use average speed for smoother movement
avgSpeed = (currentSpeed + newSpeed) / 2
distanceToMove = avgSpeed * deltaTime
```

**Thread Safety**:
- All public methods use ReadWriteLock
- Read operations (getState, getCurrentTarget) use read lock
- Modifications (update, setSpeed) use write lock
- DeviceState is immutable, safe to share

**Design Decisions**:
- Physics-based movement for realism
- Configurable auto-advance for flexibility
- Arrival threshold (0.5 units) prevents overshooting
- Lock-based thread safety for multi-threaded environments

---

### 4. CsvDataReader.java (`com.devicesim.data`)

**Purpose**: Read and filter location data from CSV files.

**Key Features**:
- OpenCSV library integration
- Header reading
- Flexible column mapping
- Multi-criteria filtering
- Range and equality filters
- Automatic type conversion (numbers vs strings)

**Filter Types**:

**Range Filter**:
```java
FilterCriteria areaFilter =
    new FilterCriteria("area", 100.0, 200.0); // min, max
```

**Equality Filter**:
```java
FilterCriteria labelFilter =
    new FilterCriteria("label", "cell_1"); // exact match
```

**Example Usage**:
```java
CsvDataReader reader = new CsvDataReader();

// Read headers
List<String> headers = reader.getHeaders("data.csv");

// Set up filters
Map<String, FilterCriteria> filters = new HashMap<>();
filters.put("area", new FilterCriteria("area", 150.0, 200.0));
filters.put("circularity", new FilterCriteria("circularity", 0.85, 1.0));

// Read and filter
List<Location> locations = reader.readLocations(
    "data.csv",
    "x",        // x column name
    "y",        // y column name
    filters
);
```

**Data Handling**:
- Automatically parses numbers (stores as Double)
- Non-numeric values stored as Strings
- Empty rows skipped
- Invalid rows logged and skipped
- All CSV columns stored in Location properties

**Error Handling**:
- File validation (exists, readable, regular file)
- Column existence validation
- Clear error messages with context
- CsvReadException for parsing errors

**Design Decisions**:
- OpenCSV for robust CSV parsing
- Flexible property storage (any CSV column)
- Non-fatal error handling (skip bad rows)
- Filter criteria as separate class for reusability

---

## Complete Workflow Example

See `CompleteWorkflowExample.java` for a full demonstration:

```bash
gradle runExample
```

This example demonstrates:
1. Loading locations from CSV with filters
2. Configuring the simulator
3. Running the simulation loop
4. Tracking visited locations
5. Summary statistics

**Sample Output**:
```
=== Device Simulation Complete Workflow Example ===

Step 1: Loading locations from CSV file...
Loaded 4 locations (after filtering)
  - loc_2_10.50_20.30 at (10.50, 20.30) - area: 150.2, circularity: 0.85
  ...

Step 2: Setting up device simulator...
Max speed: 15.00 units/sec, Acceleration: 8.00 units/sec²

Step 3: Simulating device movement...
[0.00s] Pos: (0.00, 0.00), Speed: 0.00
[1.00s] Pos: (1.84, 3.55), Speed: 8.00
...

Step 4: Simulation Summary
Total simulation time: 16.85 seconds
Locations visited: 4 / 4
```

---

## Testing

### Test Coverage

**Unit Tests**:
- `CsvDataReaderTest` - 9 tests covering CSV reading and filtering
- `DeviceSimulatorIntegrationTest` - 8 tests covering simulation logic

**Run Tests**:
```bash
gradle test
```

**Test Categories**:
1. **CSV Reading**: Headers, data loading, filtering, validation
2. **Movement Physics**: Acceleration, deceleration, target reaching
3. **State Management**: Immutability, thread safety
4. **Multi-target Navigation**: Auto-advance, visited tracking
5. **Error Handling**: Invalid files, columns, data

### Code Quality

**Best Practices Applied**:
- ✅ Modern Java 17 features (records could be used for DeviceState in future)
- ✅ Immutability where appropriate (DeviceState)
- ✅ Thread-safety (DeviceSimulator, Location volatile field)
- ✅ Comprehensive JavaDoc
- ✅ Input validation with meaningful exceptions
- ✅ SOLID principles (Single Responsibility, etc.)
- ✅ Builder pattern for complex objects
- ✅ Proper resource management
- ✅ No warnings on compilation

---

## Performance Considerations

### DeviceSimulator
- **Lock Contention**: ReadWriteLock allows concurrent reads
- **Update Frequency**: Recommended 20-50 Hz (0.02-0.05s time steps)
- **Location Count**: Tested with 100+ locations without issues

### CsvDataReader
- **Memory**: Loads entire CSV into memory (use streaming for huge files)
- **Parsing**: OpenCSV is efficient for typical CSV sizes (<10MB)
- **Filtering**: Applied during parsing (single pass)

---

## Extension Points

### Adding New Features

**Custom Movement Patterns**:
Extend `DeviceSimulator` or create alternative implementations of movement logic.

**Additional Filters**:
```java
// Add regex filter
public class RegexFilterCriteria extends FilterCriteria {
    private Pattern pattern;
    // ...
}
```

**Persistence**:
Add methods to save/load simulator state:
```java
public void saveState(Path file) { ... }
public static DeviceSimulator loadState(Path file) { ... }
```

**Real-time Updates**:
Add observer pattern:
```java
simulator.addListener(state -> {
    // React to state changes
});
```

---

## Dependencies

- **OpenCSV 5.9**: CSV parsing
- **JUnit Jupiter 5.10.0**: Testing
- **Java 17**: Target platform

All dependencies managed in `build.gradle`.

---

## File Locations

```
src/main/java/com/devicesim/
├── model/
│   ├── Location.java                 [Core]
│   └── DeviceState.java              [Core]
├── engine/
│   └── DeviceSimulator.java          [Core]
└── data/
    └── CsvDataReader.java            [Core]

src/test/java/com/devicesim/
├── CsvDataReaderTest.java            [Unit Tests]
├── DeviceSimulatorIntegrationTest.java [Integration Tests]
└── CompleteWorkflowExample.java      [Example]

data/
└── sample_locations.csv              [Sample Data]
```

---

## Summary

This implementation provides a robust, thread-safe, and extensible device simulation system with:

- **Clean Architecture**: Separation of concerns (model, engine, data)
- **Production Quality**: Proper error handling, validation, thread safety
- **Modern Java**: Java 17 features, best practices
- **Well Tested**: Comprehensive test coverage
- **Documented**: JavaDoc and usage examples
- **Flexible**: Configurable physics, filtering, auto-advance

All classes are ready for integration into larger systems (UI, MCP server, etc.).
