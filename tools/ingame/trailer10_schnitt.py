"""Setzt Trailer Teil 10 (0.5.0) zusammen: trailer10a (Treppe in die Mine), trailer10b (Abwehrgeschütze per Handy),
trailer10c (Abschleppen bis vor die Haustür) aus tools/ingame/szenarien/trailer10.txt.
Ergebnis: build/ingame/trailer10/trailer10_roh.mp4 und trailer10_kapitel.txt, danach video.py und an docs/trailer.mp4 hängen.
"""
import os
import subprocess
from pathlib import Path

W = Path(__file__).resolve().parents[2]
S = W / "build/ingame/trailer10"
ROH = Path(os.environ.get("ROH", W / "build/ingame/26.3"))
S.mkdir(parents=True, exist_ok=True)


def laenge(d):
    return float(subprocess.check_output(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(d)]))


# Stück, Rohaufnahme, behaltene Abschnitte (von, bis in Sekunden der Aufnahme; None = bis zum Ende)
STUECKE = [
    ("a", ROH / "trailer10a_roh.mp4", [(0.3, None)]),
    ("b", ROH / "trailer10b_roh.mp4", [(0.3, None)]),
    ("c", ROH / "trailer10c_roh.mp4", [(0.3, None)]),
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
                str(S / "trailer10_roh.mp4")], check=True)
kapitel.sort()
(S / "trailer10_kapitel.txt").write_text("".join(f"{t:.1f}|{x}\n" for t, x in kapitel), encoding="utf-8")
print(f"{versatz:.1f} s, {len(kapitel)} Kapitel")
for t, x in kapitel:
    print(f"  {t:6.1f} {x}")
