#!/usr/bin/env -S uv run
# /// script
# dependencies = ["pillow"]
# ///
"""Generate icon.png from the Android dark-mode vector drawable."""

import re
import xml.etree.ElementTree as ET
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).parent.parent
FOREGROUND = ROOT / "app/src/main/res/drawable/ic_launcher_foreground.xml"
NIGHT_COLORS = ROOT / "app/src/main/res/values-night/colors.xml"
OUT = ROOT / "fastlane/metadata/android/en-US/images/icon.png"

NS = "http://schemas.android.com/apk/res/android"

# --- Parse colors ---
colors = {
    el.get("name"): el.text
    for el in ET.parse(NIGHT_COLORS).getroot()
}
bg_hex = colors["ic_launcher_background"].lstrip("#")
stroke_hex = colors["ic_launcher_stroke"].lstrip("#")
bg = tuple(int(bg_hex[i:i+2], 16) for i in (0, 2, 4)) + (255,)
stroke = tuple(int(stroke_hex[i:i+2], 16) for i in (0, 2, 4)) + (255,)

# --- Parse foreground XML ---
tree = ET.parse(FOREGROUND)
root = tree.getroot()

viewport_w = float(root.get(f"{{{NS}}}viewportWidth"))
viewport_h = float(root.get(f"{{{NS}}}viewportHeight"))

group = root.find("group")
pivot_x = float(group.get(f"{{{NS}}}pivotX"))
pivot_y = float(group.get(f"{{{NS}}}pivotY"))
scale_x = float(group.get(f"{{{NS}}}scaleX"))
scale_y = float(group.get(f"{{{NS}}}scaleY"))

paths = [
    (p.get(f"{{{NS}}}pathData"), float(p.get(f"{{{NS}}}strokeWidth", 1)))
    for p in group.findall("path")
    if p.get(f"{{{NS}}}pathData")
]


# --- Parse SVG path (M and C commands only) ---
def parse_path(d):
    """Return list of cubic bezier segments as (p0, p1, p2, p3) tuples."""
    tokens = re.findall(r"[MCL]|[-+]?[0-9]*\.?[0-9]+", d)
    segments = []
    pos = [0.0, 0.0]
    i = 0
    while i < len(tokens):
        cmd = tokens[i]
        i += 1
        if cmd == "M":
            pos = [float(tokens[i]), float(tokens[i+1])]
            i += 2
        elif cmd == "C":
            while i < len(tokens) and tokens[i] not in "MCL":
                x1, y1 = float(tokens[i]),   float(tokens[i+1])
                x2, y2 = float(tokens[i+2]), float(tokens[i+3])
                x,  y  = float(tokens[i+4]), float(tokens[i+5])
                segments.append((tuple(pos), (x1, y1), (x2, y2), (x, y)))
                pos = [x, y]
                i += 6
        elif cmd == "L":
            while i < len(tokens) and tokens[i] not in "MCL":
                x, y = float(tokens[i]), float(tokens[i+1])
                # Straight line as degenerate cubic bezier
                segments.append((tuple(pos), tuple(pos), (x, y), (x, y)))
                pos = [x, y]
                i += 2
    return segments


def apply_transform(x, y):
    x = (x - pivot_x) * scale_x + pivot_x
    y = (y - pivot_y) * scale_y + pivot_y
    return (x, y)


def cubic_bezier(p0, p1, p2, p3, t):
    x = (1-t)**3*p0[0] + 3*(1-t)**2*t*p1[0] + 3*(1-t)*t**2*p2[0] + t**3*p3[0]
    y = (1-t)**3*p0[1] + 3*(1-t)**2*t*p1[1] + 3*(1-t)*t**2*p2[1] + t**3*p3[1]
    return (x, y)


# --- Render ---
RENDER = 2048
TARGET = 512
S = RENDER / viewport_w

img = Image.new("RGBA", (RENDER, RENDER), (0, 0, 0, 0))
draw = ImageDraw.Draw(img)

corner_radius = int(112 * S)
draw.rounded_rectangle([0, 0, RENDER - 1, RENDER - 1], radius=corner_radius, fill=bg)

for path_data, stroke_width in paths:
    segments = parse_path(path_data)
    points = []
    for seg in segments:
        p0, p1, p2, p3 = [apply_transform(*p) for p in seg]
        for i in range(301):
            t = i / 300
            pt = cubic_bezier(p0, p1, p2, p3, t)
            points.append((pt[0] * S, pt[1] * S))
    r = (stroke_width * scale_x * S) / 2
    for x, y in points:
        draw.ellipse([x - r, y - r, x + r, y + r], fill=stroke)

out = img.resize((TARGET, TARGET), Image.LANCZOS)
out.save(OUT)
print(f"Written to {OUT}")
