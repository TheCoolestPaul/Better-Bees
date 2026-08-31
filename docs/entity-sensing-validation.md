# Adaptive entity sensing validation

`ai.adaptive_entity_sensing` defaults to `true` on NeoForge and Fabric/Quilt.
Restart the world after changing it. With `false`, the replacement sensors call
the vanilla implementations at their native scheduled intervals, and the
pre-behavior demand hook does nothing.

Quiet adults deliberately stop publishing `NEAREST_LIVING_ENTITIES`,
`NEAREST_VISIBLE_LIVING_ENTITIES`, and `NEAREST_VISIBLE_ADULT`. Mods that read
these memories independently of Better Bees behaviors should use the opt-out.
Player, temptation, and hurt sensors are not replaced.

## Implementation boundaries

- The two replacement sensors retain vanilla's independently staggered 20-tick
  scheduling. Forced refreshes do not reset the native countdown.
- Querying, candidate filtering, sorting, and per-bee visibility remain vanilla
  operations. The 1.21.1 implementation uses its fixed radius; later targets use
  their native follow-range attribute rules.
- The bee-only Brain hook runs after sensors and before behavior startup or
  ticking. Damage, anger, targeting, mating, and becoming a baby wake sensing.
- Demand flags, dimension keys, refresh timestamps, and diagnostic scan counters
  belong to individual sensor instances. They are not serialized and retain no
  entity or level references. Nearby entity references live only in vanilla
  Brain memories, which are cleared on entering the quiet state.
- The nearest-adult sensor refreshes nearby data first on a baby's initial
  scheduled adult scan. Both sensors suppress same-tick duplicate refreshes.
- The adult sensor's parameter changes from `AgeableMob` to `LivingEntity` in
  1.21.8; the overlay preserves that signature without replacing its algorithm.
- From 1.21.4 onward, forced scans also call vanilla's private targeting-range
  preparation through a mixin bridge. This avoids inheriting the shared targeting
  conditions' range from another entity while leaving the periodic countdown
  untouched. The 1.21.1 bridge is a no-op because its targeting range is fixed.

## Regression coverage

Eight additional shared GameTests cover:

1. Quiet adults doing zero nearby/adult scans over three scheduled intervals,
   and the opt-out doing three native scans instead.
2. All eight demand predicates waking through the actual Brain hook, with no
   duplicate query when a periodic scan also occurs that tick.
3. Three periodic scans over sixty ticks of continuous demand.
4. Adult-first baby scheduling, native candidate membership and distance order,
   visible-adult selection behind/around a wall, and successful visible melee.
5. Two live AI bees mating successfully.
6. Removed entities leaving subsequent snapshots, clearing all three memories
   on quiet entry, and fresh sensor state after hive occupant reconstruction.
7. A villager retaining its native sensor and memories; dimension-key changes
   invalidating the refresh guard when a second dimension is available.
8. Forced nearby and nearest-adult sensing after a different bee's native scan
   sets a much shorter targeting range; comparison against a full native tick.

The dimension-key check is not a full entity teleport test. Hive reconstruction
checks transient state; the existing hive release and 60-bee return-home tests
exercise actual occupant operations. Native comparison uses the current target's
vanilla implementation as an oracle, not a copied query implementation.

Run `./gradlew buildAll gameTestAll performancePolicyTest` for the twelve default
NeoForge/Fabric lanes. The endpoint matrix from
`python scripts/ci/target-matrix.py validation` also defines the four supported
Quilt lanes and latest loader/API/Jade combinations.

## Performance experiment

Passing regressions or reducing scan counts does not establish a tick-time gain.
No performance percentage should be inferred from these tests or GameTest runtime.

Use the **same built artifact** with adaptation enabled and disabled, restarting
between settings. Use disposable copies of an identical world snapshot per pair.
Reference targets are **1.21.1 and 26.2**. Keep loader/JVM versions, heap, seed,
simulation distance, hive/flower placement, other mods, and nearby non-bee mob
counts identical. Run only one measured server at a time.

For **20, 60, and 120 bees**, warm up for at least 60 seconds and collect **three
60-second wall-clock samples** for each scenario and setting:

| Scenario | Fixture requirements |
| --- | --- |
| Ordinary foraging | Clear daylight, accessible flowers and hives, quiet adults |
| Return burst | Identical bees outside their hives, then rain or return demand |
| Combat | Reproducible anger/targets with both clear and obstructed sight lines |
| Mating | Reproducible pairs and love state; record births and demand transitions |
| Baby following | Fixed age distribution and accessible visible adults |

Record total living bees, outside/inside counts, active-sensing bees, babies,
mates, combatants, nearby non-bee mobs, and completed hive entries. Do not report
a low scan count without reporting how many bees were actually outside and active.
For short return bursts, reset the fixture consistently between repetitions and
report the fraction of the sample spent outside; do not silently compare one
empty apiary to one active apiary.

Both replacement sensors expose `scanCount()` for actual delegated-scan counts,
including with adaptation disabled. Sample these on the server thread and track
entity lifetimes: entering a hive or recreating an entity starts a new counter.
Do not add a neighborhood query per bee just to collect telemetry.

Collect sensing CPU and allocation samples with a JVM profiler. Attribute both
native scan stacks and `BeeSensing.beforeBehaviors`/`updateDemand` stacks so the
new per-tick transition-check overhead remains visible. Keep profiler overhead
and settings identical for the two modes. Report counts and sampled CPU separately;
sampled CPU is not an exact per-invocation timer.

Capture individual server tick durations with appropriate tick instrumentation
and calculate median/p95/p99 per sample. Minecraft's averaged tick-time JFR events
cannot supply individual-tick percentiles. Record hardware, raw captures,
instrumentation, warm-up, wall-clock duration, and fixture population changes.

Suggested results columns:

```text
minecraft,loader,artifact_sha256,adaptive,scenario,initial_bees,sample,
wall_seconds,mean_outside_bees,mean_demanding_bees,non_bee_mobs,
nearby_scans,adult_scans,sensing_sampled_cpu_ms,transition_sampled_cpu_ms,estimated_allocated_bytes,
server_tick_median_ms,server_tick_p95_ms,server_tick_p99_ms,raw_capture
```

Do not combine samples into a single speedup unless their populations and work
are comparable. Report remaining hot stacks from captures, rather than guessing
that pathfinding or flower scanning is the next bottleneck.

## Local execution record

On 2026-08-31, the final source passed:

- `buildAll`, `gameTestAll`, and `performancePolicyTest` at the configured floors
  for all six Minecraft targets on NeoForge and Fabric: 43 Better Bees GameTests
  on 1.21.1/1.21.4 and 44 reported tests on newer runners (43 Better Bees plus
  one Minecraft test). This includes the existing 60-bee return-home regression.
- A 20-row expanded build/GameTest matrix: latest NeoForge and Fabric endpoints
  for all six targets, and floor/latest Quilt endpoints for its four supported
  targets. Jade was enabled and its Better Bees server plugin registered in every
  latest-endpoint run. Transient Maven DNS failures were retried in clean Gradle
  processes; all retained final-code rows passed.
- Four real-config opt-out runs: NeoForge and Fabric on 1.21.1 and 26.2. Each
  logged `adaptive=false`, three nearby scans, and three adult scans over three
  scheduled intervals. The corresponding enabled runs logged zero quiet scans.
  Test config files were restored to `true` after each run.
- 24 Python CI-tooling tests. Four Unix-only smoke-launch tests were skipped on
  Windows. `git diff --check` reported no whitespace errors.

The GameTests use native sensor implementations as their comparison oracle, so
these results cover both 1.21.1's fixed range and later follow-range behavior.
NeoForge headless GameTests skipped client asset downloads; no new client launch
or in-world mod-interoperability session was performed for this change.

The 20/60/120-bee timed performance campaign above has **not** been captured.
There are no measured sensing-CPU, allocation, median/p95/p99 tick-time, or client
frame-time improvements to report, and remaining runtime bottlenecks are not yet
established. Test scan counts demonstrate the gating decision, not a speedup.
