"""Write isolated benchmark settings without installing a YAML dependency."""
import json
import pathlib
import re
import sys

root = pathlib.Path(sys.argv[2]).resolve() if len(sys.argv) > 2 else pathlib.Path(r"C:\Users\alph\AppData\Local\Temp\DyRTP-BetterRTP-comparison-20261023")
project = pathlib.Path(r"C:\Users\alph\Documents\DyRTP")
if sys.argv[1] not in ("small", "large"):
    raise SystemExit("Use small or large")
low, high = (500, 1000) if sys.argv[1] == "large" else (16, 96)

def patched(text, changes):
    lines, output, stack = text.splitlines(), [], []
    skip = None
    for line in lines:
        match = re.match(r"^( *)([A-Za-z0-9_-]+):(?:\s*(.*))?$", line)
        indent = len(line) - len(line.lstrip())
        if skip is not None:
            if not line.strip() or line.lstrip().startswith("#") or indent > skip or (indent == skip and line.lstrip().startswith("-")):
                continue
            skip = None
        if not match:
            output.append(line)
            continue
        indent, key, value = len(match[1]), match[2], match[3]
        while stack and stack[-1][0] >= indent:
            stack.pop()
        path = ".".join([item[1] for item in stack] + [key])
        if path in changes:
            output.append(" " * indent + key + ": " + json.dumps(changes[path]))
            if not value or value.lstrip().startswith("#"):
                skip = indent
        else:
            output.append(line)
        stack.append((indent, key))
    return "\n".join(output) + "\n"

dy = (project / "src/main/resources/config.yml").read_text(encoding="utf-8")
blacklist = []
capture = False
for line in dy.splitlines():
    if line.strip() == "blacklisted-blocks:":
        capture = True
    elif capture and re.match(r"^      - ", line):
        blacklist.append(line.strip()[2:])
    elif capture and line.strip() and not line.lstrip().startswith("#"):
        break
dy_changes = {
    "language.default": "en", "warmup.enabled": False, "cooldown.enabled": False,
    "economy.enabled": False, "protection.enabled": False, "first-join-rtp.enabled": False,
    "death-rtp.enabled": False, "feedback.titles.enabled": False, "feedback.sounds.enabled": False,
    "feedback.effects.blindness.enabled": False, "rtp.safety.min-distance-from-current": 0,
    "rtp.safety.blacklisted-biomes": [], "rtp.search.max-attempts": 32,
    "rtp.search.preload-radius": 1, "rtp.search.wait-for-preload": False,
    "rtp.default-world.min-radius": low, "rtp.default-world.max-radius": high,
}
(root / "plugins/DyRTP/config.yml").write_text(patched(dy, dy_changes), encoding="utf-8")
better = (root / "BetterRTP-stock-config.yml").read_text(encoding="utf-8")
better_changes = {
    "Settings.PreloadRadius": 1, "Settings.MaxAttempts": 32, "Settings.Cooldown.Enabled": False,
    "Settings.Delay.Enabled": False, "Settings.Delay.Time": 0, "Settings.Queue.Enabled": False,
    "Settings.DisableUpdater": True, "Settings.Logger.Enabled": False,
    "Default.MinRadius": low, "Default.MaxRadius": high, "Default.MinY": -64,
    "Default.MaxY": 320, "BlacklistedBlocks": blacklist,
    "DisabledWorlds": [], "CustomWorlds": [], "Overrides": [],
    "WorldType": [{"benchmark": "NORMAL"}, {"benchmark_nether": "NETHER"}, {"benchmark_the_end": "NORMAL"}],
}
(root / "plugins/BetterRTP/config.yml").write_text(patched(better, better_changes), encoding="utf-8")
effects = (root / "BetterRTP-stock-effects.yml").read_text(encoding="utf-8")
(root / "plugins/BetterRTP/effects.yml").write_text(patched(effects, {
    "Sounds.Enabled": False, "Titles.Enabled": False, "Particles.Enabled": False,
    "Invincible.Enabled": False, "Potions.Enabled": False,
}), encoding="utf-8")
economy = (root / "BetterRTP-stock-economy.yml").read_text(encoding="utf-8")
(root / "plugins/BetterRTP/economy.yml").write_text(patched(economy, {
    "Economy.Enabled": False, "Hunger.Enabled": False, "CustomWorlds.Enabled": False,
}), encoding="utf-8")
print(f"Isolated radius={low}..{high}, common blacklist entries={len(blacklist)}, queue/delay/economy disabled")
