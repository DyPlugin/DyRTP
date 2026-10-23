# Changelog

## 1.0.1

Changes from 1.0.0:

- Retain destination chunks during warmup and release shared tickets on completion, failure, cancellation, reload and shutdown. Preserve final terrain and claim checks.
- Keep RTP free without Vault or a registered economy provider; reject positive prices and enabling payments until a provider is detected. Detect provider registration changes without enabling payments first.
- Add `/dyr` as a player shortcut and `/dyra` for the existing admin branch, preserving permission and language rules.
- Use Turkish `Oto`/`oto` in language labels, help and TAB while accepting `otomatik`. Migrate unchanged stock labels without replacing custom translations.
- Remove the help command’s own descriptive row from the help menu.
- Name new releases and JARs without a `v` prefix; preserve historical tags and downloads.
- Repeat the controlled BetterRTP comparison for the new artifact and update the existing chart with the measured results.

## 1.0.0

Changes from 1.0.0-Beta:

- Green default presentation, concise permission-aware help and language-specific TAB suggestions. Preserve existing command aliases and custom translations.
- Global English/Turkish language mode now overrides personal/client preferences. Localize operation words while preserving data arguments.
- Add confirmation/cancellation under the DyRTP command namespace, with expiring request tokens and quoted price/world validation. Preserve legacy root aliases without taking over other plugins' commands.
- Stop cancelled/reloaded/disconnected requests and stale callbacks from applying teleport, payment or cooldown. Validate final terrain, claim and redirected world targets before teleport.
- Preserve the native cross-world respawn transition for death RTP while cancelling subsequent external changes.
- Preserve dotted Bedrock/Floodgate names in player targets and TAB; remove ambiguous partial/name-stripping fallback that could select another player.
- Fail safely on DyClaim query failures or disabled installed DyClaim. Recheck newly claimed destinations and retain owner/trust-independent claim avoidance.
- Bound search work, concurrency and duration; respect disabled chunk loading and world height/headroom boundaries.
- Wait for each candidate chunk result before loading another; retain batched cheap checks and configured destination preloading without speculative candidate generation.
- Reject invalid prices and settings. Track debits/refunds through the original provider and prevent repeated or uncertain refunds. Preserve charge-on-success configuration and success-based cooldown timing.
- Validate reload and administrator edits before replacing running settings; preserve player preferences on malformed reload and add missing defaults without resetting custom values.
- Reject malformed safety/world lists and recheck player permissions after public pre-teleport handlers and in the final teleport guard.
- Filter the existing Modrinth checker by game version/platform, prefer stable releases, handle Beta-to-stable comparison, distinguish API errors and link to the selected release.
- Add automated regression coverage and an isolated integration probe; document actual tested environments and unverified integrations.
