#!/usr/bin/env python3
"""Generator ikony SmartTube+ (320x320).

Telewizor z pionowym gradientem róż -> niebieski i białym "S+" na ekranie.
Wzór kształtu: oryginalna ikona SmartTube (mipmap-nodpi/app_icon.png).
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

# --- KOLORY (zmień tu, żeby zmienić wygląd) ---
GRAD_TOP = (255, 77, 158)    # róż (góra)
GRAD_BOTTOM = (46, 107, 255) # niebieski (dół)
TEXT = (255, 255, 255)
SIZE = 320

# Kształt telewizora (proporcje z oryginalnej ikony)
SCREEN = (14, 22, 306, 232)      # x0, y0, x1, y1 — rama ekranu
SCREEN_RADIUS = 26
STAND_TOP = (70, 244, 250, 258)  # łącznik
STAND_BASE = (30, 258, 290, 296) # nóżka (trapez)


def make_icon() -> Image.Image:
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    grad = Image.new("RGBA", (1, SIZE))
    for y in range(SIZE):
        t = y / (SIZE - 1)
        c = tuple(int(GRAD_TOP[i] + (GRAD_BOTTOM[i] - GRAD_TOP[i]) * t) for i in range(3)) + (255,)
        grad.putpixel((0, y), c)
    grad = grad.resize((SIZE, SIZE))

    mask = Image.new("L", (SIZE, SIZE), 0)
    d = ImageDraw.Draw(mask)
    d.rounded_rectangle(SCREEN, radius=SCREEN_RADIUS, fill=255)
    d.rectangle(STAND_TOP, fill=255)
    # nóżka jako trapez (szersza u dołu)
    d.polygon([(46, STAND_BASE[1]), (274, STAND_BASE[1]),
               (290, STAND_BASE[3]), (30, STAND_BASE[3])], fill=255)

    img.paste(grad, (0, 0), mask)

    # "S+" pośrodku ekranu
    d2 = ImageDraw.Draw(img)
    font = ImageFont.truetype(r"C:\Windows\Fonts\arialbd.ttf", 150)
    text = "S+"
    bbox = d2.textbbox((0, 0), text, font=font)
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    cx = (SCREEN[0] + SCREEN[2]) / 2 - bbox[0] - tw / 2
    cy = (SCREEN[1] + SCREEN[3]) / 2 - bbox[1] - th / 2
    d2.text((cx, cy), text, font=font, fill=TEXT)
    return img


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else None
    icon = make_icon()
    if out:
        icon.save(out)
        print(f"OK: {out}")
        return
    root = Path(__file__).resolve().parents[2]
    targets = [
        root / "smarttubetv/src/main/res/mipmap-nodpi/app_icon.png",
        root / "smarttubetv/src/ststable/res/mipmap-nodpi/app_icon.png",
        root / "smarttubetv/src/ststable/res/mipmap-nodpi-v30/app_icon.png",
        root / "smarttubetv/src/stbeta/res/mipmap-nodpi/app_icon.png",
        root / "smarttubetv/src/stbeta/res/mipmap-nodpi-v30/app_icon.png",
        root / "smarttubetv/src/stfdroid/res/mipmap-nodpi/app_icon.png",
        root / "smarttubetv/src/stfdroid/res/mipmap-nodpi-v30/app_icon.png",
    ]
    for t in targets:
        if t.exists():
            icon.save(t)
            print(f"OK: {t}")
        else:
            print(f"brak (pominięte): {t}")


if __name__ == "__main__":
    main()
