# DyRTP

Fast random teleportation with safe locations, protection hooks, economy support, and EN/TR localization.

DyRTP is a lightweight random teleport plugin for survival, SMP, and economy servers. It focuses on fast location searching, safe teleport targets, clean command-based usage, and broad compatibility with land-protection plugins.

## Why DyRTP?

**Fast by design.** DyRTP checks multiple candidate locations at once, uses async chunk loading on supported servers, and instantly checks already-loaded chunks.

**Safe teleportation.** Players are not sent into oceans, lava, caves, underground mines, leaves, unsafe blocks, or protected areas.

**DyClaim support.** DyRTP integrates with DyClaim and avoids claimed chunks automatically.

**Automatic language.** DyRTP detects each player's Minecraft language and shows commands and messages in their language. English and Turkish are included out of the box.

**Optional integrations.** Hooks are detected automatically. DyRTP works without Vault or protection plugins, but uses them when present.

## Features

### Random Teleportation

- Random teleport with `/dyrtp`
- Configurable minimum and maximum radius
- Per-world RTP settings
- World border support
- Optional world redirects
- Surface-only safe teleportation
- Async chunk loading on Paper/Purpur when available
- Instant checks for already-loaded chunks

### Safety

- Avoids water, lava, fire, powder snow, leaves, bedrock, and unsafe blocks
- Avoids ocean biomes by default
- Prevents cave and underground teleports in normal worlds
- Requires enough headroom before teleporting
- Avoids protected claims, towns, regions, plots, and islands
- Configurable safety rules

### Economy

- Optional Vault economy support
- Configurable RTP price
- Clickable confirmation before paid teleports
- Economy can be enabled or disabled anytime
- Works without Vault; economy features stay disabled automatically

### Cooldown And Delay

- Configurable cooldown timer
- Configurable teleport delay
- Optional movement and damage cancellation during delay
- Bypass permissions for admins
- First join, death RTP, and forced RTP can ignore cooldowns

### Player Experience

- Clean chat messages
- Optional titles
- Optional action bar messages
- Optional blindness effect during teleport delay
- Configurable sounds
- Fully editable messages
- English and Turkish language files

### Automatic RTP

- First join RTP support
- Death RTP support
- Death RTP can be skipped when the player has a bed or respawn anchor
- Automatic RTP systems are disabled by default

## Commands

| Command | Description |
|---------|-------------|
| `/dyrtp` | Randomly teleport yourself |
| `/dyrtp help` | Show the help menu |
| `/dyrtp language <auto/en/tr>` | Change your language |
| `/onayla` / `/confirm` | Confirm a paid teleport |
| `/reddet` / `/cancel` | Cancel a paid teleport |
| `/forcertp <player>` | Force RTP a player |

Admin commands are available under `/dyrtp admin` and `/dyrtp yonetim`.

## Admin Commands

| Command | Description |
|---------|-------------|
| `/dyrtp admin economy <on/off>` | Enable or disable economy |
| `/dyrtp admin price <amount>` | Set RTP price |
| `/dyrtp admin cooldown <seconds/off>` | Set cooldown |
| `/dyrtp admin delay <seconds/off>` | Set teleport delay |
| `/dyrtp admin radius min <blocks>` | Set minimum radius |
| `/dyrtp admin radius max <blocks>` | Set maximum radius |
| `/dyrtp admin language <auto/en/tr>` | Set server language mode |
| `/dyrtp admin reload` | Reload the plugin |

Turkish admin commands are also supported in-game.

## Permissions

| Permission | Default | Description |
|------------|---------|-------------|
| `dyrtp.use` | Everyone | Allows using RTP |
| `dyrtp.admin` | OP | Allows admin commands |
| `dyrtp.bypass.cooldown` | OP | Bypass RTP cooldown |
| `dyrtp.bypass.warmup` | OP | Bypass teleport delay |
| `dyrtp.bypass.economy` | OP | Bypass RTP cost |

## Compatibility

| | |
|---|---|
| Server | Paper, Purpur, Spigot, Bukkit 1.20.x - 26.1.x |
| Java | Java 17+ |

| Plugin | Integration |
|--------|-------------|
| DyClaim | Prevents teleporting into claimed chunks |
| Vault | Economy support |
| WorldGuard | Prevents teleporting into regions |
| GriefPrevention | Prevents teleporting into claims |
| Towny | Prevents teleporting into towns |
| Lands | Prevents teleporting into Lands areas |
| Residence | Prevents teleporting into residences |
| GriefDefender | Prevents teleporting into claims |
| RedProtect | Prevents teleporting into regions |
| Kingdoms | Prevents teleporting into protected land |
| PlotSquared | Prevents teleporting into plots |
| Floodgate | Better support for Bedrock player names |

All integrations are optional and detected automatically.

## Setup

1. Download the latest DyRTP jar.
2. Drop it into your server's `plugins/` folder.
3. Start the server.
4. Edit `plugins/DyRTP/config.yml`.
5. Run `/dyrtp admin reload`.

## Building

DyRTP uses Gradle.

```bash
gradle build
```

The compiled jar will be created in `build/libs/`.

## License

DyRTP is licensed under the GNU Lesser General Public License v3.0 only (`LGPL-3.0-only`). See [LICENSE](LICENSE).

## Open Source

DyRTP is open source. Contributions, issues, and feature requests are welcome.
