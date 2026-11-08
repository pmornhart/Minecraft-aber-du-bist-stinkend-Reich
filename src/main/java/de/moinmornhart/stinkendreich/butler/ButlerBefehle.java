package de.moinmornhart.stinkendreich.butler;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.Konfig;
import de.moinmornhart.stinkendreich.butler.auftrag.FahrAuftrag;
import de.moinmornhart.stinkendreich.butler.auftrag.FlugAuftrag;
import de.moinmornhart.stinkendreich.butler.auftrag.GehAuftrag;
import de.moinmornhart.stinkendreich.butler.auftrag.SortierAuftrag;
import de.moinmornhart.stinkendreich.netz.Netz;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Alle Wege, dem Butler etwas aufzutragen:
 * <ul>
 *   <li>{@code /butler} – Status und Hilfe</li>
 *   <li>{@code /butler rufen} – Butler herbeiholen (oder den ersten eigenen einstellen)</li>
 *   <li>{@code /butler truhe} – die angeschaute Truhe wird seine Lieferkiste</li>
 *   <li>{@code /butler ort merken <Name>}, {@code /butler ort vergessen <Name>}, {@code /butler orte} – Orte für den Chauffeur</li>
 *   <li>Chauffeur: „Butler, fahr mich zum Strand“, „fahr mich dahin“ (wohin man schaut), „fahr mich nach Hause“,
 *       „hol den Wagen“; Pilot genauso mit „flieg mich …“ und „hol den Hubschrauber“</li>
 *   <li>{@code /butler <Auftrag>} – z. B. {@code /butler hol 32 eisen}</li>
 *   <li>Chat: „Butler, hol mir 32 Eisen“ (oder mit seinem Namen anreden)</li>
 *   <li>Rechtsklick auf den Butler: Menü mit Knöpfen und Textfeld</li>
 * </ul>
 */
public final class ButlerBefehle {
    private ButlerBefehle() {
    }

    public static void registrieren() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, umgebung) -> dispatcher.register(
                Commands.literal("butler")
                        .executes(k -> status(k))
                        .then(Commands.literal("rufen").executes(k -> {
                            ButlerVerwaltung.rufen(k.getSource().getPlayerOrException());
                            return 1;
                        }))
                        .then(Commands.literal("truhe").executes(ButlerBefehle::truheFestlegen))
                        // Alles auf Anfang: Aufträge weg, jeder James weg, ein frischer James in der Halle
                        // Gästeliste: /butler gast <name> | /butler gast weg <name> | /butler gaeste
                        .then(Commands.literal("gaeste").executes(k -> gastBefehl(k, null, -1)))
                        .then(Commands.literal("gast")
                                .then(Commands.literal("weg").then(Commands.argument("name", StringArgumentType.word())
                                        .executes(k -> gastBefehl(k, StringArgumentType.getString(k, "name"), 0))))
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(k -> gastBefehl(k, StringArgumentType.getString(k, "name"), 1))))
                        .then(Commands.literal("zuruecksetzen").executes(k -> {
                            ButlerVerwaltung.zuruecksetzen(k.getSource().getPlayerOrException());
                            return 1;
                        }))
                        // Knöpfe des Butler-Dialogs für Spieler ohne Mod (siehe server/ButlerDialog)
                        .then(Commands.literal("knopf").then(Commands.argument("nummer", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                .then(Commands.argument("befehl", StringArgumentType.greedyString()).executes(k -> {
                                    menueKnopf(k.getSource().getPlayerOrException(),
                                            com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(k, "nummer"),
                                            StringArgumentType.getString(k, "befehl"));
                                    return 1;
                                }))))
                        .then(Commands.literal("strassen").then(Commands.literal("pruefen").requires(de.moinmornhart.stinkendreich.Kompat.istOperator()).executes(k -> {
                            var zeilen = de.moinmornhart.stinkendreich.strasse.Strassennetz.get(k.getSource().getServer()).pruefen();
                            k.getSource().sendSuccess(() -> Component.literal("STRASSEN " + String.join(" | ", zeilen)), false);
                            return 1;
                        })).then(Commands.literal("neu").requires(de.moinmornhart.stinkendreich.Kompat.istOperator()).executes(k -> {
                            de.moinmornhart.stinkendreich.strasse.Strassennetz.get(k.getSource().getServer()).neuBauen();
                            k.getSource().sendSuccess(() -> Component.translatable("stinkendreich.strasse.neu"), false);
                            return 1;
                        })).executes(k -> {
                            var netz = de.moinmornhart.stinkendreich.strasse.Strassennetz.get(k.getSource().getServer());
                            StringBuilder t = new StringBuilder();
                            netz.uebersicht().forEach((name, laenge) -> t.append(t.length() > 0 ? ", " : "").append(name)
                                    .append(" (").append(laenge).append(")"));
                            var bau = netz.aktuellerBau();
                            k.getSource().sendSuccess(() -> Component.translatable("stinkendreich.strasse.liste",
                                    t.length() == 0 ? "–" : t.toString(), bau == null ? "–" : bau.name() + " " + bau.prozent() + " %"), false);
                            return 1;
                        }))
                        // Für Tests und Neugierige: zu einem Punkt einer Straße springen („/butler strassenpunkt 0 200“)
                        .then(Commands.literal("strassenpunkt").then(Commands.argument("strasse", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0))
                                .then(Commands.argument("punkt", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0)).executes(k -> {
                                    ServerPlayer p = k.getSource().getPlayerOrException();
                                    var netz = de.moinmornhart.stinkendreich.strasse.Strassennetz.get(k.getSource().getServer());
                                    // Zu große Nummern meinen die zuletzt gebaute Straße (Tests: „99 9999“ = ihr Ende)
                                    if (netz.strassen().isEmpty()) {
                                        return 0;
                                    }
                                    int nr = Math.min(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(k, "strasse"), netz.strassen().size() - 1);
                                    var punkte = netz.strassen().get(nr).punkte();
                                    BlockPos q = punkte.get(Math.min(punkte.size() - 1, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(k, "punkt")));
                                    p.teleportTo(q.getX() + 0.5, q.getY() + 1, q.getZ() + 0.5);
                                    return 1;
                                }))))
                        // Testhilfe: Knöpfe des Automenüs für den nächsten Wagen (menue, schluessel, zu, auf, kofferraum, heim)
                        .then(Commands.literal("auto").then(Commands.argument("aktion", StringArgumentType.word()).executes(k -> {
                            ServerPlayer p = k.getSource().getPlayerOrException();
                            var wagen = p.level().getEntitiesOfClass(de.moinmornhart.stinkendreich.auto.AutoEntity.class, p.getBoundingBox().inflate(24),
                                            a -> !a.art.fliegt()).stream()
                                    .min(java.util.Comparator.comparingDouble(a -> a.distanceToSqr(p))).orElse(null);
                            if (wagen == null) {
                                return 0;
                            }
                            switch (StringArgumentType.getString(k, "aktion")) {
                                case "menue" -> de.moinmornhart.stinkendreich.auto.AutoMenue.oeffnen(p, wagen);
                                case "schluessel" -> wagen.schluesselGeben(p);
                                case "zu" -> wagen.verschliessen(true, p);
                                case "auf" -> wagen.verschliessen(false, p);
                                case "kofferraum" -> wagen.kofferraumOeffnen(p);
                                case "heim" -> wagen.heimBringen(p);
                                default -> {
                                    return 0;
                                }
                            }
                            de.moinmornhart.stinkendreich.StinkendReich.LOGGER.info("Autotest: Auto {} {} bei {} verschlossen={}",
                                    StringArgumentType.getString(k, "aktion"), wagen.art.name, wagen.blockPosition().toShortString(), wagen.verschlossen());
                            return 1;
                        })))
                        // Treppen prüfen: Stufe für Stufe frei, trocken, bis ins Ziel? (Ergebnis im Chat und im Protokoll)
                        .then(Commands.literal("treppenpruefen").executes(k -> {
                            for (String z : de.moinmornhart.stinkendreich.strasse.Strassennetz.get(k.getSource().getServer()).treppenPruefen()) {
                                de.moinmornhart.stinkendreich.StinkendReich.LOGGER.info("Treppe {}", z);
                                k.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(z), false);
                            }
                            return 1;
                        }))
                        // Testhilfe: auf eine Treppe stellen („/butler treppenblick mine 0“ = oben am Tor, 1 = unten)
                        .then(Commands.literal("treppenblick").then(Commands.argument("ziel", StringArgumentType.word())
                                .then(Commands.argument("anteil", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0, 1)).executes(k -> {
                            ServerPlayer p = k.getSource().getPlayerOrException();
                            double[] t = de.moinmornhart.stinkendreich.strasse.Strassennetz.get(k.getSource().getServer())
                                    .treppenBlick(StringArgumentType.getString(k, "ziel"), com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(k, "anteil"));
                            if (t == null) {
                                return 0;
                            }
                            de.moinmornhart.stinkendreich.Kompat.befehl(k.getSource().getServer(), String.format(java.util.Locale.ROOT,
                                    "tp %s %.2f %.2f %.2f facing %.2f %.2f %.2f", p.getScoreboardName(), t[0], t[1], t[2], t[3], t[4], t[5]));
                            return 1;
                        }))))
                        // Testhilfe: vor einen Wegweiser stellen („/butler wegweiser 0“ = der große an der Zufahrt)
                        .then(Commands.literal("wegweiser").then(Commands.argument("nr", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0)).executes(k -> {
                            ServerPlayer p = k.getSource().getPlayerOrException();
                            double[] b = de.moinmornhart.stinkendreich.strasse.Strassennetz.get(k.getSource().getServer())
                                    .wegweiserBlick(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(k, "nr"));
                            if (b == null) {
                                return 0;
                            }
                            de.moinmornhart.stinkendreich.Kompat.befehl(k.getSource().getServer(), String.format(java.util.Locale.ROOT,
                                    "tp %s %.2f %.2f %.2f facing %.2f %.2f %.2f", p.getScoreboardName(), b[0], b[1], b[2], b[3], b[4], b[5]));
                            return 1;
                        })))
                        .then(Commands.literal("strasse").then(Commands.argument("ziel", StringArgumentType.greedyString()).executes(k -> {
                            ServerPlayer p = k.getSource().getPlayerOrException();
                            ausfuehren(ButlerVerwaltung.vonSpieler(p), p, new Sprache.Befehl(Sprache.Art.STRASSE,
                                    Sprache.normalisieren(StringArgumentType.getString(k, "ziel")), -1, false));
                            return 1;
                        })))
                        .then(Commands.literal("orte").executes(k -> {
                            ServerPlayer p = k.getSource().getPlayerOrException();
                            ausfuehren(ButlerVerwaltung.vonSpieler(p), p, Sprache.Befehl.von(Sprache.Art.ORTE));
                            return 1;
                        }))
                        .then(Commands.literal("ort")
                                .then(Commands.literal("merken").then(Commands.argument("name", StringArgumentType.greedyString()).executes(k -> {
                                    ServerPlayer p = k.getSource().getPlayerOrException();
                                    ausfuehren(ButlerVerwaltung.vonSpieler(p), p, new Sprache.Befehl(Sprache.Art.ORT_MERKEN,
                                            StringArgumentType.getString(k, "name"), -1, false));
                                    return 1;
                                })))
                                .then(Commands.literal("vergessen").then(Commands.argument("name", StringArgumentType.greedyString()).executes(k -> {
                                    ServerPlayer p = k.getSource().getPlayerOrException();
                                    ButlerEntity b = ButlerVerwaltung.vonSpieler(p);
                                    String name = StringArgumentType.getString(k, "name");
                                    if (b != null) {
                                        b.sagen(Component.translatable(b.ortVergessen(name) ? "stinkendreich.butler.ort.vergessen"
                                                : "stinkendreich.butler.ort.unbekannt", name, ortListe(b)));
                                    }
                                    return 1;
                                }))))
                        .then(Commands.argument("auftrag", StringArgumentType.greedyString()).executes(k -> {
                            verarbeiten(k.getSource().getPlayerOrException(), StringArgumentType.getString(k, "auftrag"), false);
                            return 1;
                        }))));

        // Chat: Nachrichten, die mit „Butler“ oder seinem Namen beginnen
        ServerMessageEvents.CHAT_MESSAGE.register((nachricht, absender, typ) -> {
            String text = nachricht.signedContent();
            String rest = anrede(text);
            if (rest != null) {
                absender.level().getServer().execute(() -> verarbeiten(absender, rest, false));
            }
        });

        // Butler-Menü: „<Entity-Nummer>|<!Schnellbefehl oder freier Text>“
        Netz.bearbeiter("butler", 12, (spieler, pos, wert) -> {
            int trenner = wert.indexOf('|');
            if (trenner >= 0) {
                menueKnopf(spieler, parseInt(wert.substring(0, trenner)), wert.substring(trenner + 1));
            }
        });
    }

    /**
     * Ein Knopf aus dem Butler-Menü (Bildschirm der Mod oder Dialog für Spieler ohne Mod, dort über den Befehl
     * {@code /butler knopf <Nummer> <Befehl>}): „!…“ ist ein Schnellbefehl, alles andere ein freier Auftrag.
     */
    static void menueKnopf(ServerPlayer spieler, int butlerId, String befehl) {
        Entity e = spieler.level().getEntity(butlerId);
        if (!(e instanceof ButlerEntity b)) {
            return;
        }
        if (b.besitzer() == null) {
            ButlerVerwaltung.besitzerSetzen(b, spieler);
        }
        if (!b.istBesitzer(spieler)) {
            return;
        }
        if (befehl.equals("!inventar")) {
            b.inventarZeigen(spieler);
        } else if (befehl.startsWith("!")) {
            ausfuehren(b, spieler, Sprache.verstehen(befehl.substring(1)));
        } else if (!befehl.isBlank()) {
            verarbeiten(spieler, befehl, true);
        }
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Für den Autotest: wie {@link #anrede}, öffentlich. */
    public static String anredeTest(String text) {
        return anrede(text);
    }

    /** Ist die Nachricht an den Butler gerichtet? Dann der Text ohne Anrede, sonst null. */
    static String anrede(String text) {
        String t = text.trim();
        for (String wort : List.of(Konfig.get().butlerAnrede, Konfig.get().butlerName)) {
            if (wort == null || wort.isBlank()) {
                continue;
            }
            String w = wort.toLowerCase(Locale.ROOT);
            String klein = t.toLowerCase(Locale.ROOT);
            if (klein.startsWith(w) && (klein.length() == w.length() || " ,:!".indexOf(klein.charAt(w.length())) >= 0)) {
                return t.substring(w.length()).replaceFirst("^[\\s,:!]+", "");
            }
        }
        return null;
    }

    /** Freier Text an den Butler: mit Claude (falls eingerichtet), sonst über die einfache Sprachsteuerung. */
    public static void verarbeiten(ServerPlayer spieler, String text, boolean ausMenue) {
        ButlerEntity b = ButlerVerwaltung.vonSpieler(spieler);
        if (b == null) {
            // Nicht geladen (weit weg) oder noch keiner: der Auftrag wartet, bis er da ist, statt verloren zu gehen
            ButlerVerwaltung.mitButler(spieler, bb -> verarbeiten(spieler, text, ausMenue));
            return;
        }
        // Erst die eingebaute Sprachsteuerung (sofort); Claude nur, wenn sie den Satz nicht versteht – sonst wartete
        // jeder einfache Auftrag mehrere Sekunden auf die Antwort aus dem Netz
        Sprache.Befehl verstanden = Sprache.verstehen(text);
        if (ButlerKI.aktiv() && verstanden.art() == Sprache.Art.UNBEKANNT) {
            ButlerKI.anfragen(b, spieler, text);
            return;
        }
        ausfuehren(b, spieler, verstanden);
    }

    /** Antwort des Butlers auf einen Auftrag; {@code fehler}, wenn er ihn nicht ausführen kann. */
    public record Antwort(Component text, boolean fehler) {
        static Antwort ok(String schluessel, Object... werte) {
            return new Antwort(Component.translatable(schluessel, werte), false);
        }
    }

    /** Führt einen verstandenen Auftrag aus und lässt den Butler antworten. */
    public static void ausfuehren(ButlerEntity b, ServerPlayer p, Sprache.Befehl bf) {
        if (b == null) {
            ButlerVerwaltung.mitButler(p, bb -> ausfuehren(bb, p, bf));
            return;
        }
        b.sagen(befolgen(b, p, bf).text());
    }

    /** Gespeicherte Orte als Text, z. B. „strand, bar, mine“ (oder „keine“). */
    static String ortListe(ButlerEntity b) {
        return b.orte().isEmpty() ? "–" : String.join(", ", b.orte().keySet());
    }

    /** Ziel einer Fahrt oder eines Flugs: Punkt (null = nur vorfahren), Name und Antwort des Butlers. */
    public record FahrZiel(Vec3 punkt, String name, Antwort antwort) {
    }

    /**
     * Lohnt sich der Hubschrauber statt der Limo? Ja, wenn man weit (über 80 Blöcke) vom Straßennetz entfernt steht und das
     * Ziel mehr als 150 Blöcke weg ist: Dorthin führt keine Straße, die Limo käme nur querfeldein.
     */
    public static boolean besserFliegen(ServerPlayer p, Vec3 ziel) {
        if (ziel == null || p.getVehicle() instanceof de.moinmornhart.stinkendreich.auto.AutoEntity) {
            return false;
        }
        double weit = Math.hypot(ziel.x - p.getX(), ziel.z - p.getZ());
        return weit > 150 && !de.moinmornhart.stinkendreich.strasse.Strassennetz.get(p.level().getServer()).amNetz(p.position());
    }

    /** Fahr- oder Flugbefehl: Ziel bestimmen und den Butler losschicken (als Chauffeur oder Pilot). */
    private static Antwort fahren(ButlerEntity b, ServerPlayer p, String ziel, boolean fliegen) {
        // Weit weg „schläft“ der Butler (außerhalb der Simulationsweite): dann springt er zum Herrn
        if (b.level() != p.level() || b.distanceTo(p) > 32) {
            b.teleportiereNahe(p.blockPosition());
        }
        FahrZiel z = fahrZiel(b, p, ziel, fliegen);
        if (z.antwort().fehler()) {
            return z.antwort();
        }
        if (!fliegen && besserFliegen(p, z.punkt())) {
            // Weit weg von jeder Straße (Philip: „wenn ich heimfliegen möchte, soll er mit dem Heli kommen“)
            de.moinmornhart.stinkendreich.handy.Handy.heliHolen(p, b);
            b.neueAuftraege(List.of(new FlugAuftrag(z.punkt(), z.name())));
            return Antwort.ok("stinkendreich.butler.flug.statt_limo", z.name());
        }
        b.neueAuftraege(List.of(fliegen ? new FlugAuftrag(z.punkt(), z.name()) : new FahrAuftrag(z.punkt(), z.name())));
        return z.antwort();
    }

    /** Löst ein gesprochenes Ziel auf (Villa, Blickziel, festes Ziel wie „Dorf“ oder gemerkter Ort). */
    public static FahrZiel fahrZiel(ButlerEntity b, ServerPlayer p, String ziel, boolean fliegen) {
        String art = fliegen ? "flug" : "fahren";
        Vec3 punkt;
        String name;
        Antwort antwort;
        if (Sprache.ZIEL_ABHOLEN.equals(ziel)) {
            punkt = null;
            name = "";
            antwort = Antwort.ok("stinkendreich.butler." + art + ".holt");
        } else if (Sprache.ZIEL_VILLA.equals(ziel)) {
            // Nach Hause = in James' eigene Villa (bei Nachbarn deren Villa, nicht die am Spawn)
            var eigene = de.moinmornhart.stinkendreich.villa.VillaPlatzierung.bei(b.heimat());
            if (eigene != null && eigene.nr() > 0) {
                punkt = fliegen ? Vec3.atBottomCenterOf(eigene.landeplatz()) : de.moinmornhart.stinkendreich.strasse.Strassennetz.villaZiel((net.minecraft.server.level.ServerLevel) p.level(), eigene);
                name = fliegen ? Component.translatable("stinkendreich.butler.fahren.villa").getString()
                        : de.moinmornhart.stinkendreich.strasse.Strassennetz.villaName(eigene);
            } else {
                var villa = de.moinmornhart.stinkendreich.villa.VillaPlatzierung.villa();
                // Der Hubschrauber landet auf dem Landeplatz, das Auto hält vor der Haustür (danach fährt James in die Garage)
                punkt = villa.map(v -> fliegen ? Vec3.atBottomCenterOf(v.landeplatz())
                        : de.moinmornhart.stinkendreich.butler.auftrag.FahrAuftrag.vorDerTuer(v)).orElse(Vec3.atBottomCenterOf(b.heimat()));
                name = Component.translatable("stinkendreich.butler.fahren.villa").getString();
            }
            antwort = Antwort.ok("stinkendreich.butler." + art + ".heim");
        } else if (b.ort(ziel) == null && de.moinmornhart.stinkendreich.villa.VillaPlatzierung.mitHerrnImText(p.level().getServer(), ziel) != null) {
            // Die Villa eines anderen Spielers („fahr zur Villa von Max“); Spawn-Villa wie „nach Hause“ vor das Haus
            var v = de.moinmornhart.stinkendreich.villa.VillaPlatzierung.mitHerrnImText(p.level().getServer(), ziel);
            String herr = de.moinmornhart.stinkendreich.villa.VillaPlatzierung.herrName(p.level().getServer(), v);
            if (fliegen) {
                punkt = Vec3.atBottomCenterOf(v.landeplatz());
                name = Component.translatable("stinkendreich.strasse.ziel.villa", herr).getString();
            } else if (v.nr() == 0) {
                punkt = de.moinmornhart.stinkendreich.butler.auftrag.FahrAuftrag.vorDerTuer(v);
                name = Component.translatable("stinkendreich.butler.fahren.villa").getString();
            } else {
                punkt = de.moinmornhart.stinkendreich.strasse.Strassennetz.villaZiel((net.minecraft.server.level.ServerLevel) p.level(), v);
                name = de.moinmornhart.stinkendreich.strasse.Strassennetz.villaName(v);
            }
            antwort = Antwort.ok("stinkendreich.butler." + art + ".ort", Component.translatable("stinkendreich.strasse.ziel.villa", herr).getString());
        } else if (Sprache.ZIEL_BLICK.equals(ziel)) {
            HitResult blick = p.pick(300.0, 1.0F, false);
            if (!(blick instanceof BlockHitResult bt) || blick.getType() != HitResult.Type.BLOCK) {
                return new FahrZiel(null, "", new Antwort(Component.translatable("stinkendreich.butler.fahren.nichtgesehen"), true));
            }
            BlockPos pos = bt.getBlockPos().above();
            punkt = Vec3.atBottomCenterOf(pos);
            name = pos.getX() + " " + pos.getY() + " " + pos.getZ();
            antwort = Antwort.ok("stinkendreich.butler." + art + ".blick", pos.getX(), pos.getY(), pos.getZ());
        } else if (b.ort(ziel) == null && de.moinmornhart.stinkendreich.strasse.StrassenZiele.fester(ziel) != null) {
            // Feste Ziele des Straßennetzes: Dorf, Endportal, Netherportal, Mine
            var gefunden = festesZiel(p, ziel);
            if (gefunden == null) {
                return new FahrZiel(null, "", new Antwort(Component.translatable("stinkendreich.strasse.nicht_gefunden", ziel), true));
            }
            punkt = Vec3.atBottomCenterOf(gefunden.pos());
            name = Component.translatable("stinkendreich.strasse.ziel." + gefunden.name()).getString();
            antwort = Antwort.ok("stinkendreich.butler." + art + ".ort", name);
        } else {
            BlockPos ort = b.ort(ziel);
            if (ort == null) {
                return new FahrZiel(null, "", new Antwort(Component.translatable("stinkendreich.butler.ort.unbekannt", ziel, ortListe(b)), true));
            }
            punkt = Vec3.atBottomCenterOf(ort);
            name = ziel;
            antwort = Antwort.ok("stinkendreich.butler." + art + ".ort", ziel);
        }
        return new FahrZiel(punkt, name, antwort);
    }

    /** Sucht ein festes Ziel (Dorf, Endportal, Netherportal, Mine) von der Villa aus, sonst vom Herrn aus. */
    static de.moinmornhart.stinkendreich.strasse.StrassenZiele.Ziel festesZiel(ServerPlayer p, String ziel) {
        String fest = de.moinmornhart.stinkendreich.strasse.StrassenZiele.fester(ziel);
        if (fest == null || !(p.level() instanceof net.minecraft.server.level.ServerLevel welt)) {
            return null;
        }
        BlockPos von = de.moinmornhart.stinkendreich.villa.VillaPlatzierung.villa().map(v -> v.spawn()).orElse(p.blockPosition());
        return de.moinmornhart.stinkendreich.strasse.StrassenZiele.finden(welt, fest, von);
    }

    /** Straße zu einem Ziel bauen lassen (ohne hinzufahren). */
    /**
     * Gästeliste der eigenen Villa: Gäste dürfen ohne Zugangskarte herein und gelten nicht als Eindringlinge (keine
     * Laser, Abwehr, Fallen). Eintragen und streichen dürfen nur Bewohner. {@code was}: 1 = eintragen, 0 = streichen,
     * sonst die Liste nennen.
     */
    public static Antwort gast(ServerPlayer p, String name, int was) {
        var z = de.moinmornhart.stinkendreich.mechanik.VillaZustand.vonSpieler(p.level().getServer(), p);
        if (was < 0 || name == null || name.isBlank()) {
            return z.gaeste().isEmpty() ? Antwort.ok("stinkendreich.butler.gast.leer")
                    : Antwort.ok("stinkendreich.butler.gast.liste", String.join(", ", z.gaeste()));
        }
        if (!z.istBewohner(p)) {
            return new Antwort(Component.translatable("stinkendreich.butler.gast.nur_bewohner"), true);
        }
        boolean geaendert = z.gastSetzen(name, was == 1);
        if (was == 1) {
            return Antwort.ok(geaendert ? "stinkendreich.butler.gast.neu" : "stinkendreich.butler.gast.schon", name);
        }
        return Antwort.ok(geaendert ? "stinkendreich.butler.gast.weg" : "stinkendreich.butler.gast.keiner", name);
    }

    private static Antwort strasseBauen(ButlerEntity b, ServerPlayer p, String ziel) {
        Vec3 punkt;
        String name;
        var gefunden = festesZiel(p, ziel);
        if (gefunden != null) {
            punkt = Vec3.atBottomCenterOf(gefunden.pos());
            name = Component.translatable("stinkendreich.strasse.ziel." + gefunden.name()).getString();
        } else if (b.ort(ziel) != null) {
            punkt = Vec3.atBottomCenterOf(b.ort(ziel));
            name = ziel;
        } else if (de.moinmornhart.stinkendreich.strasse.StrassenZiele.fester(ziel) != null) {
            return new Antwort(Component.translatable("stinkendreich.strasse.nicht_gefunden", ziel), true);
        } else {
            return new Antwort(Component.translatable("stinkendreich.butler.ort.unbekannt", ziel, ortListe(b)), true);
        }
        var netz = de.moinmornhart.stinkendreich.strasse.Strassennetz.get(p.level().getServer());
        if (netz.strasseZu(name, punkt) >= 0) {
            return Antwort.ok("stinkendreich.strasse.gibtes", name);
        }
        var bau = netz.bauen(b.welt(), punkt, name);
        if (bau == null) {
            return new Antwort(Component.translatable("stinkendreich.strasse.keinevilla"), true);
        }
        return Antwort.ok("stinkendreich.strasse.wirdgebaut", bau.name());
    }

    /** Führt einen Auftrag aus und gibt die Antwort zurück (ohne sie auszusprechen). */
    public static Antwort befolgen(ButlerEntity b, ServerPlayer p, Sprache.Befehl bf) {
        switch (bf.art()) {
            case HOLEN -> {
                int menge = bf.menge() > 0 ? bf.menge() : Sprache.standardMenge(bf.quelle());
                Planer.Plan plan = Planer.bestellung(b, bf.quelle(), menge, bf.inTruhe());
                if (!plan.ok()) {
                    return new Antwort(plan.fehler(), true);
                }
                b.neueAuftraege(plan.schritte());
                return Antwort.ok(bf.inTruhe() ? "stinkendreich.butler.sehrwohl.truhe" : "stinkendreich.butler.sehrwohl",
                        Math.min(menge, Planer.MAX_MENGE), Quellen.get(bf.quelle()).anzeigeStapel().getHoverName());
            }
            case KOCHEN -> {
                Planer.Plan plan = Planer.kochen(b, bf.menge() > 0 ? bf.menge() : 8, bf.inTruhe());
                if (!plan.ok()) {
                    return new Antwort(plan.fehler(), true);
                }
                b.neueAuftraege(plan.schritte());
                return Antwort.ok("stinkendreich.butler.kocht");
            }
            case SORTIEREN -> {
                b.neueAuftraege(List.of(new SortierAuftrag(p.blockPosition())));
                return Antwort.ok("stinkendreich.butler.sortiert.los");
            }
            case FOLGEN -> {
                // Läuft er gerade nach Hause oder zu einem Ort, bricht er das ab (sonst bestätigte er nur und ging weiter)
                if (b.aktuellerAuftrag() instanceof de.moinmornhart.stinkendreich.butler.auftrag.GehAuftrag) {
                    b.stopp();
                }
                b.setzeFolgen(true);
                return Antwort.ok("stinkendreich.butler.folgen");
            }
            case BLEIBEN -> {
                if (b.aktuellerAuftrag() instanceof de.moinmornhart.stinkendreich.butler.auftrag.GehAuftrag) {
                    b.stopp();
                }
                b.setzeFolgen(false);
                b.setzeZuhause(b.blockPosition());
                return Antwort.ok("stinkendreich.butler.bleiben");
            }
            case HEIM -> {
                b.setzeFolgen(false);
                b.setzeZuhause(b.heimat());
                b.neueAuftraege(List.of(new GehAuftrag(true)));
                return Antwort.ok("stinkendreich.butler.heim");
            }
            case KOMMEN -> {
                b.neueAuftraege(List.of(new GehAuftrag(false)));
                return Antwort.ok("stinkendreich.butler.kommt");
            }
            case STOPP -> {
                b.stopp();
                return Antwort.ok("stinkendreich.butler.stopp");
            }
            case INVENTAR -> {
                b.inventarZeigen(p);
                return new Antwort(inhalt(b), false);
            }
            case STATUS -> {
                return new Antwort(b.status().copy().append(b.offeneAuftraege() > 1
                        ? Component.translatable("stinkendreich.butler.danach", b.offeneAuftraege() - 1) : Component.empty()), false);
            }
            case HILFE -> {
                return Antwort.ok("stinkendreich.butler.hilfe");
            }
            case BRUECKE -> {
                // Nur Bewohner dürfen die Zugbrücke bedienen (wie am Schalter)
                var villaZustand = de.moinmornhart.stinkendreich.mechanik.VillaZustand.vonSpieler(p.level().getServer(), p);
                if (!villaZustand.istBewohner(p)) {
                    return new Antwort(Component.translatable("stinkendreich.bruecke.nur_bewohner"), true);
                }
                boolean zu = "zu".equals(bf.quelle());
                String fehler = de.moinmornhart.stinkendreich.mechanik.Zugbruecke.sichern(p.level().getServer(), villaZustand.nr(), zu);
                return fehler != null ? new Antwort(Component.translatable(fehler), true)
                        : Antwort.ok(zu ? "stinkendreich.butler.bruecke.zu" : "stinkendreich.butler.bruecke.auf");
            }
            case GAST -> {
                return gast(p, bf.quelle(), bf.menge());
            }
            case FAHREN -> {
                return fahren(b, p, bf.quelle(), false);
            }
            case FLIEGEN -> {
                return fahren(b, p, bf.quelle(), true);
            }
            case STRASSE -> {
                return strasseBauen(b, p, bf.quelle());
            }
            case ORT_MERKEN -> {
                String name = bf.quelle() == null ? "" : Sprache.normalisieren(bf.quelle());
                if (name.isEmpty()) {
                    return new Antwort(Component.translatable("stinkendreich.butler.ort.ohnename"), true);
                }
                b.ortMerken(name, p.blockPosition());
                return Antwort.ok("stinkendreich.butler.ort.gemerkt", name);
            }
            case ORTE -> {
                return b.orte().isEmpty() ? Antwort.ok("stinkendreich.butler.ort.keine")
                        : Antwort.ok("stinkendreich.butler.ort.liste", ortListe(b));
            }
            default -> {
                return new Antwort(Component.translatable("stinkendreich.butler.verstehtnicht"), true);
            }
        }
    }

    /** Kurze Liste, was der Butler bei sich trägt. */
    static Component inhalt(ButlerEntity b) {
        List<ItemStack> zusammen = new ArrayList<>();
        for (int i = 0; i < b.taschen().getContainerSize(); i++) {
            ItemStack s = b.taschen().getItem(i);
            if (s.isEmpty()) {
                continue;
            }
            boolean dabei = false;
            for (ItemStack z : zusammen) {
                if (ItemStack.isSameItemSameComponents(z, s)) {
                    z.grow(s.getCount());
                    dabei = true;
                }
            }
            if (!dabei) {
                zusammen.add(s.copy());
            }
        }
        if (zusammen.isEmpty()) {
            return Component.translatable("stinkendreich.butler.leer");
        }
        var text = Component.translatable("stinkendreich.butler.dabei").copy();
        for (int i = 0; i < zusammen.size(); i++) {
            text.append(i == 0 ? " " : ", ").append(zusammen.get(i).getCount() + "× ").append(zusammen.get(i).getHoverName());
        }
        return text;
    }

    private static int status(CommandContext<CommandSourceStack> k) {
        ServerPlayer p = k.getSource().getPlayer();
        ButlerEntity b = p == null ? null : ButlerVerwaltung.vonSpieler(p);
        if (b == null) {
            k.getSource().sendSuccess(() -> Component.translatable("stinkendreich.butler.keiner"), false);
        } else {
            b.sagen(b.status());
        }
        k.getSource().sendSuccess(() -> Component.translatable("stinkendreich.butler.hilfe").withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    /** Die angeschaute Truhe wird die Lieferkiste des eigenen Butlers. */
    private static int gastBefehl(CommandContext<CommandSourceStack> k, String name, int was) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = k.getSource().getPlayerOrException();
        Antwort a = gast(p, name, was);
        Kompat.nachricht(p, a.text().copy().withStyle(a.fehler() ? ChatFormatting.RED : ChatFormatting.GOLD), false);
        return a.fehler() ? 0 : 1;
    }

    private static int truheFestlegen(CommandContext<CommandSourceStack> k) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = k.getSource().getPlayerOrException();
        ButlerEntity b = ButlerVerwaltung.vonSpieler(p);
        HitResult blick = p.pick(6.0, 1.0F, false);
        if (b == null || !(blick instanceof BlockHitResult bt) || blick.getType() != HitResult.Type.BLOCK
                || !(p.level().getBlockEntity(bt.getBlockPos()) instanceof Container)) {
            k.getSource().sendFailure(Component.translatable("stinkendreich.butler.truhe.keine"));
            return 0;
        }
        BlockPos pos = bt.getBlockPos();
        b.setzeLiefertruhe(pos);
        b.sagen(Component.translatable("stinkendreich.butler.truhe.ok", pos.getX(), pos.getY(), pos.getZ()));
        return 1;
    }
}
