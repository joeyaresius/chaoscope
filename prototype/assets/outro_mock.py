"""
Mock of the video-export outro frame (renderOutroFrame in ChaoscopeViewModel)
using the v2 icon preview, to QA layout/colours before an on-device build.
"""

import os
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(__file__)
OUT = os.path.join(HERE, "..", "out")

size = 1080
img = Image.new("RGBA", (size, size), "#06060F")

icon = Image.open(os.path.join(OUT, "icon_v2_preview_round.png"))
icon_size = int(size * 0.35)
icon = icon.resize((icon_size, icon_size), Image.LANCZOS)
icon_left = (size - icon_size) // 2
icon_top = int(size * 0.20)
img.alpha_composite(icon, (icon_left, icon_top))

d = ImageDraw.Draw(img)
try:
    small = ImageFont.truetype("C:/Windows/Fonts/segoeui.ttf", int(size * 0.048))
    big = ImageFont.truetype("C:/Windows/Fonts/segoeuib.ttf", int(size * 0.10))
except OSError:
    small = big = ImageFont.load_default()

label_y = icon_top + icon_size + size * 0.08
d.text((size / 2, label_y), "Made with", font=small, fill="#AAAAAA", anchor="ms")
d.text((size / 2, label_y + size * 0.13), "Chaoscope", font=big,
       fill="#4FC3F7", anchor="ms")

img.save(os.path.join(OUT, "outro_mock.png"))
print("wrote", os.path.join(OUT, "outro_mock.png"))
