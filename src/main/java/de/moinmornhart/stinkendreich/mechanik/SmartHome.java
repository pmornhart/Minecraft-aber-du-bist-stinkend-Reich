package de.moinmornhart.stinkendreich.mechanik;

import com.google.gson.JsonArray;
import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.register.ModBloecke;
import de.moinmornhart.stinkendreich.villa.VillaPlatzierung;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Smart Home der Villa, gesteuert über die Smart-Home-App im Handy: Rollläden hoch/runter, Zugbrücke ein/aus, alle
 * Hängelampen ({@link SmartlichtBlock}) an/aus und in acht Farben, Disco (die Farben wandern durchs Haus) und eine
 * Automatik: Abends (Sonnenuntergang) gehen die Rollläden runter, die Lampen an und die Brücke fährt ein, morgens
 * umgekehrt. Die Einstellungen liegen im {@link VillaZustand}. Bedienen dürfen nur Bewohner.
 */
public final class SmartHome {
    /** Abend und Morgen in Tageszeit-Ticks (Sonnenuntergang, Sonnenaufgang). */
    private static final long ABEND = 12500, MORGEN = 23500;
    private static final Eigenschaften.Lichtfarbe[] DISCO = {Eigenschaften.Lichtfarbe.ROT, Eigenschaften.Lichtfarbe.PINK,
            Eigenschaften.Lichtfarbe.LILA, Eigenschaften.Lichtfarbe.BLAU, Eigenschaften.Lichtfarbe.GRUEN,
            Eigenschaften.Lichtfarbe.GOLD};

    /** Lampen der Villa (Weltpositionen, aus den Marken „smartlicht“), erst beim ersten Bedarf berechnet. */
    private static final java.util.Map<Integer, List<BlockPos>> LAMPEN = new java.util.concurrent.ConcurrentHashMap<>();
    /** Muss der Zustand der Lampen neu gesetzt werden? */
    private static boolean lampenNeu = true;
    /** Zuletzt gesehene Tageshälfte für die Automatik (null = noch unbekannt). */
    private static Boolean warAbend;

    private SmartHome() {
    }

    public static void registrieren() {
        ServerTickEvents.END_SERVER_TICK.register(SmartHome::tick);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            LAMPEN.clear();
            lampenNeu = true;
            warAbend = null;
        });
    }

    /** Sind die Rollläden per App oder Automatik heruntergefahren? (Die Torsteuerung fragt das ab.) */
    public static boolean rolllaedenZu(VillaZustand z) {
        return z.smart("rollo_zu", false);
    }

    /**
     * Befehl aus der App: „rollo|zu/auf“, „licht|an/aus“, „farbe|rot …“, „disco|an/aus“, „bruecke|zu/auf“,
     * „automatik|an/aus“.
     */
    /**
     * Sicherheitsanlagen, die man im Handy einzeln steuern kann (Philip, 08.11.: „dass ich die Abwehrgeschütze auch per
     * Smart Home aktivieren kann und auch alle anderen Alarmanlagen“). Je Anlage „auto“ (folgt der Alarmstufe, Standard),
     * „an“ (immer aktiv, auch ohne Festung) oder „aus“ (abgeschaltet).
     */
    public static final String[] ANLAGEN = {"geschuetze", "laser", "melder", "sirenen", "minen", "fallen"};

    /** Einstellung einer Sicherheitsanlage: {@code automatisch} bei „auto“, sonst die feste Wahl aus dem Handy. */
    public static boolean wahl(VillaZustand z, String anlage, boolean automatisch) {
        return switch (z.smartText("anlage_" + anlage, "auto")) {
            case "an" -> true;
            case "aus" -> false;
            default -> automatisch;
        };
    }

    /** Zustand aller Schalter für die Handy-App: „rollo=1,bruecke=0,…“ (1 = an/zu/eingefahren). */
    public static String status(MinecraftServer server, ServerPlayer p) {
        VillaZustand z = VillaZustand.vonSpieler(server, p);
        StringBuilder anlagen = new StringBuilder(",stufe=" + z.stufe() + ",alarm=" + (z.alarmAktiv() ? 1 : 0)
                + ",eigentuemer=" + (z.istEigentuemer(p) ? 1 : 0));
        for (String a : ANLAGEN) {
            anlagen.append(",anlage_").append(a).append('=').append(z.smartText("anlage_" + a, "auto"));
        }
        return anlagen.substring(1) + ",rollo=" + (z.smart("rollo_zu", false) ? 1 : 0) + ",bruecke=" + (z.brueckeGesichert() ? 1 : 0)
                + ",licht=" + (z.smart("licht", true) ? 1 : 0) + ",disco=" + (z.smart("disco", false) ? 1 : 0)
                + ",automatik=" + (z.smart("automatik", false) ? 1 : 0) + ",inkognito=" + (z.smart("inkognito", false) ? 1 : 0)
                + ",krisenbunker=" + (z.smart("krisenbunker", false) ? 1 : 0)
                + ",tor=" + (z.smart("tor_zu", false) ? 1 : 0) + ",garage=" + (z.smart("garage_zu", false) ? 1 : 0);
    }

    public static void befehl(ServerPlayer p, String angabe) {
        MinecraftServer server = p.level().getServer();
        if (angabe.equals("status")) {
            return;                                              // nur den Zustand abfragen (Antwort schickt Handy)
        }
        VillaZustand z = VillaZustand.vonSpieler(server, p);     // die eigene Villa (in der Nachbarschaft jeder seine)
        if (!z.istBewohner(p)) {
            Kompat.nachricht(p, Component.translatable("stinkendreich.smarthome.nur_bewohner").withStyle(ChatFormatting.RED), true);
            return;
        }
        String[] t = angabe.split("\\|", 2);
        String was = t[0], wert = t.length > 1 ? t[1] : "";
        boolean an = wert.equals("an") || wert.equals("zu");
        switch (was) {
            case "stufe" -> {
                // Alarmstufe 0–4 (4 = Festung) – wie an der Zentrale nur der Eigentümer; übers Handy auch die Festung
                if (!z.istEigentuemer(p)) {
                    Kompat.nachricht(p, Component.translatable("stinkendreich.zentrale.nur_eigentuemer").withStyle(ChatFormatting.RED), true);
                    return;
                }
                try {
                    z.setzeStufe(Integer.parseInt(wert));     // meldet die neue Stufe selbst allen Bewohnern
                } catch (NumberFormatException ignoriert) {
                    // ungültiger Wert
                }
                return;
            }
            case "alarmaus" -> {
                z.alarmAus();
                Kompat.nachricht(p, Component.translatable("stinkendreich.alarm.ende").withStyle(ChatFormatting.GREEN), true);
                return;
            }
            case "anlage" -> {
                // „anlage|geschuetze:an“ – nur der Eigentümer
                String[] aw = wert.split(":", 2);
                if (aw.length != 2 || !java.util.Arrays.asList(ANLAGEN).contains(aw[0]) || !java.util.List.of("auto", "an", "aus").contains(aw[1])) {
                    return;
                }
                if (!z.istEigentuemer(p)) {
                    Kompat.nachricht(p, Component.translatable("stinkendreich.zentrale.nur_eigentuemer").withStyle(ChatFormatting.RED), true);
                    return;
                }
                z.setzeSmart("anlage_" + aw[0], aw[1]);
                Kompat.nachricht(p, Component.translatable("stinkendreich.smarthome.anlage." + aw[1],
                        Component.translatable("stinkendreich.handy.sh.anlage." + aw[0])).withStyle(ChatFormatting.AQUA), true);
                return;
            }
            case "rollo" -> z.setzeSmart("rollo_zu", an);
            case "licht" -> {
                z.setzeSmart("licht", an);
                if (!an) {
                    z.setzeSmart("disco", false);
                }
            }
            case "farbe" -> {
                if (Eigenschaften.Lichtfarbe.von(wert) == null) {
                    return;
                }
                z.setzeSmart("farbe", wert);
                z.setzeSmart("licht", true);
                z.setzeSmart("disco", false);
            }
            case "disco" -> {
                z.setzeSmart("disco", an);
                if (an) {
                    z.setzeSmart("licht", true);
                }
            }
            case "bruecke" -> {
                String fehler = Zugbruecke.sichern(server, z.nr(), an);
                if (fehler != null) {
                    Kompat.nachricht(p, Component.translatable(fehler).withStyle(ChatFormatting.RED), true);
                    return;
                }
            }
            case "automatik" -> z.setzeSmart("automatik", an);
            // Eingangstor bzw. Garagentor geschlossen halten (siehe TorsteuerungBlock)
            case "tor" -> z.setzeSmart("tor_zu", an);
            case "garage" -> z.setzeSmart("garage_zu", an);
            case "inkognito" -> {
                String fehler = Inkognito.schalten(server, z.nr(), an);
                if (fehler != null) {
                    Kompat.nachricht(p, Component.translatable(fehler).withStyle(ChatFormatting.RED), true);
                    return;
                }
            }
            case "krisenbunker" -> {
                // Haus dicht machen: Aufzug gesperrt (siehe Aufzug), Rollläden zu, Zugbrücke hoch
                z.setzeSmart("krisenbunker", an);
                if (an) {
                    z.setzeSmart("rollo_zu", true);
                    Zugbruecke.sichern(server, z.nr(), true);
                }
            }
            case "haie" -> {
                String fehler = Haifalle.smartHome(server, z.nr(), wert.equals("auf"));
                if (fehler != null) {
                    Kompat.nachricht(p, Component.translatable(fehler).withStyle(ChatFormatting.RED), true);
                }
                return;                                          // Meldung kommt von der Falle selbst
            }
            default -> {
                return;
            }
        }
        lampenNeu = true;
        Kompat.nachricht(p, Component.translatable("stinkendreich.smarthome." + was + "." + (an ? "an" : "aus"),
                Component.translatable("stinkendreich.smarthome.farbe." + wert)).withStyle(ChatFormatting.AQUA), true);
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 10 != 0 || VillaPlatzierung.alle().isEmpty()) {
            return;
        }
        ServerLevel welt = server.overworld();
        // Abend oder Morgen? Gewechselt wird für alle Villen gleichzeitig (es ist überall dieselbe Uhrzeit)
        //? if >=26.1 {
        long zeit = Math.floorMod(welt.getDefaultClockTime(), 24000L);
        //?} else
        /*long zeit = Math.floorMod(welt.getDayTime(), 24000L);*/
        boolean abend = zeit >= ABEND && zeit < MORGEN;
        boolean wechsel = warAbend != null && abend != warAbend;
        warAbend = abend;
        boolean neu = lampenNeu;
        lampenNeu = false;
        for (VillaPlatzierung.VillaInfo v : VillaPlatzierung.alle()) {
            if (Inkognito.aktiv(server, v.nr())) {
                continue;
            }
            VillaZustand z = VillaZustand.get(server, v.nr());
            if (wechsel) {
                automatik(welt, z, abend);
            }
            boolean disco = z.smart("disco", false) && z.smart("licht", true);
            if (neu || wechsel || disco) {
                lampenSetzen(welt, z, lampen(v), disco ? (int) (server.getTickCount() / 10) : -1);
            }
        }
    }

    /** Abends Rollläden runter, Licht an, Brücke ein; morgens umgekehrt (nur beim Wechsel, damit die App gilt). */
    private static void automatik(ServerLevel welt, VillaZustand z, boolean abend) {
        if (!z.smart("automatik", true)) {
            return;
        }
        z.setzeSmart("rollo_zu", abend);
        z.setzeSmart("licht", abend);
        if (abend) {
            if (!z.brueckeGesichert() && Zugbruecke.sichern(welt.getServer(), z.nr(), true) == null) {
                z.setzeSmart("bruecke_auto", true);                // morgens wieder ausfahren
            }
        } else if (z.smart("bruecke_auto", false)) {
            Zugbruecke.sichern(welt.getServer(), z.nr(), false);
            z.setzeSmart("bruecke_auto", false);
        }
        z.anBewohner(Component.translatable(abend ? "stinkendreich.smarthome.abend" : "stinkendreich.smarthome.morgen")
                .withStyle(ChatFormatting.GOLD), true);
    }

    /** Alle geladenen Lampen auf den eingestellten Zustand setzen; {@code disco} ≥ 0 = Schritt der Farbwelle. */
    private static void lampenSetzen(ServerLevel welt, VillaZustand z, List<BlockPos> lampen, int disco) {
        boolean an = z.smart("licht", true);
        Eigenschaften.Lichtfarbe farbe = Eigenschaften.Lichtfarbe.von(z.smartText("farbe", "warm"));
        if (farbe == null) {
            farbe = Eigenschaften.Lichtfarbe.WARM;
        }
        for (BlockPos pos : lampen) {
            if (!welt.isLoaded(pos)) {
                continue;
            }
            BlockState s = welt.getBlockState(pos);
            if (!s.is(ModBloecke.SMARTLICHT)) {
                continue;
            }
            Eigenschaften.Lichtfarbe f = disco >= 0 ? DISCO[Math.floorMod(disco + (pos.getX() + pos.getZ()) / 3, DISCO.length)] : farbe;
            BlockState neu = s.setValue(Eigenschaften.LEUCHTET, an).setValue(Eigenschaften.LICHTFARBE, f);
            if (neu != s) {
                welt.setBlock(pos, neu, 2);
            }
        }
    }

    /** Lampen einer Villa (aus den Marken „smartlicht“ des Bauplans). */
    private static List<BlockPos> lampen(VillaPlatzierung.VillaInfo v) {
        return LAMPEN.computeIfAbsent(v.nr(), n -> {
            List<BlockPos> liste = new ArrayList<>();
            for (JsonArray m : v.plan().marke("smartlicht")) {
                liste.add(v.weltPos(m.get(0).getAsDouble(), m.get(1).getAsDouble(), m.get(2).getAsDouble()));
            }
            return liste;
        });
    }
}
