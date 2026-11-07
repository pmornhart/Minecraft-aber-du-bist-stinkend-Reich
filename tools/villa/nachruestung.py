#!/usr/bin/env python3
"""Nachrüstung bestehender Villen: vergleicht zwei Stände der Villa (alte und neue villa.nbt aus villa.py) und schreibt
die geänderten Blöcke als Liste nach src/main/resources/data/stinkendreich/villa_nachruestung/<name>.json.

Die Mod (villa/VillaNachruestung) spielt jede Liste einmal je Villa ab: Ein Block wird nur getauscht, wenn an der
Stelle noch genau der alte Block der Villa steht – was Spieler umgebaut haben, bleibt unberührt.

Aufruf: python3 tools/villa/nachruestung.py <alte villa.nbt> <name>   (die neue ist die aktuelle im Repo)
Ohne <name>: nur die Unterschiede zählen (z. B. um zu prüfen, ob villa.py dieselbe Villa erzeugt).
"""
import json
import sys
from pathlib import Path

import nbtlib

WURZEL = Path(__file__).resolve().parents[2]
NEU = WURZEL / "src/main/resources/data/stinkendreich/structure/villa/villa.nbt"
AUS = WURZEL / "src/main/resources/data/stinkendreich/villa_nachruestung"


def zustand(p) -> str:
    name = str(p["Name"])
    if "Properties" in p and len(p["Properties"]):
        props = ",".join(f"{k}={v}" for k, v in sorted(p["Properties"].items()))
        return f"{name}[{props}]"
    return name


def lesen(pfad) -> dict:
    n = nbtlib.load(pfad)
    palette = [zustand(p) for p in n["palette"]]
    bloecke = {}
    for b in n["blocks"]:
        pos = tuple(int(v) for v in b["pos"])
        bloecke[pos] = (palette[int(b["state"])], "nbt" in b)
    return bloecke


def main() -> None:
    alt, neu = lesen(sys.argv[1]), lesen(NEU)
    aenderungen = []
    for pos in sorted(set(alt) | set(neu)):
        a = alt.get(pos, ("minecraft:air", False))
        b = neu.get(pos, ("minecraft:air", False))
        if a[0] != b[0]:
            if b[1]:
                print(f"Achtung: {pos} bekommt einen Block mit Daten ({b[0]}) – die Nachrüstung setzt nur den Zustand")
            aenderungen.append([*pos, a[0], b[0]])
    print(f"{len(aenderungen)} Blöcke geändert")
    if len(sys.argv) > 2:
        AUS.mkdir(parents=True, exist_ok=True)
        ziel = AUS / f"{sys.argv[2]}.json"
        ziel.write_text(json.dumps({"bloecke": aenderungen}, ensure_ascii=False, indent=0), encoding="utf-8")
        print(f"geschrieben: {ziel.relative_to(WURZEL)}")


if __name__ == "__main__":
    main()
