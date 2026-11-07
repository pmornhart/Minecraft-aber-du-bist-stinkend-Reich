#!/usr/bin/env python3
"""Erzeugt die Texturen der Show-Tänzerinnen im Geheimclub (vier Kostüme) und ihre Texte.

Textur (64x64) im Layout einer Spieler-Haut mit schmalen Armen (3 Pixel) und beiden Schichten (tools/assets/
spielerhaut.py): so sehen die Tänzerinnen auch als Schaufensterpuppe (Server ohne Mod) vollständig aus. Die
zweite Schicht gibt den langen Haaren (Hut-Schicht und Rücken der Jacke) und dem Glitzer-Top Tiefe. In freien Ecken
liegen die Zusatzteile des eigenen Modells (TaenzerinModell): Oberteil vorn (24,0) und Federschmuck (56,16).
Kostüm: zweiteiliges Glitzer-Showkostüm im Stil einer Las-Vegas-Revue (etwa so viel wie ein Bikini, nicht
freizügiger: das Repo ist öffentlich und Minecraft spielen auch Kinder), Netzstrümpfe, Pumps in Kostümfarbe.

Aufruf: python3 tools/assets/taenzerin.py
"""
from __future__ import annotations

import random
import sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
from spielerhaut import (AERMEL_R, ARM_R, BEIN_R, HOSE_R, HUT, JACKE, KOERPER, KOPF,  # noqa: E402
                         linke_seite, wuerfel)

WURZEL = Path(__file__).resolve().parents[2]
ASSETS = WURZEL / "src/main/resources/assets/stinkendreich"

# (Haut, Haar, Kostüm, Glitzer, Augen, Feder) je Kostüm
KOSTUEME = [
    ((240, 202, 172), (236, 204, 104), (214, 168, 40), (255, 238, 150), (70, 120, 200), (255, 250, 236)),   # Gold
    ((214, 164, 124), (34, 28, 34), (226, 54, 146), (255, 168, 220), (90, 60, 40), (255, 120, 200)),        # Pink
    ((184, 128, 88), (112, 62, 30), (30, 190, 196), (176, 255, 248), (60, 140, 90), (120, 240, 240)),       # Türkis
    ((118, 78, 54), (128, 54, 160), (196, 198, 212), (255, 255, 255), (60, 40, 30), (190, 120, 240)),       # Silber
]
LIPPEN = (196, 40, 70)
WIMPERN = (24, 18, 22)

TEXTE_DE = {"entity.stinkendreich.taenzerin": "Show-Tänzerin"}
TEXTE_EN = {"entity.stinkendreich.taenzerin": "Show Dancer"}


def dunkler(farbe, faktor=0.82):
    return tuple(int(c * faktor) for c in farbe[:3]) + (255,)


def heller(farbe, faktor=0.25):
    return tuple(int(c + (255 - c) * faktor) for c in farbe[:3]) + (255,)


def kostuem_bild(nr: int) -> Image.Image:
    haut, haar, stoff, glitzer, augen, feder = KOSTUEME[nr]
    zufall = random.Random(100 + nr)
    bild = Image.new("RGBA", (64, 64), (0, 0, 0, 0))

    def paillette(x, y):
        """Pailletten: Grundfarbe, dazwischen helle Glitzerpunkte."""
        r = zufall.random()
        return glitzer if r < 0.22 else (dunkler(stoff, 0.86) if r < 0.4 else stoff)

    def straehne(x, y):
        """Haar mit hellen und dunklen Strähnen (senkrecht, wie gekämmt)."""
        return heller(haar, 0.18) if x % 3 == 1 else (dunkler(haar, 0.86) if (x + y) % 5 == 0 else haar)

    # Kopf: Haut, Haar oben und hinten; Gesicht mit Wimpern, Augen, Rouge, Lippen
    def kopf(seite, x, y, w, h):
        if seite == "oben":
            return haar
        if seite == "unten":
            return dunkler(haut)
        if seite == "hinten":
            return straehne(x, y)
        if seite in ("rechts", "links"):
            hinten = (seite == "rechts" and x < 4) or (seite == "links" and x > 3)
            return straehne(x, y) if y < 2 or hinten else haut
        if y < 1 or (x in (0, 7) and y < 6):
            return haar
        if y == 3 and x in (1, 2, 5, 6):
            return WIMPERN
        if y == 4 and x in (1, 6):
            return (250, 250, 250)
        if y == 4 and x in (2, 5):
            return augen
        if y == 5 and x in (1, 6):
            return (236, 150, 150)                        # Rouge
        if y == 6 and x in (3, 4):
            return LIPPEN
        if y == 6 and x in (2, 5):
            return dunkler(LIPPEN, 0.9)
        return haut
    wuerfel(bild, *KOPF, 8, 8, 8, kopf)

    # Haar-Schicht: Volumen oben, Pony mit Seitenscheitel, lange Strähnen neben dem Gesicht, hinten bis zum Nacken
    def haarschicht(seite, x, y, w, h):
        if seite == "unten":
            return None
        if seite in ("oben", "hinten"):
            return straehne(x, y)
        if seite in ("rechts", "links"):
            hinten = (seite == "rechts" and x < 5) or (seite == "links" and x > 2)
            return straehne(x, y) if hinten or y < 3 else None
        # vorn: Pony schräg (Seitenscheitel), Strähnen links und rechts bis zum Kinn
        if y == 0 or (y == 1 and x not in (3, 4, 5)) or (y == 2 and x in (0, 1, 6, 7)):
            return straehne(x, y)
        if x in (0, 7):
            return straehne(x, y)
        return None
    wuerfel(bild, *HUT, 8, 8, 8, haarschicht)

    # Körper: Haut, Träger, Top, Bauch frei, Glitzergürtel, Höschen
    def koerper(seite, x, y, w, h):
        if seite == "oben":
            return haut
        if seite == "unten":
            return stoff
        if y < 3:
            if seite in ("vorn", "hinten") and x in (1, 6):
                return glitzer                            # Träger
            return haut
        if y < 6:
            return paillette(x, y)                        # Top
        if y < 9:
            if seite == "vorn" and y == 7 and x in (3, 4):
                return dunkler(haut, 0.9)                 # Bauchnabel
            return haut                                   # Bauch frei
        if y == 9:
            return glitzer                                # Glitzergürtel
        return paillette(x, y)                            # Höschen
    wuerfel(bild, *KOERPER, 8, 12, 4, koerper)

    # Jacken-Schicht: das Top steht vorn etwas ab (Oberweite, ganz bedeckt), Gürtel, hinten fallen die Haare
    def jacke(seite, x, y, w, h):
        if seite in ("oben", "unten"):
            return None
        if y == 9:
            return glitzer if (x + nr) % 2 else heller(glitzer, 0.4)
        if seite == "vorn":
            if y in (0, 1) and x in (1, 6):
                return glitzer                            # Träger
            if y == 2 and x in (1, 2, 5, 6):
                return paillette(x, y)
            if y in (3, 4, 5):
                if x in (3, 4) and y == 3:
                    return dunkler(stoff, 0.7)            # Naht in der Mitte
                return heller(stoff, 0.15) if (y == 3 and x in (1, 6)) else paillette(x, y)
            if y == 6 and 1 <= x <= 6:
                return glitzer                            # Glitzerkante unten
            return None
        if seite == "hinten":
            if y < 5 and 1 <= x <= 6 or y == 5 and 2 <= x <= 5:
                return straehne(x, y)                      # lange Haare über den Rücken
            if y in (3, 4):
                return paillette(x, y)
            return None
        if y in (3, 4, 5):
            return paillette(x, y)                        # Top an den Seiten
        return None
    wuerfel(bild, *JACKE, 8, 12, 4, jacke)

    # Arm (schmal): Haut, Armreif, Glitzerhandschuh; Ärmel-Schicht: Armreif und Handschuhstulpe
    def arm(seite, x, y, w, h):
        if seite == "oben":
            return haut
        if seite == "unten":
            return stoff
        if y == 2:
            return glitzer
        if y >= 10:
            return paillette(x, y)
        return haut if (x + y) % 7 else dunkler(haut, 0.95)
    wuerfel(bild, *ARM_R, 3, 12, 4, arm)

    def aermel(seite, x, y, w, h):
        if seite in ("oben", "unten"):
            return None
        if y == 2:
            return heller(glitzer, 0.3)
        if y == 9:
            return glitzer
        return None
    wuerfel(bild, *AERMEL_R, 3, 12, 4, aermel)

    # Bein: Saum des Höschens, Netzstrumpf, Pumps; Hosen-Schicht: Pumps mit Glanz
    def bein(seite, x, y, w, h):
        if seite == "oben":
            return stoff
        if seite == "unten":
            return dunkler(stoff, 0.6)
        if y < 1:
            return paillette(x, y)
        if y >= 10:
            return dunkler(stoff, 0.75) if y == 11 else stoff
        if (x + y) % 2 == 0:
            return dunkler(haut, 0.86)                    # Netzstrumpf
        return haut
    wuerfel(bild, *BEIN_R, 4, 12, 4, bein)

    def pumps(seite, x, y, w, h):
        if seite in ("oben", "unten") or y < 10:
            return None
        if seite == "vorn" and y == 10 and x in (1, 2):
            return heller(stoff, 0.45)                    # Glanz
        return stoff if y == 10 else dunkler(stoff, 0.75)
    wuerfel(bild, *HOSE_R, 4, 12, 4, pumps)
    linke_seite(bild, schmal=True)

    # Zusatzteile des eigenen Modells: Top vorn (6 × 3 × 1) und Federschmuck (2 × 5 × 1)
    wuerfel(bild, 24, 0, 6, 3, 1, lambda s, x, y, w, h: stoff if s == "oben" else paillette(x, y))
    wuerfel(bild, 56, 16, 2, 5, 1, lambda s, x, y, w, h: glitzer if y == 4 else (feder if (x + y) % 3 else dunkler(feder, 0.9)))
    return bild


def main() -> dict:
    ordner = ASSETS / "textures/entity"
    ordner.mkdir(parents=True, exist_ok=True)
    for nr in range(len(KOSTUEME)):
        kostuem_bild(nr).save(ordner / f"taenzerin_{nr}.png")
    return {"de": TEXTE_DE, "en": TEXTE_EN}


if __name__ == "__main__":
    main()
