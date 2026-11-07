"""Setzt Trailer Teil 9 (0.4.8) zusammen: trailer9a (Unterwasser-Bar, VIP-Eingang) und trailer9b (Autoschlüssel, Automenü)
aus tools/ingame/szenarien/trailer9.txt, trailer9c–f (Abschleppen per Hubschrauber, Abholung aus der Luft, Treppe zur Mine,
Wegweiser) aus dem Nachdreh trailer9b.txt. Die Rohaufnahmen liegen in ROH (erste Aufnahme vorher dorthin kopieren).
Ergebnis: build/ingame/trailer9/trailer9_roh.mp4 und trailer9_kapitel.txt, danach video.py und an docs/trailer.mp4 hängen.
"""
import os
import subprocess
from pathlib import Path

W = Path(__file__).resolve().parents[2]
S = W / "build/ingame/trailer9"
ROH = Path(os.environ.get("ROH", W / "build/ingame/26.3"))
S.mkdir(parents=True, exist_ok=True)


def laenge(d):
    return float(subprocess.check_output(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(d)]))


# Stück, Rohaufnahme, behaltene Abschnitte (von, bis in Sekunden der Aufnahme; None = bis zum Ende)
STUECKE = [
    ("a", ROH / "trailer9a_roh.mp4", [(0.3, None)]),
    ("b", ROH / "trailer9b_roh.mp4", [(0.3, None)]),
    ("c", W / "build/ingame/26.3/trailer9c_roh.mp4", [(0.3, None)]),
    ("d", W / "build/ingame/26.3/trailer9d_roh.mp4", [(0.3, None)]),
    ("e", W / "build/ingame/26.3/trailer9e_roh.mp4", [(0.3, None)]),
    ("f", W / "build/ingame/26.3/trailer9f_roh.mp4", [(0.3, None)]),
]

liste, kapitel, versatz = [], [], 0.0
for name, roh, abschnitte in STUECKE:
    marken = []
    for z in roh.with_name(roh.name.replace("_roh.mp4", "_kapitel.txt")).read_text(encoding="utf-8").splitlines():
        if z:
            t, text = z.split("|", 1)
            marken.append((float(t), text))
    for n, (von, bis) in enumerate(abschnitte):
        teil = S / f"teil_{name}{n}.mp4"
        dauer = [] if bis is None else ["-t", f"{bis - von}"]
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-ss", f"{von}", "-i", str(roh), *dauer, "-vf", "fps=25",
                        "-c:v", "libx264", "-preset", "veryfast", "-crf", "18", "-pix_fmt", "yuv420p", "-an", str(teil)],
                       check=True)
        # Kapitel, die in diesen Abschnitt fallen oder in einem herausgeschnittenen Stück davor lagen, beginnen hier
        ende = bis if bis is not None else 1e9
        for t, text in list(marken):
            if t < ende:
                kapitel.append((versatz + max(0.0, t - von), text))
                marken.remove((t, text))
        versatz += laenge(teil)
        liste.append(f"file '{teil}'")
(S / "liste.txt").write_text("\n".join(liste) + "\n")
subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "concat", "-safe", "0", "-i", str(S / "liste.txt"), "-c", "copy",
                str(S / "trailer9_roh.mp4")], check=True)
kapitel.sort()
(S / "trailer9_kapitel.txt").write_text("".join(f"{t:.1f}|{x}\n" for t, x in kapitel), encoding="utf-8")
print(f"{versatz:.1f} s, {len(kapitel)} Kapitel")
for t, x in kapitel:
    print(f"  {t:6.1f} {x}")
