package de.moinmornhart.stinkendreich.strasse;

import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.StinkendReich;
import de.moinmornhart.stinkendreich.butler.SpielerBloecke;
import de.moinmornhart.stinkendreich.villa.VillaPlatzierung;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Treppe vom Ende einer Straße hinunter ins Ziel (Philip, 07.11.: „wenn ich zur Mine gehe, dass ich dann auch direkt dahin
 * geleitet werde, also dass dann auch Treppen nach unten gehen. Das gleiche bei Netherportal und Endportal“).
 *
 * <p>Ablauf: Zuerst wird Chunk für Chunk um das Straßenende das Ziel gesucht ({@link #SUCHE} Blöcke weit, ein Chunk je
 * Tick): beim Minenschacht eine Schiene oder ein Holzzaun im Gang, bei der Festung das Endportal (sonst ein Gang aus
 * Steinziegeln), beim Netherportal der Portalblock. Dann entsteht Stufe für Stufe ein zwei Blöcke breiter, drei Blöcke
 * hoher Treppengang aus Steinziegeltreppen: erst in Richtung Ziel, und wenn es mehr in die Tiefe als in die Weite geht, als
 * eckige Wendeltreppe – im Luxus-Stil aus Quarz und Gold (siehe {@link #stufeBauen}). Spielerbauten, Truhen, Portale und
 * Villen werden nie angefasst.</p>
 */
final class Treppenbau {
    /** So weit um das Straßenende wird das Ziel gesucht (Blöcke, waagerecht). */
    static final int SUCHE = 40;
    /** Stufen je Tick. */
    private static final int JE_TICK = 6;
    private static final BlockState LICHT = Blocks.OCHRE_FROGLIGHT.defaultBlockState();
    /** Stil der Treppen: ändert er sich, werden schon gebaute Treppen im neuen Stil überbaut. */
    static final int STIL = 2;

    private final ServerLevel welt;
    final String name;
    final String art;
    private final BlockPos start;
    private final List<long[]> chunks = new ArrayList<>();
    private int chunkIndex;
    private BlockPos ziel;
    private double besteWertung = Double.MAX_VALUE;
    private List<BlockPos> stufen;
    private List<Direction> richtungen;
    /** Alle Zellen, die im Gang frei bleiben (damit eine Wand der einen Stufe nie den Gang der nächsten zumauert). */
    private final java.util.Set<Long> gang = new java.util.HashSet<>();
    private int stufe;
    private boolean fertig;

    /**
     * @param art   „mine“, „endportal“ oder „netherportal“
     * @param ende  letzter Punkt der Straße (Fahrbahnhöhe)
     */
    Treppenbau(ServerLevel welt, String name, String art, BlockPos ende) {
        this.welt = welt;
        this.name = name;
        this.art = art;
        this.start = ende.above();
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

    /**
     * Testhilfe: Blick auf die Treppe bei {@code anteil} (0 = oben am Tor, 1 = unten) – Standort zwei Stufen davor
     * (eine Stufe höher), Blick die Treppe hinunter. x, y, z, Blickziel x, y, z oder null.
     */
    double[] blick(double anteil) {
        if (stufen == null || stufen.size() < 4) {
            return null;
        }
        int i = Math.max(2, Math.min(stufen.size() - 2, (int) Math.round(anteil * (stufen.size() - 1))));
        BlockPos a = stufen.get(i - 2), b = stufen.get(i + 1);
        Direction q = richtungen.get(i).getClockWise();
        return new double[]{a.getX() + 0.5 + q.getStepX() * 0.5, a.getY() + 0.6, a.getZ() + 0.5 + q.getStepZ() * 0.5,
                b.getX() + 0.5 + q.getStepX() * 0.5, b.getY() + 0.4, b.getZ() + 0.5 + q.getStepZ() * 0.5};
    }

    boolean fertig() {
        return fertig;
    }

    void tick() {
        if (fertig) {
            return;
        }
        if (stufen == null) {
            suchen();
            return;
        }
        for (int i = 0; i < JE_TICK && stufe < stufen.size(); i++, stufe++) {
            stufeBauen(stufe);
        }
        if (stufe >= stufen.size()) {
            fertig = true;
            StinkendReich.LOGGER.info("Straßennetz: Treppe zu „{}“ fertig ({} Stufen bis {})", name, stufen.size(), ziel.toShortString());
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
            fertig = true;
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

    /**
     * Stufen von oben nach unten: Jede Stufe geht einen Block waagerecht und (solange noch Tiefe fehlt) einen hinunter.
     * Ist es tiefer als weit, läuft der Gang als eckige Wendeltreppe (Seiten zu 5 Stufen) und biegt erst zum Ziel ab,
     * wenn Tiefe und Weite zusammenpassen.
     */
    private void planen() {
        stufen = new ArrayList<>();
        richtungen = new ArrayList<>();
        BlockPos c = start;
        Direction wendel = Direction.NORTH;
        int seite = 0;
        for (int n = 0; n < 600; n++) {
            int dx = ziel.getX() - c.getX(), dz = ziel.getZ() - c.getZ(), tief = c.getY() - ziel.getY();
            int weit = Math.abs(dx) + Math.abs(dz);
            if (weit == 0 && tief <= 0) {
                break;
            }
            Direction r;
            if (weit > 0 && weit >= tief) {
                r = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
            } else {
                // Wendel: Seite um Seite im Uhrzeigersinn, jede 5 Stufen lang
                if (++seite > 5) {
                    seite = 1;
                    wendel = wendel.getClockWise();
                }
                r = wendel;
            }
            c = c.relative(r).below(tief > 0 ? 1 : (tief < 0 ? -1 : 0));
            stufen.add(c);
            richtungen.add(r);
            for (int w = 0; w <= 1; w++) {
                for (int h = 0; h <= 2; h++) {
                    gang.add(c.relative(r.getClockWise(), w).above(h).asLong());
                }
            }
        }
        StinkendReich.LOGGER.info("Straßennetz: Treppe zu „{}“ geplant: {} Stufen von {} bis {}", name, stufen.size(),
                start.toShortString(), ziel.toShortString());
    }

    // ---------------- Bauen ----------------

    /**
     * Eine Stufe im Luxus-Stil (Philip, 07.11.: „luxuriös, mit Gold und so“): Stufen aus weißem Marmor (glatter Quarz),
     * Wände aus Quarzziegeln mit goldener Sockelleiste, Decke aus glattem Quarz mit goldenen Lichtern (Ocker-Froschlicht)
     * alle vier Stufen; oben am offenen Einschnitt nur eine Brüstung (Gold + Quarz). Am Straßenende ein goldenes Tor.
     */
    private void stufeBauen(int i) {
        BlockPos c = stufen.get(i);
        Direction r = richtungen.get(i);
        BlockPos vorher = i == 0 ? start : stufen.get(i - 1);
        boolean runter = c.getY() < vorher.getY();
        Direction quer = r.getClockWise();
        for (int w = -1; w <= 2; w++) {
            BlockPos f = c.relative(quer, w);
            boolean imGang = w == 0 || w == 1;
            boolean himmel = offenZumHimmel(f.above(3));
            for (int h = -1; h <= 3; h++) {
                BlockPos p = f.above(h);
                if (!darf(p) || (h >= 0 && gang.contains(p.asLong())) || (h == -1 && imGang && gang.contains(p.asLong()))) {
                    continue;
                }
                if (imGang && h == -1) {
                    setzen(p, runter ? Blocks.SMOOTH_QUARTZ_STAIRS.defaultBlockState().setValue(StairBlock.FACING, r.getOpposite())
                            : Blocks.SMOOTH_QUARTZ.defaultBlockState());
                } else if (imGang && h <= 2) {
                    setzen(p, Blocks.AIR.defaultBlockState());
                } else if (imGang) {
                    if (!himmel) {
                        setzen(p, w == 0 && i % 4 == 2 ? LICHT : Blocks.SMOOTH_QUARTZ.defaultBlockState());
                    }
                } else if (h <= 0) {
                    // Wand unten: goldene Sockelleiste (darunter Quarz als Fundament)
                    setzen(p, h == 0 ? Blocks.GOLD_BLOCK.defaultBlockState() : Blocks.SMOOTH_QUARTZ.defaultBlockState());
                } else if (!himmel) {
                    setzen(p, h == 3 ? Blocks.SMOOTH_QUARTZ.defaultBlockState() : Blocks.QUARTZ_BRICKS.defaultBlockState());
                } else if (h == 1) {
                    setzen(p, Blocks.QUARTZ_SLAB.defaultBlockState());     // Brüstung am offenen Einschnitt
                }
            }
            // unter der Stufe kein Loch (Höhlen)
            BlockPos drunter = f.below(2);
            if (darf(drunter) && welt.getBlockState(drunter).isAir()) {
                setzen(drunter, Blocks.SMOOTH_QUARTZ.defaultBlockState());
            }
        }
        if (i == 0) {
            tor(c, quer);
        }
    }

    /** Goldenes Eingangstor über der ersten Stufe: zwei Säulen, Sturz aus Gold und gemeißeltem Quarz, Laternen obenauf. */
    private void tor(BlockPos c, Direction quer) {
        for (int w : new int[]{-1, 2}) {
            for (int h = 0; h <= 3; h++) {
                BlockPos p = c.relative(quer, w).above(h);
                if (darf(p)) {
                    setzen(p, h == 3 ? Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState() : Blocks.GOLD_BLOCK.defaultBlockState());
                }
            }
            BlockPos laterne = c.relative(quer, w).above(5);
            if (darf(laterne) && welt.getBlockState(laterne).isAir()) {
                setzen(laterne, Blocks.LANTERN.defaultBlockState());
            }
        }
        for (int w = -1; w <= 2; w++) {
            BlockPos p = c.relative(quer, w).above(4);
            if (darf(p)) {
                setzen(p, w == -1 || w == 2 ? Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState() : Blocks.GOLD_BLOCK.defaultBlockState());
            }
        }
    }

    private boolean offenZumHimmel(BlockPos p) {
        return p.getY() >= welt.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ());
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

    private void setzen(BlockPos p, BlockState s) {
        welt.setBlock(p, s, Block.UPDATE_CLIENTS);
    }
}
