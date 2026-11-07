package de.moinmornhart.stinkendreich.strasse;

import de.moinmornhart.stinkendreich.butler.Schutz;
import de.moinmornhart.stinkendreich.butler.SpielerBloecke;
import de.moinmornhart.stinkendreich.villa.VillaPlatzierung;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Baut eine Straße Stück für Stück (in jedem Server-Tick einige Schritte, mit Zeitbudget, damit der Server
 * flüssig bleibt). Die Straße ist 7 Blöcke breit: grauer Asphalt, weiße Mittellinie in Strichen, Bordstein aus
 * glattem Stein, alle 32 Blöcke eine Laterne. Jeder Block neben der Mittellinie bekommt sein Material nach dem
 * Abstand zur Linie (Fahrbahn bis 2,5, Bordstein bis 3,5, Seitenstreifen bis 4,5), so bleibt die Straße auch
 * schräg und in Kurven gleich breit. Ein Block wird erst gesetzt, wenn die Linie an ihm vorbei ist. Sie folgt sanft dem Gelände (höchstens ein Block Höhe auf drei
 * Blöcke, Übergänge mit Stufen), schneidet durch Hügel (nur natürliche Blöcke werden abgetragen, Bäume über der
 * Fahrbahn verschwinden), schüttet kleine Senken mit Steinziegeln auf und führt als Brücke mit Geländer und Pfeilern
 * über Wasser und tiefe Täler.
 *
 * <p>Nie angerührt werden: die Villa, Blöcke mit Inhalt (Truhen …) und alles, was nicht natürlich ist (Häuser im
 * Dorf, Spielerbauten). Liegt so etwas im Weg, biegt die Straße aus; kurz vor dem Ziel endet sie dort.</p>
 */
public final class StrassenBau {
    /** Halbe Breite: Mitte ± 3 Blöcke. */
    static final int HALB = 3;
    private static final int LATERNE_ALLE = 32;
    /** Mittellinie: so viele Blöcke Strich, dann ebenso viele Lücke. */
    private static final int STRICH = 4;
    /** Ein Block gilt als fertig, wenn die Linie so viele Schritte nicht mehr in seiner Nähe war. */
    private static final int FERTIG_NACH = 12;
    /** So weit darf die Fahrbahn über dem Boden liegen, bevor sie zur Brücke wird (darunter wird aufgeschüttet). */
    private static final int AUFSCHUETTEN_BIS = 6;
    /** Höchstens so viele Querschnitte je Tick und so viel Zeit (Nanosekunden). */
    private static final int PRO_TICK = 8;
    private static final long BUDGET = 25_000_000L;
    /** Stärkste Kurve je Block (Grad). */
    private static final float KURVE = 4.0F;

    // Beton über die ID (die Farbblöcke heißen im Code je nach Version anders)
    static final BlockState ASPHALT = id("gray_concrete");
    /** Halbe Stufe in Asphaltfarbe (auch auf der Mittellinie, sonst wirkt die Steigung fleckig). */
    private static final BlockState ASPHALT_STUFE = Blocks.POLISHED_DEEPSLATE_SLAB.defaultBlockState();
    static final BlockState LINIE = id("white_concrete");
    /** Stufen älterer Straßen (vor 0.3.0), damit Abzweigungen sie überbauen dürfen. */
    private static final BlockState ALT_STUFE_1 = Blocks.POLISHED_ANDESITE_SLAB.defaultBlockState();
    private static final BlockState ALT_STUFE_2 = Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState();
    private static final BlockState BORD = Blocks.SMOOTH_STONE.defaultBlockState();
    private static final BlockState BORD_STUFE = Blocks.SMOOTH_STONE_SLAB.defaultBlockState();
    private static final BlockState UNTERBAU = Blocks.STONE_BRICKS.defaultBlockState();
    private static final BlockState GELAENDER = Blocks.STONE_BRICK_WALL.defaultBlockState();
    private static final BlockState MAST = Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState();
    private static final BlockState LAMPE = Blocks.LANTERN.defaultBlockState();

    private final ServerLevel welt;
    private final String name;
    private final Vec3 ziel;
    private final int eltern, abzweig;
    private final int maxSchritte;
    private double x, z;
    private float richtung;
    private int y;
    private int schritte, letzteAenderung;
    private boolean fertig;
    /** Mittellinie (Fahrbahnblock je Schritt). */
    private final List<BlockPos> punkte = new ArrayList<>();
    /** Genaue Lage der Mittellinie je Schritt (x, z) für die Abstände. */
    private final List<double[]> linie = new ArrayList<>();
    /** Noch offene Spalten: Abstand zur Linie, nächster Schritt, zuletzt berührt. */
    private final Map<Long, double[]> offen = new HashMap<>();
    /** Fertige Spalten mit ihrem Abstand zur Linie (für Laternen und damit nichts doppelt gebaut wird). */
    private final Map<Long, Double> gebaut = new HashMap<>();
    /** Laternen, die nach dem Fertigbauen ihrer Umgebung kommen: Schritt, x, z. */
    private final List<int[]> laternen = new ArrayList<>();
    private final Map<Long, int[]> bodenCache = new HashMap<>();
    private final double startAbstand;
    /** Was zuletzt den Weg versperrt hat (für die Fehlersuche im Log). */
    private String letzteSperre = "";
    /** Chunks, die für den Bau mit einem Lade-Ticket gehalten werden (Schlüssel aus x und z, siehe {@link #chunk}). */
    private final java.util.Set<Long> gehalten = new java.util.HashSet<>();
    /** So viele Ticks wurde schon auf Chunks gewartet (danach wird notfalls direkt geladen). */
    private int wartet;
    /** Chunks rund um die Spitze, die geladen sein müssen (2 = 5 × 5 Chunks, 32 Blöcke in jede Richtung). */
    private static final int UMKREIS = 2;

    /** Fahrbahnhöhe am Ziel (Ende der Zufahrt einer Villa), NaN = unbekannt. */
    private double zielY = Double.NaN;
    /** Punkt vor der Zufahrt, über den die Straße ans Ziel kommt (von vorn statt seitlich), null = direkt. */
    private Vec3 anfahrt;

    StrassenBau(ServerLevel welt, String name, Vec3 ziel, Vec3 start, float richtung, int eltern, int abzweig) {
        this.welt = welt;
        this.name = name;
        this.ziel = ziel;
        this.x = start.x;
        this.z = start.z;
        this.richtung = richtung;
        this.eltern = eltern;
        this.abzweig = abzweig;
        this.y = (int) Math.floor(start.y) - 1;
        this.startAbstand = Math.hypot(ziel.x - x, ziel.z - z);
        this.maxSchritte = (int) (startAbstand * 1.8) + 200;
    }

    public String name() {
        return name;
    }

    /**
     * Die Straße soll über diesen Punkt ans Ziel kommen (vor der Zufahrt einer Villa): Seitlich kam sie an deren
     * Randsteinen nicht vorbei und endete zehn Blöcke vor dem Ziel.
     */
    void anfahrt(Vec3 p) {
        anfahrt = p;
    }

    /** Die Straße endet an einer Zufahrt mit dieser Fahrbahnhöhe: kurz davor rechtzeitig darauf einschwenken. */
    void zielHoehe(double y) {
        zielY = y;
    }

    /** Ist die Straße näher als {@code abstand} an einem Ziel mit bekannter Höhe? */
    private boolean naheZiel(double abstand) {
        return !Double.isNaN(zielY) && Math.hypot(ziel.x - x, ziel.z - z) < abstand;
    }

    public boolean fertig() {
        return fertig;
    }

    public List<BlockPos> punkte() {
        return punkte;
    }

    int eltern() {
        return eltern;
    }

    int abzweig() {
        return abzweig;
    }

    /** Fortschritt 0 … 100 (nach Entfernung zum Ziel). */
    public int prozent() {
        if (fertig) {
            return 100;
        }
        double rest = Math.hypot(ziel.x - x, ziel.z - z);
        return (int) Math.max(0, Math.min(99, 100 - rest * 100 / Math.max(1, startAbstand)));
    }

    // ------------------------------------------------------------------
    // Ablauf
    // ------------------------------------------------------------------

    void tick() {
        long start = System.nanoTime();
        for (int i = 0; i < PRO_TICK && !fertig && System.nanoTime() - start < BUDGET; i++) {
            if (!umgebungBereit()) {
                return;                                        // nächsten Tick weiter, Minecraft erzeugt die Chunks
            }
            schritt();
        }
    }

    /**
     * Fordert die Chunks rund um die Spitze im Hintergrund an (Lade-Ticket) und meldet, ob alle schon da sind. Vorher
     * lud der Bau jeden neuen Chunk sofort und blockierend: Beim ersten Betreten einer Welt (vier Straßen auf einmal)
     * ruckelte der Server dadurch spürbar. Nach 30 s Warten geht es notfalls mit direktem Laden weiter.
     */
    private boolean umgebungBereit() {
        int cx0 = (int) Math.floor(x) >> 4, cz0 = (int) Math.floor(z) >> 4;
        boolean alle = true;
        for (int cx = cx0 - UMKREIS; cx <= cx0 + UMKREIS; cx++) {
            for (int cz = cz0 - UMKREIS; cz <= cz0 + UMKREIS; cz++) {
                if (gehalten.add(chunk(cx, cz))) {
                    welt.getChunkSource().updateChunkForced(new net.minecraft.world.level.ChunkPos(cx, cz), true);
                }
                if (welt.getChunkSource().getChunkNow(cx, cz) == null) {
                    alle = false;
                }
            }
        }
        // Weit hinten liegende Chunks wieder freigeben (die Straße dahinter ist fertig gebaut)
        gehalten.removeIf(k -> {
            int cx = (int) (long) k, cz = (int) (k >> 32);
            if (Math.max(Math.abs(cx - cx0), Math.abs(cz - cz0)) > UMKREIS + 2) {
                welt.getChunkSource().updateChunkForced(new net.minecraft.world.level.ChunkPos(cx, cz), false);
                return true;
            }
            return false;
        });
        if (alle) {
            wartet = 0;
            return true;
        }
        return ++wartet > 20 * 30;
    }

    private static long chunk(int cx, int cz) {
        return (cx & 0xFFFFFFFFL) | ((long) cz << 32);
    }

    /** Alle Lade-Tickets des Baus zurückgeben (auch beim Beenden des Servers, sonst blieben die Chunks geladen). */
    void freigeben() {
        for (long k : gehalten) {
            welt.getChunkSource().updateChunkForced(new net.minecraft.world.level.ChunkPos((int) k, (int) (k >> 32)), false);
        }
        gehalten.clear();
    }

    private void schritt() {
        double rest = Math.hypot(ziel.x - x, ziel.z - z);
        if (rest < 5 || schritte > maxSchritte) {
            ende(rest < 5 ? "am Ziel" : "zu viele Schritte");
            return;
        }
        // Erst zum Anfahrtspunkt (falls es einen gibt), dann zum Ziel; ist die Straße schon auf Höhe des Ziels (vor der
        // Zufahrt), direkt hin – sonst kurvte sie dicht vor dem Ziel noch zum Anfahrtspunkt dahinter
        if (anfahrt != null && (Math.hypot(anfahrt.x - x, anfahrt.z - z) < 6 || z >= ziel.z - 2)) {
            anfahrt = null;
        }
        Vec3 lenk = anfahrt != null ? anfahrt : ziel;
        double lenkRest = Math.hypot(lenk.x - x, lenk.z - z);
        // Richtung: sanft zum Ziel einlenken, Hindernissen (Villa, Häuser, Spielerbauten) ausweichen
        float zum = (float) Strassennetz.gier(x, z, lenk.x, lenk.z);
        float wunsch = richtung + clamp((float) Strassennetz.wrap(zum - richtung), -KURVE, KURVE);
        // Freie Richtung nahe am Ziel; steile Hänge umfahren wie eine echte Bergstraße (nur wenn es geradeaus deutlich
        // bergauf oder bergab ginge und eine Abweichung flacher ist)
        Float frei = null;
        double besteKosten = Double.MAX_VALUE;
        // Nie im Kreis: erst nur Richtungen, die höchstens 100° vom Ziel weg zeigen (ausweichen ja, umkehren nein) –
        // sonst wich die Straße an einem Hang immer weiter aus, fuhr eine Schleife und blieb an sich selbst hängen.
        // Nur wenn dort alles versperrt ist, darf sie auch weiter abdrehen.
        boolean dorfrand = false;
        for (int durchgang = 0; durchgang < 2 && frei == null && !dorfrand; durchgang++) {
            // Im zweiten Durchgang auch scharf abdrehen (nur wenn vorn alles versperrt ist: von einer Kante freikommen)
            float[] abweichungen = durchgang == 0 ? new float[]{0, 15, -15, 30, -30, 50, -50, 75, -75}
                    : new float[]{0, 15, -15, 30, -30, 50, -50, 75, -75, 105, -105, 135, -135};
            for (float abw : abweichungen) {
                float r = wunsch + abw;
                float weg = Math.abs((float) Strassennetz.wrap(r - zum));
                if ((durchgang == 0 && weg > 100) || !frei(r)) {
                    continue;
                }
                if (Math.abs(abw) >= 30 && rest < 48 && frei == null) {
                    dorfrand = true;
                    break;      // Am Dorfrand nicht um die Häuser kurven: hier endet die Straße
                }
                double steigung = Math.max(0, steigung(r) - 3);
                boolean fern = fernGesperrt(r, lenkRest);
                double kosten = steigung * 4 + Math.abs(abw) * 0.12 + Math.max(0, weg - 45) * 0.15 + (fern ? 25 : 0);
                if (kosten < besteKosten) {
                    besteKosten = kosten;
                    frei = r;
                }
                if (steigung == 0 && weg <= 45 && !fern) {
                    break;      // flach genug: die erste (zielnächste) freie Richtung nehmen
                }
            }
        }
        if (frei == null) {
            // Kurz vor dem Ziel (etwa am Dorfrand) endet die Straße hier, sonst bricht der Bau ab; höchstens acht Blöcke
            // vor dem Ziel ist sie angeschlossen (die Fahrbahn ist sieben Blöcke breit, etwa an der Zufahrt einer Villa)
            ende(rest < 8 ? "am Ziel" : "kein freier Weg (Rest " + (int) rest + ", Sperre " + letzteSperre + ")");
            return;
        }
        // Große Ausweichschwenks nur schrittweise fahren (weite Kurven, die der Wagen sauber schafft)
        float neu = richtung + clamp((float) Strassennetz.wrap(frei - richtung), -1.5F * KURVE, 1.5F * KURVE);
        // Geprüft ist die gewählte Richtung, gefahren wird die gedrosselte: Wäre die versperrt, sofort die geprüfte
        // nehmen (sonst rutschte die Straße in der weiten Kurve in eine Sperre, etwa an die Kante eines freien Platzes)
        richtung = frei(neu) ? neu : frei;
        x += -Math.sin(Math.toRadians(richtung));
        z += Math.cos(Math.toRadians(richtung));

        // Höhe: Mittel des Geländes der nächsten Blöcke, höchstens ein Block Wechsel auf drei Blöcke; erst ab einem
        // deutlichen Unterschied (sonst geht es auf welligem Boden ständig auf und ab)
        double summe = 0;
        int n = 0;
        int ueberWasser = Integer.MIN_VALUE;
        for (int d = 0; d <= 12; d += 2) {
            int bx = (int) Math.floor(x - Math.sin(Math.toRadians(richtung)) * d);
            int bz = (int) Math.floor(z + Math.cos(Math.toRadians(richtung)) * d);
            int[] b = boden(bx, bz);
            summe += b[1] == 1 ? b[0] + 1 : b[0];
            n++;
            if (b[1] == 1) {
                ueberWasser = Math.max(ueberWasser, b[0] + 1);
            }
        }
        // Über Wasser liegt die Fahrbahn immer mindestens einen Block über der Oberfläche (rechtzeitig ansteigen)
        double soll = Math.max(summe / n, ueberWasser);
        // Kurz vor dem Ziel auf dessen Höhe einschwenken (ein Block auf drei, rechtzeitig): Sonst kam die Straße zu hoch
        // oder zu tief an der Zufahrt an, fand keinen Anschluss und kreiste um das Ziel
        if (naheZiel(Math.abs(y - zielY) * 3 + 10)) {
            soll = zielY;
        }
        if (schritte - letzteAenderung >= 3 && Math.abs(soll - y) > 0.75) {
            y += soll > y ? 1 : -1;
            letzteAenderung = schritte;
        }
        weiter();
        schritte++;
    }

    /** Größter Höhenunterschied zwischen Fahrbahn und Gelände in Richtung {@code r} (4 bis 16 Blöcke voraus). */
    private int steigung(float r) {
        double rx = -Math.sin(Math.toRadians(r)), rz = Math.cos(Math.toRadians(r));
        int max = 0;
        for (int d = 4; d <= 16; d += 4) {
            int[] b = boden((int) Math.floor(x + rx * d), (int) Math.floor(z + rz * d));
            int boden = b[1] == 1 ? b[0] + 1 : b[0];
            // Erlaubt ist ein Block auf drei; was darüber hinausgeht, zählt
            max = Math.max(max, Math.abs(boden - y) - d / 3);
        }
        return max;
    }

    /** Ist die Richtung frei (die nächsten zehn Blöcke kein geschützter Block in Fahrbahnhöhe)? */
    private boolean frei(float r) {
        double rx = -Math.sin(Math.toRadians(r)), rz = Math.cos(Math.toRadians(r));
        for (int d = 2; d <= 10; d += 2) {
            for (int o = -HALB; o <= HALB; o += HALB) {
                int bx = (int) Math.floor(x + rx * d + rz * o), bz = (int) Math.floor(z + rz * d - rx * o);
                for (int h = 0; h <= 4; h++) {
                    // Eine andere Straße höher als diese: nicht darunter hindurch (sonst entsteht eine Unterführung)
                    // (außer am Ziel: die Zufahrt, an die sie anschließt, darf höher liegen)
                    // Ebenso nicht dicht über eine andere Straße hinweg (1–4 Blöcke höher): die neue Fahrbahn läge in deren
                    // Lichtraum; auf gleicher Höhe ist es eine Kreuzung, ab 5 Blöcken eine Brücke
                    if (h >= 1 && o == 0 && !naheZiel(16)) {
                        BlockState unten = welt.getBlockState(new BlockPos(bx, y - h, bz));
                        if (unten.is(ASPHALT.getBlock()) || unten.is(LINIE.getBlock())) {
                            letzteSperre = "andere Straße darunter bei " + bx + " " + (y - h) + " " + bz;
                            return false;
                        }
                    }
                    if (h >= 1 && o == 0 && unterbauOderFahrbahn(welt.getBlockState(new BlockPos(bx, y + h, bz))) && !naheZiel(16)) {
                        letzteSperre = "andere Straße darüber bei " + bx + " " + (y + h) + " " + bz;
                        return false;
                    }
                    if (geschuetzt(new BlockPos(bx, y + h, bz))) {
                        BlockPos q = new BlockPos(bx, y + h, bz);
                        letzteSperre = welt.getBlockState(q) + " bei " + bx + " " + (y + h) + " " + bz
                                + " villa=" + VillaPlatzierung.villa().map(v -> v.enthaelt(q)).orElse(false)
                                + " gesetzt=" + SpielerBloecke.istGesetzt(welt, q) + " be=" + welt.getBlockEntity(q);
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** Der nächste Punkt der Mittellinie: Spalten in der Nähe vormerken, vorbeigezogene bauen. */
    private void weiter() {
        int i = punkte.size();
        punkte.add(BlockPos.containing(x, y, z));
        linie.add(new double[]{x, z});
        if (i > 0) {
            double[] a = linie.get(i - 1), b = linie.get(i);
            int r = HALB + 2;
            for (int bx = (int) Math.floor(Math.min(a[0], b[0])) - r; bx <= (int) Math.floor(Math.max(a[0], b[0])) + r; bx++) {
                for (int bz = (int) Math.floor(Math.min(a[1], b[1])) - r; bz <= (int) Math.floor(Math.max(a[1], b[1])) + r; bz++) {
                    long k = BlockPos.asLong(bx, 0, bz);
                    // Abstand der Blockmitte zum Stück a–b; hinter dem Anfang der Straße nichts bauen
                    double vx = b[0] - a[0], vz = b[1] - a[1], wx = bx + 0.5 - a[0], wz = bz + 0.5 - a[1];
                    double t = (wx * vx + wz * vz) / Math.max(1e-6, vx * vx + vz * vz);
                    if (i == 1 && t < 0) {
                        continue;
                    }
                    t = Math.max(0, Math.min(1, t));
                    double d = Math.hypot(wx - vx * t, wz - vz * t);
                    // Schon gebaut? Nur neu, wenn die Straße hier jetzt näher vorbeiführt (etwa in einer Kehre: dann wird
                    // aus Bordstein und Geländer der ersten Spur Fahrbahn)
                    Double schon = gebaut.get(k);
                    if (d > HALB + 1.5 || (schon != null && schon <= d + 0.01)) {
                        continue;
                    }
                    int naechster = t < 0.5 ? i - 1 : i;
                    double[] alt = offen.get(k);
                    if (alt == null || d < alt[0]) {
                        offen.put(k, new double[]{d, naechster, i});
                    } else {
                        alt[2] = i;
                    }
                }
            }
        }
        // Laterne am Rand, abwechselnd links und rechts
        if (i % LATERNE_ALLE == LATERNE_ALLE / 2) {
            double rx = -Math.sin(Math.toRadians(richtung)), rz = Math.cos(Math.toRadians(richtung));
            int seite = (i / LATERNE_ALLE) % 2 == 0 ? 1 : -1;
            laternen.add(new int[]{i, (int) Math.floor(x + rz * (HALB + 1) * seite), (int) Math.floor(z - rx * (HALB + 1) * seite)});
        }
        bauen(i - FERTIG_NACH);
    }

    /** Baut alle Spalten, an denen die Linie seit {@code bis} nicht mehr war, und die Laternen dahinter. */
    private void bauen(int bis) {
        var it = offen.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            double[] v = e.getValue();
            if (v[2] <= bis) {
                spalte(BlockPos.getX(e.getKey()), BlockPos.getZ(e.getKey()), v[0], (int) v[1]);
                gebaut.put(e.getKey(), v[0]);
                it.remove();
            }
        }
        while (!laternen.isEmpty() && laternen.get(0)[0] <= bis - FERTIG_NACH) {
            int[] l = laternen.remove(0);
            laterne(l[1], l[2]);
        }
    }

    private void ende(String grund) {
        de.moinmornhart.stinkendreich.StinkendReich.LOGGER.info("Straße {}: Ende nach {} Schritten bei {} {} {}: {}", name, schritte,
                (int) x, y, (int) z, grund);
        abschliessen();
    }

    /** Ende: alles Offene bauen. */
    private void abschliessen() {
        fertig = true;
        bauen(Integer.MAX_VALUE);
        freigeben();
    }

    /**
     * Eine Spalte im Abstand {@code d} zur Mittellinie (nächster Schritt {@code i}): Fahrbahn, Bordstein oder
     * Seitenstreifen, darunter Unterbau, darüber frei. Liegt dort schon eine andere Straße, bleibt ihre Fahrbahn
     * offen (Abzweigung, Kreisel).
     */
    private void spalte(int bx, int bz, double d, int i) {
        int y = punkte.get(i).getY();
        int[] b = boden(bx, bz);
        int grund = b[0];
        boolean wasser = b[1] == 1;
        boolean fahrbahn = d <= HALB - 0.5, bord = !fahrbahn && d <= HALB + 0.5, seite = !fahrbahn && !bord;
        boolean bruecke = wasser || y - grund > AUFSCHUETTEN_BIS;
        BlockPos oben = new BlockPos(bx, y, bz);
        BlockState vorher = welt.getBlockState(oben);
        boolean andereFahrbahn = vorher.is(ASPHALT.getBlock()) || vorher.is(LINIE.getBlock()) || vorher.is(ASPHALT_STUFE.getBlock());
        // Halbe Stufe auf der tieferen Seite eines Höhenwechsels
        boolean stufe = (i + 1 < punkte.size() && punkte.get(i + 1).getY() > y) || (i > 0 && punkte.get(i - 1).getY() > y);
        boolean gelaender = bord && bruecke;
        if (fahrbahn) {
            // Geländer, Bordstein-Stufe oder Laternenmast einer anderen Spur stehen hier im Weg
            BlockState ueber = welt.getBlockState(oben.above());
            if (ueber.is(GELAENDER.getBlock()) || ueber.is(BORD_STUFE.getBlock()) || ueber.is(MAST.getBlock())) {
                for (int h = 1; h <= 4 && istStrasse(welt.getBlockState(oben.above(h))); h++) {
                    welt.setBlock(oben.above(h), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        if (fahrbahn || (bord && !andereFahrbahn)) {
            boolean strich = d < 0.5 && (i / STRICH) % 2 == 0;
            setzen(oben, bord ? BORD : (strich && !stufe ? LINIE : ASPHALT));
            if (bruecke) {
                if (gelaender && i % 8 == 0) {
                    // Pfeiler bis zum Grund
                    for (int h = y - 1; h >= Math.max(grund - (wasser ? 8 : 0), y - 64); h--) {
                        if (ueberFahrbahn(bx, h, bz) || !setzen(new BlockPos(bx, h, bz), UNTERBAU)) {
                            break;
                        }
                    }
                }
            } else {
                for (int h = y - 1; h > grund; h--) {
                    if (ueberFahrbahn(bx, h, bz)) {
                        break;
                    }
                    setzen(new BlockPos(bx, h, bz), UNTERBAU);
                }
            }
            if (gelaender) {
                setzen(oben.above(), GELAENDER);
            } else if (stufe) {
                // Erst Hang-Erde über der Stufe wegräumen: Sonst blieb hier eine Reihe Erde quer über der Fahrbahn stehen
                // (eine ganze Blockstufe statt einer halben, das Auto knallte dagegen)
                abtragen(oben.above());
            }
            if (!gelaender && stufe && (welt.getBlockState(oben.above()).isAir() || welt.getBlockState(oben.above()).canBeReplaced())) {
                setzen(oben.above(), (bord ? BORD_STUFE : ASPHALT_STUFE).setValue(SlabBlock.TYPE, SlabType.BOTTOM));
            }
        } else if (seite && !bruecke && !andereFahrbahn && !istStrasse(vorher)) {
            // Seitenstreifen: Gelände auf Fahrbahnhöhe abtragen oder flach aufschütten, oben Gras
            for (int h = grund; h > y; h--) {
                abtragen(new BlockPos(bx, h, bz));
            }
            if (grund < y && y - grund <= 3) {
                for (int h = grund + 1; h < y; h++) {
                    setzen(new BlockPos(bx, h, bz), Blocks.DIRT.defaultBlockState());
                }
            }
            if ((grund >= y || y - grund <= 3) && Schutz.istNatuerlich(welt.getBlockState(oben)) && !geschuetzt(oben)) {
                setzen(oben, Blocks.GRASS_BLOCK.defaultBlockState());
            }
        }
        // Darüber freiräumen (Bäume über der Fahrbahn ganz). Durch Hügel geht es als offener Einschnitt bis zum Himmel,
        // nie als Tunnel: alles Natürliche über der Straße bis zur Oberfläche kommt weg (höchstens 48 Blöcke)
        boolean besetzt = gelaender || (stufe && !seite);
        int himmel = Math.min(y + 48, Math.max(y + 6, welt.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz) - 1));
        for (int h = y + (besetzt ? 2 : 1); h <= himmel; h++) {
            abtragen(new BlockPos(bx, h, bz));
        }
        if (!bruecke) {
            wasserAbdichten(bx, bz, y, seite, himmel);
        }
        if (!seite) {
            for (int h = y + 7; h <= y + 20; h++) {
                BlockPos p = new BlockPos(bx, h, bz);
                BlockState s = welt.getBlockState(p);
                if (s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS)) {
                    abtragen(p);
                }
            }
        }
    }

    private void laterne(int bx, int bz) {
        Double d = gebaut.get(BlockPos.asLong(bx, 0, bz));
        if (d != null && d <= HALB + 0.5) {
            return;     // In einer Kurve läge der Mast auf der Fahrbahn
        }
        int y = hoeheBei(bx, bz);
        BlockPos fuss = new BlockPos(bx, y, bz);
        if (geschuetzt(fuss)) {
            return;
        }
        setzen(fuss, BORD);
        for (int h = y - 1; h > boden(bx, bz)[0] && h > y - 8; h--) {
            setzen(new BlockPos(bx, h, bz), UNTERBAU);
        }
        for (int h = 1; h <= 3; h++) {
            setzen(fuss.above(h), MAST);
        }
        setzen(fuss.above(4), LAMPE);
    }

    /** Fahrbahnhöhe am nächsten Punkt der Mittellinie. */
    private int hoeheBei(int bx, int bz) {
        BlockPos best = punkte.get(punkte.size() - 1);
        double bd = Double.MAX_VALUE;
        for (BlockPos p : punkte) {
            double d = (p.getX() - bx) * (double) (p.getX() - bx) + (p.getZ() - bz) * (double) (p.getZ() - bz);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best.getY();
    }

    // ------------------------------------------------------------------
    // Blöcke
    // ------------------------------------------------------------------

    /**
     * Höhe des Bodens in der Spalte: oberster fester Block ohne Blätter, Stämme und Pflanzen. [0] = Höhe,
     * [1] = 1, wenn dort Wasser oder Lava ist (dann ist [0] die Oberfläche).
     */
    private int[] boden(int bx, int bz) {
        return bodenCache.computeIfAbsent(BlockPos.asLong(bx, 0, bz), k -> {
            // Erst laden: Für ungeladene Stücke der Welt meldet Minecraft die tiefste Höhe (−64). Die Straße tauchte
            // dann Block für Block in die Erde ab, und Meer davor wurde nicht erkannt (keine Brücke).
            welt.getChunk(bx >> 4, bz >> 4);
            int top = welt.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz) - 1;
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(bx, top, bz);
            for (int h = top; h > top - 48; h--) {
                m.setY(h);
                BlockState s = welt.getBlockState(m);
                if (!s.getFluidState().isEmpty() && !s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)) {
                    return new int[]{h, 1};
                }
                if (s.isAir() || s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || s.canBeReplaced() || s.getCollisionShape(welt, m).isEmpty()) {
                    continue;
                }
                return new int[]{h, 0};
            }
            return new int[]{top, 0};
        });
    }

    /** Darf hier nichts verändert werden? Villa, Blöcke mit Inhalt, Gesetztes und alles Gebaute. */
    private boolean geschuetzt(BlockPos p) {
        // Landminen im Rasen der Festung: Die Straße darf über sie hinweg (die Mine unter der Fahrbahn fällt weg)
        if (welt.getBlockState(p).is(de.moinmornhart.stinkendreich.register.ModBloecke.LANDMINE)) {
            return false;
        }
        // Freigehaltene Plätze für künftige Villen: drumherum (außer die Straße will genau dorthin, etwa in ein Dorf)
        int[] platz = VillaPlatzierung.reservierterPlatz(p.getX(), p.getZ());
        if (platz != null && !zielBei(platz)) {
            return true;
        }
        VillaPlatzierung.VillaInfo villa = VillaPlatzierung.bei(p);           // jede Villa, nicht nur die am Spawn
        if ((villa != null && villa.enthaelt(p)) || SpielerBloecke.istGesetzt(welt, p)
                || welt.getBlockEntity(p) != null) {
            return true;
        }
        BlockState s = welt.getBlockState(p);
        return !s.isAir() && s.getFluidState().isEmpty() && !Schutz.istNatuerlich(s) && !istStrasse(s);
    }

    /**
     * Weitblick: Führt die Richtung in den nächsten 48 Blöcken (höchstens bis zum Ziel) auf einen freigehaltenen Platz
     * oder ein Grundstück? Diese großen Flächen sind bekannt; die Straße biegt früh ab und fährt außen entlang, statt
     * erst zehn Blöcke davor zu merken, dass es nicht weitergeht (frontal kam sie sonst nicht mehr herum).
     */
    private boolean fernGesperrt(float r, double rest) {
        double rx = -Math.sin(Math.toRadians(r)), rz = Math.cos(Math.toRadians(r));
        // die ganze Breite der Straße und zwei Blöcke Rand (nur die Mittellinie: Sie fuhr dicht an der Kante entlang,
        // bis ihr Rand anstieß)
        for (int d = 12; d <= Math.min(48, rest); d += 6) {
            for (int o = -HALB - 2; o <= HALB + 2; o += HALB + 2) {
            int bx = (int) Math.floor(x + rx * d + rz * o), bz = (int) Math.floor(z + rz * d - rx * o);
            int[] platz = VillaPlatzierung.reservierterPlatz(bx, bz);
            if (platz != null && !zielBei(platz)) {
                return true;
            }
            BlockPos p = new BlockPos(bx, y, bz);
            VillaPlatzierung.VillaInfo villa = VillaPlatzierung.bei(p);
            if (villa != null && villa.inDerNaehe(p, 4)) {
                return true;
            }
            }
        }
        return false;
    }

    /** Liegt das Ziel dieser Straße auf (oder dicht an) einem freigehaltenen Platz? Dann darf sie hinein. */
    private boolean zielBei(int[] platz) {
        int rand = 48;
        return ziel.x >= platz[0] - rand && ziel.x < platz[2] + rand && ziel.z >= platz[1] - rand && ziel.z < platz[3] + rand;
    }

    /**
     * Liegt die Stelle im Lichtraum einer anderen Straße (höchstens 4 Blöcke über deren Fahrbahn)? Dort kommt kein
     * Unterbau und kein Pfeiler hin – sonst standen Steinziegel mitten auf der unteren Straße (Philips Bild vom 07.11.:
     * die Limo zwängte sich zwischen Pfeilern unter einer Brücke hindurch).
     */
    private boolean ueberFahrbahn(int bx, int h, int bz) {
        for (int d = 1; d <= 4; d++) {
            BlockState s = welt.getBlockState(new BlockPos(bx, h - d, bz));
            if (s.is(ASPHALT.getBlock()) || s.is(LINIE.getBlock()) || s.is(ASPHALT_STUFE.getBlock())) {
                return true;
            }
        }
        return false;
    }

    /** Fahrbahn, Bordstein oder Unterbau einer Straße (nicht Stufen, Geländer, Laternen). */
    private static boolean unterbauOderFahrbahn(BlockState s) {
        return s.is(ASPHALT.getBlock()) || s.is(LINIE.getBlock()) || s.is(BORD.getBlock()) || s.is(UNTERBAU.getBlock());
    }

    /** Teil einer (anderen) Straße: darf überbaut werden (Abzweigungen, Kreuzungen). */
    private static boolean istStrasse(BlockState s) {
        return s.is(ASPHALT.getBlock()) || s.is(LINIE.getBlock()) || s.is(BORD.getBlock()) || s.is(UNTERBAU.getBlock())
                || s.is(GELAENDER.getBlock()) || s.is(ASPHALT_STUFE.getBlock()) || s.is(ALT_STUFE_1.getBlock()) || s.is(ALT_STUFE_2.getBlock())
                || s.is(BORD_STUFE.getBlock()) || s.is(MAST.getBlock()) || s.is(LAMPE.getBlock());
    }

    /** Setzt einen Block, wenn die Stelle nicht geschützt ist. */
    private boolean setzen(BlockPos p, BlockState s) {
        if (geschuetzt(p)) {
            return false;
        }
        welt.setBlock(p, s, Block.UPDATE_CLIENTS);
        return true;
    }

    /**
     * Wasser und Lava an einer Straße im Einschnitt (Höhlen, Grundwasser im Hang): Nachbarn außerhalb der Straße
     * werden zu einer Stützmauer, der Seitenstreifen auch; auf Fahrbahn und Bordstein wird die Flüssigkeit entfernt.
     * Sonst läuft sie beim nächsten Block-Update auf die Fahrbahn und der Wagen bleibt stecken.
     */
    private void wasserAbdichten(int bx, int bz, int y, boolean seite, int bis) {
        for (int h = y; h <= Math.max(y + 6, bis); h++) {
            BlockPos p = new BlockPos(bx, h, bz);
            for (net.minecraft.core.Direction r : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                BlockPos n = p.relative(r);
                long spalte = BlockPos.asLong(n.getX(), 0, n.getZ());
                if (!welt.getFluidState(n).isEmpty() && !gebaut.containsKey(spalte) && !offen.containsKey(spalte)) {
                    setzen(n, UNTERBAU);
                }
            }
            if (h > y && !welt.getFluidState(p).isEmpty() && !geschuetzt(p)) {
                setzen(p, seite ? UNTERBAU : Blocks.AIR.defaultBlockState());
            }
        }
    }

    /** Räumt einen natürlichen Block weg (Wasser bleibt, die Straße führt dann als Brücke darüber). */
    private void abtragen(BlockPos p) {
        BlockState s = welt.getBlockState(p);
        if (s.isAir() || !s.getFluidState().isEmpty() || geschuetzt(p) || istStrasse(s)) {
            return;
        }
        welt.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    private static BlockState id(String name) {
        return de.moinmornhart.stinkendreich.Kompat.blockNachId(net.minecraft.resources.Identifier.withDefaultNamespace(name)).defaultBlockState();
    }

    private static float clamp(float v, float a, float b) {
        return Math.max(a, Math.min(b, v));
    }

    // ------------------------------------------------------------------
    // Kreisel
    // ------------------------------------------------------------------

    /**
     * Kreisel vor dem Tor: Fahrbahn als Ring (gestrichelte Linie in der Mitte), Bordstein innen und außen, Insel mit
     * Rasen, Blumen und einer goldenen Säule mit Licht, dazu die Zufahrt nach Norden bis an die Brücke der Villa.
     */
    static void kreiselBauen(ServerLevel welt, Vec3 mitte) {
        int y = (int) Math.floor(mitte.y);
        StrassenBau b = new StrassenBau(welt, "Kreisel", mitte, mitte, 0, -1, 0);
        b.y = y;
        int r = (int) Math.ceil(Strassennetz.AUSSEN) + 2;
        int mx = (int) Math.floor(mitte.x), mz = (int) Math.floor(mitte.z);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int bx = mx + dx, bz = mz + dz;
                double d = Math.hypot(bx + 0.5 - mitte.x, bz + 0.5 - mitte.z);
                BlockPos p = new BlockPos(bx, y, bz);
                if (d > Strassennetz.AUSSEN + 1.5) {
                    continue;
                }
                int[] boden = b.boden(bx, bz);
                for (int h = y - 1; h > boden[0] && h > y - AUFSCHUETTEN_BIS; h--) {
                    b.setzen(new BlockPos(bx, h, bz), UNTERBAU);
                }
                for (int h = y + 1; h <= y + 6; h++) {
                    b.abtragen(new BlockPos(bx, h, bz));
                }
                if (d > Strassennetz.AUSSEN + 0.5) {
                    b.setzen(p, Blocks.GRASS_BLOCK.defaultBlockState());
                } else if (d > Strassennetz.AUSSEN - 0.5 || (d > Strassennetz.INSEL - 0.5 && d <= Strassennetz.INSEL + 0.5)) {
                    b.setzen(p, BORD);
                } else if (d > Strassennetz.INSEL + 0.5) {
                    // Gestrichelte Linie zwischen den beiden Spuren
                    double winkel = Math.toDegrees(Math.atan2(bz + 0.5 - mitte.z, bx + 0.5 - mitte.x));
                    boolean linie = Math.abs(d - Strassennetz.SPUR) < 0.5 && ((int) Math.floor((winkel + 180) / 12)) % 2 == 0;
                    b.setzen(p, linie ? LINIE : ASPHALT);
                } else {
                    b.setzen(p, Blocks.GRASS_BLOCK.defaultBlockState());
                    if (d > 1.5 && (dx + dz) % 2 == 0) {
                        b.setzen(p.above(), ((dx * 7 + dz * 3) & 3) == 0 ? Blocks.POPPY.defaultBlockState()
                                : ((dx * 7 + dz * 3) & 3) == 1 ? Blocks.CORNFLOWER.defaultBlockState() : Blocks.OXEYE_DAISY.defaultBlockState());
                    }
                }
            }
        }
        // Goldene Säule mit Licht in der Mitte der Insel
        BlockPos m = BlockPos.containing(mitte.x, y, mitte.z);
        for (BlockPos q : new BlockPos[]{m, m.west(), m.north(), m.west().north()}) {
            b.setzen(q, Blocks.SMOOTH_QUARTZ.defaultBlockState());
            for (int h = 1; h <= 3; h++) {
                b.setzen(q.above(h), h == 3 ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.GOLD_BLOCK.defaultBlockState());
            }
        }
        // Zufahrt nach Norden bis an die Brücke (8 breit wie die Brücke, mittig auf der Achse)
        VillaPlatzierung.villa().ifPresent(v -> {
            int vonZ = v.ursprung().getZ() + v.plan().breiteZ;
            int bisZ = (int) Math.floor(mitte.z - Strassennetz.AUSSEN) + 1;
            for (int bz = vonZ; bz <= bisZ; bz++) {
                for (int ox = -4; ox <= 3; ox++) {
                    int bx = (int) Math.floor(mitte.x) + ox;
                    BlockPos p = new BlockPos(bx, y, bz);
                    int[] boden = b.boden(bx, bz);
                    for (int h = y - 1; h > boden[0] && h > y - AUFSCHUETTEN_BIS; h--) {
                        b.setzen(new BlockPos(bx, h, bz), UNTERBAU);
                    }
                    for (int h = y + 1; h <= y + 6; h++) {
                        b.abtragen(new BlockPos(bx, h, bz));
                    }
                    b.setzen(p, ox == -4 || ox == 3 ? BORD : ASPHALT);
                }
            }
        });
    }
}
