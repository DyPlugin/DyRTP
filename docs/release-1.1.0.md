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
