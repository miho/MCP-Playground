# Styling Guide - Cache Analysis Studio

## Component Hierarchy & Styling Reference

This guide helps you customize the visual appearance of the enhanced UI components.

## CSS Class Reference

### Enhanced Code View

#### Main Container
```css
.enhanced-code-scroll {
    /* ScrollPane wrapper for code area */
    -fx-border-radius: 6;
    -fx-background-radius: 6;
}
```

#### Line Number Container
```css
.line-number-container {
    /* Container for line number + indicators */
    -fx-background-color: rgba(128, 128, 128, 0.05);
    -fx-padding: 0 8 0 4;
}
```

#### Heat Indicators
```css
.heat-indicator {
    /* Colored bar in gutter showing severity */
    /* Color set programmatically based on severity */
    -fx-arc-width: 3;
    -fx-arc-height: 3;
}
```

#### Severity Dots
```css
.severity-dot {
    /* Small circle indicator */
}

.hotspot-low-dot {
    -fx-fill: linear-gradient(to bottom, #fbbf24, #f59e0b);
}

.hotspot-medium-dot {
    -fx-fill: linear-gradient(to bottom, #fb923c, #ea580c);
}

.hotspot-high-dot {
    -fx-fill: linear-gradient(to bottom, #f87171, #dc2626);
}
```

#### Line Highlighting
```css
.focused-line {
    /* Temporary highlight when jumping to line */
    -fx-background-color: rgba(59, 130, 246, 0.15);
    -fx-border-color: transparent transparent transparent rgba(59, 130, 246, 0.6);
    -fx-border-width: 0 0 0 3;
}

.hotspot-low {
    -fx-background-color: linear-gradient(to right, rgba(250, 204, 21, 0.15), transparent);
    -fx-border-color: transparent transparent transparent #facc15;
    -fx-border-width: 0 0 0 4;
}

.hotspot-medium {
    -fx-background-color: linear-gradient(to right, rgba(251, 146, 60, 0.2), transparent);
    -fx-border-color: transparent transparent transparent #fb923c;
    -fx-border-width: 0 0 0 4;
}

.hotspot-high {
    -fx-background-color: linear-gradient(to right, rgba(239, 68, 68, 0.25), transparent);
    -fx-border-color: transparent transparent transparent #ef4444;
    -fx-border-width: 0 0 0 4;
    -fx-effect: dropshadow(gaussian, rgba(239, 68, 68, 0.3), 8, 0.4, 0, 0);
}
```

#### Tooltips
```css
.metrics-tooltip {
    -fx-font-family: "Consolas", "Monaco", monospace;
    -fx-background-color: linear-gradient(to bottom, #2d3748, #1a202c);
    -fx-background-radius: 8;
    -fx-padding: 12;
}
```

### Performance Dashboard

#### Container
```css
.performance-dashboard {
    -fx-spacing: 16;
    -fx-padding: 16;
}
```

#### Title
```css
.dashboard-title {
    -fx-font-size: 18px;
    -fx-font-weight: bold;
}
```

#### Metric Cards
```css
.metric-card {
    -fx-background-color: linear-gradient(to bottom, rgba(255, 255, 255, 0.05), rgba(255, 255, 255, 0.02));
    -fx-background-radius: 12;
    -fx-border-radius: 12;
    -fx-padding: 16;
}

.metric-card:hover {
    -fx-scale-x: 1.02;
    -fx-scale-y: 1.02;
}

.metric-card-success {
    -fx-border-color: rgba(34, 197, 94, 0.4);  /* Green */
}

.metric-card-warning {
    -fx-border-color: rgba(245, 158, 11, 0.4);  /* Yellow */
}

.metric-card-danger {
    -fx-border-color: rgba(239, 68, 68, 0.4);   /* Red */
}

.metric-card-info {
    -fx-border-color: rgba(59, 130, 246, 0.4);  /* Blue */
}
```

#### Card Content
```css
.metric-card-title {
    -fx-font-size: 11px;
    -fx-font-weight: 600;
    -fx-text-transform: uppercase;
}

.metric-card-value {
    -fx-font-size: 28px;
    -fx-font-weight: bold;
}
```

#### Gauge
```css
.gauge-title {
    -fx-font-size: 14px;
    -fx-font-weight: bold;
}

.gauge-background {
    -fx-stroke: rgba(128, 128, 128, 0.2);
    -fx-stroke-width: 12;
}

.gauge-arc {
    -fx-stroke-line-cap: round;
    -fx-stroke-width: 12;
}

.gauge-excellent {
    -fx-stroke: linear-gradient(to bottom, #10b981, #059669);
}

.gauge-good {
    -fx-stroke: linear-gradient(to bottom, #3b82f6, #2563eb);
}

.gauge-fair {
    -fx-stroke: linear-gradient(to bottom, #f59e0b, #d97706);
}

.gauge-poor {
    -fx-stroke: linear-gradient(to bottom, #ef4444, #dc2626);
}

.gauge-center-label {
    -fx-font-size: 24px;
    -fx-font-weight: bold;
}
```

#### Charts
```css
.distribution-chart,
.metrics-chart {
    -fx-background-color: transparent;
}

.pie-hits {
    -fx-pie-color: #10b981;    /* Green */
}

.pie-misses {
    -fx-pie-color: #f59e0b;    /* Orange */
}

.pie-evictions {
    -fx-pie-color: #ef4444;    /* Red */
}
```

### Hotspot Visualization

#### Container
```css
.hotspot-visualization {
    -fx-spacing: 8;
    -fx-padding: 12;
}

.section-title {
    -fx-font-size: 16px;
    -fx-font-weight: bold;
}
```

#### Table
```css
.hotspot-table {
    -fx-border-radius: 8;
    -fx-background-radius: 8;
}

.hotspot-table .table-row-cell {
    -fx-cursor: hand;
}

.hotspot-table .table-row-cell:hover {
    -fx-background-color: rgba(59, 130, 246, 0.1);
}
```

#### Row Styling
```css
.hotspot-row-high {
    -fx-background-color: rgba(239, 68, 68, 0.08);
}

.hotspot-row-high:hover {
    -fx-background-color: rgba(239, 68, 68, 0.15);
}

.hotspot-row-medium {
    -fx-background-color: rgba(245, 158, 11, 0.06);
}

.hotspot-row-low {
    -fx-background-color: rgba(250, 204, 21, 0.04);
}
```

#### Severity Bars
```css
.severity-bar-high {
    -fx-fill: linear-gradient(to right, #dc2626, #ef4444);
    -fx-effect: dropshadow(gaussian, rgba(220, 38, 38, 0.5), 4, 0.5, 0, 0);
}

.severity-bar-medium {
    -fx-fill: linear-gradient(to right, #ea580c, #fb923c);
}

.severity-bar-low {
    -fx-fill: linear-gradient(to right, #f59e0b, #fbbf24);
}
```

#### Score Badges
```css
.score-badge {
    -fx-padding: 4 12 4 12;
    -fx-background-radius: 12;
    -fx-font-weight: bold;
}

.score-high {
    -fx-background-color: linear-gradient(to bottom, #dc2626, #991b1b);
    -fx-text-fill: white;
}

.score-medium {
    -fx-background-color: linear-gradient(to bottom, #ea580c, #c2410c);
    -fx-text-fill: white;
}

.score-low {
    -fx-background-color: linear-gradient(to bottom, #f59e0b, #d97706);
    -fx-text-fill: white;
}
```

### Common Buttons

```css
.button {
    -fx-background-radius: 6;
    -fx-padding: 8 16;
    -fx-font-weight: 600;
}

.primary-button {
    -fx-background-color: linear-gradient(to bottom, #3b82f6, #2563eb);
    -fx-text-fill: white;
}

.secondary-button {
    -fx-background-color: transparent;
    -fx-border-color: rgba(128, 128, 128, 0.4);
    -fx-border-width: 1.5;
}

.success-button {
    -fx-background-color: linear-gradient(to bottom, #10b981, #059669);
    -fx-text-fill: white;
}

.warning-button {
    -fx-background-color: linear-gradient(to bottom, #f59e0b, #d97706);
    -fx-text-fill: white;
}

.danger-button {
    -fx-background-color: linear-gradient(to bottom, #ef4444, #dc2626);
    -fx-text-fill: white;
}
```

### Tables

```css
.table-view {
    -fx-background-radius: 8;
    -fx-border-radius: 8;
}

.table-view .column-header {
    -fx-background-color: linear-gradient(to bottom, rgba(128, 128, 128, 0.12), rgba(128, 128, 128, 0.08));
    -fx-font-weight: bold;
}

.table-row-cell.best-row {
    -fx-background-color: rgba(34, 197, 94, 0.15);
    -fx-border-color: transparent transparent rgba(34, 197, 94, 0.3) transparent;
}
```

### Status Elements

```css
.status-label {
    -fx-font-weight: 600;
    -fx-padding: 8 12;
    -fx-background-radius: 6;
    -fx-border-radius: 6;
}

.control-label {
    -fx-font-size: 12px;
    -fx-font-weight: 600;
    -fx-opacity: 0.8;
}
```

## Customization Examples

### Changing Severity Colors

Edit `enhanced-styles.css`:

```css
/* Make high severity purple instead of red */
.hotspot-high {
    -fx-background-color: linear-gradient(to right, rgba(168, 85, 247, 0.25), transparent);
    -fx-border-color: transparent transparent transparent #a855f7;
}

.severity-bar-high {
    -fx-fill: linear-gradient(to right, #7c3aed, #a855f7);
}

.score-high {
    -fx-background-color: linear-gradient(to bottom, #7c3aed, #6d28d9);
}
```

### Adjusting Animation Speed

In component Java files:

```java
// Make pulse animation faster
Timeline timeline = new Timeline(
    new KeyFrame(Duration.ZERO, ...),
    new KeyFrame(Duration.seconds(0.8), ...)  // Changed from 1.5
);

// Make gauge animation slower
Timeline timeline = new Timeline(
    new KeyFrame(Duration.ZERO, ...),
    new KeyFrame(Duration.millis(1500), ...)  // Changed from 1000
);
```

### Changing Font Sizes

Edit theme CSS files:

```css
.dashboard-title {
    -fx-font-size: 20px;  /* Larger title */
}

.metric-card-value {
    -fx-font-size: 32px;  /* Bigger numbers */
}

.code-area {
    -fx-font-size: 14px;  /* Larger code font */
}
```

### Custom Metric Card Colors

```css
.metric-card-custom {
    -fx-border-color: rgba(168, 85, 247, 0.4);  /* Purple */
}

.metric-card-custom .metric-card-value {
    -fx-text-fill: #a855f7;
}
```

### Disable Animations

Set duration to zero:

```java
// In PerformanceDashboard.java
private void animateValue(Label label, double value, String displayText) {
    // Skip animation, set directly
    label.setText(displayText);
}
```

Or in CSS:

```css
.metric-card {
    -fx-transition: none;  /* Disable transitions */
}
```

## Theme-Specific Styling

### Dark Theme Variables

```css
/* Dark theme color palette */
--bg-primary: #0f172a;
--bg-secondary: #1e293b;
--text-primary: #e2e8f0;
--text-secondary: #94a3b8;
--border-color: #334155;
--accent-color: #3b82f6;
```

### Light Theme Variables

```css
/* Light theme color palette */
--bg-primary: #f8fafc;
--bg-secondary: #ffffff;
--text-primary: #0f172a;
--text-secondary: #64748b;
--border-color: #cbd5e1;
--accent-color: #3b82f6;
```

## Accessibility Considerations

### High Contrast Mode

Add to your theme:

```css
.high-contrast .hotspot-low {
    -fx-border-width: 0 0 0 6;  /* Thicker border */
}

.high-contrast .severity-bar-high {
    -fx-effect: dropshadow(gaussian, black, 8, 1.0, 0, 0);  /* Stronger shadow */
}
```

### Font Scaling

```css
.large-text .code-area {
    -fx-font-size: 16px;
}

.large-text .metric-card-value {
    -fx-font-size: 36px;
}
```

## Performance Tips

### Reduce Drop Shadows

```css
.metric-card {
    -fx-effect: none;  /* Remove shadow for better performance */
}
```

### Simplify Gradients

```css
.metric-card {
    -fx-background-color: rgba(255, 255, 255, 0.05);  /* Solid instead of gradient */
}
```

### Disable Pulse Animations

```java
// In EnhancedCodeView.java
// Comment out or remove addPulseAnimation() calls
```

## Browser-Style Inspector

To debug styling at runtime:

1. **Enable Scenic View** (JavaFX inspector):
   ```bash
   ./gradlew run -Dscenic.view=true
   ```

2. **CSS Reloading**: Enable hot reload:
   ```java
   scene.getStylesheets().addListener((Change<? extends String> change) -> {
       // Reload on CSS change
   });
   ```

3. **Print Applied Styles**:
   ```java
   System.out.println(node.getStyleClass());
   System.out.println(node.getStyle());
   ```

## Quick Reference Tables

### Color Palette

| Name | Light | Dark | Usage |
|------|-------|------|-------|
| Background | `#f8fafc` | `#0f172a` | Main background |
| Surface | `#ffffff` | `#1e293b` | Cards, panels |
| Text Primary | `#0f172a` | `#e2e8f0` | Main text |
| Text Secondary | `#64748b` | `#94a3b8` | Labels |
| Border | `#cbd5e1` | `#334155` | Borders |
| Accent | `#3b82f6` | `#3b82f6` | Buttons, links |

### Severity Colors

| Level | Color | Hex |
|-------|-------|-----|
| Success | Green | `#10b981` |
| Warning | Yellow | `#facc15` |
| Caution | Orange | `#f59e0b` |
| Danger | Red | `#ef4444` |
| Info | Blue | `#3b82f6` |

### Spacing Scale

| Size | Pixels | Usage |
|------|--------|-------|
| xs | 4px | Tight spacing |
| sm | 8px | Normal spacing |
| md | 12px | Relaxed spacing |
| lg | 16px | Loose spacing |
| xl | 24px | Section spacing |

### Border Radius

| Element | Radius |
|---------|--------|
| Button | 6px |
| Card | 12px |
| Table | 8px |
| Input | 4px |

---

**Last Updated**: 2025-10-07
**Version**: 1.0
