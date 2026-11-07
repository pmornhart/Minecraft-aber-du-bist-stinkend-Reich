package de.moinmornhart.stinkendreich.handy;

import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.butler.ButlerBefehle;
import de.moinmornhart.stinkendreich.butler.ButlerEntity;
import de.moinmornhart.stinkendreich.butler.ButlerVerwaltung;
import de.moinmornhart.stinkendreich.butler.auftrag.AnrufAuftrag;
import de.moinmornhart.stinkendreich.butler.auftrag.FahrAuftrag;
import de.moinmornhart.stinkendreich.butler.auftrag.FlugAuftrag;
import de.moinmornhart.stinkendreich.netz.Netz;
import de.moinmornhart.stinkendreich.register.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Was das Handy auf dem Server auslöst (Paket „handy“, Wert „Aktion|Angabe“):
 * <ul>
 *   <li>{@code fahr|Ziel} – James holt dich mit der Limo ab und fährt dich hin (Villa, Dorf, Endportal …); sitzt du
 *   schon im Hubschrauber, fliegt er dich</li>
 *   <li>{@code flieg|Ziel} – James holt dich mit dem Hubschrauber ab und fliegt dich hin, sobald du drin sitzt</li>
 *   <li>{@code anruf|limo} / {@code anruf|heli} – nur noch für alte Knöpfe und Spieler ohne Mod: James kommt und
 *   wartet am Steuer</li>
 *   <li>{@code orte} – schickt dem Handy die Liste der Orte, die James kennt</li>
 *   <li>{@code ort|merken|Name} / {@code ort|vergessen|Name} – Ort hier speichern (James kennt ihn dann) oder löschen</li>
 *   <li>{@code liefer|Was} – Lieferservice: James bringt Tiere oder Waren (siehe {@link Lieferservice})</li>
 *   <li>{@code auftrag|Text} – ein Auftrag wie im Chat („hol 32 Eisen“)</li>
 *   <li>{@code mitarbeiter} – eine Mitarbeiterkiste kommt ins Inventar</li>
 *   <li>{@code bodyguards|rufen} / {@code bodyguards|heim} – Leibwächter rufen oder heimschicken</li>
 *   <li>{@code bruecke} – Zugbrücke am Tor ein- oder ausfahren (nur Bewohner, siehe {@link de.moinmornhart.stinkendreich.mechanik.Zugbruecke})</li>
 * </ul>
 * Geht nur, wenn man ein Handy dabeihat.
 */
public final class Handy {
    /** James holt den Hubschrauber und fliegt damit zum Spieler (für Befehle außerhalb des Handys). */
    public static void heliHolen(ServerPlayer p, ButlerEntity b) {
        HeliBereitstellung.bereitstellen(p, b);
    }

    /** James kommt mit dem Hubschrauber zu einer Stelle (z. B. zu einem Wagen, den er abschleppt) und sitzt danach am Steuer. */
    public static void heliHolenZu(net.minecraft.server.level.ServerLevel welt, net.minecraft.world.phys.Vec3 ziel, ButlerEntity b) {
        HeliBereitstellung.bereitstellen(welt, ziel, b, true);
    }

    private Handy() {
    }

    /** Aufträge, die warten, bis James aus einem nicht geladenen Gebiet herbeigeholt ist: Spieler → (Aktion, Versuche). */
    private static final java.util.Map<java.util.UUID, String[]> NACHHOLEN = new java.util.concurrent.ConcurrentHashMap<>();

    public static void registrieren() {
        Netz.bearbeiter("handy", 0, (spieler, pos, wert) -> ausfuehren(spieler, wert));
        // Stand James weit weg (z. B. nach einem Flug auf dem Landeplatz, nicht geladen), holt butler() ihn erst herbei.
        // Der Auftrag wartet dann, bis er da ist (jede Sekunde nachsehen, höchstens 10 Sekunden) – vorher ging er verloren.
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 != 0 || NACHHOLEN.isEmpty()) {
                return;
            }
            for (var it = NACHHOLEN.entrySet().iterator(); it.hasNext(); ) {
                var e = it.next();
                ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
                int versuche = Integer.parseInt(e.getValue()[1]) + 1;
                if (p == null || versuche > 10) {
                    de.moinmornhart.stinkendreich.StinkendReich.LOGGER.info("Handy: „{}“ verworfen, James kam nicht", e.getValue()[0]);
                    it.remove();
                } else if (ButlerVerwaltung.vonSpieler(p) != null) {
                    it.remove();
                    ausfuehren(p, e.getValue()[0]);
                } else {
                    e.setValue(new String[]{e.getValue()[0], String.valueOf(versuche)});
                }
            }
        });
        // Dasselbe als Befehl, für Spieler ohne Mod (die Knöpfe des Handy-Dialogs, siehe server/HandyDialog)
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((befehle, registry, umgebung) -> befehle.register(
                net.minecraft.commands.Commands.literal("handy").then(net.minecraft.commands.Commands
                        .argument("aktion", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                        .executes(k -> {
                            ausfuehren(k.getSource().getPlayerOrException(),
                                    com.mojang.brigadier.arguments.StringArgumentType.getString(k, "aktion"));
                            return 1;
                        }))));
    }

    private static boolean hatHandy(ServerPlayer p) {
        return p.getMainHandItem().is(ModItems.HANDY) || p.getOffhandItem().is(ModItems.HANDY);
    }

    static void ausfuehren(ServerPlayer p, String wert) {
        try {
            aktion(p, wert);
        } catch (RuntimeException e) {
            // Sonst verschluckt der Befehl den Fehler, und James tut einfach nichts
            de.moinmornhart.stinkendreich.StinkendReich.LOGGER.error("Handy-Aktion „{}“ fehlgeschlagen", wert, e);
        }
    }

    private static void aktion(ServerPlayer p, String wert) {
        if (!hatHandy(p)) {
            return;
        }
        String[] teile = wert.split("\\|", 2);
        String was = teile[0], angabe = teile.length > 1 ? teile[1] : "";
        if (!was.equals("orte") && !was.equals("gaeste")) {
            p.level().playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.6F, 1.6F);
        }
        switch (was) {
            case "seite" -> {
                //? if >=1.21.6
                de.moinmornhart.stinkendreich.server.HandyDialog.oeffnen(p, angabe);
                return;
            }
            case "mitarbeiter" -> {
                ItemStack kiste = new ItemStack(ModItems.MITARBEITERKISTE);
                if (!p.getInventory().add(kiste)) {
                    net.minecraft.world.level.block.Block.popResource(p.level(), p.blockPosition(), kiste);
                }
                Kompat.nachricht(p, Component.translatable("stinkendreich.handy.mitarbeiter.da").withStyle(ChatFormatting.GOLD), false);
                return;
            }
            case "bruecke" -> {
                de.moinmornhart.stinkendreich.mechanik.Zugbruecke.umschalten(p);
                return;
            }
            case "smart" -> {
                de.moinmornhart.stinkendreich.mechanik.SmartHome.befehl(p, angabe);
                Netz.melden(p, "smart", de.moinmornhart.stinkendreich.mechanik.SmartHome.status(p.level().getServer(), p));
                return;
            }
            case "gaeste", "gast" -> {
                // Gästeliste der eigenen Villa: „gast|rein|Name“, „gast|raus|Name“, „gaeste“ = nur die Liste
                String[] teil = angabe.split("\\|", 2);
                if (was.equals("gast") && teil.length == 2) {
                    var antwort = ButlerBefehle.gast(p, teil[1].trim(), teil[0].equals("rein") ? 1 : 0);
                    Kompat.nachricht(p, antwort.text().copy().withStyle(antwort.fehler() ? ChatFormatting.RED : ChatFormatting.GOLD), true);
                }
                var z = de.moinmornhart.stinkendreich.mechanik.VillaZustand.vonSpieler(p.level().getServer(), p);
                Netz.melden(p, "gaeste", String.join("\n", z.gaeste()));
                // Ohne Mod: die Gäste-Seite gleich wieder öffnen
                //? if >=1.21.6 {
                if (was.equals("gast") && !de.moinmornhart.stinkendreich.netz.ModErkennung.hatMod(p)) {
                    de.moinmornhart.stinkendreich.server.HandyDialog.oeffnen(p, "gaeste");
                }
                //?}
                return;
            }
            case "bodyguards" -> {
                if (angabe.equals("heim")) {
                    de.moinmornhart.stinkendreich.bodyguard.Bodyguards.entlassen(p);
                } else {
                    de.moinmornhart.stinkendreich.bodyguard.Bodyguards.rufen(p);
                }
                return;
            }
            default -> {
            }
        }
        ButlerEntity b = butler(p);
        de.moinmornhart.stinkendreich.StinkendReich.LOGGER.info("Handy: „{}“ von {}, James {}", wert, p.getScoreboardName(),
                b == null ? "wird erst geholt" : "bei " + b.blockPosition().toShortString() + (b.getVehicle() != null ? " (sitzt in " + b.getVehicle().getType().toShortString() + ")" : ""));
        if (b == null) {
            NACHHOLEN.put(p.getUUID(), new String[]{wert, "0"});
            return;
        }
        switch (was) {
            case "anruf" -> {
                if (angabe.equals("heli")) {
                    HeliBereitstellung.bereitstellen(p, b);
                    b.neueAuftraege(List.of(new FlugAuftrag(null, "")));
                } else {
                    b.neueAuftraege(List.of(new AnrufAuftrag(""), new FahrAuftrag(null, "")));
                }
            }
            case "fahr", "flieg" -> {
                ButlerBefehle.FahrZiel z = ButlerBefehle.fahrZiel(b, p, angabe, false);
                if (z.antwort().fehler()) {
                    b.sagen(z.antwort().text());
                    return;
                }
                // Hubschrauber gewählt oder du sitzt schon drin: James holt dich damit ab und fliegt los, sobald du
                // eingestiegen bist (Philip: „Ich steige ein und sofort fliegt er mich“)
                boolean imHeli = p.getVehicle() instanceof de.moinmornhart.stinkendreich.auto.AutoEntity h && h.art.fliegt();
                if (was.equals("flieg") || imHeli || ButlerBefehle.besserFliegen(p, z.punkt())) {
                    if (!imHeli) {
                        HeliBereitstellung.bereitstellen(p, b);
                    }
                    b.neueAuftraege(List.of(new FlugAuftrag(z.punkt(), z.name())));
                    b.sagen(net.minecraft.network.chat.Component.translatable("stinkendreich.butler.flug.ziel", z.name()));
                    return;
                }
                // Steht der Wagen schon bereit (du sitzt drin oder James wartet am Steuer), nimmt James genau diesen –
                // sonst ruft er erst die Limo
                boolean wagenDa = p.getVehicle() instanceof de.moinmornhart.stinkendreich.auto.AutoEntity a && !a.art.fliegt()
                        || b.getVehicle() instanceof de.moinmornhart.stinkendreich.auto.AutoEntity w && !w.art.fliegt() && w.distanceTo(p) < 64;
                b.neueAuftraege(wagenDa ? List.of(new FahrAuftrag(z.punkt(), z.name()))
                        : List.of(new AnrufAuftrag(""), new FahrAuftrag(z.punkt(), z.name())));
                b.sagen(z.antwort().text());
            }
            case "liefer" -> {
                if (Lieferservice.ANGEBOT.contains(angabe)) {
                    b.neueAuftraege(List.of(new AnrufAuftrag(angabe), new FahrAuftrag(null, "")));
                }
            }
            case "auftrag" -> ButlerBefehle.verarbeiten(p, angabe, true);
            case "zuruecksetzen" -> de.moinmornhart.stinkendreich.butler.ButlerVerwaltung.zuruecksetzen(p);
            case "orte" -> orteSenden(p, b);
            case "ort" -> {
                String[] teil = angabe.split("\\|", 2);
                String name = teil.length > 1 ? teil[1].trim() : "";
                if (name.length() > 32) {
                    name = name.substring(0, 32).trim();
                }
                if (teil[0].equals("merken") && !de.moinmornhart.stinkendreich.butler.Sprache.normalisieren(name).isEmpty()) {
                    b.ortMerken(name, p.blockPosition());
                    b.sagen(Component.translatable("stinkendreich.butler.ort.gemerkt", de.moinmornhart.stinkendreich.butler.Sprache.normalisieren(name)));
                } else if (teil[0].equals("vergessen")) {
                    b.ortVergessen(name);
                }
                orteSenden(p, b);
                // Ohne Mod: die Navi-Seite mit dem neuen Ort gleich wieder öffnen
                //? if >=1.21.6 {
                if (!de.moinmornhart.stinkendreich.netz.ModErkennung.hatMod(p)) {
                    de.moinmornhart.stinkendreich.server.HandyDialog.oeffnen(p, "navi");
                }
                //?}
            }
            default -> {
            }
        }
    }

    /**
     * Die Orte, die James kennt, an das Handy (eine Zeile je Ort, alphabetisch). Davor die Villen der anderen Spieler
     * als „#villa:Name“ (nicht löschbar).
     */
    private static void orteSenden(ServerPlayer p, ButlerEntity b) {
        List<String> zeilen = new java.util.ArrayList<>();
        for (String herr : andereVillen(p)) {
            zeilen.add("#villa:" + herr);
        }
        zeilen.addAll(new java.util.TreeSet<>(b.orte().keySet()));
        Netz.melden(p, "orte", String.join("\n", zeilen));
    }

    /** Namen der Herren aller anderen Villen auf dem Server (ohne die eigene). */
    public static List<String> andereVillen(ServerPlayer p) {
        List<String> namen = new java.util.ArrayList<>();
        var eigene = de.moinmornhart.stinkendreich.mechanik.VillaZustand.vonSpieler(p.level().getServer(), p);
        for (var v : de.moinmornhart.stinkendreich.villa.VillaPlatzierung.alle()) {
            String herr = de.moinmornhart.stinkendreich.villa.VillaPlatzierung.herrName(p.level().getServer(), v);
            var zustand = de.moinmornhart.stinkendreich.mechanik.VillaZustand.get(p.level().getServer(), v.nr());
            if (!herr.isEmpty() && zustand != eigene && !zustand.bewohner().containsKey(p.getUUID())) {
                namen.add(herr);
            }
        }
        return namen;
    }

    /** Der Butler des Spielers; hat er noch keinen, wird er jetzt eingestellt. */
    private static ButlerEntity butler(ServerPlayer p) {
        ButlerEntity b = ButlerVerwaltung.vonSpieler(p);
        if (b == null) {
            ButlerVerwaltung.rufen(p);
            b = ButlerVerwaltung.vonSpieler(p);
        }
        return b;
    }
}
