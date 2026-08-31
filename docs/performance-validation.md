# Crowded-apiary validation

The optimization changes work counts, not the number of bees or their timers. Do not
infer a frame-rate or tick-time improvement from passing policy tests.

## Automated checks

Run `./gradlew performancePolicyTest buildAll gameTestAll`. On Windows the isolated
policy tests can also run with `./scripts/test-performance-policy.ps1` without
downloading Minecraft. The existing endpoint CI runner now runs the policy tests
alongside the GameTests on its full NeoForge/Fabric/Quilt matrix.

The policy tests cover 20/60/120-bee sound bursts, one shared fire scan per hive per
tick, independent hive budgets, clock resets, the interval-zero opt-out, eight-loop
selection, angry priority, hysteresis, and previously suppressed bees becoming
selected. GameTests cover fresh fire detection before entry, uncached vanilla fire
checks, hive replacement, unloaded lookup, navigation-budget restoration, prevention
of competing wander requests, block-update path recalculation, sixty live bees returning
to three hives, and full entry/emergency release under throttling, including detached hives.

## Before/after measurement protocol

Use separate baseline and changed builds with the same Minecraft/loader versions,
JVM arguments, seed, video settings, sound settings, camera path, and world snapshot.
Never reopen a newer world with an older Minecraft version. Use disposable benchmark
worlds, not a player's existing apiary. Keep indoor breeding disabled for measurements
so the population stays fixed; restore the normal setting for breeding regressions.

For each build test 20, 60, and 120 bees, distributed over 1, 3, and 6 hives at the
default capacity. Warm the world and JVM for at least 60 seconds before collecting
three 60-second samples per case:

| Case | Setup |
| --- | --- |
| Foraging | Daylight, clear weather, accessible flowers and entrances |
| Departure burst | Mature occupants in every hive, then change night to day |
| Return burst | Bees outside, then change clear weather to rain |
| Obstructed entrances | Same setup, with solid blocks directly in front of hives |

Repeat with sound enabled and with the Neutral Creatures and Blocks categories muted.
Record server median/p95/p99 tick durations and client median/p95/p99 frame durations
using a profiler that records individual ticks/frames. Capture JVM allocation and
pathfinding stacks with JFR, plus sound packet and active-channel counts. Minecraft's
averaged server-tick JFR events must not be presented as individual-tick percentiles.
Keep raw captures, hardware details, sample duration, and bee counts with the results.

Expected structural bounds: AI fire scans are shared per loaded hive per tick (fresh
entry and vanilla emergency checks are additional); an individual hive emits at most
one combined transition sound every five ticks; the client owns at most eight buzz
loops by default. These are bounds, not measured speedup percentages.

## Client listening and lifecycle checks

- Walk between two populated apiaries: nearer bees should replace farther bees,
  without rapidly alternating between equally close bees.
- Anger a bee, then let it calm down: the corresponding vanilla loop must change,
  without a duplicate loop. Hurt/sting/death cues must remain audible.
- Move out of range and back, let bees enter and leave, and unload/reload the chunk:
  no orphan loops, and previously suppressed bees must be able to play again.
- Reload sound resources, disconnect/reconnect, and change dimension: no retained
  references or permanently silent apiaries. Verify master/neutral volume mute and
  unmute recovers playback.
- Relaunch with adaptive audio disabled and server interval zero: vanilla buzz
  admission and every hive transition sound should be restored.

Client lifecycle checks and actual before/after performance captures require a running
client. The dependency-free selector tests do not validate the audio engine or replace
those checks.

## Local validation record (2026-08-31)

- Built all six NeoForge and all six Fabric targets at their configured dependency floors.
- Ran the shared GameTest suite, including the 60-bee return scenario, across those
  twelve targets: 35 Better Bees tests per target (newer runners also include one
  Minecraft test). The NeoForge fixture uses wider spacing to prevent neighboring
  release/harvest tests from interfering. Return-test bees have an age cooldown so
  incidental births cannot change its fixed population.
- Ran builds and GameTests on all four supported Quilt floor lanes.
- Passed the four standalone policy scenarios and 24 Python tooling tests. Four
  Unix-specific tooling tests were skipped on Windows.
- Started NeoForge 1.21.1 and Fabric 26.2 clients, confirmed resource loading and sound
  engine startup, and closed them cleanly. These are startup checks, not in-world
  listening or lifecycle validation.

Logs are local build artifacts: `build/validation-complete.log`,
`build/validation-quilt.log`, `build/validation-client-1.21.1.log`, and
`build/validation-client-26.2.log`. Headless NeoForge tests skipped client asset
downloads; normal client smoke launches used real downloaded assets.

Still outstanding: the full latest/floor endpoint matrix with Jade, every client and
server smoke lane, in-world audio lifecycle/listening checks, and the before/after
20/60/120-bee profiles above. No FPS, tick-time, allocation, packet-count, or audio-channel
improvement has been measured; remaining performance bottlenecks are not yet established.
