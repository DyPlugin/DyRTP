"""Render the documented final RTP measurements as PNG and editable SVG.

Uses the existing Pillow runtime; no product dependency is added.
"""
from pathlib import Path
from html import escape
import re
from PIL import Image, ImageDraw, ImageFont

PROJECT = Path(__file__).resolve().parents[2]
REPORT = PROJECT / "docs/betterrtp-comparison.md"
OUTPUT = PROJECT / "docs/images"
OUTPUT.mkdir(exist_ok=True)
pattern = r"^\| (Overworld .*?|Nether .*?|End .*?) \| (\d+) \| ([\d.]+) / ([\d.]+) \| ([\d.]+) / ([\d.]+) \|$"
rows = re.findall(pattern, REPORT.read_text(encoding="utf-8"), re.MULTILINE)
assert len(rows) == 5, "Expected exactly five final-artifact scenarios"
assert sum(int(row[1]) for row in rows) == 70

W, H = 2000, 1680
BG, INK, MUTED = "#f4f2ed", "#18191b", "#686862"
GREEN, GRAY, GRID = "#248b46", "#aaa8a0", "#e2dfd7"
image = Image.new("RGB", (W, H), BG)
draw = ImageDraw.Draw(image)
svg = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">',
       '<title>DyRTP versus BetterRTP: median command-to-teleport time</title>',
       '<desc>Five matched-setting samples on Paper 26.1.2. 70 successful requests per plugin. '
       'Lower is faster. One client; protection and queue disabled; not CPU or RAM usage.</desc>',
       f'<rect width="{W}" height="{H}" fill="{BG}"/>']

def text(x, y, value, size=32, color=INK, bold=False, right=False):
    font = ImageFont.truetype("C:/Windows/Fonts/arialbd.ttf" if bold else "C:/Windows/Fonts/arial.ttf", size)
    width = draw.textlength(value, font=font)
    actual_x = x - width if right else x
    assert 0 <= actual_x and actual_x + width <= W, f"Text outside canvas: {value}"
    draw.text((actual_x, y), value, font=font, fill=color, anchor="lt")
    svg.append(f'<text x="{x}" y="{y}" fill="{color}" font-family="Arial, sans-serif" '
               f'font-size="{size}" font-weight="{700 if bold else 400}" '
               f'dominant-baseline="text-before-edge" text-anchor="{"end" if right else "start"}">{escape(value)}</text>')

def rect(x, y, width, height, color):
    draw.rectangle((x, y, x + width, y + height), fill=color)
    svg.append(f'<rect x="{x}" y="{y}" width="{width:.3f}" height="{height}" fill="{color}"/>')

text(150, 80, "DyRTP", 68, bold=True)
text(1850, 104, "2 PLUGINS · 70 RTPs EACH", 26, MUTED, right=True)
text(150, 254, "Same settings. Measured RTP.", 68, bold=True)
text(150, 356, "Lower median time in these samples.", 64, GREEN, True)
text(150, 454, "Command-to-teleport time. Lower is faster.", 34, MUTED)
rect(150, 523, 26, 26, GREEN)
text(190, 518, "DyRTP 1.0.1", 30, bold=True)
rect(515, 523, 26, 26, GRAY)
text(555, 518, "BetterRTP 3.6.13", 30)
text(1850, 520, "MEDIAN · ms", 28, MUTED, right=True)

left, plot_width, maximum = 150, 1450, 200.0
for index, (scenario, count, dy, dy95, better, better95) in enumerate(rows):
    y = 592 + index * 144
    label = scenario.replace(" first pass", " · first pass").replace(" second pass", " · second pass")
    label = label.replace("Overworld ", "Overworld · ").replace("Nether ", "Nether · ").replace("End ", "End · ")
    text(left, y, label, 32, bold=True)
    text(1850, y + 3, f"{count} RTPs / plugin", 26, MUTED, right=True)
    for offset, value, color in [(49, float(dy), GREEN), (87, float(better), GRAY)]:
        assert 0 <= value <= maximum
        rect(left, y + offset, plot_width, 24, GRID)
        rect(left, y + offset, plot_width * value / maximum, 24, color)
        text(1850, y + offset - 8, f"{value:.2f} ms", 34, color if color == GREEN else INK, True, True)

# Both series share a zero baseline and a 0–200 ms linear scale.
for value in (0, 50, 100, 150, 200):
    x = left + plot_width * value / maximum
    text(x, 1312, str(value), 24, MUTED, right=value == 200)
rect(150, 1370, 1700, 2, "#d7d3cb")
text(150, 1401, "70 successful RTPs each. Basic check failures: DyRTP 0; BetterRTP 1.", 29, bold=True)
text(150, 1454, "Paper 26.1.2 · Java 25 · one client · restored world snapshot · queue and protection disabled.", 25, MUTED)
text(150, 1497, "Median samples: 10–20 requests per scenario. Random destinations; not CPU/RAM usage.", 25, MUTED)
text(150, 1540, "Flat Overworld; results are not a general performance or safety guarantee. Method and p95 in the report.", 25, MUTED)
text(150, 1620, "modrinth.com/plugin/dyrtp", 28, bold=True)
text(1850, 1624, "23.10.2026", 24, MUTED, right=True)
svg.append("</svg>")
image.save(OUTPUT / "DyRTP-vs-BetterRTP-en.png", optimize=True)
(OUTPUT / "DyRTP-vs-BetterRTP-en.svg").write_text("\n".join(svg), encoding="utf-8")
print("Rendered 2000x1680 PNG and SVG from five documented final measurement rows.")
