# Crimson Covenant

A NeoForge mod about vampires, vampire hunters, and other mystical things.

- Minecraft 26.3, NeoForge 26.3.0.95 (versions live in `gradle.properties`)
- Java 25 (Gradle downloads it automatically if it isn't installed)

## Common commands

| Command | What it does |
| --- | --- |
| `./gradlew runClient` | Launch Minecraft with the mod loaded |
| `./gradlew runServer` | Launch a dedicated server with the mod |
| `./gradlew runData` | Run data generators (output goes to `src/generated/resources`) |
| `./gradlew build` | Build the mod jar into `build/libs/` |

On Windows PowerShell or cmd, use `.\gradlew.bat` instead of `./gradlew`.

## Layout

- `src/main/java/com/zestudio/crimsoncovenant/`
  - `CrimsonCovenant.java`: main mod class
  - `CrimsonCovenantClient.java`: client-only setup (renderers, screens, keybinds)
  - `registry/`: blocks, items and creative tabs
- `src/main/resources/assets/crimsoncovenant/`: textures, models, lang files
- `src/main/templates/META-INF/neoforge.mods.toml`: mod metadata (filled in from `gradle.properties`)

## Opening in an IDE

IntelliJ IDEA: File > Open, then select this folder, and let it import the Gradle project.
Run configurations for the client and server are generated automatically.
