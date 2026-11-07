package de.moinmornhart.stinkendreich.server;

//? if >=1.21.6 {
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import de.moinmornhart.stinkendreich.fernseher.FernseherEntity;
import de.moinmornhart.stinkendreich.klavier.Klavier;
import de.moinmornhart.stinkendreich.klavier.Lieder;
import de.moinmornhart.stinkendreich.mechanik.AufzugstafelBlock;
import de.moinmornhart.stinkendreich.netz.Netz;
import de.moinmornhart.stinkendreich.netz.OhneMod;
import de.moinmornhart.stinkendreich.mechanik.VillaZustand;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.Action;
import net.minecraft.server.dialog.action.CommandTemplate;
import net.minecraft.server.dialog.action.ParsedTemplate;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.input.TextInput;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Bedienfelder der Villa für Spieler ohne Mod: Sicherheitszentrale, Tastenfeld, Aufzug, Konzertflügel,
 * Musikanlage und Fernseher als Dialogfenster von Minecraft. Die Knöpfe lösen über {@code /stinkendreich knopf …} genau die
 * Aktionen aus, die auch die Bildschirme der Mod schicken ({@link Netz#ausfuehren}); Reichweite und Rechte prüft
 * dieselbe Stelle.
 */
final class Bedienfelder {
    private static final int BREITE = 200;
    /** Schallplatten von Minecraft für die Musikanlage (nur die, die es in dieser Version gibt). */
    private static final String[] PLATTEN = {"pigstep", "otherside", "creator", "relic", "precipice", "lava_chicken",
            "cat", "chirp", "blocks", "mall", "strad", "wait"};
    private static final String[] NOTENNAMEN = {"C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "H"};

    private Bedienfelder() {
    }

    static void registrieren() {
        OhneMod.anmelden("zentrale", Bedienfelder::zentrale);
        OhneMod.anmelden("tastenfeld", Bedienfelder::tastenfeld);
        OhneMod.anmelden("aufzug", Bedienfelder::aufzug);
        OhneMod.anmelden("klavier", Bedienfelder::klavier);
        OhneMod.anmelden("musikanlage", Bedienfelder::musik);
        OhneMod.anmelden("fernseher", Bedienfelder::fernseher);
        Netz.bearbeiter("musik_platte", 8, Bedienfelder::platte);
        Netz.bearbeiter("fenster_klaviatur", 10, (spieler, pos, wert) -> klaviatur(spieler, pos));
    }

    // ------------------------------------------------------------------
    // Die einzelnen Fenster
    // ------------------------------------------------------------------

    private static void zentrale(ServerPlayer spieler, BlockPos pos) {
        VillaZustand z = VillaZustand.fuer(spieler.level().getServer(), pos);
        List<ActionButton> knoepfe = new ArrayList<>();
        boolean bunker = de.moinmornhart.stinkendreich.mechanik.SicherheitszentraleBlock.kommandozentrale(spieler.level(), pos);
        for (int stufe = 0; stufe <= (bunker ? VillaZustand.MAX_STUFE : VillaZustand.FESTUNG - 1); stufe++) {
            knoepfe.add(knopf(Component.translatable("stinkendreich.stufe." + stufe), BREITE, aktion("zentrale", pos, "stufe:" + stufe)));
        }
        knoepfe.add(knopf(Component.translatable("stinkendreich.zentrale.alarm_aus"), BREITE, aktion("zentrale", pos, "alarm_aus")));
        knoepfe.add(knopf(Component.translatable("stinkendreich.zentrale.eintragen"), BREITE, aktion("zentrale", pos, "eintragen")));
        Component status = Component.translatable("stinkendreich.zentrale.status",
                Component.translatable("stinkendreich.stufe." + z.stufe()),
                Component.translatable(z.alarmAktiv() ? "stinkendreich.alarm.an" : "stinkendreich.alarm.aus"),
                String.join(", ", z.bewohner().values()));
        zeigen(spieler, Component.translatable("block.stinkendreich.sicherheitszentrale"), List.of(text(status)),
                List.of(), knoepfe, 1, DialogAction.CLOSE);
    }

    private static void tastenfeld(ServerPlayer spieler, BlockPos pos) {
        List<Input> felder = List.of(
                new Input("code", new TextInput(BREITE, Component.translatable("stinkendreich.tastenfeld.code"), true, "", 8, Optional.empty())),
                new Input("neu", new TextInput(BREITE, Component.translatable("stinkendreich.ohnemod.code_neu"), true, "", 8, Optional.empty())));
        List<ActionButton> knoepfe = List.of(
                knopf(Component.translatable("stinkendreich.ohnemod.oeffnen"), 98, vorlage("tastenfeld", pos, "$(code)")),
                knopf(Component.translatable("stinkendreich.tastenfeld.aendern"), 98, vorlage("tastenfeld_neu", pos, "$(code):$(neu)")));
        zeigen(spieler, Component.translatable("block.stinkendreich.tastenfeld"),
                List.of(text(Component.translatable("stinkendreich.ohnemod.code_hinweis"))), felder, knoepfe, 2, DialogAction.CLOSE);
    }

    private static void aufzug(ServerPlayer spieler, BlockPos pos) {
        ServerLevel welt = (ServerLevel) spieler.level();
        List<BlockPos> halte = AufzugstafelBlock.haltestellen(welt, pos);
        List<ActionButton> knoepfe = new ArrayList<>();
        for (int i = halte.size() - 1; i >= 0; i--) {
            BlockPos ziel = halte.get(i);
            String name = AufzugstafelBlock.name(welt, ziel);
            knoepfe.add(ziel.equals(pos)
                    ? new ActionButton(new CommonButtonData(Component.literal("● " + name), BREITE), Optional.empty())
                    : knopf(AufzugstafelBlock.nurMitKarte(welt, ziel)
                            ? Component.literal(name + " ").append(Component.translatable("stinkendreich.aufzug.mit_karte")
                                    .withStyle(ChatFormatting.GOLD))
                            : Component.literal(name), BREITE, aktion("aufzug", pos, String.valueOf(ziel.asLong()))));
        }
        zeigen(spieler, Component.translatable("block.stinkendreich.aufzugstafel"), List.of(), List.of(), knoepfe, 1, DialogAction.CLOSE);
    }

    /** Flügel: die Stücke, Stopp und ein Knopf zur Klaviatur. */
    private static void klavier(ServerPlayer spieler, BlockPos fluegel) {
        List<ActionButton> knoepfe = new ArrayList<>();
        for (String lied : Lieder.NAMEN) {
            knoepfe.add(knopf(Component.translatable("stinkendreich.klavier.lied." + lied), BREITE, aktion("klavier", fluegel, "l|" + lied)));
        }
        knoepfe.add(knopf(Component.translatable("stinkendreich.klavier.stopp"), BREITE, aktion("klavier", fluegel, "s")));
        knoepfe.add(knopf(Component.translatable("stinkendreich.ohnemod.klaviatur"), BREITE, aktion("fenster_klaviatur", fluegel, "")));
        zeigen(spieler, Component.translatable("block.stinkendreich.konzertfluegel"), List.of(), List.of(), knoepfe, 1, DialogAction.CLOSE);
    }

    /** Klaviatur aus Knöpfen, eine Oktave je Zeile; das Fenster bleibt beim Spielen offen. */
    private static void klaviatur(ServerPlayer spieler, BlockPos fluegel) {
        List<ActionButton> tasten = new ArrayList<>();
        for (int note = Klavier.TIEFSTE; note <= Klavier.HOECHSTE; note++) {
            tasten.add(knopf(Component.literal(NOTENNAMEN[note % 12] + (note / 12 - 1)), 34, aktion("klavier", fluegel, "t|" + note)));
        }
        zeigen(spieler, Component.translatable("block.stinkendreich.konzertfluegel"),
                List.of(text(Component.translatable("stinkendreich.ohnemod.klavier_hinweis"))), List.of(), tasten, 12, DialogAction.NONE);
    }

    /** Musikanlage: eigene Musik geht nur mit der Mod; ohne Mod laufen die Schallplatten von Minecraft. */
    private static void musik(ServerPlayer spieler, BlockPos pos) {
        List<ActionButton> knoepfe = new ArrayList<>();
        for (String platte : PLATTEN) {
            if (BuiltInRegistries.SOUND_EVENT.containsKey(Identifier.withDefaultNamespace("music_disc." + platte))) {
                knoepfe.add(knopf(Component.translatable("jukebox_song.minecraft." + platte), 150, aktion("musik_platte", pos, platte)));
            }
        }
        knoepfe.add(knopf(Component.translatable("stinkendreich.musik.stopp"), 150, aktion("musik_platte", pos, "stopp")));
        zeigen(spieler, Component.translatable("stinkendreich.musik.titel"),
                List.of(text(Component.translatable("stinkendreich.ohnemod.musik_hinweis"))), List.of(), knoepfe, 2, DialogAction.CLOSE);
    }

    /** Spielt eine Schallplatte an der Musikanlage (alle in der Nähe hören sie) oder hält sie an. */
    private static void platte(ServerPlayer spieler, BlockPos pos, String wert) {
        ServerLevel welt = (ServerLevel) spieler.level();
        // Erst alles stoppen, was an Schallplatten läuft, dann die neue starten
        for (ServerPlayer p : welt.players()) {
            if (p.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) < 96 * 96) {
                p.connection.send(new ClientboundStopSoundPacket(null, SoundSource.RECORDS));
            }
        }
        if (wert.equals("stopp")) {
            return;
        }
        BuiltInRegistries.SOUND_EVENT.getOptional(Identifier.withDefaultNamespace("music_disc." + wert))
                .ifPresent(ton -> welt.playSound(null, pos, ton, SoundSource.RECORDS, 4.0F, 1.0F));
    }

    /**
     * Fernseher: YouTube-Link eingeben (dieselbe Aktion „fernseher“ wie das Menü der Mod), Video im Browser ansehen,
     * ausschalten. Ein Bild auf dem Schirm gibt es ohne Mod nicht (das Vorschaubild lädt erst der Rechner mit Mod).
     */
    private static void fernseher(ServerPlayer spieler, BlockPos pos) {
        FernseherEntity tv = null;
        for (FernseherEntity e : spieler.level().getEntitiesOfClass(FernseherEntity.class, new AABB(pos).inflate(1.5))) {
            if (tv == null || e.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) < tv.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos))) {
                tv = e;
            }
        }
        if (tv == null) {
            return;
        }
        String id = tv.getId() + "|";
        List<DialogBody> inhalt = new ArrayList<>();
        List<ActionButton> knoepfe = new ArrayList<>();
        knoepfe.add(knopf(Component.translatable("stinkendreich.fernseher.anzeigen"), 210, vorlage("fernseher", pos, id + "$(link)")));
        if (!tv.video().isEmpty()) {
            String link = de.moinmornhart.stinkendreich.fernseher.FernseherEntity.adresse(tv.video());
            inhalt.add(text(Component.translatable("stinkendreich.fernseher.laeuft", link)));
            knoepfe.add(knopf(Component.translatable("stinkendreich.fernseher.abspielen"), 210,
                    new StaticAction(new ClickEvent.OpenUrl(java.net.URI.create(link)))));
        }
        knoepfe.add(knopf(Component.translatable("stinkendreich.fernseher.aus"), 210, aktion("fernseher", pos, id)));
        inhalt.add(text(Component.translatable("stinkendreich.ohnemod.fernseher_hinweis")));
        List<Input> felder = List.of(new Input("link", new TextInput(BREITE + 100,
                Component.translatable("stinkendreich.fernseher.link"), true, "", 200, Optional.empty())));
        zeigen(spieler, Component.translatable("stinkendreich.fernseher.titel"), inhalt, felder, knoepfe, 1, DialogAction.CLOSE);
    }

    // ------------------------------------------------------------------
    // Hilfen
    // ------------------------------------------------------------------

    static void zeigen(ServerPlayer spieler, Component titel, List<DialogBody> inhalt, List<Input> felder,
                       List<ActionButton> knoepfe, int spalten, DialogAction nachKlick) {
        CommonDialogData daten = new CommonDialogData(titel, Optional.empty(), true, false, nachKlick, inhalt, felder);
        spieler.openDialog(Holder.direct(new MultiActionDialog(daten, knoepfe, Optional.empty(), spalten)));
    }

    static DialogBody text(Component text) {
        return new PlainMessage(text, BREITE + 100);
    }

    static ActionButton knopf(Component text, int breite, Action aktion) {
        return new ActionButton(new CommonButtonData(text, breite), Optional.of(aktion));
    }

    /** Knopf löst die Netz-Aktion {@code art} am Block {@code pos} mit festem Wert aus. */
    static Action aktion(String art, BlockPos pos, String wert) {
        return new StaticAction(new ClickEvent.RunCommand("/" + befehl(art, pos, wert)));
    }

    /** Wie {@link #aktion}, aber der Wert enthält Platzhalter $(feld) für die Eingabefelder. */
    static Action vorlage(String art, BlockPos pos, String wert) {
        return new CommandTemplate(ParsedTemplate.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(befehl(art, pos, wert))).getOrThrow());
    }

    private static String befehl(String art, BlockPos pos, String wert) {
        return "stinkendreich knopf " + art + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + wert;
    }
}
//?}
