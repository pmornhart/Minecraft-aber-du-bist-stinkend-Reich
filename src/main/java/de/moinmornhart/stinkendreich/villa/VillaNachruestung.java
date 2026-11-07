package de.moinmornhart.stinkendreich.villa;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.StinkendReich;
import de.moinmornhart.stinkendreich.mechanik.Inkognito;
import de.moinmornhart.stinkendreich.mechanik.VillaZustand;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Nachrüstung schon gebauter Villen: Ändert sich der Bauplan (tools/villa/villa.py), gelten die Änderungen sonst nur für
 * neue Villen. Für jede Änderung liegt deshalb eine Liste der geänderten Blöcke in
 * {@code data/stinkendreich/villa_nachruestung/<name>.json} (erzeugt von tools/villa/nachruestung.py aus altem und neuem
 * Bauplan). Jede Liste wird einmal je Villa abgespielt, sobald alle ihre Chunks geladen sind.
 *
 * <p>Getauscht wird ein Block nur, wenn dort noch der alte Block aus dem Bauplan steht: Was ein Spieler umgebaut hat,
 * bleibt, wie es ist. Versteckte Villen (Inkognito) warten, bis sie wieder oben sind.</p>
 */
public final class VillaNachruestung {
    /** Alle Nachrüstungen in der Reihenfolge, in der sie entstanden sind. */
    private static final List<String> LISTEN = List.of("048_bar_kuppel");
    private static final List<Liste> GELADEN = new ArrayList<>();

    private record Eintrag(int x, int y, int z, String alt, String neu) {
    }

    private record Liste(String name, List<Eintrag> bloecke) {
    }

    private VillaNachruestung() {
    }

    public static void registrieren() {
        for (String name : LISTEN) {
            try (InputStream in = VillaNachruestung.class.getResourceAsStream("/data/stinkendreich/villa_nachruestung/" + name + ".json")) {
                if (in == null) {
                    StinkendReich.LOGGER.warn("Nachrüstung {} fehlt in der .jar", name);
                    continue;
                }
                JsonObject j = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                List<Eintrag> bloecke = new ArrayList<>();
                for (var e : j.getAsJsonArray("bloecke")) {
                    JsonArray a = e.getAsJsonArray();
                    bloecke.add(new Eintrag(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt(), a.get(3).getAsString(), a.get(4).getAsString()));
                }
                GELADEN.add(new Liste(name, bloecke));
            } catch (Exception e) {
                StinkendReich.LOGGER.warn("Nachrüstung {} nicht lesbar: {}", name, e.toString());
            }
        }
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 100 == 37) {
                pruefen(server);
            }
        });
    }

    private static void pruefen(MinecraftServer server) {
        ServerLevel welt = server.overworld();
        for (VillaPlatzierung.VillaInfo v : VillaPlatzierung.alle()) {
            if (Inkognito.aktiv(server, v.nr())) {
                continue;
            }
            VillaZustand z = VillaZustand.get(server, v.nr());
            for (Liste l : GELADEN) {
                String marke = "nachruestung_" + l.name();
                if (z.smart(marke, false) || !geladen(welt, v, l)) {
                    continue;
                }
                int getauscht = 0, belassen = 0;
                for (Eintrag e : l.bloecke()) {
                    BlockPos p = v.ursprung().offset(e.x(), e.y(), e.z());
                    String jetzt = BlockStateParser.serialize(welt.getBlockState(p));
                    if (passt(jetzt, e.neu())) {
                        continue;
                    }
                    if (!passt(jetzt, e.alt())) {
                        belassen++;
                        continue;
                    }
                    Kompat.befehl(server, String.format(Locale.ROOT, "setblock %d %d %d %s", p.getX(), p.getY(), p.getZ(), e.neu()));
                    getauscht++;
                }
                z.setzeSmart(marke, true);
                StinkendReich.LOGGER.info("Villa {}: Nachrüstung {} – {} Blöcke getauscht, {} vom Spieler veränderte belassen",
                        v.nr(), l.name(), getauscht, belassen);
            }
        }
    }

    /** Sind alle Chunks, die die Liste berührt, samt Figuren geladen? */
    private static boolean geladen(ServerLevel welt, VillaPlatzierung.VillaInfo v, Liste l) {
        Set<Long> chunks = new HashSet<>();
        for (Eintrag e : l.bloecke()) {
            int x = v.ursprung().getX() + e.x(), z = v.ursprung().getZ() + e.z();
            if (chunks.add(((long) (x >> 4) << 32) ^ ((z >> 4) & 0xffffffffL))
                    && !Kompat.ganzGeladen(welt, new BlockPos(x, v.ursprung().getY(), z))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Passt der Zustand in der Welt zum Bauplan? Gleicher Block und jede Eigenschaft, die im Bauplan steht, hat denselben
     * Wert (der Bauplan lässt Standardwerte teils weg, die Welt nennt immer alle).
     */
    static boolean passt(String welt, String plan) {
        if (!name(welt).equals(name(plan))) {
            return false;
        }
        Set<String> w = new HashSet<>(Arrays.asList(eigenschaften(welt)));
        for (String e : eigenschaften(plan)) {
            if (!e.isEmpty() && !w.contains(e)) {
                return false;
            }
        }
        return true;
    }

    private static String name(String zustand) {
        int k = zustand.indexOf('[');
        String n = k < 0 ? zustand : zustand.substring(0, k);
        return n.contains(":") ? n : "minecraft:" + n;
    }

    private static String[] eigenschaften(String zustand) {
        int k = zustand.indexOf('[');
        return k < 0 || !zustand.endsWith("]") ? new String[0] : zustand.substring(k + 1, zustand.length() - 1).split(",");
    }
}
