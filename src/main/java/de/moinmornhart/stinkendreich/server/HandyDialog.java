package de.moinmornhart.stinkendreich.server;

//? if >=1.21.6 {
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import de.moinmornhart.stinkendreich.butler.ButlerEntity;
import de.moinmornhart.stinkendreich.butler.ButlerVerwaltung;
import de.moinmornhart.stinkendreich.handy.Lieferservice;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.Action;
import net.minecraft.server.dialog.action.CommandTemplate;
import net.minecraft.server.dialog.action.ParsedTemplate;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.input.TextInput;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Das goldene Handy für Spieler ohne Mod: dieselben Apps wie der Handy-Bildschirm der Mod, aber als Dialogfenster,
 * das Minecraft selbst mitbringt (seit 1.21.6). Jeder Knopf schickt den Befehl {@code /handy …} an den Server
 * (siehe {@code handy.Handy}); Seitenwechsel öffnen einfach den nächsten Dialog.
 */
public final class HandyDialog {
    private static final int BREITE = 200;

    private HandyDialog() {
    }

    /** Öffnet eine Seite des Handys: start, navi, reise|Ziel (Limo oder Heli?), lieferung oder auftrag. */
    public static void oeffnen(ServerPlayer spieler, String seite) {
        List<ActionButton> knoepfe = new ArrayList<>();
        List<Input> eingaben = new ArrayList<>();
        String titel = "start";
        // Navi-Ziel: Sitzt der Spieler schon in Limo oder Hubschrauber, geht es gleich los, sonst erst „Limo oder Heli?“
        String reise = spieler.getVehicle() instanceof de.moinmornhart.stinkendreich.auto.AutoEntity ? "fahr|" : "seite|reise|";
        if (seite.startsWith("reise|")) {
            String ziel = seite.substring("reise|".length());
            seite = "reise";
            titel = "reise";
            knopf(knoepfe, "reise.limo", "fahr|" + ziel);
            knopf(knoepfe, "reise.heli", "flieg|" + ziel);
            knopf(knoepfe, "zurueck", "seite|navi");
        }
        switch (seite) {
            case "reise" -> {
            }
            case "navi" -> {
                titel = "navi";
                knopf(knoepfe, "navi.villa", reise + "#villa");
                for (String ziel : List.of("dorf", "endportal", "netherportal", "mine")) {
                    knopf(knoepfe, "navi." + ziel, reise + ziel);
                }
                // Villen der anderen Spieler
                for (String herr : de.moinmornhart.stinkendreich.handy.Handy.andereVillen(spieler)) {
                    knoepfe.add(new ActionButton(new CommonButtonData(Component.literal("🏠 ")
                            .append(Component.translatable("stinkendreich.strasse.ziel.villa", herr)), BREITE),
                            Optional.of(befehl(reise + "villa von " + herr))));
                }
                ButlerEntity b = ButlerVerwaltung.vonSpieler(spieler);
                if (b != null) {
                    for (String ort : new java.util.TreeSet<>(b.orte().keySet())) {
                        knoepfe.add(new ActionButton(new CommonButtonData(Component.literal("➜ " + ort), BREITE),
                                Optional.of(befehl(reise + ort))));
                    }
                }
                // Eigenen Ort an der aktuellen Stelle speichern: James kennt ihn danach auch im Chat
                eingaben.add(new Input("name", new TextInput(BREITE, Component.translatable("stinkendreich.handy.orte.name"),
                        true, "", 32, Optional.empty())));
                knoepfe.add(new ActionButton(new CommonButtonData(Component.translatable("stinkendreich.handy.orte.speichern"), BREITE),
                        Optional.of(new CommandTemplate(ParsedTemplate.CODEC.parse(JsonOps.INSTANCE,
                                new JsonPrimitive("handy ort|merken|$(name)")).getOrThrow()))));
                zurueck(knoepfe);
            }
            case "smarthome" -> {
                titel = "smarthome";
                for (String[] zeile : new String[][]{{"rollo", "zu", "auf"}, {"bruecke", "zu", "auf"}, {"licht", "an", "aus"},
                        {"disco", "an", "aus"}, {"automatik", "an", "aus"}, {"inkognito", "an", "aus"},
                        {"krisenbunker", "an", "aus"}, {"haie", "auf", "zu"}, {"tor", "zu", "auf"}, {"garage", "zu", "auf"}}) {
                    knopf(knoepfe, "smart." + zeile[0] + "." + zeile[1], "smart|" + zeile[0] + "|" + zeile[1]);
                    knopf(knoepfe, "smart." + zeile[0] + "." + zeile[2], "smart|" + zeile[0] + "|" + zeile[2]);
                }
                for (var f : de.moinmornhart.stinkendreich.mechanik.Eigenschaften.Lichtfarbe.values()) {
                    knopf(knoepfe, "smart.farbe." + f.getSerializedName(), "smart|farbe|" + f.getSerializedName());
                }
                zurueck(knoepfe);
            }
            case "sicherheit" -> {
                // Alarmanlage übers Handy: Stufe 0–4, Alarm aus, jede Anlage auf Auto/An/Aus
                titel = "sicherheit";
                for (int i = 0; i <= 4; i++) {
                    knoepfe.add(new ActionButton(new CommonButtonData(Component.translatable("stinkendreich.handy.sh.stufe",
                            Component.translatable("stinkendreich.stufe." + i)), BREITE), Optional.of(befehl("smart|stufe|" + i))));
                }
                knopf(knoepfe, "sh.alarmaus", "smart|alarmaus");
                for (String a : de.moinmornhart.stinkendreich.mechanik.SmartHome.ANLAGEN) {
                    for (String m : new String[]{"auto", "an", "aus"}) {
                        knoepfe.add(new ActionButton(new CommonButtonData(Component.translatable("stinkendreich.handy.sh.anlage." + a)
                                .append(": ").append(Component.translatable("stinkendreich.handy.sh.modus." + m)), BREITE),
                                Optional.of(befehl("smart|anlage|" + a + ":" + m))));
                    }
                }
                zurueck(knoepfe);
            }
            case "gaeste" -> {
                titel = "gaeste";
                var z = de.moinmornhart.stinkendreich.mechanik.VillaZustand.vonSpieler(spieler.level().getServer(), spieler);
                for (String gast : z.gaeste()) {
                    knoepfe.add(new ActionButton(new CommonButtonData(Component.literal("✕ " + gast), BREITE),
                            Optional.of(befehl("gast|raus|" + gast))));
                }
                eingaben.add(new Input("name", new TextInput(BREITE, Component.translatable("stinkendreich.handy.gaeste.name"),
                        true, "", 16, Optional.empty())));
                knoepfe.add(new ActionButton(new CommonButtonData(Component.translatable("stinkendreich.handy.gaeste.eintragen"), BREITE),
                        Optional.of(new CommandTemplate(ParsedTemplate.CODEC.parse(JsonOps.INSTANCE,
                                new JsonPrimitive("handy gast|rein|$(name)")).getOrThrow()))));
                zurueck(knoepfe);
            }
            case "lieferung" -> {
                titel = "lieferung";
                for (String was : Lieferservice.ANGEBOT) {
                    knopf(knoepfe, "liefer." + was, "liefer|" + was);
                }
                zurueck(knoepfe);
            }
            case "auftrag" -> {
                titel = "auftrag";
                for (String a : List.of("eisen", "diamanten", "holz", "essen", "sortieren", "heim")) {
                    knopf(knoepfe, "auftrag." + a, "auftrag|" + Component.translatable("stinkendreich.handy.auftrag." + a + ".text").getString());
                }
                // Eigener Auftrag: Textfeld, der Knopf setzt den Text in den Befehl ein
                eingaben.add(new Input("text", new TextInput(BREITE, Component.translatable("stinkendreich.handy.auftrag.frei"),
                        true, "", 120, Optional.empty())));
                knoepfe.add(new ActionButton(new CommonButtonData(Component.translatable("stinkendreich.handy.auftrag.senden"), BREITE),
                        Optional.of(new CommandTemplate(ParsedTemplate.CODEC.parse(JsonOps.INSTANCE,
                                new JsonPrimitive("handy auftrag|$(text)")).getOrThrow()))));
                zurueck(knoepfe);
            }
            default -> {
                knopf(knoepfe, "navi", "seite|navi");
                knopf(knoepfe, "lieferung", "seite|lieferung");
                knopf(knoepfe, "auftraege", "seite|auftrag");
                knopf(knoepfe, "mitarbeiter", "mitarbeiter");
                knopf(knoepfe, "bodyguards", "bodyguards|rufen");
                knopf(knoepfe, "bodyguards_heim", "bodyguards|heim");
                knopf(knoepfe, "smarthome", "seite|smarthome");
                knopf(knoepfe, "sicherheit", "seite|sicherheit");
                knopf(knoepfe, "gaeste", "seite|gaeste");
            }
        }
        CommonDialogData daten = new CommonDialogData(Component.translatable("stinkendreich.handy.titel." + titel),
                Optional.empty(), true, false, DialogAction.CLOSE, List.of(), eingaben);
        spieler.openDialog(Holder.direct(new MultiActionDialog(daten, knoepfe, Optional.empty(), 1)));
    }

    private static void knopf(List<ActionButton> knoepfe, String text, String aktion) {
        knoepfe.add(new ActionButton(new CommonButtonData(Component.translatable("stinkendreich.handy." + text), BREITE),
                Optional.of(befehl(aktion))));
    }

    private static void zurueck(List<ActionButton> knoepfe) {
        knopf(knoepfe, "zurueck", "seite|start");
    }

    private static Action befehl(String aktion) {
        return new StaticAction(new ClickEvent.RunCommand("/handy " + aktion));
    }
}
//?}
