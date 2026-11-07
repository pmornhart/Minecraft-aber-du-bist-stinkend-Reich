package de.moinmornhart.stinkendreich.register;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Deckenblöcke der Villa: oben und an den Seiten wie ein Block aus dem Spiel (Holzboden, Teppich, Gold …),
 * unten aber weiß. So bleibt der Boden im Obergeschoss und im Penthouse wie er ist, und die Decke darunter ist
 * überall weiß. Der Bauplan (tools/villa/villa.py, {@code decken_weiss}) setzt sie überall dort ein, wo man einen
 * Boden von unten sieht. Aussehen: tools/assets/assets.py ({@code DECKEN}).
 */
public final class ModDecken {
    /** Kurzname → Block (registriert als „decke_&lt;Kurzname&gt;“). */
    public static final Map<String, Block> BLOECKE = new LinkedHashMap<>();
    /** Kurzname → ID des Vorbilds aus dem Spiel. */
    public static final Map<String, String> VORBILDER = new LinkedHashMap<>();

    static {
        // Vorbilder über ihre ID (Wolle und Beton heißen im Code je nach Version anders)
        decke("holz", "dark_oak_planks");
        decke("teppich_weiss", "white_wool");
        decke("teppich_rot", "red_wool");
        decke("teppich_gelb", "yellow_wool");
        decke("grau", "gray_concrete");
        decke("gold", "gold_block");
        decke("schwarz", "polished_blackstone");
        decke("schiefer", "polished_deepslate");
    }

    private ModDecken() {
    }

    /** Übernimmt Härte, Klang und Werkzeug vom Vorbild. */
    private static void decke(String name, String vorbild) {
        Block block = de.moinmornhart.stinkendreich.Kompat.blockNachId(net.minecraft.resources.Identifier.withDefaultNamespace(vorbild));
        VORBILDER.put(name, vorbild);
        BLOECKE.put(name, ModRegister.block("decke_" + name, Block::new, BlockBehaviour.Properties.ofFullCopy(block)));
    }

    public static void init() {
        // Laden der Klasse registriert alle Deckenblöcke
    }
}
