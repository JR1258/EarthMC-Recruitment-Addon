# EarthMC-Recruitment-Addon — Minecraft 26.2

Client-side EarthMC recruiting assistant: it watches the server player list, and when a
genuinely new account joins it posts a clickable chat line that copies your recruit
message. It also detects Towny "joined the town" messages and offers follow-up copy
buttons, plus an optional recurring reminder to post a public recruitment ad.

> **This branch targets Minecraft 26.2.** One branch per game version:
> [`main`](../../tree/main) (1.21.11) · [`26.1.x`](../../tree/26.1.x) · `26.2` (here)

| | |
| --- | --- |
| Minecraft | 26.2 |
| Java | 25 |
| Fabric Loom | 1.17-SNAPSHOT |
| Gradle | 9.5.1 |
| Fabric Loader | 0.19.3 |
| Fabric API | 0.158.0+26.2 |
| Mod Menu | 20.0.1 |

## Building and testing

```bash
./gradlew build
```

`build` is finalized by `deployToMods`, which drops the jar straight into your Modrinth
launcher profile and clears out any older build of this mod first. The target profile is
set by `modrinth_profile` in `gradle.properties` (default `26.2`), resolving to:

```
~/Library/Application Support/ModrinthApp/profiles/26.2/mods
```

The profile folder name is a separate property because it does not always match the game
version — the launcher suffixes duplicated profiles, so yours may be named something like
`26.1.2 (1)`. Set `modrinth_profile` to the folder name exactly as it appears in the
launcher.

## What changed from 26.1.x

26.2's breaking changes are concentrated in the rendering backend (the new Vulkan/OpenGL
split), which this mod does not touch — so the port is a dependency bump plus one
deliberate addition:

- `/recruit` is now registered with `.requires(FabricClientCommandSource::attended)`, so
  it runs only when you type it, and not when a server hands the client a text component
  that clicks through to it. The command writes the config file, so it is worth gating.
  `attended()` does not exist in 26.1's Fabric API, which is why 26.1.x has no equivalent.
- Loom 1.17 and Gradle 9.5.1, per Fabric's 26.2 recommendation.
- The Modrinth deploy profile is configurable rather than hard-coded.

The mixin targets are unchanged: 26.2 only *adds* members to `ClientPacketListener`, and
none of the player-list or chat packet classes moved.

## Configuration

Settings live in `config/recruitmentaddon.json` and can be edited in-game through Mod
Menu or the `/recruit` command (`/recruit status`, `on`/`off`, `window`, `cooldown`,
`message`, `ad`, `followup`, `exclude`, …).
