# Better Bees

[![CI](https://github.com/TheCoolestPaul/Better-Bees/actions/workflows/ci.yml/badge.svg)](https://github.com/TheCoolestPaul/Better-Bees/actions/workflows/ci.yml)

Better Bees is a multi-version NeoForge and Fabric mod that gives vanilla bees a Brain-based AI,
raises bee nest and beehive capacity to 20, and lets eligible adult occupants
occasionally produce a stored baby while inside their hive. Hives also store
20 honey by default and support incremental bottle and shears harvesting.

The behavior is a 1.21.1 backport of Brainier Bees `main` at commit
`0ccfabf1752679e01fb3783aa7eb5679e8453a54`. It aims for behavior parity rather
than copying APIs from a newer Minecraft version. See `THIRD_PARTY_NOTICES.md`.

## Supported targets

Each Minecraft version has its own fully featured jar. Do not use one target's
jar on another Minecraft version.

| Minecraft | Java | NeoForge | Fabric Loader | Quilt Loader |
| --- | ---: | --- | --- | --- |
| 1.21.1 | 21 | 21.1.1-21.1.249 | 0.16.0-0.19.5 | 0.27.0-0.30.1 |
| 1.21.4 | 21 | 21.4.121-21.4.157 | 0.16.10-0.19.5 | 0.28.0-0.30.1 |
| 1.21.8 | 21 | 21.8.9-21.8.54 | 0.16.14-0.19.5 | 0.29.2-0.30.1 |
| 1.21.11 | 21 | 21.11.42-21.11.45 | 0.18.4-0.19.5 | 0.30.0-0.30.1 |
| 26.1.2 | 25 | 26.1.2.71-26.1.2.100 | 0.18.4-0.19.5 | Not available |
| 26.2 | 25 | 26.2.0.57-26.2.0.72 | 0.19.0-0.19.5 | Not available |

Install Better Bees on both clients and the server. Fabric API is required by
the Fabric jar. Quilt runs that exact Fabric jar with the matching Fabric API;
there is no separate Quilt artifact or QFAPI requirement. Quilt Loader currently
does not invoke Fabric entrypoints on the Java 25 / 26.x games, so Quilt support
is limited to the four 1.21.x targets until upstream support exists. Loader ranges in each
jar are intentionally limited to the lines tested for that target.

## Features

- Brain activities for combat, pollination, temptation, breeding, hive search,
  hive travel, crop growth, and ceiling-aware wandering.
- Each loaded hive builds one transient, shared flower index on demand. Its
  incremental scanner is capped per tick, never loads chunks, and uses soft
  reservations to spread nestmates across available flowers.
- Return-home intent remains latched through travel, homeless bees prefer less
  crowded valid hives, and temporary path failures are retried before a hive is
  blacklisted.
- Persistent memorized hive and AI cooldown data.
- Configurable hive/nest capacity, defaulting to 20.
- Staggered indoor breeding checks with no unloaded-chunk catch-up and no
  per-tick occupant deserialization.
- Existing over-capacity hives are retained when capacity is lowered.
- Authoritative 0-64 honey storage that survives saves and silk-touch items.
- Bottles and shears consume one honey by default; shears drop a uniformly
  random 1-3 honeycomb. Dispenser shears use the same rules.
- Vanilla campfire safety and unsmoked bee anger/release behavior are retained.
- The vanilla 0-5 state is a proportional display proxy; comparators emit a
  proportional 0-15 fullness signal.
- Optional Jade integration displays authoritative honey and occupant fractions
  such as `Honey: 14/20` and `Bees: 12/20`.
- Every bee has a stable UUID-derived physical scale between 20% and 50% by
  default, including matching model, shadow, eye height, and hitbox dimensions.

## Server configuration

NeoForge and Fabric create `world/serverconfig/betterbees-server.toml` for a world.
Settings require a restart.

| Setting | Default | Range |
| --- | ---: | ---: |
| `ai.max_wander_radius` | 25 | 1-128 |
| `ai.flower_locate_range` | 8 | 1-64 |
| `ai.search_attempts` | 10 | 1-100 |
| `ai.flower_scan_budget` | 32 | 1-512 |
| `ai.flower_cache_size` | 512 | 16-4096 |
| `ai.hive_path_failures_before_blacklist` | 3 | 1-10 |
| `hive.capacity` | 20 | 1-64 |
| `hive.honey_capacity` | 20 | 1-64 |
| `hive.harvest_cost` | 1 | 1-64 |
| `hive.shears_honeycomb_min` | 1 | 1-64 |
| `hive.shears_honeycomb_max` | 3 | 1-64 |
| `hive.indoor_breeding_enabled` | true | boolean |
| `hive.breeding_interval_ticks` | 1200 | 20-72000 |
| `hive.breeding_chance` | 0.05 | 0.0-1.0 |
| `appearance.minimum_bee_scale` | 0.20 | 0.0625-1.0 |
| `appearance.maximum_bee_scale` | 0.50 | 0.0625-1.0 |
| `audio.hive_transition_interval_ticks` | 5 | 0-100 |
| `ai.adaptive_entity_sensing` | true | boolean |

Adaptive entity sensing skips nearby-mob scans for quiet adult bees. Combat, mating,
damage, and baby-following needs wake sensing before behaviors run; active sensors
keep vanilla scan intervals, range rules, and visibility checks. Player detection,
temptation, and hurt sensing are unchanged. Quiet bees clear their nearby-entity
Brain memories; set `ai.adaptive_entity_sensing = false` and restart the world if
another mod needs those memories continuously. Sensor state is transient.
See [entity-sensing validation](docs/entity-sensing-validation.md) for regression
coverage, compatibility results, and the profiling protocol.

Hive entry and exit share one sound cooldown per loaded hive. The first sound plays
immediately; duplicates within the interval are discarded. This does not delay bees,
honey deposits, emergency releases, or block-change game events. Set the interval to
`0` to restore unrestricted entry/exit playback.

Clients also create `config/betterbees-client.toml` on both loaders. Restart the client
after changing these local settings:

| Setting | Default | Range |
| --- | ---: | --- |
| `audio.adaptive_bee_sounds` | true | boolean |
| `audio.max_bee_loops` | 8 | 1-64 |

Adaptive audio selects nearby audible bees every five client ticks, prioritizes angry
bees, and retains existing loops unless another bee is meaningfully closer. Bees that
were suppressed can become audible again as the listener moves. Selected loops retain
vanilla position, pitch, and volume behavior. Hurt, sting, death, harvesting, and
pollination sounds are unchanged. Disable adaptation to restore vanilla buzzing.

AI callers share a hive's fire scan within one tick; entry performs a fresh check and
vanilla emergency fire checks remain uncached. Hive and flower path requests restore
the previous pathfinding budget, and idle wandering cannot replace return-home paths.
All caches are transient and require no world migration.

See [performance validation](docs/performance-validation.md) for the automated checks,
client listening checks, and the before/after measurement protocol.

Indoor breeding needs two serialized adult bees with no age cooldown and one
free slot. A successful roll adds one vanilla-aged baby, consumes no honey,
and does not alter either parent.

The effective harvest cost is clamped to honey capacity. Inverted honeycomb
bounds are sorted and reported once at startup. Existing vanilla hives migrate
their 0-5 honey value one-for-one. Lowering honey capacity never deletes stored
honey; new deposits pause until the hive is below the configured limit.

Flower knowledge is deliberately not saved or copied to hive items. A request
keeps the hive scanner active for 1,200 ticks, and later requests extend that
window. Scanning pauses as soon as all active requests are satisfied or a full
generation completes. The bounded cache remains available while the hive stays
loaded, then is rebuilt lazily after unload or restart. This does not require
migration in existing worlds.

Bee size is derived deterministically from each bee's UUID and does not consume
the world's random stream. Existing bees gain stable individual sizes without
save migration or custom scale data in entity and hive NBT. The server applies
the size through Minecraft's synchronized scale attribute, so clients receive
the authoritative model and hitbox scale automatically. Babies retain vanilla's
additional half-size multiplier. Set both appearance values to `1.0` to restore
vanilla size; inverted bounds are sorted and reported once at startup.

Existing world configurations retain their saved settings. To adopt the wider
20-50% range, set `maximum_bee_scale = 0.50` under `[appearance]`
(`appearance.maximum_bee_scale`), keep `minimum_bee_scale = 0.20`, and restart
the world.

## Build and test

```powershell
.\gradlew.bat buildAll
.\gradlew.bat gameTestAll
```

To launch or run GameTests with the optional Jade integration enabled:

```powershell
.\gradlew.bat -PwithJade=true gameTestAll
```

Individual Fabric and Quilt-compatible lanes can be reproduced with the pinned
values from `gradle/targets.json`:

```powershell
.\gradlew.bat :fabricMc1_21_1:build -Pfabric_target=fabricMc1_21_1
.\gradlew.bat :fabricMc1_21_1:build -Pfabric_target=fabricMc1_21_1 -PwithQuilt=true
.\gradlew.bat :fabricMc1_21_1:build -Pfabric_target=fabricMc1_21_1 -PwithJade=true
```

NeoForge artifacts are written beneath `versions/<minecraft>/build/libs/`;
Fabric/Quilt artifacts are beneath `fabric-versions/<minecraft>/build/libs/`:

```text
betterbees-<mod.version>-neoforge-<minecraft.version>.jar
betterbees-<mod.version>-fabric-<minecraft.version>.jar
```

The committed [`gradle/targets.json`](gradle/targets.json) manifest is the
authoritative source for Minecraft, Java, NeoForge, Fabric Loader, Fabric API,
Quilt Loader, mappings, and loader-specific Jade targets. Every artifact is
compiled against its platform floor so newer-only calls fail during development.

### Continuous integration and releases

Pull requests, pushes to `main`, and releases share one validation workflow.
It runs 32 endpoint jobs: minimum and latest dependency stacks for six NeoForge,
six Fabric, and four supported Quilt targets. Each builds, runs the gameplay
GameTests, and starts a client without Jade. Latest-stack jobs also start a
normal dedicated server and client with Jade. Three sequential world-upgrade
jobs protect saved bee, honey, and hive-item data using normal dedicated
servers that save and reopen the same world (GameTest runners can reset worlds).
Missing upgrade fixtures after the first version hop fail instead of being
recreated. Upgrade runs require a fresh directory and refuse to downgrade an
existing world. Normal smoke launches also use isolated CI run directories.

That is 35 runtime validation jobs and 64 ordinary smoke launches, plus one
small tooling/matrix job. The two intermediate NeoForge 1.21.1 checkpoints and
separate overlapping build/Jade jobs are no longer mandatory. Jade remains
optional; the release gate tests its latest version, not every advertised Jade
minimum. On NeoForge Minecraft 1.21.4, Jade 17.3.0 is the minimum supported
version because 17.0.1 fails during client initialization.

Smoke checks require actual Better Bees initialization, Fabric API on
Fabric/Quilt, and completed Jade provider registration when enabled. Asset
preparation runs before the startup deadline and may retry one asset-task
failure; mod failures are never retried. Failed jobs retain startup logs,
crash reports, and available test reports for seven days. Ordinary CI retains
no release jars.

Releases are self-service from **Actions > Release > Run workflow**. Choose
`current`, `patch`, `minor`, `major`, or `custom`; supply `custom_version` only
for a custom strict SemVer such as `1.1.0-beta.1`. GitHub cannot show values
read from the repository before the form is submitted. The first job and the
run summary report the current project version, last published release,
calculated release version, and `v<version>` tag before expensive validation.

The workflow applies the calculated version to the balanced validation matrix,
then verifies all twelve floor-built jars and checksums. Only after
all checks pass does it update `mod_version` (when needed), commit, tag, and
publish the GitHub Release. Prerelease suffixes automatically create GitHub
prereleases. A changed `main`, invalid/backward version, failed test, or jar
verification failure leaves the repository unpublished.

All validated jars are published to one GitHub Release. Modrinth receives a
NeoForge and a Fabric/Quilt entry per target, such as
`1.2.0+neoforge.mc1.21.8` and `1.2.0+fabric.mc1.21.8`. The four 1.21.x Fabric
entries advertise both Fabric and Quilt; 26.x entries advertise Fabric only.
All Fabric entries require Fabric API and use the same platform jar. Jade remains an
optional project dependency with the target-specific compatible range.
Stable Better Bees versions become Modrinth releases; SemVer suffix versions
become betas.

Publication targets the Better Bees Modrinth project `zMjnE1QT`. Before the
first publication, configure this GitHub repository setting under **Settings >
Secrets and variables > Actions**:

- Secret `MODRINTH_TOKEN`: a Modrinth personal access token allowed to upload
  versions to the project.

The `betterbees` Modrinth slug is already owned by an unrelated project, so do
not replace the configured ID with that project's ID. The workflow validates
the target project, Jade compatibility, dependency relationship, filename,
version type, and SHA-512 hash before and after upload. Matching existing
versions are reused safely; conflicting uploads fail instead of being
overwritten.

The twelve target jars, individual checksum files, and combined `SHA256SUMS`
manifest are retained as Actions artifacts for 14 days and attached to the
GitHub release. Safe retries may
replace GitHub assets only when an existing `v<version>` tag still points to
the exact tested commit; the workflow never moves a tag. The old nonrelease tag
`v0.1.0-NEO-1.21.1` is retained but does not participate in version selection.

## Compatibility

Jade is optional and is compiled separately against the compatible Jade line
for each loader and Minecraft target. Install Jade on both the client and server to see exact stored honey
and bee-capacity fractions. A Jade-only client safely retains Jade's normal
scaled `Honey: x/5` display when the server cannot provide authoritative Better
Bees data. Jade is never bundled into or required by the Better Bees jar. Other
inspection overlays, including The One Probe, are not currently integrated.

Better Bees is intentionally incompatible with the `brainierbees` and
`brainier_bees` mod IDs. It is also incompatible with Realistic Bees because
both mods change bee scale and beehive behavior. The individual-size feature
is inspired by Realistic Bees but uses Minecraft's native synchronized scale
attribute and does not copy its implementation. Better Bees may also conflict
with mods that replace Bee AI, change `BeehiveBlockEntity` capacity/storage, or
inject into the same Bee and beehive methods. Mods that replace beehive
harvesting, honey-level handling, or dispenser shearing may also conflict.
Mods that replace flower-search, hive-selection, or hive-travel AI may conflict
with the collective-foraging behavior as well.
Mods that replace bee loop admission or hive entry/exit sound calls may conflict
with the adaptive audio hooks; adaptive buzzing can be disabled in the client config.

## World upgrades

Better Bees keeps the same registry IDs, NBT keys, item component, configuration
keys, and behavior data across targets. Forward upgrades are supported in this
order: `1.21.1 -> 1.21.4 -> 1.21.8 -> 1.21.11 -> 26.1.2 -> 26.2`. Back up the
world before each Minecraft upgrade and let each version save cleanly before
moving to the next. NeoForge and Fabric test the full chain; Quilt tests the
supported 1.21.x chain. Cross-loader transfers are best-effort and are not advertised as
supported; downgrading a world is unsupported.

## License

Copyright (c) 2026 TheCoolestPaul. Better Bees is licensed under
GPL-3.0-only. Brainier Bees-derived work remains credited under its MIT notice.
