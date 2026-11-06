# DyRTP 1.4.0

This release makes DyRTP lighter on busy servers and adds near and far teleports, a one-time way back, RTP statistics, commands after a teleport, keeping away from other players and a search test for administrators. Existing settings, translations, aliases and player data are kept, and every new option stays off until you turn it on.

## Added

- **Near and far.** `/rtp near` and `/rtp far` search between their own distances from the world's center instead of the world's usual radius.
  - Off by default: turn it on with `distance-presets.enabled: true`.
  - `distance-presets.near` (100 to 1000 blocks) and `distance-presets.far` (5000 to 20000 blocks) set `min-radius` and `max-radius`. The world border still limits the distance.
  - Players need `dyrtp.near` or `dyrtp.far`, which everyone has by default. Cooldown and price apply as for `/rtp`, including the payment confirmation.
- **Go back once.** `/rtp back` takes a player back to where they stood before their last `/rtp`, sign or portal RTP. Forced, first-join and death RTP open no way back.
  - Off by default: `back.enabled`. `back.expire-seconds` (120; 0 keeps it until the next RTP) and `back.cooldown-seconds` (0) control it. Players need `dyrtp.back`.
  - A failed return keeps the way back for another try.
- **RTP statistics.** `/rtp stats` shows how many successful random teleports a player made; `/rtp stats <player>` needs `dyrtp.stats.others` (operators). `%dyrtp_count%` works with PlaceholderAPI, also for offline players.
  - Off by default: `stats.enabled`. Counts are kept in `players.yml` and written in the background.
- **Commands after a teleport.** `after-teleport-commands` runs commands after a successful RTP, for example a starter kit.
  - Start a line with `console:` or `player:`; `{player}`, `{world}`, `{x}`, `{y}` and `{z}` are filled in.
  - Off by default: `after-teleport-commands.enabled`, with an empty `commands` list. `first-rtp-only` limits the commands to a player's first RTP.
  - Console commands run on the server thread and player commands on the player's own thread, so this is safe on Folia.
- **Keep away from players.** `rtp.safety.avoid-players-radius` rejects destinations within that many blocks of other players. Spectators and the player asking are not counted, and the check is repeated just before the teleport. 0, off, by default.
- **Test a search.** `/dyrtp admin test [world]` runs one search and reports how long it took and how many attempts it needed. Nobody is teleported.

## Changed

- Movement checks for warmups and portals return before reading any setting when the player has no warmup or the server has no portals.
- Damage events only read the warmup setting for players with a warmup running.
- Portals are indexed by world, so a step only looks at the portals of the player's own world.
- A search reads its settings once instead of on every step, reads the world border once per step instead of three times per spot, reuses one point for the border check and checks each spot against the border only once.
- Spots near other players are rejected before their chunk is loaded or protection is asked.
- Disabled worlds and blacklisted blocks and biomes are cached until the configuration changes, so placeholders, ready-spot refills and final safety checks no longer copy these lists on every call.
- Block safety checks look up liquid, fire and leaf kinds in a table built once at startup instead of comparing block names for every block.
- Player data access is synchronised, so saves in the background are safe.

## Upgrading

Stop the server, replace the JAR with `DyRTP-1.4.0.jar` and start it again. New settings and messages are added in every language; nothing you changed is replaced. The admin help gains the test command.

Full changelog: https://dynastyplugins.com/dyrtp/versions/1.4.0
