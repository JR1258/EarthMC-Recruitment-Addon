# Project notes for Claude

## Commit identity

Commit and push as the repository owner's GitHub account, not as Claude:

```bash
git config user.name  "JR1258"
git config user.email "113603723+JR1258@users.noreply.github.com"
```

Set this at the start of any session that will commit — a fresh container defaults to
`Claude <noreply@anthropic.com>`, which shows up as a separate contributor on GitHub.
Keep the `Co-Authored-By: Claude ...` trailer in commit messages; it records how the
commit was made without changing who it is attributed to.

## Branch layout

One branch per Minecraft version, project at the repository root — no per-version
subfolders:

| Branch | Minecraft |
| --- | --- |
| `main` | 1.21.11 |
| `26.1.x` | 26.1 – 26.1.2 |
| `26.2` | 26.2 |

Port a new game version by branching from the closest existing one and keeping the diff
minimal, rather than restructuring the tree.

## Building

`./gradlew build` is finalized by `deployToMods`, which copies the jar into a local
Modrinth launcher profile. The target folder comes from `modrinth_profile` in
`gradle.properties`; it is a separate property because the launcher suffixes duplicated
profiles, so the folder is not always named after the game version (e.g. `26.1.2 (1)`).
