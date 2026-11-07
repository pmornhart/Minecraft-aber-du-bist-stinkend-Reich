#!/usr/bin/env python3
"""Erzeugt alle Grafiken und Datendateien der Mechanik-Blöcke (eigene Assets, nichts kopiert).

Schreibt nach src/main/resources:
  assets/stinkendreich/textures/block|item/*.png   selbst gezeichnete 16x16-Texturen
  assets/stinkendreich/models/block|item/*.json    Blockmodelle, Item-Modelle (bis 1.21.3)
  assets/stinkendreich/items/*.json                Item-Definitionen (ab 1.21.4)
  assets/stinkendreich/blockstates/*.json          Zustände (Richtung, an/aus, offen/zu)
  data/stinkendreich/loot_table/blocks/*.json      Blöcke lassen sich selbst fallen
  data/minecraft/tags/block/mineable/*.json        richtiges Werkzeug

Aufruf: python3 tools/assets/assets.py
"""
from __future__ import annotations

import json
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw

import ausruestung
import taenzerin
import handy
import hai
import deko
import drehteller
import fahrzeuge
import fernseher
import gemaelde
import tueren

WURZEL = Path(__file__).resolve().parents[2]
RES = WURZEL / "src/main/resources"
ASSETS = RES / "assets/stinkendreich"
NS = "stinkendreich"

# ---------------------------------------------------------------------------
# Texturen
# ---------------------------------------------------------------------------


def neu(farbe=(40, 42, 46)) -> tuple[Image.Image, ImageDraw.ImageDraw]:
    img = Image.new("RGBA", (16, 16), farbe + (255,))
    return img, ImageDraw.Draw(img)


def rauschen(img: Image.Image, staerke: int = 6, saat: int = 1) -> Image.Image:
    rnd = random.Random(saat)
    px = img.load()
    for x in range(16):
        for y in range(16):
            r, g, b, a = px[x, y]
            d = rnd.randint(-staerke, staerke)
            px[x, y] = (max(0, min(255, r + d)), max(0, min(255, g + d)), max(0, min(255, b + d)), a)
    return img


def rahmen(d: ImageDraw.ImageDraw, hell=(78, 82, 88), dunkel=(22, 23, 26)) -> None:
    d.line([(0, 0), (15, 0)], fill=hell)
    d.line([(0, 0), (0, 15)], fill=hell)
    d.line([(0, 15), (15, 15)], fill=dunkel)
    d.line([(15, 0), (15, 15)], fill=dunkel)


def metall(saat=1, farbe=(52, 55, 60)):
    img, d = neu(farbe)
    rauschen(img, 4, saat)
    rahmen(d)
    return img, d


def tex_technik_seite():
    img, d = metall(2)
    for y in (5, 10):
        d.line([(2, y), (13, y)], fill=(44, 46, 50))
    return img


def tex_kartenleser(an: bool):
    img, d = metall(3, (24, 25, 28))
    d.rectangle([3, 2, 12, 5], fill=(12, 12, 14))            # Kartenschlitz
    d.line([(4, 3), (11, 3)], fill=(2, 2, 3))
    d.rectangle([4, 8, 11, 12], fill=(30, 60, 90))            # Anzeige
    d.line([(5, 10), (10, 10)], fill=(90, 170, 230))
    led = (60, 230, 90) if an else (200, 40, 40)
    d.rectangle([7, 13, 8, 14], fill=led)
    return img


def tex_tastenfeld(an: bool):
    img, d = metall(4, (26, 27, 30))
    d.rectangle([3, 1, 12, 3], fill=(20, 70, 40) if not an else (40, 160, 80))
    for i in range(12):
        x = 3 + (i % 3) * 3
        y = 5 + (i // 3) * 3
        d.rectangle([x, y, x + 1, y + 1], fill=(190, 194, 200) if i != 11 else (80, 200, 100))
    return img


def tex_bewegungsmelder(an: bool):
    img, d = neu((220, 222, 224))
    rahmen(d, (240, 240, 240), (170, 172, 175))
    d.ellipse([3, 3, 12, 12], fill=(245, 245, 245), outline=(190, 190, 192))
    d.ellipse([6, 6, 9, 9], fill=(255, 120, 40) if an else (120, 120, 125))
    return img


def tex_alarmsirene():
    img, d = neu((150, 20, 20))
    rahmen(d, (200, 60, 60), (90, 10, 10))
    d.ellipse([2, 2, 13, 13], fill=(220, 40, 30))
    for x in range(4, 12, 2):
        d.line([(x, 4), (x, 11)], fill=(140, 20, 15))
    d.ellipse([6, 6, 9, 9], fill=(255, 210, 120))
    return img


def tex_aufzugstafel():
    img, d = neu((170, 174, 180))
    for y in range(16):
        d.line([(0, y), (15, y)], fill=(165 + (y % 3) * 4, 169 + (y % 3) * 4, 175 + (y % 3) * 4))
    rahmen(d, (210, 212, 216), (110, 112, 118))
    d.polygon([(8, 3), (5, 7), (11, 7)], fill=(255, 200, 60))
    d.polygon([(5, 9), (11, 9), (8, 13)], fill=(255, 200, 60))
    return img


def tex_kamera():
    img, d = neu((205, 208, 212))
    rauschen(img, 3, 5)
    rahmen(d, (235, 236, 238), (150, 152, 156))
    return img


def tex_kamera_linse():
    img, d = neu((20, 20, 24))
    d.ellipse([3, 3, 12, 12], fill=(10, 10, 12), outline=(60, 60, 70))
    d.ellipse([6, 6, 9, 9], fill=(40, 90, 200))
    d.point((7, 7), fill=(200, 230, 255))
    d.point((13, 2), fill=(230, 30, 30))
    return img


def tex_tuerklingel():
    img, d = neu((176, 140, 64))
    rauschen(img, 5, 6)
    rahmen(d, (220, 186, 100), (120, 90, 30))
    d.ellipse([5, 5, 10, 10], fill=(240, 236, 220), outline=(130, 100, 40))
    return img


def tex_kaminsteuerung():
    img, d = metall(7, (24, 24, 26))
    d.polygon([(8, 3), (11, 9), (8, 13), (5, 9)], fill=(240, 120, 30))
    d.polygon([(8, 7), (9, 10), (8, 12), (7, 10)], fill=(255, 220, 90))
    return img


def tex_brueckenschalter():
    """Schalter der Zugbrücke: Brücke über blauem Wasser, darüber ein Doppelpfeil, oben eine grüne Lampe."""
    img, d = metall(9, (34, 36, 40))
    d.rectangle([2, 10, 13, 12], fill=(40, 90, 170))                 # Wasser
    d.rectangle([3, 8, 12, 9], fill=(225, 220, 205))                 # Fahrbahn
    d.line([(3, 7), (12, 7)], fill=(212, 175, 55))                   # Geländer (Gold)
    d.line([(5, 4), (10, 4)], fill=(240, 240, 240))                  # Doppelpfeil
    d.point([(4, 4), (5, 3), (5, 5), (11, 4), (10, 3), (10, 5)], fill=(240, 240, 240))
    d.rectangle([7, 1, 8, 2], fill=(60, 220, 110))                   # Lampe
    return img


def tex_zentrale_front():
    img, d = metall(8, (30, 32, 36))
    d.rectangle([2, 2, 13, 9], fill=(10, 24, 40))
    for i, y in enumerate((4, 6, 8)):
        d.line([(3, y), (6 + i * 2, y)], fill=(60, 220, 120))
    d.rectangle([10, 3, 12, 5], fill=(220, 60, 50))
    for i, f in enumerate(((60, 200, 90), (230, 200, 40), (230, 120, 30), (210, 40, 40))):
        d.rectangle([2 + i * 3, 11, 3 + i * 3, 13], fill=f)
    return img


def tex_monitor_front():
    """Bildschirm (oben 14 × 10 Pixel genutzt): vier Kamerabilder, roter Aufnahmepunkt, dünner Rand."""
    img, d = neu((14, 14, 16))
    d.rectangle([0, 0, 13, 9], fill=(12, 12, 14))
    bilder = ((1, 1, 6, 4, (70, 110, 80)), (7, 1, 12, 4, (90, 100, 120)), (1, 5, 6, 8, (110, 110, 118)), (7, 5, 12, 8, (60, 80, 70)))
    for x0, y0, x1, y1, farbe in bilder:
        d.rectangle([x0, y0, x1, y1], fill=farbe)
        d.point((x0 + 1, y0 + 1), fill=tuple(min(255, c + 60) for c in farbe))
    d.point((12, 1), fill=(240, 40, 40))
    return img


def tex_monitor_wand():
    """Großer Wandmonitor (ganzer Block): vier Kamerabilder."""
    img, d = neu((18, 18, 20))
    d.rectangle([1, 2, 14, 12], fill=(70, 90, 110))
    for y in range(3, 12, 2):
        d.line([(2, y), (13, y)], fill=(80, 102, 124))
    d.rectangle([2, 8, 6, 11], fill=(60, 120, 60))
    d.rectangle([8, 5, 12, 11], fill=(150, 150, 160))
    d.point((13, 3), fill=(240, 40, 40))
    d.line([(6, 14), (9, 14)], fill=(60, 60, 64))
    return img


def tex_laser_front():
    img, d = metall(9, (22, 22, 24))
    d.ellipse([4, 4, 11, 11], fill=(60, 0, 0), outline=(120, 120, 128))
    d.ellipse([6, 6, 9, 9], fill=(255, 40, 30))
    d.point((7, 7), fill=(255, 200, 190))
    return img


def tex_laserstrahl():
    img, d = neu((255, 30, 20))
    for x in range(16):
        d.point((x, 7), fill=(255, 180, 170))
        d.point((x, 8), fill=(255, 180, 170))
    return img


def tex_warnleuchte(an: bool):
    basis = (255, 50, 30) if an else (110, 20, 18)
    img, d = neu(basis)
    for i in range(0, 16, 4):
        d.line([(i, 0), (i, 15)], fill=tuple(int(c * 0.8) for c in basis))
        d.line([(0, i), (15, i)], fill=tuple(int(c * 0.8) for c in basis))
    if an:
        d.ellipse([5, 5, 10, 10], fill=(255, 230, 200))
    rahmen(d, (60, 60, 62), (30, 30, 32))
    return img


def tex_torsteuerung():
    img, d = metall(10, (60, 62, 66))
    for i in range(-16, 16, 4):
        d.polygon([(i, 15), (i + 2, 15), (i + 18, 0), (i + 16, 0)], fill=(230, 190, 30))
    d.rectangle([3, 3, 12, 12], fill=(48, 50, 54))
    d.rectangle([6, 6, 9, 9], fill=(90, 200, 90))
    return img














def tex_whirlpool_oben():
    img, d = neu((226, 232, 236))
    d.rectangle([0, 0, 15, 15], outline=(190, 198, 204))
    for x, y in ((4, 4), (11, 4), (4, 11), (11, 11), (7, 7), (8, 8), (7, 8), (8, 7)):
        d.point((x, y), fill=(60, 90, 120))
    d.ellipse([5, 5, 10, 10], outline=(120, 160, 200))
    return img


def tex_zugangskarte():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([1, 4, 14, 12], radius=1, fill=(22, 22, 24, 255), outline=(200, 160, 50, 255))
    d.rectangle([3, 6, 5, 8], fill=(220, 180, 60, 255))     # Chip
    d.line([(7, 10), (12, 10)], fill=(200, 160, 50, 255))
    d.line([(7, 7), (12, 7)], fill=(90, 90, 96, 255))
    return img


def tex_autoschluessel():
    """Autoschlüssel: schwarzer Funkschlüssel mit goldenem Bart und rotem Knopf."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([2, 2, 8, 10], radius=2, fill=(24, 24, 28, 255), outline=(200, 160, 50, 255))
    d.rectangle([4, 4, 6, 5], fill=(200, 40, 40, 255))           # Knopf zum Abschließen
    d.point((5, 8), fill=(90, 90, 96, 255))
    d.rectangle([7, 8, 13, 9], fill=(225, 185, 70, 255))         # Bart
    d.point((11, 10), fill=(225, 185, 70, 255))
    d.point((13, 10), fill=(225, 185, 70, 255))
    d.line([(7, 9), (13, 9)], fill=(170, 130, 40, 255))
    return img


def tex_landmine():
    """Oberseite der Landmine: olivgrüner Stahl mit Nieten im Kreis."""
    img, d = neu((74, 80, 52))
    rauschen(img, 5, 21)
    d.ellipse([1, 1, 14, 14], outline=(52, 56, 36))
    for x, y in ((8, 2), (13, 8), (8, 13), (2, 8), (4, 4), (12, 4), (12, 12), (4, 12)):
        d.point((x, y), fill=(120, 126, 92))
    return img


def tex_landmine_licht(an: bool):
    img, d = neu((255, 40, 30) if an else (90, 18, 16))
    if an:
        d.rectangle([5, 5, 10, 10], fill=(255, 200, 180))
    return img


def tex_geschuetz():
    """Gehäuse des Abwehrgeschützes: dunkler Stahl mit Goldkante und Warnstreifen."""
    img, d = metall(23, (46, 48, 52))
    d.line([(0, 0), (15, 0)], fill=(212, 175, 55))
    for i in range(-16, 16, 5):
        d.polygon([(i, 15), (i + 2, 15), (i + 7, 11), (i + 5, 11)], fill=(230, 190, 30))
    return img


def tex_geschuetz_lauf():
    img, d = neu((20, 20, 22))
    rauschen(img, 3, 24)
    d.rectangle([5, 5, 10, 10], fill=(6, 6, 8))
    return img


SMART_FARBEN = {"weiss": (244, 244, 244), "warm": (255, 213, 138), "rot": (255, 59, 48), "gruen": (59, 227, 106),
                "blau": (59, 130, 255), "lila": (164, 91, 255), "pink": (255, 91, 200), "gold": (242, 194, 48)}


def tex_smartlicht(farbe):
    """Glasschirm der Smart-Home-Lampe: leuchtende Farbe mit hellem Glanzpunkt (aus = graues Glas)."""
    basis = SMART_FARBEN.get(farbe, (70, 72, 78))
    img = Image.new("RGBA", (16, 16), basis + (255,))
    d = ImageDraw.Draw(img)
    dunkel = tuple(int(c * 0.78) for c in basis)
    d.rectangle([0, 0, 15, 15], outline=dunkel)
    hell = tuple(min(255, c + 70) for c in basis)
    d.rectangle([3, 3, 5, 5], fill=hell)
    return img


def tex_geheimluke():
    """Versteckte Falltür: Rasen wie auf einer Wiese (Ebenen-Grün), ohne Fuge."""
    rnd = random.Random(31)
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    for x in range(16):
        for y in range(16):
            g = rnd.randint(-14, 14)
            px[x, y] = (max(0, 98 + g), max(0, 158 + g), max(0, 68 + g // 2), 255)
    return img                                  # keine Fuge am Rand, damit man die Luke nicht sieht


def tex_haifalle():
    """Glasboden über dem Haibecken: dünner Goldrand, sonst klar mit ein paar Glanzpunkten (Haie gut sichtbar)."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 15, 15], outline=(212, 175, 55, 255))
    for x, y in ((3, 2), (2, 3), (4, 3), (3, 4), (11, 10), (12, 11)):
        img.putpixel((x, y), (235, 245, 255, 255))
    return img


def tex_haifalle_schalter():
    """Schalter der Haifalle: grauer Kasten, Haiflosse über Wellen, großer roter Knopf."""
    img, d = metall(12, (34, 36, 40))
    d.rectangle([2, 9, 13, 10], fill=(40, 90, 170))                  # Wasser
    d.polygon([(6, 8), (9, 8), (9, 4)], fill=(150, 160, 170))        # Flosse
    d.ellipse([5, 11, 10, 14], fill=(200, 20, 20))                   # roter Knopf
    d.point([(6, 12)], fill=(255, 120, 120))
    return img


TEXTUREN = {
    "haifalle": tex_haifalle,
    "haifalle_schalter": tex_haifalle_schalter,
    "technik_seite": tex_technik_seite,
    "geheimluke": tex_geheimluke,
    **{f"smartlicht_{f}": (lambda f=f: tex_smartlicht(f)) for f in list(SMART_FARBEN) + ["aus"]},
    "landmine": tex_landmine,
    "landmine_licht": lambda: tex_landmine_licht(False),
    "landmine_licht_an": lambda: tex_landmine_licht(True),
    "geschuetz": tex_geschuetz,
    "geschuetz_lauf": tex_geschuetz_lauf,
    "kartenleser": lambda: tex_kartenleser(False),
    "kartenleser_an": lambda: tex_kartenleser(True),
    "tastenfeld": lambda: tex_tastenfeld(False),
    "tastenfeld_an": lambda: tex_tastenfeld(True),
    "bewegungsmelder": lambda: tex_bewegungsmelder(False),
    "bewegungsmelder_an": lambda: tex_bewegungsmelder(True),
    "alarmsirene": tex_alarmsirene,
    "aufzugstafel": tex_aufzugstafel,
    "kamera": tex_kamera,
    "kamera_linse": tex_kamera_linse,
    "tuerklingel": tex_tuerklingel,
    "kaminsteuerung": tex_kaminsteuerung,
    "brueckenschalter": tex_brueckenschalter,
    "sicherheitszentrale": tex_zentrale_front,
    "monitor": tex_monitor_front,
    "monitor_wand": tex_monitor_wand,
    "laser_sender": tex_laser_front,
    "laserstrahl": tex_laserstrahl,
    "warnleuchte": lambda: tex_warnleuchte(False),
    "warnleuchte_an": lambda: tex_warnleuchte(True),
    "torsteuerung": tex_torsteuerung,
    "whirlpoolduese": tex_whirlpool_oben,
}

# ---------------------------------------------------------------------------
# Modelle
# ---------------------------------------------------------------------------

SEITE = f"{NS}:block/technik_seite"


def m_wandplatte(front: str, seite: str = SEITE) -> dict:
    """Flache Platte an der Wand, Vorderseite zeigt nach Norden (für facing=north)."""
    return {
        "parent": "minecraft:block/block",
        "textures": {"particle": front, "front": front, "seite": seite},
        "elements": [{
            "from": [2, 2, 14], "to": [14, 14, 16],
            "faces": {
                "north": {"uv": [2, 2, 14, 14], "texture": "#front"},
                "south": {"uv": [2, 2, 14, 14], "texture": "#seite", "cullface": "south"},
                "east": {"uv": [14, 2, 16, 14], "texture": "#seite"},
                "west": {"uv": [0, 2, 2, 14], "texture": "#seite"},
                "up": {"uv": [2, 14, 14, 16], "texture": "#seite"},
                "down": {"uv": [2, 0, 14, 2], "texture": "#seite"},
            },
        }],
    }


def m_kamera() -> dict:
    gehaeuse = f"{NS}:block/kamera"
    linse = f"{NS}:block/kamera_linse"
    return {
        "parent": "minecraft:block/block",
        "textures": {"particle": gehaeuse, "g": gehaeuse, "l": linse},
        "elements": [
            {"from": [6, 9, 14], "to": [10, 11, 16], "faces": {f: {"texture": "#g"} for f in ("north", "south", "east", "west", "up", "down")}},
            {"from": [5, 7, 6], "to": [11, 12, 14], "rotation": {"angle": -22.5, "axis": "x", "origin": [8, 10, 14]},
             "faces": {"north": {"texture": "#l"}, "south": {"texture": "#g"}, "east": {"texture": "#g"},
                       "west": {"texture": "#g"}, "up": {"texture": "#g"}, "down": {"texture": "#g"}}},
        ],
    }


def m_landmine(licht: str) -> dict:
    """Flacher Minenteller mit Deckel und rotem Licht in der Mitte."""
    t, l = B("landmine"), B(licht)
    alle = ("north", "south", "east", "west", "up", "down")
    return {
        "parent": "minecraft:block/block",
        "textures": {"particle": t, "t": t, "l": l},
        "elements": [
            {"from": [3, 0, 3], "to": [13, 1, 13], "faces": {f: {"texture": "#t"} for f in alle}},
            {"from": [5, 1, 5], "to": [11, 2, 11], "faces": {f: {"texture": "#t"} for f in alle}},
            {"from": [7, 2, 7], "to": [9, 2.5, 9], "faces": {f: {"texture": "#l"} for f in alle}},
        ],
    }


def m_geschuetz(eingefahren: bool = False) -> dict:
    """Abwehrgeschütz: Sockel, Drehkopf mit Goldring, zwei Läufe nach vorn (Norden). Eingefahren steckt der Kopf
    im Sockel und die Läufe sind verborgen (außerhalb der Festung)."""
    g, lauf, gold = B("geschuetz"), B("geschuetz_lauf"), "minecraft:block/gold_block"
    alle = ("north", "south", "east", "west", "up", "down")
    def teil(von, bis, tex):
        return {"from": von, "to": bis, "faces": {f: {"texture": tex} for f in alle}}
    return {
        "parent": "minecraft:block/block",
        "textures": {"particle": g, "g": g, "l": lauf, "gold": gold},
        "elements": [teil([2, 0, 2], [14, 3, 14], "#g"), teil([4, 3, 4], [12, 5, 12], "#g"), teil([5, 5, 5], [11, 6, 11], "#gold")]
        if eingefahren else [
            teil([2, 0, 2], [14, 3, 14], "#g"),
            teil([5, 3, 5], [11, 4, 11], "#gold"),
            teil([4, 4, 4], [12, 10, 12], "#g"),
            teil([5, 6, -3], [7, 8, 4], "#l"),
            teil([9, 6, -3], [11, 8, 4], "#l"),
            teil([7, 10, 6], [9, 11, 10], "#l"),
        ],
    }


def m_orientierbar(front: str, seite: str = SEITE, oben: str = SEITE) -> dict:
    return {"parent": "minecraft:block/orientable", "textures": {"front": front, "side": seite, "top": oben}}


def m_monitor(winkel: float) -> dict:
    """Flacher Monitor auf Standfuß, Bildschirm nach Norden; winkel dreht ihn um die Hochachse."""
    t = B("monitor")
    gehaeuse = "minecraft:block/black_concrete"
    elemente = [
        {"from": [1, 3, 7], "to": [15, 13, 8.5], "faces": {
            "north": {"uv": [0, 0, 14, 10], "texture": "#bild"}, "south": {"texture": "#g"}, "east": {"texture": "#g"},
            "west": {"texture": "#g"}, "up": {"texture": "#g"}, "down": {"texture": "#g"}}},
        {"from": [7, 1, 8.5], "to": [9, 7, 9.5], "faces": {f: {"texture": "#g"} for f in ("north", "south", "east", "west", "up", "down")}},
        {"from": [5, 0, 6], "to": [11, 1, 11], "faces": {f: {"texture": "#g"} for f in ("north", "south", "east", "west", "up", "down")}},
    ]
    if winkel:
        for e in elemente:
            e["rotation"] = {"angle": winkel, "axis": "y", "origin": [8, 0, 8]}
    return {"parent": "minecraft:block/block", "textures": {"particle": gehaeuse, "g": gehaeuse, "bild": t},
            "elements": elemente}


def m_wuerfel(tex: str) -> dict:
    return {"parent": "minecraft:block/cube_all", "textures": {"all": tex}}


def m_saeule(seite: str, oben: str) -> dict:
    return {"parent": "minecraft:block/cube_column", "textures": {"side": seite, "end": oben}}




def m_laserstrahl() -> dict:
    t = f"{NS}:block/laserstrahl"
    return {
        "parent": "minecraft:block/block",
        "ambientocclusion": False,
        "textures": {"particle": t, "t": t},
        "elements": [{"from": [7.5, 0, 7.5], "to": [8.5, 16, 8.5], "shade": False,
                      "faces": {f: {"texture": "#t"} for f in ("north", "south", "east", "west")}}],
    }


LEER = {"textures": {"particle": "minecraft:block/iron_block"}, "elements": []}

# Drehungen für Blockzustände
WAAGERECHT = {"north": 0, "east": 90, "south": 180, "west": 270}
ALLE = {"north": (0, 0), "east": (0, 90), "south": (0, 180), "west": (0, 270), "up": (270, 0), "down": (90, 0)}


def bs_waagerecht(modell: str, extra: dict | None = None) -> dict:
    """facing-Varianten, optional mit weiterer Eigenschaft (z. B. powered=true -> anderes Modell)."""
    varianten = {}
    for f, y in WAAGERECHT.items():
        if extra:
            for wert, m in extra.items():
                varianten[f"facing={f},{wert}"] = {"model": m, "y": y} if y else {"model": m}
        else:
            varianten[f"facing={f}"] = {"model": modell, "y": y} if y else {"model": modell}
    return {"variants": varianten}


def bs_alle(modell: str) -> dict:
    v = {}
    for f, (x, y) in ALLE.items():
        e = {"model": modell}
        if x:
            e["x"] = x
        if y:
            e["y"] = y
        v[f"facing={f}"] = e
    return {"variants": v}


def m_smartlicht(farbe: str) -> dict:
    """Hängelampe: Kette aus Metall, goldene Fassung, Glasschirm in der Lampenfarbe."""
    glas, kette, gold = B(f"smartlicht_{farbe}"), "minecraft:block/black_concrete", "minecraft:block/gold_block"
    alle = ("north", "south", "east", "west", "up", "down")
    def teil(von, bis, tex):
        return {"from": von, "to": bis, "faces": {f: {"texture": tex} for f in alle}}
    return {"parent": "minecraft:block/block", "textures": {"particle": glas, "glas": glas, "kette": kette, "gold": gold},
            "elements": [teil([7.5, 10, 7.5], [8.5, 16, 8.5], "#kette"), teil([6, 9, 6], [10, 10, 10], "#gold"),
                         teil([4.5, 2, 4.5], [11.5, 9, 11.5], "#glas"), teil([6, 1, 6], [10, 2, 10], "#glas")]}


def m_rasenluke(von, bis, rasenseite: str) -> dict:
    """Versteckte Falltür: Oberseite mit der echten Grastextur des Spiels, im Spiel mit der Grasfarbe der Umgebung
    eingefärbt (tintindex 0, siehe StinkendReichClient), sonst Erde – so sieht man sie im Rasen nicht."""
    flaechen = {}
    for f in ("north", "south", "east", "west", "up", "down"):
        if f == rasenseite or (rasenseite == "north" and f == "south"):
            flaechen[f] = {"texture": "#rasen", "tintindex": 0}
        else:
            flaechen[f] = {"texture": "#erde"}
    return {"parent": "minecraft:block/block", "textures": {"particle": "minecraft:block/dirt", "erde": "minecraft:block/dirt",
                                                            "rasen": "minecraft:block/grass_block_top"},
            "elements": [{"from": von, "to": bis, "faces": flaechen}]}


def bs_falltuer(name: str) -> dict:
    """Blockzustände einer Falltür wie im Spiel (Richtung × Hälfte × offen)."""
    v = {}
    for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        v[f"facing={f},half=bottom,open=false"] = {"model": B(name), "y": y}
        v[f"facing={f},half=top,open=false"] = {"model": B(name + "_top"), "y": y}
        v[f"facing={f},half=bottom,open=true"] = {"model": B(name + "_open"), "y": y}
        v[f"facing={f},half=top,open=true"] = {"model": B(name + "_open"), "x": 180, "y": (y + 180) % 360}
    return {"variants": v}


def bs_einfach(modell: str) -> dict:
    return {"variants": {"": {"model": modell}}}




def B(name: str) -> str:
    return f"{NS}:block/{name}"


# Deckenblöcke (register/ModDecken.java): oben und seitlich wie das Vorbild, unten weiß
# Kurzname: (Textur des Vorbilds, Werkzeug oder None, Deutsch, Englisch)
DECKEN = {
    "holz": ("dark_oak_planks", "axe", "Holzboden mit weißer Decke", "Wood Floor with White Ceiling"),
    "teppich_weiss": ("white_wool", None, "Weißer Teppich mit weißer Decke", "White Carpet with White Ceiling"),
    "teppich_rot": ("red_wool", None, "Roter Teppich mit weißer Decke", "Red Carpet with White Ceiling"),
    "teppich_gelb": ("yellow_wool", None, "Gelber Teppich mit weißer Decke", "Yellow Carpet with White Ceiling"),
    "grau": ("gray_concrete", "pickaxe", "Grauer Beton mit weißer Decke", "Gray Concrete with White Ceiling"),
    "gold": ("gold_block", "pickaxe", "Goldboden mit weißer Decke", "Gold Floor with White Ceiling"),
    "schwarz": ("polished_blackstone", "pickaxe", "Schwarzer Stein mit weißer Decke", "Blackstone Floor with White Ceiling"),
    "schiefer": ("polished_deepslate", "pickaxe", "Tiefenschiefer mit weißer Decke", "Deepslate Floor with White Ceiling"),
}


def m_decke(vorbild: str) -> dict:
    t = f"minecraft:block/{vorbild}"
    return {"parent": "minecraft:block/cube",
            "textures": {"particle": t, "up": t, "north": t, "south": t, "east": t, "west": t,
                         "down": "minecraft:block/white_concrete"}}


# Name -> (Blockmodelle {modellname: json}, Blockzustand, Item-Modell, Werkzeug, Deutsch, Englisch)
BLOECKE: dict[str, dict] = {
    "sicherheitszentrale": dict(modelle={"sicherheitszentrale": m_orientierbar(B("sicherheitszentrale"))},
                                zustand=bs_waagerecht(B("sicherheitszentrale")), de="Sicherheitszentrale", en="Security Console"),
    "laser_sender": dict(modelle={"laser_sender": {"parent": "minecraft:block/cube",
                                                     "textures": {"particle": SEITE, "north": B("laser_sender"), "south": SEITE,
                                                                  "east": SEITE, "west": SEITE, "up": SEITE, "down": SEITE}}},
                         zustand=bs_alle(B("laser_sender")), de="Laser-Sender", en="Laser Emitter"),
    "laserstrahl": dict(modelle={"laserstrahl": m_laserstrahl()},
                        zustand={"variants": {"axis=y": {"model": B("laserstrahl")},
                                              "axis=x": {"model": B("laserstrahl"), "x": 90, "y": 90},
                                              "axis=z": {"model": B("laserstrahl"), "x": 90}}},
                        item=False, loot=False, de="Laserstrahl", en="Laser Beam"),
    "bewegungsmelder": dict(modelle={"bewegungsmelder": m_wandplatte(B("bewegungsmelder"), B("bewegungsmelder")),
                                     "bewegungsmelder_an": m_wandplatte(B("bewegungsmelder_an"), B("bewegungsmelder"))},
                            zustand=bs_waagerecht("", {"powered=false": B("bewegungsmelder"), "powered=true": B("bewegungsmelder_an")}),
                            de="Bewegungsmelder", en="Motion Sensor"),
    "alarmsirene": dict(modelle={"alarmsirene": m_wandplatte(B("alarmsirene"))},
                        zustand=bs_waagerecht(B("alarmsirene")), de="Alarmsirene", en="Alarm Siren"),
    "warnleuchte": dict(modelle={"warnleuchte": m_wuerfel(B("warnleuchte")), "warnleuchte_an": m_wuerfel(B("warnleuchte_an"))},
                        zustand={"variants": {"lit=false": {"model": B("warnleuchte")}, "lit=true": {"model": B("warnleuchte_an")}}},
                        de="Warnleuchte", en="Warning Light"),
    "kartenleser": dict(modelle={"kartenleser": m_wandplatte(B("kartenleser")), "kartenleser_an": m_wandplatte(B("kartenleser_an"))},
                        zustand=bs_waagerecht("", {"powered=false": B("kartenleser"), "powered=true": B("kartenleser_an")}),
                        de="Kartenleser", en="Card Reader"),
    "tastenfeld": dict(modelle={"tastenfeld": m_wandplatte(B("tastenfeld")), "tastenfeld_an": m_wandplatte(B("tastenfeld_an"))},
                       zustand=bs_waagerecht("", {"powered=false": B("tastenfeld"), "powered=true": B("tastenfeld_an")}),
                       de="Tastenfeld", en="Keypad"),
    "tuerklingel": dict(modelle={"tuerklingel": m_wandplatte(B("tuerklingel"), B("tuerklingel"))},
                        zustand=bs_waagerecht(B("tuerklingel")), de="Türklingel", en="Doorbell"),
    "torsteuerung": dict(modelle={"torsteuerung": m_wuerfel(B("torsteuerung"))}, zustand=bs_einfach(B("torsteuerung")),
                         de="Torsteuerung", en="Gate Controller"),
    "geheimschalter": dict(modelle={"geheimschalter": m_saeule("minecraft:block/bookshelf", "minecraft:block/oak_planks")},
                           zustand=bs_einfach(B("geheimschalter")), werkzeug="axe", de="Geheimschalter", en="Secret Switch"),
    "aufzugstafel": dict(modelle={"aufzugstafel": m_wandplatte(B("aufzugstafel"), B("aufzugstafel"))},
                         zustand=bs_waagerecht(B("aufzugstafel")), de="Aufzugstafel", en="Elevator Panel"),
    "fallenplatte": dict(modelle={"fallenplatte": m_wuerfel("minecraft:block/gold_block")}, zustand=bs_einfach(B("fallenplatte")),
                         de="Fallenplatte", en="Trap Plate"),
    "entschaerfknopf": dict(modelle={"entschaerfknopf": m_wuerfel("minecraft:block/white_concrete")},
                            zustand=bs_einfach(B("entschaerfknopf")), de="Entschärfknopf", en="Hidden Disarm Button"),
    "kamera": dict(modelle={"kamera": m_kamera()}, zustand=bs_waagerecht(B("kamera")), de="Überwachungskamera", en="Security Camera"),
    "monitor": dict(modelle={"monitor": m_monitor(0), "monitor_links": m_monitor(22.5), "monitor_rechts": m_monitor(-22.5),
                             "monitor_wand": m_orientierbar(B("monitor_wand"), "minecraft:block/black_concrete",
                                                            "minecraft:block/black_concrete")},
                    zustand=bs_waagerecht("", {"winkel=gerade": B("monitor"), "winkel=links": B("monitor_links"),
                                              "winkel=rechts": B("monitor_rechts"), "winkel=wand": B("monitor_wand")}),
                    de="Überwachungsmonitor", en="Security Monitor"),
    "whirlpoolduese": dict(modelle={"whirlpoolduese": m_saeule("minecraft:block/quartz_block_side", B("whirlpoolduese"))},
                           zustand=bs_einfach(B("whirlpoolduese")), de="Whirlpool-Düse", en="Whirlpool Jet"),
    "kaminsteuerung": dict(modelle={"kaminsteuerung": m_wandplatte(B("kaminsteuerung"))},
                           zustand=bs_waagerecht(B("kaminsteuerung")), de="Kaminschalter", en="Fireplace Switch"),
    "brueckenschalter": dict(modelle={"brueckenschalter": m_wandplatte(B("brueckenschalter"))},
                             zustand=bs_waagerecht(B("brueckenschalter")), de="Brückenschalter", en="Drawbridge Switch"),
    "landmine": dict(modelle={"landmine": m_landmine("landmine_licht"), "landmine_an": m_landmine("landmine_licht_an")},
                     zustand={"variants": {"lit=false": {"model": B("landmine")}, "lit=true": {"model": B("landmine_an")}}},
                     de="Landmine (scharf in Stufe 4)", en="Land Mine (armed at level 4)"),
    "smartlicht": dict(modelle={"smartlicht" if f == "warm" else f"smartlicht_{f}": m_smartlicht(f) for f in list(SMART_FARBEN) + ["aus"]},
                       zustand={"variants": {f"farbe={f},lit={'true' if an else 'false'}":
                                             {"model": B("smartlicht" if (an and f == "warm") else (f"smartlicht_{f}" if an else "smartlicht_aus"))}
                                             for f in SMART_FARBEN for an in (True, False)}},
                       de="Smart-Home-Lampe", en="Smart Home Lamp"),
    "geheimluke": dict(modelle={"geheimluke": m_rasenluke([0, 0, 0], [16, 3, 16], "up"),
                                "geheimluke_top": m_rasenluke([0, 13, 0], [16, 16, 16], "up"),
                                "geheimluke_open": m_rasenluke([0, 0, 13], [16, 16, 16], "north")},
                       zustand=bs_falltuer("geheimluke"), de="Versteckte Falltür (nur mit Zugangskarte)",
                       en="Hidden Trapdoor (key card only)"),
    "haifalle": dict(modelle={"haifalle": m_wuerfel(B("haifalle")),
                              "haifalle_offen": {"textures": {"particle": B("haifalle")}}},
                     zustand={"variants": {"open=false": {"model": B("haifalle")}, "open=true": {"model": B("haifalle_offen")}}},
                     de="Haifallen-Boden", en="Shark Trap Floor"),
    "haifalle_schalter": dict(modelle={"haifalle_schalter": m_wandplatte(B("haifalle_schalter"))},
                              zustand=bs_waagerecht(B("haifalle_schalter")), de="Haifallen-Schalter", en="Shark Trap Switch"),
    "abwehrgeschuetz": dict(modelle={"abwehrgeschuetz": m_geschuetz(), "abwehrgeschuetz_ein": m_geschuetz(eingefahren=True)},
                            zustand=bs_waagerecht("", {"powered=true": B("abwehrgeschuetz"), "powered=false": B("abwehrgeschuetz_ein")}),
                            de="Abwehrgeschütz", en="Defense Turret"),
    # Regendusche (mechanik/Dusche): Modelle aus deko.py, Zustände facing × wand (Rohr) × an (Wasser läuft)
    "duschkopf": dict(modelle={("duschkopf" + ("_wand" if wand else "") + ("_an" if an else "")): deko.modell(deko.duschkopf(wand, an))
                               for wand in (False, True) for an in (False, True)},
                      zustand={"variants": {f"an={str(an).lower()},facing={f},wand={str(wand).lower()}":
                                            {"model": B("duschkopf" + ("_wand" if wand else "") + ("_an" if an else "")), **({"y": y} if y else {})}
                                            for f, y in WAAGERECHT.items() for wand in (False, True) for an in (False, True)}},
                      de="Regen-Duschkopf", en="Rain Shower Head"),
    "duscharmatur": dict(modelle={"duscharmatur": deko.modell(deko.duscharmatur(False)),
                                  "duscharmatur_an": deko.modell(deko.duscharmatur(True))},
                         zustand=bs_waagerecht("", {"an=false": B("duscharmatur"), "an=true": B("duscharmatur_an")}),
                         de="Duscharmatur", en="Shower Valve"),
}

TEXTE_DE = {
    "entity.stinkendreich.hai": "Hai",
    "death.attack.stinkendreich.hai": "%1$s wurde von einem Hai gefressen",
    "death.attack.stinkendreich.hai.player": "%1$s wurde von einem Hai gefressen, während %2$s zusah",
    "stinkendreich.haifalle.nur_bewohner": "Nur Bewohner dürfen die Haie füttern.",
    "stinkendreich.haifalle.ausgeloest": "♒ Ab zu den Haien!",
    "stinkendreich.haifalle.kein_opfer": "Niemand Frecher in der Nähe.",
    "stinkendreich.haifalle.keine_falle": "Kein Haibecken in der Nähe.",
    "stinkendreich.haifalle.laeuft": "Die Falle ist noch offen.",
    "stinkendreich.haifalle.opfer": "♒ Du landest bei den Haien!",
    "stinkendreich.haifalle.automatik": "♒ %s hat dich angegriffen und landet bei den Haien!",
    "itemGroup.stinkendreich": "Stinkend Reich",
    "item.stinkendreich.zugangskarte": "Zugangskarte",
    "stinkendreich.stufe.0": "Stufe 0 – Aus",
    "stinkendreich.stufe.1": "Stufe 1 – Alarm",
    "stinkendreich.stufe.2": "Stufe 2 – Lockdown",
    "stinkendreich.stufe.3": "Stufe 3 – Laser",
    "stinkendreich.stufe.4": "Festung",
    "stinkendreich.festung.an": "⚠ FESTUNG AKTIV ⚠",
    "stinkendreich.stufe.gesetzt": "Sicherheitssystem: %s",
    "stinkendreich.alarm.start": "ALARM! %s (bei %s %s %s)",
    "stinkendreich.alarm.ende": "Alarm beendet.",
    "stinkendreich.alarm.an": "Alarm",
    "stinkendreich.alarm.aus": "ruhig",
    "stinkendreich.alarm.grund.zentrale": "%s hat die Zentrale ohne Berechtigung benutzt",
    "stinkendreich.alarm.grund.laser": "%s hat einen Laser durchbrochen",
    "stinkendreich.alarm.grund.bewegung": "Bewegung erkannt: %s",
    "stinkendreich.alarm.grund.karte": "%s hat ohne Karte versucht, eine Tür zu öffnen",
    "stinkendreich.alarm.grund.code": "%s hat dreimal einen falschen Code eingegeben",
    "stinkendreich.alarm.grund.tresor": "%s hat die Tresorfalle ausgelöst",
    "stinkendreich.alarm.grund.mine": "%s ist auf eine Mine getreten",
    "stinkendreich.zentrale.status": "Sicherheitssystem: %s · Zustand: %s · Bewohner: %s",
    "stinkendreich.zentrale.eingetragen": "Du bist jetzt Bewohner der Villa.",
    "stinkendreich.zentrale.nur_eigentuemer": "Die Alarmstufe kann nur der Eigentümer der Villa einstellen.",
    "stinkendreich.zentrale.nur_bunker": "Die Festung lässt sich nur in der Kommandozentrale im Bunker einschalten.",
    "stinkendreich.zentrale.alarm_aus": "Alarm abschalten",
    "stinkendreich.zentrale.eintragen": "Mich als Bewohner eintragen",
    "stinkendreich.zugang.verweigert": "Zugriff verweigert.",
    "stinkendreich.zugang.karte": "Karte erkannt – Tür offen.",
    "stinkendreich.zugang.keine_karte": "Keine Zugangskarte!",
    "stinkendreich.aufzug.nur_karte": "%s nur mit Zugangskarte!",
    "stinkendreich.aufzug.mit_karte": "(Zugangskarte)",
    "stinkendreich.zugang.code": "Code richtig – Tür offen.",
    "stinkendreich.zugang.falscher_code": "Falscher Code!",
    "stinkendreich.zugang.code_geaendert": "Neuer Code gespeichert.",
    "stinkendreich.zugang.code_nicht_geaendert": "Code nicht geändert (alter Code falsch, kein Bewohner oder neuer Code nicht 4–8 Ziffern).",
    "stinkendreich.tresor.falle": "FALLE! Der Tresor ist verriegelt.",
    "stinkendreich.privattuer.verweigert": "Privat! Nur Bewohner der Villa kommen hier durch.",
    "stinkendreich.sicherheitstuer.karte": "Halte die Zugangskarte an den Kartenleser neben der Tür.",
    "stinkendreich.strasse.bau": "Nach %s gibt es noch keine Straße, Sir. Ich lasse eine bauen – das dauert einen Moment.",
    "stinkendreich.strasse.fortschritt": "Straßenbau nach %s: %s %%",
    "stinkendreich.strasse.fertig": "Die Straße nach %s ist fertig. Wir fahren los, Sir.",
    "stinkendreich.strasse.nicht_gefunden": "Ich finde „%s“ nirgends in der Nähe, Sir.",
    "stinkendreich.strasse.gibtes": "Nach %s gibt es schon eine Straße, Sir.",
    "stinkendreich.strasse.wirdgebaut": "Sehr wohl, ich lasse eine Straße nach %s bauen.",
    "stinkendreich.strasse.keinevilla": "Ohne Villa kein Kreisel, Sir – hier kann ich keine Straße bauen lassen.",
    "stinkendreich.strasse.liste": "Straßen: %s · im Bau: %s",
    "stinkendreich.strasse.ziel.dorf": "Dorf",
    "stinkendreich.strasse.ziel.villa": "Villa von %s",
    "stinkendreich.strasse.ziel.villa_heim": "Villa",
    "stinkendreich.strasse.ziel.villa_nr": "Villa %s",
    "stinkendreich.strasse.neu": "Alle Straßen werden neu gebaut (Dorf, Mine, Netherportal, Endportal).",
    "stinkendreich.strasse.ziel.endportal": "Endportal",
    "stinkendreich.strasse.ziel.netherportal": "Netherportal",
    "stinkendreich.strasse.ziel.mine": "Mine",
    "stinkendreich.geheimgang.offen": "✨ Ein magischer Eingang öffnet sich …",
    "stinkendreich.geheimgang.nichts": "Die Bücher flüstern … aber das Regal bleibt zu.",
    "stinkendreich.klavier.lied.fuer_elise": "Für Elise",
    "stinkendreich.klavier.lied.ode": "Ode an die Freude",
    "stinkendreich.klavier.lied.kanon": "Kanon in D",
    "stinkendreich.klavier.stopp": "Stopp",
    "stinkendreich.klavier.hinweis": "Tasten anklicken oder spielen: Y–M, Q–U, I–+  (schwarz: S D G H J · 2 3 5 6 7 · 9 0 ´)",
    "stinkendreich.graben.lava": "Alarmanlage an und du bist zu Hause: Der Graben füllt sich mit Lava.",
    "stinkendreich.graben.wasser": "Der Graben ist wieder voller Wasser.",
    "stinkendreich.bruecke.einfahren": "Achtung: Die Zugbrücke fährt ein …",
    "stinkendreich.bruecke.ausfahren": "Die Zugbrücke fährt aus …",
    "stinkendreich.bruecke.eingefahren": "Zugbrücke eingefahren. Niemand kommt mehr über den Graben.",
    "stinkendreich.bruecke.ausgefahren": "Zugbrücke ausgefahren. Der Weg ist frei.",
    "stinkendreich.bruecke.nur_bewohner": "Nur Bewohner der Villa können die Zugbrücke bedienen.",
    "stinkendreich.bruecke.keine": "In dieser Welt gibt es keine Zugbrücke.",
    "stinkendreich.bruecke.james": "James fährt die Zugbrücke für den Wagen aus.",
    "stinkendreich.bruecke.fuer_dich": "Willkommen! Die Zugbrücke fährt für dich aus …",
    "stinkendreich.bruecke.gesichert": "Zugbrücke gesichert: Sie fährt ein und kommt nur für Bewohner und Besitzer einer Zugangskarte heraus.",
    "stinkendreich.bruecke.offen": "Zugbrücke offen: Sie fährt aus und bleibt draußen.",
    "stinkendreich.tresor.entschaerft": "Tresorfallen für 60 Sekunden entschärft.",
    "stinkendreich.tresor.knopf": "Klick … noch %s Knopf",
    "stinkendreich.klingel": "Es klingelt am Tor: %s",
    "stinkendreich.tastenfeld.aendern": "Code ändern",
    "stinkendreich.tastenfeld.code": "Code",
    "stinkendreich.tastenfeld.alt": "Alter Code",
    "stinkendreich.tastenfeld.neu": "Neuer Code",
    "stinkendreich.kamera.beenden": "Überwachung beenden",
    "stinkendreich.kamera.keine": "Keine Kameras in der Nähe.",
    "stinkendreich.kamera.nummer": "Kamera %s von %s",
    "stinkendreich.dusche.kein_kopf": "Kein Duschkopf in der Nähe (bis 4 Blöcke).",
}
TEXTE_EN = {
    "entity.stinkendreich.hai": "Shark",
    "death.attack.stinkendreich.hai": "%1$s was eaten by a shark",
    "death.attack.stinkendreich.hai.player": "%1$s was eaten by a shark while fighting %2$s",
    "stinkendreich.haifalle.nur_bewohner": "Only residents may feed the sharks.",
    "stinkendreich.haifalle.ausgeloest": "♒ Off to the sharks!",
    "stinkendreich.haifalle.kein_opfer": "Nobody cheeky nearby.",
    "stinkendreich.haifalle.keine_falle": "No shark tank nearby.",
    "stinkendreich.haifalle.laeuft": "The trap is still open.",
    "stinkendreich.haifalle.opfer": "♒ You land with the sharks!",
    "stinkendreich.haifalle.automatik": "♒ %s attacked you and lands with the sharks!",
    "itemGroup.stinkendreich": "Filthy Rich",
    "item.stinkendreich.zugangskarte": "Access Card",
    "stinkendreich.stufe.0": "Level 0 – Off",
    "stinkendreich.stufe.1": "Level 1 – Alarm",
    "stinkendreich.stufe.2": "Level 2 – Lockdown",
    "stinkendreich.stufe.3": "Level 3 – Lasers",
    "stinkendreich.stufe.4": "Fortress",
    "stinkendreich.festung.an": "⚠ FORTRESS ACTIVE ⚠",
    "stinkendreich.stufe.gesetzt": "Security system: %s",
    "stinkendreich.alarm.start": "ALARM! %s (at %s %s %s)",
    "stinkendreich.alarm.ende": "Alarm ended.",
    "stinkendreich.alarm.an": "alarm",
    "stinkendreich.alarm.aus": "quiet",
    "stinkendreich.alarm.grund.zentrale": "%s used the console without permission",
    "stinkendreich.alarm.grund.laser": "%s broke a laser beam",
    "stinkendreich.alarm.grund.bewegung": "Motion detected: %s",
    "stinkendreich.alarm.grund.karte": "%s tried to open a door without a card",
    "stinkendreich.alarm.grund.code": "%s entered a wrong code three times",
    "stinkendreich.alarm.grund.tresor": "%s triggered the vault trap",
    "stinkendreich.alarm.grund.mine": "%s stepped on a mine",
    "stinkendreich.zentrale.status": "Security system: %s · State: %s · Residents: %s",
    "stinkendreich.zentrale.eingetragen": "You are now a resident of the villa.",
    "stinkendreich.zentrale.nur_eigentuemer": "Only the owner of the villa can change the alarm level.",
    "stinkendreich.zentrale.nur_bunker": "The fortress mode can only be switched on in the bunker's command center.",
    "stinkendreich.zentrale.alarm_aus": "Turn off alarm",
    "stinkendreich.zentrale.eintragen": "Register me as resident",
    "stinkendreich.zugang.verweigert": "Access denied.",
    "stinkendreich.zugang.karte": "Card accepted – door open.",
    "stinkendreich.zugang.keine_karte": "No access card!",
    "stinkendreich.aufzug.nur_karte": "%s only with an access card!",
    "stinkendreich.aufzug.mit_karte": "(access card)",
    "stinkendreich.zugang.code": "Code correct – door open.",
    "stinkendreich.zugang.falscher_code": "Wrong code!",
    "stinkendreich.zugang.code_geaendert": "New code saved.",
    "stinkendreich.zugang.code_nicht_geaendert": "Code not changed (old code wrong, not a resident, or new code not 4–8 digits).",
    "stinkendreich.tresor.falle": "TRAP! The vault is locked.",
    "stinkendreich.privattuer.verweigert": "Private! Residents of the villa only.",
    "stinkendreich.sicherheitstuer.karte": "Hold your key card to the card reader next to the door.",
    "stinkendreich.strasse.bau": "There is no road to %s yet, sir. I'll have one built – it takes a moment.",
    "stinkendreich.strasse.fortschritt": "Building the road to %s: %s %%",
    "stinkendreich.strasse.fertig": "The road to %s is finished. Off we go, sir.",
    "stinkendreich.strasse.nicht_gefunden": "I can't find \u201c%s\u201d anywhere nearby, sir.",
    "stinkendreich.strasse.gibtes": "There already is a road to %s, sir.",
    "stinkendreich.strasse.wirdgebaut": "Very well, I'll have a road to %s built.",
    "stinkendreich.strasse.keinevilla": "No villa, no roundabout, sir – I can't have a road built here.",
    "stinkendreich.strasse.liste": "Roads: %s · being built: %s",
    "stinkendreich.strasse.ziel.dorf": "the village",
    "stinkendreich.strasse.ziel.villa": "%s's villa",
    "stinkendreich.strasse.ziel.villa_heim": "Villa",
    "stinkendreich.strasse.ziel.villa_nr": "villa %s",
    "stinkendreich.strasse.neu": "All roads are being rebuilt (village, mine, nether portal, end portal).",
    "stinkendreich.strasse.ziel.endportal": "the End portal",
    "stinkendreich.strasse.ziel.netherportal": "the Nether portal",
    "stinkendreich.strasse.ziel.mine": "the mine",
    "stinkendreich.geheimgang.offen": "✨ A magical entrance opens …",
    "stinkendreich.geheimgang.nichts": "The books whisper … but the shelf stays shut.",
    "stinkendreich.klavier.lied.fuer_elise": "Für Elise",
    "stinkendreich.klavier.lied.ode": "Ode to Joy",
    "stinkendreich.klavier.lied.kanon": "Canon in D",
    "stinkendreich.klavier.stopp": "Stop",
    "stinkendreich.klavier.hinweis": "Click the keys or play with Z–M, Q–U, I–]  (black: S D G H J · 2 3 5 6 7 · 9 0 =)",
    "stinkendreich.graben.lava": "Alarm armed and you are home: the moat fills with lava.",
    "stinkendreich.graben.wasser": "The moat is filled with water again.",
    "stinkendreich.bruecke.einfahren": "Attention: the drawbridge is retracting …",
    "stinkendreich.bruecke.ausfahren": "The drawbridge is extending …",
    "stinkendreich.bruecke.eingefahren": "Drawbridge retracted. Nobody gets across the moat now.",
    "stinkendreich.bruecke.ausgefahren": "Drawbridge extended. The way is clear.",
    "stinkendreich.bruecke.nur_bewohner": "Only residents of the villa can operate the drawbridge.",
    "stinkendreich.bruecke.keine": "There is no drawbridge in this world.",
    "stinkendreich.bruecke.james": "James is extending the drawbridge for the car.",
    "stinkendreich.bruecke.fuer_dich": "Welcome! The drawbridge is extending for you …",
    "stinkendreich.bruecke.gesichert": "Drawbridge secured: it retracts and only comes out for residents and access card holders.",
    "stinkendreich.bruecke.offen": "Drawbridge open: it extends and stays out.",
    "stinkendreich.tresor.entschaerft": "Vault traps disarmed for 60 seconds.",
    "stinkendreich.tresor.knopf": "Click … %s button(s) left",
    "stinkendreich.klingel": "Someone is ringing at the gate: %s",
    "stinkendreich.tastenfeld.aendern": "Change code",
    "stinkendreich.tastenfeld.code": "Code",
    "stinkendreich.tastenfeld.alt": "Old code",
    "stinkendreich.tastenfeld.neu": "New code",
    "stinkendreich.kamera.beenden": "Stop watching",
    "stinkendreich.kamera.keine": "No cameras nearby.",
    "stinkendreich.kamera.nummer": "Camera %s of %s",
    "stinkendreich.dusche.kein_kopf": "No shower head nearby (up to 4 blocks).",
}


def schreibe_json(pfad: Path, daten) -> None:
    pfad.parent.mkdir(parents=True, exist_ok=True)
    pfad.write_text(json.dumps(daten, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def main() -> None:
    # Texturen
    for name, fabrik in TEXTUREN.items():
        pfad = ASSETS / "textures/block" / f"{name}.png"
        pfad.parent.mkdir(parents=True, exist_ok=True)
        fabrik().save(pfad)
    (ASSETS / "textures/item").mkdir(parents=True, exist_ok=True)
    tex_zugangskarte().save(ASSETS / "textures/item/zugangskarte.png")
    tex_autoschluessel().save(ASSETS / "textures/item/autoschluessel.png")
    schreibe_json(ASSETS / "models/item/autoschluessel.json",
                  {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/autoschluessel"}})
    schreibe_json(ASSETS / "items/autoschluessel.json",
                  {"model": {"type": "minecraft:model", "model": f"{NS}:item/autoschluessel"}})

    schreibe_json(ASSETS / "models/block/leer.json", LEER)
    werkzeuge: dict[str, list[str]] = {"pickaxe": [], "axe": []}
    for name, b in BLOECKE.items():
        for modell, inhalt in b["modelle"].items():
            schreibe_json(ASSETS / "models/block" / f"{modell}.json", inhalt)
        schreibe_json(ASSETS / "blockstates" / f"{name}.json", b["zustand"])
        if b.get("item", True):
            # bis 1.21.3: Item-Modell; ab 1.21.4: Item-Definition
            schreibe_json(ASSETS / "models/item" / f"{name}.json", {"parent": B(name)})
            schreibe_json(ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": B(name)}})
        if b.get("loot", True):
            schreibe_json(RES / f"data/{NS}/loot_table/blocks/{name}.json", {
                "type": "minecraft:block",
                "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}],
                           "conditions": [{"condition": "minecraft:survives_explosion"}]}],
            })
            werkzeuge[b.get("werkzeug", "pickaxe")].append(f"{NS}:{name}")
        TEXTE_DE[f"block.{NS}.{name}"] = b["de"]
        TEXTE_EN[f"block.{NS}.{name}"] = b["en"]

    for kurz, (vorbild, werkzeug, de, en) in DECKEN.items():
        name = f"decke_{kurz}"
        schreibe_json(ASSETS / "models/block" / f"{name}.json", m_decke(vorbild))
        schreibe_json(ASSETS / "blockstates" / f"{name}.json", bs_einfach(B(name)))
        schreibe_json(ASSETS / "models/item" / f"{name}.json", {"parent": B(name)})
        schreibe_json(ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": B(name)}})
        schreibe_json(RES / f"data/{NS}/loot_table/blocks/{name}.json", {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}],
        })
        if werkzeug:
            werkzeuge[werkzeug].append(f"{NS}:{name}")
        TEXTE_DE[f"block.{NS}.{name}"] = de
        TEXTE_EN[f"block.{NS}.{name}"] = en

    schreibe_json(ASSETS / "models/item/zugangskarte.json",
                  {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/zugangskarte"}})
    schreibe_json(ASSETS / "items/zugangskarte.json",
                  {"model": {"type": "minecraft:model", "model": f"{NS}:item/zugangskarte"}})
    # Türen und Torflügel (eigener Generator), dann gemeinsame Werkzeug-Listen und Namen
    t = tueren.main()
    for werkzeug, liste in t["werkzeuge"].items():
        werkzeuge[werkzeug] += liste
    TEXTE_DE.update(t["de"])
    TEXTE_EN.update(t["en"])
    dk = deko.main()
    for werkzeug, liste in dk["werkzeuge"].items():
        werkzeuge[werkzeug] += liste
    TEXTE_DE.update(dk["de"])
    TEXTE_EN.update(dk["en"])
    fz = fahrzeuge.main()
    TEXTE_DE.update(fz["de"])
    TEXTE_EN.update(fz["en"])
    for texte in (ausruestung.main(), drehteller.main(), fernseher.main(), taenzerin.main(), handy.main(), hai.main()):
        TEXTE_DE.update(texte["de"])
        TEXTE_EN.update(texte["en"])
    g = gemaelde.main()
    TEXTE_DE.update(g["de"])
    TEXTE_EN.update(g["en"])
    for werkzeug, liste in werkzeuge.items():
        pfad = RES / f"data/minecraft/tags/block/mineable/{werkzeug}.json"
        # Einträge anderer Generatoren (z. B. musik.py) behalten
        alt = json.loads(pfad.read_text(encoding="utf-8"))["values"] if pfad.exists() else []
        fremde = [v for v in alt if v not in liste and v.removeprefix(f"{NS}:") not in BLOECKE
                  and not v.startswith(f"{NS}:decke_")]
        schreibe_json(pfad, {"replace": False, "values": liste + fremde})

    # Sprachdateien ergänzen (vorhandene Einträge bleiben)
    for datei, texte in (("de_de", TEXTE_DE), ("en_us", TEXTE_EN)):
        pfad = ASSETS / "lang" / f"{datei}.json"
        vorhanden = json.loads(pfad.read_text(encoding="utf-8")) if pfad.exists() else {}
        vorhanden.update(texte)
        schreibe_json(pfad, dict(sorted(vorhanden.items())))
    print(f"{len(TEXTUREN) + 1} Texturen, {len(BLOECKE)} Blöcke geschrieben")


if __name__ == "__main__":
    main()
