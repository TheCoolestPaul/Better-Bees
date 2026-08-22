# Better Bees

[![CI](https://github.com/TheCoolestPaul/Better-Bees/actions/workflows/ci.yml/badge.svg)](https://github.com/TheCoolestPaul/Better-Bees/actions/workflows/ci.yml)

Better Bees is a NeoForge 1.21.1 mod that gives vanilla bees a Brain-based AI,
raises bee nest and beehive capacity to 20, and lets eligible adult occupants
occasionally produce a stored baby while inside their hive. Hives also store
10 honey by default and support incremental bottle and shears harvesting.

The behavior is a 1.21.1 backport of Brainier Bees `main` at commit
`0ccfabf1752679e01fb3783aa7eb5679e8453a54`. It aims for behavior parity rather
than copying APIs from a newer Minecraft version. See `THIRD_PARTY_NOTICES.md`.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.1 through any stable 21.1.x release
- Java 21
- Install on both clients and the server

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
  such as `Honey: 7/10` and `Bees: 12/20`.

## Server configuration

NeoForge creates `world/serverconfig/betterbees-server.toml` for a world.
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
| `hive.honey_capacity` | 10 | 1-64 |
| `hive.harvest_cost` | 1 | 1-64 |
| `hive.shears_honeycomb_min` | 1 | 1-64 |
| `hive.shears_honeycomb_max` | 3 | 1-64 |
| `hive.indoor_breeding_enabled` | true | boolean |
| `hive.breeding_interval_ticks` | 1200 | 20-72000 |
| `hive.breeding_chance` | 0.05 | 0.0-1.0 |

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

## Build and test

```powershell
.\gradlew.bat build
.\gradlew.bat runGameTestServer
```

To launch or run GameTests with the optional Jade integration enabled:

```powershell
.\gradlew.bat -PwithJade=true runGameTestServer
```

The built mod is written to `build/libs/betterbees-<version>.jar`, using the
`mod_version` value in `gradle.properties`.

Better Bees ships one jar for Minecraft 1.21.1. Releases are compiled against
NeoForge 21.1.1 so newer-only API usage cannot enter the artifact accidentally.
The automated compatibility matrix tests 21.1.1, 21.1.50, 21.1.213, and
21.1.248. Later 21.1.x releases are allowed by the metadata and are added to the
tested list as they are validated; other Minecraft versions require separate
artifacts.

### Continuous integration and releases

Pull requests and pushes to `main` build the mod and run all GameTests against
the four explicitly tested NeoForge versions. Endpoint jobs also exercise Jade
15.1.6 and 15.10.6. Ordinary CI retains no jars.

Releases are self-service from **Actions > Release > Run workflow**. Choose
`current`, `patch`, `minor`, `major`, or `custom`; supply `custom_version` only
for a custom strict SemVer such as `1.1.0-beta.1`. GitHub cannot show values
read from the repository before the form is submitted. The first job and the
run summary report the current project version, last published release,
calculated release version, and `v<version>` tag before expensive validation.

The workflow applies the calculated version to the full four-version GameTest
matrix, smoke-launches dedicated servers and headless clients at both supported
endpoints, then verifies a fresh NeoForge 21.1.1 build and checksum. Only after
all checks pass does it update `mod_version` (when needed), commit, tag, and
publish the GitHub Release. Prerelease suffixes automatically create GitHub
prereleases. A changed `main`, invalid/backward version, failed test, or jar
verification failure leaves the repository unpublished.

The validated `betterbees-<version>.jar` and checksum are retained as Actions
artifacts for 14 days and attached to the release. Safe retries may replace
assets only when an existing `v<version>` tag still points to the exact tested
commit; the workflow never moves a tag. The old nonrelease tag
`v0.1.0-NEO-1.21.1` is retained but does not participate in version selection.

## Compatibility

Jade is optional and supported from version 15.1.6 through all compatible 15.x
releases. Install Jade on both the client and server to see exact stored honey
and bee-capacity fractions. A Jade-only client safely retains Jade's normal
scaled `Honey: x/5` display when the server cannot provide authoritative Better
Bees data. Jade is never bundled into or required by the Better Bees jar. Other
inspection overlays, including The One Probe, are not currently integrated.

Better Bees is intentionally incompatible with the `brainierbees` and
`brainier_bees` mod IDs. It may also conflict with mods that replace Bee AI,
change `BeehiveBlockEntity` capacity/storage, or inject into the same Bee and
beehive methods. Mods that replace beehive harvesting, honey-level handling,
or dispenser shearing may also conflict.
Mods that replace flower-search, hive-selection, or hive-travel AI may conflict
with the collective-foraging behavior as well.

## License

Copyright (c) 2026 TheCoolestPaul. Better Bees is licensed under
GPL-3.0-only. Brainier Bees-derived work remains credited under its MIT notice.
