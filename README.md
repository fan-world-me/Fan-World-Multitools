[![Telegram](https://img.shields.io/badge/Telegram-@fan__world__me-2CA5E0?style=flat-square&logo=telegram)](https://t.me/fan_world_me)   [![Discord](https://img.shields.io/badge/Discord-fan__world__me-5865F2?style=flat-square&logo=discord)](https://discord.com/users/fan_world_me)   [![GitHub](https://img.shields.io/badge/GitHub-fan--world--me-181717?style=flat-square&logo=github)](https://github.com/fan-world-me)   [![Portfolio](https://img.shields.io/badge/Portfolio-fan--world--me.github.io-00e5ff?style=flat-square&logo=githubpages&logoColor=white)](https://fan-world-me.github.io/)

```
███╗   ███╗██╗   ██╗██╗  ████████╗██╗████████╗ ██████╗  ██████╗ ██╗     ███████╗
████╗ ████║██║   ██║██║  ╚══██╔══╝██║╚══██╔══╝██╔═══██╗██╔═══██╗██║     ██╔════╝
██╔████╔██║██║   ██║██║     ██║   ██║   ██║   ██║   ██║██║   ██║██║     ███████╗
██║╚██╔╝██║██║   ██║██║     ██║   ██║   ██║   ██║   ██║██║   ██║██║     ╚════██║
██║ ╚═╝ ██║╚██████╔╝███████╗██║   ██║   ██║   ╚██████╔╝╚██████╔╝███████╗███████║
╚═╝     ╚═╝ ╚═════╝ ╚══════╝╚═╝   ╚═╝   ╚═╝    ╚═════╝  ╚═════╝ ╚══════╝╚══════╝
  Fan-World Multitools v6.0.0 — Fabric client mod for Minecraft 1.21.11
```

Client-side Fabric mod for Minecraft `1.21.11` — hotbar automation, elytra movement helpers, mace PvP combos, and utility keybinds in one package.

### 💭 More about this mod

- ⚔️ **SpearDash** — swap to a Lunge spear, attack, restore previous slot instantly
- 🎯 **PearlCatch** — ender pearl follow-up flow with timing precision
- 🪂 **DiveCombo** — full elytra-mace combo loop via state machine (start / cancel)
- 🗡️ **SmartAttack** — falling attack assist with player-first target priority and threat detection
- 🌊 **WaterUtility** — smart bucket placement for fluid extinguish or lava control
- 🕸️ **WebTrap** — cobweb placement utility
- 🎆 **FireworkBoost** — fire fireworks from offhand, hotbar, or inventory swap
- 🪶 **AutoElytraSwap** — auto equip elytra / restore chestplate on landing
- 💀 **AutoTotem** — keeps Totem of Undying in offhand automatically
- ⚙️ **Mod Menu config** — in-game GUI for all toggles via Mod Menu integration

---

### 🗂️ Keybinds

| Key | Action |
|---|---|
| `C` | **SpearDash** — swap to Lunge spear, hit, restore slot |
| `V` | **PearlCatch** — pearl follow-up flow |
| `Mouse 4` | **DiveCombo** — start or cancel elytra-mace combo loop |
| `Mouse 5` | **SmartAttack** — falling attack assist |
| `X` | **WaterUtility** — smart bucket placement |
| `Z` | **WebTrap** — cobweb placement |
| `Left Shift` | **FireworkBoost** — fire firework (offhand / hotbar / inventory) |
| `` ` `` | **Toggle AutoElytraSwap** — enable / disable auto elytra equip |
| `R` | **AutoTotem** — keep totem in offhand |
| `L` | `/ck load main` — load main loadout command |
| `F6` | Drop/move item if needed → `/givef railgun straight 1` |
| `F7` | Drop/move item if needed → `/givef nuke 1 11` |
| `F8` | Drop item if needed → `/givef stab 1` |
| `F9` | Throw self-buff splash potions downward |
| `F10` | `/effect give @p minecraft:saturation infinite 255 false` |
| `F12` | `/kill @e[type=!minecraft:player]` |

---

### 🧰 Tech Stack

**Core**
![Java](https://img.shields.io/badge/Java-21-ED8B00?style=flat-square&logo=openjdk&logoColor=white)
![Fabric](https://img.shields.io/badge/Fabric_Loader-0.18.2-DBB981?style=flat-square)
![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-62B47A?style=flat-square&logo=minecraft&logoColor=white)

**APIs & Libraries**
![Fabric API](https://img.shields.io/badge/Fabric_API-0.141.3-DBB981?style=flat-square)
![Mod Menu](https://img.shields.io/badge/Mod_Menu-17.0.0-00b4d8?style=flat-square)
![Mixins](https://img.shields.io/badge/Mixins-SpongePowered-7B4397?style=flat-square)

---

### 🏗️ Project Structure

```
spearswap/
├── src/main/java/fan_world_me/multitools/
│   ├── MultitoolsClient.java         — main client mod initializer, all keybind logic
│   ├── ModKeyBindings.java           — keybind registration (GLFW keys + mouse buttons)
│   ├── DiveComboState.java           — elytra-mace combo state machine
│   ├── MaceSwapState.java            — hidden mace/axe → mace follow-up swap logic
│   ├── MultitoolsSettings.java       — persistent settings (load/save to disk)
│   ├── MultitoolsSettingsScreen.java — in-game config GUI (Mod Menu integration)
│   ├── MultitoolsModMenu.java        — Mod Menu entry point
│   └── mixin/                        — Mixin patches (client-side only)
├── src/main/resources/
│   ├── fabric.mod.json               — mod metadata
│   ├── multitools.mixins.json        — server mixin config
│   ├── multitools.client.mixins.json — client mixin config
│   └── assets/multitools/lang/       — i18n strings
├── build.gradle                      — Gradle build script (Fabric Loom)
├── gradle.properties                 — versions: mod 6.0.0, MC 1.21.11, Java 21
└── gradlew.bat                       — Gradle wrapper
```

---

### 🚀 Build

```powershell
.\gradlew.bat build
```

Output jar: `build/libs/multitools-6.0.0.jar`

Run the client for development:

```powershell
.\gradlew.bat runClient
```

---

### 📦 Install

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) `0.18.2+` for Minecraft `1.21.11`
2. Install [Fabric API](https://modrinth.com/mod/fabric-api)
3. Optionally install [Mod Menu](https://modrinth.com/mod/modmenu) for the in-game config screen
4. Drop `multitools-6.0.0.jar` into your `.minecraft/mods/` folder

---

### ⚙️ How it works

```
[Tick event every client frame]
        │
        ├──► SpearDash (C) ──────► swap hotbar slot → swing → restore
        ├──► DiveCombo (M4) ─────► DiveComboState: ARM → GLIDE → DIVE → STRIKE → DONE
        ├──► SmartAttack (M5) ───► scan nearby entities → score by health/type/distance
        ├──► AutoElytraSwap (`) ─► detect fall → equip elytra; detect ground → restore chestplate
        └──► AutoTotem (R) ──────► check offhand each tick → swap totem if missing
```

---

### 📄 License

[GPL-3.0-only](LICENSE) © [fan-world-me](https://github.com/fan-world-me)

---

Made with 🩵 in Ukraine 🇺🇦

[![Telegram](https://img.shields.io/badge/Telegram-@fan__world__me-2CA5E0?style=flat-square&logo=telegram)](https://t.me/fan_world_me)   [![Discord](https://img.shields.io/badge/Discord-fan__world__me-5865F2?style=flat-square&logo=discord)](https://discord.com/users/fan_world_me)   [![Portfolio](https://img.shields.io/badge/Portfolio-fan--world--me.github.io-00e5ff?style=flat-square&logo=githubpages&logoColor=white)](https://fan-world-me.github.io/)
