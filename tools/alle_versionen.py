#!/usr/bin/env python3
"""Eine .jar für alle Minecraft-Versionen (Philip, 07.11.: „dass du mit einer Mod alle Versionen gehen“).

Nimmt die fertigen Dateien je Minecraft-Version (stinkendreich-<mod>+mc<minecraft>.jar aus build/libs/<mod>/) und packt
sie in eine Sammel-Datei stinkendreich-<mod>+alle.jar:

- Die Sammel-Datei ist selbst eine kleine Fabric-Mod (Kennung „stinkendreich_alle“) ohne eigenen Code. In ihr stecken
  alle Fassungen als eingebettete Jars (Fabric „Jar-in-Jar“). Jede Fassung hat die Kennung „stinkendreich“ und verlangt
  genau ihre Minecraft-Version; Fabric lädt von mehreren Kandidaten mit derselben Kennung nur einen, dessen Bedingungen
  erfüllt sind – also genau die passende.
- Die Bibliotheken, die jede Fassung sonst selbst mitbringt (Anthropic-Bibliothek für den Claude-Butler, MP3-Decoder
  usw.), liegen nur einmal in der Sammel-Datei. Sonst wäre sie 17-mal so groß.
- Die Update-Funktion erkennt die Sammel-Datei (Kennung „stinkendreich_alle“ geladen) und bietet dann wieder die
  Sammel-Datei des neuen Release an.

Aufruf: python3 tools/alle_versionen.py build/libs/<mod>   →   build/libs/<mod>/stinkendreich-<mod>+alle.jar
"""
import io
import json
import re
import sys
import zipfile
from pathlib import Path

MUSTER = re.compile(r"^stinkendreich-(?P<mod>[^+]+)\+mc(?P<mc>[0-9.]+)\.jar$")


def lesen_json(z: zipfile.ZipFile, name: str) -> dict:
    return json.loads(z.read(name).decode("utf-8"))


def schreiben(ziel: zipfile.ZipFile, name: str, daten: bytes) -> None:
    info = zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
    # Eingebettete Jars unkomprimiert: Fabric liest sie dann direkt, und gepackt würde es kaum kleiner
    info.compress_type = zipfile.ZIP_STORED if name.endswith(".jar") else zipfile.ZIP_DEFLATED
    ziel.writestr(info, daten)


def fassung_ohne_bibliotheken(daten: bytes, bibliotheken: dict) -> bytes:
    """Kopie einer Fassung ohne ihre eingebetteten Bibliotheken; die Bibliotheken wandern nach {@code bibliotheken}."""
    aus = io.BytesIO()
    with zipfile.ZipFile(io.BytesIO(daten)) as z, zipfile.ZipFile(aus, "w", zipfile.ZIP_DEFLATED) as n:
        fmj = lesen_json(z, "fabric.mod.json")
        for eintrag in fmj.pop("jars", []):
            datei = eintrag["file"]
            inhalt = z.read(datei)
            alt = bibliotheken.get(Path(datei).name)
            if alt is not None and alt != inhalt:
                print(f"  Hinweis: {Path(datei).name} unterscheidet sich zwischen Fassungen, nehme die erste")
            bibliotheken.setdefault(Path(datei).name, inhalt)
        for info in z.infolist():
            if info.filename == "fabric.mod.json" or info.filename.startswith("META-INF/jars/"):
                continue
            n.writestr(info, z.read(info.filename))
        n.writestr("fabric.mod.json", json.dumps(fmj, indent=2, ensure_ascii=False))
    return aus.getvalue()


def main() -> None:
    ordner = Path(sys.argv[1])
    fassungen = []
    for p in sorted(ordner.glob("stinkendreich-*+mc*.jar")):
        m = MUSTER.match(p.name)
        if m:
            fassungen.append((m.group("mod"), m.group("mc"), p))
    if not fassungen:
        sys.exit(f"Keine Fassungen in {ordner}")
    mod = fassungen[0][0]
    bibliotheken: dict[str, bytes] = {}
    eingebettet: list[tuple[str, bytes]] = []
    for _, mc, p in fassungen:
        eingebettet.append((f"META-INF/jars/{p.name}", fassung_ohne_bibliotheken(p.read_bytes(), bibliotheken)))
        print(f"  {p.name}")
    with zipfile.ZipFile(fassungen[-1][2]) as z:
        icon = z.read("assets/stinkendreich/icon.png")
        vorlage = lesen_json(z, "fabric.mod.json")
    mcs = [mc for _, mc, _ in fassungen]
    fmj = {
        "schemaVersion": 1,
        "id": "stinkendreich_alle",
        "version": f"{mod}+alle",
        "name": "Stinkend Reich (alle Minecraft-Versionen)",
        "description": f"Eine Datei für Minecraft {mcs[0]} bis {mcs[-1]}: Fabric lädt daraus automatisch die passende Fassung "
                       "von „Minecraft, aber du bist stinkend reich“.",
        "authors": vorlage.get("authors", []),
        "contact": vorlage.get("contact", {}),
        "license": vorlage.get("license", "All-Rights-Reserved"),
        "icon": "icon.png",
        "environment": "*",
        "jars": [{"file": name} for name, _ in eingebettet] + [{"file": f"META-INF/jars/{b}"} for b in sorted(bibliotheken)],
        "depends": {"fabricloader": ">=0.16.0", "minecraft": mcs},
        "custom": {"modmenu": {"parent": "stinkendreich"}},
    }
    ziel = ordner / f"stinkendreich-{mod}+alle.jar"
    with zipfile.ZipFile(ziel, "w", zipfile.ZIP_DEFLATED) as z:
        schreiben(z, "fabric.mod.json", json.dumps(fmj, indent=2, ensure_ascii=False).encode("utf-8"))
        schreiben(z, "icon.png", icon)
        for name, daten in eingebettet:
            schreiben(z, name, daten)
        for b, daten in sorted(bibliotheken.items()):
            schreiben(z, f"META-INF/jars/{b}", daten)
    groesse = ziel.stat().st_size / 1e6
    print(f"{ziel.name}: {len(eingebettet)} Fassungen, {len(bibliotheken)} Bibliotheken, {groesse:.1f} MB")


if __name__ == "__main__":
    main()
