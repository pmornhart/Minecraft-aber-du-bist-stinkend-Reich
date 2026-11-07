"""Hilfen für Häute im Layout einer Spieler-Haut (64 × 64, zwei Schichten).

So eine Haut passt überall: im Modell der Mod (client/MenschenNetz), auf Schaufensterpuppen der Server-Version, als
Standbild bis 1.21.8. Linke Arme und Beine haben eigene Flächen (bei den alten Häuten der Mod
waren sie nur gespiegelt, auf einer Schaufensterpuppe fehlten sie dann). Die zweite Schicht (Hut, Jacke, Ärmel,
Hosenbeine) liegt ein Stück über dem Körper und gibt Haaren und Kleidung Tiefe.
"""
from __future__ import annotations

from PIL import Image

# Würfel der Spieler-Haut: (u, v) der rechten Seite, (u, v) der linken Seite, Breite, Höhe, Tiefe
KOPF = (0, 0)
HUT = (32, 0)
KOERPER = (16, 16)
JACKE = (16, 32)
ARM_R, ARM_L = (40, 16), (32, 48)
AERMEL_R, AERMEL_L = (40, 32), (48, 48)
BEIN_R, BEIN_L = (0, 16), (16, 48)
HOSE_R, HOSE_L = (0, 32), (0, 48)


def seiten(u: int, v: int, w: int, h: int, d: int) -> dict:
    """Die sechs Flächen eines Würfels: Name → (x0, y0, Breite, Höhe). „rechts“ ist die Außenseite eines rechten
    Glieds (−x), „vorn“ die Seite zum Betrachter (−z), wie bei Minecraft-Modellen."""
    return {
        "oben": (u + d, v, w, d), "unten": (u + d + w, v, w, d),
        "rechts": (u, v + d, d, h), "vorn": (u + d, v + d, w, h),
        "links": (u + d + w, v + d, d, h), "hinten": (u + 2 * d + w, v + d, w, h),
    }


def wuerfel(img: Image.Image, u: int, v: int, w: int, h: int, d: int, f) -> None:
    """Malt die Flächen mit f(seite, x, y, breite, hoehe) → Farbe (RGB, RGBA) oder None (bleibt, wie es ist)."""
    for name, (x0, y0, bw, bh) in seiten(u, v, w, h, d).items():
        for y in range(bh):
            for x in range(bw):
                farbe = f(name, x, y, bw, bh)
                if farbe is not None:
                    img.putpixel((x0 + x, y0 + y), tuple(farbe[:3]) + (255,) if len(farbe) == 3 else tuple(farbe))


def spiegeln(img: Image.Image, von: tuple, nach: tuple, w: int, h: int, d: int) -> None:
    """Überträgt ein rechtes Glied spiegelbildlich auf das linke (wie „mirror“ bei Minecraft-Modellen): jede Fläche
    waagerecht gespiegelt, Außen- und Innenseite getauscht."""
    a, b = seiten(*von, w, h, d), seiten(*nach, w, h, d)
    tausch = {"rechts": "links", "links": "rechts"}
    for name, (x0, y0, bw, bh) in b.items():
        qx, qy, _, _ = a[tausch.get(name, name)]
        stueck = img.crop((qx, qy, qx + bw, qy + bh)).transpose(Image.FLIP_LEFT_RIGHT)
        img.paste(stueck, (x0, y0))


def linke_seite(img: Image.Image, schmal: bool = False) -> Image.Image:
    """Ergänzt linken Arm und linkes Bein (beide Schichten) als Spiegelbild der rechten."""
    arm = 3 if schmal else 4
    spiegeln(img, ARM_R, ARM_L, arm, 12, 4)
    spiegeln(img, AERMEL_R, AERMEL_L, arm, 12, 4)
    spiegeln(img, BEIN_R, BEIN_L, 4, 12, 4)
    spiegeln(img, HOSE_R, HOSE_L, 4, 12, 4)
    return img
