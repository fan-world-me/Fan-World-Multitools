# Fan World Me Multitools

Client-side Fabric mod for Minecraft `1.21.11` focused on hotbar automation, elytra movement, and mace PvP helpers.

## Keybinds

- `C` - `Spear Dash`: swap to a Lunge spear, hit, then restore the previous slot.
- `V` - `Pearl Catch`: original pearl follow-up flow from the base project.
- `Mouse 4` - `Dive Combo`: start or cancel the elytra-mace combo loop.
- `Mouse 5` - `Smart Attack`: falling attack assist with player-first target priority.
- `Left Shift` - `Firework Boost`
- `` ` `` - toggle auto elytra swap
- `R` - `Auto Totem`
- `L` - `/ck load main`
- `F6` - drop/move held item if needed, then send `/givef railgun straight 1`
- `F7` - drop/move held item if needed, then send `/givef nuke 1 11`
- `F8` - drop held item if needed, then send `/givef stab 1`
- `F9` - throw self-buff splash potions downward
- `F10` - `/effect give @p minecraft:saturation infinite 255 false`
- `F12` - `/kill @e[type=!minecraft:player]`

## Features

- Package and mod id renamed to `fan_world_me.multitools`
- Old Wind Charge Jump and unfinished bounce logic removed
- Dive Combo state machine for elytra-mace automation
- Hidden mace / axe -> mace follow-up swap logic
- Auto elytra equip and chestplate restore helpers
- Firework use from offhand, hotbar, or inventory swap
- Auto Totem offhand swap
- Mod Menu config screen for the main toggles

## Requirements

- Java `21`
- Fabric Loader `0.18.2+`
- Fabric API
- Minecraft `1.21.11`

## Build

```powershell
.\gradlew.bat build
```

Output jar:

- `build/libs/multitools-5.0.2.jar`

## GitHub

For GitHub releases, attach the built jar from `build/libs/` and keep the version in `build.gradle` and this README aligned.

If you want signed release tags, create them with GPG before pushing:

```powershell
git tag -s v5.0.2 -m "multitools 5.0.2"
git push origin v5.0.2
```

If you publish release checksums, sign or verify those alongside the jar so users can confirm the artifact they download.

## Development

Run the client:

```powershell
.\gradlew.bat runClient
```

Main sources:

- `src/main/java/fan_world_me/multitools/`
- `src/main/resources/fabric.mod.json`
- `src/main/resources/assets/multitools/lang/en_us.json`

## License

GPL-3.0-only. See `LICENSE`.

