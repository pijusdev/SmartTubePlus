#!/usr/bin/env python3
"""Wstawia oficjalną ikonę SmartTube+ (zip od Usera) do wszystkich mipmap.

Źródło: STPLUS/tools/icon_new/ (wypakowany android_smarttube_icon.zip)
Użycie:
    python STPLUS/tools/apply_icon.py

Wymiar 320x320: ikona startowa (app_icon) — 1:1 z oryginałem.
Wymiar 320x180: logo w belce (app_logo*) — ikona kwadratowa 180x180
wycentrowana na przezroczystym pasku (slot w belce jest szeroki).
"""
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ICON_DIR = ROOT / "STPLUS/tools/icon_new"

# ikona startowa (320x320)
ICONS_320 = [
    "smarttubetv/src/main/res/mipmap-nodpi/app_icon.png",
    "smarttubetv/src/ststable/res/mipmap-nodpi/app_icon.png",
    "smarttubetv/src/ststable/res/mipmap-nodpi-v30/app_icon.png",
    "smarttubetv/src/stbeta/res/mipmap-nodpi/app_icon.png",
    "smarttubetv/src/stbeta/res/mipmap-nodpi-v30/app_icon.png",
    "smarttubetv/src/stfdroid/res/mipmap-nodpi/app_icon.png",
    "smarttubetv/src/stfdroid/res/mipmap-nodpi-v30/app_icon.png",
]
# logo w górnej belce (320x180) — nadpisywane przez motyw (appLogo w styles.xml)
LOGOS_320x180 = [
    "smarttubetv/src/main/res/mipmap-nodpi/app_logo.png",
    "smarttubetv/src/main/res/mipmap-nodpi/app_logo_semi_red.png",
    "smarttubetv/src/main/res/mipmap-nodpi/app_logo_semi_grey.png",
    "smarttubetv/src/main/res/mipmap-nodpi/app_logo_none.png",
    "smarttubetv/src/main/res/mipmap-nodpi/app_icon_alt.png",
]
# logo w belce w FLAVORACH (180x180, kwadrat) — NADPISUJĄ main, stąd stara ikona!
LOGOS_180 = [
    "smarttubetv/src/ststable/res/mipmap-nodpi/app_logo.png",
    "smarttubetv/src/ststable/res/mipmap-nodpi/app_logo_semi_red.png",
    "smarttubetv/src/ststable/res/mipmap-nodpi/app_logo_semi_grey.png",
    "smarttubetv/src/stbeta/res/mipmap-nodpi/app_logo.png",
    "smarttubetv/src/stbeta/res/mipmap-nodpi/app_logo_semi_red.png",
    "smarttubetv/src/stbeta/res/mipmap-nodpi/app_logo_semi_grey.png",
    "smarttubetv/src/stfdroid/res/mipmap-nodpi/app_logo.png",
    "smarttubetv/src/stfdroid/res/mipmap-nodpi/app_logo_semi_red.png",
    "smarttubetv/src/stfdroid/res/mipmap-nodpi/app_logo_semi_grey.png",
]


def main():
    candidates = sorted(ICON_DIR.rglob("*.png"), key=lambda p: p.stat().st_size, reverse=True)
    if not candidates:
        raise SystemExit(f"Brak PNG w {ICON_DIR}")
    src_path = candidates[0]
    print(f"Źródło: {src_path}")

    src = Image.open(src_path).convert("RGBA")

    for rel in ICONS_320:
        t = ROOT / rel
        if t.exists():
            src.resize((320, 320), Image.LANCZOS).save(t)
            print(f"OK (320x320): {t}")
        else:
            print(f"brak (pominięte): {t}")

    logo = Image.new("RGBA", (320, 180), (0, 0, 0, 0))
    sq = src.resize((180, 180), Image.LANCZOS)
    logo.paste(sq, ((320 - 180) // 2, 0), sq)
    for rel in LOGOS_320x180:
        t = ROOT / rel
        if t.exists():
            logo.save(t)
            print(f"OK (320x180): {t}")
        else:
            print(f"brak (pominięte): {t}")

    # kwadratowe logo 180x180 dla flavorów (nadpisują main!)
    for rel in LOGOS_180:
        t = ROOT / rel
        if t.exists():
            sq.save(t)
            print(f"OK (180x180): {t}")
        else:
            print(f"brak (pominięte): {t}")


if __name__ == "__main__":
    main()
