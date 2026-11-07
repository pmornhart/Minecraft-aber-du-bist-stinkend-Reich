#!/usr/bin/env python3
"""Eigener Entwurf der Villa im Stil einer modernen Hochsicherheits-Villa.

Erzeugt src/main/resources/data/stinkendreich/structure/villa/villa.nbt und
villa.json (Markierungen für die Mod: Spawnpunkt, Aufzug, Etagen, Auto, Butler).

Aufbau der Strukturdatei (112 x 56 x 112):
  y = G (25)          Geländeoberkante (Grasschicht); darüber Haus, Garten, Mauern
  y = 4 bis 18        Untergrundbasis (Bunker) unter dem Haus
  Grundstück          äußerer Zaun, Lava-Kanal unter Glas, Wassergraben, Innenmauer, Tor im Süden

Aufruf:  python3 tools/villa/villa.py [--vorschau]
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

from bau import Bau, RICHTUNG_VEKTOR, basisname, eigenschaften, ist_voll, zustand
from moebel import (badewanne, kingsize_bett, reihe, strandliege, aquarium, baum, bett, bilderrahmen, blumenbeet, brunnen_kuppel, deko, fernbedienung, doppeltuer, fass, gemaelde,
                    hecke, kronleuchter, laterne, pflanze, sofa, stuhl, tisch, truhe, tuer)

WURZEL = Path(__file__).resolve().parents[2]
ZIEL = WURZEL / "src/main/resources/data/stinkendreich/structure/villa"

# ---------------- Maße ----------------
GROESSE = (112, 56, 112)
G = 25                 # Grasschicht
EG = G                 # Erdgeschoss-Boden
OG = G + 6             # Obergeschoss-Boden
DACH = G + 12          # Dachplatte
PH_DACH = G + 17       # Dach des Penthouse
BUNKER = 5             # Bunker-Boden
UG = 15                # Boden des Untergeschosses (Nachtclub, Foyer, Tiefgarage)
UG_DECKE = 22          # Decke des Untergeschosses

# Haus
HX0, HX1, HZ0, HZ1 = 30, 79, 36, 67
# Aufzugsschacht (außen 4x4, innen 2x2), Tür nach Süden
AX0, AX1, AZ0, AZ1 = 56, 59, 37, 40
MAUER_OBEN = G + 8       # Krone der Innenmauer (Gitter, Lichter, Geschütze, Himmelslaser)
# Ab hier ist der Garagenboden über der Rampe offen (genug Kopfhöhe auch für die hohe G-Klasse)
RAMPE_OFFEN_AB = 55
# Rampe in die Tiefgarage (Fahrbahn x RAMPE_X0 … RAMPE_X1), mittig unter dem Garagentor
RAMPE_X0, RAMPE_X1 = 85, 88
# Haltestellen des Aufzugs: Name -> Höhe des Bodens
# Landeplatz: eigene Lage über dem Penthouse-Dach (sonst sah man die Markierungen von innen an der Decke)
LANDEPLATZ = PH_DACH + 1
AUFZUG_HALTE = {"bunker": BUNKER, "keller": UG, "eg": EG, "og": OG, "dach": DACH, "heli": LANDEPLATZ}

# Materialien
WEISS = "white_concrete"
SCHWARZ = "black_concrete"
GRAU = "gray_concrete"
HELLGRAU = "light_gray_concrete"
QUARZ = "smooth_quartz"
GLAS = "glass_pane"
HOLZBODEN = "dark_oak_planks"
LICHT = "sea_lantern"

b = Bau(*GROESSE)


def grundstueck() -> None:
    """Rasen, Zaun, Lava-Kanal, Wassergraben, Innenmauer."""
    n = GROESSE[0] - 1
    # Außenzaun mit Leuchtpfosten
    b.ring(1, 1, n - 1, n - 1, G + 1, QUARZ)
    b.ring(1, 1, n - 1, n - 1, G + 2, "iron_bars")
    for i in range(1, n, 11):
        for x, z in ((i, 1), (i, n - 1), (1, i), (n - 1, i)):
            b.setze(x, G + 1, z, QUARZ)
            b.setze(x, G + 2, z, QUARZ)
            b.setze(x, G + 3, z, LICHT)
    # Lava-Kanal unter begehbarem Glas; Seitenwände, damit nichts in Höhlen läuft
    for y in (G - 2, G - 1):
        b.ring(2, 2, n - 2, n - 2, y, "polished_blackstone")
    for k in (3, 4):
        b.ring(k, k, n - k, n - k, G - 2, "magma_block")
        b.ring(k, k, n - k, n - k, G - 1, "lava")
        b.ring(k, k, n - k, n - k, G, "glass")
    # Wassergraben, 5 breit, 3 tief, mit Seitenwänden und Lichtern am Grund
    for y in range(G - 4, G):
        b.ring(5, 5, n - 5, n - 5, y, "dark_prismarine")
        b.ring(11, 11, n - 11, n - 11, y, "dark_prismarine")
    for k in range(6, 11):
        b.ring(k, k, n - k, n - k, G - 4, "dark_prismarine")
        for y in range(G - 3, G):
            b.ring(k, k, n - k, n - k, y, "water")
        b.ring(k, k, n - k, n - k, G, "air")
    b.ring(5, 5, n - 5, n - 5, G, "polished_andesite")
    b.ring(11, 11, n - 11, n - 11, G, "polished_andesite")
    for i in range(8, n - 8, 7):
        for x, z in ((i, 8), (i, n - 8), (8, i), (n - 8, i)):
            b.setze(x, G - 4, z, LICHT)
    # Für die Mod: Bereich des Grabens (Kasten + Breite des Rings). Ist die Alarmanlage an und ein Bewohner im
    # Haus, wird aus dem Wasser Lava (siehe Graben.java).
    b.marke("graben", 6, G - 3, 6, n - 6, G - 1, n - 6, 5)
    # Innenmauer (MAUER_OBEN hoch): weiß mit schwarzem Abschluss und Sicherheitsgitter; auf der Krone sitzen die
    # Himmelslaser und Abwehrgeschütze der Festung (siehe festung())
    for y in range(G + 1, MAUER_OBEN - 1):
        b.ring(12, 12, n - 12, n - 12, y, WEISS, breite=2)
    b.ring(12, 12, n - 12, n - 12, MAUER_OBEN - 1, SCHWARZ, breite=2)
    b.ring(12, 12, n - 12, n - 12, MAUER_OBEN, "iron_bars")
    for i in range(12, n - 11, 9):
        for x, z in ((i, 12), (i, n - 12), (12, i), (n - 12, i)):
            b.setze(x, MAUER_OBEN, z, LICHT)


def tor_und_bruecke() -> None:
    """Tor im Süden, Brücke über Graben und Lava, roter Teppich bis zum Haus. Alles liegt symmetrisch auf der
    Achse der Haustür (x 54–55, Mitte 55,0)."""
    n = GROESSE[0] - 1
    x0, x1 = 50, 59
    # Öffnungen in Zaun und Mauer
    b.luft(x0 + 1, G + 1, n - 1, x1 - 1, G + 3, n - 1)
    b.luft(x0 + 1, G + 1, n - 13, x1 - 1, G + 5, n - 12)
    # Torpfeiler mit Lichtern
    for x in (x0, x1):
        b.quader(x, G + 1, n - 13, x, MAUER_OBEN, n - 12, SCHWARZ)
        b.setze(x, MAUER_OBEN + 1, n - 13, LICHT)
        b.setze(x, MAUER_OBEN + 1, n - 12, LICHT)
    b.marke("tor", x0 + 1, G + 1, n - 13, x1 - 1, G + 4, n - 12)
    # Brücke
    b.quader(x0, G, n - 12, x1, G, n - 1, QUARZ)
    for z in range(n - 11, n):
        for x in (x0, x1):
            b.setze(x, G + 1, z, zustand("quartz_slab", type="bottom", waterlogged="false"))
            b.setze(x, G + 2, z, GLAS)
    b.quader(x0 + 3, G, n - 12, x1 - 3, G, n - 1, "red_concrete")
    # Für die Mod: das Stück über dem Wassergraben ist eine Zugbrücke (Kasten x0 y0 z0 x1 y1 z1 mit Geländer, dann die
    # Richtung, in die sie einfährt: nach Norden unter das Tor). Siehe Zugbruecke.java.
    b.marke("bruecke", x0, G, n - 10, x1, G + 2, n - 6, 0, -1)
    # Teppich vom Tor (auch unter dem Torflügel, kein Gras) bis direkt an die Haustür
    b.quader(x0 + 3, G, HZ1 + 1, x1 - 3, G, n - 13, "red_concrete")
    b.quader(x0 + 1, G, HZ1 + 1, x0 + 2, G, n - 13, QUARZ)
    b.quader(x1 - 2, G, HZ1 + 1, x1 - 1, G, n - 13, QUARZ)
    for z in range(74, n - 14, 6):
        laterne(b, x0, G + 1, z)
        if z < 90:      # an der Abzweigung zur Garage steht keine Laterne im Weg
            laterne(b, x1, G + 1, z)


def kurve_innen(ex: int, ez: int, rx: int, rz: int, r: int) -> None:
    """Füllt die Innenecke zweier Fahrbahnen zu einem Viertelkreis aus. (ex, ez) ist der Eckpunkt zwischen den
    Blöcken, (rx, rz) zeigt von dort in die Ecke hinein (weg von beiden Fahrbahnen)."""
    mx, mz = ex + rx * r, ez + rz * r
    for i in range(r):
        for k in range(r):
            x = ex + (i if rx > 0 else -1 - i)
            z = ez + (k if rz > 0 else -1 - k)
            if (x + 0.5 - mx) ** 2 + (z + 0.5 - mz) ** 2 > r * r:
                b.setze(x, G, z, "polished_andesite")


def kurve_aussen(x: int, z: int, r: int) -> None:
    """Rundet die Außenecke einer Fahrbahn ab: (x, z) ist der Eckblock, die Fahrbahn liegt nordwestlich davon."""
    mx, mz = x + 1 - r, z + 1 - r
    for i in range(r):
        for k in range(r):
            if (x - i + 0.5 - mx) ** 2 + (z - k + 0.5 - mz) ** 2 > r * r:
                b.setze(x - i, G, z - k, "grass_block")


def vorplatz() -> None:
    """Zwei Glaskuppel-Brunnen, Beete, Zufahrt zur Garage."""
    brunnen_kuppel(b, 39, G, 84)
    brunnen_kuppel(b, 70, G, 84)
    blumenbeet(b, 35, G + 1, 72, 44, 76)
    blumenbeet(b, 65, G + 1, 72, 74, 76)
    # Zufahrt: vom Tor nach Osten und hinauf zur Garage, beide Kurven gerundet, Mittellinie bis zum Garagentor
    b.quader(59, G, 92, 91, G, 97, "polished_andesite")
    b.quader(82, G, 68, 91, G, 97, "polished_andesite")
    kurve_innen(59, 92, 1, -1, 5)       # Abzweigung vom Teppich: Ecke nördlich der Fahrbahn
    kurve_innen(82, 92, -1, -1, 5)      # Kurve nach Norden: Ecke nordwestlich
    kurve_aussen(91, 97, 4)             # Außenkurve im Südosten: Ecke abrunden, dort Rasen
    # Mittellinie: gerade nach Osten, im Bogen nach Norden, gerade bis zum Garagentor
    mx, mz, r = 80, 88, 7                       # Bogenmitte und Halbmesser; die Linie ist 2 Blöcke breit
    b.quader(60, G, 94, mx - 1, G, 95, "gray_concrete")
    b.quader(86, G, 68, 87, G, mz - 1, "gray_concrete")
    for x in range(mx, 88):
        for z in range(mz, 96):
            if r - 1 <= ((x + 0.5 - mx) ** 2 + (z + 0.5 - mz) ** 2) ** 0.5 < r + 1:
                b.setze(x, G, z, "gray_concrete")
    for z in (72, 80, 88):
        laterne(b, 92, G + 1, z)
    # Hecken entlang der Innenmauer
    hecke(b, 14, G + 1, 96, 49, 97, 2)
    hecke(b, 14, G + 1, 14, 15, 95, 2)


def garten() -> None:
    """Hinterhof mit Pool, Holzdeck, Liegen, Grill und Bäumen; Westgarten mit Bäumen."""
    # Holzdeck und Pool
    b.quader(36, G, 15, 74, G, 31, "stripped_dark_oak_wood")
    b.quader(40, G - 3, 18, 69, G - 1, 27, "white_concrete")
    b.quader(41, G - 2, 19, 68, G, 26, "water")
    for x in range(42, 68, 5):
        b.setze(x, G - 3, 22, LICHT)
        b.setze(x, G - 3, 23, LICHT)
    b.ring(40, 18, 69, 27, G, QUARZ)
    # Strandliegen mit Blick auf den Pool, dazwischen Sonnenschirme und Loungetische
    for x in range(42, 68, 5):
        strandliege(b, x, G + 1, 30, "north")
        deko(b, x + 2, G + 1, 30, "loungetisch")
        if x % 10 == 2:
            deko(b, x + 2, G + 1, 29, "sonnenschirm")
    # Grill-Ecke
    b.quader(76, G, 16, 84, G, 24, "polished_andesite")
    b.setze(78, G + 1, 17, zustand("campfire", lit="true", facing="south", signal_fire="false", waterlogged="false"))
    b.setze(77, G + 1, 17, zustand("smoker", facing="south", lit="false"))
    b.setze(79, G + 1, 17, zustand("smooth_quartz_slab", type="top", waterlogged="false"))
    b.marke("grill", 78, G + 1, 17)
    tisch(b, 78, G + 1, 21, 82, 22)
    reihe(b, 82, G + 1, 20, 5, "gartenbank", "south")                       # Teakbänke auf beiden Seiten
    reihe(b, 78, G + 1, 23, 5, "gartenbank", "north")
    # Bäume und Beete
    for x, z, art in ((20, 20, "oak"), (26, 30, "birch"), (20, 44, "oak"), (22, 60, "birch"), (19, 76, "oak"),
                      (88, 28, "oak"), (92, 40, "birch"), (30, 20, "birch"), (86, 18, "oak"), (28, 88, "birch")):
        baum(b, x, G + 1, z, art, hoehe=6 + (x + z) % 3)
    blumenbeet(b, 16, G + 1, 50, 26, 56)
    blumenbeet(b, 84, G + 1, 30, 94, 36)
    # Pavillon im Westgarten: Lounge unter einem Marmordach mit Goldkante. Weiße Sofas als U (nach Süden offen),
    # Marmortisch mit Sekt in der Mitte, Kronleuchter, Pflanzen zwischen den Säulen, Holzdeck mit Marmorrand
    for x, z in ((15, 64), (23, 64), (15, 72), (23, 72)):
        b.quader(x, G + 1, z, x, G + 4, z, "quartz_pillar")
    b.quader(14, G + 5, 63, 24, G + 5, 73, zustand("smooth_quartz_slab", type="bottom", waterlogged="false"))
    b.ring(14, 63, 24, 73, G + 5, "gold_block")
    b.quader(15, G, 64, 23, G, 72, QUARZ)
    b.quader(16, G, 65, 22, G, 71, "stripped_dark_oak_wood")
    reihe(b, 21, G + 1, 65, 5, "sofa_weiss", "south")                      # hinten, x 21 … 17
    reihe(b, 16, G + 1, 67, 3, "sofa_weiss", "east")                       # links, z 67 … 69
    reihe(b, 22, G + 1, 69, 3, "sofa_weiss", "west")                       # rechts, z 69 … 67
    tisch(b, 18, G + 1, 67, 20, 68, "smooth_quartz_slab")
    deko(b, 18, G + 2, 67, "champagner")
    deko(b, 20, G + 2, 68, "cocktail")
    kronleuchter(b, 19, G + 4, 68)
    for x, z in ((15, 66), (15, 70), (23, 66), (23, 70)):
        pflanze(b, x, G + 1, z, "potted_azalea_bush")


def tuerrahmen(x0: int, y0: int, z: int, x1: int, y1: int, gold: int | None = None) -> None:
    """Schwarzer Rahmen um eine Türöffnung in einer Glaswand (Pfosten links/rechts, Sturz oben),
    damit die Tür nicht frei im Glas steht. `gold`: Höhe einer goldenen Sturzleiste."""
    b.quader(x0, y0, z, x0, y1, z, SCHWARZ)
    b.quader(x1, y0, z, x1, y1, z, SCHWARZ)
    oben = y0 + 2 if gold is None else gold
    b.quader(x0 + 1, oben, z, x1 - 1, y1, z, SCHWARZ)
    if gold is not None:
        b.quader(x0, gold, z, x1, gold, z, "gold_block")


def huelle() -> None:
    """Außenwände, Decken, Dachplatten mit weitem Überstand, Glasfassade im Süden."""
    # Fundament und Böden
    b.quader(HX0, EG, HZ0, HX1, EG, HZ1, "polished_deepslate")
    b.quader(HX0, OG, HZ0, HX1, OG, HZ1, WEISS)
    b.quader(HX0, DACH, HZ0, HX1, DACH, HZ1, WEISS)
    # Wände bis zur Dachplatte
    b.waende(HX0, EG + 1, HZ0, HX1, DACH - 1, HZ1, SCHWARZ)
    # Innenraum leer räumen
    b.luft(HX0 + 1, EG + 1, HZ0 + 1, HX1 - 1, OG - 1, HZ1 - 1)
    b.luft(HX0 + 1, OG + 1, HZ0 + 1, HX1 - 1, DACH - 1, HZ1 - 1)
    # Graue Paneele an Ost-, West- und Nordseite
    for y in (EG + 1, EG + 5, OG + 1, OG + 5):
        for x in range(HX0, HX1 + 1):
            b.setze(x, y, HZ0, GRAU)
        for z in range(HZ0, HZ1 + 1):
            b.setze(HX0, y, z, GRAU)
            b.setze(HX1, y, z, GRAU)
    # Fensterbänder an Ost, West und Nord
    for y0 in (EG + 2, OG + 2):
        for z in range(HZ0 + 3, HZ1 - 2):
            if z % 7 not in (0, 1):
                b.quader(HX0, y0, z, HX0, y0 + 2, z, GLAS)
                b.quader(HX1, y0, z, HX1, y0 + 2, z, GLAS)
        for x in range(HX0 + 3, HX1 - 2):
            if x % 7 not in (0, 1) and not (AX0 - 1 <= x <= AX1 + 1):
                b.quader(x, y0, HZ0, x, y0 + 2, HZ0, GLAS)
    # Südfassade: Pfeiler alle 7 Blöcke, dazwischen Glas über die ganze Höhe
    pfeiler = list(range(HX0, HX1 + 1, 7)) + [HX1]
    for x in range(HX0, HX1 + 1):
        for y in list(range(EG + 1, OG)) + list(range(OG + 1, DACH)):
            b.setze(x, y, HZ1, SCHWARZ if x in pfeiler else GLAS)
    # Eingang: Öffnung für die automatische Schiebetür (siehe mechanismen())
    b.luft(54, EG + 1, HZ1, 55, EG + 3, HZ1)
    b.quader(54, EG, HZ1, 55, EG, HZ1, "red_wool")      # roter Teppich auch unter der Tür (vorher grauer Schiefer)
    b.marke("haustuer", 54, EG + 1, HZ1, 55, EG + 3, HZ1)
    tuerrahmen(53, EG + 1, HZ1, 56, OG - 1, gold=EG + 4)
    # Balkon über dem Eingang: Obergeschoss-Platte kragt 3 Blöcke aus
    b.quader(HX0, OG, HZ1 + 1, HX1, OG, HZ1 + 3, WEISS)
    for x in range(HX0, HX1 + 1):
        b.setze(x, OG + 1, HZ1 + 3, GLAS)
    for z in range(HZ1 + 1, HZ1 + 4):
        b.setze(HX0, OG + 1, z, GLAS)
        b.setze(HX1, OG + 1, z, GLAS)
    b.luft(54, OG + 1, HZ1, 55, OG + 2, HZ1)
    tuerrahmen(53, OG + 1, HZ1, 56, OG + 3)
    doppeltuer(b, 55, OG + 1, HZ1, "south", "stinkendreich:glastuer")      # Flügel bei x 55 und 54
    # Dachplatte mit großem Überstand (2 seitlich, 6 nach Süden)
    b.quader(HX0 - 2, DACH, HZ0 - 2, HX1 + 2, DACH, HZ1 + 6, WEISS)
    b.quader(HX0 - 2, DACH - 1, HZ1 + 6, HX1 + 2, DACH - 1, HZ1 + 6, WEISS)
    # Lichtleiste unter dem Überstand
    for x in range(HX0, HX1 + 1, 4):
        b.setze(x, DACH, HZ1 + 4, LICHT)


def treppe_und_aufzug() -> None:
    """Gerade Treppe im Westteil der Halle, gläserner Aufzugsschacht vom Bunker bis zum Penthouse."""
    for i in range(6):
        for x in (50, 51):
            b.setze(x, EG + 1 + i, 50 - i, zustand("dark_oak_stairs", facing="north", half="bottom", shape="straight", waterlogged="false"))
            for y in range(EG + 1, EG + 1 + i):
                b.setze(x, y, 50 - i, "dark_oak_planks")
    # Über jeder Stufe drei Blöcke Kopffreiheit (Decke des EG und Boden des OG). Vorher wurde nur der OG-Boden über
    # der ganzen Treppe ausgeschnitten: dabei verschwand die oberste Stufe und die EG-Decke blieb als Wand stehen.
    for i in range(6):
        b.luft(50, EG + 2 + i, 50 - i, 51, EG + 4 + i, 50 - i)
    for z in range(45, 51):
        b.setze(52, OG + 1, z, GLAS)
    b.setze(50, OG + 1, 51, GLAS)
    b.setze(51, OG + 1, 51, GLAS)
    # Aufzugsschacht: Glaswände, in den Ecken warm leuchtende Lichtsäulen, bis unter das Penthouse-Dach.
    # Darin fährt die gläserne Kabine (Entity, siehe AufzugKabine.java). Oben ragt der Kopf über das Dach.
    for y in range(BUNKER + 1, LANDEPLATZ + 4):
        for x in range(AX0, AX1 + 1):
            for z in range(AZ0, AZ1 + 1):
                rand = x in (AX0, AX1) or z in (AZ0, AZ1)
                ecke = x in (AX0, AX1) and z in (AZ0, AZ1)
                if ecke:
                    b.setze(x, y, z, zustand("ochre_froglight", axis="y"))
                elif rand:
                    b.setze(x, y, z, "glass")
                else:
                    b.setze(x, y, z, "air")
    b.quader(AX0, LANDEPLATZ + 4, AZ0, AX1, LANDEPLATZ + 4, AZ1, WEISS)
    # Haltestellen: Boden im Schacht (die Kabine nimmt ihn beim Durchfahren kurz heraus), Öffnung nach Süden
    for name, y in AUFZUG_HALTE.items():
        b.quader(AX0 + 1, y, AZ0 + 1, AX1 - 1, y, AZ1 - 1, "polished_deepslate")
        b.luft(AX0 + 1, y + 1, AZ1, AX1 - 1, y + 3, AZ1)
        b.setze(AX0 + 1, y + 4, AZ1, "gold_block")
        b.setze(AX1 - 1, y + 4, AZ1, "gold_block")
        b.marke("aufzug", name, AX0 + 2, y + 1, AZ1 + 1)


def konzertfluegel(x0: int, y: int, z0: int, tasten: str) -> None:
    """Konzertflügel (3 × 3, spielbar): großes Modell im mittleren Block, acht unsichtbare Teile drumherum, davor die
    Klavierbank. (x0, z0) ist die Ecke im Nordwesten; tasten = Seite mit der Klaviatur (north oder east)."""
    for x in range(x0, x0 + 3):
        for z in range(z0, z0 + 3):
            deko(b, x, y, z, "konzertfluegel" if (x, z) == (x0 + 1, z0 + 1) else "fluegel_teil", tasten)
    if tasten == "north":
        deko(b, x0 + 1, y, z0 - 1, "klavierbank", "south")
    else:
        deko(b, x0 + 3, y, z0 + 1, "klavierbank", "west")


def erdgeschoss() -> None:
    """Ballsaal (Halle), Wohnzimmer mit Aquarium und Kamin, Luxusküche mit Personalküche, Esszimmer."""
    y = EG + 1
    # --- Ballsaal: Marmor im Schachbrett, roter Teppich mit Goldkante, Säulen, Sektturm ---
    for x in range(50, 60):
        for z in range(HZ0 + 1, HZ1):
            b.setze(x, EG, z, QUARZ if (x + z) % 2 == 0 else "polished_blackstone")
    b.quader(54, EG, 61, 55, EG, HZ1 - 1, "red_wool")
    b.quader(53, EG, 61, 53, EG, HZ1 - 1, "gold_block")
    b.quader(56, EG, 61, 56, EG, HZ1 - 1, "gold_block")
    for z in (53, 57, 61):
        for x in (50, 59):
            b.setze(x, y, z, "chiseled_quartz_block")
            b.quader(x, y + 1, z, x, OG - 2, z, zustand("quartz_pillar", axis="y"))
            b.setze(x, OG - 1, z, "gold_block")
    b.setze(52, y, 64, "gold_block")
    deko(b, 52, y + 1, 64, "sektturm")
    pflanze(b, 51, y, HZ1 - 1, "potted_bamboo")
    pflanze(b, 58, y, HZ1 - 1, "potted_bamboo")
    # Kronleuchter: über dem Luftraum an Ketten, im Eingang unter der Decke
    for z in (52, 55, 58):
        for x in (54, 55):
            kronleuchter(b, x, DACH - 1, z, kette=3)
    for x in (54, 55):
        kronleuchter(b, x, OG - 1, 64)
    # Trennwand Wohnzimmer | Ballsaal mit Aquarium
    b.quader(49, y, HZ0 + 1, 49, OG - 1, HZ1 - 1, WEISS)
    aquarium(b, 47, y, 52, 49, OG - 1, 63)
    doppeltuer(b, 49, y, 65, "west", "stinkendreich:prunktuer")            # ins Wohnzimmer (Flügel bei z 65 und 64)

    # --- Wohnzimmer: Kamin mit Gemälde, Fernsehwand mit Goldrahmen, weiße Sofas, großer Teppich ---
    b.quader(HX0 + 1, EG, HZ0 + 1, 48, EG, HZ1 - 1, HOLZBODEN)
    b.quader(32, EG, 43, 41, EG, 57, "yellow_wool")
    b.quader(33, EG, 44, 40, EG, 56, "red_wool")
    b.quader(HX0 + 1, y + 1, 46, HX0 + 1, y + 3, 54, SCHWARZ)          # Fernseher
    b.quader(HX0 + 1, y + 4, 45, HX0 + 1, y + 4, 55, "gold_block")
    b.quader(HX0 + 1, y + 1, 45, HX0 + 1, y + 3, 45, "gold_block")
    b.quader(HX0 + 1, y + 1, 55, HX0 + 1, y + 3, 55, "gold_block")
    b.quader(HX0 + 1, y, 45, HX0 + 1, y, 55, zustand("dark_oak_slab", type="top", waterlogged="false"))
    # Sofalandschaft als U mit Blick auf den Fernseher (Mitte z = 50), Couchtisch in der Mitte
    reihe(b, 40, y, 54, 9, "sofa_weiss", "west")          # hinten: z 54 … 46
    reihe(b, 38, y, 44, 5, "sofa_weiss", "south")         # Nordseite: x 38 … 34
    reihe(b, 34, y, 56, 5, "sofa_weiss", "north")         # Südseite: x 34 … 38
    tisch(b, 35, y, 48, 36, 52)
    deko(b, 35, y + 1, 49, "champagner")
    deko(b, 36, y + 1, 51, "goldbarren", "west")
    # Kamin an der Nordwand, darüber das Gemälde „Villa bei Nacht“: Kamin (x 35–42), Feuer (37–40) und Bild
    # (37–40) haben dieselbe Mitte, damit alles symmetrisch sitzt
    b.quader(35, y, HZ0 + 1, 42, y + 4, HZ0 + 1, "polished_blackstone_bricks")
    b.luft(37, y, HZ0 + 1, 40, y + 1, HZ0 + 1)
    for x in (37, 38, 39, 40):
        b.setze(x, y, HZ0 + 1, zustand("campfire", lit="true", facing="south", signal_fire="false", waterlogged="false"))
    b.quader(37, y + 2, HZ0 + 1, 40, y + 2, HZ0 + 1, "iron_bars")
    b.quader(35, y + 4, HZ0 + 1, 42, y + 4, HZ0 + 1, "gold_block")
    gemaelde(b, 37, y + 3, HZ0 + 2, "south", "villa_nacht", 4, 2)
    # Bücherwand, Vitrine mit Pokalen
    b.quader(HX0 + 1, y, HZ0 + 1, HX0 + 1, y + 3, 42, "bookshelf")
    for i, z in enumerate((60, 62, 64)):
        b.setze(HX0 + 1, y, z, "polished_blackstone")
        deko(b, HX0 + 1, y + 1, z, "pokal", "east")
    pflanze(b, HX0 + 1, y, HZ1 - 1, "potted_fern")
    pflanze(b, 46, y, HZ0 + 1, "potted_azalea_bush")
    # Konzertflügel links hinter der Tür aus dem Ballsaal (Philip: „wenn du reingehst gleich links“), aber mitten im
    # freien Teil zwischen Sofas und Südfenster (x 39–41, z 61–63, Bank bei z 60): drei Blöcke Luft zum Fenster, sechs
    # zum Aquarium und zur Vitrine (Philip: „mehr in die Mitte, nicht so nah am Rand“)
    konzertfluegel(39, y, 61, "north")
    kronleuchter(b, 37, OG - 1, 50)
    kronleuchter(b, 42, OG - 1, 50)

    # --- Luxusküche: Marmor, weiße Fronten mit Gold, Kochinsel mit Barhockern ---
    b.quader(60, EG, HZ0 + 1, HX1 - 1, EG, HZ1 - 1, QUARZ)
    b.quader(60, y, HZ0 + 1, 60, OG - 1, 50, WEISS)
    b.luft(60, y, 44, 60, y + 2, 46)
    gemaelde(b, 59, y + 1, 47, "west", "goldrausch", 4, 3)
    deko(b, 62, y, HZ0 + 1, "kuehlschrank", "south")
    deko(b, 62, y + 1, HZ0 + 1, "kuehlschrank", "south")
    deko(b, 63, y, HZ0 + 1, "weinkuehlschrank", "south")
    deko(b, 63, y + 1, HZ0 + 1, "weinkuehlschrank", "south")
    for x in (64, 65, 67, 69, 70, 71, 72):
        b.setze(x, y, HZ0 + 1, "polished_blackstone")
    deko(b, 66, y, HZ0 + 1, "spuele", "south")
    deko(b, 68, y, HZ0 + 1, "herd", "south")
    deko(b, 68, OG - 2, HZ0 + 1, "dunstabzug", "south")
    deko(b, 70, y + 1, HZ0 + 1, "kaffeemaschine", "south")
    deko(b, 72, y + 1, HZ0 + 1, "cocktail")
    # Kochinsel mit Goldenden, Barhocker, Hängelampen
    b.quader(64, y, 43, 71, y, 44, "polished_blackstone")
    for x in (64, 71):
        b.quader(x, y, 43, x, y, 44, "gold_block")
    deko(b, 66, y + 1, 43, "champagner")
    deko(b, 69, y + 1, 44, "cocktail")
    for x in (65, 67, 69):
        deko(b, x, y, 46, "barhocker")
    for x in (65, 68, 70):
        b.setze(x, OG - 1, 43, zustand("chain", axis="y", waterlogged="false"))
        b.setze(x, OG - 2, 43, zustand("lantern", hanging="true", waterlogged="false"))
    # Personalküche hinter der Küche: hier kocht und schmilzt James (echte Öfen), Vorräte und Brennstoff
    b.quader(73, y, HZ0 + 1, 73, OG - 1, 44, WEISS)
    b.luft(73, y, 42, 73, y + 1, 42)                                        # nur die Tür (daneben bleibt die Wand zu)
    tuer(b, 73, y, 42, "east", "stinkendreich:prunktuer", "right")
    b.setze(75, y, HZ0 + 1, zustand("smoker", facing="south", lit="false"))
    b.setze(76, y, HZ0 + 1, zustand("furnace", facing="south", lit="false"))
    b.setze(77, y, HZ0 + 1, zustand("blast_furnace", facing="south", lit="false"))
    for x in (75, 76, 77):
        b.marke("ofen", x, y, HZ0 + 1)
    fass(b, 78, y, HZ0 + 3, "west", [("cooked_beef", 32), ("bread", 32), ("baked_potato", 32), ("golden_carrot", 16),
                                      ("cooked_salmon", 16), ("pumpkin_pie", 8)])
    fass(b, 78, y, HZ0 + 1, "south", [("coal", 64), ("coal", 64), ("charcoal", 32)])
    b.marke("vorrat", 78, y, HZ0 + 3)
    b.marke("brennstoff", 78, y, HZ0 + 1)
    b.setze(78, y, HZ0 + 5, zustand("water_cauldron", level="3"))
    # Südwand (vorher offen, so ergab die Trennwand keinen Sinn) und Glastür hinaus in den Garten zum Pool
    b.quader(73, y, 45, HX1 - 1, OG - 1, 45, WEISS)
    b.luft(74, y, HZ0, 74, y + 1, HZ0)
    tuer(b, 74, y, HZ0, "north", "stinkendreich:glastuer")
    b.setze(74, y + 2, HZ0, SCHWARZ)
    b.quader(73, G, HZ0 - 4, 75, G, HZ0 - 1, "stripped_dark_oak_wood")        # Holzweg bis zum Pooldeck

    # --- Esszimmer: lange Marmortafel mit Kerzen, zwei Kronleuchter, Bildnis von James ---
    tisch(b, 66, y, 56, 74, 58, "smooth_quartz_slab")
    for x in range(66, 75):
        reihe(b, x, y, 55, 1, "esszimmerstuhl", "south")
        reihe(b, x, y, 59, 1, "esszimmerstuhl", "north")
    for x in (67, 70, 73):
        b.setze(x, y + 1, 57, zustand("yellow_candle", candles="3", lit="true", waterlogged="false"))
    kronleuchter(b, 68, OG - 1, 57)
    kronleuchter(b, 72, OG - 1, 57)
    b.quader(65, EG, 54, 75, EG, 60, "red_wool")
    gemaelde(b, HX1 - 1, y + 1, 56, "west", "james", 2, 2)
    # Durchgang zur Garage: 2 breit, 3 hoch, durch Hauswand (x 79) und Garagenwand (x 80); in der Hauswand
    # sitzt die automatische Schiebetür (siehe mechanismen())
    b.luft(HX1, y, 60, HX1 + 1, y + 2, 61)
    b.quader(HX1, y + 3, 60, HX1, y + 3, 61, SCHWARZ)
    b.marke("garagentuer", HX1, y, 60, HX1, y + 2, 61)


def obergeschoss() -> None:
    """Galerie um den Luftraum des Ballsaals, Schlafzimmer mit Himmelbett, Bad mit Goldwanne,
    Schmucktresor, Überwachungsraum und Büro."""
    y = OG + 1
    # Galerie mit Luftraum über dem Ballsaal, Goldkante und Glasgeländer
    b.quader(50, OG, HZ0 + 1, 59, OG, HZ1 - 1, QUARZ)
    b.luft(50, OG, 45, 51, OG, 50)
    b.luft(53, OG, 51, 56, OG, 60)
    for x in range(52, 58):
        for z in (50, 61):
            b.setze(x, OG, z, "gold_block")
            b.setze(x, y, z, GLAS)
    for z in range(51, 61):
        for x in (52, 57):
            b.setze(x, OG, z, "gold_block")
            b.setze(x, y, z, GLAS)
    # Wände der Räume
    b.quader(49, y, HZ0 + 1, 49, DACH - 1, HZ1 - 1, WEISS)
    b.quader(60, y, HZ0 + 1, 60, DACH - 1, HZ1 - 1, WEISS)
    doppeltuer(b, 49, y, 63, "west", "stinkendreich:prunktuer")            # Gästezimmer (Flügel bei z 63 und 62)
    # Büro: Sicherheitstür mit Kartenleser auf beiden Seiten (nur mit Zugangskarte)
    b.luft(60, y, 58, 60, y + 1, 58)
    tuer(b, 60, y, 58, "east", "stinkendreich:sicherheitstuer")
    gemaelde(b, 50, y, 54, "east", "der_bonze", 2, 3)
    gemaelde(b, 59, y + 1, 52, "west", "yacht", 4, 2)

    # Gästezimmer (das Penthouse ist Philips eigenes Schlafzimmer): XXL-Bett mit Samt-Kopfteil und Nachttischen,
    # Bettbank, Fernseher gegenüber, Leseecke am Fenster, Kommode, Pflanzen, Kronleuchter
    b.quader(HX0 + 1, OG, 50, 48, OG, HZ1 - 1, HOLZBODEN)
    b.quader(HX0 + 1, y, 49, 48, DACH - 1, 49, WEISS)
    doppeltuer(b, 45, y, 49, "north", "stinkendreich:prunktuer")            # ins Bad (Flügel bei x 45 und 46)
    b.quader(HX0 + 1, OG, 54, 37, OG, 60, "white_wool")
    kingsize_bett(b, HX0 + 1, y, 58, 3, "west")
    for z in (56, 57, 58):
        b.quader(HX0, y, z, HX0, y + 2, z, zustand("stinkendreich:kopfteil", facing="east", waterlogged="false"))
    b.quader(HX0, y + 3, 55, HX0, y + 3, 59, "gold_block")
    for z in (55, 59):
        b.setze(HX0, y, z, "gold_block")
        b.setze(HX0, y + 1, z, "gold_block")
        b.setze(HX0, y + 2, z, "gold_block")
        deko(b, HX0 + 1, y, z, "nachttisch", "east")
        if z == 55:
            deko(b, HX0 + 1, y + 1, z, "tischlampe", "east")
        else:
            fernbedienung(b, HX0 + 1, y + 1, z, "east", 2)                  # für den Fernseher gegenüber
    reihe(b, HX0 + 4, y, 56, 3, "bettbank", "east")                         # z 56 … 58
    b.marke("fernseher", 48.85, y + 0.4, 57.5, 90.0)                        # gegenüber dem Bett
    # Leseecke am Fenster
    reihe(b, 40, y, 65, 4, "sofa_weiss", "north")                           # x 40 … 43
    deko(b, 41, y, 63, "loungetisch", "north")
    deko(b, 42, y, 63, "champagner", "north")
    # Kommode an der Wand zum Bad mit Lampen, Geld und Pokal
    for x in range(35, 41):
        deko(b, x, y, 50, "nachttisch", "south")
    deko(b, 35, y + 1, 50, "tischlampe", "south")
    deko(b, 40, y + 1, 50, "tischlampe", "south")
    deko(b, 37, y + 1, 50, "geldstapel", "south")
    deko(b, 38, y + 1, 50, "pokal", "south")
    gemaelde(b, 36, y + 2, 50, "south", "goldbarren", 2, 2)
    for x, z in ((HX0 + 1, 50), (HX0 + 1, HZ1 - 1), (47, HZ1 - 1), (47, 50)):
        pflanze(b, x, y, z, "potted_bamboo")
    kronleuchter(b, 36, DACH - 1, 57)
    kronleuchter(b, 42, DACH - 1, 63)

    # Bad: Marmor, goldene Badewanne, Regendusche, goldene Toilette, Doppelwaschbecken
    b.quader(HX0 + 1, OG, HZ0 + 1, 48, OG, 48, QUARZ)
    # Freistehende Badewanne der Mod (man kann sich hineinsetzen) auf einem Goldpodest, daneben Handtücher
    b.quader(HX0 + 1, OG, HZ0 + 1, HX0 + 5, OG, HZ0 + 5, "gold_block")
    for x in (HX0 + 2, HX0 + 4):
        badewanne(b, x, y, HZ0 + 4, "north")
    pflanze(b, HX0 + 1, y, HZ0 + 5, "potted_bamboo")
    pflanze(b, HX0 + 5, y, HZ0 + 5, "potted_bamboo")
    b.quader(HX0 + 12, y, HZ0 + 1, HX0 + 14, y + 3, HZ0 + 3, "glass")
    b.luft(HX0 + 13, y, HZ0 + 2, HX0 + 13, y + 2, HZ0 + 3)                # Glaskasten, vorn offen
    mech(HX0 + 13, y + 2, HZ0 + 2, "duschkopf", "south", {"an": "false", "wand": "false"})   # Regendusche
    mech(HX0 + 13, y + 1, HZ0 + 2, "duscharmatur", "south", {"an": "false"})
    deko(b, HX0 + 8, y, HZ0 + 1, "spuele", "south")
    deko(b, HX0 + 9, y, HZ0 + 1, "spuele", "south")
    deko(b, HX0 + 17, y, HZ0 + 1, "goldtoilette", "south")
    inhalt = [
        [("golden_apple", 16), ("cake", 1), ("cookie", 64), ("honey_bottle", 16)],
        [("experience_bottle", 64), ("enchanted_golden_apple", 4), ("totem_of_undying", 2)],
        [("diamond", 32), ("emerald", 64), ("gold_ingot", 64)],
        [("firework_rocket", 64), ("name_tag", 8), ("saddle", 2), ("lead", 8)],
        [("elytra", 1), ("trident", 1), ("shield", 1), ("spyglass", 1)],
        [("white_bed", 2), ("candle", 16), ("flower_pot", 8), ("painting", 8)],
        [("clock", 1), ("compass", 1), ("recovery_compass", 1), ("book", 16), ("writable_book", 4)],
    ]
    for z, sachen in zip(range(41, 48), inhalt):
        truhe(b, 48, y, z, "west", sachen)
    kronleuchter(b, 39, DACH - 1, 42)

    # Schmucktresor (Nordosten): außen weiß wie die anderen Räume, innen Gold; runde Tresortür zum Flur
    b.quader(61, OG, HZ0 + 1, 67, DACH - 1, 47, WEISS)
    b.luft(62, y, HZ0 + 2, 66, DACH - 2, 46)
    b.quader(62, OG, HZ0 + 2, 66, OG, 46, "gold_block")
    b.luft(63, y, 47, 64, y + 1, 47)
    b.marke("tresortuer", 63, y, 47, 64, y + 1, 47)
    b.quader(62, y + 2, 47, 65, y + 2, 47, "gold_block")
    b.quader(65, y, 47, 65, y + 1, 47, "gold_block")
    # Eingerichtet wie auf Philips Bild: Diamantberg in der hinteren Ecke, links zwei Stapel Tresore
    # (Tresor-Blöcke aus dem Spiel), rechts Truhen, Gold und Geld in der Mitte, Edelsteine in Rahmen
    for x, hoehe in ((62, 3), (63, 3), (64, 2), (65, 2), (66, 1)):
        b.quader(x, y, HZ0 + 2, x, y + hoehe - 1, HZ0 + 2, "diamond_block")
    for x, hoehe in ((62, 2), (63, 1), (64, 1)):
        b.quader(x, y, HZ0 + 3, x, y + hoehe - 1, HZ0 + 3, "diamond_block")
    b.setze(62, y, HZ0 + 4, "diamond_block")
    for z in (44, 45):
        for dy in (0, 1):
            b.setze(62, y + dy, z, zustand("vault", facing="east", ominous="true", vault_state="inactive"))
    for z in (40, 42, 44):
        truhe(b, 66, y, z, "west", [("diamond", 24), ("emerald", 48), ("gold_ingot", 64), ("golden_apple", 6)])
    b.quader(65, y, 45, 66, y, 46, "emerald_block")
    deko(b, 64, y, 42, "goldbarren", "south")
    deko(b, 64, y, 44, "geldstapel", "west")
    deko(b, 63, y, 41, "geldstapel", "south")
    for z, ding in ((41, "emerald"), (39, "diamond")):
        bilderrahmen(b, 62, y + 2, z, "east", ding)
    bilderrahmen(b, 62, y + 2, 45, "east", "nether_star")
    kronleuchter(b, 64, DACH - 2, 42)

    # Überwachungsraum (Nordost hinten)
    b.quader(68, OG, HZ0 + 1, HX1 - 1, OG, 50, "gray_concrete")
    b.quader(68, y, 51, HX1 - 1, DACH - 1, 51, WEISS)
    b.quader(67, y, 48, 67, DACH - 1, 50, WEISS)                           # Wand zum Vorraum des Tresors
    b.luft(70, y, 51, 70, y + 1, 51)
    tuer(b, 70, y, 51, "north", "stinkendreich:sicherheitstuer")
    b.quader(69, y + 1, HZ0 + 1, HX1 - 1, y + 3, HZ0 + 1, SCHWARZ)        # Monitorwand
    for x in range(69, HX1, 2):
        b.marke("monitor", x, y + 2, HZ0 + 1)
    tisch(b, 69, y, 40, HX1 - 1, 40, "polished_blackstone_slab")
    for x in (71, 74, 77):
        reihe(b, x, y, 42, 1, "gamingstuhl", "north")

    # Büro mit Sicherheitszentrale (Südosten): Chefschreibtisch mit drei Überwachungsmonitoren
    # (Rechtsklick zeigt alle Kameras der Villa), Geld, Pokal, Gemälde
    b.quader(61, OG, 52, HX1 - 1, OG, HZ1 - 1, HOLZBODEN)
    b.quader(66, y, 60, 72, y, 61, "polished_blackstone")
    b.quader(66, y, 60, 66, y, 61, "gold_block")
    b.quader(72, y, 60, 72, y, 61, "gold_block")
    # Drei flache Monitore, die äußeren schräg zum Platz; davor Tastatur und Maus
    for x, winkel in ((68, "rechts"), (69, "gerade"), (70, "links")):     # äußere zum Stuhl hin (von oben geprüft)
        mech(x, y + 1, 61, "monitor", "north", {"winkel": winkel})
    deko(b, 69, y + 1, 60, "tastatur", "north")
    reihe(b, 69, y, 59, 1, "gamingstuhl", "south")
    deko(b, 67, y + 1, 60, "geldstapel", "north")
    deko(b, 72, y + 1, 60, "pokal")
    b.quader(65, OG, 57, 73, OG, 59, "red_wool")
    b.quader(HX1 - 1, y, 53, HX1 - 1, y + 3, 64, "bookshelf")
    gemaelde(b, 72, y + 1, 52, "south", "privatjet", 3, 2)
    kronleuchter(b, 69, DACH - 1, 58)
    b.marke("sicherheitszentrale", 71, y + 1, 61)


def penthouse() -> None:
    """Privates Penthouse auf dem Dach (Schlafbereich): XXL-Kingsize-Bett, XXL-Fernseher, Hausbar, Lounge;
    draußen Dachgarten, Whirlpool und Glasgeländer."""
    x0, x1, z0, z1 = 42, 67, 37, 58
    y = DACH + 1
    b.waende(x0, y, z0, x1, PH_DACH - 1, z1, SCHWARZ)
    for x in range(x0 + 1, x1):
        for yy in range(y, PH_DACH):
            b.setze(x, yy, z1, GLAS)
    for z in range(z0 + 2, z1):
        for yy in range(y + 1, PH_DACH - 1):
            b.setze(x0, yy, z, GLAS)
            b.setze(x1, yy, z, GLAS)
    b.luft(x0 + 1, y, z0 + 1, x1 - 1, PH_DACH - 1, z1 - 1)
    b.quader(x0 + 1, DACH, z0 + 1, x1 - 1, DACH, z1 - 1, HOLZBODEN)
    b.quader(x0 - 2, PH_DACH, z0 - 2, x1 + 2, PH_DACH, z1 + 5, WEISS)
    for x in range(x0, x1 + 1, 4):
        b.setze(x, PH_DACH, z1 + 3, LICHT)
    b.luft(54, y, z1, 55, y + 1, z1)
    tuerrahmen(53, y, z1, 56, y + 2)
    doppeltuer(b, 55, y, z1, "south", "stinkendreich:glastuer")            # Flügel bei x 55 und 54
    # Hausbar an der Nordwand: Theke mit Goldkante, Flaschenregale, Barhocker
    b.quader(x0 + 2, y, z0 + 3, x0 + 10, y, z0 + 3, "polished_blackstone")
    b.quader(x0 + 2, y, z0 + 4, x0 + 10, y, z0 + 4, "gold_block")
    for x in range(x0 + 2, x0 + 11):
        deko(b, x, y, z0 + 1, "flaschenregal", "south")
        deko(b, x, y + 1, z0 + 1, "flaschenregal", "south")
    for x in range(x0 + 3, x0 + 11, 2):
        deko(b, x, y, z0 + 5, "barhocker")
    deko(b, x0 + 4, y + 1, z0 + 3, "cocktail")
    deko(b, x0 + 8, y + 1, z0 + 3, "champagner")
    # Konzertflügel zwischen Hausbar und Lounge, mitten im freien Teil (x 47–49, z 44–46), die Tasten zum Raum hin
    # (Bank bei x 50): vier Blöcke Luft zur Glaswand, der Weg vom Aufzug zur Terrassentür (x 53–58) bleibt frei
    # (Philip: „etwas mehr mittig im Raum“)
    konzertfluegel(x0 + 5, y, 44, "east")
    # XXL-Kingsize-Bett im Nordosten: ein Bett aus einem Stück (4 × 3 Blöcke, Satindecke, weißes Fell), Samt-
    # Kopfteil mit Goldrahmen in der Wand, Nachttische mit Lampen, Bettbank, lila Leuchtband im Boden
    kingsize_bett(b, 61, y, z0 + 1, 4, "north")
    for x in range(61, 65):
        b.quader(x, y, z0, x, y + 2, z0, zustand("stinkendreich:kopfteil", facing="south", waterlogged="false"))
    b.quader(60, y, z0, 60, y + 3, z0, "gold_block")
    b.quader(65, y, z0, 65, y + 3, z0, "gold_block")
    b.quader(61, y + 3, z0, 64, y + 3, z0, "gold_block")
    for x in (60, 65):
        deko(b, x, y, z0 + 1, "nachttisch", "south")
        deko(b, x, y + 1, z0 + 1, "tischlampe", "south")
    reihe(b, 64, y, z0 + 4, 4, "bettbank", "south")
    b.quader(60, DACH, z0 + 5, 65, DACH, z0 + 5, "crying_obsidian")
    kronleuchter(b, 62, PH_DACH - 1, z0 + 2)
    kronleuchter(b, 63, PH_DACH - 1, z0 + 2)
    # XXL-Fernseher an einer frei stehenden Wand gegenüber dem Bett (zeigt YouTube-Vorschau, siehe
    # FernseherEntity); die Wand trennt den Schlafbereich von der Lounge
    b.quader(59, y, 47, 66, PH_DACH - 1, 47, SCHWARZ)
    b.quader(59, y, 47, 66, y, 47, "gold_block")
    b.marke("fernseher", 63.0, y + 0.4, 46.85, 180.0)                       # mittig über dem Sofa-U (x 60 … 65)
    # Kino vor dem Fernseher: drei Samtsofas als U (hinten drei Plätze, links und rechts je zwei), in der Mitte
    # Popcorn- und Loungetisch
    reihe(b, 64, y, 43, 4, "sofa_samt", "south")                            # x 64 … 61
    reihe(b, 60, y, 44, 2, "sofa_samt", "east")                             # z 44 … 45
    reihe(b, 65, y, 45, 2, "sofa_samt", "west")                             # z 45 … 44
    deko(b, 62, y, 45, "kinotisch", "south")
    deko(b, 63, y, 45, "loungetisch", "south")
    fernbedienung(b, 63, y + 1, 45, "north", 6)                             # auf dem Loungetisch
    # Der Weg vom Aufzug (x 57–58) zur Terrassentür (x 54–55) bleibt frei: x 53 … 58 ohne Möbel
    # Lounge im Westen mit Blick über die Stadt: Sofas als U nach Süden offen, Marmortisch mit Sektturm, Fell
    b.quader(x0 + 2, DACH, 47, x0 + 9, DACH, 55, "white_wool")
    reihe(b, x0 + 9, y, 48, 8, "sofa_weiss", "south")      # hinten: x 51 … 44
    reihe(b, x0 + 2, y, 50, 5, "sofa_weiss", "east")       # links: z 50 … 54
    reihe(b, x0 + 9, y, 54, 5, "sofa_weiss", "west")       # rechts: z 54 … 50
    tisch(b, x0 + 5, y, 51, x0 + 6, 53)
    deko(b, x0 + 5, y + 1, 52, "sektturm")
    deko(b, x0 + 6, y + 1, 51, "champagner")
    deko(b, x0 + 6, y + 1, 53, "goldbarren", "south")
    for x, z in ((x0 + 1, z1 - 1), (x0 + 1, 47), (x0 + 10, z1 - 1)):
        pflanze(b, x, y, z, "potted_bamboo")
    kronleuchter(b, x0 + 5, PH_DACH - 1, 52)
    kronleuchter(b, 55, PH_DACH - 1, 50)
    # Ostecke hinter der Fernsehwand: Sofa mit Blick durch die Glaswand nach Osten, zwei Samtsessel, Tisch,
    # Lampen, Pflanzen, Gemälde auf der Rückseite der Fernsehwand
    b.quader(60, DACH, 49, 65, DACH, 56, "white_wool")
    reihe(b, 60, y, 50, 6, "sofa_weiss", "east")           # z 50 … 55
    for z in (50, 55):
        reihe(b, 65, y, z, 1, "sofa_samt", "west")
    tisch(b, 62, y, 51, 63, 54)
    deko(b, 62, y + 1, 52, "champagner")
    deko(b, 63, y + 1, 53, "geldstapel", "west")
    deko(b, 62, y + 1, 54, "cocktail")
    for z in (49, 56):
        deko(b, 60, y, z, "nachttisch", "east")
        deko(b, 60, y + 1, z, "tischlampe", "east")
    for x, z in ((66, 48), (66, z1 - 1), (59, z1 - 1)):
        pflanze(b, x, y, z, "potted_bamboo")
    gemaelde(b, 61, y + 1, 48, "south", "yacht", 4, 2)
    kronleuchter(b, 63, PH_DACH - 1, 52)
    # Glasgeländer um die Dachterrasse
    rx0, rx1, rz0, rz1 = HX0 - 2, HX1 + 2, HZ0 - 2, HZ1 + 6
    b.ring(rx0, rz0, rx1, rz1, y, GLAS)
    # Dachgarten-Beete und Whirlpool
    for (bx0, bz0, bx1, bz1) in ((HX0, HZ0, HX0 + 8, HZ0 + 6), (HX1 - 8, HZ1 - 2, HX1, HZ1 + 4), (HX0, HZ1 - 2, HX0 + 8, HZ1 + 4)):
        b.quader(bx0, y, bz0, bx1, y, bz1, "grass_block")
        b.ring(bx0, bz0, bx1, bz1, y, "polished_andesite")
        for x in range(bx0 + 1, bx1):
            for z in range(bz0 + 1, bz1):
                if (x + z) % 4 == 0:
                    b.setze(x, y + 1, z, zustand("azalea_leaves", persistent="true", distance="1", waterlogged="false"))
                elif (x + z) % 4 == 2:
                    b.setze(x, y + 1, z, ("poppy", "allium", "oxeye_daisy")[(x * z) % 3])
    b.quader(HX1 - 9, y, HZ0, HX1 - 1, y, HZ0 + 7, QUARZ)
    b.quader(HX1 - 8, y, HZ0 + 1, HX1 - 2, y, HZ0 + 6, "water")
    b.setze(HX1 - 5, DACH, HZ0 + 3, LICHT)
    b.marke("whirlpool", HX1 - 5, y, HZ0 + 3)
    deko(b, HX1 - 10, y, HZ0 + 2, "champagner")
    deko(b, HX1 - 10, y, HZ0 + 4, "cocktail")


def dachparty() -> None:
    """Party auf der Dachterrasse südlich vom Penthouse: Tanzfläche unter Lichterketten, DJ, Feuerstelle
    mit Lounge, Sonnenliegen am Whirlpool."""
    y = DACH + 1
    # Tanzfläche unter freiem Himmel, Lichterketten an vier Pfosten
    for x in range(50, 59):
        for z in range(64, 71):
            deko(b, x, DACH, z, "tanzflaeche")
    for x, z in ((48, 63), (60, 63), (48, 72), (60, 72)):
        b.quader(x, y, z, x, y + 3, z, "polished_blackstone_wall")
        b.setze(x, y + 4, z, LICHT)
    for z in (63, 72):
        b.quader(49, y + 3, z, 59, y + 3, z, zustand("chain", axis="x", waterlogged="false"))
        for x in range(50, 59, 2):
            b.setze(x, y + 2, z, zustand("lantern", hanging="true", waterlogged="false"))
    # DJ-Pult mit Lautsprechern am Südrand, Blick zur Tanzfläche
    b.setze(54, y, 72, zustand("stinkendreich:musikanlage", facing="north"))
    b.marke("dj_dach", 54, y, 72)
    for x in (52, 56):
        b.setze(x, y, 72, zustand("stinkendreich:lautsprecher", facing="north"))
        b.setze(x, y + 1, 72, zustand("stinkendreich:lautsprecher", facing="north"))
    # Feuerstelle mit Lounge (Westen)
    b.quader(41, DACH, 66, 45, DACH, 70, "polished_blackstone")
    b.setze(43, y, 68, zustand("campfire", lit="true", facing="north", signal_fire="false", waterlogged="false"))
    reihe(b, 45, y, 65, 5, "sofa_weiss", "south")                           # x 45 … 41
    reihe(b, 41, y, 71, 5, "sofa_weiss", "north")                           # x 41 … 45
    deko(b, 47, y, 67, "loungetisch", "west")
    deko(b, 47, y, 69, "champagner", "west")
    # Strandliegen auf der Ostseite mit Sonnenschirmen und Loungetischen
    for z in range(45, 58, 3):
        strandliege(b, HX1 - 2, y, z, "west")
        deko(b, HX1 - 2, y, z + 1, "sonnenschirm" if (z - 45) % 6 == 0 else "loungetisch")


def hubschrauberlandeplatz() -> None:
    """Landeplatz auf dem Penthouse-Dach: dunkle Fläche mit gelbem Kreis und weißem H, Randlichter,
    Windsack, Glasgeländer. Der Aufzug fährt bis hier hoch."""
    lp = LANDEPLATZ
    y = lp + 1
    cx, cz = 55, 50
    b.quader(40, lp, 35, 69, lp, 63, WEISS)
    b.quader(cx - 6, lp, cz - 6, cx + 6, lp, cz + 6, "gray_concrete")
    for dx in range(-6, 7):
        for dz in range(-6, 7):
            r = (dx * dx + dz * dz) ** 0.5
            if 4.6 <= r <= 5.6:
                b.setze(cx + dx, lp, cz + dz, "yellow_concrete")
    for dz in range(-3, 4):
        b.setze(cx - 2, lp, cz + dz, "white_concrete")
        b.setze(cx + 2, lp, cz + dz, "white_concrete")
    for dx in (-1, 0, 1):
        b.setze(cx + dx, lp, cz, "white_concrete")
    for d in range(-6, 7, 3):
        for (x, z) in ((cx + d, cz - 6), (cx + d, cz + 6), (cx - 6, cz + d), (cx + 6, cz + d)):
            b.setze(x, lp, z, zustand("verdant_froglight", axis="y"))
    b.marke("hubschrauber", cx + 0.5, y, cz + 0.5, 180.0)
    # Glasgeländer am Dachrand, Windsack an der Südostecke
    b.ring(40, 35, 69, 63, y, GLAS)
    b.quader(67, y, 61, 67, y + 4, 61, "polished_blackstone_wall")
    for i, farbe in enumerate(("orange_wool", "white_wool", "orange_wool")):
        b.setze(66 - i, y + 4, 61, farbe)


def poolparty() -> None:
    """Poolparty im Garten: Poolbar mit Barhockern, DJ, Flamingos, Wasserrutsche, bunte Lichter, Palmen."""
    y = G + 1
    # Poolbar am Westende
    b.quader(35, G, 17, 39, G, 28, "stripped_dark_oak_wood")
    b.quader(37, y, 19, 37, y, 26, "polished_blackstone")
    b.quader(36, y, 19, 36, y, 26, "gold_block")
    for z in range(19, 27):
        deko(b, 35, y, z, "flaschenregal", "east")
    for z in range(20, 27, 2):
        deko(b, 38, y, z, "barhocker", "west")                               # Blick zur Theke
    for z, ding in ((20, "cocktail"), (23, "champagner"), (25, "cocktail")):
        deko(b, 37, y + 1, z, ding)
    b.quader(35, y + 3, 18, 39, y + 3, 27, "white_wool")
    for (x, z) in ((35, 18), (39, 18), (35, 27), (39, 27)):
        b.quader(x, y, z, x, y + 2, z, "dark_oak_fence")
    deko(b, 37, y + 2, 18, "neon_bar", "south")
    # Flamingos im Pool
    for x, z, blick in ((47, 21, "east"), (58, 24, "west"), (63, 20, "north")):
        deko(b, x, G, z, "flamingo", blick, nass=True)
    # DJ am Nordrand des Pools
    b.setze(54, y, 16, zustand("stinkendreich:musikanlage", facing="south"))
    b.marke("dj_pool", 54, y, 16)
    for x in (51, 57):
        b.setze(x, y, 16, zustand("stinkendreich:lautsprecher", facing="south"))
        b.setze(x, y + 1, 16, zustand("stinkendreich:lautsprecher", facing="south"))
    # Wasserrutsche: Turm am Ostende (innen Leiter bis auf die Plattform), von dort eine lange Glasrinne in
    # Stufen über den Pool. Die Mod schiebt jeden, der in der Rinne liegt, kräftig bergab (siehe Rutsche.java).
    # Am Ende hält ein unsichtbarer Lichtblock das Wasser zurück (man selbst fällt hindurch in den Pool), so
    # läuft kein Wasser über die Poolabdeckung.
    oben = G + 7                                              # Plattform (man steht auf oben + 1)
    b.quader(70, G, 19, 73, oben, 22, "smooth_quartz")
    b.luft(71, G + 1, 20, 72, oben - 1, 21)                 # Schacht
    b.setze(72, oben, 20, "air")                             # Loch für die Leiter in der Plattform
    for yy in range(G + 1, oben + 1):
        b.setze(72, yy, 20, zustand("ladder", facing="west", waterlogged="false"))
    b.luft(71, G + 1, 19, 71, G + 2, 19)
    b.ring(70, 19, 73, 22, oben + 1, GLAS)                  # Geländer um die Plattform
    b.setze(70, oben + 1, 21, "air")                         # Einstieg in die Rinne
    # Fünf Stufen zu je zwei Blöcken; die unterste bleibt über der Poolabdeckung (G + 1)
    stufen = [(69, 68), (67, 66), (65, 64), (63, 62), (61, 60)]
    for i, (xa, xb) in enumerate(stufen):
        wasser = oben - i                                     # erste Stufe eine Stufe unter der Plattform
        for x in (xa, xb):
            b.setze(x, wasser - 1, 21, "light_blue_stained_glass")
            for dy in (0, 1):
                b.setze(x, wasser + dy, 20, "glass")
                b.setze(x, wasser + dy, 22, "glass")
            b.setze(x, wasser + 1, 21, "air")
        b.setze(xa, wasser, 21, zustand("water", level="0"))
        b.setze(xb, wasser, 21, zustand("water", level="1"))
        b.gewollter_fluss.update({(xa, wasser, 21), (xb, wasser, 21)})
    ende_x, ende_y = stufen[-1][1] - 1, oben - len(stufen) + 1
    b.setze(ende_x, ende_y, 21, zustand("light", level="12", waterlogged="false"))
    b.marke("rutsche", ende_x, ende_y - 1, 20, 69, oben + 1, 22, -1, 0)
    # Bunte Lichter an Pfosten rund um den Pool
    for x in range(40, 70, 6):
        for z in (17, 28):
            b.quader(x, y, z, x, y + 1, z, "polished_blackstone_wall")
            b.setze(x, y + 2, z, ("pearlescent_froglight", "verdant_froglight", "ochre_froglight")[(x // 6) % 3])
    # Palmen (Dschungelbäume) an den Ecken
    for x, z in ((33, 14), (75, 30)):
        baum(b, x, y, z, "jungle", hoehe=7)


def garage() -> None:
    """Garage östlich des Hauses: Einfahrt zur Rampe in die Tiefgarage, Werkstatt für James, Werkzeugwand."""
    x0, x1, z0, z1 = 80, 94, 46, 67
    b.quader(x0, EG, z0, x1, EG, z1, "polished_andesite")
    b.waende(x0, EG + 1, z0, x1, OG - 1, z1, GRAU)
    b.luft(x0 + 1, EG + 1, z0 + 1, x1 - 1, OG - 1, z1 - 1)
    b.quader(x0 - 1, OG, z0 - 1, x1 + 2, OG, z1 + 3, WEISS)
    for x in range(x0 + 2, x1 - 1, 3):
        b.setze(x, OG, z1 + 1, LICHT)
        b.setze(x, OG, 56, LICHT)
    b.luft(x0 + 2, EG + 1, z1, x1 - 2, EG + 4, z1)
    b.marke("garagentor", x0 + 2, EG + 1, z1, x1 - 2, EG + 4, z1)
    # Werkstatt an der Westwand: Werkbank, Amboss, Schmiedetisch, zwei Hochöfen und ein Kohlenfass für James
    y = EG + 1
    b.setze(x0 + 1, y, z0 + 1, "crafting_table")
    b.setze(x0 + 1, y, z0 + 2, zustand("anvil", facing="north"))
    b.setze(x0 + 1, y, z0 + 3, "smithing_table")
    for z in (z0 + 4, z0 + 5):
        b.setze(x0 + 1, y, z, zustand("blast_furnace", facing="east", lit="false"))
        b.marke("ofen", x0 + 1, y, z)
    fass(b, x0 + 1, y, z0 + 6, "east", [("coal", 64), ("coal", 64)])
    b.marke("brennstoff", x0 + 1, y, z0 + 6)
    # Werkzeugwand: Lochwand aus Fichtenholz, Werkzeuge in Rahmen, Licht darüber
    b.quader(x0 + 2, y, z0, x0 + 8, y + 3, z0, "stripped_spruce_wood")
    for i, ding in enumerate(("diamond_pickaxe", "diamond_axe", "diamond_shovel", "diamond_hoe", "shears",
                              "flint_and_steel", "diamond_sword")):
        bilderrahmen(b, x0 + 2 + i, y + 1 + i % 2, z0 + 1, "south", ding, unsichtbar=True)
    b.quader(x0 + 2, y + 3, z0 + 1, x0 + 8, y + 3, z0 + 1, zustand("spruce_trapdoor", facing="south", half="top",
                                                                    open="false", powered="false", waterlogged="false"))
    gemaelde(b, x0 + 1, y + 1, 55, "east", "bonzenauto", 3, 2)
    # Rampe in die Tiefgarage (Ostseite): Absturzsicherung am Rand der Grube
    for z in range(RAMPE_OFFEN_AB, z1 - 1):
        b.setze(RAMPE_X0 - 1, y, z, GLAS)
        b.setze(RAMPE_X1 + 1, y, z, GLAS)
    for x in range(RAMPE_X0 - 1, RAMPE_X1 + 2):
        b.setze(x, y, RAMPE_OFFEN_AB - 1, GLAS)
    # Durchgang ins Haus (Gegenstück in erdgeschoss())
    b.luft(x0, y, 60, x0, y + 2, 61)


def keller() -> None:
    """Untergeschoss zwischen Bunker und Erdgeschoss: Nachtclub (Westen) mit Treppe aus dem Garten,
    Foyer am Aufzug mit Weinlounge, Tiefgarage (Osten) mit Rampe aus der Garage und Drehteller."""
    y = UG + 1
    oben = UG_DECKE - 1
    # Hülle: fest von der Bunkerdecke bis unter das Erdgeschoss (sonst offene Höhlen und Erdreich)
    b.quader(29, UG - 1, 31, 97, EG - 1, 68, "polished_deepslate")

    # --- Nachtclub ---
    b.luft(31, y, 37, 53, oben, 65)
    b.quader(31, UG, 37, 53, UG, 65, "polished_blackstone")
    b.quader(31, UG_DECKE, 37, 53, UG_DECKE, 65, SCHWARZ)
    for x in (30, 54):
        b.quader(x, y, 36, x, oben, 66, SCHWARZ)
    for z in (36, 66):
        b.quader(30, y, z, 54, oben, z, SCHWARZ)
    # Leuchtband aus weinendem Obsidian (lila, leuchtet) rund um den Raum
    for x in (30, 54):
        b.quader(x, y + 3, 37, x, y + 3, 65, "crying_obsidian")
    for z in (36, 66):
        b.quader(31, y + 3, z, 53, y + 3, z, "crying_obsidian")
    # Deckenlichter in drei Farben
    lichter_farben = ("pearlescent_froglight", "verdant_froglight", "ochre_froglight")
    for i, x in enumerate(range(33, 53, 5)):
        for j, z in enumerate(range(39, 65, 5)):
            b.setze(x, UG_DECKE, z, zustand(lichter_farben[(i + j) % 3], axis="y"))
    # Unsichtbare Lichtquellen in Kopfhöhe: der Club bleibt dunkel-edel, aber man sieht genug
    for x in range(33, 53, 5):
        for z in range(40, 65, 6):
            if b.hole(x, y + 3, z) == "minecraft:air":
                b.setze(x, y + 3, z, zustand("light", level="10", waterlogged="false"))
    # Tanzfläche (leuchtet und wechselt die Farben), darüber Discokugeln
    for x in range(37, 46):
        for z in range(45, 54):
            deko(b, x, UG, z, "tanzflaeche")
    for x, z in ((39, 47), (43, 51), (41, 49), (39, 52), (44, 46)):
        deko(b, x, oben, z, "discokugel")
    # DJ-Pult auf dem Podest im Norden, Leuchtschrift CLUB
    b.quader(39, y, 37, 45, y, 39, "polished_blackstone_bricks")
    b.quader(40, y + 1, 39, 41, y + 1, 39, "polished_blackstone")
    b.quader(43, y + 1, 39, 44, y + 1, 39, "polished_blackstone")
    b.setze(42, y + 1, 39, zustand("stinkendreich:musikanlage", facing="south"))
    b.marke("dj_club", 42, y + 1, 39)
    for x in (39, 45):
        for dy in (1, 2, 3):
            b.setze(x, y + dy, 37, zustand("stinkendreich:lautsprecher", facing="south"))
    deko(b, 42, y + 4, 37, "neon_club", "south")
    # Bar an der Südwand: Theke, Flaschenregale, Barhocker, Leuchtschrift BAR
    b.quader(33, y, 62, 47, y, 62, "polished_blackstone")
    b.quader(33, y, 61, 47, y, 61, "gold_block")
    for x in range(34, 47):
        deko(b, x, y, 65, "flaschenregal", "north")
        deko(b, x, y + 1, 65, "flaschenregal", "north")
    for x in range(34, 47, 2):
        deko(b, x, y, 60, "barhocker", "south")                              # Blick zur Theke
    for x, ding in ((35, "cocktail"), (38, "champagner"), (41, "cocktail"), (44, "champagner"), (46, "cocktail")):
        deko(b, x, y + 1, 62, ding)
    deko(b, 40, y + 3, 65, "neon_bar", "north")
    # VIP-Lounge im Osten: Podest, weiße Sofas, Sektturm, Geld auf dem Tisch, Leuchtschrift VIP
    b.quader(48, y, 50, 53, y, 59, "polished_blackstone_bricks")
    b.quader(48, y + 1, 50, 48, y + 1, 54, zustand("chain", axis="z", waterlogged="false"))
    b.quader(48, y + 1, 56, 48, y + 1, 59, zustand("chain", axis="z", waterlogged="false"))
    reihe(b, 53, y + 1, 58, 8, "sofa_weiss", "west")       # z 58 … 51, ganz auf dem Podest
    tisch(b, 51, y + 1, 53, 51, 56)
    deko(b, 51, y + 2, 53, "sektturm")
    deko(b, 51, y + 2, 55, "geldstapel", "west")
    deko(b, 53, y + 3, 55, "neon_vip", "west")
    # Sitzecken im Westen
    sofa(b, 31, y, 39, 5, "east", farbe="schwarz")
    tisch(b, 33, y, 40, 33, 42, "smooth_quartz_slab")
    deko(b, 33, y + 1, 41, "champagner")
    sofa(b, 31, y, 53, 5, "east", farbe="schwarz")
    tisch(b, 33, y, 54, 33, 56, "smooth_quartz_slab")
    deko(b, 33, y + 1, 55, "cocktail")
    # Treppe aus dem Westgarten (offener Abgang mit Glasgeländer, dann Tunnel) bis zur Clubtür
    b.luft(30, y, 47, 30, y + 2, 48)
    b.quader(19, UG - 1, 46, 30, EG - 1, 49, "polished_deepslate")
    for i in range(10):
        x = 19 + i
        for z in (47, 48):
            b.setze(x, G - i, z, zustand("polished_blackstone_stairs", facing="west", half="bottom", shape="straight",
                                         waterlogged="false"))
            # Vorne offen bis in den Himmel, ab x = 24 unter der Tunneldecke
            b.luft(x, G - i + 1, z, x, G + 3 if x < 24 else G - 1, z)
    b.luft(29, y, 47, 30, y + 2, 48)
    b.quader(30, y + 2, 47, 30, y + 2, 48, SCHWARZ)
    doppeltuer(b, 30, y, 47, "east", "stinkendreich:clubtuer")              # in den Club
    for x in range(19, 24):
        b.setze(x, G + 1, 46, GLAS)
        b.setze(x, G + 1, 49, GLAS)
    b.setze(18, G + 1, 46, GLAS)
    b.setze(18, G + 1, 49, GLAS)
    for z in (46, 49):
        b.quader(24, G, z, 24, G + 3, z, SCHWARZ)
    b.quader(24, G + 3, 46, 24, G + 3, 49, SCHWARZ)
    deko(b, 24, G + 4, 47, "neon_club", "west")
    deko(b, 24, G + 4, 48, "neon_vip", "west")
    b.quader(24, G, 47, 29, G, 48, "polished_deepslate")
    b.marke("club_eingang", 18.5, G + 1, 48.0, 90.0)

    # --- Foyer am Aufzug und Weinlounge ---
    b.luft(55, y, 41, 60, oben, 48)
    b.quader(55, UG, 41, 60, UG, 48, QUARZ)
    b.luft(54, y, 44, 54, y + 2, 45)
    b.quader(54, y + 2, 44, 54, y + 2, 45, SCHWARZ)
    doppeltuer(b, 54, y, 45, "west", "stinkendreich:prunktuer")            # Tür in den Club
    b.luft(61, y, 44, 61, y + 2, 45)
    b.quader(54, y + 3, 44, 54, y + 3, 45, "gold_block")
    b.quader(61, y + 3, 44, 61, y + 3, 45, "gold_block")
    for x in range(55, 61, 2):
        b.setze(x, UG_DECKE, 44, LICHT)
    # Weinlounge (Weinkeller zum Probieren): links eine Wand aus Flaschenregalen (verdeckt auch das lila
    # Leuchtband des Clubs dahinter), rechts Weinkühlschränke, Probierfässer mit Hockern, Samtsofa am Ende
    b.luft(55, y, 50, 60, oben, 65)
    b.luft(57, y, 49, 58, y + 2, 49)
    b.quader(55, UG, 49, 60, UG, 65, HOLZBODEN)
    b.quader(55, y, 50, 55, oben, 65, "stripped_dark_oak_wood")
    for z in range(51, 65):
        for dy in (0, 1, 2):
            deko(b, 55, y + dy, z, "flaschenregal", "east")
        deko(b, 60, y, z, "weinkuehlschrank", "west")
        deko(b, 60, y + 1, z, "weinkuehlschrank", "west")
    deko(b, 57, y + 3, 48, "neon_wein", "north")                            # Leuchtschrift über dem Eingang
    for z in (53, 57):
        b.setze(58, y, z, zustand("barrel", facing="up", open="false"))
        b.setze(58, y + 1, z, zustand("red_candle", candles="3", lit="true", waterlogged="false"))
        for x, blick in ((57, "east"), (59, "west")):
            deko(b, x, y, z, "barhocker", blick)                             # Blick zum Fass
    reihe(b, 56, y, 64, 4, "sofa_samt", "north")            # x 56 … 59
    tisch(b, 57, y, 62, 58, 62, "dark_oak_slab")
    deko(b, 57, y + 1, 62, "champagner")
    deko(b, 58, y + 1, 62, "cocktail")
    kronleuchter(b, 57, oben, 55)
    kronleuchter(b, 58, oben, 61)
    kronleuchter(b, 57, oben, 45)

    # --- Tiefgarage ---
    b.luft(62, y, 32, 96, oben, 66)
    b.quader(62, UG, 32, 96, UG, 66, "gray_concrete")
    b.quader(62, UG_DECKE, 32, 96, UG_DECKE, 66, WEISS)
    for x in range(64, 96, 4):
        for z in range(34, 66, 4):
            b.setze(x, UG_DECKE, z, LICHT)
    for x in (61, 97):
        b.quader(x, y, 32, x, oben, 66, WEISS)
        b.quader(x, y + 2, 32, x, y + 2, 66, SCHWARZ)
    for z in (31, 67):
        b.quader(62, y, z, 96, oben, z, WEISS)
        b.quader(62, y + 2, z, 96, y + 2, z, SCHWARZ)
    b.luft(61, y, 44, 61, y + 1, 45)
    doppeltuer(b, 61, y, 44, "east", "stinkendreich:privattuer")           # nur Bewohner öffnen und schließen
    # Stellplätze mit weißen Linien: je vier im Süden (Blick nach Norden) und Norden (Blick nach Süden), dazu zwei
    # in der Bucht östlich der Rampe
    for x in (62, 67, 72, 77, 82):
        b.quader(x, UG, 59, x, UG, 66, "white_concrete")
        b.quader(x, UG, 32, x, UG, 39, "white_concrete")
    b.quader(91, UG, 58, 96, UG, 58, "white_concrete")
    b.quader(91, UG, 41, 96, UG, 41, "white_concrete")
    plaetze = ([(64.5 + i * 5, 63.0, 180.0) for i in range(4)] + [(93.5, 62.5, 180.0)]
               + [(64.5 + i * 5, 35.5, 0.0) for i in range(4)] + [(93.5, 36.0, 0.0)])
    for i, (px, pz, blick) in enumerate(plaetze):
        b.marke("stellplatz", px, y, pz, blick, i)
    b.marke("auto", plaetze[0][0], y, plaetze[0][1], plaetze[0][2])
    # Rampe aus der Garage, mittig unter dem Garagentor (x 85–88), daneben bleibt oben eine Fahrspur frei:
    # von z 65 (Garagenboden) bis z 46 (Tiefgarage), halbe Stufen
    for x in (RAMPE_X0 - 1, RAMPE_X1 + 1):
        b.quader(x, y, 47, x, EG - 1, 66, WEISS)
    for z in range(46, 66):
        h = 16 + (z - 46) * 0.5                       # Höhe der Fahrbahn
        voll = int(h) - 1
        b.quader(RAMPE_X0, UG, z, RAMPE_X1, voll, z, "polished_andesite")
        if h != int(h):
            b.quader(RAMPE_X0, int(h), z, RAMPE_X1, int(h), z, zustand("smooth_stone_slab", type="bottom", waterlogged="false"))
        frei_ab = int(h) + (1 if h != int(h) else 0)
        b.luft(RAMPE_X0, frei_ab, z, RAMPE_X1, EG - 1 if z < RAMPE_OFFEN_AB else OG - 1, z)
        if z >= RAMPE_OFFEN_AB and h < EG:
            b.quader(RAMPE_X0, EG, z, RAMPE_X1, EG, z, "air")
    mitte = (RAMPE_X0 + RAMPE_X1 + 1) / 2
    b.marke("rampe_oben", mitte, EG + 1, 66.5)
    # Drehteller am Fuß der Rampe: goldener Ring im Boden, Mitte für den Drehteller (Entity)
    for dx in range(-4, 5):
        for dz in range(-4, 5):
            r = (dx * dx + dz * dz) ** 0.5
            if 3.3 <= r <= 4.3:
                b.setze(RAMPE_X0 + 1 + dx, UG, 40 + dz, "gold_block")
            elif r < 3.3:
                b.setze(RAMPE_X0 + 1 + dx, UG, 40 + dz, "polished_andesite")
    b.marke("drehteller", RAMPE_X0 + 1.5, y, 40.5)


def club_ausbau() -> None:
    """Nachtclub größer (Philip, 26.10.): Der Club wächst nach Süden bis z 70, die Bar zieht an die neue Südwand –
    eine Glaswand zum Meeresaquarium, hinter der die Fische schwimmen. Wo die alte Bar stand, ist jetzt Platz für eine
    große Tanzfläche. Im Südosten führt ein Glastunnel ins VIP-Seerestaurant."""
    y = UG + 1
    oben = UG_DECKE - 1
    # Hülle für den neuen Teil (zum Gelände hin dicht), dann Raum z 66–70
    b.quader(29, UG - 1, 66, 55, EG - 1, 71, "polished_deepslate")
    b.luft(31, y, 60, 47, oben, 65)                                       # alte Bar weg
    b.luft(31, y, 66, 53, oben, 70)
    b.quader(31, UG, 60, 47, UG, 70, "polished_blackstone")
    b.quader(31, UG, 66, 53, UG, 70, "polished_blackstone")
    b.quader(31, UG_DECKE, 66, 53, UG_DECKE, 70, SCHWARZ)
    for x in (30, 54):
        b.quader(x, y, 66, x, oben, 70, SCHWARZ)
        b.quader(x, y + 3, 66, x, y + 3, 70, "crying_obsidian")
    # Geheimgang zum Geheimclub (Regal in der Westwand bei z 63–64) bleibt frei: Bar beginnt erst bei x 33
    # Südwand: Glas zum Aquarium (unten und oben schwarz)
    b.quader(30, UG, 71, 54, UG_DECKE, 71, SCHWARZ)
    b.quader(31, y, 71, 53, oben, 71, "glass")
    # Neue, längere Bar vor der Glaswand: Theke z 67/68, Barhocker davor mit Blick zur Theke, Flaschenregale seitlich
    b.quader(33, y, 68, 48, y, 68, "polished_blackstone")
    b.quader(33, y, 67, 48, y, 67, "gold_block")
    for x in range(33, 49, 2):
        deko(b, x, y, 66, "barhocker", "south")
    for x, ding in ((34, "cocktail"), (37, "champagner"), (40, "cocktail"), (43, "champagner"), (46, "cocktail"), (48, "sektturm")):
        deko(b, x, y + 1, 68, ding)
    # Flaschenregale nur noch an der Glaswand (z 70): Bei z 69 bleibt an beiden Enden ein Durchgang hinter die Theke,
    # damit Personal (und James) hineinkommt (Philip, 07.11.: „einen Eingang zur Bar, dass Mitarbeiter reingehen“)
    for dy in (0, 1):
        deko(b, 32, y + dy, 70, "flaschenregal", "east")
        deko(b, 49, y + dy, 70, "flaschenregal", "west")
    b.quader(33, y - 1, 69, 48, y - 1, 70, "dark_oak_planks")                # Holzboden hinter der Theke
    deko(b, 40, y + 3, 70, "neon_bar", "north")
    b.setze(41, oben, 69, LICHT)
    # Große Tanzfläche (x 35–47, z 42–58) mit mehr Discokugeln
    for x in range(35, 48):
        for z in range(42, 59):
            if b.hole(x, UG, z) is not None:
                deko(b, x, UG, z, "tanzflaeche")
    for x in range(35, 48):
        for z in range(42, 59):
            if (x, z) not in ((x2, z2) for x2 in range(41, 47) for z2 in range(43, 58)) and (x + z) % 4 == 0 and x % 3 == 0:
                if b.hole(x, oben, z) in (None, "minecraft:air"):
                    deko(b, x, oben, z, "discokugel")
    # Eingang zum VIP-Seerestaurant: Durchgang in der Glaswand bei x 50–51 mit Goldrahmen (Säulen x 49 und 52, Sturz),
    # roter Teppich vom Club bis in den Glastunnel, Bambus in Töpfen links und rechts, Leuchtschrift über dem Rahmen
    b.luft(50, y, 71, 51, y + 2, 71)
    b.quader(49, y, 71, 49, y + 3, 71, "gold_block")
    b.quader(52, y, 71, 52, y + 3, 71, "gold_block")
    b.quader(50, y + 3, 71, 51, y + 3, 71, "gold_block")
    for z in range(66, 72):
        b.quader(50, y, z, 51, y, z, "red_carpet")
    pflanze(b, 53, y, 70, "potted_bamboo")
    pflanze(b, 53, y, 66, "potted_bamboo")
    b.setze(52, oben, 68, LICHT)
    # VIP mittig über dem Durchgang (Philip, 07.11.: „das soll mittig sein“): Block bei x 50, Schild um einen halben
    # Block nach Osten versetzt, sitzt also genau über der Mitte zwischen x 50 und 51
    deko(b, 50, y + 3, 70, "neon_vip_mitte", "north")


def seerestaurant() -> None:
    """VIP-Seerestaurant unter dem Südgarten: ein großes Meeresaquarium (x 31–59, z 72–96) mit Sand, Seegras, Tang,
    Korallen und vielen Fischen; mitten darin eine Glaskuppel (Mitte x 46, z 85, Radius 7), in der man an einer
    Marmortafel speist, rundherum Wasser. Der Weg aus dem Club führt durch einen Glastunnel."""
    boden = UG                      # Kuppelboden auf Clubhöhe
    # Hülle und Wasser
    b.quader(30, boden - 2, 71, 60, UG_DECKE + 1, 97, "polished_deepslate")
    b.quader(31, boden, 72, 59, UG_DECKE, 96, "water")
    b.quader(31, boden - 1, 72, 59, boden - 1, 96, "sand")
    for x in range(33, 59, 4):
        for z in range(74, 96, 4):
            b.setze(x, boden - 1, z, LICHT)                           # Licht im Sand
    for x in range(33, 59, 5):
        for z in range(74, 96, 5):
            b.setze(x, UG_DECKE + 1, z, LICHT)                        # und von oben
    # Kuppel
    mx, mz, r = 46, 85, 7
    # Die untersten zwei Lagen stehen senkrecht (Abstand nur waagerecht gemessen): Bei einer reinen Halbkugel standen
    # in der ersten Lage über dem Boden einzelne Glasblöcke auf dem Marmor (Philips Bild vom 07.11.: „der stört“)
    for x in range(mx - r - 1, mx + r + 2):
        for z in range(mz - r - 1, mz + r + 2):
            for yy in range(boden, boden + r + 2):
                d = ((x - mx) ** 2 + max(0, yy - boden - 1) ** 2 + (z - mz) ** 2) ** 0.5
                if d <= r - 0.5:
                    b.setze(x, yy, z, QUARZ if yy == boden else "air")
                elif d <= r + 0.5:
                    b.setze(x, yy, z, "glass")
    # Glastunnel vom Club (z 71) bis in die Kuppel. In der Kuppel selbst kein Tunnelglas: Dort blieben sonst Glasblöcke
    # neben der Tafel stehen (Philips Bild vom 07.11.)
    def in_kuppel(x, yy, z):
        return ((x - mx) ** 2 + max(0, yy - boden - 1) ** 2 + (z - mz) ** 2) ** 0.5 <= r - 0.5
    for z in range(72, 81):
        for x in range(49, 53):
            for yy in range(boden, boden + 5):
                if in_kuppel(x, yy, z):
                    continue
                innen = 50 <= x <= 51 and boden + 1 <= yy <= boden + 3
                b.setze(x, yy, z, "air" if innen else (QUARZ if yy == boden and 50 <= x <= 51 else "glass"))
    # Goldring im Kuppelboden, Lichter, Tafel mit Stühlen, Sektturm
    for x in range(mx - 6, mx + 7):
        for z in range(mz - 6, mz + 7):
            d = ((x - mx) ** 2 + (z - mz) ** 2) ** 0.5
            if 5.0 <= d <= 5.8 and b.hole(x, boden, z) == "minecraft:smooth_quartz":
                b.setze(x, boden, z, "gold_block")
            elif d < 1.0:
                b.setze(x, boden, z, LICHT)
    y = boden + 1
    tisch(b, 44, y, 84, 48, 86, "smooth_quartz_slab")
    for x in range(44, 49):
        reihe(b, x, y, 83, 1, "esszimmerstuhl", "south")
        reihe(b, x, y, 87, 1, "esszimmerstuhl", "north")
    reihe(b, 43, y, 85, 1, "esszimmerstuhl", "east")
    reihe(b, 49, y, 85, 1, "esszimmerstuhl", "west")
    deko(b, 46, y + 1, 85, "sektturm")
    deko(b, 44, y + 1, 84, "champagner")
    deko(b, 48, y + 1, 86, "cocktail")
    for (x, z) in ((41, 85), (51, 85), (46, 80), (46, 90)):
        b.setze(x, y + 2, z, zustand("light", level="13", waterlogged="false"))
    kronleuchter(b, 46, boden + r - 1, 85)
    # Unterwasserwelt: Seegras, Tang, Korallenblöcke mit Korallen, Seegurken
    import random
    rnd = random.Random(84)
    korallen = ("brain", "tube", "fire", "horn", "bubble")
    for x in range(31, 60):
        for z in range(72, 97):
            if b.hole(x, boden, z) != "minecraft:water":
                continue
            w = rnd.random()
            if w < 0.22:
                b.setze(x, boden, z, zustand("seagrass"))
            elif w < 0.30:
                hoehe = rnd.randint(3, 6)
                for dy in range(hoehe):
                    ok = b.hole(x, boden + dy, z) == "minecraft:water"
                    if not ok:
                        break
                    letzte = dy == hoehe - 1 or b.hole(x, boden + dy + 1, z) != "minecraft:water"
                    b.setze(x, boden + dy, z, zustand("kelp", age="20") if letzte else "kelp_plant")
                    if letzte:
                        break
            elif w < 0.36:
                art = rnd.choice(korallen)
                b.setze(x, boden, z, f"{art}_coral_block")
                if b.hole(x, boden + 1, z) == "minecraft:water":
                    b.setze(x, boden + 1, z, zustand(f"{art}_coral" if rnd.random() < 0.5 else f"{art}_coral_fan",
                                                     waterlogged="true"))
            elif w < 0.39:
                b.setze(x, boden, z, zustand("sea_pickle", pickles=str(rnd.randint(2, 4)), waterlogged="true"))
    # Fische: bunte Tropenfische, Lachse, Kabeljau, Schildkröten, zwei Delfine und zwei Haie
    varianten = (117506305, 67764993, 50790401, 117899265, 185008129, 67371009, 235340288, 918529, 16778497, 50660352)
    for i in range(28):
        while True:
            x, z = rnd.uniform(32, 59), rnd.uniform(73, 96)
            if ((x - mx) ** 2 + (z - mz) ** 2) ** 0.5 > r + 1.5 and not (48 <= x <= 53 and z < 80):
                break
        art = "tropical_fish" if i < 18 else ("salmon" if i < 22 else ("cod" if i < 25 else "turtle"))
        daten = {"id": f"minecraft:{art}", "PersistenceRequired": ("b", 1), "Rotation": [rnd.uniform(0, 360), 0.0],
                 "Motion": [0.0, 0.0, 0.0], "Pos": [0.0, 0.0, 0.0]}
        if art == "tropical_fish":
            daten["Variant"] = varianten[i % len(varianten)]
        b.entity(x, boden + rnd.uniform(1.0, 5.5), z, daten)
    for (x, z, art) in ((35.5, 80.5, "minecraft:dolphin"), (56.5, 90.5, "minecraft:dolphin"),
                        (36.5, 92.5, "stinkendreich:hai"), (56.5, 77.5, "stinkendreich:hai")):
        b.entity(x, boden + 3.0, z, {"id": art, "PersistenceRequired": ("b", 1), "Rotation": [0.0, 0.0],
                                       "Motion": [0.0, 0.0, 0.0], "Pos": [0.0, 0.0, 0.0]})


def haibecken() -> None:
    """Haifischbecken im Wohnzimmer (x 42–45, z 44–56): unter einem Glasboden aus Haifallen-Platten schwimmen zwei
    Haie, von unten sieht man sie durch die Glasdecke über der Tanzfläche des Clubs. Der rote Knopf an der Wand
    (x 48, z 46) wirft einen frechen Gast hinein (Haifalle öffnet sich für ein paar Sekunden)."""
    x0, x1, z0, z1 = 42, 45, 44, 56
    b.quader(x0 - 1, UG_DECKE - 1, z0 - 1, x1 + 1, EG - 1, z1 + 1, "glass")    # Wände und Boden (Club-Decke) aus Glas
    b.quader(x0, UG_DECKE, z0, x1, EG - 1, z1, "water")
    b.quader(x0 - 1, EG, z0 - 1, x1 + 1, EG, z1 + 1, "gold_block")           # Goldrahmen im Wohnzimmerboden
    b.quader(x0, EG, z0, x1, EG, z1, "stinkendreich:haifalle")
    b.marke("haibecken", (x0 + x1 + 1) / 2, UG_DECKE, (z0 + z1 + 1) / 2)
    for (x, z) in ((43.5, 47.5), (44.5, 53.5)):
        b.entity(x, UG_DECKE + 0.2, z, {"id": "stinkendreich:hai", "PersistenceRequired": ("b", 1), "Rotation": [0.0, 0.0],
                                         "Motion": [0.0, 0.0, 0.0], "Pos": [0.0, 0.0, 0.0]})
    b.setze(48, EG + 1, 46, zustand("stinkendreich:haifalle_schalter", facing="west"))


def geheimclub() -> None:
    """Geheimer Club unter dem Westgarten: Eingang hinter einem Bücherregal an der Bar des Nachtclubs (Schalter im
    Regal), Treppe hinab, Show-Bühne mit Tanzstangen und Leuchtschrift, Samtnischen, Bar, Discokugel."""
    y = BUNKER + 1                                    # Boden des Clubs auf Höhe des Bunkerbodens
    # Gang hinter dem Regal und Treppe nach Westen hinab (z 63–64)
    b.quader(18, BUNKER - 1, 62, 29, UG + 4, 65, "polished_deepslate")
    b.luft(29, UG + 1, 63, 29, UG + 3, 64)
    for i in range(10):
        x, ys = 28 - i, UG - i
        b.luft(x, ys + 1, 63, x, ys + 3, 64)
        for z in (63, 64):
            b.setze(x, ys, z, zustand("polished_blackstone_stairs", facing="east", half="bottom", shape="straight",
                                      waterlogged="false"))
        if i % 3 == 1:
            b.setze(x, ys + 3, 62, zustand("pearlescent_froglight", axis="y"))
    # Raum: innen x 6–17, z 63–80, Höhe 6
    x0, x1, z0, z1 = 6, 17, 63, 80
    b.quader(x0 - 1, BUNKER - 1, z0 - 1, x1 + 1, y + 6, z1 + 1, "polished_deepslate")
    b.luft(x0, y, z0, x1, y + 5, z1)
    b.luft(x1 + 1, y, 63, x1 + 1, y + 2, 64)
    b.quader(x0, BUNKER, z0, x1, BUNKER, z1, "polished_blackstone")
    for x in range(x0, x1 + 1):
        b.setze(x, y + 3, z0 - 1, "crying_obsidian")
        b.setze(x, y + 3, z1 + 1, "crying_obsidian")
    for z in range(z0, z1 + 1):
        b.setze(x0 - 1, y + 3, z, "crying_obsidian")
        if z not in (63, 64):
            b.setze(x1 + 1, y + 3, z, "crying_obsidian")
    for x in range(x0 + 1, x1, 3):
        for z in range(z0 + 1, z1, 3):
            b.setze(x, y + 6, z, zustand("pearlescent_froglight", axis="y"))
    # Bühne im Westen: erhöht, leuchtender Rand, drei Tanzstangen bis zur Decke, Leuchtschrift SHOW
    b.quader(x0, y, 66, x0 + 3, y, 77, "polished_blackstone_bricks")
    for z in range(66, 78):
        deko(b, x0 + 3, y, z, "tanzflaeche")
    for z in (68, 71, 75):
        b.quader(x0 + 1, y + 1, z, x0 + 1, y + 5, z, zustand("stinkendreich:tanzstange", facing="north", waterlogged="false"))
    deko(b, x0, y + 4, 71, "neon_show", "east")
    deko(b, x0, y + 4, 72, "neon_vip", "east")
    for z in (66, 77):
        for dy in (1, 2):
            b.setze(x0, y + dy, z, zustand("stinkendreich:lautsprecher", facing="east"))
    deko(b, 11, y + 5, 71, "discokugel")
    deko(b, 11, y + 5, 75, "discokugel")
    # Samtnischen mit Blick auf die Bühne, Tische davor
    # Die Nische an der Bar beginnt bei z 67, so bleibt zwischen Barhockern (z 66) und Sofa ein Block frei
    for zl in (71, 78):
        reihe(b, 15, y, zl, 4, "sofa_samt", "west")
    tisch(b, 13, y, 69, 13, 70)
    tisch(b, 13, y, 76, 13, 77)
    deko(b, 13, y + 1, 69, "champagner")
    deko(b, 13, y + 1, 77, "cocktail")
    # Bar im Norden des Raums (bei z 63–64 liegt der Eingang): Theke, Flaschenregale, Hocker
    b.quader(10, y, 65, 15, y, 65, "polished_blackstone")
    b.quader(10, y, 64, 15, y, 64, "gold_block")
    for x in range(10, 16):
        deko(b, x, y, z0, "flaschenregal", "south")
        deko(b, x, y + 1, z0, "flaschenregal", "south")
    deko(b, 12, y + 1, 65, "cocktail")
    deko(b, 14, y + 1, 65, "champagner")
    for x in (11, 13, 15):
        deko(b, x, y, 66, "barhocker")
    deko(b, 12, y + 3, z0, "neon_bar", "south")
    # Show-Tänzerinnen (TaenzerinEntity): je eine an jeder Stange, drei laufen durch den Club und tanzen im Stehen.
    # „Art|Kostüm“; den Anker (Mitte der Stange bzw. des Laufbereichs) setzt die Figur beim ersten Tick selbst.
    for nr, z in enumerate((68, 71, 75)):
        b.entity(x0 + 1.5, y + 1, z + 0.5, {"id": "stinkendreich:taenzerin", "stinkendreich": f"stange|{nr}"})
    for nr, (x, z) in enumerate(((11.5, 70.5), (13.5, 73.5), (12.5, 79.5))):
        b.entity(x, y, z, {"id": "stinkendreich:taenzerin", "stinkendreich": f"laufen|{(nr + 3) % 4}"})


def bunker() -> None:
    """Untergrundbasis: Kommandozentrale, Panikraum, Waffenkammer, Haupttresor, Fluchttunnel."""
    x0, x1, z0, z1 = 36, 73, 30, 69
    y0, y1 = BUNKER, BUNKER + 7
    b.quader(x0 - 1, y0 - 1, z0 - 1, x1 + 1, y1 + 1, z1 + 1, "polished_deepslate")
    b.luft(x0 + 1, y0 + 1, z0 + 1, x1 - 1, y1 - 1, z1 - 1)
    b.quader(x0 + 1, y0, z0 + 1, x1 - 1, y0, z1 - 1, "polished_andesite")
    for x in range(x0 + 3, x1 - 1, 4):
        for z in range(z0 + 3, z1 - 1, 4):
            b.setze(x, y1, z, LICHT)
    y = y0 + 1
    # Flur entlang des Aufzugs (Nord-Süd)
    b.quader(54, y0, z0 + 1, 61, y0, z1 - 1, "gray_concrete")
    for z in range(z0 + 1, z1, 5):
        b.setze(54, y0, z, LICHT)
        b.setze(61, y0, z, LICHT)
    # Lager am Nordende des Flurs hinter dem Aufzug: Fässer mit Vorräten, Werkbank, Amboss, Braustand
    lager = [[("cooked_beef", 64), ("bread", 64)], [("golden_carrot", 64), ("baked_potato", 64)],
             [("torch", 64), ("lantern", 32)], [("iron_ingot", 64), ("gold_ingot", 64)],
             [("arrow", 64), ("tnt", 16)], [("oak_log", 64), ("cobblestone", 64)]]
    for x, inhalt in zip(range(55, 61), lager):
        fass(b, x, y, z0 + 1, "south", inhalt)
        fass(b, x, y + 1, z0 + 1, "south", [])
    b.setze(54, y, z0 + 3, "crafting_table")
    b.setze(54, y, z0 + 4, zustand("anvil", facing="east"))
    b.setze(61, y, z0 + 3, zustand("brewing_stand", has_bottle_0="false", has_bottle_1="false", has_bottle_2="false"))
    b.setze(61, y, z0 + 4, zustand("water_cauldron", level="3"))
    bunker_gang(y, y0, y1)
    # Trennwände
    b.quader(53, y, z0 + 1, 53, y1 - 1, z1 - 1, "deepslate_tiles")
    b.quader(62, y, z0 + 1, 62, y1 - 1, z1 - 1, "deepslate_tiles")
    b.quader(x0 + 1, y, 50, 52, y1 - 1, 50, "deepslate_tiles")
    b.quader(63, y, 50, x1 - 1, y1 - 1, 50, "deepslate_tiles")
    for (tx, tz) in ((53, 44), (53, 58), (62, 44), (62, 58)):
        b.luft(tx, y, tz, tx, y + 2, tz + 1)
    # Türen: Kommandozentrale (Banktür mit Kartenleser), Geldspeicher (Bunkertür mit Kartenleser); Waffenkammer und Panikraum
    # haben ihre Türen in mechanismen()
    b.quader(53, y + 2, 44, 53, y + 2, 45, "deepslate_tiles")
    doppeltuer(b, 53, y, 45, "west", "stinkendreich:banktuer")              # nur mit Zugangskarte (Kartenleser in mechanismen())
    b.quader(62, y, 59, 62, y + 2, 59, "deepslate_tiles")
    b.setze(62, y + 2, 58, "deepslate_tiles")
    tuer(b, 62, y, 58, "east", "stinkendreich:bunkertuer")
    kommandozentrale(y, y0, y1)
    # Waffenkammer (Nordost): sechs Rüstungsständer mit voll verzauberten Sets (die Mod rüstet sie beim
    # Platzieren aus, siehe Waffenkammer.java), davor goldene Podeste, Truhen mit Agenten-Waffen und Vorräten
    b.quader(63, y0, z0 + 1, x1 - 1, y0, 49, "polished_blackstone")              # dunkler Boden
    b.quader(x1 - 1, y, z0 + 1, x1 - 1, y1 - 1, 49, SCHWARZ)                   # Rückwand schwarz
    b.quader(x1 - 1, y1 - 1, z0 + 1, x1 - 1, y1 - 1, 49, "gold_block")          # Goldleiste oben
    for i, z in enumerate(range(z0 + 2, 49, 3)):
        b.setze(x1 - 2, y0, z, "gold_block")                                    # goldener Sockel im Boden
        b.setze(x1 - 2, y, z, "smooth_quartz_slab")                             # Podest
        b.setze(x1 - 2, y1, z, LICHT)                                           # Spot von oben
        b.setze(x1 - 1, y + 3, z, "gold_block")                                 # Goldplakette hinter dem Kopf
        b.marke("ruestung", x1 - 1.5, y + 0.5, z + 0.5, 90.0, i)
    vorraete = [
        [("stinkendreich:goldene_pistole", 1), ("stinkendreich:laseruhr", 1), ("stinkendreich:geldkanone", 1)],
        [("stinkendreich:bond_sonnenbrille", 1), ("stinkendreich:smoking", 1), ("stinkendreich:anzughose", 1),
         ("stinkendreich:lackschuhe", 1)],
        [("totem_of_undying", 1), ("totem_of_undying", 1), ("enchanted_golden_apple", 8), ("golden_apple", 32)],
        [("ender_pearl", 16), ("firework_rocket", 64), ("arrow", 64), ("spectral_arrow", 64),
         ("netherite_ingot", 16), ("diamond", 64), ("experience_bottle", 64), ("shield", 1)],
    ]
    # Vier Truhen; der Platz direkt neben dem ersten Ständer bleibt frei (verdeckte sonst den Agenten-Anzug)
    for x, inhalt in zip(range(63, x1 - 2, 2), vorraete):
        truhe(b, x, y, z0 + 1, "south", inhalt)
    bunker_suite(y, y0, y1)
    # Haupttresor (Südost) als Geldspeicher: Goldwand, ein Berg aus Goldmünzen, in den man hineinspringen kann
    # (eigener Block ohne feste Oberfläche, man sinkt weich ein, siehe Goldbad.java), Sprungbrett mit Leiter
    b.quader(63, y, 51, x1 - 1, y1 - 1, 51, "iron_block")
    b.quader(x1 - 1, y, 52, x1 - 1, y1 - 1, z1 - 1, "gold_block")
    # Münzgrube vier Lagen tief unter dem Boden, darüber ein flacher Münzberg (der Bunker ist nur 6 hoch)
    muenze = zustand("stinkendreich:goldmuenzen", facing="north", waterlogged="false")
    b.quader(62, y0 - 4, 55, x1, y0, z1, "polished_deepslate")
    b.quader(63, y0 - 3, 56, x1 - 2, y0, z1 - 1, muenze)
    mx, mz = 67.5, 62.5
    for x in range(63, x1 - 1):
        for z in range(56, z1):
            r = ((x - mx) ** 2 + ((z - mz) * 0.8) ** 2) ** 0.5
            for dy in range(max(0, int(3.5 - r * 0.7))):
                b.setze(x, y + dy, z, muenze)
    # Sprungbrett: Leiter hinauf auf die Plattform, Brett ragt über die Grube
    b.quader(66, y, 52, 68, y + 2, 52, "iron_block")
    b.quader(66, y + 3, 52, 68, y + 3, 53, "smooth_quartz")
    b.quader(67, y + 3, 54, 67, y + 3, 57, zustand("smooth_quartz_slab", type="top", waterlogged="false"))
    for x in (66, 68):
        b.setze(x, y + 4, 52, "iron_bars")
    # Leiter an der Eisensäule östlich davon (zeigt nach Westen, sonst hätte sie keinen Halt und fiele ab)
    b.quader(70, y, 53, 70, y + 3, 53, "iron_block")
    for yy in range(y, y + 4):
        b.setze(69, yy, 53, zustand("ladder", facing="west", waterlogged="false"))
    for x, z in ((64, 53), (71, 53), (64, 55)):
        deko(b, x, y, z, "geldstapel", "north")
    for x, z in ((63, 54), (71, 55)):
        deko(b, x, y, z, "goldbarren", "east")
    kronleuchter(b, 67, y1 - 1, 60)
    # Notausgang: Fluchttunnel nach Westen unter Graben und Lava-Kanal hindurch, Leiter hoch hinter den Kanal und
    # eine Privattür im Außenzaun (nur Bewohner) – so kommt man auch im totalen Lockdown hinaus. Rundum Tiefenschiefer,
    # damit kein Grundwasser aus Höhlen hereinläuft; grünes Notlicht
    b.quader(0, y0 - 1, 59, x0 - 1, y + 3, 62, "polished_deepslate")
    b.luft(x0 - 1, y, 60, 0, y + 2, 61)
    b.quader(x0 - 1, y0, 60, 0, y0, 61, "polished_andesite")
    for x in range(5, x0, 5):
        b.setze(x, y + 2, 60, zustand("verdant_froglight", axis="y"))
    # Weiter nach Westen gräbt die Mod beim Bau der Welt: 50 Blöcke unter dem Gelände bis zu einer versteckten
    # Falltür im Boden, die nur mit Zugangskarte aufgeht (Festungsbau.notausgang): Rand x, Höhe, z, Länge
    b.marke("notausgang", 0, y, 60, 50)
    # Notausgang-Schilder: über dem Geheimregal in der Suite und über der Panzertür im Gang
    deko(b, 37, y + 3, 61, "notausgang_links", "east")
    deko(b, 37, y + 3, 60, "notausgang_rechts", "east")
    deko(b, 54, y + 3, 59, "notausgang_links", "east")
    deko(b, 54, y + 3, 58, "notausgang_rechts", "east")


def kommandozentrale(y: int, y0: int, y1: int) -> None:
    """Kommandozentrale im Bunker (x 37–52, z 31–49), eingerichtet wie das Büro oben: Monitorwand mit zwölf Bildschirmen
    (zeigen alle Kameras), Chefschreibtisch mit drei Monitoren, Tastatur und der Sicherheitszentrale, an der allein sich
    die Festung (Stufe 4) einschalten lässt, Bedienplätze an den Seiten, Bücherwand, Kronleuchter. Eine Prunktür führt
    in die Bunker-Suite."""
    b.quader(37, y0, 31, 52, y0, 49, "polished_blackstone")
    b.quader(40, y0, 33, 50, y0, 47, "red_wool")
    b.quader(51, y0, 44, 52, y0, 45, "red_wool")                            # Läufer von der Banktür
    # Monitorwand: schwarz mit Goldrahmen, zwei Reihen Bildschirme
    b.quader(37, y, 30, 52, y1 - 2, 30, "gold_block")
    b.quader(38, y + 1, 30, 51, y1 - 2, 30, SCHWARZ)
    for x in range(39, 51, 2):
        for dy in (2, 4):
            b.marke("bunker_monitor", x, y + dy, 31)
    # Goldleiste oben an den Wänden (nicht über die Stahlrippen der Ostwand)
    b.quader(37, y1 - 1, 30, 52, y1 - 1, 30, "gold_block")
    b.quader(36, y1 - 1, 31, 36, y1 - 1, 49, "gold_block")
    for z in range(31, 50):
        if z not in (35, 47):
            b.setze(53, y1 - 1, z, "gold_block")
    # Chefschreibtisch mit Goldenden, drei Monitoren, Tastatur und der Zentrale
    b.quader(42, y, 36, 48, y, 37, "polished_blackstone")
    b.quader(42, y, 36, 42, y, 37, "gold_block")
    b.quader(48, y, 36, 48, y, 37, "gold_block")
    for x, winkel in ((44, "links"), (45, "gerade"), (46, "rechts")):     # äußere zum Stuhl hin (von oben geprüft)
        mech(x, y + 1, 36, "monitor", "south", {"winkel": winkel})
    deko(b, 45, y + 1, 37, "tastatur", "south")
    reihe(b, 45, y, 38, 1, "gamingstuhl", "north")
    deko(b, 43, y + 1, 37, "geldstapel", "south")
    deko(b, 48, y + 1, 37, "pokal", "south")
    b.marke("bunker_zentrale", 47, y + 1, 36)                               # Zentrale: siehe mechanismen()
    # Bedienplätze an der West- und Ostwand
    for z in (39, 41):
        b.setze(37, y, z, "polished_blackstone")
        mech(37, y + 1, z, "monitor", "east", {"winkel": "gerade"})
        reihe(b, 38, y, z, 1, "gamingstuhl", "west")
        b.setze(52, y, z - 3, "polished_blackstone")
        mech(52, y + 1, z - 3, "monitor", "west", {"winkel": "gerade"})
        reihe(b, 51, y, z - 3, 1, "gamingstuhl", "east")
    # Besprechungstisch aus Marmor mit Lederstühlen im hinteren Teil
    tisch(b, 41, y, 44, 47, 45, "polished_blackstone_slab")
    for x in (42, 44, 46):
        reihe(b, x, y, 43, 1, "esszimmerstuhl", "south")
        reihe(b, x, y, 46, 1, "esszimmerstuhl", "north")
    for z in (44, 45):
        reihe(b, 40, y, z, 1, "esszimmerstuhl", "east")
        reihe(b, 48, y, z, 1, "esszimmerstuhl", "west")
    kronleuchter(b, 44, y1 - 1, 45)
    # Bücherwand im Westen (bündig in der Wand), Kronleuchter über dem Schreibtisch
    b.quader(36, y, 43, 36, y + 3, 48, "bookshelf")
    gemaelde(b, 37, y + 1, 34, "east", "privatjet", 3, 2)
    kronleuchter(b, 45, y1 - 1, 40)
    for x, z in ((37, 31), (52, 49), (37, 49)):
        pflanze(b, x, y, z, "potted_bamboo")
    # Prunktür in die Bunker-Suite (Flügel bei x 52 und 51)
    doppeltuer(b, 52, y, 50, "south", "stinkendreich:prunktuer")


def festung() -> None:
    """Festung (Sicherheitsstufe 4): Abwehrgeschütze auf der Innenmauer und Landminen im Rasen. Beide sind nur in
    der Festung scharf und treffen nur Monster und Eindringlinge (siehe Festung.java)."""
    n = GROESSE[0] - 1
    # Mauerkrone rundherum abwechselnd: Abwehrgeschütz (Blick nach außen), Himmelslaser, Geschütz, Laser …
    # Die Laser sind dieselben roten Strahlen wie im Bunker und im Tresor, nur 40 Blöcke senkrecht nach oben
    # (ab Stufe 3 an, in der Festung tödlich); die Geschütze fahren nur in der Festung aus.
    nr = 0
    for k in range(14, n - 13, 4):
        for x, z, blick in ((k, 12, "north"), (k, n - 12, "south"), (12, k, "west"), (n - 12, k, "east")):
            if not basisname(b.hole(x, MAUER_OBEN, z) or "air").endswith(("iron_bars", "air")):
                continue                                                    # nicht auf die Lichter
            if (k // 4) % 2 == 0:
                mech(x, MAUER_OBEN, z, "abwehrgeschuetz", blick)
            else:
                mech(x, MAUER_OBEN, z, "laser_sender", zustand_extra={"facing": "up"}, reichweite="40")
            nr += 1
    # Die Landminen legt die Mod beim Bau der Welt in einem 40 Blöcke breiten Gürtel rund um das Grundstück
    # (siehe Festungsbau.java), genau wie das Ende des Notausgangs 50 Blöcke westlich.


def warnstreifen(x0: int, z0: int, x1: int, z1: int, y: int) -> None:
    """Gelb-schwarze Warnstreifen (schräg) im Boden."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            b.setze(x, y, z, "yellow_concrete" if (x + z) % 4 < 2 else "black_concrete")


def bunker_gang(y: int, y0: int, y1: int) -> None:
    """Bunker-Look im Hauptgang: Stahlrippen, Rohre und Kabel unter der Decke, Lüftungsgitter, Warnstreifen vor
    Aufzug und Türen, roter Läufer in der Mitte (als Bodeneinlage, damit die Laser im Gang frei bleiben)."""
    # Rohre (Kupfer) und Kabelstränge (Ketten) an beiden Seiten unter der Decke
    for x, rohr in ((54, "chain"), (55, "lightning_rod"), (60, "lightning_rod"), (61, "chain")):
        teil = (zustand("chain", axis="z", waterlogged="false") if rohr == "chain"
                else zustand("lightning_rod", facing="south", powered="false", waterlogged="false"))
        b.quader(x, y1 - 1, 31, x, y1 - 1, 68, teil)
    # Stahlrippen: Säulen in beiden Wänden und ein Träger unter der Decke (nicht unter den Deckenlampen)
    for z in (35, 47, 55, 63):
        b.quader(53, y, z, 53, y1 - 1, z, "iron_block")
        b.quader(62, y, z, 62, y1 - 1, z, "iron_block")
        b.quader(54, y1 - 1, z, 61, y1 - 1, z, "iron_block")
    # Lüftungsgitter in der Decke
    for z in (45, 59):
        b.quader(57, y1, z, 58, y1, z, "waxed_copper_grate")
    # Roter Läufer vom Aufzug bis zur Kamera am Südende
    b.quader(57, y0, 43, 58, y0, 67, "red_wool")
    # Warnstreifen: vor der Aufzugstür, vor Banktür, Panzertür, Waffenkammer und Geldspeicher
    warnstreifen(55, 41, 60, 42, y0)
    warnstreifen(54, 43, 55, 45, y0)
    warnstreifen(54, 57, 55, 60, y0)
    warnstreifen(60, 43, 61, 45, y0)
    warnstreifen(60, 57, 61, 60, y0)


def bunker_suite(y: int, y0: int, y1: int) -> None:
    """Panikraum als luxuriöse Bunker-Suite (x 37–52, z 51–68): Lounge mit XXL-Fernseher und Samtsofas,
    Küchenzeile, Bad mit goldener Toilette, Regendusche und Badewanne, Kingsize-Bett. Der Weg von der Panzertür
    (x 53, z 58–59) zum Geheimregal vor dem Fluchttunnel (x 36, z 60–61) bleibt frei."""
    b.quader(37, y0, 51, 52, y0, 68, HOLZBODEN)
    warnstreifen(51, 57, 52, 60, y0)                                        # innen vor der Panzertür
    # Goldleiste oben an allen Wänden
    b.quader(37, y1 - 1, 50, 52, y1 - 1, 50, "gold_block")
    b.quader(36, y1 - 1, 51, 36, y1 - 1, 68, "gold_block")
    b.quader(37, y1 - 1, 69, 52, y1 - 1, 69, "gold_block")
    b.quader(53, y1 - 1, 51, 53, y1 - 1, 54, "gold_block")
    b.quader(53, y1 - 1, 56, 53, y1 - 1, 62, "gold_block")
    b.quader(53, y1 - 1, 64, 53, y1 - 1, 68, "gold_block")

    # Lounge: Medienwand mit Goldrahmen an der Nordwand, XXL-Fernseher, Samtsofas als U, Teppich
    b.quader(40, y, 50, 49, y1 - 2, 50, "gold_block")
    b.quader(41, y + 1, 50, 48, y1 - 2, 50, SCHWARZ)
    # Riesiger Fernseher: füllt die ganze schwarze Fläche bis zur Goldkante (x 41–48, y+1 … y+4 = 8 × 4 Blöcke)
    b.marke("fernseher", 45.0, y + 1, 51.15, 0.0, 8.0, 4.0)                 # Bild nach Süden zu den Sofas
    b.quader(41, y0, 52, 48, y0, 55, "red_wool")
    reihe(b, 41, y, 56, 8, "sofa_samt", "north")                            # hinten: x 41 … 48
    reihe(b, 40, y, 53, 3, "sofa_samt", "east")                             # links: z 53 … 55
    reihe(b, 49, y, 55, 3, "sofa_samt", "west")                             # rechts: z 55 … 53
    deko(b, 44, y, 54, "loungetisch", "north")
    deko(b, 45, y, 54, "kinotisch", "north")
    fernbedienung(b, 44, y + 1, 54, "north", 6)                             # auf dem Loungetisch
    # Küchenzeile an der Westwand: Kühlschrank, Weinkühlschrank, Spüle, Herd, Kaffeemaschine
    for teil, z in (("kuehlschrank", 51), ("weinkuehlschrank", 52)):
        deko(b, 37, y, z, teil, "east")
        deko(b, 37, y + 1, z, teil, "east")
    deko(b, 37, y, 53, "spuele", "east")
    deko(b, 37, y, 54, "herd", "east")
    b.setze(37, y, 55, "polished_blackstone")
    deko(b, 37, y + 1, 55, "kaffeemaschine", "east")
    fass(b, 37, y, 56, "east", [("golden_carrot", 64), ("cooked_beef", 64), ("bread", 64)])
    fass(b, 37, y + 1, 56, "east", [("cake", 4), ("honey_bottle", 16), ("cookie", 64)])

    # Bad im Südosten: Wände aus Quarz mit Goldleiste, Prunktür, Regendusche, Badewanne, goldene Toilette
    b.quader(45, y, 62, 52, y1 - 1, 62, "quartz_bricks")
    b.quader(45, y, 63, 45, y1 - 1, 68, "quartz_bricks")
    b.quader(45, y + 3, 62, 52, y + 3, 62, "gold_block")
    b.quader(45, y + 3, 63, 45, y + 3, 68, "gold_block")
    tuer(b, 49, y, 62, "north", "stinkendreich:prunktuer")
    b.quader(46, y0, 63, 52, y0, 68, QUARZ)
    b.quader(46, y, 66, 48, y + 3, 68, "glass")
    b.luft(47, y, 66, 47, y + 2, 67)                                        # Glaskasten, zum Bad hin offen
    mech(47, y + 2, 67, "duschkopf", "north", {"an": "false", "wand": "false"})   # Regendusche
    mech(47, y + 1, 67, "duscharmatur", "north", {"an": "false"})
    badewanne(b, 49, y, 68, "east")
    deko(b, 52, y, 66, "goldtoilette", "west")
    deko(b, 51, y, 63, "spuele", "south")
    deko(b, 52, y, 63, "spuele", "south")
    pflanze(b, 46, y, 63, "potted_bamboo")

    # Schlafbereich im Südwesten: Kingsize-Bett mit Samt-Kopfteil und Goldrahmen, Nachttische, Kronleuchter
    kingsize_bett(b, 42, y, 68, 4, "south")                                 # x 42 … 39, z 66 … 68
    for x in range(39, 43):
        b.quader(x, y, 69, x, y + 2, 69, zustand("stinkendreich:kopfteil", facing="north", waterlogged="false"))
    b.quader(38, y, 69, 38, y + 3, 69, "gold_block")
    b.quader(43, y, 69, 43, y + 3, 69, "gold_block")
    b.quader(38, y + 3, 69, 43, y + 3, 69, "gold_block")
    deko(b, 38, y, 68, "nachttisch", "north")
    deko(b, 38, y + 1, 68, "tischlampe", "north")
    deko(b, 43, y, 68, "nachttisch", "north")
    fernbedienung(b, 43, y + 1, 68, "north", 2)
    kronleuchter(b, 40, y1 - 1, 66)
    # Notvorrat und Ausrüstung an der Wand neben dem Bett
    truhe(b, 37, y, 65, "east", [("enchanted_golden_apple", 4), ("golden_apple", 16), ("totem_of_undying", 1)])
    truhe(b, 37, y, 64, "east", [("torch", 64), ("lantern", 16), ("water_bucket", 2), ("milk_bucket", 2)])


# Mod-Blöcke mit eigener Block-Entity (für gespeicherte Werte wie Modus, Name, Code)
def mech(x: int, y: int, z: int, block: str, richtung: str | None = None, zustand_extra: dict | None = None,
         **werte) -> None:
    """Setzt einen Mechanik-Block der Mod; Werte landen in seiner Block-Entity, zustand_extra in den Blockzustand."""
    props = dict(zustand_extra or {})
    if richtung:
        props["facing"] = richtung
    if block in ("kartenleser", "tastenfeld", "bewegungsmelder"):
        props["powered"] = "false"
    if block == "warnleuchte":
        props["lit"] = "false"
    daten = {"id": "stinkendreich:mechanik", "stinkendreich": json.dumps({"werte": werte})} if werte else None
    b.setze(x, y, z, zustand(f"stinkendreich:{block}", **props), daten)


def fluegel(x0, y0, z0, x1, y1, z1, name: str, offen: bool) -> None:
    """Fläche aus Torflügeln (Tor, Rollladen, Schiebetür …), offen oder geschlossen.

    Jedes Feld bekommt seine Lage im Ganzen (Spalte längs der Fläche, Zeile von oben nach unten), damit
    Rahmen und Griffe stimmen. Liegt die Fläche flach (Poolabdeckung), zählen die Zeilen von Norden nach Süden.
    """
    def lage(i: int, n: int, namen: tuple[str, str, str]) -> str:
        if n == 1:
            return "einzeln"
        return namen[0] if i == 0 else namen[2] if i == n - 1 else namen[1]

    flach = y0 == y1 and x0 != x1 and z0 != z1
    # Längs z nur, wenn die Fläche wirklich in z-Richtung reicht: ein einzelnes Feld (x0 == x1, z0 == z1) vor einer
    # Fassade nach Süden gehört längs x davor (sonst stand der schmale Rollladen neben der Haustür quer ab)
    achse = "z" if (x0 == x1 and z0 != z1 and not flach) else "x"
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            for z in range(z0, z1 + 1):
                i, n = (z - z0, z1 - z0 + 1) if achse == "z" else (x - x0, x1 - x0 + 1)
                j, m = (z - z0, z1 - z0 + 1) if flach else (y1 - y, y1 - y0 + 1)
                b.setze(x, y, z, zustand(f"stinkendreich:{name}", open=str(offen).lower(), stufe="3" if offen else "0",
                                         axis=achse, spalte=lage(i, n, ("links", "mitte", "rechts")),
                                         zeile=lage(j, m, ("oben", "mitte", "unten"))))


def mechanismen() -> None:
    """Setzt alle Mechanismen der Villa (eigene Blöcke der Mod, keine Command-Blöcke)."""
    n = GROESSE[0] - 1
    yo = OG + 1
    pfeiler = set(range(HX0, HX1 + 1, 7)) | {HX1}

    # Tor: öffnet für Bewohner und Kartenträger, Klingel außen
    fluegel(51, G + 1, n - 13, 58, G + 4, n - 12, "tor", False)
    mech(54, G - 1, n - 13, "torsteuerung", modus="tor", ziel="stinkendreich:tor", radius=6)
    mech(59, G + 2, n - 11, "tuerklingel", "south")

    # Automatische Türen: Haustür (große weiß-goldene Portaltür) und Glas-Schiebetür zur Garage
    fluegel(54, EG + 1, HZ1, 55, EG + 3, HZ1, "portaltuer", False)
    mech(54, EG - 1, HZ1, "torsteuerung", modus="schiebetuer", ziel="stinkendreich:portaltuer", radius=3)
    fluegel(HX1, EG + 1, 60, HX1, EG + 3, 61, "schiebetuer", False)
    mech(HX1, EG - 1, 60, "torsteuerung", modus="schiebetuer", ziel="stinkendreich:schiebetuer", radius=3)

    # Garagentor
    fluegel(82, EG + 1, 67, 92, EG + 4, 67, "garagentor", False)
    mech(87, EG - 1, 67, "torsteuerung", modus="tor", ziel="stinkendreich:garagentor", radius=6)

    # Rollläden vor den Fenstern im Erdgeschoss: schließen ab Stufe 2 (Lockdown)
    luecke = []
    for x in range(HX0 + 1, HX1 + 1):
        if x in pfeiler or 53 <= x <= 56 or x == HX1:
            if luecke:
                fluegel(luecke[0], EG + 1, HZ1 + 1, luecke[-1], OG - 1, HZ1 + 1, "rollladen", True)
            luecke = []
        else:
            luecke.append(x)
    mech(55, EG - 1, HZ1 + 2, "torsteuerung", modus="rollladen", ziel="stinkendreich:rollladen", radius=24)

    # Sicherheitszentralen und Überwachung
    mech(71, yo + 1, 61, "sicherheitszentrale", "north")                    # auf dem Schreibtisch im Büro
    # Kartenleser an den Sicherheitstüren im Obergeschoss (Büro, Überwachungsraum)
    mech(59, yo + 1, 57, "kartenleser", "west")
    mech(61, yo + 1, 57, "kartenleser", "east")
    mech(69, yo + 1, 52, "kartenleser", "south")
    mech(69, yo + 1, 50, "kartenleser", "north")
    # Banktür der Kommandozentrale im Bunker: Kartenleser im Flur und drinnen
    mech(54, BUNKER + 2, 43, "kartenleser", "east")
    mech(52, BUNKER + 2, 43, "kartenleser", "west")
    mech(47, BUNKER + 2, 36, "sicherheitszentrale", "south", kommando="ja")  # Kommandozentrale: nur hier Stufe 4
    for m in b.marken["monitor"] + b.marken["bunker_monitor"]:
        mech(m[0], m[1], m[2], "monitor", "south", {"winkel": "wand"})
    for (x, y, z, r) in ((50, G + 5, n - 11, "south"), (59, G + 5, n - 14, "north"), (51, EG + 4, HZ1 + 1, "south"),
                         (58, EG + 4, HZ1 + 1, "south"), (55, OG + 3, HZ0 - 1, "north"), (65, yo + 3, 46, "north"),
                         (93, EG + 4, 47, "south"), (57, BUNKER + 4, 68, "north")):
        mech(x, y, z, "kamera", r)
    truhe(b, 77, yo, 66, "north", [("stinkendreich:zugangskarte", 1)] * 4)

    # Bewegungsmelder (Alarm bei Eindringlingen, Lichtautomatik), Sirenen, Warnleuchten
    mech(52, EG + 5, HZ0 + 1, "bewegungsmelder", "south")                   # über dem Fensterband
    mech(50, G + 3, n - 11, "bewegungsmelder", "south")
    mech(60, BUNKER + 3, 31, "bewegungsmelder", "south")
    mech(91, EG + 3, 47, "bewegungsmelder", "south")                       # Garage: Licht an, wenn jemand kommt
    for (x, y) in ((90, EG + 3), (92, EG + 3), (91, EG + 4)):
        b.setze(x, y, 46, "redstone_lamp")
    mech(58, OG + 4, HZ1 + 1, "alarmsirene", "south")                       # am Pfeiler, nicht vor dem Glas
    mech(52, OG + 5, HZ0 + 1, "alarmsirene", "south")                       # über dem Fensterband
    mech(57, BUNKER + 4, 31, "alarmsirene", "south")
    for x in (50, 59):
        mech(x, G + 7, n - 13, "warnleuchte")
    for z in (36, 50, 62):
        mech(57, BUNKER + 7, z, "warnleuchte")                              # Bunkergang: rotes Licht bei Alarm
    mech(64, DACH - 1, 42, "warnleuchte")

    # Laser (ab Stufe 3): die Sender sitzen bündig in Hecke bzw. Wand, nur der dünne Strahl ist zu sehen
    for z in (40, 58):
        mech(15, G + 1, z, "laser_sender", "east")
    for y in (BUNKER + 1, BUNKER + 2):
        mech(53, y, 48, "laser_sender", "east")
    for y in (yo, yo + 1):
        mech(61, y, 43, "laser_sender", "east")

    # Schmuck-Tresor: runde Tresortür mit Tastenfeld außen und Hebel innen, Fallenplatten, Gitterfalle, Entschärfknöpfe
    fluegel(63, yo, 47, 64, yo + 1, 47, "tresortuer", False)
    mech(62, yo, 47, "torsteuerung", modus="redstone", ziel="stinkendreich:tresortuer", radius=3)
    mech(62, yo + 1, 48, "tastenfeld", "south")
    b.setze(62, yo + 1, 46, zustand("lever", face="wall", facing="north", powered="false"))
    fluegel(63, yo, 46, 64, yo + 1, 46, "gitterfalle", True)
    for (x, z) in ((63, 42), (64, 40), (65, 43), (63, 45)):
        b.setze(x, OG, z, "stinkendreich:fallenplatte")
    mech(66, yo + 2, 47, "entschaerfknopf")
    b.quader(62, yo, 66, 62, DACH - 1, 66, WEISS)
    mech(62, yo + 1, 66, "entschaerfknopf")

    # Waffenkammer im Bunker: Bunkertür (Schloss und Riegel) mit Kartenleser auf beiden Seiten
    b.quader(62, BUNKER + 1, 45, 62, BUNKER + 3, 45, "deepslate_tiles")
    b.setze(62, BUNKER + 3, 44, "deepslate_tiles")
    tuer(b, 62, BUNKER + 1, 44, "east", "stinkendreich:bunkertuer")
    mech(61, BUNKER + 2, 43, "kartenleser", "west")
    mech(63, BUNKER + 2, 43, "kartenleser", "east")
    mech(61, BUNKER + 2, 57, "kartenleser", "west")                         # Geldspeicher
    mech(63, BUNKER + 2, 57, "kartenleser", "east")

    # Aufzug: Ruftafel rechts neben der Tür an jeder Haltestelle; die Kabine steht 1,5 Blöcke links
    # und 2,5 Blöcke hinter der Tafel
    namen = {"bunker": "Untergrundbasis", "keller": "Club & Tiefgarage", "eg": "Erdgeschoss", "og": "Obergeschoss",
             "dach": "Penthouse", "heli": "Hubschrauber"}
    for name, y in AUFZUG_HALTE.items():
        karte = {"karte": "ja"} if name == "bunker" else {}                 # Bunker nur mit Zugangskarte
        mech(AX1, y + 2, AZ1 + 1, "aufzugstafel", "south", name=namen[name],
             kabine_dx="-1.5", kabine_dz="-2.5", kabine_dy="-1", **karte)

    # Geheimgang: Bücherwand im Panikraum vor dem Fluchttunnel
    b.luft(35, BUNKER + 1, 60, 35, BUNKER + 3, 61)
    b.quader(36, BUNKER + 1, 57, 36, BUNKER + 3, 64, "bookshelf")
    fluegel(36, BUNKER + 1, 60, 36, BUNKER + 3, 61, "geheimregal", False)
    mech(36, BUNKER + 2, 63, "geheimschalter")

    # Geheimclub: Bücherregal an der Bar des Nachtclubs ist eine Geheimtür, der Schalter steckt im Regal daneben
    b.quader(30, UG + 1, 61, 30, UG + 3, 65, "bookshelf")
    fluegel(30, UG + 1, 63, 30, UG + 3, 64, "geheimregal", False)
    mech(30, UG + 2, 61, "geheimschalter")

    # Panikraum: Panzertür, Verriegelung per Schalter innen
    fluegel(53, BUNKER + 1, 58, 53, BUNKER + 3, 59, "panzertuer", True)
    mech(52, BUNKER + 1, 56, "torsteuerung", modus="schalter", ziel="stinkendreich:panzertuer", radius=3)

    # Komfort: Poolabdeckung mit Hebel, Whirlpool, Kaminschalter, Musikanlage
    fluegel(41, G + 1, 19, 68, G + 1, 26, "poolabdeckung", True)
    mech(70, G, 22, "torsteuerung", modus="redstone_invers", ziel="stinkendreich:poolabdeckung", radius=29)
    b.setze(70, G + 1, 22, zustand("lever", face="floor", facing="north", powered="false"))
    mech(HX1 - 5, DACH, HZ0 + 3, "whirlpoolduese")
    mech(43, EG + 2, HZ0 + 1, "kaminsteuerung", "south")
    # Brückenschalter: innen neben der Haustür (am schwarzen Türpfosten), innen am Torpfeiler und draußen am Zaun
    n = GROESSE[0] - 1
    mech(53, EG + 2, HZ1 - 1, "brueckenschalter", "north")
    mech(59, G + 2, n - 14, "brueckenschalter", "north")
    # … und draußen vor der Einfahrt am Außenzaun (dort Quarz statt Gitter, damit der Schalter auf etwas Festem sitzt)
    b.setze(60, G + 2, n - 1, QUARZ)
    mech(60, G + 2, n, "brueckenschalter", "south")
    b.setze(52, DACH + 1, 40, zustand("jukebox", has_record="false"))


def licht_im_garten() -> None:
    """Unsichtbare Lichtblöcke auf dem Grundstück, damit nachts keine Monster spawnen und Alarm auslösen."""
    for x in range(15, GROESSE[0] - 15, 9):
        for z in range(15, GROESSE[2] - 15, 9):
            if b.hole(x, G + 1, z) is None:
                b.setze(x, G + 1, z, zustand("light", level="15", waterlogged="false"))


def lichter() -> None:
    """Smart-Home-Hängelampen unter allen Decken, nur wo der Raum frei ist (zuletzt ausführen)."""
    def haengen(x0, x1, z0, z1, y, abstand=4):
        for x in range(x0, x1 + 1, abstand):
            for z in range(z0, z1 + 1, abstand):
                if b.hole(x, y, z) == "minecraft:air" and b.hole(x, y + 1, z) not in (None, "minecraft:air"):
                    b.setze(x, y, z, zustand("stinkendreich:smartlicht", farbe="warm", lit="true"))     # Smart-Home-Lampe
                    b.marke("smartlicht", x, y, z)
    haengen(HX0 + 3, HX1 - 2, HZ0 + 3, HZ1 - 2, OG - 1)          # Erdgeschoss
    haengen(HX0 + 3, HX1 - 2, HZ0 + 3, HZ1 - 2, DACH - 1)        # Obergeschoss
    haengen(44, 65, 40, 56, PH_DACH - 1)                         # Penthouse
    haengen(82, 92, 48, 64, OG - 1, 3)                           # Garage


def decken_weiss() -> None:
    """Weiße Decken in Haus und Penthouse: Wo man einen Boden aus Holz, Teppich, Gold, Beton oder Stein von unten
    sieht, kommt der Deckenblock der Mod hin. Er sieht oben und an den Seiten aus wie vorher, unten ist er weiß
    (register/ModDecken.java). Gilt für die Decken im Erdgeschoss, im Obergeschoss und im Penthouse."""
    tausch = {"dark_oak_planks": "holz", "white_wool": "teppich_weiss", "red_wool": "teppich_rot",
              "yellow_wool": "teppich_gelb", "gray_concrete": "grau", "gold_block": "gold",
              "polished_blackstone": "schwarz", "polished_deepslate": "schiefer"}
    for y in (OG, DACH, PH_DACH):
        for x in range(HX0 - 3, HX1 + 4):
            for z in range(HZ0 - 3, HZ1 + 8):
                block, unten = b.hole(x, y, z), b.hole(x, y - 1, z)
                if block is None or basisname(block).removeprefix("minecraft:") not in tausch or "[" in block:
                    continue
                if unten is None or not ist_voll(unten):
                    b.setze(x, y, z, f"stinkendreich:decke_{tausch[basisname(block).removeprefix('minecraft:')]}")


def pruefe_vor_glas() -> list[str]:
    """Alarmanlagen, Kameras und Kartenleser dürfen nicht vor einer Glasscheibe hängen (sieht von außen und innen
    unschön aus). Liefert die Stellen, an denen das doch so ist."""
    an_der_wand = ("alarmsirene", "bewegungsmelder", "kamera", "kartenleser", "tastenfeld", "tuerklingel",
                   "aufzugstafel", "kaminsteuerung", "brueckenschalter")
    fehler = []
    for (x, y, z), block in b.bloecke.items():
        name = basisname(block).removeprefix("stinkendreich:")
        if not block.startswith("stinkendreich:") or name not in an_der_wand:
            continue
        richtung = eigenschaften(block).get("facing")
        if richtung not in RICHTUNG_VEKTOR:
            continue
        vx, _, vz = RICHTUNG_VEKTOR[richtung]
        hinten = b.hole(x - vx, y, z - vz) or ""
        if "glass" in hinten:
            fehler.append(f"{name} bei ({x}, {y}, {z}) hängt vor {hinten}")
    return fehler


def main() -> None:
    grundstueck()
    tor_und_bruecke()
    vorplatz()
    garten()
    garage()
    huelle()
    erdgeschoss()
    obergeschoss()
    penthouse()
    dachparty()
    hubschrauberlandeplatz()
    poolparty()
    bunker()
    keller()
    seerestaurant()
    club_ausbau()
    geheimclub()
    # Treppe und Aufzug nach allen Räumen, die beim Bau Luft schaffen (sonst fehlte der Schacht im Bunker)
    treppe_und_aufzug()
    mechanismen()
    licht_im_garten()
    lichter()
    festung()
    haibecken()

    # Wo ein Bewohner als „zu Hause“ gilt (für den Lava-Graben): Haus mit Penthouse und Terrassen, Garage, Untergeschoss, Bunker
    b.marke("haus", HX0, EG, HZ0, HX1, LANDEPLATZ + 4, HZ1 + 6)                 # samt Balkon und Dachterrasse
    b.marke("haus", 80, EG, 46, 94, OG, 67)
    b.marke("haus", 29, UG - 1, 31, 97, EG - 1, 68)
    b.marke("haus", 35, BUNKER - 1, 29, 74, BUNKER + 8, 70)
    # Spawnpunkt vor dem Haus, Blick nach Norden auf den Eingang
    b.marke("spawn", 55.0, G + 1, 93.5, 180.0)
    b.marke("butler", 57.5, EG + 1, 60.5)
    # Lieferkiste des Butlers in der Halle (Bestellungen „… in die Truhe“)
    truhe(b, 59, EG + 1, 62, "west", [])
    b.marke("lieferkiste", 59, EG + 1, 62)
    decken_weiss()
    b.verbinden()

    vor_glas = pruefe_vor_glas()
    if vor_glas:
        print(f"{len(vor_glas)} Geräte vor Glas, Villa wird nicht gespeichert:")
        for f in vor_glas:
            print("  " + f)
        sys.exit(1)

    # Dichtheitsprüfung: kein Wasser und keine Lava darf auslaufen, kein Raum unter der Erde offen sein
    probleme = b.pruefe_dichtheit(G)
    if probleme:
        print(f"{len(probleme)} Lecks im Bauplan, Villa wird nicht gespeichert:")
        for p in probleme[:40]:
            print("  " + p)
        sys.exit(1)

    ZIEL.mkdir(parents=True, exist_ok=True)
    b.speichern(ZIEL / "villa.nbt")
    meta = {
        "groesse": list(GROESSE),
        "boden_y": G,
        "etagen": {"bunker": BUNKER + 1, "eg": EG + 1, "og": OG + 1, "dach": DACH + 1},
        "marken": b.marken,
    }
    (ZIEL / "villa.json").write_text(json.dumps(meta, indent=1, ensure_ascii=False), encoding="utf-8")
    print(f"villa.nbt: {len(b.bloecke)} Blöcke, {len(b.entities)} Entities, {(ZIEL / 'villa.nbt').stat().st_size // 1024} KB")

    if "--vorschau" in sys.argv:
        from render import isometrisch
        aus = WURZEL / "build/villa"
        aus.mkdir(parents=True, exist_ok=True)
        vol = b.als_volumen()
        # Bunker ausblenden, damit man das Haus sieht
        oben = vol.bloecke.copy()
        oben[:G - 6] = 0
        vol.bloecke = oben
        isometrisch(vol, kante=6, blickrichtung=0).save(aus / "vorschau_iso.png")
        print(f"Vorschau: {aus / 'vorschau_iso.png'}")


if __name__ == "__main__":
    main()
