package de.moinmornhart.stinkendreich.strasse;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.StinkendReich;
import de.moinmornhart.stinkendreich.butler.SpielerBloecke;
import de.moinmornhart.stinkendreich.villa.VillaPlatzierung;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Treppe vom Ende einer Straße hinunter ins Ziel (Philip, 07.11.: „wenn ich zur Mine gehe, dass ich dann auch direkt dahin
 * geleitet werde, also dass dann auch Treppen nach unten gehen. Das gleiche bei Netherportal und Endportal“).
 *
 * <p>Neu gebaut am 08.11. (Philip: „das muss wirklich frei sein, es muss nach unten gehen und es darf kein Wasser drin
 * sein“). Ablauf in Phasen:</p>
 * <ol>
 *   <li>SUCHEN: Chunk für Chunk um das Straßenende das Ziel suchen ({@link #SUCHE} Blöcke weit): beim Minenschacht eine
 *       Schiene oder ein Holzzaun, bei der Festung das Endportal (sonst ein Gang aus Steinziegeln), beim Netherportal
 *       der Portalblock.</li>
 *   <li>Weg planen ({@link TreppenPlan}): drei Blöcke breit, drei hoch, Absätze an den Ecken, nie Zickzack, kein Boden im
 *       Gang einer anderen Stufe.</li>
 *   <li>HUELLE: Boden (Marmorstufen), Wände (goldene Sockelleiste, Quarzziegel), Decke mit goldenen Lichtern.</li>
 *   <li>RAEUMEN: jede Zelle im Gang wird Luft – auch Wasser, Kies, Spinnweben.</li>
 *   <li>DICHTEN: jede Nachbarzelle des Gangs (auch schräg, an Ecken und Stirnseiten) wird geprüft: Wasser, Lava, Löcher
 *       (Höhlen), Sand/Kies und Pflanzen werden zugemauert; nur oben am offenen Einschnitt bleibt der Himmel frei.</li>
 *   <li>SCHMUCK: goldenes Tor am Straßenende. PRUEFEN: jede Stufe begehbar und trocken? Ergebnis im Protokoll.</li>
 * </ol>
 * <p>Danach hält {@link #pflegen} die Treppe in Spielernähe trocken und frei (Treppenwart, auch nach einem Neustart: der
 * Plan steht im Straßennetz-JSON). Spielerbauten, Truhen, Portale und Villen werden nie angefasst.</p>
 */
final class Treppenbau {
    /** So weit um das Straßenende wird das Ziel gesucht (Blöcke, waagerecht). */
    static final int SUCHE = 40;
    /** Stil der Treppen: ändert er sich, werden schon gebaute Treppen neu gebaut. */
    static final int STIL = 3;
    private static final int STUFEN_JE_TICK = 4;
    private static final int ZELLEN_JE_TICK = 500;
    private static final BlockState LICHT = Blocks.OCHRE_FROGLIGHT.defaultBlockState();
    private static final BlockState MARMOR = Blocks.SMOOTH_QUARTZ.defaultBlockState();
    private static final BlockState ZIEGEL = Blocks.QUARTZ_BRICKS.defaultBlockState();
    private static final BlockState GOLD = Blocks.GOLD_BLOCK.defaultBlockState();

    private enum Phase { SUCHEN, HUELLE, RAEUMEN, DICHTEN, SCHMUCK, FERTIG }

    private final ServerLevel welt;
    final String name;
    final String art;
    private final BlockPos start;
    /** Richtung der Straße am Ende (die Treppe geht zuerst geradeaus weiter), −1 = unbekannt. */
    private final int strassenRichtung;
    private final List<long[]> chunks = new ArrayList<>();
    private int chunkIndex;
    private BlockPos ziel;
    private double besteWertung = Double.MAX_VALUE;
    private List<TreppenPlan.Stufe> stufen;
    /** Zellen, die im Gang frei bleiben. */
    private final Set<Long> gang = new HashSet<>();
    private final List<BlockPos> gangListe = new ArrayList<>();
    /** Bodenzellen und ihr Block (Stufe oder ebener Marmor am Absatz). */
    private final Map<Long, BlockState> boden = new HashMap<>();
    /** Je Stufe: liegt sie offen unter dem Himmel (beim Planen gemessen, bevor die Decke steht)? */
    private boolean[] himmel;
    private Phase phase = Phase.SUCHEN;
    private int index;
    private int gedichtet;

    /**
     * @param art   „mine“, „endportal“ oder „netherportal“
     * @param ende  letzter Punkt der Straße (Fahrbahnhöhe)
     * @param strassenRichtung Richtung der Straße am Ende (0 = Norden, 1 = Osten, 2 = Süden, 3 = Westen), −1 = unbekannt
     */
    Treppenbau(ServerLevel welt, String name, String art, BlockPos ende, int strassenRichtung) {
        this.welt = welt;
        this.name = name;
        this.art = art;
        this.start = ende.above();
        this.strassenRichtung = strassenRichtung;
        int r = (SUCHE >> 4) + 1;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                chunks.add(new long[]{(ende.getX() >> 4) + dx, (ende.getZ() >> 4) + dz});
            }
        }
        // nahe Chunks zuerst
        chunks.sort(java.util.Comparator.comparingLong(c -> (c[0] - (ende.getX() >> 4)) * (c[0] - (ende.getX() >> 4))
                + (c[1] - (ende.getZ() >> 4)) * (c[1] - (ende.getZ() >> 4))));
    }

    /** Fertige Treppe aus dem Straßennetz-JSON (für den Treppenwart und die Testhilfen). */
    static Treppenbau ausJson(ServerLevel welt, JsonObject j) {
        Treppenbau t = new Treppenbau(welt, j.get("name").getAsString(), j.get("art").getAsString(),
                BlockPos.of(j.get("start").getAsLong()).below(), -1);
        t.ziel = BlockPos.of(j.get("ziel").getAsLong());
        List<TreppenPlan.Stufe> st = new ArrayList<>();
        for (var e : j.getAsJsonArray("stufen")) {
            JsonArray a = e.getAsJsonArray();
            st.add(new TreppenPlan.Stufe(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt(), a.get(3).getAsInt(), a.get(4).getAsInt() == 1));
        }
        t.stufen = st;
        t.zellenBerechnen();
        t.phase = Phase.FERTIG;
        return t;
    }

    /** Für das Straßennetz-JSON (nur fertige Treppen mit Plan). */
    JsonObject alsJson() {
        if (stufen == null || stufen.isEmpty()) {
            return null;
        }
        JsonObject j = new JsonObject();
        j.addProperty("name", name);
        j.addProperty("art", art);
        j.addProperty("start", start.asLong());
        j.addProperty("ziel", ziel.asLong());
        JsonArray st = new JsonArray();
        for (TreppenPlan.Stufe s : stufen) {
            JsonArray a = new JsonArray();
            a.add(s.x());
            a.add(s.y());
            a.add(s.z());
            a.add(s.richtung());
            a.add(s.eben() ? 1 : 0);
            st.add(a);
        }
        j.add("stufen", st);
        return j;
    }

    /**
     * Testhilfe: Blick auf die Treppe bei {@code anteil} (0 = oben am Tor, 1 = unten) – Standort zwei Stufen davor
     * (Augenhöhe), Blick die Treppe hinunter. x, y, z, Blickziel x, y, z oder null.
     */
    double[] blick(double anteil) {
        if (stufen == null || stufen.size() < 4) {
            return null;
        }
        int i = Math.max(2, Math.min(stufen.size() - 2, (int) Math.round(anteil * (stufen.size() - 1))));
        TreppenPlan.Stufe a = stufen.get(i - 2), b = stufen.get(i + 1);
        return new double[]{a.x() + 0.5, a.y() + 0.6, a.z() + 0.5, b.x() + 0.5, b.y() + 0.4, b.z() + 0.5};
    }

    boolean fertig() {
        return phase == Phase.FERTIG;
    }

    /** Hat die Treppe einen Plan (also wirklich Stufen)? */
    boolean gebaut() {
        return stufen != null && !stufen.isEmpty();
    }

    void tick() {
        switch (phase) {
            case SUCHEN -> suchen();
            case HUELLE -> {
                for (int n = 0; n < STUFEN_JE_TICK && index < stufen.size(); n++, index++) {
                    huelle(index);
                }
                weiter(stufen.size(), Phase.RAEUMEN);
            }
            case RAEUMEN -> {
                for (int n = 0; n < ZELLEN_JE_TICK && index < gangListe.size(); n++, index++) {
                    BlockPos p = gangListe.get(index);
                    if (!welt.getBlockState(p).isAir() && darf(p)) {
                        setzen(p, Blocks.AIR.defaultBlockState());
                    }
                }
                weiter(gangListe.size(), Phase.DICHTEN);
            }
            case DICHTEN -> {
                for (int n = 0; n < ZELLEN_JE_TICK / 4 && index < gangListe.size(); n++, index++) {
                    gedichtet += dichten(gangListe.get(index), true);
                }
                weiter(gangListe.size(), Phase.SCHMUCK);
            }
            case SCHMUCK -> {
                TreppenPlan.Stufe s = stufen.get(0);
                tor(new BlockPos(s.x(), s.y(), s.z()), quer(s));
                phase = Phase.FERTIG;
                StinkendReich.LOGGER.info("Straßennetz: Treppe zu „{}“ fertig ({} Stufen bis {}, {} Stellen abgedichtet) – {}",
                        name, stufen.size(), ziel.toShortString(), gedichtet, pruefen());
            }
            case FERTIG -> {
            }
        }
    }

    private void weiter(int anzahl, Phase naechste) {
        if (index >= anzahl) {
            index = 0;
            phase = naechste;
        }
    }

    // ---------------- Ziel suchen ----------------

    private void suchen() {
        if (chunkIndex < chunks.size()) {
            long[] c = chunks.get(chunkIndex++);
            welt.getChunk((int) c[0], (int) c[1]);
            int x0 = (int) c[0] << 4, z0 = (int) c[1] << 4;
            int unten = Kompat.minY(welt) + 4, oben = start.getY() - 3;
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int x = x0; x < x0 + 16; x++) {
                for (int z = z0; z < z0 + 16; z++) {
                    double weit = Math.hypot(x - start.getX(), z - start.getZ());
                    if (weit > SUCHE) {
                        continue;
                    }
                    for (int y = oben; y >= unten; y--) {
                        p.set(x, y, z);
                        BlockPos stelle = kandidat(p);
                        if (stelle != null) {
                            // nah und nicht zu tief bevorzugen; Endportal weit vor einem bloßen Gang
                            double w = weit + (start.getY() - y) * 0.6 + (prioritaet(p) ? 0 : 200);
                            if (w < besteWertung) {
                                besteWertung = w;
                                ziel = stelle;
                            }
                        }
                    }
                }
            }
            return;
        }
        if (ziel == null || start.getY() - ziel.getY() < 4) {
            // Nichts gefunden (oder das Ziel liegt an der Oberfläche): keine Treppe nötig
            phase = Phase.FERTIG;
            StinkendReich.LOGGER.info("Straßennetz: keine Treppe zu „{}“ ({})", name, ziel == null ? "nichts gefunden" : "liegt oben");
            return;
        }
        planen();
    }

    /** Stelle zum Hinkommen (Luft mit Boden), wenn der Block zu dem gesuchten Ziel gehört – sonst null. */
    private BlockPos kandidat(BlockPos p) {
        BlockState s = welt.getBlockState(p);
        boolean treffer = switch (art) {
            case "mine" -> s.is(Blocks.RAIL) || s.is(Blocks.OAK_FENCE) || s.is(Blocks.DARK_OAK_FENCE);
            case "endportal" -> s.is(Blocks.END_PORTAL_FRAME) || s.is(Blocks.STONE_BRICKS) || s.is(Blocks.MOSSY_STONE_BRICKS)
                    || s.is(Blocks.CRACKED_STONE_BRICKS);
            case "netherportal" -> s.is(Blocks.NETHER_PORTAL) || s.is(Blocks.CRYING_OBSIDIAN);
            default -> false;
        };
        if (!treffer) {
            return null;
        }
        // Daneben eine Stelle, an der man stehen kann (zwei Blöcke Luft über festem Boden)
        for (Direction r : Direction.Plane.HORIZONTAL) {
            for (int dy = -1; dy <= 1; dy++) {
                BlockPos q = p.relative(r).above(dy);
                if (welt.getBlockState(q).isAir() && welt.getBlockState(q.above()).isAir() && welt.getBlockState(q.below()).isSolidRender(
                        //? if <1.21.2
                        /*welt, q.below()*/
                )) {
                    return q.immutable();
                }
            }
        }
        if (s.is(Blocks.RAIL) && welt.getBlockState(p.above()).isAir()) {
            return p.immutable();
        }
        return null;
    }

    private boolean prioritaet(BlockPos p) {
        BlockState s = welt.getBlockState(p);
        return !art.equals("endportal") || s.is(Blocks.END_PORTAL_FRAME);
    }

    // ---------------- Weg planen ----------------

    private void planen() {
        stufen = TreppenPlan.planen(start.getX(), start.getY(), start.getZ(), ziel.getX(), ziel.getY(), ziel.getZ(), strassenRichtung);
        if (stufen.isEmpty()) {
            phase = Phase.FERTIG;
            StinkendReich.LOGGER.warn("Straßennetz: kein Weg für die Treppe zu „{}“ gefunden", name);
            return;
        }
        int fehler = TreppenPlan.konflikte(stufen);
        zellenBerechnen();
        // Himmel messen, bevor die erste Decke steht (die Decke ändert die Höhenkarte)
        himmel = new boolean[stufen.size()];
        for (int i = 0; i < stufen.size(); i++) {
            TreppenPlan.Stufe s = stufen.get(i);
            himmel[i] = offenZumHimmel(new BlockPos(s.x(), s.y() + 3, s.z()));
        }
        phase = Phase.HUELLE;
        index = 0;
        StinkendReich.LOGGER.info("Straßennetz: Treppe zu „{}“ geplant: {} Stufen von {} bis {}{}", name, stufen.size(),
                start.toShortString(), ziel.toShortString(), fehler == 0 ? "" : " (" + fehler + " Konflikte!)");
    }

    /** Gang- und Bodenzellen aus den Stufen. */
    private void zellenBerechnen() {
        gang.clear();
        gangListe.clear();
        boden.clear();
        for (TreppenPlan.Stufe s : stufen) {
            for (int w = -1; w <= 1; w++) {
                for (int h = 0; h <= 2; h++) {
                    BlockPos p = new BlockPos(s.x() + s.querX() * w, s.y() + h, s.z() + s.querZ() * w);
                    if (gang.add(p.asLong())) {
                        gangListe.add(p);
                    }
                }
            }
        }
        for (TreppenPlan.Stufe s : stufen) {
            BlockState stufe = s.eben() ? MARMOR : Blocks.SMOOTH_QUARTZ_STAIRS.defaultBlockState()
                    .setValue(StairBlock.FACING, richtung(s.richtung()).getOpposite());
            for (int w = -1; w <= 1; w++) {
                long k = BlockPos.asLong(s.x() + s.querX() * w, s.y() - 1, s.z() + s.querZ() * w);
                if (gang.contains(k)) {
                    continue;
                }
                // Zwei Stufen auf derselben Zelle (Absatz an einer Ecke): ebener Marmor
                boden.merge(k, stufe, (alt, neu) -> alt.equals(neu) ? alt : MARMOR);
            }
        }
    }

    private static Direction richtung(int r) {
        return switch (r & 3) {
            case 0 -> Direction.NORTH;
            case 1 -> Direction.EAST;
            case 2 -> Direction.SOUTH;
            default -> Direction.WEST;
        };
    }

    private static Direction quer(TreppenPlan.Stufe s) {
        return richtung(s.richtung() + 1);
    }

    // ---------------- Bauen ----------------

    /**
     * Hülle einer Stufe im Luxus-Stil (Philip, 07.11.: „luxuriös, mit Gold und so“): Boden aus Marmorstufen (glatter
     * Quarz), Wände mit goldener Sockelleiste und Quarzziegeln, Decke aus glattem Quarz mit goldenen Lichtern alle vier
     * Stufen. Oben am offenen Einschnitt (kein Dach darüber) stehen die Wände bis Brusthöhe mit einer Quarzplatte als
     * Brüstung.
     */
    private void huelle(int i) {
        TreppenPlan.Stufe s = stufen.get(i);
        BlockPos c = new BlockPos(s.x(), s.y(), s.z());
        Direction q = quer(s);
        boolean offen = himmel[i];
        for (int w = -2; w <= 2; w++) {
            BlockPos f = c.relative(q, w);
            boolean spur = Math.abs(w) <= 1;
            // unter dem Boden kein Loch (Höhle, Wasser)
            BlockPos drunter = f.below(2);
            if (spur && frei(drunter) && loch(drunter)) {
                setzen(drunter, MARMOR);
            }
            for (int h = -1; h <= 3; h++) {
                BlockPos p = f.above(h);
                if (spur && h == -1) {
                    // Boden: gehört zur Treppe (frei() schließt ihn deshalb aus) – Marmorstufe bzw. Marmor am Absatz
                    BlockState b = boden.get(p.asLong());
                    if (b != null && darf(p)) {
                        setzen(p, w == 0 && s.eben() && b == MARMOR ? GOLD : b);
                    }
                    continue;
                }
                if (!frei(p)) {
                    continue;
                }
                if (spur && h == 3) {
                    if (!offen) {
                        setzen(p, w == 0 && i % 4 == 2 ? LICHT : MARMOR);
                    }
                } else if (!spur) {
                    switch (h) {
                        case -1 -> setzen(p, MARMOR);
                        case 0 -> setzen(p, GOLD);
                        case 1 -> setzen(p, ZIEGEL);
                        case 2 -> setzen(p, offen ? Blocks.QUARTZ_SLAB.defaultBlockState() : ZIEGEL);
                        default -> {
                            if (!offen) {
                                setzen(p, MARMOR);
                            }
                        }
                    }
                }
            }
        }
    }

    /** Zelle gehört weder zum Gang noch zum Boden einer Stufe und darf verändert werden. */
    private boolean frei(BlockPos p) {
        return !gang.contains(p.asLong()) && !boden.containsKey(p.asLong()) && darf(p);
    }

    /** Luft, Flüssigkeit, rieselnder Block oder Pflanze – also keine dichte Wand. */
    private boolean loch(BlockPos p) {
        BlockState s = welt.getBlockState(p);
        return s.isAir() || !s.getFluidState().isEmpty() || s.getBlock() instanceof FallingBlock || s.canBeReplaced();
    }

    /**
     * Prüft alle 26 Nachbarn einer Gangzelle (und, wenn {@code schmuck}, verkleidet sichtbare Naturblöcke mit Quarz):
     * Flüssigkeit wird immer zugemauert, wassergefüllte Blöcke werden trockengelegt. Löcher, Sand/Kies und Pflanzen
     * werden zugemauert, außer am offenen Himmel. Am Ziel und am Straßenende bleibt Luft offen (dort geht es hinein).
     *
     * @return Anzahl veränderter Zellen
     */
    private int dichten(BlockPos g, boolean schmuck) {
        int n = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    BlockPos p = g.offset(dx, dy, dz);
                    if (!frei(p)) {
                        // Boden einer Stufe: Wasser in der Stufe weg
                        BlockState b = boden.get(p.asLong());
                        if (b != null && darf(p) && !welt.getBlockState(p).equals(b) && loch(p)) {
                            setzen(p, b);
                            n++;
                        }
                        continue;
                    }
                    BlockState s = welt.getBlockState(p);
                    if (s.hasProperty(BlockStateProperties.WATERLOGGED) && s.getValue(BlockStateProperties.WATERLOGGED)) {
                        setzen(p, s.setValue(BlockStateProperties.WATERLOGGED, false));
                        n++;
                        continue;
                    }
                    boolean nass = !s.getFluidState().isEmpty();
                    if (!nass && (amEingang(p) || offenZumHimmel(p))) {
                        continue;
                    }
                    boolean zu = nass || loch(p) || (schmuck && !luxus(s) && !amEingang(p));
                    if (zu) {
                        setzen(p, gang.contains(p.above().asLong()) || gang.contains(p.below().asLong()) ? MARMOR : ZIEGEL);
                        n++;
                    }
                }
            }
        }
        return n;
    }

    /** Nahe am Ziel oder am Straßenende: dort bleibt offen, was offen ist (hinein in Mine/Festung, hinaus auf die Straße). */
    private boolean amEingang(BlockPos p) {
        boolean amZiel = Math.abs(p.getX() - ziel.getX()) <= 3 && Math.abs(p.getZ() - ziel.getZ()) <= 3
                && p.getY() >= ziel.getY() - 1 && p.getY() <= ziel.getY() + 3;
        boolean amStart = Math.abs(p.getX() - start.getX()) <= 3 && Math.abs(p.getZ() - start.getZ()) <= 3 && p.getY() >= start.getY() - 1;
        return amZiel || amStart;
    }

    private static boolean luxus(BlockState s) {
        return s.is(Blocks.SMOOTH_QUARTZ) || s.is(Blocks.QUARTZ_BRICKS) || s.is(Blocks.QUARTZ_BLOCK) || s.is(Blocks.CHISELED_QUARTZ_BLOCK)
                || s.is(Blocks.QUARTZ_PILLAR) || s.is(Blocks.SMOOTH_QUARTZ_STAIRS) || s.is(Blocks.QUARTZ_SLAB) || s.is(Blocks.SMOOTH_QUARTZ_SLAB)
                || s.is(Blocks.GOLD_BLOCK) || s.is(Blocks.OCHRE_FROGLIGHT) || s.is(Blocks.LANTERN);
    }

    /** Goldenes Eingangstor über der ersten Stufe: zwei Säulen, Sturz aus Gold und gemeißeltem Quarz, Laternen obenauf. */
    private void tor(BlockPos c, Direction quer) {
        for (int w : new int[]{-2, 2}) {
            for (int h = 0; h <= 3; h++) {
                BlockPos p = c.relative(quer, w).above(h);
                if (darf(p) && !gang.contains(p.asLong())) {
                    setzen(p, h == 3 ? Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState() : GOLD);
                }
            }
            BlockPos laterne = c.relative(quer, w).above(5);
            if (darf(laterne) && welt.getBlockState(laterne).isAir()) {
                setzen(laterne, Blocks.LANTERN.defaultBlockState());
            }
        }
        for (int w = -2; w <= 2; w++) {
            BlockPos p = c.relative(quer, w).above(4);
            if (darf(p)) {
                setzen(p, Math.abs(w) == 2 ? Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState() : GOLD);
            }
        }
    }

    // ---------------- Prüfen und Pflegen ----------------

    /**
     * Geht die Treppe Stufe für Stufe ab: Mittelspur frei (Füße und Kopf Luft, kein Wasser), Boden darunter fest, kein
     * Wasser im ganzen Gang, letzte Stufe am Ziel. Text fürs Protokoll und für {@code /butler treppenpruefen}.
     */
    String pruefen() {
        if (stufen == null || stufen.isEmpty()) {
            return "keine Treppe";
        }
        int versperrt = 0, ohneBoden = 0, wasser = 0, naturboden = 0;
        BlockPos erste = null;
        for (TreppenPlan.Stufe s : stufen) {
            BlockPos c = new BlockPos(s.x(), s.y(), s.z());
            BlockState fuss = welt.getBlockState(c), kopf = welt.getBlockState(c.above()), unten = welt.getBlockState(c.below());
            if (!fuss.getCollisionShape(welt, c).isEmpty() || !kopf.getCollisionShape(welt, c.above()).isEmpty()) {
                versperrt++;
                erste = erste == null ? c : erste;
            }
            if (unten.getCollisionShape(welt, c.below()).isEmpty()) {
                ohneBoden++;
                erste = erste == null ? c : erste;
            } else if (!luxus(unten) && darf(c.below())) {
                naturboden++;                                  // begehbar, aber keine Marmorstufe
            }
        }
        for (BlockPos p : gangListe) {
            if (!welt.getFluidState(p).isEmpty()) {
                wasser++;
                erste = erste == null ? p : erste;
            }
        }
        TreppenPlan.Stufe l = stufen.get(stufen.size() - 1);
        boolean amZiel = l.x() == ziel.getX() && l.y() == ziel.getY() && l.z() == ziel.getZ();
        return String.format("PRÜFUNG %s: %d Stufen, %d versperrt, %d ohne Boden, %d ohne Marmor, %d Wasser im Gang, Ziel %s%s",
                versperrt + ohneBoden + wasser + naturboden == 0 && amZiel ? "OK" : "FEHLER", stufen.size(), versperrt, ohneBoden, naturboden, wasser,
                amZiel ? "erreicht" : "verfehlt", erste == null ? "" : " (z. B. bei " + erste.toShortString() + ")");
    }

    /** Liegt ein Spieler in der Nähe der Treppe ({@code abstand} Blöcke um irgendeine Stufe, grob über den Rahmen)? */
    boolean spielerNahe(double abstand) {
        if (stufen == null || stufen.isEmpty()) {
            return false;
        }
        int x0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z0 = Integer.MAX_VALUE, z1 = Integer.MIN_VALUE, y0 = Integer.MAX_VALUE, y1 = Integer.MIN_VALUE;
        for (TreppenPlan.Stufe s : stufen) {
            x0 = Math.min(x0, s.x()); x1 = Math.max(x1, s.x());
            z0 = Math.min(z0, s.z()); z1 = Math.max(z1, s.z());
            y0 = Math.min(y0, s.y()); y1 = Math.max(y1, s.y());
        }
        for (var sp : welt.players()) {
            if (sp.getX() > x0 - abstand && sp.getX() < x1 + abstand && sp.getZ() > z0 - abstand && sp.getZ() < z1 + abstand
                    && sp.getY() > y0 - abstand && sp.getY() < y1 + abstand) {
                return true;
            }
        }
        return false;
    }

    /**
     * Treppenwart: hält eine fertige Treppe frei und trocken. Was in den Gang gefallen oder geflossen ist (Wasser, Kies,
     * Sand), verschwindet; läuft von außen Flüssigkeit an, wird die Stelle zugemauert; fehlende Stufen kommen wieder.
     * Nur in geladenen Chunks; Spielerbauten bleiben (darf).
     *
     * @return Anzahl veränderter Zellen
     */
    int pflegen() {
        int n = 0;
        for (BlockPos p : gangListe) {
            if (!welt.hasChunkAt(p)) {
                continue;
            }
            BlockState s = welt.getBlockState(p);
            if (!s.isAir() && darf(p) && (!s.getFluidState().isEmpty() || s.getBlock() instanceof FallingBlock
                    || !s.getCollisionShape(welt, p).isEmpty())) {
                setzen(p, Blocks.AIR.defaultBlockState());
                n++;
            }
            for (Direction r : Direction.values()) {
                BlockPos q = p.relative(r);
                if (!welt.hasChunkAt(q)) {
                    continue;
                }
                BlockState b = boden.get(q.asLong());
                if (b != null) {
                    if (darf(q) && loch(q)) {
                        setzen(q, b);
                        n++;
                    }
                } else if (!gang.contains(q.asLong()) && !welt.getFluidState(q).isEmpty() && darf(q)) {
                    BlockState t = welt.getBlockState(q);
                    setzen(q, t.hasProperty(BlockStateProperties.WATERLOGGED) ? t.setValue(BlockStateProperties.WATERLOGGED, false) : ZIEGEL);
                    n++;
                }
            }
        }
        return n;
    }

    // ---------------- Hilfen ----------------

    private boolean offenZumHimmel(BlockPos p) {
        return p.getY() >= welt.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ());
    }

    private boolean darf(BlockPos p) {
        if (welt.getBlockEntity(p) != null || SpielerBloecke.istGesetzt(welt, p)) {
            return false;
        }
        BlockState s = welt.getBlockState(p);
        if (s.is(Blocks.END_PORTAL_FRAME) || s.is(Blocks.END_PORTAL) || s.is(Blocks.NETHER_PORTAL) || s.is(Blocks.OBSIDIAN)
                || s.is(Blocks.CRYING_OBSIDIAN) || s.is(Blocks.BEDROCK) || s.is(Blocks.SPAWNER)) {
            return false;
        }
        return VillaPlatzierung.bei(p) == null;
    }

    /**
     * Setzt ohne Nachbar-Aktualisierung (sonst fließt angrenzendes Wasser los, bevor die Wand steht, und Kies fällt in den
     * Gang). Geplante Flüssigkeits-Ticks an der Stelle werden mit dem Block ersetzt.
     */
    private void setzen(BlockPos p, BlockState s) {
        welt.setBlock(p, s, Block.UPDATE_CLIENTS);
    }
}
