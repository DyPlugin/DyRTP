# DyRTP 1.1.0

This release adds biome choice, settings per rank, PlaceholderAPI placeholders, three more languages, MiniMessage formatting and a developer API. Existing settings, translations, aliases and player data are kept.

## Added

- **Biome choice.** `/dyrtp biome <biome>` (Turkish `/dyrtp biyom`) searches only the chosen biome, with TAB suggestions.
  - `biome-selection.biomes` limits the choice; an empty list allows every biome that is not blacklisted.
  - Blacklisted biomes such as oceans can never be chosen, and a world's own `allowed-biomes` list still applies.
  - Biome searches have their own attempt and time limits.
  - Price, cooldown and confirmation work as for a normal RTP.
  - Permission `dyrtp.biome`; switch `biome-selection.enabled`.
- **Settings per rank.**
  - `dyrtp.cooldown.<seconds>` and `dyrtp.price.<amount>` replace the cooldown and price for a player or group; the lowest one wins. `dyrtp.cooldown.0` removes the wait.
  - `dyrtp.radius.<blocks>` replaces the farthest distance; the highest one wins.
  - Switches: `cooldown.permission-overrides`, `economy.permission-prices` and `rtp.permission-radius`.
- **PlaceholderAPI.** `%dyrtp_cooldown%`, `%dyrtp_cooldown_formatted%`, `%dyrtp_ready%`, `%dyrtp_price%`, `%dyrtp_price_formatted%` and `%dyrtp_world%`. Switch: `integrations.placeholderapi.enabled`.
- **German, Spanish and Russian** messages. `language.default: auto` picks them from the Minecraft language.
- **MiniMessage.** On Paper and Purpur, messages may use MiniMessage tags such as `<green>` or `<gradient:#55ff55:#55ffff>` next to `&` codes. Adventure is not bundled, so on Spigot only `&` codes apply. Text typed by players is never treated as tags.
- **Target world for automatic RTP.** `first-join-rtp.world` and `death-rtp.world` send players to another world, for example the survival world.
- **Developer API.** `dev.dyrtp.api.DyRTPAPI` reports cooldown, price and destination world, tells whether a teleport is running, and can start a teleport like `/forcertp`. The existing events remain.
- **bStats** (plugin ID 34913), off with `metrics.enabled: false`.

## Changed

- **New license.** DyRTP 1.1.0 and later use the DyRTP Source-Available License 1.0, the same terms as DyClaim: free to use and modify on any server, no paid distribution, and distributed versions must share their source. Earlier versions keep LGPL-3.0-only.
- New installations read the vanilla `/worldborder` by default (`use-world-border: true`); `max-radius` still caps the distance. Existing configurations keep their value. Destinations outside the world border were and are always rejected.
- Unchanged stock help and language texts in existing message files list the new languages after the upgrade. Edited texts are kept.

## Updating

Stop the server, back up the existing JAR and the complete `plugins/DyRTP` folder, replace the JAR with **DyRTP-1.1.0.jar**, then restart. Missing settings and message keys are added without resetting your values.

Full changelog: https://dynastyplugins.com/dyrtp/versions/1.1.0

---

# DyRTP 1.0.1

Changes from 1.0.0:

- Retain destination chunks during warmup and release shared tickets on completion, failure, cancellation, reload and shutdown. Preserve final terrain and claim checks.
- Keep RTP free without Vault or a registered economy provider; reject positive prices and enabling payments until a provider is detected. Detect provider registration changes without enabling payments first.
- Add `/dyr` as a player shortcut and `/dyra` for the existing admin branch, preserving permission and language rules.
- Use Turkish `Oto`/`oto` in language labels, help and TAB while accepting `otomatik`. Migrate unchanged stock labels without replacing custom translations.
- Remove the help command’s own descriptive row from the help menu.
- Name new releases and JARs without a `v` prefix; preserve historical tags and downloads.
- Repeat the controlled BetterRTP comparison for the new artifact and update the existing chart with the measured results.

---

# DyRTP 1.0.0

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
