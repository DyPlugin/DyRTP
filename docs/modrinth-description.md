# DyRTP — Random Teleportation Done Right

Fast teleports. Safe locations. Clean commands.

DyRTP is a command-based random teleport plugin for survival, SMP and economy servers. Configure world profiles, protection rules, warmup and optional payments without adding a GUI. Version 1.0.0 improves search efficiency, destination validation, transaction handling and localized commands while preserving existing aliases, settings and YAML player preferences.

## Why DyRTP?

**Bounded searches.** Loaded candidates are checked immediately. Supported servers load chunks asynchronously, with one pending candidate load per search to avoid speculative generation. Attempt, time and concurrency limits keep each search bounded.

**Protection-aware destinations.** Configured terrain checks reject unsafe ground, liquids and obstructed headroom. Optional protection hooks exclude protected destinations; terrain and protection are checked again before teleport.

**Familiar commands.** English and Turkish messages, help and TAB follow the effective language. Existing aliases and dotted player names remain supported. Green defaults and short console messages keep the presentation consistent with the DyPlugin family.

## A measured look at teleport time

![DyRTP and BetterRTP median command-to-teleport time across five scenarios](https://raw.githubusercontent.com/DyPlugin/DyRTP/v1.0.0/docs/images/DyRTP-vs-BetterRTP-en.png)

On Paper 26.3 with one client, DyRTP had the lower observed median in all five matched-setting samples. Each plugin completed 70 requests. These are command-to-teleport durations, not CPU/RAM measurements, and do not establish a universal speed or safety advantage. Protection and economy were disabled for the timing comparison.

[Method, results and limitations](https://github.com/DyPlugin/DyRTP/blob/v1.0.0/docs/betterrtp-comparison.md)

## Random teleportation, your way

Use `/dyrtp` to search for a destination. Choose square or circular profiles, minimum/maximum radius, center, height limits, world-border behavior, biomes and world redirects. Normal worlds and the End use surface checks; the Nether uses a vertical ground/headroom scan. End void, configured hazardous blocks and obstructed destinations are rejected.

Configure warmup and movement/damage cancellation, cooldown, titles, action bars, sounds and optional blindness. Cooldown begins after successful teleport. Existing blindness is preserved; cancelled or stale operations cannot later teleport, charge or start cooldown.

First-join and death RTP are disabled by default. Death RTP can respect a bed or respawn anchor. Administrators can force RTP for an exact online player, including a name such as `.Player_Name`.

## Optional protection and economy

DyClaim is optional. With its hook enabled, claimed chunks are excluded even when the player owns or is trusted in that claim. Claims created after the search are checked again before teleport. An unavailable installed hook or failed DyClaim query stops the operation safely. Core RTP works without DyClaim.

Existing optional adapters include WorldGuard, GriefPrevention, Towny, Lands, Residence, GriefDefender and other configured protection plugins. Their presence in configuration does not establish compatibility with every provider version; validate your selected integrations.

Paid RTP requires Vault and a registered economy provider. Confirmation is bound to the player, quoted price and operation, expires and is single-use. Changed price/world settings require a new request. Use `/dyrtp confirm` and `/dyrtp cancel`; clickable buttons target DyRTP's own namespace so another plugin's root confirmation cannot approve a DyRTP operation.

The debit occurs immediately before teleport. The existing charge/refund settings are retained. Failed or uncertain provider transactions are not blindly retried; reconciliation details are logged. Enabling paid RTP without its dependencies does not silently provide free teleports.

## English and Turkish, automatically

With `language.default: auto`, a saved preference takes priority over client language when enabled; Turkish clients receive Turkish and other locales fall back to English. `/dyrtp language auto|en|tr` changes the personal preference. `/dyrtp admin language en|tr` forces one language for everyone; `auto` restores automatic selection.

Help and TAB show the effective language and permitted commands. Both languages and existing ASCII aliases remain accepted when typed. Player names, world names and data arguments are not translated.

## Commands

| Command | Purpose |
| --- | --- |
| `/dyrtp` | Search for a random destination |
| `/dyrtp help` | Show permitted commands |
| `/dyrtp language <auto/en/tr>` | Choose a personal language |
| `/dyrtp confirm` | Confirm the current paid RTP request |
| `/dyrtp cancel` | Reject a pending confirmation |
| `/dyrtp admin help` | Show administration commands |
| `/dyrtp admin force <player>` | Force RTP for an exact online player |
| `/dyrtp admin economy <on/off>` | Enable or disable paid RTP |
| `/dyrtp admin price <amount>` | Set a finite, non-negative price |
| `/dyrtp admin cooldown <seconds/off>` | Configure reuse cooldown |
| `/dyrtp admin delay <seconds/off>` | Configure teleport warmup |
| `/dyrtp admin radius min <blocks>` | Configure minimum radius |
| `/dyrtp admin radius max <blocks>` | Configure maximum radius |
| `/dyrtp admin language <auto/en/tr>` | Configure global language |
| `/dyrtp admin reload` | Validate and reload settings/messages |

`/rtp`, `/randomtp`, `/wild`, `/wildtp`, `/rastgeletp`, `/rastgeleteleport`, `/forcertp` and `/rtpforce` remain accepted. The old `yönetim`/`yonetim` branches and root confirmation aliases remain available, but another plugin can own an unqualified `/confirm` or `/cancel`. Prefer the DyRTP subcommands. Console senders can reload, manage global settings and force RTP.

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

Release listing: Minecraft 1.20.x through the currently available 26.x releases, with Bukkit, Spigot, Paper and Purpur. Compiled for Java 17 bytecode against Purpur/Bukkit API 1.20.4; use the Java runtime required by your server. The listing is broader than the completed live test matrix: version 1.0.0 passed 80 automated tests and isolated Paper 26.3 / Java 25 checks, including real DyClaim 1.0.2, normal RTP in all three dimensions, reload/restart and exact dotted-name targeting.

Every Minecraft/platform release was not retested. Real Geyser/Floodgate transport, a real Vault economy provider and high-player-count performance remain unverified. Dotted names were checked with an actual offline Java protocol client, not a Bedrock device.

[Detailed verification record](https://github.com/DyPlugin/DyRTP/blob/v1.0.0/docs/verification.md)

## Setup and upgrades

1. Stop the server and back up the current JAR and complete `plugins/DyRTP/` folder.
2. Replace the JAR with DyRTP 1.0.0 and start the server.
3. Edit `config.yml` and the English/Turkish message files as needed.
4. Run `/dyrtp admin reload` as a permitted player or from the console.

Existing world profiles, player preferences and custom translations are retained. Missing defaults are added without resetting files; unchanged stock blue presentation migrates to green. Invalid reloads preserve the working configuration. Automatic RTP remains disabled by default.

## Update notifications

DyRTP checks Modrinth for a compatible stable release and notifies administrators with its version link. It does not replace the JAR automatically. Beta users should install 1.0.0 manually; the corrected checker applies after upgrading.

## Open source

DyRTP is licensed under LGPL-3.0-only. Contributions, bug reports and feature requests are welcome.

[Source code and issues](https://github.com/DyPlugin/DyRTP)
