#!/usr/bin/env python3
"""Deko-Blöcke der Villa: Möbel, Partyzubehör, Küchengeräte und Goldkram (eigene Pixelgrafik).

Jeder Block besteht aus Kästen (Elementen) mit Materialien. Materialien sind kachelbare 16×16-Texturen
(Gold, Samt, Marmor, Glas …) oder Bilder für eine Fläche (Herdplatte, Flaschenregal, Neonschild).
Die Vorderseite zeigt im Modell nach Norden; der Blockzustand dreht nach der Blickrichtung.

Schreibt nach src/main/resources:
  deko_bloecke.json                          Liste für ModDeko.java (Name, Form, Licht, Klang)
  assets/stinkendreich/textures/block/deko/  Materialien (zwei davon animiert)
  assets/stinkendreich/models/…, blockstates/…, items/…
  data/stinkendreich/loot_table/blocks/…

Wird von tools/assets/assets.py aufgerufen (Sprachdateien und Werkzeug-Listen).
"""
from __future__ import annotations

import json
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw

WURZEL = Path(__file__).resolve().parents[2]
RES = WURZEL / "src/main/resources"
ASSETS = RES / "assets/stinkendreich"
NS = "stinkendreich"
TEX = ASSETS / "textures/block/deko"

GOLD = (232, 186, 52)
GOLD_HELL = (255, 228, 120)
GOLD_DUNKEL = (160, 112, 22)
LEER = (0, 0, 0, 0)


def neu(farbe=(0, 0, 0), alpha=255, w=16, h=16):
    img = Image.new("RGBA", (w, h), (*farbe, alpha))
    return img, ImageDraw.Draw(img)


def rauschen(img, staerke, saat):
    rnd = random.Random(saat)
    px = img.load()
    for x in range(img.width):
        for y in range(img.height):
            r, g, b, a = px[x, y]
            if a:
                d = rnd.randint(-staerke, staerke)
                px[x, y] = (max(0, min(255, r + d)), max(0, min(255, g + d)), max(0, min(255, b + d)), a)
    return img


# ---------------------------------------------------------------------------
# Materialien
# ---------------------------------------------------------------------------


def m_gold():
    img, d = neu(GOLD)
    for y in range(16):
        d.line([(0, y), (15, y)], fill=GOLD_HELL if y % 5 == 0 else (GOLD_DUNKEL if y % 5 == 3 else GOLD))
    for x, y in ((3, 2), (11, 7), (6, 12)):
        d.point((x, y), fill=(255, 250, 210))
    return img


def m_chrom():
    img, d = neu((196, 202, 210))
    for y in range(16):
        g = 196 + int(30 * math.sin(y / 15 * math.pi))
        d.line([(0, y), (15, y)], fill=(g, g + 4, g + 10))
    return rauschen(img, 3, 2)


def m_samt_rot():
    img, _ = neu((150, 20, 34))
    return rauschen(img, 10, 3)


def m_holz_dunkel():
    img, d = neu((70, 42, 26))
    rnd = random.Random(4)
    for y in range(16):
        if rnd.random() < 0.35:
            d.line([(0, y), (15, y)], fill=(56, 32, 20))
    return rauschen(img, 4, 4)


def m_marmor():
    img, d = neu((238, 236, 232))
    rauschen(img, 4, 5)
    rnd = random.Random(5)
    x, y = 2, 0
    for _ in range(24):
        d.point((x % 16, y % 16), fill=(176, 174, 180))
        x += rnd.choice((0, 1, 1))
        y += 1
    return img


def m_lack_weiss():
    img, d = neu((244, 244, 240))
    d.rectangle([0, 0, 15, 15], outline=(222, 222, 216))
    d.line([(2, 1), (13, 1)], fill=GOLD)
    return img


def m_glas():
    img, d = neu((0, 0, 0), 0)
    for i in range(3):
        d.point((2 + i, 5 - i), fill=(236, 246, 252, 255))
        d.point((11 + i, 13 - i), fill=(236, 246, 252, 255))
    d.rectangle([0, 0, 15, 15], outline=(220, 232, 240, 255))
    return img


def m_schwarzglas():
    img, d = neu((18, 18, 22))
    for i in range(4):
        d.point((3 + i, 6 - i), fill=(70, 74, 86))
    return img


def m_keramik():
    img, _ = neu((246, 246, 244))
    return rauschen(img, 2, 6)


def m_pink():
    img, d = neu((240, 120, 170))
    for y in range(0, 16, 4):
        d.line([(0, y), (15, y)], fill=(255, 160, 200))
    return rauschen(img, 3, 7)


def m_schnabel():
    img, d = neu((240, 180, 190))
    d.rectangle([0, 8, 15, 15], fill=(30, 30, 30))
    return img


def m_led():
    img, _ = neu((255, 250, 232))
    return img


def m_kerze():
    img, d = neu((246, 240, 224))
    d.rectangle([0, 0, 15, 3], fill=(255, 200, 80))
    d.rectangle([5, 0, 10, 1], fill=(255, 250, 200))
    return img


def m_kristall():
    img, d = neu((0, 0, 0), 0)
    for x in range(16):
        for y in range(16):
            if (x + y) % 4 == 0 or (x - y) % 4 == 0:
                d.point((x, y), fill=(220, 236, 255, 255))
    return img


def m_spiegel():
    """Discokugel: Spiegelfacetten, 4 Bilder, die Lichtpunkte wandern."""
    bilder = []
    for f in range(4):
        img, d = neu((150, 156, 168))
        for x in range(0, 16, 2):
            for y in range(0, 16, 2):
                g = 150 + ((x * 7 + y * 13 + f * 5) % 7) * 15
                d.rectangle([x, y, x + 1, y + 1], fill=(g, min(255, g + 6), min(255, g + 20)))
                d.point((x + 1, y + 1), fill=(90, 96, 110))
        for i in range(3):
            x, y = (3 + i * 5 + f * 4) % 16, (2 + i * 6 + f * 3) % 16
            d.point((x, y), fill=(255, 255, 255))
            d.point(((x + 1) % 16, y), fill=(255, 240, 255))
        bilder.append(img)
    return bilder


def m_tanz():
    """Tanzfläche: 2 × 2 Leuchtfelder mit dunklen Fugen, Farben wechseln reihum (8 Bilder)."""
    farben = [(255, 40, 160), (40, 220, 255), (255, 220, 40), (90, 255, 90), (180, 70, 255), (255, 120, 30)]
    bilder = []
    for f in range(8):
        img, d = neu((10, 10, 14))
        for i, (x0, y0) in enumerate(((1, 1), (9, 1), (1, 9), (9, 9))):
            c = farben[(f + (0, 2, 3, 1)[i]) % len(farben)]
            d.rectangle([x0, y0, x0 + 5, y0 + 5], fill=c)
            d.rectangle([x0 + 1, y0 + 1, x0 + 4, y0 + 4], fill=tuple(min(255, v + 60) for v in c))
        bilder.append(img)
    return bilder


def m_goldbarren():
    img, d = neu(GOLD)
    d.rectangle([0, 0, 15, 15], outline=GOLD_DUNKEL)
    d.line([(1, 1), (14, 1)], fill=GOLD_HELL)
    d.rectangle([5, 6, 10, 9], outline=GOLD_DUNKEL)
    d.point((7, 7), fill=GOLD_DUNKEL)
    d.point((8, 8), fill=GOLD_DUNKEL)
    return img


def m_geld_oben():
    """Geldbündel von oben: grüner Schein mit Muster, Papierband in der Mitte."""
    img, d = neu((120, 170, 110))
    d.rectangle([0, 0, 15, 15], outline=(80, 120, 74))
    d.ellipse([5, 3, 10, 8], outline=(70, 110, 64))
    d.line([(2, 11), (13, 11)], fill=(90, 140, 84))
    d.line([(2, 13), (9, 13)], fill=(90, 140, 84))
    d.rectangle([0, 6, 15, 9], fill=(236, 226, 196))
    d.line([(0, 6), (15, 6)], fill=(200, 186, 150))
    return img


def m_geld_seite():
    img, d = neu((200, 220, 190))
    for y in range(0, 16, 2):
        d.line([(0, y), (15, y)], fill=(130, 170, 120))
    d.rectangle([6, 0, 9, 15], fill=(236, 226, 196))
    return img


def m_flaschen():
    """Barregal von vorn: drei Fächer voller bunter Flaschen, Hintergrundlicht."""
    img, d = neu((40, 26, 18))
    rnd = random.Random(9)
    for fach in range(3):
        y0 = fach * 5 + 1
        d.rectangle([1, y0, 14, y0 + 3], fill=(240, 196, 110))
        x = 2
        while x < 14:
            farbe = rnd.choice([(40, 110, 50), (120, 30, 30), (200, 160, 60), (230, 230, 240), (60, 40, 30), (40, 80, 140)])
            d.rectangle([x, y0 + 1, x + 1, y0 + 3], fill=farbe)
            d.point((x, y0), fill=farbe)
            x += 3
        d.line([(0, y0 + 4), (15, y0 + 4)], fill=GOLD_DUNKEL)
    return img


def m_herd_oben():
    img, d = neu((16, 16, 20))
    for cx, cy in ((4, 4), (11, 4), (4, 11), (11, 11)):
        d.ellipse([cx - 3, cy - 3, cx + 3, cy + 3], outline=(110, 110, 120))
        d.point((cx, cy), fill=(200, 60, 40))
    d.line([(0, 15), (15, 15)], fill=GOLD)
    return img


def m_ofen_front():
    img, d = neu((236, 236, 232))
    d.rectangle([1, 4, 14, 14], fill=(26, 26, 30))
    d.rectangle([3, 7, 12, 12], fill=(60, 40, 30))
    d.point((4, 8), fill=(255, 160, 80))
    d.point((9, 10), fill=(255, 120, 60))
    d.line([(3, 5), (12, 5)], fill=GOLD)
    for x in (3, 6, 9, 12):
        d.point((x, 2), fill=(60, 60, 66))
    return img


def m_kuehl_front():
    img, d = neu((200, 206, 214))
    rauschen(img, 3, 10)
    d.line([(7, 0), (7, 15)], fill=(120, 126, 134))
    d.rectangle([5, 2, 5, 13], fill=GOLD)
    d.rectangle([9, 2, 9, 13], fill=GOLD)
    d.rectangle([11, 3, 13, 5], fill=(30, 40, 60))
    return img


def m_weinkuehl_front():
    img, d = neu((20, 22, 30))
    for fach in range(4):
        y = 2 + fach * 3
        d.line([(1, y + 2), (14, y + 2)], fill=(70, 50, 30))
        for x in range(2, 14, 3):
            d.rectangle([x, y, x + 1, y + 1], fill=(90, 20, 40) if (x + fach) % 2 else (40, 70, 40))
    d.rectangle([0, 0, 15, 15], outline=(160, 166, 176))
    d.line([(14, 4), (14, 11)], fill=GOLD)
    return img


def m_champagner():
    img, d = neu((30, 70, 40))
    d.rectangle([0, 0, 15, 4], fill=GOLD)
    d.rectangle([2, 7, 13, 11], fill=(236, 226, 196))
    return img


def m_cocktail():
    img, d = neu((255, 140, 60))
    d.rectangle([0, 0, 15, 4], fill=(255, 210, 120))
    return img


def m_sektglas():
    """Gefüllte Sektschale von außen: goldener Sekt mit Perlen, heller Glasrand oben."""
    img, d = neu((248, 212, 104))
    d.line([(0, 0), (15, 0)], fill=(240, 246, 252))
    d.line([(0, 1), (15, 1)], fill=(255, 236, 160))
    for x, y in ((2, 5), (6, 9), (10, 4), (13, 11), (4, 13), (9, 7)):
        d.point((x, y), fill=(255, 250, 225))
    return img


def m_flasche_gruen():
    """Sektflasche: dunkelgrünes Glas mit goldener Halsfolie und hellem Etikett."""
    img, d = neu((26, 64, 36))
    d.rectangle([0, 0, 15, 3], fill=GOLD)
    d.rectangle([0, 8, 15, 11], fill=(236, 226, 196))
    d.line([(3, 0), (3, 15)], fill=(52, 104, 66))
    return img


def m_badewasser():
    """Badewasser von oben: hellblau mit Wellen und etwas Schaum."""
    img, d = neu((92, 172, 228))
    rnd = random.Random(52)
    for y in range(1, 16, 4):
        for x in range(16):
            if (x + y) % 5 in (0, 1):
                d.point((x, y), fill=(150, 206, 244))
    for _ in range(14):
        d.point((rnd.randrange(16), rnd.randrange(16)), fill=(244, 250, 255))
    return img


def m_goldmuenzen():
    """Berg aus Goldmünzen: übereinanderliegende runde Münzen mit Glanzpunkt."""
    img, d = neu((150, 104, 20))
    rnd = random.Random(77)
    for _ in range(26):
        x, y = rnd.randrange(-2, 16), rnd.randrange(-2, 16)
        d.ellipse([x, y, x + 4, y + 3], fill=GOLD, outline=GOLD_DUNKEL)
        d.point((x + 1, y + 1), fill=GOLD_HELL)
    return img


def m_gaming_schwarz():
    """Kunstleder des Gaming-Stuhls: schwarz mit Steppnähten."""
    img, d = neu((28, 28, 32))
    for y in range(2, 16, 5):
        d.line([(0, y), (15, y)], fill=(48, 48, 54))
    return rauschen(img, 3, 91)


def m_gaming_rot():
    """Rote Seitenwangen des Gaming-Stuhls."""
    img, d = neu((196, 26, 36))
    for y in range(1, 16, 4):
        d.line([(0, y), (15, y)], fill=(226, 52, 60))
    return rauschen(img, 3, 92)


def m_sekt():
    """Sektgläser: Glas mit goldenem Inhalt, oben durchsichtig."""
    img, d = neu((0, 0, 0), 0)
    d.rectangle([0, 0, 15, 15], outline=(230, 240, 250, 255))
    d.rectangle([1, 6, 14, 14], fill=(250, 214, 110, 255))
    for x in (3, 8, 12):
        d.point((x, 9), fill=(255, 250, 220, 255))
    return img


def neon(text: str, farbe) -> Image.Image:
    """Leuchtschrift auf durchsichtigem Grund (5-Pixel-Schrift, eigene Buchstaben)."""
    buchstaben = {
        "B": ["110", "101", "110", "101", "110"], "A": ["010", "101", "111", "101", "101"],
        "R": ["110", "101", "110", "101", "101"], "V": ["101", "101", "101", "101", "010"],
        "I": ["111", "010", "010", "010", "111"], "P": ["110", "101", "110", "100", "100"],
        "C": ["011", "100", "100", "100", "011"], "L": ["100", "100", "100", "100", "111"],
        "U": ["101", "101", "101", "101", "111"], "W": ["101", "101", "101", "111", "101"],
        "E": ["111", "100", "110", "100", "111"], "N": ["101", "111", "111", "101", "101"],
        "S": ["011", "100", "010", "001", "110"], "H": ["101", "101", "111", "101", "101"],
        "O": ["111", "101", "101", "101", "111"],
    }
    img, d = neu((0, 0, 0), 0)
    breite = len(text) * 4 - 1
    x0, y0 = (16 - breite) // 2, 5
    hell = tuple(min(255, v + 90) for v in farbe)
    for i, ch in enumerate(text):
        for zy, zeile in enumerate(buchstaben[ch]):
            for zx, bit in enumerate(zeile):
                if bit == "1":
                    d.point((x0 + i * 4 + zx, y0 + zy), fill=(*hell, 255))
    # Leuchtrand um die Schrift
    px = img.load()
    for x in range(16):
        for y in range(16):
            if px[x, y][3] == 0 and any(0 <= x + dx < 16 and 0 <= y + dy < 16 and px[x + dx, y + dy][:3] == hell
                                        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                px[x, y] = (*farbe, 255)
    return img


GRUEN_NOT = (0, 150, 72)


def m_notausgang_gruen():
    img, d = neu(GRUEN_NOT)
    return img


def m_notausgang_mann():
    """Linke Hälfte des Notausgang-Schilds: weißes Männchen, das nach rechts zur Tür läuft (Zeilen 3–12)."""
    img, d = neu(GRUEN_NOT)
    w = (255, 255, 255, 255)
    d.rectangle([9, 3, 10, 4], fill=w)                               # Kopf
    d.line([(9, 5), (7, 8)], fill=w, width=2)                        # Rumpf
    d.line([(9, 6), (12, 7)], fill=w)                                # Arm vorn
    d.line([(8, 6), (5, 5)], fill=w)                                 # Arm hinten
    d.line([(7, 9), (9, 10), (10, 12)], fill=w)                      # Bein vorn
    d.line([(7, 9), (5, 10), (3, 11)], fill=w)                       # Bein hinten
    d.line([(13, 8), (15, 8)], fill=w)                               # Pfeil nach rechts
    d.point((14, 7), fill=w)
    d.point((14, 9), fill=w)
    return img


def m_notausgang_tuer():
    """Rechte Hälfte: offene Tür (weißer Rahmen, Licht dahinter)."""
    img, d = neu(GRUEN_NOT)
    w = (255, 255, 255, 255)
    d.rectangle([3, 3, 10, 12], outline=w)
    d.rectangle([4, 4, 9, 12], fill=(200, 255, 220, 255))
    d.polygon([(4, 4), (7, 5), (7, 12), (4, 12)], fill=w)           # aufgeschwungener Flügel
    return img


def m_fluegel_tasten():
    img, d = neu((240, 240, 236))
    for x in range(0, 16, 2):
        d.line([(x, 0), (x, 15)], fill=(200, 200, 196))
    for x in (1, 3, 7, 9, 11, 15):
        d.rectangle([x, 0, x, 8], fill=(20, 20, 24))
    return img


def m_fluegel_innen():
    """Innenleben des Flügels von oben: goldene Gussplatte mit Löchern, darüber die Saiten."""
    img, d = neu((200, 156, 44))
    rauschen(img, 5, 31)
    for x in range(1, 16, 3):
        d.line([(x, 0), (x, 15)], fill=(214, 206, 188))
    return img


def m_noten():
    """Notenblatt: weißes Papier mit fünf Notenlinien und ein paar Noten."""
    img, d = neu((246, 244, 236))
    for y in (3, 5, 7, 9, 11):
        d.line([(1, y), (14, y)], fill=(70, 70, 70))
    for x, y in ((3, 8), (6, 6), (9, 9), (12, 5)):
        d.point((x, y), fill=(10, 10, 10))
        d.point((x + 1, y), fill=(10, 10, 10))
    return img


def m_samt_knoepfe():
    """Gepolstertes Kopfteil: roter Samt mit goldenen Knöpfen im Rautenmuster, Goldkante."""
    img, d = neu((140, 18, 32))
    rauschen(img, 6, 21)
    for y in range(2, 16, 4):
        for x in range(2 + (y // 4 % 2) * 2, 16, 4):
            d.point((x, y), fill=GOLD)
            d.point((x - 1, y - 1), fill=(110, 12, 24))
    d.rectangle([0, 0, 15, 15], outline=GOLD_DUNKEL)
    return img


def m_tastatur():
    img, d = neu((30, 30, 34))
    for y in range(1, 15, 3):
        for x in range(1, 15, 2):
            d.point((x, y), fill=(150, 150, 158))
    return img


def m_maus():
    img, d = neu((40, 40, 44))
    d.line([(7, 0), (7, 6)], fill=(20, 20, 22))
    d.point((7, 3), fill=(80, 200, 255))
    return img


def m_lack_schwarz():
    img, d = neu((14, 14, 18))
    for i in range(5):
        d.point((2 + i, 8 - i), fill=(80, 80, 92))
    return img


def m_leder_weiss():
    """Weißes Nappaleder mit feiner Steppnaht am Rand."""
    img, d = neu((236, 232, 222))
    rauschen(img, 3, 31)
    for i in range(1, 15, 2):
        d.point((i, 1), fill=(214, 208, 196))
        d.point((i, 14), fill=(214, 208, 196))
    return img


def m_samt_schwarz():
    img, _ = neu((30, 28, 34))
    return rauschen(img, 6, 32)


def m_satin_gold():
    """Bettdecke: champagnerfarbener Satin mit Glanzstreifen und Steppkaros."""
    img, d = neu((206, 168, 96))
    for i in range(-16, 16, 6):
        d.line([(i, 15), (i + 15, 0)], fill=(226, 192, 120))
    for x in range(0, 16, 8):
        d.line([(x, 0), (x, 15)], fill=(180, 142, 74))
    for y in range(0, 16, 8):
        d.line([(0, y), (15, y)], fill=(180, 142, 74))
    return rauschen(img, 3, 33)


def m_satin_schwarz():
    """Bettdecke: schwarzer Satin mit weichem, schrägem Glanz (kein Raster, sonst wirkt es wie Dielen)."""
    img, d = neu((22, 20, 26))
    px = img.load()
    for x in range(16):
        for y in range(16):
            g = (x + y) % 16
            licht = max(0, 10 - abs(g - 8) * 2)
            px[x, y] = (22 + licht, 20 + licht, 26 + licht + licht // 2, 255)
    return rauschen(img, 2, 42)


def m_laken():
    """Bettwäsche: weißer Satin mit Goldpaspel an der Kante."""
    img, d = neu((246, 244, 238))
    rauschen(img, 2, 34)
    d.line([(0, 15), (15, 15)], fill=GOLD)
    return img


def m_matratze():
    img, d = neu((240, 238, 232))
    for x in range(0, 16, 4):
        for y in range(2 + (x // 4 % 2) * 2, 16, 4):
            d.point((x, y), fill=(214, 212, 206))
    return img


def m_kissen_weiss():
    img, d = neu((250, 250, 246))
    d.rectangle([0, 0, 15, 15], outline=(222, 220, 214))
    d.line([(2, 2), (6, 2)], fill=(255, 255, 255))
    return img


def m_fell():
    """Weißes Fell (Überwurf am Bettende)."""
    img, d = neu((236, 234, 230))
    rnd = random.Random(35)
    for _ in range(60):
        x, y = rnd.randrange(16), rnd.randrange(16)
        d.line([(x, y), (x + rnd.choice((-1, 0, 1)), y + 1)], fill=rnd.choice([(212, 208, 202), (255, 255, 252), (224, 220, 214)]))
    return img


def m_teak():
    """Teakholz der Strandliegen: warm, mit Maserung."""
    img, d = neu((150, 96, 54))
    rnd = random.Random(36)
    for y in range(16):
        if rnd.random() < 0.4:
            d.line([(0, y), (15, y)], fill=(128, 80, 44))
    for y in range(0, 16, 4):
        d.line([(0, y), (15, y)], fill=(100, 62, 32))
    return rauschen(img, 4, 36)


def m_polster_weiss():
    """Dickes Liegenpolster mit Paspel."""
    img, d = neu((244, 242, 236))
    rauschen(img, 2, 37)
    d.rectangle([0, 0, 15, 15], outline=(210, 206, 198))
    for x in (5, 10):
        d.point((x, 7), fill=(214, 210, 202))
    return img


def m_handtuch():
    """Frotteehandtuch, türkis-weiß gestreift."""
    img, d = neu((250, 250, 250))
    for y in range(16):
        if y % 6 in (0, 1, 2):
            d.line([(0, y), (15, y)], fill=(40, 186, 196))
    return rauschen(img, 3, 38)


def m_schirm():
    """Sonnenschirmstoff: Creme mit Goldbordüre."""
    img, d = neu((246, 238, 220))
    rauschen(img, 2, 39)
    d.rectangle([0, 0, 15, 15], outline=GOLD)
    return img


def m_schirm_glatt():
    img, _ = neu((246, 238, 220))
    return rauschen(img, 2, 43)


def m_lampenschirm():
    img, d = neu((252, 236, 196))
    for y in range(16):
        d.line([(0, y), (15, y)], fill=(255, 240 - y, 200 - y * 2))
    d.line([(0, 0), (15, 0)], fill=GOLD)
    d.line([(0, 15), (15, 15)], fill=GOLD)
    return img


def m_nachttisch_front():
    """Nachttisch von vorn: zwei Schubladen in schwarzem Lack mit Goldgriffen."""
    img, d = neu((16, 16, 20))
    for y0 in (3, 9):
        d.rectangle([2, y0, 13, y0 + 4], outline=(46, 46, 52))
        d.line([(6, y0 + 2), (9, y0 + 2)], fill=GOLD)
    d.line([(0, 15), (15, 15)], fill=GOLD_DUNKEL)
    return img


def m_fernbedienung_hd():
    """Fernbedienung von oben (64 × 64, 4 Texel je Pixel; genutzt wird oben links 16 × 40 für 4 × 10 Pixel):
    schwarzes Gehäuse mit Goldrand, rote Ein-Taste, goldenes Steuerkreuz mit OK, Zifferntasten, Lautstärkewippen."""
    img = Image.new("RGBA", (64, 64), (18, 18, 22, 255))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 15, 39], fill=(26, 26, 30), outline=GOLD_DUNKEL)
    d.ellipse([10, 2, 13, 5], fill=(220, 40, 40))                       # Ein/Aus
    d.point((11, 3), fill=(255, 140, 140))
    d.rectangle([3, 3, 6, 4], fill=GOLD)                                 # Schriftzug
    d.ellipse([3, 9, 12, 18], outline=GOLD)                              # Steuerkreuz
    d.ellipse([6, 12, 9, 15], fill=(60, 60, 68), outline=(110, 110, 120))
    for x, y in ((7, 10), (7, 16), (4, 13), (10, 13)):
        d.point((x, y), fill=(240, 220, 150))
    for zeile in range(4):                                               # Ziffern 1 … 9, 0
        for spalte in range(3):
            if zeile == 3 and spalte != 1:
                continue
            x, y = 3 + spalte * 4, 21 + zeile * 4
            d.rectangle([x, y, x + 2, y + 2], fill=(58, 58, 66))
            d.point((x + 1, y + 1), fill=(200, 200, 210))
    d.rectangle([1, 21, 1, 30], fill=(80, 80, 90))                       # Lautstärke
    d.rectangle([14, 21, 14, 30], fill=(80, 80, 90))                     # Programm
    d.rectangle([5, 37, 10, 38], fill=GOLD_DUNKEL)
    return img


def m_popcorn_tuete():
    img, d = neu((250, 250, 250))
    for x in range(0, 16, 4):
        d.rectangle([x, 0, x + 1, 15], fill=(210, 30, 40))
    return img


def m_popcorn():
    img, d = neu((250, 236, 190))
    rnd = random.Random(40)
    for _ in range(40):
        d.point((rnd.randrange(16), rnd.randrange(16)), fill=rnd.choice([(255, 250, 230), (236, 200, 110), (255, 255, 255)]))
    return img


def m_schreibunterlage():
    """Schreibtischunterlage aus schwarzem Leder mit feiner Goldnaht (64 × 64, passt zum Kasten x 0–16,
    z 2,5–13,5; die Naht liegt 2 Texel innerhalb der Kante)."""
    img = Image.new("RGBA", (64, 64), (26, 24, 26, 255))
    rauschen(img, 3, 41)
    d = ImageDraw.Draw(img)
    x0, z0, x1, z1 = 2, 12, 61, 51
    for x in range(x0, x1 + 1, 3):
        d.line([(x, z0), (x + 1, z0)], fill=(150, 116, 46))
        d.line([(x, z1), (x + 1, z1)], fill=(150, 116, 46))
    for z in range(z0, z1 + 1, 3):
        d.line([(x0, z), (x0, z + 1)], fill=(150, 116, 46))
        d.line([(x1, z), (x1, z + 1)], fill=(150, 116, 46))
    return img


def m_buero_hd():
    """Fein aufgelöste Bürotextur (64 × 64, 4 Texel pro Pixel): Tastatur oben links (48 × 18), Maus darunter.
    Gemalt wie der Sitzende sie sieht und dann um 180° gedreht, weil er von Norden auf den Block schaut."""
    img = Image.new("RGBA", (64, 64), (18, 18, 22, 255))
    # --- Tastatur (aus Sicht des Sitzenden) ---
    t = Image.new("RGBA", (48, 18), (24, 24, 28, 255))
    d = ImageDraw.Draw(t)
    d.rectangle([0, 0, 47, 17], outline=(70, 70, 78))
    taste, hell, schrift = (44, 44, 50), (58, 58, 66), (200, 200, 210)
    # Reihen: F-Tasten, Zahlen, drei Buchstabenreihen, unten Leertaste; rechts der Ziffernblock
    reihen_y = (1, 4, 7, 10, 13)
    for i, y in enumerate(reihen_y):
        for x in range(2, 35, 3):
            d.rectangle([x, y, x + 1, y + 1], fill=taste)
            d.point((x, y), fill=hell)
            if (x + i) % 2:
                d.point((x + 1, y + 1), fill=schrift)
    d.rectangle([2, 15, 7, 16], fill=taste)
    d.rectangle([9, 15, 26, 16], fill=taste)           # Leertaste
    d.line([(9, 15), (26, 15)], fill=hell)
    d.rectangle([28, 15, 34, 16], fill=taste)
    for y in reihen_y[1:]:
        for x in (38, 41, 44):
            d.rectangle([x, y, x + 1, y + 1], fill=taste)
            d.point((x, y), fill=hell)
    d.rectangle([38, 15, 42, 16], fill=taste)
    d.point((45, 1), fill=(80, 220, 120))               # Kontrollleuchte
    d.point((46, 1), fill=(80, 220, 120))
    img.paste(t.rotate(180), (0, 0))
    # --- Maus (7 × 12 Texel bei 0,24): Tasten vorn, Mausrad, Goldlogo hinten ---
    m = Image.new("RGBA", (7, 12), (22, 22, 26, 255))
    d = ImageDraw.Draw(m)
    d.rectangle([0, 0, 6, 11], outline=(52, 52, 58))
    d.line([(3, 0), (3, 4)], fill=(8, 8, 10))
    d.rectangle([3, 1, 3, 2], fill=(150, 150, 160))
    d.point((3, 8), fill=GOLD)
    d.point((1, 1), fill=(90, 90, 100))
    img.paste(m.rotate(180), (0, 24))
    return img


# Regendusche (Duschkopf und Armatur, siehe assets.py und mechanik/Dusche.java)
def m_duesen(an: bool):
    """Unterseite der Regenbrause: dunkles Chrom mit einem Raster aus Düsen (an: hellblau leuchtend)."""
    img, d = neu((150, 156, 166))
    rauschen(img, 3, 21)
    for x in range(1, 16, 2):
        for y in range(1, 16, 2):
            d.point((x, y), fill=(150, 205, 255) if an else (70, 74, 82))
    return img


def m_duschstrahl():
    """Wasserstrahl unter der Brause (animiert): senkrechte, durchsichtige Wasserfäden, deren Tropfen nach unten
    wandern. Nur ganze Pixel (Ausschnitt-Modus), die Lücken bleiben durchsichtig."""
    rnd = random.Random(22)
    spalten = [(x, rnd.randrange(8), rnd.choice(((200, 230, 255), (160, 205, 245), (235, 248, 255)))) for x in range(0, 16, 2)]
    bilder = []
    for schritt in range(8):
        img, d = neu((0, 0, 0), 0)
        for x, versatz, farbe in spalten:
            for y in range(16):
                if (y - schritt * 2 - versatz) % 8 < 4:
                    d.point((x, y), fill=(*farbe, 255))
        bilder.append(img)
    return bilder


def m_armatur():
    """Rosette der Duscharmatur: Chrom mit Skala, links rot (warm), rechts blau (kalt)."""
    img, d = neu((0, 0, 0), 0)
    d.ellipse([0, 0, 15, 15], fill=(200, 206, 214), outline=(150, 156, 166))
    d.ellipse([2, 2, 13, 13], fill=(222, 228, 236))
    d.arc([1, 1, 14, 14], 120, 240, fill=(220, 50, 50))
    d.arc([1, 1, 14, 14], -60, 60, fill=(50, 110, 230))
    return img


MATERIALIEN = {
    "gold": m_gold, "chrom": m_chrom, "samt_rot": m_samt_rot, "holz_dunkel": m_holz_dunkel, "marmor": m_marmor,
    "lack_weiss": m_lack_weiss, "glas": m_glas, "schwarzglas": m_schwarzglas, "keramik": m_keramik, "pink": m_pink,
    "schnabel": m_schnabel, "led": m_led, "kerze": m_kerze, "kristall": m_kristall, "goldbarren": m_goldbarren,
    "geld_oben": m_geld_oben, "geld_seite": m_geld_seite, "flaschen": m_flaschen, "herd_oben": m_herd_oben,
    "ofen_front": m_ofen_front, "kuehl_front": m_kuehl_front, "weinkuehl_front": m_weinkuehl_front,
    "champagner": m_champagner, "cocktail": m_cocktail, "sekt": m_sekt, "fluegel_tasten": m_fluegel_tasten,
    "lack_schwarz": m_lack_schwarz, "fluegel_innen": m_fluegel_innen, "noten": m_noten, "tastatur": m_tastatur, "maus": m_maus, "samt_knoepfe": m_samt_knoepfe,
    "neon_bar": lambda: neon("BAR", (255, 40, 160)), "neon_vip": lambda: neon("VIP", (255, 200, 40)),
    "neon_club": lambda: neon("CLUB", (40, 220, 255)),
    "neon_wein": lambda: neon("WEIN", (255, 70, 90)),
    "neon_show": lambda: neon("SHOW", (255, 60, 200)),
    "notausgang_gruen": m_notausgang_gruen, "notausgang_mann": m_notausgang_mann, "notausgang_tuer": m_notausgang_tuer,
    "sektglas": m_sektglas, "flasche_gruen": m_flasche_gruen, "badewasser": m_badewasser, "goldmuenzen": m_goldmuenzen,
    "gaming_schwarz": m_gaming_schwarz, "gaming_rot": m_gaming_rot,
    "leder_weiss": m_leder_weiss, "samt_schwarz": m_samt_schwarz, "satin_gold": m_satin_gold, "satin_schwarz": m_satin_schwarz, "laken": m_laken,
    "matratze": m_matratze, "kissen_weiss": m_kissen_weiss, "fell": m_fell, "teak": m_teak,
    "polster_weiss": m_polster_weiss, "handtuch": m_handtuch, "schirm": m_schirm, "schirm_glatt": m_schirm_glatt, "lampenschirm": m_lampenschirm,
    "nachttisch_front": m_nachttisch_front, "fernbedienung_hd": m_fernbedienung_hd, "popcorn_tuete": m_popcorn_tuete, "popcorn": m_popcorn,
    "schreibunterlage": m_schreibunterlage, "buero_hd": m_buero_hd,
    "duesen": lambda: m_duesen(False), "duesen_an": lambda: m_duesen(True), "armatur": m_armatur,
}
ANIMIERT = {"spiegel": (m_spiegel, 5), "tanz": (m_tanz, 8), "duschstrahl": (m_duschstrahl, 1)}

# ---------------------------------------------------------------------------
# Modelle: Kästen in Pixeln, Vorderseite nach Norden
# ---------------------------------------------------------------------------


def k(von, bis, mat, uv=None, drehung=None, **flaechen):
    """Kasten mit einem Material; einzelne Flächen (north, up …) können ein anderes bekommen.
    uv: eigene Ausschnitte je Fläche (sonst nach Lage im Block); drehung: {"angle", "axis", "origin"}."""
    t = {"von": von, "bis": bis, "mat": mat, "flaechen": flaechen}
    if uv:
        t["uv"] = uv
    if drehung:
        t["drehung"] = drehung
    return t


def gedreht(achse, winkel, ursprung):
    return {"angle": winkel, "axis": achse, "origin": ursprung}


def ring(y0, y1, a0, a1, dicke, mat):
    """Viereckiger Ring (z. B. Schwimmring, Kronleuchterreif) aus vier Kästen."""
    return [k([a0, y0, a0], [a1, y1, a0 + dicke], mat), k([a0, y0, a1 - dicke], [a1, y1, a1], mat),
            k([a0, y0, a0 + dicke], [a0 + dicke, y1, a1 - dicke], mat), k([a1 - dicke, y0, a0 + dicke], [a1, y1, a1 - dicke], mat)]


def kronleuchter():
    teile = [k([6, 15, 6], [10, 16, 10], "gold"), k([7.5, 6, 7.5], [8.5, 15, 8.5], "gold"),
             k([2, 6, 7.5], [14, 7, 8.5], "gold"), k([7.5, 6, 2], [8.5, 7, 14], "gold"),
             k([7, 2, 7], [9, 4, 9], "kristall")]
    teile += ring(4, 5, 2, 14, 1, "gold")
    for x, z in ((2, 2), (13, 2), (2, 13), (13, 13), (7.5, 2), (7.5, 13), (2, 7.5), (13, 7.5)):
        teile.append(k([x, 5, z], [x + 1, 8, z + 1], "kerze"))
    for x, z in ((4, 2), (11, 2), (4, 13), (11, 13), (2, 4), (2, 11), (13, 4), (13, 11)):
        teile.append(k([x, 2, z], [x + 1, 4, z + 1], "kristall"))
    return teile


def goldbarren():
    teile = []
    for x in (2, 6, 10):
        teile.append(k([x, 0, 3], [x + 4, 3, 13], "goldbarren"))
    for x in (4, 8):
        teile.append(k([x, 3, 3], [x + 4, 6, 13], "goldbarren"))
    teile.append(k([6, 6, 3], [10, 9, 13], "goldbarren"))
    return teile


def geldstapel():
    return [k([1, 0, 2], [8, 7, 14], "geld_seite", up="geld_oben"),
            k([8, 0, 2], [15, 4, 14], "geld_seite", up="geld_oben"),
            k([9, 4, 4], [14, 6, 13], "geld_seite", up="geld_oben")]


def pokal():
    return [k([4, 0, 4], [12, 2, 12], "gold"), k([5, 2, 5], [11, 4, 11], "schwarzglas"),
            k([7, 4, 7], [9, 8, 9], "gold"), k([5, 8, 5], [11, 14, 11], "gold"),
            k([3, 9, 7.5], [5, 13, 8.5], "gold"), k([11, 9, 7.5], [13, 13, 8.5], "gold")]


def kaffeemaschine():
    return [k([3, 0, 6], [13, 12, 14], "chrom", north="schwarzglas"), k([3, 0, 3], [13, 1, 6], "chrom"),
            k([6, 5, 3], [10, 6, 6], "schwarzglas"), k([6, 1, 3.5], [8, 3, 5.5], "keramik"),
            k([4, 12, 7], [12, 13, 13], "gold")]


def spuele():
    return [k([0, 0, 0], [16, 14, 16], "lack_weiss"), k([0, 14, 0], [16, 16, 16], "marmor"),
            k([7, 16, 12], [9, 24, 14], "gold"), k([7, 22, 7], [9, 24, 12], "gold")]


def dunstabzug():
    return [k([1, 0, 2], [15, 5, 16], "chrom", down="led"), k([5, 5, 9], [11, 16, 16], "chrom")]


def goldtoilette():
    return [k([4, 0, 2], [12, 7, 12], "gold"), k([3.5, 7, 1.5], [12.5, 8, 12], "gold"),
            k([3, 7, 12], [13, 15, 16], "gold"), k([7, 15, 13], [9, 16, 15], "keramik")]


def barhocker():
    return [k([4, 0, 4], [12, 1, 12], "gold"), k([7, 1, 7], [9, 9, 9], "gold"),
            k([5, 4, 5], [11, 5, 11], "gold"), k([4, 9, 4], [12, 11, 12], "samt_rot")]


def cocktail():
    return [k([7.5, 0, 7.5], [8.5, 3, 8.5], "glas"), k([6, 3, 6], [10, 6, 10], "cocktail"),
            k([9, 6, 8], [10, 9, 9], "holz_dunkel"), k([7, 9, 6], [12, 10, 11], "pink")]


def champagner():
    return [k([5, 0, 5], [11, 6, 11], "chrom"), k([7, 4, 7], [9, 13, 9], "champagner")]


def sektturm():
    """Sektturm: einzelne gefüllte Schalen auf Stielen in drei Ebenen (3 × 3, 2 × 2, 1) auf einem Goldtablett,
    obendrauf die Flasche (vorher waren es gestapelte Glaskästen, die verbuggt aussahen)."""
    t = [k([0.5, 0, 0.5], [15.5, 0.5, 15.5], "gold")]

    def schale(x, y, z):
        return [k([x - 0.3, y, z - 0.3], [x + 0.3, y + 1.6, z + 0.3], "kristall"),
                k([x - 1.6, y + 1.6, z - 1.6], [x + 1.6, y + 2.6, z + 1.6], "sektglas")]
    for x in (3.5, 8, 12.5):
        for z in (3.5, 8, 12.5):
            t += schale(x, 0.5, z)
    for x in (5.75, 10.25):
        for z in (5.75, 10.25):
            t += schale(x, 3.1, z)
    t += schale(8, 5.7, 8)
    t += [k([7, 8.3, 7], [9, 13, 9], "flasche_gruen"), k([7.4, 13, 7.4], [8.6, 15, 8.6], "gold")]
    return t


def gamingstuhl(spalte):
    """Gaming-Stuhl (Blick nach Norden): Fünfstern-Fuß mit Rollen, Gaslift, Sitz mit roten Wangen, hohe
    Rückenlehne mit roten Streifen und Nackenkissen, Armlehnen. Steht immer einzeln."""
    t = [k([2, 1, 7.25], [14, 2, 8.75], "lack_schwarz"), k([7.25, 1, 2], [8.75, 2, 14], "lack_schwarz")]
    for x, z in ((2, 7.25), (12.5, 7.25), (7.25, 2), (7.25, 12.5)):
        t.append(k([x, 0, z], [x + 1.5, 1, z + 1.5], "lack_schwarz"))
    t += [k([7.25, 2, 7.25], [8.75, 6, 8.75], "chrom"),
          k([2.5, 6, 2], [13.5, 8, 13], "gaming_schwarz"),
          k([2, 6.5, 2], [3, 9, 13], "gaming_rot"), k([13, 6.5, 2], [14, 9, 13], "gaming_rot"),
          k([3, 8, 12.5], [13, 24, 14.5], "gaming_schwarz"),
          k([2.5, 9, 12], [3.5, 23, 14.5], "gaming_rot"), k([12.5, 9, 12], [13.5, 23, 14.5], "gaming_rot"),
          k([5, 20, 11.5], [11, 22.5, 12.5], "gaming_rot"), k([5, 9, 11.5], [11, 12, 12.5], "gaming_rot")]
    for x in (1, 13.5):
        t += [k([x + 0.5, 8, 7], [x + 1, 11, 8], "lack_schwarz"), k([x, 11, 4], [x + 1.5, 12, 10], "gaming_schwarz")]
    return t


def esszimmerstuhl(spalte):
    """Esszimmerstuhl (Blick nach Norden): weißes Lederpolster, hohe gepolsterte Lehne im Goldrahmen, Goldbeine.
    Steht immer einzeln; Rechtsklick setzt sich drauf."""
    t = [k([x, 0, z], [x + 1.5, 7, z + 1.5], "gold") for x, z in ((3, 3), (11.5, 3), (3, 11.5), (11.5, 11.5))]
    t += [k([2.5, 6.5, 2.5], [13.5, 7, 13.5], "gold"),                                   # Zarge
          k([2.5, 7, 2.5], [13.5, 9, 13.5], "leder_weiss"),                              # Sitzpolster
          k([3, 9, 12], [13, 21, 13.5], "leder_weiss"),                                  # Lehne
          k([2.5, 9, 11.5], [3, 21.5, 14], "gold"), k([13, 9, 11.5], [13.5, 21.5, 14], "gold"),
          k([2.5, 21, 11.5], [13.5, 21.5, 14], "gold")]
    return t


def gartenbank(spalte):
    """Gartenbank aus Teak (Sitzrichtung Norden): drei Sitzlatten, zwei Rückenlatten auf schwarzen Querstreben;
    an den Enden schwarze Metallwangen mit Armlehne (links = Westen aus Sicht des Sitzenden), in der Mitte Stützen."""
    t = [k([0, 6, 2], [16, 7, 5], "teak"), k([0, 6, 5.5], [16, 7, 8.5], "teak"), k([0, 6, 9], [16, 7, 12], "teak"),
         k([0, 8, 12.5], [16, 10.5, 13.5], "teak"), k([0, 11.5, 12.5], [16, 14, 13.5], "teak"),
         k([0, 5, 3], [16, 6, 4], "lack_schwarz"), k([0, 5, 10], [16, 6, 11], "lack_schwarz")]
    for ende, x in (("links", 0.5), ("rechts", 14)):
        if spalte in (ende, "einzeln"):
            t += [k([x, 0, 2.5], [x + 1.5, 9, 4], "lack_schwarz"),                     # Bein vorn bis zur Armlehne
                  k([x, 0, 11], [x + 1.5, 14, 12.5], "lack_schwarz"),                  # Bein hinten bis zur Lehne
                  k([x, 9, 2.5], [x + 1.5, 10, 12.5], "lack_schwarz")]                 # Armlehne
    if spalte == "mitte":
        t += [k([7.25, 0, 3], [8.75, 5, 4.5], "lack_schwarz"), k([7.25, 0, 10], [8.75, 5, 11.5], "lack_schwarz")]
    return t


def _wanne(kopf):
    """Freistehende Badewanne aus zwei Teilen (Blick nach Norden): Kopfteil hinten mit Lehne und Handtuch,
    Fußteil vorn mit goldenem Wasserhahn. Weiße Keramik, Goldrand, Goldfüße, Wasser bis knapp unter den Rand."""
    wasser_z = (0, 13.5) if kopf else (2.5, 16)
    t = [k([1, 1.5, 0], [15, 3, 16], "keramik"),
         k([1, 3, 0], [2.5, 10, 16], "keramik"), k([13.5, 3, 0], [15, 10, 16], "keramik"),
         k([0.75, 10, 0], [2.75, 10.8, 16], "gold"), k([13.25, 10, 0], [15.25, 10.8, 16], "gold"),
         k([2.5, 8, wasser_z[0]], [13.5, 8.5, wasser_z[1]], "badewasser")]
    if kopf:
        t += [k([1, 3, 13.5], [15, 10, 15], "keramik"), k([0.75, 10, 13.25], [15.25, 10.8, 15.25], "gold"),
              k([4, 8.5, 12], [12, 11.5, 13.5], "handtuch"),
              k([1.5, 0, 11.5], [3.5, 1.5, 13.5], "gold"), k([12.5, 0, 11.5], [14.5, 1.5, 13.5], "gold")]
    else:
        t += [k([1, 3, 1], [15, 10, 2.5], "keramik"), k([0.75, 10, 0.75], [15.25, 10.8, 2.75], "gold"),
              k([7, 10.8, 1], [9, 14, 2.5], "gold"), k([7.25, 13, 2.5], [8.75, 14, 5], "gold"),
              k([1.5, 0, 2.5], [3.5, 1.5, 4.5], "gold"), k([12.5, 0, 2.5], [14.5, 1.5, 4.5], "gold")]
    return t


def tanzstange():
    """Tanzstange aus Chrom mit Goldringen, lässt sich stapeln (Boden bis Decke)."""
    return [k([7, 0, 7], [9, 16, 9], "chrom"), k([6.75, 0, 6.75], [9.25, 0.5, 9.25], "gold"),
            k([6.75, 15.5, 6.75], [9.25, 16, 9.25], "gold")]


def flamingo():
    teile = ring(12, 15, 1, 15, 3, "pink")
    teile += [k([11, 15, 6], [13, 26, 8], "pink"), k([10, 24, 3], [14, 28, 7], "pink"),
              k([10.5, 24.5, 1], [13.5, 26.5, 3], "schnabel")]
    return teile


def neonschild(mat):
    return [k([0, 3, 15], [16, 13, 16], mat)]


def fluegel_tasten():
    """Vorderteil des Konzertflügels: Tastatur, Notenpult, Beine."""
    return [k([0, 6, 0], [16, 12, 16], "lack_schwarz"), k([0, 10, -3], [16, 11, 0], "lack_schwarz", up="fluegel_tasten"),
            k([1, 0, 1], [3, 6, 3], "lack_schwarz"), k([13, 0, 1], [15, 6, 3], "lack_schwarz"),
            k([2, 12, 6], [14, 17, 7], "lack_schwarz"), k([7, 0, 6], [9, 2, 10], "gold")]


def fluegel_korpus():
    """Hinterteil des Flügels: Korpus mit schräg geöffnetem Deckel."""
    return [k([0, 6, 0], [16, 12, 12], "lack_schwarz"), k([2, 6, 12], [14, 12, 15], "lack_schwarz"),
            k([7, 0, 9], [9, 6, 11], "lack_schwarz"),
            {"von": [0, 12, 0], "bis": [16, 13, 14], "mat": "lack_schwarz", "flaechen": {},
             "drehung": {"angle": 22.5, "axis": "x", "origin": [8, 12, 0]}},
            k([13, 12, 2], [14, 20, 3], "gold")]



def g(von, bis, mat, drehung=None, **flaechen):
    """Kasten für große Modelle über mehrere Blöcke (-16 … 32): Texturausschnitt nach Lage modulo 16, damit sich
    die Textur wiederholt statt gestreckt zu werden."""
    uv = {}
    for f in ("north", "south", "east", "west", "up", "down"):
        a, b2 = {"north": (0, 1), "south": (0, 1), "east": (2, 1), "west": (2, 1), "up": (0, 2), "down": (0, 2)}[f]
        u0, u1 = von[a] % 16, (von[a] % 16) + min(16, bis[a] - von[a])
        v0, v1 = von[b2] % 16, (von[b2] % 16) + min(16, bis[b2] - von[b2])
        if u1 > 16:
            u0, u1 = 0, min(16, bis[a] - von[a])
        if v1 > 16:
            v0, v1 = 0, min(16, bis[b2] - von[b2])
        uv[f] = [round(u0, 3), round(v0, 3), round(max(u1, u0 + 0.5), 3), round(max(v1, v0 + 0.5), 3)]
    return k(von, bis, mat, uv=uv, drehung=drehung, **flaechen)


def konzertfluegel():
    """Großer Konzertflügel über 3 × 3 Blöcke, alles im Modell des mittleren Blocks (x und z von -16 bis 32).
    Tastatur vorn (Norden), wer davor sitzt, schaut nach Süden; die lange gerade Seite liegt links vom Spieler
    (Osten), die geschwungene Seite rechts (Westen), das Ende zeigt nach Süden. Deckel offen auf einer Stütze,
    innen goldene Platte mit Saiten, Notenpult mit Noten, Pedale aus Gold, drei Beine mit goldenen Rollen."""
    lack, gold = "lack_schwarz", "gold"
    teile = []
    # Gehäuse (Zarge) in Stufen, die geschwungene Seite nach Westen
    zarge = [(-16, -6, 32, 8), (-14, 8, 32, 12), (-11, 12, 32, 16), (-7, 16, 32, 19), (-3, 19, 32, 22), (1, 22, 32, 25),
             (6, 25, 32, 27.5), (11, 27.5, 32, 29.5), (17, 29.5, 31.5, 31), (23, 31, 30.5, 32)]
    for x0, z0, x1, z1 in zarge:
        teile.append(g([x0, 10, z0], [x1, 18, z1], lack, up="fluegel_innen"))
    # Goldene Leiste rundherum oben auf der Zarge (vorn)
    teile.append(g([-16, 17, -6.2], [32, 18, -6], gold))
    # Deckel: eine Platte in denselben Stufen, an der geraden Ostseite angeschlagen und schräg geöffnet
    for x0, z0, x1, z1 in zarge:
        teile.append(g([x0, 18, z0], [x1, 19, z1], lack, drehung=gedreht("z", -22.5, [32, 18, 8])))
    # Deckelstütze auf der geschwungenen Seite
    teile.append(g([-3, 18, 13], [-2, 31, 14], gold))
    # Spieltisch: Tastenbett, Tasten (drei Oktaven je 14 Pixel), Wangen, Tastenklappe mit Goldstreifen
    teile.append(g([-13, 10, -15], [29, 13, -6], lack))
    for i in range(3):
        x0 = -13 + i * 14
        teile.append(k([x0, 13, -15], [x0 + 14, 14, -8], lack, uv={"up": [0, 14, 14, 0]}, up="fluegel_tasten"))
    teile.append(g([-16, 10, -16], [-13, 17, -6], lack))
    teile.append(g([29, 10, -16], [32, 17, -6], lack))
    teile.append(g([-13, 13, -8], [29, 17, -6], lack))
    teile.append(g([2, 15.5, -8.2], [14, 16.5, -8], gold))
    # Notenpult mit Notenblatt, leicht nach hinten geneigt
    teile.append(g([-4, 17, -7], [20, 25, -6], lack, drehung=gedreht("x", -22.5, [8, 17, -6])))
    teile.append(g([-2, 18, -7.3], [18, 24, -7], "noten", drehung=gedreht("x", -22.5, [8, 17, -6])))
    # Beine mit goldenen Rollen
    for x0, z0 in ((-14, -13), (26, -13), (22, 24)):
        teile.append(g([x0, 1, z0], [x0 + 4, 10, z0 + 4], lack))
        teile.append(g([x0 + 0.5, 0, z0 + 0.5], [x0 + 3.5, 1, z0 + 3.5], gold))
    # Pedale: Lyra unter der Tastatur, drei goldene Pedale
    teile.append(g([6, 1, -6], [10, 10, -4], lack))
    for x0 in (5.5, 7.5, 9.5):
        teile.append(g([x0, 1, -9], [x0 + 1, 1.6, -6], gold))
    return teile


def klavierbank():
    """Klavierbank: schwarzer Lack, gepolsterte Sitzfläche aus rotem Samt mit Goldknöpfen, 1,25 Blöcke breit."""
    return [k([-2, 8, 3], [18, 10, 13], "lack_schwarz"), k([-1.5, 10, 3.5], [17.5, 12, 12.5], "samt_knoepfe"),
            k([-1, 0, 4], [1, 8, 6], "lack_schwarz"), k([15, 0, 4], [17, 8, 6], "lack_schwarz"),
            k([-1, 0, 10], [1, 8, 12], "lack_schwarz"), k([15, 0, 10], [17, 8, 12], "lack_schwarz"),
            k([-2, 9.5, 2.8], [18, 10, 3], "gold")]


def tastatur():
    """Schreibunterlage aus Leder, flache Tastatur mit Ziffernblock und Maus (rechts aus Sicht des Sitzenden,
    der von Norden auf den Tisch schaut). Tastatur und Maus nutzen die feine Bürotextur."""
    return [k([0, 0, 2.5], [16, 0.3, 13.5], "schreibunterlage"),
            k([3.5, 0.3, 6], [15.5, 1.0, 10.5], "lack_schwarz", uv={"up": [0, 0, 12, 4.5]}, up="buero_hd"),
            k([0.8, 0.3, 7], [2.6, 1.1, 10], "lack_schwarz", uv={"up": [0, 6, 1.75, 9]}, up="buero_hd"),
            k([1.1, 1.1, 7.6], [2.3, 1.4, 9.6], "lack_schwarz")]


def sofa(spalte, polster="leder_weiss", kissen="leder_weiss", deko_kissen=None):
    """Sofastück (Sitzrichtung Norden): Polster bis zum Boden (kein Spalt, sonst wirkt es schwebend), Sitzkissen,
    Rückenlehne mit geneigtem Rückenkissen, Armlehne nur am Ende (links = Westen aus Sicht des Sitzenden)."""
    links, rechts = spalte in ("links", "einzeln"), spalte in ("rechts", "einzeln")
    x0, x1 = (3 if links else 0), (13 if rechts else 16)
    t = [k([0, 0, 1], [16, 5, 16], polster),
         k([x0 + 0.2, 5, 1.2], [x1 - 0.2, 8, 12.5], kissen),
         k([0, 5, 12.5], [16, 13, 16], polster),
         k([x0 + 0.4, 8, 10.5], [x1 - 0.4, 14, 12.5], kissen, drehung=gedreht("x", 22.5, [8, 8, 10.5]))]
    if links:
        t.append(k([0, 0, 1], [3, 11, 16], polster))
        if deko_kissen:
            t.append(k([3.2, 8, 7], [7.5, 12.5, 10], deko_kissen, drehung=gedreht("z", -22.5, [3.2, 8, 8])))
    if rechts:
        t.append(k([13, 0, 1], [16, 11, 16], polster))
        if deko_kissen:
            t.append(k([8.5, 8, 7], [12.8, 12.5, 10], deko_kissen, drehung=gedreht("z", 22.5, [12.8, 8, 8])))
    return t


def tafel(spalte, platte="marmor", beine="gold", zarge="gold"):
    """Tisch aus einem Stück (verbindet sich in der Reihe): volle Platte oben (bündig, damit Deko darauf nicht
    schwebt), schmale Zarge darunter, Beine nur an den Enden der Reihe."""
    t = [k([0, 14, 0], [16, 16, 16], platte), k([0, 12.5, 0.5], [16, 14, 1.5], zarge), k([0, 12.5, 14.5], [16, 14, 15.5], zarge)]
    if spalte in ("links", "einzeln"):
        t += [k([1, 0, 1], [3, 14, 3], beine), k([1, 0, 13], [3, 14, 15], beine), k([0.5, 12.5, 1.5], [1.5, 14, 14.5], zarge)]
    if spalte in ("rechts", "einzeln"):
        t += [k([13, 0, 1], [15, 14, 3], beine), k([13, 0, 13], [15, 14, 15], beine), k([14.5, 12.5, 1.5], [15.5, 14, 14.5], zarge)]
    return t


def _bett_rand(spalte, y0, y1, z0, z1, mat):
    """Seitlich herabhängende Decke bzw. Fell an den Außenkanten des Betts."""
    t = []
    if spalte in ("links", "einzeln"):
        t.append(k([-0.6, y0, z0], [0, y1, z1], mat))
    if spalte in ("rechts", "einzeln"):
        t.append(k([16, y0, z0], [16.6, y1, z1], mat))
    return t


def _bett_unterbau(spalte, z0, z1):
    """Schwarzer Lacksockel mit Goldkante und Matratze."""
    return [k([0, 0, z0], [16, 3, z1], "lack_schwarz"), k([0, 3, z0], [16, 3.5, z1], "gold"),
            k([0, 3.5, z0], [16, 9, z1], "matratze")] + _bett_rand(spalte, 3.6, 10.5, z0, z1, "satin_schwarz")


def bett_kopf(spalte):
    """Kopfteil des XXL-Betts (Kopf im Norden): Kissen, Zierkissen, umgeschlagenes Laken, Decke."""
    zier = "samt_rot" if spalte != "mitte" else "satin_gold"
    return _bett_unterbau(spalte, 0, 16) + [
        k([0, 9, 0], [16, 9.3, 8], "laken"),
        k([0.8, 9.3, 0.6], [15.2, 13.5, 6.8], "kissen_weiss"),
        k([2.5, 9.6, 5.6], [13.5, 15, 8], zier, drehung=gedreht("x", -22.5, [8, 9.6, 8])),
        k([0, 9, 8], [16, 10.5, 16], "satin_schwarz"),
        k([0, 10.5, 8], [16, 11, 10], "laken")]


def bett_fuss(spalte):
    """Mittelstück des XXL-Betts: Decke über die ganze Länge."""
    return _bett_unterbau(spalte, 0, 16) + [k([0, 9, 0], [16, 10.5, 16], "satin_schwarz")]


def bett_ende(spalte):
    """Fußende des XXL-Betts (Deko, nicht zum Schlafen): Decke fällt vorn herab, weißes Fell quer darüber."""
    return _bett_unterbau(spalte, 0, 15.4) + [
        k([0, 9, 0], [16, 10.5, 16], "satin_schwarz"), k([0, 3.6, 15.4], [16, 10.5, 16], "satin_schwarz"),
        k([0, 10.5, 3], [16, 11.6, 10.5], "fell")] + _bett_rand(spalte, 6, 11.6, 3, 10.5, "fell")


def bettbank(spalte):
    """Gepolsterte Bank am Bettende (Sitzrichtung Norden): Samt mit Goldknöpfen, Goldbeine an den Enden."""
    t = [k([0, 5, 3], [16, 9, 13], "samt_rot", up="samt_knoepfe"), k([0, 4, 3], [16, 5, 13], "gold")]
    if spalte in ("links", "einzeln"):
        t += [k([0.5, 0, 3.5], [2, 4, 5], "gold"), k([0.5, 0, 11], [2, 4, 12.5], "gold")]
    if spalte in ("rechts", "einzeln"):
        t += [k([14, 0, 3.5], [15.5, 4, 5], "gold"), k([14, 0, 11], [15.5, 4, 12.5], "gold")]
    return t


def nachttisch():
    return [k([1, 0, 1], [15, 13, 15], "lack_schwarz", north="nachttisch_front"), k([0.5, 13, 0.5], [15.5, 14, 15.5], "marmor")]


def fernbedienung():
    """Fernbedienung, flach liegend (4 × 10 Pixel, 1 hoch), die Ein-Taste zeigt nach Norden."""
    return [k([6, 0, 3], [10, 1, 13], "lack_schwarz", uv={"up": [0, 0, 4, 10]}, up="fernbedienung_hd")]


def tischlampe():
    return [k([5.5, 0, 5.5], [10.5, 1, 10.5], "gold"), k([7.5, 1, 7.5], [8.5, 8, 8.5], "gold"),
            k([3, 8, 4], [13, 15, 12], "lampenschirm"), k([4, 8, 3], [12, 15, 13], "lampenschirm"),
            k([7, 15, 7], [9, 15.5, 9], "gold")]


def kinotisch():
    """Goldener Beistelltisch am Kinosofa mit Popcorn-Tüte und zwei Drinks."""
    return [k([6, 0, 6], [10, 0.5, 10], "gold"), k([7.5, 0.5, 7.5], [8.5, 9, 8.5], "gold"),
            k([2, 9, 3], [14, 10, 13], "marmor"), k([3, 9, 2], [13, 10, 14], "marmor"),
            k([4, 10, 5], [9, 15, 10], "popcorn_tuete", up="popcorn"), k([4.5, 15, 5.5], [8.5, 16, 9.5], "popcorn"),
            k([10.5, 10, 4.5], [12.5, 13, 6.5], "cocktail"), k([10.5, 10, 9.5], [12.5, 14, 11.5], "champagner")]


def loungetisch():
    """Goldener Beistelltisch für die Liegen: Cocktail und Champagner im Eiskübel."""
    return [k([6, 0, 6], [10, 0.5, 10], "gold"), k([7.5, 0.5, 7.5], [8.5, 9, 8.5], "gold"),
            k([2, 9, 3], [14, 10, 13], "marmor"), k([3, 9, 2], [13, 10, 14], "marmor"),
            k([4, 10, 4], [6, 13, 6], "cocktail"), k([4, 13, 4], [6, 13.5, 6], "pink"),
            k([8.5, 10, 7.5], [12.5, 14, 11.5], "chrom"), k([9.8, 13, 8.8], [11.2, 18, 10.2], "champagner")]


def _liege_gestell(beine_z):
    t = [k([1.5, 3, 0], [3, 5, 16], "teak"), k([13, 3, 0], [14.5, 5, 16], "teak"), k([3, 3.6, 0], [13, 4.4, 16], "teak")]
    for z in beine_z:
        t += [k([1.5, 0, z], [3, 3, z + 1.5], "teak"), k([13, 0, z], [14.5, 3, z + 1.5], "teak")]
    return t


def liege_kopf():
    """Kopfteil der Strandliege (Blick nach Norden zum Fußteil): Teakgestell, hochgestellte Rückenlehne
    mit dickem Polster, Nackenrolle aus Handtuch."""
    lehne = gedreht("x", -45, [8, 4.5, 6])
    return _liege_gestell((13,)) + [
        k([2, 4.4, 0], [14, 7, 6.2], "polster_weiss"),
        k([3, 3.4, 6], [13, 4.6, 15.5], "teak", drehung=lehne),
        k([2, 4.6, 6], [14, 7, 15.5], "polster_weiss", drehung=lehne),
        k([3, 7, 12.5], [13, 8.6, 14.5], "handtuch", drehung=lehne)]


def liege_fuss():
    """Fußteil der Strandliege mit gefaltetem Handtuch."""
    return _liege_gestell((1.5,)) + [k([2, 4.4, 0.5], [14, 7, 16], "polster_weiss"),
                                     k([4, 7, 2], [12, 7.8, 7.5], "handtuch")]


def sonnenschirm():
    """Großer Sonnenschirm (Schirm 2,7 Blöcke breit, ab 1,75 Blöcken Höhe) auf Chromfuß."""
    return [k([4.5, 0, 4.5], [11.5, 1, 11.5], "chrom"), k([7.5, 1, 7.5], [8.5, 31, 8.5], "gold"),
            k([-13, 28, -5], [29, 29, 21], "schirm"), k([-5, 28, -13], [21, 29, 29], "schirm"),
            k([-9, 28, -9], [25, 29, 25], "schirm", drehung=gedreht("y", 45, [8, 28, 8])),
            k([-7, 29, -7], [23, 30, 23], "schirm_glatt"), k([0, 30, 0], [16, 31, 16], "schirm_glatt"),
            k([5, 31, 5], [11, 31.5, 11], "schirm_glatt"), k([7, 31.5, 7], [9, 32, 9], "gold")]


def duschkopf(wand: bool, an: bool):
    """Regen-Duschkopf (Vorderseite Norden): große quadratische Chrom-Brause (14 × 14) mit Goldrand und Düsen auf der
    Unterseite. Deckenrohr senkrecht nach oben oder Wandrohr waagerecht nach Süden (zur Wand hinter der Rückseite).
    Läuft das Wasser, hängt unter der Brause ein Vorhang aus Wasserfäden (der Rest sind Tropfen aus Dusche.java)."""
    t = [k([1, 10, 1], [15, 11, 15], "chrom", down="duesen_an" if an else "duesen")]
    t += ring(9.6, 11.4, 0.6, 15.4, 0.6, "gold")
    t.append(k([6, 11, 6], [10, 12, 10], "gold"))                                   # Gelenk über der Brause
    if wand:
        t += [k([7.25, 12, 7.25], [8.75, 13, 8.75], "chrom"),                       # kurzes Stück nach oben
              k([7, 12.75, 7], [9, 14.75, 9], "gold"),                              # Winkel
              k([7.25, 13, 9], [8.75, 14.5, 15.5], "chrom"),                        # Wandrohr
              k([5.5, 11.5, 15.5], [10.5, 16, 16], "gold")]                         # Rosette an der Wand
    else:
        t += [k([7.25, 12, 7.25], [8.75, 15.5, 8.75], "chrom"),                     # Deckenrohr
              k([5.5, 15.5, 5.5], [10.5, 16, 10.5], "gold")]                        # Rosette an der Decke
    if an:
        strahl = k([2, 0, 2], [14, 9.9, 14], "duschstrahl")
        strahl["ohne"] = ("up", "down")
        t.append(strahl)
    return t


def duscharmatur(an: bool):
    """Duscharmatur (Vorderseite Norden, Wand im Süden): runde Chrom-Rosette mit Skala, goldener Drehgriff.
    Aus: Griff senkrecht; an: um 45° gedreht."""
    griff_drehung = gedreht("z", 45, [8, 8, 13]) if an else None
    return [k([4, 4, 15], [12, 12, 16], "chrom", uv={"north": [0, 0, 16, 16]}, north="armatur"),
            k([7, 7, 13.5], [9, 9, 15], "gold"),
            k([7.25, 3.5, 12.5], [8.75, 12.5, 13.5], "gold", drehung=griff_drehung),
            k([6.5, 6.5, 12], [9.5, 9.5, 13.5], "gold")]


# Name: (Teile, Kollisionsform oder None = aus den Teilen, Licht, fest, Klang, Werkzeug, Deutsch, Englisch)
BLOECKE = {
    "tanzflaeche": ([k([0, 0, 0], [16, 16, 16], "schwarzglas", up="tanz", down="lack_weiss")], None, 12, True, "glas", "pickaxe", "Tanzfläche", "Dance Floor"),
    "discokugel": ([k([7.5, 11, 7.5], [8.5, 16, 8.5], "chrom"), k([3, 3, 5], [13, 9, 11], "spiegel"),
                    k([5, 1, 5], [11, 11, 11], "spiegel"), k([5, 3, 3], [11, 9, 13], "spiegel"),
                    k([4, 2, 4], [12, 10, 12], "spiegel")], [[3, 1, 3, 13, 16, 13]], 10, False, "glas", "pickaxe", "Discokugel", "Disco Ball"),
    "kronleuchter": (kronleuchter(), [[2, 2, 2, 14, 16, 14]], 15, False, "kette", "pickaxe", "Kronleuchter", "Chandelier"),
    "flaschenregal": ([k([0, 0, 0], [16, 16, 16], "holz_dunkel", north="flaschen")], None, 0, True, "holz", "axe", "Flaschenregal", "Bottle Shelf"),
    "goldbarren": (goldbarren(), None, 0, True, "metall", "pickaxe", "Goldbarren", "Gold Bars"),
    "geldstapel": (geldstapel(), None, 0, True, "wolle", "axe", "Geldstapel", "Stacks of Cash"),
    "pokal": (pokal(), None, 0, True, "metall", "pickaxe", "Goldpokal", "Golden Trophy"),
    "kaffeemaschine": (kaffeemaschine(), None, 0, True, "metall", "pickaxe", "Kaffeemaschine", "Espresso Machine"),
    "herd": ([k([0, 0, 0], [16, 16, 16], "lack_weiss", north="ofen_front", up="herd_oben")], None, 0, True, "metall", "pickaxe", "Herd", "Stove"),
    "spuele": (spuele(), [[0, 0, 0, 16, 16, 16]], 0, True, "stein", "pickaxe", "Spüle", "Sink"),
    "kuehlschrank": ([k([0, 0, 0], [16, 16, 16], "chrom", north="kuehl_front")], None, 0, True, "metall", "pickaxe", "Kühlschrank", "Fridge"),
    "weinkuehlschrank": ([k([0, 0, 0], [16, 16, 16], "schwarzglas", north="weinkuehl_front")], None, 4, True, "glas", "pickaxe", "Weinkühlschrank", "Wine Cooler"),
    "dunstabzug": (dunstabzug(), None, 8, True, "metall", "pickaxe", "Dunstabzug", "Range Hood"),
    "goldtoilette": (goldtoilette(), None, 0, True, "metall", "pickaxe", "Goldene Toilette", "Golden Toilet"),
    "barhocker": (barhocker(), None, 0, True, "metall", "pickaxe", "Barhocker", "Bar Stool"),
    "cocktail": (cocktail(), [[6, 0, 6, 10, 10, 10]], 0, False, "glas", "pickaxe", "Cocktail", "Cocktail"),
    "champagner": (champagner(), None, 0, True, "glas", "pickaxe", "Champagner im Eiskübel", "Champagne on Ice"),
    "sektturm": (sektturm(), None, 0, True, "glas", "pickaxe", "Sektturm", "Champagne Tower"),
    "flamingo": (flamingo(), [[1, 12, 1, 15, 15, 15]], 0, False, "wolle", "axe", "Flamingo-Schwimmring", "Flamingo Float"),
    "neon_bar": (neonschild("neon_bar"), None, 10, False, "glas", "pickaxe", "Leuchtschrift BAR", "Neon Sign BAR"),
    "neon_vip": (neonschild("neon_vip"), None, 10, False, "glas", "pickaxe", "Leuchtschrift VIP", "Neon Sign VIP"),
    # VIP über einem zwei Blöcke breiten Durchgang: Schild um einen halben Block nach Osten versetzt (sitzt dann mittig)
    "neon_vip_mitte": ([k([8, 3, 15], [24, 13, 16], "neon_vip", uv={"north": [0, 3, 16, 13], "south": [0, 3, 16, 13],
                                                                       "up": [0, 15, 16, 16], "down": [0, 15, 16, 16]})],
                       [[8, 3, 15, 16, 13, 16]], 10, False, "glas", "pickaxe", "Leuchtschrift VIP (mittig)", "Neon Sign VIP (centred)"),
    "neon_club": (neonschild("neon_club"), None, 10, False, "glas", "pickaxe", "Leuchtschrift CLUB", "Neon Sign CLUB"),
    "neon_wein": (neonschild("neon_wein"), None, 10, False, "glas", "pickaxe", "Leuchtschrift WEIN", "Neon Sign WINE"),
    "notausgang_links": ([k([0, 3, 15], [16, 13, 16], "notausgang_gruen", north="notausgang_mann")], None, 8, False, "glas",
                         "pickaxe", "Notausgang-Schild (links)", "Emergency Exit Sign (left)"),
    "notausgang_rechts": ([k([0, 3, 15], [16, 13, 16], "notausgang_gruen", north="notausgang_tuer")], None, 8, False, "glas",
                          "pickaxe", "Notausgang-Schild (rechts)", "Emergency Exit Sign (right)"),
    "kopfteil": ([k([0, 0, 0], [16, 16, 16], "samt_rot", north="samt_knoepfe")], None, 0, True, "wolle", "axe", "Gepolstertes Kopfteil", "Tufted Headboard"),
    "tastatur": (tastatur(), [[0, 0, 2.5, 16, 1.4, 13.5]], 0, False, "stein", "pickaxe", "Tastatur, Maus und Schreibunterlage", "Keyboard, Mouse and Desk Mat"),
    "nachttisch": (nachttisch(), [[1, 0, 1, 15, 14, 15]], 0, True, "holz", "axe", "Nachttisch", "Nightstand"),
    "tischlampe": (tischlampe(), [[3, 0, 3, 13, 15.5, 13]], 14, True, "glas", "pickaxe", "Tischlampe", "Table Lamp"),
    "fernbedienung": (fernbedienung(), [[6, 0, 3, 10, 1, 13]], 0, False, "stein", "pickaxe", "Fernbedienung", "TV Remote"),
    "kinotisch": (kinotisch(), [[2, 0, 2, 14, 10, 14]], 0, True, "metall", "pickaxe", "Kinotisch mit Popcorn", "Movie Side Table"),
    "loungetisch": (loungetisch(), [[2, 0, 2, 14, 10, 14]], 0, True, "metall", "pickaxe", "Loungetisch", "Lounge Side Table"),
    "liege_kopf": (liege_kopf(), [[1.5, 0, 0, 14.5, 7, 16]], 0, True, "holz", "axe", "Strandliege (Kopfteil)", "Sun Lounger (Head)"),
    "liege_fuss": (liege_fuss(), [[1.5, 0, 0, 14.5, 7, 16]], 0, True, "holz", "axe", "Strandliege (Fußteil)", "Sun Lounger (Foot)"),
    "sonnenschirm": (sonnenschirm(), [[7, 0, 7, 9, 16, 9]], 0, True, "wolle", "axe", "Sonnenschirm", "Parasol"),
    "fluegel_tasten": (fluegel_tasten(), [[0, 0, 0, 16, 12, 16]], 0, True, "holz", "axe", "Konzertflügel (Tasten)", "Grand Piano (Keys)"),
    "fluegel_korpus": (fluegel_korpus(), [[0, 0, 0, 16, 13, 15]], 0, True, "holz", "axe", "Konzertflügel (Korpus)", "Grand Piano (Body)"),
    "neon_show": (neonschild("neon_show"), None, 10, False, "glas", "pickaxe", "Leuchtschrift SHOW", "Neon Sign SHOW"),
    "wanne_kopf": (_wanne(True), [[1, 0, 0, 15, 10, 16]], 0, True, "stein", "pickaxe", "Badewanne (Kopfteil)", "Bathtub (Head)"),
    "wanne_fuss": (_wanne(False), [[1, 0, 0, 15, 10, 16]], 0, True, "stein", "pickaxe", "Badewanne (Fußteil)", "Bathtub (Foot)"),
    "konzertfluegel": (konzertfluegel(), [[0, 0, 0, 16, 16, 16]], 0, True, "holz", "axe", "Konzertflügel", "Grand Piano"),
    "fluegel_teil": ([], [[0, 0, 0, 16, 16, 16]], 0, True, "holz", "axe", "Konzertflügel (Teil)", "Grand Piano (Part)"),
    "klavierbank": (klavierbank(), [[0, 0, 3, 16, 12, 13]], 0, True, "holz", "axe", "Klavierbank", "Piano Bench"),
    "tanzstange": (tanzstange(), [[7, 0, 7, 9, 16, 9]], 0, True, "metall", "pickaxe", "Tanzstange", "Dance Pole"),
    "goldmuenzen": ([k([0, 0, 0], [16, 16, 16], "goldmuenzen")], None, 0, False, "metall", "pickaxe",
                    "Goldmünzen (zum Hineinspringen)", "Gold Coins (jump in)"),
}

# Sitzhöhe in Pixeln für Sitzmöbel (Rechtsklick setzt sich drauf)
SITZE = {"liege_kopf": 7, "wanne_kopf": 4, "klavierbank": 11, "barhocker": 11}
# Mit Schublade zum Hineinlegen (NachttischBlock, 9 Plätze)
SCHUBLADE = {"nachttisch"}
# Liegt auf Möbeln: bis zu so vielen Pixeln tiefer (FernbedienungBlock)
TIEFER = {"fernbedienung": 8}
# Eigenes Modell für die Hand (Philip: „richtig in der Hand halten“): das Teil in die Mitte gelegt und eigene Lagen für
# Ich-Sicht (der Arm wird dazu gezeichnet, siehe HandMitFernbedienungMixin), dritte Person, Inventar, Boden und Rahmen.
HAND = {"fernbedienung": {
    "firstperson_righthand": {"rotation": [-10, 8, 0], "translation": [-1.5, 2.5, -4.5], "scale": [0.6, 0.6, 0.6]},
    "firstperson_lefthand": {"rotation": [-10, -8, 0], "translation": [-1.5, 2.5, -4.5], "scale": [0.6, 0.6, 0.6]},
    "thirdperson_righthand": {"rotation": [90, 0, 0], "translation": [0, -1.5, 0.5], "scale": [0.6, 0.6, 0.6]},
    "thirdperson_lefthand": {"rotation": [90, 0, 0], "translation": [0, -1.5, 0.5], "scale": [0.6, 0.6, 0.6]},
    "gui": {"rotation": [90, 0, 45], "translation": [0, 0, 0], "scale": [1.2, 1.2, 1.2]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "fixed": {"rotation": [90, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
    "head": {"rotation": [0, 0, 0], "translation": [0, 13, 0], "scale": [1, 1, 1]}}}


def hand_modell(teile, lagen) -> dict:
    """Item-Modell: Teile mittig (Mitte der Höhe auf 8) und mit eigener Lage in Hand, Inventar und Rahmen."""
    unten = min(t["von"][1] for t in teile)
    oben = max(t["bis"][1] for t in teile)
    dy = 8 - (unten + oben) / 2
    mitte = [dict(t, von=[t["von"][0], t["von"][1] + dy, t["von"][2]], bis=[t["bis"][0], t["bis"][1] + dy, t["bis"][2]]) for t in teile]
    m = modell(mitte)
    m["display"] = lagen
    return m
# Rechtsklick öffnet das Spielfenster des Flügels (KlavierBlock)
KLAVIER = {"konzertfluegel", "fluegel_teil"}
# Blöcke mit eigener Funktion beim Rechtsklick (siehe ModDeko): Toilette spült
SPUELEN = {"goldtoilette"}

# Möbel, die sich mit Nachbarn verbinden: Name → (Teile je Spalte, Form, Licht, fest, Klang, Werkzeug,
# Deutsch, Englisch, Sitzhöhe oder None)
SPALTEN = ("links", "mitte", "rechts", "einzeln")
VERBUND = {
    "sofa_weiss": (lambda sp: sofa(sp), [[0, 0, 1, 16, 8, 16], [0, 8, 12, 16, 13, 16]], 0, True, "wolle", "axe",
                   "Ledersofa (weiß)", "Leather Sofa (White)", 8),
    "sofa_samt": (lambda sp: sofa(sp, "samt_rot", "samt_rot", "satin_gold"), [[0, 0, 1, 16, 8, 16], [0, 8, 12, 16, 13, 16]], 0, True,
                  "wolle", "axe", "Samtsofa (rot)", "Velvet Sofa (Red)", 8),
    "sofa_schwarz": (lambda sp: sofa(sp, "samt_schwarz", "samt_schwarz", "samt_rot"), [[0, 0, 1, 16, 8, 16], [0, 8, 12, 16, 13, 16]], 0, True,
                     "wolle", "axe", "Samtsofa (schwarz)", "Velvet Sofa (Black)", 8),
    "bett_ende": (bett_ende, [[0, 0, 0, 16, 11, 16]], 0, True, "wolle", "axe", "Kingsize-Bett (Fußende)", "King-Size Bed (Foot End)", None),
    "bettbank": (bettbank, [[0, 0, 3, 16, 9, 13]], 0, True, "wolle", "axe", "Bettbank", "Bed Bench", 9),
    "tafel_marmor": (lambda sp: tafel(sp), [[0, 14, 0, 16, 16, 16]], 0, True, "stein", "pickaxe",
                     "Marmortafel", "Marble Table", None),
    "tafel_holz": (lambda sp: tafel(sp, "teak", "lack_schwarz", "lack_schwarz"), [[0, 14, 0, 16, 16, 16]], 0, True, "holz", "axe",
                   "Holztafel", "Wooden Table", None),
    "gamingstuhl": (gamingstuhl, [[2, 0, 2, 14, 8, 14], [3, 8, 12, 13, 16, 15]], 0, True, "wolle", "axe",
                    "Gaming-Stuhl", "Gaming Chair", 8),
    "esszimmerstuhl": (esszimmerstuhl, [[2.5, 0, 2.5, 13.5, 9, 13.5], [2.5, 9, 11.5, 13.5, 16, 14]], 0, True, "wolle", "axe",
                       "Esszimmerstuhl", "Dining Chair", 9),
    "gartenbank": (gartenbank, [[0, 0, 2, 16, 7, 13], [0, 7, 12.5, 16, 14, 13.5]], 0, True, "holz", "axe",
                   "Gartenbank", "Garden Bench", 7),
}

FLAECHEN = ("north", "south", "east", "west", "up", "down")


def tex(mat: str) -> str:
    return f"{NS}:block/deko/{mat}"


def uv_fuer(flaeche: str, von, bis):
    """Ausschnitt aus der Materialtextur wie bei Minecraft-Würfeln (auf 0…16 begrenzt)."""
    x0, y0, z0 = von
    x1, y1, z1 = bis
    w = {"north": (16 - x1, 16 - y1, 16 - x0, 16 - y0), "south": (x0, 16 - y1, x1, 16 - y0),
         "west": (z0, 16 - y1, z1, 16 - y0), "east": (16 - z1, 16 - y1, 16 - z0, 16 - y0),
         "up": (x0, z0, x1, z1), "down": (x0, 16 - z1, x1, 16 - z0)}[flaeche]
    return [max(0.0, min(16.0, round(v, 3))) for v in w]


def modell(teile) -> dict:
    mats = sorted({t["mat"] for t in teile} | {m for t in teile for m in t["flaechen"].values()})
    elemente = []
    for t in teile:
        e = {"from": t["von"], "to": t["bis"], "faces": {}}
        for f in FLAECHEN:
            if f in t.get("ohne", ()):
                continue
            mat = t["flaechen"].get(f, t["mat"])
            uv = list(t["uv"][f]) if f in t.get("uv", {}) else uv_fuer(f, t["von"], t["bis"])
            if uv[0] == uv[2] or uv[1] == uv[3]:
                uv = [0, 0, 16, 16] if f in ("up", "down") else uv
                if uv[0] == uv[2]:
                    uv[2] = min(16.0, uv[0] + 1)
                if uv[1] == uv[3]:
                    uv[3] = min(16.0, uv[1] + 1)
            e["faces"][f] = {"uv": uv, "texture": f"#{mat}"}
        if "drehung" in t:
            e["rotation"] = t["drehung"]
        elemente.append(e)
    texturen = {m: tex(m) for m in mats}
    texturen["particle"] = tex(teile[0]["flaechen"].get("north", teile[0]["mat"])) if teile else tex("lack_schwarz")
    return {"parent": "minecraft:block/block", "textures": texturen, "elements": elemente}


def form_aus_teilen(teile):
    kaesten = []
    for t in teile:
        if "drehung" in t:
            continue
        x0, y0, z0 = (max(0.0, min(16.0, v)) for v in t["von"])
        x1, y1, z1 = (max(0.0, min(16.0, v)) for v in t["bis"])
        if x1 > x0 and y1 > y0 and z1 > z0:
            kaesten.append([x0, y0, z0, x1, y1, z1])
    return kaesten


def schreibe_json(pfad: Path, daten) -> None:
    pfad.parent.mkdir(parents=True, exist_ok=True)
    pfad.write_text(json.dumps(daten, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def kingsize_bett(de: dict, en: dict) -> None:
    """XXL-Kingsize-Bett (KingsizeBettBlock): Modelle je Teil und Spalte, Blockzustände, Beute nur vom Kopfteil."""
    varianten = {}
    for teil, fabrik in (("head", bett_kopf), ("foot", bett_fuss)):
        for sp in SPALTEN:
            schreibe_json(ASSETS / "models/block/deko" / f"bett_{teil}_{sp}.json", modell(fabrik(sp)))
            for richtung, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
                varianten[f"facing={richtung},part={teil},spalte={sp}"] = {"model": f"{NS}:block/deko/bett_{teil}_{sp}", **({"y": y} if y else {})}
    schreibe_json(ASSETS / "blockstates/kingsize_bett.json", {"variants": varianten})
    schreibe_json(ASSETS / "models/item/kingsize_bett.json", {"parent": f"{NS}:block/deko/bett_head_einzeln"})
    schreibe_json(ASSETS / "items/kingsize_bett.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/kingsize_bett"}})
    schreibe_json(RES / f"data/{NS}/loot_table/blocks/kingsize_bett.json", {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{NS}:kingsize_bett",
                                            "conditions": [{"condition": "minecraft:block_state_property", "block": f"{NS}:kingsize_bett",
                                                            "properties": {"part": "head"}}]}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    de[f"block.{NS}.kingsize_bett"], en[f"block.{NS}.kingsize_bett"] = "XXL-Kingsize-Bett", "XXL King-Size Bed"
    de[f"container.{NS}.schublade"], en[f"container.{NS}.schublade"] = "Schublade", "Drawer"


def main() -> dict:
    TEX.mkdir(parents=True, exist_ok=True)
    for alt in TEX.glob("*"):
        alt.unlink()
    for name, fabrik in MATERIALIEN.items():
        fabrik().save(TEX / f"{name}.png")
    for name, (fabrik, takt) in ANIMIERT.items():
        bilder = fabrik()
        streifen = Image.new("RGBA", (16, 16 * len(bilder)))
        for i, b in enumerate(bilder):
            streifen.paste(b, (0, 16 * i))
        streifen.save(TEX / f"{name}.png")
        schreibe_json(TEX / f"{name}.png.mcmeta", {"animation": {"frametime": takt}})

    liste = []
    werkzeuge = {"pickaxe": [], "axe": []}
    de, en = {}, {}
    for name, (teile, form, licht, fest, klang, werkzeug, name_de, name_en) in BLOECKE.items():
        schreibe_json(ASSETS / "models/block/deko" / f"{name}.json", modell(teile))
        modell_id = f"{NS}:block/deko/{name}"
        varianten = {}
        if name in TIEFER:
            # Zustand „tiefe“ = so viele Pixel tiefer (FernbedienungBlock wählt ihn nach der Oberkante darunter)
            for tiefe in range(TIEFER[name] + 1):
                tiefer = [dict(t, von=[t["von"][0], t["von"][1] - tiefe, t["von"][2]], bis=[t["bis"][0], t["bis"][1] - tiefe, t["bis"][2]])
                          for t in teile]
                schreibe_json(ASSETS / "models/block/deko" / f"{name}_{tiefe}.json", modell(tiefer))
                for richtung, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
                    varianten[f"facing={richtung},tiefe={tiefe}"] = {"model": f"{modell_id}_{tiefe}", **({"y": y} if y else {})}
        else:
            for richtung, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
                varianten[f"facing={richtung}"] = {"model": modell_id, **({"y": y} if y else {})}
        schreibe_json(ASSETS / "blockstates" / f"{name}.json", {"variants": varianten})
        if name in HAND:
            schreibe_json(ASSETS / "models/item" / f"{name}.json", hand_modell(teile, HAND[name]))
            schreibe_json(ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}})
        else:
            schreibe_json(ASSETS / "models/item" / f"{name}.json", {"parent": modell_id})
            schreibe_json(ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": modell_id}})
        schreibe_json(RES / f"data/{NS}/loot_table/blocks/{name}.json", {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
        eintrag = {"name": name, "form": form or form_aus_teilen(teile), "licht": licht, "fest": fest, "klang": klang}
        if name in SITZE:
            eintrag["sitz"] = SITZE[name]
        if name in SPUELEN:
            eintrag["spuelen"] = True
        if name in KLAVIER:
            eintrag["klavier"] = True
        if name in TIEFER:
            eintrag["fernbedienung"] = True
        if name in SCHUBLADE:
            eintrag["schublade"] = True
        liste.append(eintrag)
        werkzeuge[werkzeug].append(f"{NS}:{name}")
        de[f"block.{NS}.{name}"], en[f"block.{NS}.{name}"] = name_de, name_en
    for name, (fabrik, form, licht, fest, klang, werkzeug, name_de, name_en, sitz) in VERBUND.items():
        varianten = {}
        for sp in SPALTEN:
            schreibe_json(ASSETS / "models/block/deko" / f"{name}_{sp}.json", modell(fabrik(sp)))
            for richtung, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
                varianten[f"facing={richtung},spalte={sp}"] = {"model": f"{NS}:block/deko/{name}_{sp}", **({"y": y} if y else {})}
        schreibe_json(ASSETS / "blockstates" / f"{name}.json", {"variants": varianten})
        modell_id = f"{NS}:block/deko/{name}_einzeln"
        schreibe_json(ASSETS / "models/item" / f"{name}.json", {"parent": modell_id})
        schreibe_json(ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": modell_id}})
        schreibe_json(RES / f"data/{NS}/loot_table/blocks/{name}.json", {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
        eintrag = {"name": name, "form": form, "licht": licht, "fest": fest, "klang": klang, "verbund": True}
        if sitz is not None:
            eintrag["sitz"] = sitz
        liste.append(eintrag)
        werkzeuge[werkzeug].append(f"{NS}:{name}")
        de[f"block.{NS}.{name}"], en[f"block.{NS}.{name}"] = name_de, name_en
    kingsize_bett(de, en)
    schreibe_json(RES / "deko_bloecke.json", liste)
    print(f"{len(BLOECKE) + len(VERBUND)} Deko-Blöcke, {len(MATERIALIEN) + len(ANIMIERT)} Materialien geschrieben")
    return {"werkzeuge": werkzeuge, "de": de, "en": en}


if __name__ == "__main__":
    main()
