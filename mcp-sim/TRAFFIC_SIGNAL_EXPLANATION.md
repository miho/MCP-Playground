# Understanding Traffic Signal Delay

## Why Does Delay Increase When Multiple Directions Have Traffic?

### Scenario Comparison

**Scenario 1: E=10, W=0, N=0, S=0**
- Only East has traffic (10 veh/min)
- Total demand: 10 veh/min

**Scenario 2: E=10, W=10, N=0, S=0**
- Both East and West have traffic (10 veh/min each)
- Total demand: 20 veh/min

### What Happens with a Fixed Signal Plan (NS=25s, EW=25s)

#### Cycle Breakdown
```
Total cycle: 60 seconds
├─ NS Green: 25s  (WASTED when NS has no traffic!)
├─ NS Yellow: 3s
├─ NS All-Red: 1s
├─ EW Green: 25s  (NEEDED for E=10, W=10)
├─ EW Yellow: 3s
└─ EW All-Red: 1s
```

## The Problem: Inefficient Green Time Allocation

### Scenario 1 Analysis (E=10 only)

**Green Time Allocation:**
- NS gets 25s but has NO traffic → **25s wasted**
- EW gets 25s and has 10 veh/min demand → Could be efficient

**What happens:**
- East vehicles arrive during the full 60s cycle
- Some arrive during NS green (wasted 25s) → they wait
- Some arrive during EW green (useful 25s) → they can leave
- Average wait time includes those who arrived during NS green

**Average delay:** ~15-20 seconds per vehicle

### Scenario 2 Analysis (E=10, W=10)

**Green Time Allocation:**
- NS gets 25s but has NO traffic → **Still 25s wasted!**
- EW gets 25s but now has 20 veh/min total demand → Getting tight

**What happens:**
- Twice as many vehicles arriving (20 instead of 10)
- They ALL have to wait through the wasted NS phase (25s)
- During EW green (25s), both queues discharge, but:
  - More vehicles accumulated during red
  - Longer average queue lengths
  - Some vehicles don't clear in one green phase

**Average delay:** ~25-30 seconds per vehicle (HIGHER!)

## Why the Delay Increases

### 1. **Wasted Cycle Time**
When NS has no traffic but gets 25s of green:
- 41.7% of the cycle (25/60) is wasted
- EW vehicles arriving during this time must wait
- More EW traffic = more vehicles waiting through wasted time

### 2. **Increased Queue Formation**
```
With E=10, W=0:
  Red Phase (NS): East queue grows
  Green Phase (EW): East queue empties
  Max queue: ~4-5 vehicles

With E=10, W=10:
  Red Phase (NS): BOTH East and West queues grow
  Green Phase (EW): BOTH queues must empty
  Max queue: ~8-10 vehicles total
  Each vehicle waits longer on average
```

### 3. **Cycle Efficiency**
- **Green Ratio** = Useful green / Total cycle
- Scenario 1: 25s useful / 60s total = 41.7% efficient
- Scenario 2: 25s useful / 60s total = 41.7% efficient (same!)
- BUT the demand doubled, so utilization is higher

## The Math: Queuing Theory

### Capacity vs Demand

**EW Direction Capacity:**
- Saturation flow: 1800 veh/hr per lane
- Green time: 25s per 60s cycle = 41.7%
- Effective capacity per lane: 1800 × 0.417 = 750 veh/hr = 12.5 veh/min

**Scenario 1 (E=10):**
- Demand: 10 veh/min
- Capacity: 12.5 veh/min
- Utilization: 10/12.5 = 80%
- Delay formula (Webster): delay ≈ 15-20 seconds

**Scenario 2 (E=10, W=10):**
- Total demand: 20 veh/min
- Total capacity: 25 veh/min (12.5 × 2 lanes)
- Utilization: 20/25 = 80% (same!)
- But each direction still at 80% individually
- Delay formula: delay ≈ 20-25 seconds

**Why higher delay?**
- Both queues building simultaneously during red
- Random variation in arrivals compounds
- More vehicles in system = more total waiting

### Webster's Delay Formula (Simplified)

```
Delay ≈ (Cycle × (1 - green_ratio)²) / (2 × (1 - utilization))
```

Even though utilization is the same (80%), having TWO queues at 80% instead of ONE creates more variability and longer delays.

## The Solution: Adaptive Timing

### Optimal Plan for E=10, W=10, N=0, S=0

Instead of NS=25s, EW=25s:

**Better plan: NS=10s, EW=45s**
- Give minimal time to NS (it has no traffic)
- Give maximum time to EW (it has all the traffic)

**Results:**
```
Total cycle: 63 seconds
├─ NS Green: 10s  (minimal waste)
├─ NS Yellow: 3s
├─ NS All-Red: 1s
├─ EW Green: 45s  (MORE time for heavy traffic)
├─ EW Yellow: 3s
└─ EW All-Red: 1s
```

**New efficiency:**
- Green ratio: 45/63 = 71.4% (up from 41.7%!)
- EW Capacity: 1800 × 0.714 = 1285 veh/hr = 21.4 veh/min per lane
- Total capacity: 42.8 veh/min
- Utilization: 20/42.8 = 47% (much better!)
- **Average delay: ~8-12 seconds** (MUCH LOWER!)

## Key Insights

### 1. It's Not Just About Queue Length
Yes, having cars on both E and W means two queues instead of one, but the real issue is:
- **Wasted green time** on directions with no traffic
- **Insufficient green time** on directions with traffic

### 2. The Cycle Matters
In a 60-second cycle with NS=25s, EW=25s:
- Every EW vehicle must wait through at least part of the 25s NS phase
- With more EW traffic, more vehicles experience this wait
- The average delay across all EW vehicles increases

### 3. Random Arrivals Create Variability
- Vehicles don't arrive perfectly evenly
- Sometimes East gets a burst, sometimes West
- Both queues can spike during the same red phase
- This creates longer delays than if traffic was perfectly uniform

## Try It Yourself

1. Set E=10, W=0, N=0, S=0
   - Apply baseline plan (NS=25s, EW=25s)
   - Note average delay

2. Set E=10, W=10, N=0, S=0
   - Keep same plan
   - Note delay increased!

3. Manually adjust timing:
   - NS Green: 10s
   - EW Green: 45s
   - Click "Apply Timing"
   - Watch delay DROP dramatically!

4. Use "LLM Optimize"
   - See what timing it chooses
   - Compare to your manual timing

## Conclusion

**Your hypothesis was partially correct!** Yes, having cars waiting on both sides contributes, but the deeper issue is:

> **Wasted green time on empty directions forces vehicles on busy directions to wait longer, increasing average delay across the system.**

The solution is **adaptive signal timing** that gives more green time to directions with more traffic!
