# Automation compatibility

## Dispenser bottles (issue #1)

The unmodified Fabric 26.2 regression failed because dispensing did not consume
authoritative honey. Vanilla checks only the 0–5 display state and resets that
state without updating `BetterBeesHoney`. At default settings this rejects
harvestable low amounts; even at capacity/cost 5/5 a vanilla harvest leaves real
storage full behind an empty display, preventing subsequent production.

The wrapper uses the resolved dispenser behavior for non-hive targets and vanilla's
`consumeWithRemainder` for successful hive output. Failed hive attempts eject the
empty bottle, without falling through to the display-based harvesting branch.
Droppers, water bottling, and dispenser shears retain their behavior.

Shared GameTests cover both hive types, capacity/cost 20/1 and 5/5, insufficient
honey, full storage, stale display values, stacked bottles, inventory overflow,
repeated activations, water, and droppers. The tests use a real dispenser block
and its normal dispensing entrypoint. The original 396-mod pack is not available;
isolated tests do not establish compatibility with every mod in that pack.

## Create profile

Only NeoForge 1.21.1 includes `betterbees.create.mixins.json`. Its plugin checks
Create's presence before applying adapters. Build dependencies are compile-only;
`-PwithCreate=true` adds the pinned runtime and integration GameTests. The profile
requires explicitly named `:mc1_21_1:` tasks and selects the latest pinned NeoForge
unless overridden. The compile artifact is Create 6.0.10-280, the upstream Maven
build corresponding to release 6.0.10.

Tests exercise actual Create deployer activation and mounted movement behavior,
open-pipe handlers, pipe buffer serialization, recipe-aware spout processing,
and bearing-contraption assembly/serialization/disassembly. Assertions cover
simulation, fluid filters, partial and oversized transfers, multiple consumers,
exhaustion, configured harvest cost, safe bee harvesting, honeycomb collection,
over-capacity contents, and released bees' relocated home memory.

The open-pipe guard is limited to Better Bees hives. It validates a requested
fluid before executing a world drain, limits output to the acquired batch, and
keeps any remainder in Create's existing buffer. No fractional honey save field,
new fluid, capability, or migration is introduced.

Run the integration suite:

```powershell
.\gradlew.bat :mc1_21_1:runGameTestServer -PwithCreate=true
```

Run baseline builds and GameTests without Create:

```powershell
.\gradlew.bat buildAll gameTestAll
```

Create integration tests are opt-in local checks; there is no dedicated Create CI
job. Existing CI lanes continue to test normal operation without Create. When run
locally with the Create profile, startup checks require both Better Bees
initialization and the Create integration marker.

## Local validation (2026-09-17)

- All twelve supported artifacts built; all twelve baseline GameTest lanes passed.
  The four modern NeoForge lanes were also rerun after adding their explicit test
  registrations, so the dispenser and relocated-home regressions execute there.
- Fabric 26.2 passed with Jade 26.2.11+fabric, including its server provider.
- The final Create profile passed all 54 tests, including filtered mounted deployer
  output. One preceding run failed the existing `adaptiveSensingPermitsMating`
  test; the unchanged rerun passed. No retry policy was added to CI.
- Create-enabled client and ordinary dedicated-server startup passed. Client
  startup without Create also passed.
- All twelve release jars were inspected: Create adapters occur only in the
  NeoForge 1.21.1 jar, no Create dependencies are bundled, and optional integration
  test classes are absent from the normal release jars.
- Smoke-checker, target-matrix, release-version, and Modrinth validation tooling
  tests passed. Shell syntax checks passed. The Unix process-launch tests skip
  on Windows; real local startup checks were run separately.

These checks cover the pinned local stacks. GitHub's complete minimum/latest
endpoint matrix and sequential world-upgrade jobs remain CI checks.
