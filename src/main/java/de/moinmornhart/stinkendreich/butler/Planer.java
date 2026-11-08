package de.moinmornhart.stinkendreich.butler;

import de.moinmornhart.stinkendreich.butler.auftrag.Auftrag;
import de.moinmornhart.stinkendreich.butler.auftrag.HerstellAuftrag;
import de.moinmornhart.stinkendreich.butler.auftrag.HolzAuftrag;
import de.moinmornhart.stinkendreich.butler.auftrag.JagdAuftrag;
import de.moinmornhart.stinkendreich.butler.auftrag.LieferAuftrag;
import de.moinmornhart.stinkendreich.butler.auftrag.OfenAuftrag;
import de.moinmornhart.stinkendreich.butler.auftrag.SammelAuftrag;
import de.moinmornhart.stinkendreich.butler.auftrag.VorratAuftrag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Zerlegt eine Bestellung („32 Eisenbarren“) in Arbeitsschritte: was fehlt, wird abgebaut,
 * geschmolzen oder hergestellt – auch über mehrere Stufen (Fackeln brauchen Stöcke und Kohle,
 * Stöcke brauchen Bretter, Bretter brauchen Holz). Was der Butler schon in den Taschen hat,
 * wird mitgezählt.
 */
public final class Planer {
    /** Höchstmenge je Bestellung (passt sicher in die Taschen). */
    public static final int MAX_MENGE = 64 * 12;

    private final ButlerEntity butler;
    private final List<Auftrag> plan = new ArrayList<>();
    /** Verfügbare Menge je Quelle nach den bisher geplanten Schritten. */
    private final Map<String, Integer> bestand = new HashMap<>();
    private Component fehler;

    private Planer(ButlerEntity butler) {
        this.butler = butler;
    }

    /** Ergebnis der Planung: Schritte oder ein Grund, warum es nicht geht. */
    public record Plan(List<Auftrag> schritte, Component fehler) {
        public boolean ok() {
            return fehler == null;
        }
    }

    /** Plant „besorge {@code menge} × {@code quelle} und liefere sie“. */
    public static Plan bestellung(ButlerEntity butler, String quelle, int menge, boolean inTruhe) {
        Planer p = new Planer(butler);
        menge = Math.max(1, Math.min(MAX_MENGE, menge));
        if (!p.besorgen(quelle, menge, 0)) {
            return new Plan(List.of(), p.fehler);
        }
        p.plan.add(new LieferAuftrag(quelle, menge, inTruhe));
        return new Plan(p.plan, null);
    }

    /** Plant „brate alles Rohe aus den Taschen (sonst erst jagen) und liefere es“. */
    public static Plan kochen(ButlerEntity butler, int menge, boolean inTruhe) {
        Planer p = new Planer(butler);
        int roh = butler.taschen().zaehlen(Schmelzen::istRohesEssen);
        int n = roh > 0 ? Math.min(roh, MAX_MENGE) : Math.max(1, menge);
        if (roh == 0) {
            p.plan.add(new JagdAuftrag(n));
        }
        p.bestand.put("rohes_fleisch", n);
        if (!p.brennstoffSichern(n)) {
            return new Plan(List.of(), p.fehler);
        }
        p.plan.add(new OfenAuftrag("rohes_fleisch", n));
        p.plan.add(new LieferAuftrag("essen", -1, inTruhe));
        return new Plan(p.plan, null);
    }

    private int verfuegbar(String quelle) {
        return bestand.computeIfAbsent(quelle, q -> butler.taschen().zaehlen(Quellen.get(q).passt()));
    }

    private void verbrauchen(String quelle, int menge) {
        bestand.put(quelle, verfuegbar(quelle) - menge);
    }

    private void hinzu(String quelle, int menge) {
        bestand.put(quelle, verfuegbar(quelle) + menge);
    }

    /** Sorgt dafür, dass {@code menge} Stück der Quelle verfügbar sind, und reserviert sie. */
    private boolean besorgen(String quelleId, int menge, int tiefe) {
        Quellen.Quelle q = Quellen.get(quelleId);
        if (q == null || tiefe > 8) {
            fehler = Component.translatable("stinkendreich.butler.unbekannt", quelleId);
            return false;
        }
        int fehlt = menge - verfuegbar(quelleId);
        if (fehlt > 0) {
            switch (q.weg()) {
                case ABBAUEN -> {
                    Rohstoffe.Rohstoff r = Rohstoffe.get(q.rohstoff());
                    plan.add(r.art() == Rohstoffe.Art.HOLZ ? new HolzAuftrag(fehlt) : new SammelAuftrag(r.id(), fehlt));
                    hinzu(quelleId, fehlt);
                }
                case SCHMELZEN -> {
                    if (!besorgen(q.eingang(), fehlt, tiefe + 1) || !brennstoffSichern(fehlt)) {
                        return false;
                    }
                    plan.add(new OfenAuftrag(q.eingang(), fehlt));
                    hinzu(quelleId, fehlt);
                }
                case HERSTELLEN -> {
                    int mal = (fehlt + q.menge() - 1) / q.menge();
                    for (Quellen.Zutat z : q.zutaten()) {
                        if (!besorgen(z.quelle(), z.anzahl() * mal, tiefe + 1)) {
                            return false;
                        }
                    }
                    plan.add(new HerstellAuftrag(quelleId, mal));
                    hinzu(quelleId, mal * q.menge());
                }
                case JAGEN -> {
                    plan.add(new JagdAuftrag(fehlt));
                    hinzu(quelleId, fehlt);
                }
                case ESSEN -> {
                    // Erst die Speisekammer, den Rest jagen und braten
                    int vorrat = Math.min(fehlt, VorratAuftrag.imVorrat(butler, quelleId));
                    if (vorrat > 0) {
                        plan.add(new VorratAuftrag(quelleId, vorrat));
                        hinzu(quelleId, vorrat);
                    }
                    int rest = fehlt - vorrat;
                    if (rest > 0) {
                        if (!besorgen("rohes_fleisch", rest, tiefe + 1) || !brennstoffSichern(rest)) {
                            return false;
                        }
                        plan.add(new OfenAuftrag("rohes_fleisch", rest));
                        hinzu(quelleId, rest);
                    }
                }
                case KEINER -> {
                    fehler = Component.translatable("stinkendreich.butler.kannnicht", q.anzeigeStapel().getHoverName());
                    return false;
                }
            }
        }
        verbrauchen(quelleId, menge);
        return true;
    }

    /**
     * Brennstoff für {@code stueck} Schmelzvorgänge: Kohle aus den Taschen oder dem Vorrat der Villa,
     * sonst wird Kohle abgebaut.
     */
    private boolean brennstoffSichern(int stueck) {
        int brauche = (stueck + 7) / 8;
        int imVorrat = 0;
        for (var p : Umgebung.villaMarken(butler, "brennstoff")) {
            if (butler.level().getBlockEntity(p) instanceof Container c) {
                imVorrat += Taschen.zaehlen(c, s -> s.is(Items.COAL) || s.is(Items.CHARCOAL));
            }
        }
        int kohle = verfuegbar("kohle") + Math.max(0, imVorrat - bestand.getOrDefault("_vorratKohle", 0));
        if (kohle >= brauche) {
            int ausTaschen = Math.min(brauche, verfuegbar("kohle"));
            verbrauchen("kohle", ausTaschen);
            bestand.merge("_vorratKohle", brauche - ausTaschen, Integer::sum);
            return true;
        }
        return besorgen("kohle", brauche - Math.max(0, imVorrat - bestand.getOrDefault("_vorratKohle", 0)), 1);
    }
}
