# DyRTP 1.2.0

This release runs on Folia, finds safe spots ahead of time, lets friends travel together and works with DyHomes. Existing settings, translations, aliases and player data are kept.

## Added

- **Folia.** The same JAR runs on Bukkit, Spigot, Paper, Purpur and Folia. On Folia, destinations are read on the region that owns them and players are teleported asynchronously. Update notices on Folia look for Folia builds.
- **Ready spots.** `rtp.queue.size` (default 3, 0 turns it off) keeps safe spots per world found in advance. `/rtp` uses one right away and a new one is searched in the background.
  - `rtp.queue.worlds` fills chosen worlds at startup; other worlds are filled after their first RTP.
  - Every spot is checked again before the teleport. An unsafe or protected one falls back to a live search.
  - Biome choice and rank distances always search live.
- **Group RTP.** `/dyrtp group <player>` (Turkish `/dyrtp grup`) invites a player with clickable Accept and Decline buttons. `join`, `decline`, `leave` and `disband` (Turkish `katil`, `reddet`, `ayril`) manage the group.
  - Members within `group.max-distance` blocks (32) come along on the leader's next `/rtp` and land next to them.
  - `group.max-size` (4) counts the leader. Invitations last `invite-seconds` (60) and groups `expire-seconds` (300) without an RTP.
  - `member-cooldown` gives members the cooldown too. Permission `dyrtp.group`.
- **DyHomes hint.** With DyHomes installed, a successful RTP offers a click that types `/sethome` for you. Switch: `integrations.dyhomes.sethome-hint`.

## Upgrading

Stop the server, replace the JAR with `DyRTP-1.2.0.jar` and start it again. New settings (config version 10) and messages are added in every language; nothing you changed is replaced.

Full changelog: https://dynastyplugins.com/dyrtp/versions/1.2.0
