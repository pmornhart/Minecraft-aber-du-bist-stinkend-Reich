package de.moinmornhart.stinkendreich.strasse;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Weg einer Treppe vom Straßenende hinunter ins Ziel – reine Rechnung ohne Minecraft (lässt sich so mit tausenden
 * Zielen durchprobieren, siehe {@code tools/treppen_plan_test.java}).
 *
 * <p>Der Gang ist drei Blöcke breit (Mittelspur ± 1) und drei hoch. Jede Stufe geht einen Block waagerecht und einen
 * hinunter; an jeder Ecke liegt ein ebener Absatz (die Stufe davor und die erste danach gehen nicht hinunter). So schneidet
 * die nächste Stufe nie in den Boden der vorigen. Richtungen wechseln höchstens alle paar Stufen und nie um 180° –
 * vorher entstand bei ähnlichem x- und z-Abstand ein Zickzack, in dem Böden fehlten und Ecken offen blieben (Philip,
 * 08.11.: „das muss wirklich frei sein, es muss nach unten gehen“).</p>
 *
 * <p>Ist das Ziel tiefer als weit, läuft der Gang als eckige Wendel (Seiten zu {@code seite} Stufen, im Uhrzeigersinn),
 * bis der gerade Weg passt. Mehrere Seitenlängen werden ausprobiert; genommen wird der erste Plan, in dem kein Boden im
 * Gang einer anderen Stufe liegt.</p>
 */
public final class TreppenPlan {
    /** Richtungen 0 = Norden (−z), 1 = Osten (+x), 2 = Süden (+z), 3 = Westen (−x). */
    public static final int[] DX = {0, 1, 0, -1};
    public static final int[] DZ = {-1, 0, 1, 0};
    /** So viele Stufen geht die Treppe am Anfang geradeaus weiter (weg von der Straße, damit sie die Fahrbahn nicht anschneidet). */
    public static final int ANFANG = 4;
    private static final int GRENZE = 1500;

    /** Eine Stufe: Mittelspur (x, y = Fußhöhe, z), Richtung, eben (Podest) oder Stufe hinunter. */
    public record Stufe(int x, int y, int z, int richtung, boolean eben) {
        public int querX() {
            return DX[(richtung + 1) & 3];
        }

        public int querZ() {
            return DZ[(richtung + 1) & 3];
        }
    }

    private TreppenPlan() {
    }

    /**
     * Bester Plan von {@code start} (Luft über dem Straßenende) bis {@code ziel} (Stelle zum Stehen im Ziel).
     *
     * @param ersteRichtung Richtung der Straße am Ende (die Treppe geht zuerst so weiter), −1 = beliebig
     * @return Stufen, oder eine leere Liste, wenn kein brauchbarer Plan gefunden wurde
     */
    public static List<Stufe> planen(int sx, int sy, int sz, int zx, int zy, int zz, int ersteRichtung) {
        List<Stufe> notfall = null;
        int notfallFehler = Integer.MAX_VALUE;
        // zuerst geradeaus von der Straße weg, dann frei, zuletzt in jede andere Richtung
        for (int anfang : new int[]{ersteRichtung, -1, (ersteRichtung + 1) & 3, (ersteRichtung + 3) & 3, (ersteRichtung + 2) & 3}) {
            for (int variante = 0; variante < 12; variante++) {
                // Wendel im Uhrzeigersinn, dann gegen ihn; Seitenlängen 5–9 und 4
                int seite = new int[]{5, 6, 7, 4, 8, 9}[variante % 6];
                int drehung = variante < 6 ? 1 : 3;
                List<Stufe> plan = versuch(sx, sy, sz, zx, zy, zz, anfang, seite, drehung);
                if (plan == null) {
                    continue;
                }
                int fehler = konflikte(plan);
                if (fehler == 0) {
                    return plan;
                }
                if (fehler < notfallFehler) {
                    notfall = plan;
                    notfallFehler = fehler;
                }
            }
        }
        return notfall == null ? List.of() : notfall;
    }

    private static List<Stufe> versuch(int sx, int sy, int sz, int zx, int zy, int zz, int anfang, int seite, int drehung) {
        List<Stufe> plan = new ArrayList<>();
        int x = sx, y = sy, z = sz, dir = -1, gleich = 0;
        for (int n = 0; n < GRENZE; n++) {
            if (x == zx && z == zz && y <= zy) {
                return y == zy ? plan : null;
            }
            int d = wahl(x, y, z, zx, zy, zz, dir, gleich, n, anfang, seite, drehung);
            // Absatz: die Stufe vor einer Ecke und die erste danach sind eben (3 × 3 Blöcke auf einer Höhe), sonst ragt der
            // neue Gang in den Boden der vorletzten Stufe
            boolean eben = (dir >= 0 && d != dir) || y <= zy;
            if (!eben) {
                int nx = x + DX[d], nz = z + DZ[d];
                int g2 = d == dir ? gleich + 1 : 1;
                if (!(nx == zx && nz == zz && y - 1 <= zy) && wahl(nx, y - 1, nz, zx, zy, zz, d, g2, n + 1, anfang, seite, drehung) != d) {
                    eben = true;
                }
            }
            x += DX[d];
            z += DZ[d];
            if (!eben) {
                y--;
            }
            gleich = d == dir ? gleich + 1 : 1;
            dir = d;
            plan.add(new Stufe(x, y, z, d, eben));
        }
        return null;
    }

    /** Richtung der nächsten Stufe. */
    private static int wahl(int x, int y, int z, int zx, int zy, int zz, int dir, int gleich, int n, int anfang, int seite, int drehung) {
        int dx = zx - x, dz = zz - z, tief = y - zy;
        if (anfang >= 0 && n < ANFANG) {
            return anfang;
        }
        int gerade = geradeRichtung(dx, dz, dir);
        boolean passt = gerade >= 0 && (dir < 0 || gerade != ((dir + 2) & 3))
                && Math.max(0, Math.abs(dx) + Math.abs(dz) - ecken(dx, dz, dir, gerade)) >= tief
                // nach dem Abbiegen mindestens drei Stufen geradeaus (kein Zickzack)
                && (gerade == dir || dir < 0 || gleich >= 3);
        if (passt) {
            return gerade;
        }
        if (dir < 0) {
            return gerade >= 0 ? gerade : 0;
        }
        return gleich >= seite ? (dir + drehung) & 3 : dir;       // Wendel (1 = im Uhrzeigersinn, 3 = dagegen)
    }

    /** Richtung für den geraden Weg: die bisherige, solange es in ihr noch weitergeht, sonst die Achse mit mehr Rest. */
    private static int geradeRichtung(int dx, int dz, int dir) {
        if (dir >= 0 && DX[dir] * dx + DZ[dir] * dz > 0) {
            return dir;
        }
        if (dx == 0 && dz == 0) {
            return -1;
        }
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx > 0 ? 1 : 3;
        }
        return dz > 0 ? 2 : 0;
    }

    /** Ebene Stufen, die der gerade Weg noch braucht (je Abbiegen zwei: Absatz davor und danach). */
    private static int ecken(int dx, int dz, int dir, int gerade) {
        int e = dir >= 0 && gerade != dir ? 2 : 0;
        boolean zweiteAchse = gerade == 1 || gerade == 3 ? dz != 0 : dx != 0;
        return e + (zweiteAchse ? 2 : 0);
    }

    // ---------------- Zellen des Gangs ----------------

    public static long schluessel(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    /** Alle Zellen, die im Gang frei bleiben (drei Spuren, Höhe 0–2 über der Stufe). */
    public static Set<Long> gang(List<Stufe> plan) {
        Set<Long> g = new HashSet<>();
        for (Stufe s : plan) {
            for (int w = -1; w <= 1; w++) {
                for (int h = 0; h <= 2; h++) {
                    g.add(schluessel(s.x + s.querX() * w, s.y + h, s.z + s.querZ() * w));
                }
            }
        }
        return g;
    }

    /** Anzahl Bodenzellen, die im Gang einer anderen Stufe liegen (wären dort ein Loch) – 0 = Plan in Ordnung. */
    public static int konflikte(List<Stufe> plan) {
        Set<Long> g = gang(plan);
        int n = 0;
        for (Stufe s : plan) {
            for (int w = -1; w <= 1; w++) {
                if (g.contains(schluessel(s.x + s.querX() * w, s.y - 1, s.z + s.querZ() * w))) {
                    n++;
                }
            }
        }
        return n;
    }
}
