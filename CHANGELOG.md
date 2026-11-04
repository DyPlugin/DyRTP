# DyRTP 1.3.0

This release lets players choose the world they travel to, gives each world its own price and cooldown, and adds RTP signs and portal regions for a spawn exit. Existing settings, translations, aliases and player data are kept, and nothing changes until you turn the new options on.

## Added

- **Choose a world.** `/rtp world <world>`, or simply `/rtp <world>`, searches in another world, for example the Nether or the End. TAB lists the worlds the player may choose.
  - Off by default: turn it on with `world-selection.enabled: true`.
  - `world-selection.worlds` lists the worlds players may pick; empty allows every loaded world that is not in `rtp.disabled-worlds`.
  - Players need `dyrtp.world.<world>`, or `dyrtp.world.*`, which everyone has by default.
  - A paid world choice keeps its world through the confirmation step.
- **Price and cooldown per world.** `rtp.worlds.<world>.price` and `rtp.worlds.<world>.cooldown` (seconds) replace `economy.cost` and `cooldown.seconds` for RTP into that world. Rank permissions such as `dyrtp.price.<amount>` still replace them.
- **RTP signs.** An administrator writes `[RTP]` on the first line of a sign; right-clicking it starts an RTP. The second line may name a world to send players to.
  - Only players with `dyrtp.admin` can create one. The sign is marked in its own block data, so copying the text onto another sign does nothing.
  - Players need `dyrtp.sign`. Settings: `signs.enabled`, `signs.header` and `signs.header-color`.
- **Portal regions.** `/dyrtp admin portal pos1` and `pos2` mark two corners, `create <name> [world]` turns the space between them into a portal, and `delete` and `list` manage portals.
  - Walking into a portal starts one RTP per entry; cooldown and price apply as for `/rtp`. Portals are saved in `portals.yml`.
  - A portal side can be at most 64 blocks. Players need `dyrtp.portal`. Settings: `portals.enabled` and `portals.skip-warmup` (on, because the step into the portal would cancel a warmup).

## Upgrading

Stop the server, replace the JAR with `DyRTP-1.3.0.jar` and start it again. New settings and messages are added in every language; nothing you changed is replaced. The admin help gains the portal command.

Full changelog: https://dynastyplugins.com/dyrtp/versions/1.3.0
