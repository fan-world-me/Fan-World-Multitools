[![Telegram](https://img.shields.io/badge/Telegram-@fan__world__me-2CA5E0?style=flat-square&logo=telegram)](https://t.me/fan_world_me)   [![Discord](https://img.shields.io/badge/Discord-fan__world__me-5865F2?style=flat-square&logo=discord)](https://discord.com/users/1040301536036786248)   [![GitHub](https://img.shields.io/badge/GitHub-fan--world--me-181717?style=flat-square&logo=github)](https://github.com/fan-world-me)   [![Portfolio](https://img.shields.io/badge/Portfolio-fan--world--me.github.io-00e5ff?style=flat-square&logo=githubpages&logoColor=white)](https://fan-world-me.github.io/)

```
███╗   ███╗██╗   ██╗██╗  ████████╗██╗████████╗ ██████╗  ██████╗ ██╗     ███████╗
████╗ ████║██║   ██║██║  ╚══██╔══╝██║╚══██╔══╝██╔═══██╗██╔═══██╗██║     ██╔════╝
██╔████╔██║██║   ██║██║     ██║   ██║   ██║   ██║   ██║██║   ██║██║     ███████╗
██║╚██╔╝██║██║   ██║██║     ██║   ██║   ██║   ██║   ██║██║   ██║██║     ╚════██║
██║ ╚═╝ ██║╚██████╔╝███████╗██║   ██║   ██║   ╚██████╔╝╚██████╔╝███████╗███████║
╚═╝     ╚═╝ ╚═════╝ ╚══════╝╚═╝   ╚═╝   ╚═╝    ╚═════╝  ╚═════╝ ╚══════╝╚══════╝
  Fan-World Multitools v7.0.0 — Fabric client mod for Minecraft 1.21.11
```

Client-side Fabric mod for Minecraft `1.21.11` — hotbar automation, elytra movement helpers, mace PvP combos, and utility keybinds in one package.

> **Latest release:** [v7.0.0](https://github.com/fan-world-me/multitools/releases/tag/7.0.0)

### 💭 More about this mod

- ⚔️ **SpearDash** — swap to a Lunge spear, attack, restore previous slot instantly
- 🎯 **PearlCatch** — ender pearl follow-up with Wind Charge assist and auto pitch correction
- 🪂 **DiveCombo** — full elytra-mace combo loop via state machine (launch → boost → dive → strike → re-approach)
- 🗡️ **SmartAttack** — falling attack assist with player-first target priority and auto elytra removal
- 🌊 **WaterUtility** — smart bucket: extinguish fire, break fall, place lava under enemies
- 🕸️ **WebTrap** — cobweb behind target + sword follow-up in melee range
- 🎆 **FireworkBoost** — fire fireworks from offhand, hotbar, or inventory swap
- 🪶 **AutoElytraSwap** — auto equip elytra / restore chestplate; threat detection swaps armor vs crystals, TNT, and mace divers
- 💀 **AutoTotem** — keeps Totem of Undying in offhand automatically
- 🤖 **Auto Mace Swap** (passive) — intercepts every attack, swaps to mace; axe+mace combo to break shields
- ⚙️ **Mod Menu config** — in-game GUI for all toggles and keybinds via Mod Menu integration

---

### 🗂️ Keybinds

| Key | Action |
|---|---|
| `C` | **SpearDash** — swap to Lunge spear, hit, restore slot |
| `V` | **PearlCatch** — pearl + Wind Charge follow-up with auto pitch assist |
| `Mouse 4` | **DiveCombo** — start or cancel elytra-mace combo loop |
| `Mouse 5` | **SmartAttack** — falling attack assist with auto elytra removal |
| `X` | **WaterUtility** — extinguish / break fall / place lava under target |
| `Z` | **WebTrap** — cobweb behind target + sword strike in range |
| `Left Shift` | **FireworkBoost** — fire firework while gliding (offhand / hotbar / inventory) |
| `` ` `` | **Toggle AutoElytraSwap** — enable / disable auto elytra equip |
| `R` | **AutoTotem** — toggle auto-keep totem in offhand |
| `L` | `/ck load main` — load main loadout |
| `K` | `/ck load crystall` — load crystal PvP loadout |
| `←` | `/ck load ender-chest` — load ender chest loadout |
| `F6` | `/givef railgun straight 1` — railgun (requires multitools-help on server) |
| `F7` | `/givef nuke 1 11` — nuke shot (requires multitools-help on server) |
| `F8` | `/givef stab 1` — stab shot (requires multitools-help on server) |
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
│   ├── DiveComboState.java           — elytra-mace combo state machine (9 phases)
│   ├── MaceSwapState.java            — passive mace/axe swap + shield-break combo logic
│   ├── MultitoolsSettings.java       — persistent settings (load/save to disk)
│   ├── MultitoolsSettingsScreen.java — in-game config GUI (Mod Menu integration)
│   ├── MultitoolsModMenu.java        — Mod Menu entry point
│   └── mixin/                        — Mixin patches (client-side only)
├── multitools-help/                  — optional server-side companion mod
│   └── src/main/java/fan_world_me/multitoolshelp/
│       ├── MultitoolsHelp.java       — server mod initializer
│       ├── GivefCommand.java         — /givef command (nuke / stab / railgun items)
│       ├── NukeLogic.java            — TNT ring spawner
│       ├── StabLogic.java            — instant TNT column detonator
│       ├── RailgunLogic.java         — high-speed arrow volley
│       └── StrikeScheduler.java     — batched tick scheduler (no lag spikes)
├── src/main/resources/
│   ├── fabric.mod.json               — mod metadata
│   ├── multitools.mixins.json        — server mixin config
│   ├── multitools.client.mixins.json — client mixin config
│   └── assets/multitools/lang/       — i18n strings
├── build.gradle                      — Gradle build script (Fabric Loom)
├── gradle.properties                 — versions: mod 7.0.0, MC 1.21.11, Java 21
└── gradlew.bat                       — Gradle wrapper
```

---

### 🚀 Build

```powershell
.\gradlew.bat build
```

Output jar: `build/libs/multitools-7.0.0.jar`

Run the client for development:

```powershell
.\gradlew.bat runClient
```

---

### 📦 Install

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) `0.18.2+` for Minecraft `1.21.11`
2. Install [Fabric API](https://modrinth.com/mod/fabric-api)
3. Optionally install [Mod Menu](https://modrinth.com/mod/modmenu) for the in-game config screen
4. Drop `multitools-7.0.0.jar` into your `.minecraft/mods/` folder
5. *(Optional)* Drop `multitools-help-1.0.0.jar` into your **server's** `mods/` folder to enable `/givef` commands

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

[![Telegram](https://img.shields.io/badge/Telegram-@fan__world__me-2CA5E0?style=flat-square&logo=telegram)](https://t.me/fan_world_me)   [![Discord](https://img.shields.io/badge/Discord-fan__world__me-5865F2?style=flat-square&logo=discord)](https://discord.com/users/1040301536036786248)   [![Portfolio](https://img.shields.io/badge/Portfolio-fan--world--me.github.io-00e5ff?style=flat-square&logo=githubpages&logoColor=white)](https://fan-world-me.github.io/)
