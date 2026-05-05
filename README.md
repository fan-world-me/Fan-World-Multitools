# Fan World Multitools

Client-side Fabric mod for Minecraft 1.21.11 with fast combat and movement utility keybinds.

## Features

- Spear Dash: swaps to a Lunge-enchanted spear, attacks, then returns to the previous hotbar slot.
- Pearl Catch: throws an ender pearl and follows up with a wind charge timing helper.
- Smart Attack: attacks the best nearby living target while falling.
- Wind Charge Jump: swaps to a wind charge, uses it with a suitable pitch, then restores the previous slot.
- Firework Boost: uses fireworks while gliding, including from hotbar or inventory.
- Elytra Swap: double-jump style elytra equip, landing chestplate restore, and toggle key.
- Auto Totem: moves a totem from inventory to the offhand.
- Load Main Kit: sends `/ck load main`.
- Mace combat helper: handles mace, axe, and sword follow-up swaps around shield interactions.

## Requirements

- Java 21
- Fabric Loader 0.18.2 or newer
- Fabric API
- Minecraft 1.21.11

## Build

Windows:

```powershell
.\gradlew.bat build
```

Linux/macOS:

```bash
./gradlew build
```

The built mod jar will be in `build/libs/`.

## Development

Run a client from the Gradle project:

```powershell
.\gradlew.bat runClient
```

Useful files:

- `src/main/java/fan_world/spearcore/` - Java sources.
- `src/main/resources/fabric.mod.json` - Fabric mod metadata.
- `src/main/resources/assets/multitools/lang/en_us.json` - keybind translations.

## License

This project is licensed under the GNU General Public License v3.0 only. See `LICENSE`.
