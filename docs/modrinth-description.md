# DyRTP — Random Teleportation Made Simple

**Find new places. Choose your worlds. Keep exploring.**

DyRTP is a command-based random teleport plugin for survival, SMP and economy servers. Help players find new places, choose where they can travel and configure each world without a GUI.

DyRTP combines configurable world profiles, destination safety checks, optional protection hooks and economy support with a familiar command-based workflow. **Version 1.0.1** fixes destination retention during warmup, keeps RTP free without an economy provider and adds shorter player/admin commands while preserving existing settings, aliases and player preferences.

---

## New in 1.0.1

**Keep the destination ready.** A bounded plugin chunk ticket retains the selected destination throughout warmup. Tickets are shared safely between requests and released after completion, cancellation, reload or shutdown; terrain and protection are still checked before teleport.

**Stay free without a provider.** Missing Vault or a registered economy provider keeps RTP free, including for ordinary players with older paid settings. Positive prices and enabling payments are rejected until a provider is detected. Provider transaction errors still stop paid requests safely.

**Use shorter commands.** `/dyr` is the player shortcut for `/dyrtp`; `/dyra` opens the existing admin branch with the same permissions and TAB suggestions. Turkish help/TAB use `oto`, with `Oto` as the language label; `otomatik` remains accepted. Help no longer includes a row describing the help command itself.

## Choose your server build

| Server | JAR |
| --- | --- |
| Bukkit, Spigot, Paper, Purpur | `DyRTP-1.0.1.jar` |

Install **one** JAR per server. Stop the server and back up the complete DyRTP folder before replacing an older JAR. Existing world profiles, player preferences and custom translations are retained.

Java 17 bytecode; use the runtime required by your server. Minecraft 1.20.x–26.x is the release listing; the fresh 1.0.1 live checks used Paper 26.1.2 / Java 25. Optional integrations must support your selected server too. Historical 1.0.0 downloads retain their original filenames.

[Downloads](https://github.com/DyPlugin/DyRTP/releases/tag/1.0.1) · [Compatibility notes](https://github.com/DyPlugin/DyRTP/blob/main/docs/verification.md)

## Why DyRTP?

**Your worlds, your rules.** Choose a radius, center, shape and allowed biomes for each world. Add a warmup, cooldown or world redirect to fit your server.

**Safety you can configure.** Ground, liquids, height and headroom checks help reject unsafe destinations. Optional protection hooks keep players out of protected areas.

**Automatic language.** English and Turkish messages, help and TAB follow the effective language. Players can save a preference, and administrators can choose one language for everyone.

**Works with your server.** DyClaim and other supported protection adapters are optional. Add Vault and an economy provider for paid RTP, or use core teleportation without them.

---

## A measured look at teleport time

![DyRTP and BetterRTP median command-to-teleport time across five scenarios](https://raw.githubusercontent.com/DyPlugin/DyRTP/1.0.1/docs/images/DyRTP-vs-BetterRTP-en.png)

On Paper 26.1.2 with one non-OP client, DyRTP had the lower observed median in all five matched-setting samples. Each plugin completed 70 requests. These are command-to-teleport durations, not CPU/RAM measurements, and do not establish a universal speed or safety advantage. Protection and economy were disabled for the timing comparison.

[Method, results and limitations](https://github.com/DyPlugin/DyRTP/blob/1.0.1/docs/betterrtp-comparison.md)

## Random teleportation, your way

Use `/dyrtp` to search for a destination. Choose square or circular profiles, minimum/maximum radius, center, height limits, world-border behavior, biomes and world redirects. Normal worlds and the End use surface checks; the Nether uses a vertical ground/headroom scan. End void, configured hazardous blocks and obstructed destinations are rejected.

Configure warmup and movement/damage cancellation, cooldown, titles, action bars, sounds and optional blindness. Cooldown begins after successful teleport. Existing blindness is preserved; cancelled or stale operations cannot later teleport, charge or start cooldown.

First-join and death RTP are disabled by default. Death RTP can respect a bed or respawn anchor. Administrators can force RTP for an exact online player, including a name such as `.Player_Name`.

## Optional protection and economy

DyClaim is optional. With its hook enabled, claimed chunks are excluded even when the player owns or is trusted in that claim. Claims created after the search are checked again before teleport. An unavailable installed hook or failed DyClaim query stops the operation safely. Core RTP works without DyClaim.

Existing optional adapters include WorldGuard, GriefPrevention, Towny, Lands, Residence, GriefDefender and other configured protection plugins. Their presence in configuration does not establish compatibility with every provider version; validate your selected integrations.

Paid RTP requires Vault and a registered economy provider. Without either, RTP remains free; positive prices and enabling payments are rejected until a provider is detected. Confirmation is bound to the player, quoted price and operation, expires and is single-use. Changed price/world settings require a new request. Use `/dyrtp confirm` and `/dyrtp cancel`; clickable buttons target DyRTP's own namespace so another plugin's root confirmation cannot approve a DyRTP operation.

The debit occurs immediately before teleport. The existing charge/refund settings are retained. Failed or uncertain provider transactions are not blindly retried; reconciliation details are logged. Provider query/transaction errors still stop paid requests safely.

## English and Turkish, automatically

With `language.default: auto`, a saved preference takes priority over client language when enabled; Turkish clients receive Turkish and other locales fall back to English. `/dyrtp language auto|en|tr` changes the personal preference. `/dyrtp admin language en|tr` forces one language for everyone; `auto` restores automatic selection.

Help and TAB show the effective language and permitted commands. Both languages and existing ASCII aliases remain accepted when typed. Player names, world names and data arguments are not translated.

## Commands

| Command | Purpose |
| --- | --- |
| `/dyrtp` or `/dyr` | Search for a random destination |
| `/dyrtp help` | Show permitted commands |
| `/dyrtp language <auto/en/tr>` | Choose a personal language |
| `/dyrtp confirm` | Confirm the current paid RTP request |
| `/dyrtp cancel` | Reject a pending confirmation |
| `/dyrtp admin help` or `/dyra` | Show administration commands |
| `/dyrtp admin force <player>` | Force RTP for an exact online player |
| `/dyrtp admin economy <on/off>` | Enable or disable paid RTP |
| `/dyrtp admin price <amount>` | Set a finite, non-negative price |
| `/dyrtp admin cooldown <seconds/off>` | Configure reuse cooldown |
| `/dyrtp admin delay <seconds/off>` | Configure teleport warmup |
| `/dyrtp admin radius min <blocks>` | Configure minimum radius |
| `/dyrtp admin radius max <blocks>` | Configure maximum radius |
| `/dyrtp admin language <auto/en/tr>` | Configure global language |
| `/dyrtp admin reload` | Validate and reload settings/messages |

`/dyr`, `/rtp`, `/randomtp`, `/wild`, `/wildtp`, `/rastgeletp`, `/rastgeleteleport`, `/forcertp` and `/rtpforce` remain accepted. The old `yönetim`/`yonetim` branches and root confirmation aliases remain available, but another plugin can own an unqualified `/confirm` or `/cancel`. Prefer the DyRTP subcommands. Console senders can reload, manage global settings and force RTP.

## Permissions

| Permission | Bukkit default | Purpose |
| --- | --- | --- |
| `dyrtp.use` | Everyone | Self-RTP and confirmation |
| `dyrtp.admin` | OP | Administration and update notices |
| `dyrtp.bypass.cooldown` | OP | Skip cooldown |
| `dyrtp.bypass.warmup` | OP | Skip warmup |
| `dyrtp.bypass.economy` | OP | Skip payment |

Explicit denials are respected, including for OPs. Each bypass is checked separately. A permission does not enable an optional feature disabled in configuration.

## Compatibility and verification

Release listing: Minecraft 1.20.x through the currently available 26.x releases, with Bukkit, Spigot, Paper and Purpur. Compiled for Java 17 bytecode against Purpur/Bukkit API 1.20.4; use the Java runtime required by your server. The listing is broader than the completed live test matrix: version 1.0.1 passed 90 automated tests and isolated Paper 26.1.2 / Java 25 checks, including non-OP warmup, free RTP without a provider, shortcuts and normal RTP in all three dimensions. The earlier real DyClaim 1.0.2 and dotted-name live checks applied to 1.0.0.

Every Minecraft/platform release was not retested. Real Geyser/Floodgate transport, a real Vault economy provider and high-player-count performance remain unverified. Dotted names were checked with an actual offline Java protocol client, not a Bedrock device.

[Detailed verification record](https://github.com/DyPlugin/DyRTP/blob/1.0.1/docs/verification.md)

## Setup and upgrades

1. Stop the server and back up the current JAR and complete `plugins/DyRTP/` folder.
2. Install `DyRTP-1.0.1.jar` and start the server.
3. Edit `config.yml` and the English/Turkish message files as needed.
4. Run `/dyrtp admin reload` as a permitted player or from the console.

Existing world profiles, player preferences and custom translations are retained. Missing defaults are added without resetting files; unchanged stock blue presentation migrates to green. Invalid reloads preserve the working configuration. Automatic RTP remains disabled by default.

## Update notifications

DyRTP checks Modrinth for a compatible stable release and notifies administrators with its version link. It does not replace the JAR automatically. Install the current JAR manually or follow the version link in an administrator update notice.

## Source and support

DyRTP is licensed under LGPL-3.0-only. Contributions, bug reports and feature requests are welcome.

[Source code and issues](https://github.com/DyPlugin/DyRTP)

**By Claww / DyPlugin.**
