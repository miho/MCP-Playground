# Why Not Maximize Green Time?

## The Question

**Scenario:** E=10, W=10, N=0, S=0 (all traffic on E-W)

**Intuition:** Give E-W maximum green time (60s)! More green = better, right?

**Reality:** Optimal is often around 35-45s, not 60s. Why?

## The Problem with Very Long Cycles

### Scenario A: Maximum Green (EW=60s, NS=10s)

```
Cycle breakdown:
├─ NS Green: 10s   (minimum for safety)
├─ NS Yellow: 3s
├─ NS All-Red: 1s
├─ EW Green: 60s   ← MAXIMUM
├─ EW Yellow: 3s
└─ EW All-Red: 1s

Total cycle: 78 seconds
```

**What happens to arriving vehicles?**

Vehicle arrives at time T in the cycle:
- **T=0-10s** (NS green): Must wait ~14s for EW green to start
- **T=10-14s** (NS yellow/red): Must wait ~10s for EW green
- **T=14-74s** (EW green): Gets through quickly! ✓
- **T=74-78s** (EW yellow/red): Must wait ~14s + full cycle!

**The problem:**
- Vehicles arriving at T=74s miss the green by seconds
- They must wait **14s + 60s = 74s** for the next EW green!
- This creates a huge spike in delay for unlucky vehicles

### Scenario B: Shorter Cycle (EW=35s, NS=10s)

```
Cycle breakdown:
├─ NS Green: 10s
├─ NS Yellow: 3s
├─ NS All-Red: 1s
├─ EW Green: 35s   ← SHORTER
├─ EW Yellow: 3s
└─ EW All-Red: 1s

Total cycle: 53 seconds
```

**What happens to arriving vehicles?**

Vehicle arrives at time T:
- **T=0-10s** (NS green): Must wait ~14s for EW green
- **T=10-14s** (NS yellow/red): Must wait ~10s
- **T=14-49s** (EW green): Gets through quickly! ✓
- **T=49-53s** (EW yellow/red): Must wait ~14s + next cycle

**The benefit:**
- Vehicles arriving at T=49s wait 14s + 35s = 49s for next green
- Much better than 74s!
- The cycle repeats more frequently

## The Mathematics: Average Delay

### Webster's Delay Formula (Simplified)

```
Average Delay = (Cycle × (1 - green_ratio)²) / (2 × (1 - utilization))
                + Random delay component
```

**Key insight:** Delay increases with cycle length!

### Numerical Example: E=10, W=10 (20 veh/min total)

**Long Cycle (78s, EW=60s):**
- Green ratio: 60/78 = 76.9%
- Capacity: 1800 × 0.769 = 1384 veh/hr per lane = 23 veh/min per lane
- Total capacity: 46 veh/min
- Utilization: 20/46 = 43.5%
- Base delay: (78 × (1-0.769)²) / (2 × (1-0.435)) = 4.1/1.13 = **3.6s**
- BUT vehicles arriving late in cycle wait up to 78s!
- Average delay including this: **~18-22 seconds**

**Medium Cycle (53s, EW=35s):**
- Green ratio: 35/53 = 66%
- Capacity: 1800 × 0.66 = 1188 veh/hr per lane = 19.8 veh/min per lane
- Total capacity: 39.6 veh/min
- Utilization: 20/39.6 = 50.5%
- Base delay: (53 × (1-0.66)²) / (2 × (1-0.505)) = 6.1/0.99 = **6.2s**
- Vehicles arriving late wait up to 53s (not 78s!)
- Average delay: **~12-16 seconds** ← BETTER!

**Why the medium cycle wins:**
- Yes, capacity is slightly lower (39.6 vs 46 veh/min)
- But utilization is still comfortable (50.5%)
- The shorter cycle means less variability in wait times
- Average delay is lower because no one waits 74+ seconds

## Visual Explanation

### Long Cycle Problem

```
Time: 0    10   14              74  78    88  92             152 156
      |NS|--|EW Green           |--|  |NS|--|EW Green        |--|

Vehicle A arrives at t=15: Waits 0s, gets through immediately ✓
Vehicle B arrives at t=73: Waits 1s, gets through in current green ✓
Vehicle C arrives at t=75: Waits 3s + 10s + 60s = 73s! ✗✗✗
Vehicle D arrives at t=150: Waits 2s, gets through immediately ✓

Average for 4 vehicles: (0 + 1 + 73 + 2) / 4 = 19s
```

### Shorter Cycle

```
Time: 0    10   14        49  53    63  67       102 106
      |NS|--|EW Green |--|  |NS|--|EW Green|--|

Vehicle A arrives at t=15: Waits 0s ✓
Vehicle B arrives at t=48: Waits 1s ✓
Vehicle C arrives at t=50: Waits 3s + 10s + 35s = 48s (not 73s!)
Vehicle D arrives at t=100: Waits 2s ✓

Average for 4 vehicles: (0 + 1 + 48 + 2) / 4 = 12.75s ← BETTER!
```

## The Optimal Balance

Traffic engineers use formulas to find the optimal cycle length. The key factors:

### 1. **Cycle Length Sweet Spot: 60-120 seconds**

- **Too short** (<40s):
  - Too many yellow/all-red transitions
  - Lost time per cycle = 8s (two transitions × 4s each)
  - If cycle = 30s, lost time = 8/30 = 27% waste!

- **Too long** (>120s):
  - Vehicles arriving late in red wait too long
  - Average delay increases linearly with cycle length
  - If cycle = 180s, some vehicles wait 180s!

- **Optimal** (60-90s):
  - Reasonable lost time percentage (8/70 = 11%)
  - Reasonable maximum wait time (~70s worst case)
  - Good balance

### 2. **Green Ratio vs Cycle Length**

For the same green ratio, shorter cycle = lower average delay:

**Example: 70% green ratio**

- Cycle 60s: EW green = 42s, max wait = 18s
- Cycle 90s: EW green = 63s, max wait = 27s
- Cycle 120s: EW green = 84s, max wait = 36s

Even though all have 70% green ratio, shorter cycle = less delay!

### 3. **The Formula for Optimal Cycle Length**

Webster's formula for optimal cycle:
```
C_optimal = (1.5L + 5) / (1 - Y)

Where:
L = Lost time per cycle (yellow + all-red for all phases) ≈ 8s
Y = Sum of critical flow ratios ≈ 0.5 for our case

C_optimal = (1.5 × 8 + 5) / (1 - 0.5) = 17 / 0.5 = 34s

But minimum cycle for safety: ~40s
Practical optimal: 50-70s
```

## What the LLM Optimizer Finds

When you run "LLM Optimize" with E=10, W=10, N=0, S=0:

**It tests:**
1. Short cycle (60s): NS=25s, EW=25s → delay ~18s
2. Medium cycle (80s): NS=35s, EW=35s → delay ~14s
3. Long cycle (100s): NS=45s, EW=45s → delay ~16s
4. NS priority (60s): NS=30s, EW=20s → delay ~22s (bad for EW traffic!)
5. EW priority (60s): NS=20s, EW=30s → delay ~15s

**Winner:** Medium cycle with balanced green (or slightly EW-favored)
- **Not maximum** because cycle length matters more than green time!

## Try It Yourself

```bash
./gradlew run
```

**Experiment:**

1. Set E=10, W=10, N=0, S=0
2. Set speed to 5x

3. **Try maximum green:**
   - NS Green: 10s
   - EW Green: 60s
   - Apply Timing
   - Note delay: ~18-22s

4. **Try medium green:**
   - NS Green: 10s
   - EW Green: 35s
   - Apply Timing
   - Note delay: ~12-16s ← BETTER!

5. **Try short green:**
   - NS Green: 10s
   - EW Green: 20s
   - Apply Timing
   - Note delay: ~14-18s (worse than medium)

6. **Use LLM Optimize:**
   - See what it chooses
   - Likely picks 30-40s for EW (medium range)

## Key Takeaways

1. **Longer cycles aren't always better** - they create longer maximum wait times

2. **The cycle repeats** - shorter cycles mean faster recovery if you miss the green

3. **Optimal is around 50-80s total cycle** for most intersections

4. **Diminishing returns** - going from 30s to 40s green helps a lot, but 50s to 60s helps very little

5. **Balance matters** - even with zero NS traffic, giving it 10s is fine because it keeps the cycle short

## The Bottom Line

**Maximum green time maximizes capacity.**
**Optimal green time minimizes average delay.**

For our scenario (E=10, W=10), we have plenty of capacity with EW=35s. Going to EW=60s adds capacity we don't need, while increasing the cycle length and creating longer wait times for unlucky vehicles.

**Trade more capacity we don't need for lower average delay!** ✓
