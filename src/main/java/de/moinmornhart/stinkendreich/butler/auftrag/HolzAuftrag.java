package de.moinmornhart.stinkendreich.butler.auftrag;

import com.google.gson.JsonObject;
import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.Konfig;
import de.moinmornhart.stinkendreich.butler.Abbau;
import de.moinmornhart.stinkendreich.butler.ButlerEntity;
import de.moinmornhart.stinkendreich.butler.Schutz;
import de.moinmornhart.stinkendreich.butler.Suche;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Fällt natürliche Bäume (Stämme mit echtem Laub), bis genug Holz beisammen ist, und pflanzt
 * danach einen Setzling. Gebaute Holzhäuser erkennt er am fehlenden Laub bzw. als Spielerblöcke.
 */
public class HolzAuftrag extends Auftrag {
    public static final String TYP = "holz";

    private enum Phase { RAUS, SUCHEN, HIN, FAELLEN, PFLANZEN }

    private final int menge;
    private int gesammelt;

    private Phase phase = Phase.RAUS;
    private Suche suche;
    private List<BlockPos> kandidaten = new ArrayList<>();
    private final Set<BlockPos> gesperrt = new HashSet<>();
    private BlockPos fuss;
    private final List<BlockPos> stamm = new ArrayList<>();
    private BlockState holzArt;
    private int wartezeit;
    private final Abbau abbau = new Abbau();

    public HolzAuftrag(int menge) {
        this.menge = menge;
    }

    @Override
    protected Stand tick(ButlerEntity butler) {
        if (gesammelt >= menge && phase != Phase.PFLANZEN) {
            return Stand.FERTIG;
        }
        if (butler.taschen().freiePlaetze() == 0) {
            butler.sagen(Component.translatable("stinkendreich.butler.voll"));
            return Stand.FERTIG;
        }
        ServerLevel welt = butler.welt();
        switch (phase) {
            case RAUS -> {
                if (Ausgang.hinaus(butler) != Stand.LAEUFT) {
                    phase = Phase.SUCHEN;
                }
            }
            case SUCHEN -> {
                if (suche == null) {
                    suche = new Suche(butler.blockPosition(), Konfig.get().butlerSuchradius, s -> s.is(BlockTags.LOGS));
                }
                if (suche.weiter(welt)) {
                    kandidaten = new ArrayList<>();
                    Set<BlockPos> schonDa = new HashSet<>();
                    for (BlockPos p : suche.sortiert(butler.blockPosition())) {
                        BlockPos f = stammFuss(welt, p);
                        if (f != null && !gesperrt.contains(f) && schonDa.add(f) && istNatuerlicherBaum(welt, f)) {
                            kandidaten.add(f);
                        }
                        if (kandidaten.size() > 40) {
                            break;
                        }
                    }
                    suche = null;
                    if (!naechsterBaum()) {
                        if (gesammelt > 0) {
                            butler.sagen(Component.translatable("stinkendreich.butler.nur", gesammelt, Items.OAK_LOG.getDefaultInstance().getHoverName()));
                            return Stand.FERTIG;
                        }
                        return fehlschlag(Component.translatable("stinkendreich.butler.keinbaum"));
                    }
                }
            }
            case HIN -> {
                ButlerEntity.Weg w = butler.gehZu(fuss, 2.5);
                if (w == ButlerEntity.Weg.UNERREICHBAR) {
                    gesperrt.add(fuss);
                    if (!naechsterBaum()) {
                        phase = Phase.SUCHEN;
                    }
                } else if (w == ButlerEntity.Weg.DA) {
                    stammErfassen(welt);
                    phase = Phase.FAELLEN;
                }
            }
            case FAELLEN -> {
                if (stamm.isEmpty()) {
                    phase = Phase.PFLANZEN;
                    wartezeit = 0;
                    return Stand.LAEUFT;
                }
                BlockPos p = stamm.get(0);
                if (!welt.getBlockState(p).is(BlockTags.LOGS)) {
                    stamm.remove(0);
                    return Stand.LAEUFT;
                }
                if (butler.inReichweite(p)) {
                    Abbau.Ergebnis e = abbau.tick(butler, p, Items.IRON_AXE, s -> s.is(ItemTags.LOGS));
                    if (e == Abbau.Ergebnis.FERTIG) {
                        gesammelt += abbau.zuletztGezaehlt();
                        stamm.remove(0);
                    } else if (e == Abbau.Ergebnis.VERBOTEN) {
                        stamm.remove(0);
                    }
                } else if (++wartezeit % 3 == 0) {
                    // Der Rest des Stamms ist außer Reichweite: der angesägte Baum fällt
                    BlockState s = welt.getBlockState(p);
                    if (Schutz.darfAbbauen(welt, p, s)) {
                        List<ItemStack> ertrag = Block.getDrops(s, welt, p, null, butler, new ItemStack(Items.IRON_AXE));
                        welt.destroyBlock(p, false, butler);
                        gesammelt += Abbau.einpacken(butler, p, ertrag, st -> st.is(ItemTags.LOGS));
                    }
                    stamm.remove(0);
                }
            }
            case PFLANZEN -> {
                pflanzen(butler);
                fuss = null;
                if (gesammelt >= menge) {
                    return Stand.FERTIG;
                }
                if (!naechsterBaum()) {
                    phase = Phase.SUCHEN;
                }
            }
        }
        return Stand.LAEUFT;
    }

    private boolean naechsterBaum() {
        while (!kandidaten.isEmpty()) {
            BlockPos f = kandidaten.remove(0);
            if (!gesperrt.contains(f)) {
                fuss = f;
                phase = Phase.HIN;
                return true;
            }
        }
        return false;
    }

    /** Unterster Stammblock eines Baums (darunter Erde oder Gras). */
    private static BlockPos stammFuss(ServerLevel welt, BlockPos p) {
        BlockPos.MutableBlockPos m = p.mutable();
        int schritte = 0;
        while (welt.getBlockState(m.below()).is(BlockTags.LOGS) && schritte++ < 32) {
            m.move(0, -1, 0);
        }
        return istBoden(welt.getBlockState(m.below())) ? m.immutable() : null;
    }

    /** Boden, auf dem ein Baum wächst. Ab 26.1 gehören Gras, Podsol, Myzel, Schlamm und Moos nicht mehr zur Gruppe „Erde“. */
    private static boolean istBoden(BlockState s) {
        return s.is(BlockTags.DIRT) || s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.PODZOL) || s.is(Blocks.MYCELIUM)
                || s.is(Blocks.MUD) || s.is(Blocks.MOSS_BLOCK) || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.ROOTED_DIRT);
    }

    /** Ein echter Baum: Stamm darf abgebaut werden und trägt natürliches (nicht festes) Laub. */
    private static boolean istNatuerlicherBaum(ServerLevel welt, BlockPos fuss) {
        if (!Schutz.darfAbbauen(welt, fuss, welt.getBlockState(fuss))) {
            return false;
        }
        BlockPos.MutableBlockPos m = fuss.mutable();
        int hoehe = 0;
        while (welt.getBlockState(m.above()).is(BlockTags.LOGS) && hoehe++ < 32) {
            m.move(0, 1, 0);
        }
        for (BlockPos q : BlockPos.betweenClosed(m.offset(-3, -2, -3), m.offset(3, 3, 3))) {
            BlockState s = welt.getBlockState(q);
            if (s.is(BlockTags.LEAVES) && s.hasProperty(LeavesBlock.PERSISTENT) && !s.getValue(LeavesBlock.PERSISTENT)) {
                return true;
            }
        }
        return false;
    }

    /** Alle zusammenhängenden Stammblöcke des Baums, von unten nach oben. */
    private void stammErfassen(ServerLevel welt) {
        stamm.clear();
        holzArt = welt.getBlockState(fuss);
        Deque<BlockPos> offen = new ArrayDeque<>();
        Set<BlockPos> gesehen = new HashSet<>();
        offen.add(fuss);
        gesehen.add(fuss);
        while (!offen.isEmpty() && stamm.size() < 200) {
            BlockPos p = offen.poll();
            BlockState s = welt.getBlockState(p);
            if (!s.is(BlockTags.LOGS) || !Schutz.darfAbbauen(welt, p, s)) {
                continue;
            }
            stamm.add(p);
            for (BlockPos n : BlockPos.betweenClosed(p.offset(-1, 0, -1), p.offset(1, 1, 1))) {
                if (gesehen.add(n.immutable()) && n.getY() >= fuss.getY()) {
                    offen.add(n.immutable());
                }
            }
        }
        stamm.sort(Comparator.comparingInt(BlockPos::getY));
    }

    /** Pflanzt an der alten Stelle einen Setzling derselben Holzart. */
    private void pflanzen(ButlerEntity butler) {
        ServerLevel welt = butler.welt();
        if (fuss == null || holzArt == null || !welt.getBlockState(fuss).isAir()) {
            return;
        }
        String pfad = BuiltInRegistries.BLOCK.getKey(holzArt.getBlock()).getPath();
        String art = pfad.replace("stripped_", "").replace("_log", "").replace("_wood", "");
        String setzling = switch (art) {
            case "mangrove" -> "mangrove_propagule";
            case "crimson" , "warped" -> null;
            default -> art + "_sapling";
        };
        if (setzling == null) {
            return;
        }
        Block block = Kompat.blockNachId(Identifier.withDefaultNamespace(setzling));
        if (block == Blocks.AIR) {
            return;
        }
        BlockState s = block.defaultBlockState();
        if (s.canSurvive(welt, fuss)) {
            welt.setBlock(fuss, s, Block.UPDATE_ALL);
            Kompat.schwingen(butler);
        }
    }

    @Override
    public void abbrechen(ButlerEntity butler) {
        abbau.zuruecksetzen(butler);
    }

    @Override
    public Component status() {
        return Component.translatable(phase == Phase.SUCHEN ? "stinkendreich.butler.status.baumsuche" : "stinkendreich.butler.status.holz",
                gesammelt, menge);
    }

    @Override
    public String typ() {
        return TYP;
    }

    @Override
    public void schreiben(JsonObject j) {
        j.addProperty("menge", menge);
        j.addProperty("gesammelt", gesammelt);
    }

    static HolzAuftrag ausJson(JsonObject j) {
        HolzAuftrag a = new HolzAuftrag(zahl(j, "menge", 16));
        a.gesammelt = zahl(j, "gesammelt", 0);
        return a;
    }
}
