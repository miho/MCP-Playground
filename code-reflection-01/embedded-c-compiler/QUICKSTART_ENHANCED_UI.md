# Quick Start - Enhanced UI

## Running the Beautiful New UI

### Option 1: Gradle (Recommended)

```bash
# Navigate to project directory
cd /mnt/c/Dev/repos/MCP-Playground/code-reflection-01/embedded-c-compiler

# Run the enhanced UI
./gradlew run -PmainClass=com.embeddedcc.ui.EnhancedEmbeddedCApp
```

### Option 2: IDE

1. Open the project in IntelliJ IDEA or Eclipse
2. Navigate to: `src/main/java/com/embeddedcc/ui/EnhancedEmbeddedCApp.java`
3. Right-click → Run 'EnhancedEmbeddedCApp.main()'

### Option 3: Build JAR

```bash
# Build the project
./gradlew build

# Run the JAR with enhanced UI
java -cp build/libs/embedded-c-compiler.jar com.embeddedcc.ui.EnhancedEmbeddedCApp
```

## What You'll See

### 1. Enhanced Code Editor (Left Side)
- **Heat map gutter** with colored bars showing performance issues
- **Line numbers** with severity indicators
- **Syntax highlighting** for C code
- **Hover tooltips** with detailed cache metrics
- **Animated highlights** for critical hotspots

### 2. Tabbed Analysis Panel (Right Side)

#### Analysis Tab
- Table of instrumentation candidates
- Select which array accesses to monitor
- Click on row to jump to code location

#### Performance Tab
- **Dashboard** with animated metrics cards:
  - Cache Hit Rate (with circular gauge)
  - Total Misses
  - Evictions
  - Total Accesses
- **Pie Chart** showing event distribution
- **Bar Chart** for detailed comparison
- **Hotspot Table** with visual severity bars
  - Click any row to jump to that line in code
  - Color-coded rows (green → yellow → red)
  - Score badges showing severity

#### Results Tab
- Program output
- Instrumented source code
- Compilation and execution logs

#### Block Sweep Tab
- Configure block size sweep parameters
- Run multiple tests automatically
- Results table with best configuration highlighted

## First Steps

1. **Load a Sample**:
   - Select "Matrix Multiply" or "Blocked Transpose" from dropdown
   - Click "Load"
   - Click "Analyze" to detect array accesses

2. **Select Instrumentation Points**:
   - Go to "Analysis" tab
   - Check boxes for array accesses to monitor
   - Or click "Select All" to monitor everything

3. **Configure Cache** (optional):
   - Adjust "Set bits (s)", "Lines/set (E)", "Block bits (b)"
   - Default values are reasonable for most tests

4. **Run Analysis**:
   - Click "Instrument & Run Analysis" button
   - Wait for compilation and execution
   - Watch the status label for progress

5. **Explore Results**:
   - Switch to "Performance" tab to see dashboard
   - Hover over heat map indicators in code editor
   - Click hotspot rows to navigate to problem areas
   - Check the gauge showing cache hit rate

## Features to Try

### Interactive Navigation
- Click any row in the hotspot table → jumps to that line
- Click on severity indicators in the gutter → shows tooltip
- Double-click on instrumentation candidate → focuses code line

### Visual Indicators
- **Green bars**: Good performance, few issues
- **Yellow bars**: Some cache misses
- **Orange bars**: Moderate performance problems
- **Red bars**: Critical hotspots, many misses/evictions
- **Pulsing indicators**: Highest severity issues

### Theme Toggle
- Click the theme button (☀️/🌙) in the top bar
- Switch between beautiful dark and light themes
- All visualizations adapt to the new theme

### Block Size Sweep
1. Go to "Block Sweep" tab
2. Set macro name (e.g., "BLOCK_SIZE")
3. Configure start, end, and step values
4. Click "Run Block Size Sweep"
5. Best configuration is highlighted in green

## Keyboard Shortcuts

- **Ctrl+S**: Save (if editing code)
- **F5**: Refresh analysis
- **Escape**: Clear selection
- **Tab**: Navigate between controls
- **Enter**: Activate focused button

## Tips

1. **Performance**: Start with a small sample to see quick results
2. **Hotspots**: Focus on red/orange indicators first for maximum impact
3. **Dashboard**: Watch the hit rate gauge - aim for 90%+ (green zone)
4. **Navigation**: Use the hotspot table for quick access to problem areas
5. **Tooltips**: Hover over anything for more information

## Troubleshooting

### Issue: UI doesn't start
**Solution**: Ensure you have JavaFX dependencies:
```bash
./gradlew --refresh-dependencies
./gradlew build
```

### Issue: Animations are laggy
**Solution**: This is a feature-rich UI. If performance is slow:
- Close other applications
- Reduce window size
- Use a more powerful machine

### Issue: Code doesn't compile
**Solution**:
- Check that GCC is installed: `gcc --version`
- Ensure code is valid C syntax
- Check the Results tab for compilation errors

### Issue: No hotspots showing
**Solution**:
- Make sure you selected instrumentation points
- Verify the program executed successfully (check Results tab)
- Try a different cache configuration
- Use a sample with array operations

## Comparing with Original UI

Run both versions to see the difference:

```bash
# Original UI
./gradlew run -PmainClass=com.embeddedcc.ui.EmbeddedCApp

# Enhanced UI
./gradlew run -PmainClass=com.embeddedcc.ui.EnhancedEmbeddedCApp
```

### Key Visual Differences:

| Feature | Original | Enhanced |
|---------|----------|----------|
| Code gutter | Simple line numbers | Heat map + severity dots |
| Hotspot display | Plain table | Animated bars + badges |
| Metrics | Text labels | Dashboard with gauges/charts |
| Theme | Basic dark/light | Modern gradients + shadows |
| Navigation | Manual scrolling | Click-to-jump animations |
| Feedback | Static | Smooth animations |

## Next Steps

- Read `UI_ENHANCEMENTS.md` for detailed component documentation
- Explore the source code in `src/main/java/com/embeddedcc/ui/components/`
- Customize colors in `src/main/resources/ui/enhanced-styles.css`
- Try different cache configurations to see performance impact
- Use block sweep to optimize your code automatically

## Support

For issues or questions:
1. Check the detailed documentation in `UI_ENHANCEMENTS.md`
2. Review component source code for API details
3. Examine the CSS files for styling customization

---

**Enjoy the beautiful new UI!** 🎨✨
