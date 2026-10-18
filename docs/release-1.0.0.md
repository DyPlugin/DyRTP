# DyRTP 1.0.0

Changes from 1.0.0-Beta, focused on reliable RTP, bounded search work and clearer commands while preserving existing settings, data and aliases.

## Fixed and improved

- Wait for one candidate chunk result per search instead of generating speculative batches. Keep batched cheap checks, configured destination preloading and bounded attempts/time/concurrency.
- Recheck terrain, claims and supported redirected teleport targets immediately before teleport. Reject unsafe height/headroom, disabled worlds and cancelled/failed teleports.
- Stop stale callbacks after cancellation, disconnect, death, world changes, reload or shutdown from applying teleport, payment or cooldown. Preserve the native respawn transition for death RTP.
- Use DyClaim's actual claim-existence API, including negative chunk coordinates and owner claims. Reject newly claimed targets and stop safely on query failure or an unavailable installed hook.
- Bind expiring paid confirmations to the player, operation and quoted price/world. Use DyRTP's namespace for clickable confirmation; retain legacy root aliases without taking over other plugins' commands.
- Validate finite, non-negative prices. Keep debit/refund receipts bound to the original provider and prevent repeated or uncertain refunds. Preserve existing charge-on-success settings and success-based cooldown.
- Validate configuration before reload or administrator edits. Preserve working settings on invalid files and retain custom translations/player preferences.
- Preserve exact dotted player names in targets and TAB; remove ambiguous name guessing. Respect explicit permission denial and recheck permission after pre-teleport events.
- Apply global EN/TR overrides consistently to messages, help and TAB. Keep localized operation words, data arguments and old aliases. Use green defaults, concise help and short console messages.
- Correct the existing Modrinth checker's stable-release selection, Beta-to-stable comparison, compatibility filtering, errors and version links.

## Measured teleport time

![Median command-to-teleport time across five scenarios](https://raw.githubusercontent.com/DyPlugin/DyRTP/v1.0.0/docs/images/DyRTP-vs-BetterRTP-en.png)

Each plugin completed 70 requests in the isolated comparison. DyRTP had the lower observed median in these samples; this is not a CPU/RAM measurement or universal speed/safety guarantee. [Method and limitations](https://github.com/DyPlugin/DyRTP/blob/v1.0.0/docs/betterrtp-comparison.md).

## Verification and upgrading

80 automated tests passed. The final JAR was checked on Paper 26.3 / Java 25 with actual protocol clients, real DyClaim 1.0.2, all three dimensions, reload/restart, exact dotted-name targeting, permissions and effect preservation. Java 17 bytecode and the original API/dependency baseline are retained.

The release is listed for Minecraft 1.20.x through currently available 26.x releases on Bukkit, Spigot, Paper and Purpur. The completed live matrix is Paper 26.3; a real Vault provider, real Geyser/Floodgate transport, high-player-count load and every listed Minecraft/platform combination remain unverified. [Detailed verification](https://github.com/DyPlugin/DyRTP/blob/v1.0.0/docs/verification.md).

Stop the server, back up the old JAR and complete DyRTP data folder, replace the JAR and restart. Keep existing configuration/message/player files; missing defaults are added without resetting custom values. Beta users should upgrade manually once. No other plugin is required for core RTP; paid RTP requires Vault and a registered economy provider.
