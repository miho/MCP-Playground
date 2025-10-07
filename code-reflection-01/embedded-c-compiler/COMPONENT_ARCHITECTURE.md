# Component Architecture - Enhanced UI

## Visual Component Hierarchy

```
EnhancedEmbeddedCApp (Main Application)
│
├─── ServerControlBar (Top)
│    ├─── Server Launch/Stop Buttons
│    ├─── Settings Dialog
│    └─── Theme Toggle Button
│
├─── SplitPane (Main Content)
│    │
│    ├─── LEFT PANE (60% width)
│    │    └─── EnhancedCodeView
│    │         ├─── Line Number Gutter
│    │         │    ├─── Heat Map Indicators (Rectangle)
│    │         │    ├─── Line Numbers (Label)
│    │         │    └─── Severity Dots (Circle)
│    │         ├─── Code Area (RichTextFX)
│    │         │    ├─── Syntax Highlighting
│    │         │    ├─── Line Highlighting
│    │         │    └─── Hotspot Backgrounds
│    │         └─── Tooltips (on hover)
│    │              └─── HotspotMetrics Display
│    │
│    └─── RIGHT PANE (40% width)
│         ├─── Controls Section
│         │    ├─── Sample Selector
│         │    ├─── Cache Configuration
│         │    ├─── Action Buttons
│         │    └─── Status Label
│         │
│         └─── TabPane (Main Tabs)
│              │
│              ├─── ANALYSIS TAB
│              │    └─── InstrumentationTable
│              │         ├─── Checkbox Column
│              │         ├─── Line Number Column
│              │         ├─── Type Column
│              │         └─── Expression Column
│              │
│              ├─── PERFORMANCE TAB
│              │    ├─── Run Info Label
│              │    ├─── PerformanceDashboard
│              │    │    ├─── Metric Cards (HBox)
│              │    │    │    ├─── Hit Rate Card
│              │    │    │    ├─── Misses Card
│              │    │    │    ├─── Evictions Card
│              │    │    │    └─── Total Accesses Card
│              │    │    ├─── Visualizations (HBox)
│              │    │    │    ├─── Hit Rate Gauge
│              │    │    │    │    ├─── Background Circle
│              │    │    │    │    ├─── Arc (animated)
│              │    │    │    │    └─── Center Label
│              │    │    │    └─── Pie Chart
│              │    │    │         └─── Data Slices
│              │    │    └─── Bar Chart
│              │    │         └─── Animated Bars
│              │    │
│              │    └─── HotspotVisualization
│              │         └─── TableView
│              │              ├─── Severity Bar Column
│              │              │    └─── Animated Rectangle
│              │              ├─── ID Column
│              │              ├─── Line Column
│              │              ├─── Expression Column
│              │              ├─── Misses Column (colored)
│              │              ├─── Evictions Column (colored)
│              │              └─── Score Column
│              │                   └─── Badge (styled)
│              │
│              ├─── RESULTS TAB
│              │    └─── TabPane (Sub-tabs)
│              │         ├─── Program Output Tab
│              │         │    └─── TextArea (read-only)
│              │         └─── Instrumented Code Tab
│              │              └─── TextArea (read-only)
│              │
│              └─── BLOCK SWEEP TAB
│                   ├─── Sweep Controls (HBox)
│                   │    ├─── Macro Field
│                   │    ├─── Start Spinner
│                   │    ├─── End Spinner
│                   │    ├─── Step Spinner
│                   │    └─── Run Button
│                   └─── Sweep Results Table
│                        ├─── Block Size Column
│                        ├─── Misses Column
│                        ├─── Hits Column
│                        ├─── Evictions Column
│                        └─── Status Column
│
└─── Status Bar (Bottom - if needed)
```

## Data Flow Diagram

```
User Input
    │
    ↓
┌─────────────────────┐
│ EnhancedEmbeddedCApp│ (Main Controller)
└──────────┬──────────┘
           │
           ├─── Load Sample ───→ ResourceHelper ───→ File System
           │                          ↓
           │                     String (code)
           │                          ↓
           ├─── Analyze Code ──→ ProgramService ───→ ProgramAnalysis
           │                          ↓
           │                   List<ArrayAccess>
           │                          ↓
           │              ┌───────────────────────┐
           │              │   Update UI Tables    │
           │              └───────────────────────┘
           │
           ├─── Run Pipeline ──→ Task (background)
           │                          ↓
           │                  InstrumentationService
           │                          ↓
           │                  CompileAndRun
           │                          ↓
           │                  CacheSummary
           │                          ↓
           │              ┌───────────────────────┐
           │              │   Update All Views    │
           │              ├───────────────────────┤
           │              │ • EnhancedCodeView    │
           │              │ • PerformanceDashboard│
           │              │ • HotspotVisualization│
           │              └───────────────────────┘
           │
           └─── User Interactions
                    ↓
           ┌────────────────────┐
           │ Click Hotspot Row  │ ───→ codeView.focusLine(line)
           │ Hover Heat Map     │ ───→ Show Tooltip
           │ Toggle Theme       │ ───→ Reload CSS
           │ Select Candidate   │ ───→ Focus Code Line
           └────────────────────┘
```

## Component Communication Patterns

### Pattern 1: Event Callbacks

```java
// HotspotVisualization notifies parent when clicked
hotspotViz.setOnHotspotClick(line -> {
    codeView.focusLine(line);
});

// Candidate table notifies on selection
candidateTable.getSelectionModel().selectedItemProperty()
    .addListener((obs, old, val) -> {
        if (val != null) {
            codeView.focusLine(val.getLine());
        }
    });
```

### Pattern 2: Direct Updates

```java
// Main app updates components after analysis
void updateCacheView(CacheSummary summary, ...) {
    dashboard.updateMetrics(summary);
    hotspotViz.setHotspots(hotspotItems);
    codeView.highlightHotspotsWithMetrics(metricsMap);
}
```

### Pattern 3: Observable Properties

```java
// Server manager state changes
serverManager.runningProperty().addListener((obs, oldVal, newVal) ->
    Platform.runLater(() ->
        controlBar.setServerRunning(newVal, ...)
    ));
```

## State Management

```
Application State
│
├─── UI State (JavaFX Properties)
│    ├─── currentTheme: Theme
│    ├─── serverRunning: Boolean
│    └─── selectedSample: String
│
├─── Analysis State
│    ├─── currentAnalysis: ProgramAnalysis
│    ├─── currentSourceName: String
│    ├─── candidates: ObservableList<CandidateRow>
│    └─── sweepRows: ObservableList<BlockSweepRow>
│
├─── Cache State
│    ├─── lastSummary: CacheSummary
│    ├─── cacheConfiguration: CacheConfiguration
│    └─── hotspotMetrics: Map<Integer, HotspotMetrics>
│
└─── Server State
     ├─── serverConfig: ServerConfig
     └─── serverManager: McpServerManager
```

## CSS Class Hierarchy

```
Root Classes
├─── .root (base theme colors)
├─── .code-area-dark
└─── .code-area-light

EnhancedCodeView
├─── .enhanced-code-scroll
├─── .line-number-container
├─── .heat-indicator
├─── .severity-dot
│    ├─── .hotspot-low-dot
│    ├─── .hotspot-medium-dot
│    └─── .hotspot-high-dot
├─── .focused-line
├─── .hotspot-low
├─── .hotspot-medium
├─── .hotspot-high
└─── .metrics-tooltip

PerformanceDashboard
├─── .performance-dashboard
├─── .dashboard-title
├─── .metric-card
│    ├─── .metric-card-success
│    ├─── .metric-card-warning
│    ├─── .metric-card-danger
│    └─── .metric-card-info
├─── .metric-card-title
├─── .metric-card-value
├─── .gauge-title
├─── .gauge-background
├─── .gauge-arc
│    ├─── .gauge-excellent
│    ├─── .gauge-good
│    ├─── .gauge-fair
│    └─── .gauge-poor
├─── .gauge-center-label
└─── Charts
     ├─── .pie-hits
     ├─── .pie-misses
     └─── .pie-evictions

HotspotVisualization
├─── .hotspot-visualization
├─── .section-title
├─── .hotspot-table
├─── Row Classes
│    ├─── .hotspot-row-high
│    ├─── .hotspot-row-medium
│    └─── .hotspot-row-low
├─── Severity Bars
│    ├─── .severity-bar-high
│    ├─── .severity-bar-medium
│    └─── .severity-bar-low
└─── Score Badges
     ├─── .score-badge
     ├─── .score-high
     ├─── .score-medium
     └─── .score-low

Common Components
├─── Buttons
│    ├─── .primary-button
│    ├─── .secondary-button
│    ├─── .success-button
│    ├─── .warning-button
│    └─── .danger-button
├─── Tables
│    ├─── .table-view
│    └─── .table-row-cell.best-row
└─── Status
     ├─── .status-label
     └─── .control-label
```

## Animation Choreography

```
Timeline: User Runs Analysis

0ms: User clicks "Run Analysis" button
    ↓
    [Button press animation: 150ms scale down]
    ↓
100ms: Status label updates
    ↓
    [Fade transition: 300ms]
    ↓
2000ms: Analysis completes (background task)
    ↓
    [All updates happen in parallel]
    ↓
    ├─── Dashboard Metric Cards
    │    └─── [Fade: 300ms × 4 cards sequentially]
    │
    ├─── Gauge Animation
    │    └─── [Arc rotation: 1000ms with ease-both]
    │
    ├─── Pie Chart
    │    └─── [Fade in: 500ms]
    │
    ├─── Bar Chart
    │    └─── [Scale Y: 800ms per bar, staggered]
    │
    ├─── Hotspot Table
    │    ├─── [Rows fade in: 100ms each, staggered]
    │    └─── [Severity bars grow: 500ms]
    │
    └─── Code View
         ├─── [Heat indicators fade in: 300ms]
         ├─── [Background highlights appear: 200ms]
         └─── [High severity starts pulsing: infinite loop]
              └─── [Pulse: 1.5s fade between 0.9 and 0.4 opacity]
```

## Thread Model

```
Main JavaFX Application Thread
├─── UI Rendering
├─── Event Handling
├─── Animation Updates
└─── Platform.runLater() tasks

Background Worker Threads
├─── Analysis Task
│    ├─── Code parsing
│    ├─── Instrumentation
│    ├─── Compilation
│    └─── Execution
│
├─── Block Sweep Task
│    └─── Multiple compile/run cycles
│
└─── Server Manager Thread
     └─── MCP server lifecycle

Thread Safety
├─── All UI updates via Platform.runLater()
├─── Background tasks use Task<T> abstraction
├─── Observable collections automatically sync
└─── No shared mutable state between threads
```

## Memory Management

```
Component Lifecycle

Creation:
├─── Components instantiated on startup
├─── Event listeners registered
├─── CSS loaded and parsed
└─── Initial state set

Active Use:
├─── Data bound to ObservableLists
├─── Animations run and complete
├─── Temporary objects GC'd promptly
└─── Listeners fire on events

Cleanup:
├─── Window close triggers cleanup
├─── Animation timelines stopped
├─── Server processes terminated
└─── Listeners removed

Memory Considerations:
├─── Heat map indicators: ~100 bytes × line count
├─── Animation timelines: Stopped when complete
├─── Chart data: Cleared between runs
└─── Total overhead: ~15MB for visualizations
```

## Plugin Points

Future extensibility points in the architecture:

```java
// Custom visualization plugins
interface PerformanceVisualization {
    void updateMetrics(CacheSummary summary);
    Node getVisualization();
}

// Custom severity calculators
interface SeverityCalculator {
    double calculateSeverity(int misses, int evictions, int total);
    String getSeverityClass(double severity);
}

// Custom theme providers
interface ThemeProvider {
    String getStylesheetPath();
    boolean isDark();
}

// Export handlers
interface ExportHandler {
    void export(CacheSummary summary, Path outputPath);
    String getFormat();
}
```

## Component Dependencies

```
EnhancedCodeView
├─── RichTextFX (CodeArea)
├─── JavaFX Animation API
└─── No external UI components

PerformanceDashboard
├─── JavaFX Charts API
│    ├─── PieChart
│    └─── BarChart
├─── JavaFX Shapes (Arc, Circle)
└─── JavaFX Animation API

HotspotVisualization
├─── JavaFX TableView
├─── JavaFX Animation API
└─── Custom cell renderers

EnhancedEmbeddedCApp
├─── All new components
├─── Original service layer (unchanged)
│    ├─── ProgramService
│    ├─── CacheConfiguration
│    └─── RunResultPersister
└─── ServerControlBar (existing)
```

## Testing Strategy

```
Unit Tests (per component)
├─── EnhancedCodeViewTest
│    ├─── testHeatMapUpdate()
│    ├─── testSeverityCalculation()
│    ├─── testTooltipContent()
│    └─── testAnimationCleanup()
│
├─── PerformanceDashboardTest
│    ├─── testMetricUpdates()
│    ├─── testGaugeColors()
│    ├─── testChartData()
│    └─── testReset()
│
└─── HotspotVisualizationTest
     ├─── testRowSorting()
     ├─── testClickHandling()
     ├─── testSeverityBars()
     └─── testBadgeColors()

Integration Tests
├─── testEndToEndWorkflow()
├─── testThemeSwitch()
├─── testNavigationSync()
└─── testMultipleRuns()

Visual Regression Tests
├─── captureCodeViewHeatMap()
├─── captureDashboardLayout()
└─── compareWithBaseline()
```

---

**Created**: 2025-10-07
**Version**: 1.0
**Purpose**: Developer reference for architecture and design
