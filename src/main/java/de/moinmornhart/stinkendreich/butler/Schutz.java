package de.moinmornhart.stinkendreich.butler;

import de.moinmornhart.stinkendreich.villa.VillaPlatzierung;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/**
 * Regeln, was der Butler abbauen darf. Er baut nur natürliche Blöcke ab (Stein, Erde, Sand, Erze,
 * Stämme echter Bäume …), nie etwas an der Villa und nie einen Block, den ein Spieler gesetzt hat.
 */
public final class Schutz {
    /** Sicherheitsabstand rund um das Villa-Grundstück (Blöcke). */
    private static final int VILLA_ABSTAND = 3;

    private Schutz() {
    }

    /** Darf der Butler diesen Block abbauen? */
    public static boolean darfAbbauen(ServerLevel welt, BlockPos pos, BlockState zustand) {
        if (zustand.isAir() || !zustand.getFluidState().isEmpty()) {
            return false;
        }
        if (welt.getBlockEntity(pos) != null) {
            return false; // Truhen, Öfen, Spawner …
        }
        float haerte = zustand.getDestroySpeed(welt, pos);
        if (haerte < 0 || haerte > 10) {
            return false; // Grundgestein, Obsidian
        }
        if (inDerVilla(welt, pos) || SpielerBloecke.istGesetzt(welt, pos)) {
            return false;
        }
        return istNatuerlich(zustand);
    }

    /** Liegt die Position auf dem Grundstück irgendeiner Villa (mit Sicherheitsabstand)? Gilt für alle Villen der Welt. */
    public static boolean inDerVilla(Level welt, BlockPos pos) {
        if (welt.dimension() != Level.OVERWORLD) {
            return false;
        }
        for (VillaPlatzierung.VillaInfo v : VillaPlatzierung.alle()) {
            BlockPos u = v.ursprung();
            if (pos.getX() >= u.getX() - VILLA_ABSTAND && pos.getX() < u.getX() + v.plan().breiteX + VILLA_ABSTAND
                    && pos.getZ() >= u.getZ() - VILLA_ABSTAND && pos.getZ() < u.getZ() + v.plan().breiteZ + VILLA_ABSTAND
                    && pos.getY() >= u.getY() - VILLA_ABSTAND && pos.getY() < u.getY() + v.plan().hoehe + VILLA_ABSTAND) {
                return true;
            }
        }
        return false;
    }

    /** Kommt dieser Block so in der Natur vor? Alles andere gilt als gebaut. */
    public static boolean istNatuerlich(BlockState s) {
        if (s.is(BlockTags.LEAVES)) {
            // Feste Blätter (persistent) hat jemand gesetzt, natürliche Blätter zerfallen
            return !(s.hasProperty(LeavesBlock.PERSISTENT) && s.getValue(LeavesBlock.PERSISTENT));
        }
        return s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.BASE_STONE_NETHER)
                || s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(BlockTags.LOGS)
                // Ab 26.1 stehen Gras, Podsol, Myzel, Schlamm und Moos nicht mehr in der Gruppe „Erde“
                || s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.PODZOL) || s.is(Blocks.MYCELIUM) || s.is(Blocks.MUD)
                || s.is(Blocks.MOSS_BLOCK) || s.is(Blocks.MUDDY_MANGROVE_ROOTS) || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.ROOTED_DIRT)
                || net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath().startsWith("pale_")
                // Wilde Pflanzen, Eis und Gestein der Tafelberge (Felder im Dorf bleiben geschützt: Getreide fehlt hier bewusst)
                || s.is(BlockTags.FLOWERS) || s.getBlock() instanceof net.minecraft.world.level.block.SaplingBlock || s.is(BlockTags.ICE) || s.is(BlockTags.TERRACOTTA)
                || s.is(BlockTags.CORAL_BLOCKS) || s.is(BlockTags.CORALS) || s.is(BlockTags.WALL_CORALS)
                || s.is(Blocks.CACTUS) || s.is(Blocks.SUGAR_CANE) || s.is(Blocks.PUMPKIN) || s.is(Blocks.MELON) || s.is(Blocks.BAMBOO)
                || s.is(Blocks.SWEET_BERRY_BUSH) || s.is(Blocks.BROWN_MUSHROOM) || s.is(Blocks.RED_MUSHROOM)
                || s.is(Blocks.BROWN_MUSHROOM_BLOCK) || s.is(Blocks.RED_MUSHROOM_BLOCK) || s.is(Blocks.MUSHROOM_STEM)
                || s.is(Blocks.LILY_PAD) || s.is(Blocks.KELP) || s.is(Blocks.KELP_PLANT) || s.is(Blocks.SEA_PICKLE)
                || s.is(Blocks.MOSS_CARPET) || s.is(Blocks.BIG_DRIPLEAF) || s.is(Blocks.BIG_DRIPLEAF_STEM) || s.is(Blocks.SMALL_DRIPLEAF)
                || s.is(Blocks.AZALEA) || s.is(Blocks.FLOWERING_AZALEA) || s.is(Blocks.SPORE_BLOSSOM) || s.is(Blocks.GLOW_LICHEN)
                || s.is(Blocks.COCOA) || s.is(Blocks.MANGROVE_ROOTS) || s.is(Blocks.MANGROVE_PROPAGULE)
                || s.is(Blocks.CAVE_VINES) || s.is(Blocks.CAVE_VINES_PLANT) || s.is(Blocks.PACKED_ICE) || s.is(Blocks.BLUE_ICE)
                || s.is(Blocks.POWDER_SNOW) || s.is(Blocks.AMETHYST_BLOCK) || s.is(Blocks.BUDDING_AMETHYST) || s.is(Blocks.OBSIDIAN)
                || istErz(s)
                || s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY) || s.is(Blocks.SANDSTONE) || s.is(Blocks.RED_SANDSTONE)
                || s.is(Blocks.CALCITE) || s.is(Blocks.DRIPSTONE_BLOCK) || s.is(Blocks.POINTED_DRIPSTONE)
                || s.is(Blocks.SMOOTH_BASALT) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.SNOW)
                || s.is(Blocks.SOUL_SAND) || s.is(Blocks.SOUL_SOIL) || s.is(Blocks.MAGMA_BLOCK)
                // Gras, Blumen, Ranken und Schneedecken (selbst gepflanzte sind über SpielerBloecke geschützt)
                || s.canBeReplaced();
    }

    /** Namensteile von Blöcken, die nur Spieler (oder Dörfer und andere Bauwerke) setzen. */
    private static final String[] GEBAUT = {"planks", "_stairs", "_slab", "fence", "_wall", "glass", "wool", "carpet",
            "_bed", "door", "sign", "banner", "torch", "lantern", "brick", "concrete", "glazed", "crafting_table",
            "furnace", "smoker", "chest", "barrel", "rail", "polished_", "smooth_stone", "cut_", "chiseled_", "cobblestone",
            "bookshelf", "anvil", "hopper", "ladder", "stripped_", "button", "pressure_plate", "lever", "redstone",
            "piston", "observer", "dispenser", "dropper", "bell", "cauldron", "potted_", "candle", "campfire", "composter",
            "lectern", "loom", "stonecutter", "grindstone", "smithing_table", "cartography_table", "fletching_table",
            "beehive", "scaffolding", "hay_block", "target", "iron_block", "gold_block", "diamond_block", "emerald_block",
            "netherite_block", "quartz", "purpur", "prismarine", "sea_lantern", "shulker_box", "enchanting_table",
            "brewing_stand", "beacon", "jukebox", "note_block", "farmland", "dirt_path", "iron_bars", "chain", "copper_",
            "_tiles", "grate", "bulb", "crafter", "decorated_pot", "spawner"};
    /** Eindeutige Spuren von Spielern unter der Erde (in Minen und Höhlen von Minecraft gibt es sie nicht). */
    private static final String[] SPUR = {"torch", "crafting_table", "furnace", "smoker", "_bed", "sign", "barrel",
            "lantern", "shulker_box", "enchanting_table", "brewing_stand"};
    /** Antworten je Blockart (die Platzprüfung einer neuen Villa fragt hunderttausende Blöcke ab; nur Server-Thread). */
    private static final Map<Block, Boolean> GEBAUT_MERK = new HashMap<>(), SPUR_MERK = new HashMap<>();

    /**
     * Ein Block, den Spieler (oder ein Dorf) gesetzt haben – nie Gelände, Pflanzen, Wasser oder Luft. Für die Platzwahl
     * neuer Villen, zusätzlich zu {@link SpielerBloecke} (das kennt nur, was seit der Mod gesetzt wurde).
     */
    public static boolean istGebaut(BlockState s) {
        return !s.isAir() && s.getFluidState().isEmpty() && !istNatuerlich(s)
                && GEBAUT_MERK.computeIfAbsent(s.getBlock(), b -> enthaelt(b, GEBAUT));
    }

    /** Ein Block unter der Erde, der sicher von einem Spieler stammt (Fackel, Werkbank, Ofen, Bett …). */
    public static boolean istSpielerSpur(BlockState s) {
        return !s.isAir() && SPUR_MERK.computeIfAbsent(s.getBlock(), b -> enthaelt(b, SPUR));
    }

    private static boolean enthaelt(Block b, String[] teile) {
        String name = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b).getPath();
        for (String t : teile) {
            if (name.contains(t)) {
                return true;
            }
        }
        return false;
    }

    /** Alle Erze der Oberwelt und des Nethers. */
    public static boolean istErz(BlockState s) {
        // Kohle, Diamant, Smaragd, Redstone und Lapis haben seit 26.2 keine eigenen Gruppen mehr
        return s.is(BlockTags.IRON_ORES) || s.is(BlockTags.COPPER_ORES) || s.is(BlockTags.GOLD_ORES)
                || s.is(Blocks.COAL_ORE) || s.is(Blocks.DEEPSLATE_COAL_ORE)
                || s.is(Blocks.DIAMOND_ORE) || s.is(Blocks.DEEPSLATE_DIAMOND_ORE)
                || s.is(Blocks.EMERALD_ORE) || s.is(Blocks.DEEPSLATE_EMERALD_ORE)
                || s.is(Blocks.REDSTONE_ORE) || s.is(Blocks.DEEPSLATE_REDSTONE_ORE)
                || s.is(Blocks.LAPIS_ORE) || s.is(Blocks.DEEPSLATE_LAPIS_ORE) || s.is(Blocks.NETHER_QUARTZ_ORE);
    }
}
