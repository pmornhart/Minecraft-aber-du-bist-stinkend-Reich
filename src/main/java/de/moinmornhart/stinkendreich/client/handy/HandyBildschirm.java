package de.moinmornhart.stinkendreich.client.handy;

import de.moinmornhart.stinkendreich.client.KompatClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else
/*import net.minecraft.client.gui.GuiGraphics;*/

import java.util.List;

/**
 * Das Handy: ein goldenes Smartphone mit Apps. Startseite: Navigation (Ziel wählen, dann Limo oder Hubschrauber:
 * James holt dich ab und bringt dich hin, sobald du eingestiegen bist; sitzt du schon in Limo oder Hubschrauber, geht es
 * gleich damit los; unter „Meine Orte“ eigene Ziele speichern, die James dann auch im Chat kennt), Lieferservice (Tiere und Waren), Aufträge (wie im Chat), Mitarbeiter bestellen, Bodyguards rufen oder
 * heimschicken. Jede Taste schickt eine Nachricht „handy“ an den Server.
 */
public class HandyBildschirm extends Screen {
    private static final int BREITE = 160, HOEHE = 270;
    private static final int KNOPF_B = 136, KNOPF_H = 18, ABSTAND = 3;
    /** Etwas flachere Schalter auf der Smart-Home-Seite, damit alle zehn plus Farben passen. */
    private static final int SMART_H = 16;

    private enum Seite { START, NAVI, REISE, ORTE, LIEFERUNG, AUFTRAG, SMART, SICHERHEIT, GAESTE }

    /** Höchstens so viele eigene Orte passen auf eine Seite. */
    private static final int ORTE_MAX = 7;
    /** Die Orte, die James kennt (schickt der Server, siehe {@link #orteEmpfangen}). */
    private static List<String> orte = List.of();
    /** Gästeliste der eigenen Villa (schickt der Server, siehe {@link #gaesteEmpfangen}). */
    private static List<String> gaeste = List.of();
    /** Smart-Home-Schalter: Name, Symbol, Wert zum Einschalten, Wert zum Ausschalten. */
    private static final String[][] SMART_ZEILEN = {{"rollo", "▤", "zu", "auf"}, {"bruecke", "⇄", "zu", "auf"},
            {"licht", "☀", "an", "aus"}, {"disco", "♫", "an", "aus"}, {"automatik", "☾", "an", "aus"},
            {"inkognito", "◐", "an", "aus"}, {"krisenbunker", "☢", "an", "aus"}, {"haie", "♒", "auf", "auf"},
            {"tor", "▥", "zu", "auf"}, {"garage", "▦", "zu", "auf"}};
    /** Zustand der Smart-Home-Schalter (schickt der Server, siehe {@link #smartEmpfangen}). */
    private static final java.util.Map<String, Boolean> smartZustand = new java.util.HashMap<>();
    /** Dieselben Werte als Text (Alarmstufe, Einstellung der Sicherheitsanlagen „auto/an/aus“). */
    private static final java.util.Map<String, String> smartWerte = new java.util.HashMap<>();
    /** Sicherheitsanlagen mit Symbol (Reihenfolge wie {@code SmartHome.ANLAGEN}). */
    private static final String[][] ANLAGEN = {{"geschuetze", "✦"}, {"laser", "≋"}, {"melder", "◎"}, {"sirenen", "♪"},
            {"minen", "✸"}, {"fallen", "▣"}};

    private Seite seite = Seite.START;
    /** Gewähltes Navi-Ziel, solange auf der Seite REISE noch Limo oder Hubschrauber gewählt wird. */
    private String reiseZiel = "";
    private EditBox eingabe;

    public HandyBildschirm() {
        super(Component.translatable("item.stinkendreich.handy"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int links() {
        return width / 2 - BREITE / 2;
    }

    private int oben() {
        return height / 2 - HOEHE / 2;
    }

    @Override
    protected void init() {
        int x = links() + (BREITE - KNOPF_B) / 2;
        int y = oben() + 40;
        switch (seite) {
            case START -> {
                y = knopf(x, y, "navi", () -> wechseln(Seite.NAVI));
                y = knopf(x, y, "lieferung", () -> wechseln(Seite.LIEFERUNG));
                y = knopf(x, y, "auftraege", () -> wechseln(Seite.AUFTRAG));
                y = knopf(x, y, "mitarbeiter", () -> senden("mitarbeiter"));
                y = knopf(x, y, "bodyguards", () -> senden("bodyguards|rufen"));
                y = knopf(x, y, "bodyguards_heim", () -> senden("bodyguards|heim"));
                y = knopf(x, y, "smarthome", () -> wechseln(Seite.SMART));
                y = knopf(x, y, "sicherheit", () -> wechseln(Seite.SICHERHEIT));
                knopf(x, y, "gaeste", () -> wechseln(Seite.GAESTE));
            }
            case NAVI -> {
                for (String ziel : List.of("villa", "dorf", "endportal", "netherportal", "mine")) {
                    String befehl = ziel.equals("villa") ? "#villa" : ziel;
                    y = knopf(x, y, "navi." + ziel, () -> reisen(befehl));
                }
                y += 4;
                knopf(x, y, "orte", () -> wechseln(Seite.ORTE));
                knopf(x, oben() + HOEHE - 30, "zurueck", () -> wechseln(Seite.START));
            }
            case REISE -> {
                y += 14;    // Platz für den Hinweis „James holt dich ab“
                y = knopf(x, y, "reise.limo", () -> senden("fahr|" + reiseZiel));
                knopf(x, y, "reise.heli", () -> senden("flieg|" + reiseZiel));
                knopf(x, oben() + HOEHE - 30, "zurueck", () -> wechseln(Seite.NAVI));
            }
            case ORTE -> {
                // Gespeicherte Orte: Name = hinfahren, ✕ = löschen
                List<String> liste = orte.size() > ORTE_MAX ? orte.subList(0, ORTE_MAX) : orte;
                for (String ort : liste) {
                    if (ort.startsWith("#villa:")) {
                        // Villa eines anderen Spielers: hinfahren, aber nicht löschbar
                        String herr = ort.substring("#villa:".length());
                        Component text = Component.literal("🏠 ").append(Component.translatable("stinkendreich.strasse.ziel.villa", herr));
                        addRenderableWidget(Button.builder(text, b -> reisen("villa von " + herr))
                                .bounds(x, y, KNOPF_B, KNOPF_H).build());
                        y += KNOPF_H + ABSTAND;
                        continue;
                    }
                    addRenderableWidget(Button.builder(Component.literal("➜ " + ort.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + ort.substring(1)),
                            b -> reisen(ort))
                            .bounds(x, y, KNOPF_B - 22, KNOPF_H).build());
                    addRenderableWidget(Button.builder(Component.literal("✕"), b -> anfragen("ort|vergessen|" + ort))
                            .bounds(x + KNOPF_B - 20, y, 20, KNOPF_H).build());
                    y += KNOPF_H + ABSTAND;
                }
                if (orte.isEmpty()) {
                    y += 14;    // Platz für den Hinweis „noch keine Orte“
                }
                eingabe = new EditBox(font, x, Math.max(y + 4, oben() + 192), KNOPF_B, KNOPF_H, Component.translatable("stinkendreich.handy.orte.name"));
                eingabe.setHint(Component.translatable("stinkendreich.handy.orte.name"));
                eingabe.setMaxLength(32);
                addRenderableWidget(eingabe);
                knopf(x, Math.max(y + 4, oben() + 192) + KNOPF_H + 4, "orte.speichern", () -> {
                    if (!eingabe.getValue().isBlank()) {
                        anfragen("ort|merken|" + eingabe.getValue().trim());
                        eingabe.setValue("");
                    }
                });
                knopf(x, oben() + HOEHE - 30, "zurueck", () -> wechseln(Seite.NAVI));
            }
            case GAESTE -> {
                // Gästeliste: ✕ streicht einen Gast, unten Name eintippen + „Als Gast eintragen“
                List<String> liste = gaeste.size() > ORTE_MAX ? gaeste.subList(0, ORTE_MAX) : gaeste;
                for (String gast : liste) {
                    addRenderableWidget(Button.builder(Component.literal("☺ " + gast), b -> { })
                            .bounds(x, y, KNOPF_B - 22, KNOPF_H).build()).active = false;
                    addRenderableWidget(Button.builder(Component.literal("✕").withStyle(ChatFormatting.RED), b -> anfragen("gast|raus|" + gast))
                            .bounds(x + KNOPF_B - 20, y, 20, KNOPF_H).build());
                    y += KNOPF_H + ABSTAND;
                }
                if (gaeste.isEmpty()) {
                    y += 14;    // Platz für den Hinweis „noch keine Gäste“
                }
                int ey = Math.max(y + 4, oben() + 192);
                eingabe = new EditBox(font, x, ey, KNOPF_B, KNOPF_H, Component.translatable("stinkendreich.handy.gaeste.name"));
                eingabe.setHint(Component.translatable("stinkendreich.handy.gaeste.name"));
                eingabe.setMaxLength(16);
                addRenderableWidget(eingabe);
                knopf(x, ey + KNOPF_H + 4, "gaeste.eintragen", () -> {
                    if (!eingabe.getValue().isBlank()) {
                        anfragen("gast|rein|" + eingabe.getValue().trim());
                        eingabe.setValue("");
                    }
                });
                knopf(x, oben() + HOEHE - 30, "zurueck", () -> wechseln(Seite.START));
            }
            case LIEFERUNG -> {
                for (String was : de.moinmornhart.stinkendreich.handy.Lieferservice.ANGEBOT) {
                    y = knopf(x, y, "liefer." + was, () -> senden("liefer|" + was), KNOPF_H - 3) - 1;
                }
                knopf(x, oben() + HOEHE - 30, "zurueck", () -> wechseln(Seite.START));
            }
            case SMART -> {
                // Smart Home: je Funktion ein Schalter mit Symbol und Zustand (grün = an), Klick schaltet um;
                // dazu acht Lichtfarben. Das Handy bleibt offen, der Server schickt den neuen Zustand zurück.
                for (String[] zeile : SMART_ZEILEN) {
                    String was = zeile[0];
                    boolean an = smartZustand.getOrDefault(was, false);
                    Component zustand = was.equals("haie")
                            ? Component.translatable("stinkendreich.handy.sh.haie.los").withStyle(ChatFormatting.RED)
                            : Component.translatable("stinkendreich.handy.sh." + was + "." + (an ? "an" : "aus"))
                            .withStyle(an ? ChatFormatting.GREEN : ChatFormatting.GRAY);
                    Component text = Component.literal(zeile[1] + " ").withStyle(ChatFormatting.GOLD)
                            .append(Component.translatable("stinkendreich.handy.sh." + was).withStyle(ChatFormatting.WHITE))
                            .append(Component.literal("  ")).append(zustand);
                    String wert = an ? zeile[3] : zeile[2];
                    addRenderableWidget(Button.builder(text, knopf -> anfragen("smart|" + was + "|" + wert))
                            .bounds(x, y, KNOPF_B, SMART_H).build());
                    y += SMART_H + 2;
                }
                int fx = x, fb = (KNOPF_B - 7 * 2) / 8;
                for (var f : de.moinmornhart.stinkendreich.mechanik.Eigenschaften.Lichtfarbe.values()) {
                    String name = f.getSerializedName();
                    Button knopf = Button.builder(Component.literal("■").withStyle(s -> s.withColor(FARBEN.get(name))),
                            b -> anfragen("smart|farbe|" + name)).bounds(fx, y, fb, SMART_H).build();
                    knopf.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                            Component.translatable("stinkendreich.smarthome.farbe." + name)));
                    addRenderableWidget(knopf);
                    fx += fb + 2;
                }
                knopf(x, oben() + HOEHE - 30, "zurueck", () -> wechseln(Seite.START));
            }
            case SICHERHEIT -> {
                // Alarmanlage übers Handy (Philip, 08.11.): Alarmstufe 0–4 (4 = Festung), Alarm aus und jede Anlage einzeln:
                // Auto (folgt der Stufe) → An (immer scharf, auch ohne Festung) → Aus. Nur der Eigentümer darf umschalten.
                int stufe = parse(smartWerte.getOrDefault("stufe", "0"));
                int sb = (KNOPF_B - 4 * 2) / 5;
                for (int i = 0; i <= 4; i++) {
                    int st = i;
                    Component t = Component.literal(String.valueOf(i)).withStyle(i == stufe
                            ? (i == 4 ? ChatFormatting.RED : ChatFormatting.GREEN) : ChatFormatting.GRAY);
                    Button b = Button.builder(t, k -> anfragen("smart|stufe|" + st)).bounds(x + i * (sb + 2), y, sb, SMART_H).build();
                    b.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("stinkendreich.stufe." + i)));
                    addRenderableWidget(b);
                }
                y += SMART_H + 4;
                boolean alarm = "1".equals(smartWerte.get("alarm"));
                addRenderableWidget(Button.builder(Component.translatable("stinkendreich.handy.sh.alarmaus")
                                .withStyle(alarm ? ChatFormatting.RED : ChatFormatting.GRAY), k -> anfragen("smart|alarmaus"))
                        .bounds(x, y, KNOPF_B, SMART_H).build());
                y += SMART_H + 6;
                for (String[] a : ANLAGEN) {
                    String modus = smartWerte.getOrDefault("anlage_" + a[0], "auto");
                    String naechster = switch (modus) {
                        case "auto" -> "an";
                        case "an" -> "aus";
                        default -> "auto";
                    };
                    ChatFormatting farbe = switch (modus) {
                        case "an" -> ChatFormatting.GREEN;
                        case "aus" -> ChatFormatting.RED;
                        default -> ChatFormatting.AQUA;
                    };
                    Component text = Component.literal(a[1] + " ").withStyle(ChatFormatting.GOLD)
                            .append(Component.translatable("stinkendreich.handy.sh.anlage." + a[0]).withStyle(ChatFormatting.WHITE))
                            .append(Component.literal("  ")).append(Component.translatable("stinkendreich.handy.sh.modus." + modus).withStyle(farbe));
                    addRenderableWidget(Button.builder(text, k -> anfragen("smart|anlage|" + a[0] + ":" + naechster))
                            .bounds(x, y, KNOPF_B, SMART_H).build());
                    y += SMART_H + 2;
                }
                knopf(x, oben() + HOEHE - 30, "zurueck", () -> wechseln(Seite.START));
            }
            case AUFTRAG -> {
                // Aufträge in zwei Spalten, darunter „Abbrechen“ und „James zurücksetzen“
                int halb = (KNOPF_B - 4) / 2;
                List<String> auftraege = List.of("eisen", "diamanten", "holz", "essen", "sortieren", "heim");
                for (int i = 0; i < auftraege.size(); i++) {
                    String a = auftraege.get(i);
                    String text = Component.translatable("stinkendreich.handy.auftrag." + a + ".text").getString();
                    addRenderableWidget(Button.builder(Component.translatable("stinkendreich.handy.auftrag." + a),
                            b -> senden("auftrag|" + text)).bounds(x + (i % 2) * (halb + 4), y, halb, KNOPF_H).build());
                    if (i % 2 == 1) {
                        y += KNOPF_H + ABSTAND;
                    }
                }
                addRenderableWidget(Button.builder(Component.translatable("stinkendreich.handy.auftrag.stopp")
                        .withStyle(ChatFormatting.RED), b -> senden("auftrag|stopp")).bounds(x, y, halb, KNOPF_H).build());
                addRenderableWidget(Button.builder(Component.translatable("stinkendreich.handy.auftrag.reset")
                        .withStyle(ChatFormatting.GOLD), b -> senden("zuruecksetzen")).bounds(x + halb + 4, y, halb, KNOPF_H).build());
                y += KNOPF_H + ABSTAND;
                eingabe = new EditBox(font, x, y + 4, KNOPF_B, KNOPF_H, Component.translatable("stinkendreich.handy.auftrag.frei"));
                eingabe.setHint(Component.translatable("stinkendreich.handy.auftrag.frei"));
                eingabe.setMaxLength(120);
                addRenderableWidget(eingabe);
                y += KNOPF_H + 8;
                knopf(x, y, "auftrag.senden", () -> {
                    if (!eingabe.getValue().isBlank()) {
                        senden("auftrag|" + eingabe.getValue().trim());
                    }
                });
                knopf(x, oben() + HOEHE - 30, "zurueck", () -> wechseln(Seite.START));
            }
        }
    }

    /** Farben der Farbknöpfe in der Smart-Home-App (wie die Glasschirme der Lampen). */
    private static final java.util.Map<String, Integer> FARBEN = java.util.Map.of("weiss", 0xF4F4F4, "warm", 0xFFD58A,
            "rot", 0xFF3B30, "gruen", 0x3BE36A, "blau", 0x3B82FF, "lila", 0xA45BFF, "pink", 0xFF5BC8, "gold", 0xF2C230);

    private int knopf(int x, int y, String name, Runnable aktion) {
        return knopf(x, y, name, aktion, KNOPF_H);
    }

    private int knopf(int x, int y, String name, Runnable aktion, int hoehe) {
        addRenderableWidget(Button.builder(Component.translatable("stinkendreich.handy." + name), b -> aktion.run())
                .bounds(x, y, KNOPF_B, hoehe).build());
        return y + hoehe + ABSTAND;
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void wechseln(Seite neu) {
        seite = neu;
        eingabe = null;
        if (neu == Seite.ORTE) {
            anfragen("orte");
        }
        if (neu == Seite.SMART || neu == Seite.SICHERHEIT) {
            anfragen("smart|status");
        }
        if (neu == Seite.GAESTE) {
            anfragen("gaeste");
        }
        rebuildWidgets();
    }

    /** Schickt eine Aktion an den Server, ohne das Handy zu schließen (die Antwort kommt als neue Ortsliste). */
    private void anfragen(String wert) {
        Minecraft mc = Minecraft.getInstance();
        KompatClient.sende("handy", mc.player != null ? mc.player.blockPosition() : BlockPos.ZERO, wert);
    }

    /** Zustand der Smart-Home-Schalter vom Server („rollo=1,licht=0,…“); ein offenes Handy zeigt ihn sofort. */
    public static void smartEmpfangen(String werte) {
        for (String teil : werte.split(",")) {
            String[] kv = teil.split("=", 2);
            if (kv.length == 2) {
                smartZustand.put(kv[0], kv[1].equals("1"));
                smartWerte.put(kv[0], kv[1]);
            }
        }
        if (KompatClient.offenerBildschirm() instanceof HandyBildschirm h && (h.seite == Seite.SMART || h.seite == Seite.SICHERHEIT)) {
            h.rebuildWidgets();
        }
    }

    /** Neue Gästeliste vom Server (Namen zeilenweise); ein offenes Handy zeigt sie sofort. */
    public static void gaesteEmpfangen(String zeilen) {
        gaeste = zeilen.isBlank() ? List.of() : List.of(zeilen.split("\n"));
        if (KompatClient.offenerBildschirm() instanceof HandyBildschirm h && h.seite == Seite.GAESTE) {
            String text = h.eingabe != null ? h.eingabe.getValue() : "";
            h.rebuildWidgets();
            if (h.eingabe != null) {
                h.eingabe.setValue(text);
            }
        }
    }

    /** Neue Ortsliste vom Server (Namen zeilenweise); ein offenes Handy zeigt sie sofort. */
    public static void orteEmpfangen(String zeilen) {
        orte = zeilen.isBlank() ? List.of() : List.of(zeilen.split("\n"));
        if (KompatClient.offenerBildschirm() instanceof HandyBildschirm h && h.seite == Seite.ORTE) {
            String text = h.eingabe != null ? h.eingabe.getValue() : "";
            h.rebuildWidgets();
            if (h.eingabe != null) {
                h.eingabe.setValue(text);
            }
        }
    }

    /**
     * Navi-Ziel gewählt: Sitzt der Spieler schon in Limo oder Hubschrauber, bringt James ihn gleich damit hin; sonst
     * fragt das Handy, womit James ihn abholen soll.
     */
    private void reisen(String ziel) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getVehicle() instanceof de.moinmornhart.stinkendreich.auto.AutoEntity a) {
            senden((a.art.fliegt() ? "flieg|" : "fahr|") + ziel);
            return;
        }
        reiseZiel = ziel;
        wechseln(Seite.REISE);
    }

    /** Schickt die Aktion an den Server und schließt das Handy (wie Auflegen). */
    private void senden(String wert) {
        Minecraft mc = Minecraft.getInstance();
        BlockPos pos = mc.player != null ? mc.player.blockPosition() : BlockPos.ZERO;
        KompatClient.sende("handy", pos, wert);
        onClose();
    }

    // ------------------------------------------------------------------
    // Zeichnen: goldener Rahmen, dunkler Bildschirm, Statusleiste mit Uhrzeit
    // ------------------------------------------------------------------

    // Das Gehäuse gehört zum Hintergrund (sonst läge die Abdunklung darüber), die Schrift kommt nach den Knöpfen
    //? if >=26.1 {
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractBackground(g, mx, my, delta);
        gehaeuse(g);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        beschriftung(g);
    }

    private void gehaeuse(GuiGraphicsExtractor g) {
    //?} else {
    /*@Override
    public void renderBackground(GuiGraphics g, int mx, int my, float delta) {
        super.renderBackground(g, mx, my, delta);
        gehaeuse(g);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        super.render(g, mx, my, delta);
        beschriftung(g);
    }

    private void gehaeuse(GuiGraphics g) {
    *///?}
        int x = links(), y = oben();
        g.fill(x - 3, y - 3, x + BREITE + 3, y + HOEHE + 3, 0xFFB8891E);          // Goldrahmen
        g.fill(x - 2, y - 2, x + BREITE + 2, y + HOEHE + 2, 0xFFE8BA34);
        g.fill(x, y, x + BREITE, y + HOEHE, 0xFF0E0F14);                          // Gehäuse
        g.fill(x + 5, y + 12, x + BREITE - 5, y + HOEHE - 12, 0xFF1B2033);        // Bildschirm
        g.fill(x + 5, y + 12, x + BREITE - 5, y + 24, 0xFF11131D);                // Statusleiste
        g.fill(x + BREITE / 2 - 16, y + 4, x + BREITE / 2 + 16, y + 7, 0xFF2A2C35);    // Lautsprecher
        g.fill(x + BREITE / 2 - 12, y + HOEHE - 8, x + BREITE / 2 + 12, y + HOEHE - 6, 0xFF3A3C45); // Leiste unten
    }

    //? if >=26.1 {
    private void beschriftung(GuiGraphicsExtractor g) {
    //?} else
    /*private void beschriftung(GuiGraphics g) {*/
        int x = links(), y = oben();
        schrift(g, uhrzeit(), x + 10, y + 14, 0xFFFFFFFF, false);
        schrift(g, "▮▮▮ 100%", x + BREITE - 10 - font.width("▮▮▮ 100%"), y + 14, 0xFF8BE38B, false);
        String titel = Component.translatable("stinkendreich.handy.titel." + seite.name().toLowerCase(java.util.Locale.ROOT)).getString();
        schrift(g, titel, x + BREITE / 2, y + 28, 0xFFE8BA34, true);
        if (seite == Seite.ORTE && orte.isEmpty()) {
            schrift(g, Component.translatable("stinkendreich.handy.orte.leer").getString(), x + BREITE / 2, y + 46, 0xFFB8BCC8, true);
        }
        if (seite == Seite.REISE) {
            schrift(g, Component.translatable("stinkendreich.handy.reise.hinweis").getString(), x + BREITE / 2, y + 46, 0xFFB8BCC8, true);
        }
        if (seite == Seite.GAESTE && gaeste.isEmpty()) {
            schrift(g, Component.translatable("stinkendreich.handy.gaeste.leer").getString(), x + BREITE / 2, y + 46, 0xFFB8BCC8, true);
        }
    }

    /** Uhrzeit der Spielwelt (6000 Ticks = 12:00 Uhr). */
    private static String uhrzeit() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return "";
        }
        //? if >=26.1 {
        long t = (mc.level.getDefaultClockTime() + 6000) % 24000;
        //?} else
        /*long t = (mc.level.getDayTime() + 6000) % 24000;*/
        return String.format("%02d:%02d", t / 1000, (t % 1000) * 60 / 1000);
    }

    //? if >=26.1 {
    private void schrift(GuiGraphicsExtractor g, String text, int x, int y, int farbe, boolean mittig) {
        g.text(font, text, mittig ? x - font.width(text) / 2 : x, y, farbe, false);
    }
    //?} else {
    /*private void schrift(GuiGraphics g, String text, int x, int y, int farbe, boolean mittig) {
        g.drawString(font, text, mittig ? x - font.width(text) / 2 : x, y, farbe, false);
    }
    *///?}
}
