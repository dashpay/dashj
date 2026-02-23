# Comparison: CoinJoinCoinSelector (Greedy) vs. the Knapsack Problem

## The Knapsack Problem

The classic **0/1 Knapsack Problem** is: given a set of items each with a weight and value, select a subset that maximizes total value without exceeding a weight capacity. It is NP-hard and typically solved optimally via dynamic programming in O(n*W) pseudo-polynomial time.

## How CoinJoinCoinSelector Differs

The greedy algorithm in `findBestCombination` (CoinJoinCoinSelector.java:127) is **not** a standard knapsack problem. Here's why:

### 1. No weight/value trade-off

In a knapsack, each item has independent weight and value dimensions. In CoinJoin coin selection, each UTXO's "weight" (denomination) **is** its value. There's no optimization of value-per-unit-weight — every coin contributes exactly its face value. This makes it closer to the **Subset Sum Problem** (a special case of knapsack where weight = value): "find a subset of coins that sums to at least the target."

### 2. Fixed denominations with multiplicities

CoinJoin uses a small set of standard denominations (0.001, 0.01, 0.1, 1, 10 DASH). This is actually the **Bounded Knapsack / Change-Making Problem** — select counts of fixed denominations to meet a target. The greedy approach works well here because denominations are powers of 10, which makes greedy optimal for exact coverage (similar to how greedy works for US coin denominations).

### 3. Multi-phase greedy heuristic, not DP

The algorithm uses 5 sequential phases rather than dynamic programming:

| Phase | Strategy | Knapsack Analog |
|-------|----------|-----------------|
| **Phase 1** (line 143) | Largest-first greedy fill | Greedy fractional knapsack (but with discrete items) |
| **Phase 2** (line 197) | Smallest-first to cover fees | Gap-filling heuristic |
| **Phase 3** (line 224) | Use one larger denom if small ones insufficient | "Overshoot" fallback |
| **Phase 4** (line 257) | Consolidate 10 small → 1 large | Input count optimization (reduces tx size/fee) |
| **Phase 5** (line 331) | Remove unnecessary small inputs | Pruning excess |

### 4. The objective function is different

Knapsack maximizes value within capacity. CoinJoin minimizes inputs (to reduce fees) while meeting or exceeding the target + fee. The fee itself depends on the number of inputs selected (lines 171-187), creating a **feedback loop** that doesn't exist in standard knapsack — adding an input increases the fee, which may require adding another input.

### 5. No optimality guarantee

A true knapsack DP solution guarantees optimality. This greedy approach can miss better combinations. For example, Phase 1 greedily takes as many of each large denomination as possible, but sometimes using fewer large + more medium denominations would result in less overshoot and fewer total inputs.

## Summary

| Property | Knapsack | CoinJoinCoinSelector (Greedy) |
|----------|----------|-------------------------------|
| **Problem type** | Maximize value ≤ capacity | Minimize inputs ≥ target + fee |
| **Items** | Arbitrary weight/value | Fixed denominations (weight = value) |
| **Algorithm** | DP (optimal) | Multi-phase greedy (heuristic) |
| **Complexity** | O(n*W) pseudo-polynomial | O(n) linear scan per phase |
| **Feedback loop** | None | Fee depends on # inputs selected |
| **Optimality** | Guaranteed | Not guaranteed |
| **Closest classic problem** | 0/1 Knapsack | Bounded Change-Making with variable cost |

## Dash Core C++ KnapsackSolver Comparison

The C++ `KnapsackSolver` in Dash Core is also **not** a true knapsack solver. It is a subset sum approximation using stochastic random search.

### What the C++ code actually does

**Step 1: Trivial checks**
- Exact match → return immediately
- Partition into `applicable_groups` (< target + change) and `lowest_larger` (smallest single coin ≥ target)
- If all smaller coins sum to exactly the target → use them all
- If all smaller coins sum to less than target → use `lowest_larger` as a single overshoot

**Step 2: `ApproximateBestSubset` — stochastic random search**
This is the core algorithm. It uses randomized iterative inclusion/exclusion (1000 iterations of random subset sampling) to find a subset close to the target. This is a **Monte Carlo approximation**, not dynamic programming.

**Step 3: Compare stochastic result vs. single larger coin**
Pick whichever is closer to the target.

### Why it's named "Knapsack"

Bitcoin Core originally named it `KnapsackSolver` because subset sum is a special case of knapsack (where weight = value). The name stuck even though the implementation is a randomized approximation, not the textbook DP knapsack. This naming has been discussed in Bitcoin Core — it is acknowledged as a misnomer.

### Three-way comparison

| Aspect | True 0/1 Knapsack | C++ KnapsackSolver | Java CoinJoinCoinSelector |
|--------|-------------------|---------------------|---------------------------|
| **Algorithm** | Dynamic programming | Stochastic random search | Multi-phase greedy |
| **Optimality** | Guaranteed | Probabilistic (not guaranteed) | Not guaranteed |
| **Complexity** | O(n*W) | O(1000*n) ≈ O(n) | O(n) per phase |
| **Approach** | Exact | Random sampling + heuristics | Denomination-aware greedy |
| **Problem solved** | Max value ≤ capacity | Subset sum ≈ target | Minimize inputs ≥ target + fee |

### Key differences between C++ and Java implementations

1. **C++ uses randomized search** (`ApproximateBestSubset`) — tries random subsets to get close to the exact target, aiming for minimal overshoot
2. **Java uses deterministic greedy** — fills largest-first, then adjusts with smaller denominations, then optimizes input count
3. **C++ handles arbitrary UTXO values** — works with any coin amounts
4. **Java assumes CoinJoin denominations** — exploits the power-of-10 structure for a simpler algorithm
5. **C++ has a two-pass denom strategy** — first tries non-denominated coins, then falls back to denominated (to preserve mixing liquidity)
6. **Java's fee is dynamic** — fee depends on selection size (feedback loop), while C++ treats fee as known beforehand for fully-mixed transactions

## Conclusion

Neither the C++ `KnapsackSolver` nor the Java `CoinJoinCoinSelector` is a true knapsack algorithm. Both are heuristic solvers for what is fundamentally a subset sum / change-making problem. The greedy approach in Java is a pragmatic trade-off: it runs fast on potentially large UTXO sets and produces good-enough results for the power-of-10 denomination structure, even though it can't guarantee the minimum possible number of inputs in all cases.