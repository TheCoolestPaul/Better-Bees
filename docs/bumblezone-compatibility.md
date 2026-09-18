# Bumblezone compatibility (Minecraft 1.21.1)

The optional adapter is packaged in Better Bees' NeoForge and Fabric 1.21.1
jars. Quilt uses the same Fabric jar. Bumblezone, Jade, Create, and their
libraries are not bundled into Better Bees. Other Minecraft targets contain
no Bumblezone adapters. No configuration or world-data migration is required.

## Behavior

- Successful player shearing keeps Better Bees' configured base honeycomb roll
  and honey cost, then invokes Bumblezone's Comb Cutter bonus/advancement once,
  before damaging the tool. Create's stationary and mounted deployers follow
  the same path. Pipes and ordinary dispensers do not receive this bonus.
- Essence of the Bees protects harvesting without smoke. Nearby-bee target
  selection excludes protected players. Smoke and Create deployer protection
  still apply independently.
- With Bumblezone present, an exception-safe method wrapper replaces Better
  Bees' hive-release redirect. Bumblezone's original release-call injection
  stays intact; nested release sound contexts are restored in `finally`.
  Without Bumblezone, the existing release mixin remains active.
- Flower Headwear runs as a separate Brain activity. It considers living
  wearers within 20 blocks, including nonplayers and Bumblezone-supported
  equipment slots, independently of adaptive entity sensing. Combat, home
  intent, breeding, and food temptation take precedence. Losing eligibility,
  removing headwear, leaving range, or gaining Wrath stops attraction.
- Variant bees use Brain breeding instead of their additional BreedGoal.
  Partner selection preserves `canMate` and the parent's offspring factory;
  variant children receive Brain memories and individual scale. Indoor
  breeding and serialized hive occupants retain variant identity.
- Bumblezone's Mob attack targets are synchronized before Brain execution.
  Wrath starts combat; Hidden and calming clear stale Brain attack targets.
- Jade continues to report authoritative honey and occupant fractions through
  the existing payload and overlay. Create's fluid accounting and transported
  hive storage are unchanged.

## Pinned local stacks

These are the validated combinations, not a claim about every older loader or
every release inside the optional dependency metadata's version range.

| Component | NeoForge | Fabric / Quilt |
| --- | --- | --- |
| Minecraft / Java | 1.21.1 / 21 | 1.21.1 / 21 |
| Loader | NeoForge 21.1.249 | Fabric 0.19.5 / Quilt 0.30.1 |
| Fabric API | — | 0.116.15+1.21.1 |
| Bumblezone | 7.16.1+1.21.1-neoforge | 7.16.0+1.21.1-fabric |
| Jade | 15.10.6+neoforge | 15.10.6+fabric |
| Resourceful Lib | 3.0.12 | 3.0.12 |
| Athena | 4.0.6, nested in Bumblezone | 4.0.6, explicit development runtime |
| MidnightLib | — | 1.5.7-fabric |
| Create | 6.0.10 (Maven build 280) | Not tested/supported by this adapter |

Create uses Ponder 1.0.82, Flywheel 1.0.6, and Registrate MC1.21-1.3.0+67.
Athena must be explicit in the Fabric development runtime because Loom does
not load Bumblezone's nested model library there. The normal Bumblezone
release already contains it. The adapter uses MixinExtras' method wrapper;
use the tested loader stacks above for Bumblezone compatibility. Baseline
Better Bees builds and tests retain their existing minimum loader versions.

## Reproducing validation

`withBumblezone` selects the latest pinned loader/API/Jade defaults for the
1.21.1 project. Explicit version overrides remain available. Unsupported
Minecraft tasks and NeoForge/Quilt combinations fail configuration.
Profile run directories include the loader and enabled integrations.

```powershell
.\gradlew.bat :mc1_21_1:runGameTestServer -PwithBumblezone=true -PwithCreate=true -PwithJade=true
.\gradlew.bat :fabricMc1_21_1:runGameTest -Pfabric_target=fabricMc1_21_1 -PwithBumblezone=true -PwithJade=true
.\gradlew.bat :fabricMc1_21_1:runGameTest -Pfabric_target=fabricMc1_21_1 -PwithBumblezone=true -PwithJade=true -PwithQuilt=true

# Eleven isolated local lanes, with logs and results.json; fails on the first failure.
.\scripts\local\test-bumblezone.ps1
```

Use `:mc1_21_1:runClient` or `:fabricMc1_21_1:runClient` with the same flags
for client checks; use `runServer` for ordinary dedicated-server checks.
The existing smoke checker accepts `--bumblezone` alongside `--jade` and
`--create`, requires the integration activation marker, and rejects fatal
initialization errors. Optional runtime tests remain local; hosted CI's
runtime matrix is unchanged.

Build release jars **without** optional runtime flags. Those flags include
development integration tests; normal builds exclude them. The jar verifier
checks adapter placement and rejects bundled optional mod classes and
optional integration test classes.

## Local results — 2026-09-18

The eleven-lane matrix covers NeoForge baseline, Bumblezone, Bumblezone+Jade,
Create+Jade, and all three; Fabric and Quilt each cover baseline, Bumblezone,
and Bumblezone+Jade. The combined NeoForge suite includes 65 tests; the
Bumblezone Fabric/Quilt suites include 54. Jade's actual server provider is
exercised when Jade is installed; its test returns early in Jade-absent lanes.

The initial full matrix passed all eleven lanes. A later repeat exposed an
intermittent variant-breeding fixture failure on Quilt+Jade. The fixture now
encloses the autonomous mating pair, restricts offspring assertions to that
enclosure, and uses a dedicated 32-block Fabric/Quilt test structure to separate
neighboring tests. It retains real AI, adaptive sensing, and the original
timeout; no retry or forced offspring is used.
The final isolated suites passed all 65 NeoForge, 54 Fabric, and 54 Quilt tests
with their combined integrations enabled.

Regression coverage includes Comb Cutter drops/advancement/durability and
insufficient honey; essence harvesting and explicit emergency release;
headwear priority and invalidation; Wrath/Hidden/calming; variant and mixed
Brain breeding; indoor breeding, hive items, relocated home memory; actual
Jade provider data; and enchanted stationary/mounted Create deployers plus
over-capacity variant hive assembly, serialization, movement, and release.
Existing Create pipe, filter, simulation, buffering, collection, and spout
tests run with Bumblezone installed.

All twelve baseline artifacts built. Eleven baseline GameTest lanes passed
in the broad run; NeoForge 1.21.4's existing `adaptiveSensingPermitsMating`
test failed once and its isolated fresh-world rerun passed. No automatic
retry or weakened assertion was added. Release jars were inspected for
adapter scope, remapped Fabric method selectors, and absence of bundled
optional mods and integration test classes.

Ordinary dedicated-server startup passed on NeoForge, Fabric, and Quilt.
NeoForge and Fabric clients reached healthy initialized states and connected
to their respective local servers. The initial Fabric client failure exposed
the missing Athena development dependency; the corrected launch passed.

The NeoForge client was visually checked inside Bumblezone with Jade and
Create loaded: Jade showed `Honey: 13/20`, `Bees: 1/20`; an actual Comb Cutter
harvest granted its advancement and immediately updated Jade to `12/20` and
`0/20`. A live variant moved toward a Flower Headwear wearer, and a Blue Bee
rendered at its synchronized individual scale (observed attribute: 0.264375).

Desktop control was stopped by the user before Fabric/Quilt visual checks.
Fabric startup/connection is verified, but its overlay/gameplay visuals are
not. Quilt client startup and visual gameplay remain unverified. Automated
tests do not replace those remaining checks. Fabric/Quilt Create ports and
other Minecraft versions are outside this integration's support boundary.
