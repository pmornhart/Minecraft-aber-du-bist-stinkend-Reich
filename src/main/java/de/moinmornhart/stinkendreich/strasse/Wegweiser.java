package de.moinmornhart.stinkendreich.strasse;

import com.google.gson.JsonObject;
import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.StinkendReich;
import de.moinmornhart.stinkendreich.mechanik.Zugbruecke;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Wegweiser an den Straßen (Philip: „Schilder, wo dann steht, dass es da lang zur Mine geht – so richtig geile
 * Straßenschilder mit Holzpfeilern“). Ein großer Wegweiser steht an der Zufahrt vor dem Kreisel und zeigt zu allen
 * Straßen, die dort abgehen; an jedem Abzweig steht einer mit der neuen Straße, der weiterführenden und dem Heimweg.
 *
 * <p>Die Wegweiser bestehen aus Anzeige-Figuren ({@link WegweiserForm}) und werden gesetzt, sobald ihr Chunk mitsamt
 * Figuren geladen ist. Gemerkt wird je Wegweiser, mit welchen Schildern er steht: Kommt eine Straße dazu, wird er neu
 * aufgestellt; fällt er weg (Straßen neu gebaut), wird er abgebaut.</p>
 */
final class Wegweiser {
    static final String TAG = StinkendReich.MOD_ID + "_wegweiser";
    /** Abstand des Pfahls von der Mittellinie (außerhalb von Bordstein und Seitenstreifen) und vor dem Abzweig. */
    private static final double SEITE = 6.5, DAVOR = 4;
    /** Abzweige, deren Wegweiser näher als das beieinander stünden, teilen sich einen. */
    private static final double ZUSAMMEN = 12;

    /** Ein geplanter Wegweiser: Kennung (auch Marke der Figuren), Fußpunkt, Höhe der Straße daneben, Schilder. */
    private record Pfahl(String id, double x, double z, int strassenY, List<WegweiserForm.Schild> schilder) {
        String signatur() {
            StringBuilder sb = new StringBuilder("f" + WegweiserForm.FORM + ";");
            schilder.forEach(s -> sb.append(s.schluessel()).append(':').append(s.text()).append('@').append(Math.round(Math.toDegrees(Math.atan2(s.dz(), s.dx())))).append(';'));
            return sb.toString();
        }
    }

    /** Aufgestellte Wegweiser: Kennung → Signatur der Schilder. */
    private final Map<String, String> gesetzt = new HashMap<>();
    private List<Pfahl> plan = List.of();
    private int planFuer = -1;

    /** Alle 40 Ticks: Plan an die Straßen anpassen, fehlende oder veraltete Wegweiser setzen, verwaiste abbauen. */
    boolean tick(ServerLevel welt, List<Strassennetz.Strasse> strassen, Vec3 kreisel, String heim) {
        if (strassen.size() != planFuer) {
            plan = planen(strassen, kreisel, heim);
            planFuer = strassen.size();
        }
        boolean geaendert = false;
        Map<String, Pfahl> soll = new HashMap<>();
        for (Pfahl p : plan) {
            soll.put(p.id(), p);
            BlockPos fuss = BlockPos.containing(p.x(), p.strassenY(), p.z());
            if (!p.signatur().equals(gesetzt.get(p.id())) && Kompat.ganzGeladen(welt, fuss)) {
                aufstellen(welt, p);
                gesetzt.put(p.id(), p.signatur());
                geaendert = true;
            }
        }
        for (var it = gesetzt.keySet().iterator(); it.hasNext(); ) {
            String id = it.next();
            if (!soll.containsKey(id)) {
                String[] xz = id.substring(4).split("_(?=-?\\d+$)");
                if (xz.length == 2 && Kompat.ganzGeladen(welt, new BlockPos(Integer.parseInt(xz[0]), 64, Integer.parseInt(xz[1])))) {
                    abbauen(welt, id);
                    it.remove();
                    geaendert = true;
                }
            }
        }
        return geaendert;
    }

    /**
     * Testhilfe (/butler wegweiser &lt;nr&gt;): Standort zum Anschauen des Wegweisers {@code nr} – x, y, z des Betrachters und
     * der Punkt, auf den er schaut –, oder null. Der Betrachter steht schräg hinter dem Pfahl, etwas erhöht
     * (über Büschen); der Blickpunkt ist um die Augenhöhe gesenkt, weil {@code tp … facing} von den Füßen aus rechnet.
     */
    double[] blick(ServerLevel welt, int nr) {
        if (nr < 0 || nr >= plan.size()) {
            return null;
        }
        Pfahl p = plan.get(nr);
        WegweiserForm.Schild s = p.schilder().get(0);
        double l = Math.hypot(s.dx(), s.dz()), dx = s.dx() / l, dz = s.dz() / l;
        double bx = p.x() - dx * 4 - dz * 4, bz = p.z() - dz * 4 + dx * 4;
        int boden = welt.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(p.x()), (int) Math.floor(p.z()));
        int y = Math.abs(boden - (p.strassenY() + 1)) <= 4 ? boden : p.strassenY() + 1;
        return new double[]{bx, y + 2.5, bz, p.x() + dx * 1.2, y + 2.4 - 1.62, p.z() + dz * 1.2};
    }

    /** Alle Wegweiser vergessen (Straßen werden neu gebaut); geladene werden sofort abgebaut, die übrigen beim Laden. */
    void neuPlanen() {
        planFuer = -1;
    }

    // ---------------- Planen ----------------

    private static List<Pfahl> planen(List<Strassennetz.Strasse> strassen, Vec3 kreisel, String heim) {
        Map<String, List<WegweiserForm.Schild>> schilder = new LinkedHashMap<>();
        List<double[]> orte = new ArrayList<>();          // x, z, Straßenhöhe je Kennung (gleiche Reihenfolge)
        List<String> ids = new ArrayList<>();
        // Großer Wegweiser an der Zufahrt, kurz vor dem Kreisel (östlich der Fahrbahn)
        if (kreisel != null) {
            double px = kreisel.x + 5.5, pz = kreisel.z - Strassennetz.AUSSEN - 2.5;
            String id = kennung(px, pz);
            List<WegweiserForm.Schild> liste = new ArrayList<>();
            for (Strassennetz.Strasse s : strassen) {
                if (s.eltern() < 0 && !s.punkte().isEmpty()) {
                    BlockPos q = s.punkte().get(Math.min(10, s.punkte().size() - 1));
                    hinzu(liste, schild(anzeigeName(s.name()), laenge(s, 0) + Strassennetz.AUSSEN, q.getX() + 0.5 - px, q.getZ() + 0.5 - pz));
                }
            }
            if (!liste.isEmpty()) {
                schilder.put(id, liste);
                orte.add(new double[]{px, pz, kreisel.y});
                ids.add(id);
            }
        }
        // Je Abzweig einer: neue Straße, weiter geradeaus, zurück nach Hause
        for (Strassennetz.Strasse s : strassen) {
            if (s.eltern() < 0 || s.eltern() >= strassen.size() || s.punkte().size() < 2) {
                continue;
            }
            Strassennetz.Strasse e = strassen.get(s.eltern());
            List<BlockPos> ep = e.punkte();
            if (ep.size() < 2) {
                continue;
            }
            int i = Math.max(0, Math.min(s.abzweig(), ep.size() - 1));
            BlockPos j = ep.get(i);
            BlockPos vor = ep.get(Math.min(i + 3, ep.size() - 1)), nach = ep.get(Math.max(i - 3, 0));
            double tx = vor.getX() - nach.getX(), tz = vor.getZ() - nach.getZ(), tl = Math.hypot(tx, tz);
            if (tl < 1e-6) {
                continue;
            }
            tx /= tl;
            tz /= tl;
            BlockPos b = s.punkte().get(Math.min(8, s.punkte().size() - 1));
            double bx = b.getX() - j.getX(), bz = b.getZ() - j.getZ();
            // Auf die Seite gegenüber der neuen Straße, ein Stück vor den Abzweig (aus Richtung Villa gesehen)
            double nx = -tz, nz = tx, seite = bx * nx + bz * nz >= 0 ? 1 : -1;
            double px = j.getX() + 0.5 - seite * nx * SEITE - tx * DAVOR, pz = j.getZ() + 0.5 - seite * nz * SEITE - tz * DAVOR;
            String id = null;
            for (int k = 0; k < ids.size(); k++) {
                if (Math.hypot(orte.get(k)[0] - px, orte.get(k)[1] - pz) < ZUSAMMEN) {
                    id = ids.get(k);
                    break;
                }
            }
            if (id == null) {
                id = kennung(px, pz);
                schilder.put(id, new ArrayList<>());
                orte.add(new double[]{px, pz, j.getY()});
                ids.add(id);
            }
            List<WegweiserForm.Schild> liste = schilder.get(id);
            hinzu(liste, schild(anzeigeName(s.name()), laenge(s, 0), bx, bz));
            if (i + 8 < ep.size()) {
                BlockPos w = ep.get(i + 8);
                hinzu(liste, schild(anzeigeName(e.name()), laenge(e, i), w.getX() - j.getX(), w.getZ() - j.getZ()));
            }
            if (heim != null) {
                BlockPos h = ep.get(Math.max(i - 8, 0));
                hinzu(liste, schild(heim, bisKreisel(strassen, s.eltern(), i, 0), h.getX() - j.getX(), h.getZ() - j.getZ()));
            }
        }
        List<Pfahl> plan = new ArrayList<>();
        for (int k = 0; k < ids.size(); k++) {
            List<WegweiserForm.Schild> liste = schilder.get(ids.get(k));
            plan.add(new Pfahl(ids.get(k), orte.get(k)[0], orte.get(k)[1], (int) Math.floor(orte.get(k)[2]),
                    List.copyOf(WegweiserForm.entzerren(liste, 40))));
        }
        return plan;
    }

    /** Schild anfügen, außer es gibt schon eins zum selben Ziel. */
    private static void hinzu(List<WegweiserForm.Schild> liste, WegweiserForm.Schild s) {
        if (liste.stream().noneMatch(a -> a.ziel().equals(s.ziel()))) {
            liste.add(s);
        }
    }

    /** Feste Ziele: Kennung und alle Namen, unter denen eine Straße dorthin heißen kann (deutsch und englisch). */
    private static final String[][] FESTE = {
            {"dorf", "DORF", "dorf", "the village"},
            {"mine", "MINE", "mine", "the mine"},
            {"netherportal", "NETHERPORTAL", "netherportal", "the nether portal"},
            {"endportal", "ENDPORTAL", "endportal", "the end portal"}};
    private static final java.util.regex.Pattern VILLA_VON = java.util.regex.Pattern.compile("(?i)villa von (.+)|(.+)'s villa");
    private static final java.util.regex.Pattern VILLA_NR = java.util.regex.Pattern.compile("(?i)villa (\\d+)");

    /** Festes Ziel („dorf“, „mine“, „netherportal“, „endportal“) zu einem Straßennamen, sonst null. */
    static String festesZiel(String name) {
        for (String[] f : FESTE) {
            for (int i = 2; i < f.length; i++) {
                if (name.trim().equalsIgnoreCase(f[i])) {
                    return f[0];
                }
            }
        }
        return null;
    }

    /**
     * Schild zu einem Straßennamen. Die Namen der Straßen entstehen auf dem Server und sind deshalb oft englisch („the
     * village“); auf dem Schild steht stattdessen ein Übersetzungsschlüssel, den jeder Spieler in seiner Sprache sieht.
     */
    static WegweiserForm.Schild schild(String name, double meter, double dx, double dz) {
        String n = name.trim(), weite = WegweiserForm.weite(meter);
        for (String[] f : FESTE) {
            for (int i = 2; i < f.length; i++) {
                if (n.equalsIgnoreCase(f[i])) {
                    return new WegweiserForm.Schild(f[1], "stinkendreich.wegweiser." + f[0], null, weite, dx, dz);
                }
            }
        }
        var m = VILLA_NR.matcher(n);
        if (m.matches()) {
            return new WegweiserForm.Schild("VILLA " + m.group(1), "stinkendreich.wegweiser.villa_nr", m.group(1), weite, dx, dz);
        }
        m = VILLA_VON.matcher(n);
        if (m.matches()) {
            String herr = (m.group(1) != null ? m.group(1) : m.group(2)).toUpperCase(Locale.ROOT);
            return new WegweiserForm.Schild("VILLA VON " + herr, "stinkendreich.wegweiser.villa", herr, weite, dx, dz);
        }
        if (n.equalsIgnoreCase("villa")) {
            return new WegweiserForm.Schild("VILLA", "stinkendreich.wegweiser.villa_heim", null, weite, dx, dz);
        }
        return new WegweiserForm.Schild(n.toUpperCase(Locale.GERMAN), null, null, weite, dx, dz);
    }

    /** Name auf dem Schild: Zubringer führen zur selben Villa wie ihre Straße. */
    private static String anzeigeName(String name) {
        int z = name.indexOf(" – ");
        return z > 0 ? name.substring(0, z) : name;
    }

    private static String kennung(double x, double z) {
        return String.format(Locale.ROOT, "srw_%d_%d", (int) Math.floor(x), (int) Math.floor(z));
    }

    /** Länge der Straße ab Punkt {@code ab} bis zu ihrem Ende (Blöcke). */
    private static double laenge(Strassennetz.Strasse s, int ab) {
        double l = 0;
        for (int i = Math.max(ab, 0) + 1; i < s.punkte().size(); i++) {
            l += Math.sqrt(s.punkte().get(i).distSqr(s.punkte().get(i - 1)));
        }
        return l;
    }

    /** Weg von Punkt {@code bis} der Straße {@code nr} zurück bis zum Kreisel (über die Eltern-Straßen). */
    private static double bisKreisel(List<Strassennetz.Strasse> strassen, int nr, int bis, int tiefe) {
        if (nr < 0 || nr >= strassen.size() || tiefe > 16) {
            return Strassennetz.AUSSEN;
        }
        Strassennetz.Strasse s = strassen.get(nr);
        double l = 0;
        for (int i = 1; i <= Math.min(bis, s.punkte().size() - 1); i++) {
            l += Math.sqrt(s.punkte().get(i).distSqr(s.punkte().get(i - 1)));
        }
        return l + bisKreisel(strassen, s.eltern(), s.abzweig(), tiefe + 1);
    }

    // ---------------- Aufstellen ----------------

    private static void aufstellen(ServerLevel welt, Pfahl p) {
        abbauen(welt, p.id());
        // Auf den Boden neben der Straße; ist der viel höher oder tiefer (Böschung, Wasser), auf Höhe der Straße
        int boden = welt.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(p.x()), (int) Math.floor(p.z()));
        int y = Math.abs(boden - (p.strassenY() + 1)) <= 4 ? boden : p.strassenY() + 1;
        for (WegweiserForm.Teil t : WegweiserForm.teile(p.schilder())) {
            String befehl = t.text() == null
                    ? String.format(Locale.ROOT, "summon minecraft:block_display %.3f %d %.3f {Tags:[\"%s\",\"%s\"],block_state:%s,view_range:1.5f,transformation:%s}",
                    p.x(), y, p.z(), TAG, p.id(), Zugbruecke.anzeigeZustand(t.block()), t.snbt())
                    : String.format(Locale.ROOT, "summon minecraft:text_display %.3f %d %.3f {Tags:[\"%s\",\"%s\"],text:%s,background:0,shadow:1b,"
                    + "line_width:1000,alignment:\"center\",view_range:1.0f,transformation:%s}", p.x(), y, p.z(), TAG, p.id(), text(t.text()), t.snbt());
            Kompat.befehl(welt.getServer(), befehl);
        }
        StinkendReich.LOGGER.info("Straßennetz: Wegweiser bei {} {} {} ({} Schilder)", (int) Math.floor(p.x()), y, (int) Math.floor(p.z()), p.schilder().size());
    }

    private static void abbauen(ServerLevel welt, String id) {
        Kompat.befehl(welt.getServer(), "kill @e[tag=" + TAG + ",tag=" + id + "]");
    }

    /**
     * Aufschrift als Text-Baustein: Ziel übersetzbar (mit deutschem Ersatz für Spieler ohne Mod), dahinter die Entfernung.
     * Ab 1.21.5 als NBT, davor als JSON in einer Zeichenkette.
     */
    private static String text(WegweiserForm.Schild s) {
        String ziel = sicher(s.ziel()), rest = sicher("  ·  " + s.weite()), farbe = WegweiserForm.SCHRIFTFARBE;
        //? if >=1.21.5 {
        String kopf = s.schluessel() == null ? "text:\"" + ziel + "\""
                : "translate:\"" + s.schluessel() + "\",fallback:\"" + ziel + "\"" + (s.wert() == null ? "" : ",with:[\"" + sicher(s.wert()) + "\"]");
        return "{" + kopf + ",color:\"" + farbe + "\",bold:true,extra:[{text:\"" + rest + "\"}]}";
        //?} else {
        /*String kopf = s.schluessel() == null ? "\"text\":\"" + ziel + "\""
                : "\"translate\":\"" + s.schluessel() + "\",\"fallback\":\"" + ziel + "\"" + (s.wert() == null ? "" : ",\"with\":[\"" + sicher(s.wert()) + "\"]");
        return "'{" + kopf.replace("'", "\\'") + ",\"color\":\"" + farbe + "\",\"bold\":true,\"extra\":[{\"text\":\"" + rest + "\"}]}'";
        *///?}
    }

    private static String sicher(String inhalt) {
        return inhalt.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // ---------------- Speichern ----------------

    JsonObject speichern() {
        JsonObject o = new JsonObject();
        gesetzt.forEach(o::addProperty);
        return o;
    }

    void laden(JsonObject o) {
        gesetzt.clear();
        o.entrySet().forEach(e -> gesetzt.put(e.getKey(), e.getValue().getAsString()));
    }
}
