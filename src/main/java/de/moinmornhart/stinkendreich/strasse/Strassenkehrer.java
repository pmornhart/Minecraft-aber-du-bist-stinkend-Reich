package de.moinmornhart.stinkendreich.strasse;

import de.moinmornhart.stinkendreich.StinkendReich;
import de.moinmornhart.stinkendreich.villa.VillaPlatzierung;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Räumt liegengebliebene Gegenstände an den Straßen weg. Die Straße schneidet Bäume, Hänge und Pflanzen an; danach
 * zerfällt das Laub (Setzlinge, Stöcke, Äpfel), schwebende Blumen und Gras fallen ab – und wer die Straße entlangfährt,
 * hatte bald die Taschen voll davon.
 *
 * <p>Weggeräumt wird nur, was niemand geworfen hat ({@link ItemEntity#getOwner()} ist leer), mindestens
 * {@link #MINDESTALTER} Ticks liegt (eigene Abbau-Beute hebt man vorher auf) und höchstens {@link #ABSTAND} Blöcke
 * neben einem Wegpunkt einer Straße liegt – nie auf dem Grundstück einer Villa.</p>
 */
final class Strassenkehrer {
    /** Wie oft gekehrt wird (Ticks). */
    static final int ALLE = 40;
    /** So lange muss ein Gegenstand schon liegen (Ticks). */
    private static final int MINDESTALTER = 100;
    /** Abstand zur Mittellinie (Blöcke, waagerecht); Straße 4,5 breit je Seite + Baumkronen daneben. */
    private static final double ABSTAND = 11;
    private static final double HOEHE = 10;

    /** Wegpunkte je Chunk; neu aufgebaut, sobald sich die Zahl der Punkte ändert. */
    private final Map<Long, List<BlockPos>> raster = new HashMap<>();
    private int punkteImRaster = -1;

    void kehren(ServerLevel welt, List<List<BlockPos>> strecken) {
        int anzahl = 0;
        for (List<BlockPos> s : strecken) {
            anzahl += s.size();
        }
        if (anzahl == 0) {
            return;
        }
        if (anzahl != punkteImRaster) {
            raster.clear();
            for (List<BlockPos> s : strecken) {
                for (BlockPos p : s) {
                    raster.computeIfAbsent(chunk(p.getX() >> 4, p.getZ() >> 4), k -> new ArrayList<>()).add(p);
                }
            }
            punkteImRaster = anzahl;
        }
        List<ItemEntity> weg = new ArrayList<>();
        for (Entity e : welt.getAllEntities()) {
            if (e instanceof ItemEntity item && item.isAlive() && item.getOwner() == null && item.getAge() >= MINDESTALTER
                    && anderStrasse(item) && !aufEinemGrundstueck(item)) {
                weg.add(item);
            }
        }
        for (ItemEntity item : weg) {
            item.discard();
        }
        if (!weg.isEmpty() && de.moinmornhart.stinkendreich.dev.Autotest.aktiv()) {
            StinkendReich.LOGGER.info("Autotest: Straßenkehrer hat {} Gegenstände weggeräumt", weg.size());
        }
    }

    /** Halbe Breite des Bereichs über der Straße, der trocken bleibt (Fahrbahn 3, Bordstein, Seitenstreifen). */
    private static final int TROCKEN = StrassenBau.HALB + 1;
    /** Wasser wird nur in der Nähe von Spielern gesucht (dort fährt jemand). */
    private static final double SPIELER_NAEHE = 80;

    /**
     * Hält Wasser und Lava von den Straßen fern (Philips Bild vom 07.11.: Ein Wasserfall aus dem Hang lief über die
     * Fahrbahn, die Limo stand mit 0 km/h im Wasser). Über jedem Wegpunkt in Spielernähe wird der Bereich
     * {@link #TROCKEN} Blöcke links und rechts, vom Unterbau bis 14 Blöcke hoch, durchsucht: Läuft Flüssigkeit von außen
     * hinein, wird die Stelle außen mit Steinziegeln zugemauert, und die Flüssigkeit im Bereich verschwindet. Nie auf
     * dem Grundstück einer Villa (Pool, Graben, Aquarium).
     */
    void wasserFernhalten(ServerLevel welt) {
        if (raster.isEmpty()) {
            return;
        }
        int abgedichtet = 0, entfernt = 0;
        BlockPos erste = null;
        java.util.Set<Long> bereich = new java.util.HashSet<>();
        List<BlockPos> naheStellen = new ArrayList<>();
        for (var spieler : welt.players()) {
            int cx = spieler.blockPosition().getX() >> 4, cz = spieler.blockPosition().getZ() >> 4;
            int r = (int) Math.ceil(SPIELER_NAEHE / 16);
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    List<BlockPos> pkt = raster.get(chunk(cx + dx, cz + dz));
                    if (pkt != null) {
                        for (BlockPos p : pkt) {
                            if (p.distSqr(spieler.blockPosition()) <= SPIELER_NAEHE * SPIELER_NAEHE && welt.hasChunkAt(p)) {
                                naheStellen.add(p);
                            }
                        }
                    }
                }
            }
        }
        // Spalten des trockenen Bereichs (je Spalte die Höhe der Straße)
        Map<Long, Integer> spalten = new HashMap<>();
        for (BlockPos p : naheStellen) {
            for (int dx = -TROCKEN; dx <= TROCKEN; dx++) {
                for (int dz = -TROCKEN; dz <= TROCKEN; dz++) {
                    if (dx * dx + dz * dz <= TROCKEN * TROCKEN + 1) {
                        long k = BlockPos.asLong(p.getX() + dx, 0, p.getZ() + dz);
                        spalten.merge(k, p.getY(), Math::max);
                        bereich.add(k);
                    }
                }
            }
        }
        // Lichtraum über der Fahrbahn freiräumen: Pfeiler, Unterbau, Geländer oder Laternen einer Straße, die später darüber
        // gebaut wurde, standen sonst mitten auf dieser (Philips Bild vom 07.11.)
        int freigeraeumt = 0;
        for (BlockPos p : naheStellen) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos f = p.offset(dx, 0, dz);
                    BlockState boden = welt.getBlockState(f);
                    if (dx * dx + dz * dz > 5 || !(boden.is(StrassenBau.ASPHALT.getBlock()) || boden.is(StrassenBau.LINIE.getBlock()))
                            || villaBei(f)) {
                        continue;
                    }
                    for (int h = 1; h <= 3; h++) {
                        BlockState s = welt.getBlockState(f.above(h));
                        if (s.is(net.minecraft.world.level.block.Blocks.STONE_BRICKS) || s.is(net.minecraft.world.level.block.Blocks.STONE_BRICK_WALL)
                                || s.is(net.minecraft.world.level.block.Blocks.POLISHED_BLACKSTONE_WALL) || s.is(net.minecraft.world.level.block.Blocks.LANTERN)
                                || s.is(net.minecraft.world.level.block.Blocks.SMOOTH_STONE)) {
                            welt.setBlock(f.above(h), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
                            freigeraeumt++;
                        }
                    }
                }
            }
        }
        if (freigeraeumt > 0) {
            StinkendReich.LOGGER.info("Straßenwart: {} Blöcke (Pfeiler, Geländer) von der Fahrbahn geräumt", freigeraeumt);
        }
        for (var e : spalten.entrySet()) {
            int x = BlockPos.getX(e.getKey()), z = BlockPos.getZ(e.getKey()), y = e.getValue();
            for (int h = y; h <= y + 14; h++) {
                BlockPos p = new BlockPos(x, h, z);
                if (welt.getFluidState(p).isEmpty() || !welt.hasChunkAt(p) || villaBei(p)) {
                    continue;
                }
                // Zulauf von außen (waagerecht neben dem Bereich oder von oben über dem Bereich) zumauern
                for (net.minecraft.core.Direction r : net.minecraft.core.Direction.values()) {
                    if (r == net.minecraft.core.Direction.DOWN) {
                        continue;
                    }
                    BlockPos n = p.relative(r);
                    boolean aussen = r == net.minecraft.core.Direction.UP ? h == y + 14 : !bereich.contains(BlockPos.asLong(n.getX(), 0, n.getZ()));
                    if (aussen && !welt.getFluidState(n).isEmpty() && !villaBei(n)) {
                        welt.setBlock(n, net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
                        abgedichtet++;
                    }
                }
                // Was im Bereich steht, verschwindet (unten an der Straße die Fahrbahnhöhe nicht anfassen: dort sind
                // Brücken über Wasser gebaut, die Flüssigkeit liegt dann darunter)
                if (h > y) {
                    var zustand = welt.getBlockState(p);
                    if (zustand.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock) {
                        welt.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
                    } else if (zustand.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)) {
                        welt.setBlock(p, zustand.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, false),
                                net.minecraft.world.level.block.Block.UPDATE_ALL);
                    } else {
                        welt.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
                    }
                    entfernt++;
                    if (erste == null) {
                        erste = p.immutable();
                    }
                }
            }
        }
        if (abgedichtet + entfernt > 0) {
            StinkendReich.LOGGER.info("Straßenwart: {} Zuläufe zugemauert, {} Wasser-/Lavablöcke von der Straße entfernt (z. B. bei {}, Straße auf {})",
                    abgedichtet, entfernt, erste == null ? "-" : erste.toShortString(), erste == null ? "-" : spalten.get(BlockPos.asLong(erste.getX(), 0, erste.getZ())));
        }
    }

    private static boolean villaBei(BlockPos p) {
        for (VillaPlatzierung.VillaInfo v : VillaPlatzierung.alle()) {
            if (v.inDerNaehe(p, 8)) {
                return true;
            }
        }
        return false;
    }

    /** Liegt der Gegenstand neben einem Wegpunkt (gleicher oder benachbarter Chunk)? */
    private boolean anderStrasse(ItemEntity item) {
        int cx = item.blockPosition().getX() >> 4, cz = item.blockPosition().getZ() >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                List<BlockPos> pkt = raster.get(chunk(cx + dx, cz + dz));
                if (pkt == null) {
                    continue;
                }
                for (BlockPos p : pkt) {
                    double x = item.getX() - (p.getX() + 0.5), z = item.getZ() - (p.getZ() + 0.5);
                    if (x * x + z * z <= ABSTAND * ABSTAND && Math.abs(item.getY() - p.getY()) <= HOEHE) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Auf dem Grundstück einer Villa (nicht nur in der Nähe) bleibt alles liegen. */
    private static boolean aufEinemGrundstueck(ItemEntity item) {
        for (VillaPlatzierung.VillaInfo v : VillaPlatzierung.alle()) {
            if (v.inDerNaehe(item.blockPosition(), 0)) {
                return true;
            }
        }
        return false;
    }

    private static long chunk(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xffffffffL);
    }
}
