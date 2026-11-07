package de.moinmornhart.stinkendreich.butler;

import de.moinmornhart.stinkendreich.Konfig;
import de.moinmornhart.stinkendreich.auto.AutoEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Der Butler am Steuer: lenkt ein Auto ohne Wegnetz zum Ziel. Alle paar Ticks tastet er vor dem Wagen
 * mehrere Richtungen ab (Stufen bis einen Block, kein Wasser, keine Abgründe, so breit wie der Wagen) und
 * wählt die freie Richtung, die am besten zum Ziel passt. Das Tempo richtet sich nach der freien Strecke,
 * der Kurve und dem Abstand zum Ziel. Steckt der Wagen fest, setzt er zurück und versucht es anders.
 *
 * <p>Läuft nur auf dem Server; gibt seine Eingaben über {@link AutoEntity#chauffeurEingabe} an das Auto.
 * Zwischenziele (z. B. vor der Brücke und hinter dem Tor der Villa) durchfährt er ohne anzuhalten. Liegen die
 * Wegpunkte dicht (eine Straße aus dem Straßennetz), fährt er wie auf Schienen: Er zielt auf einen Punkt einige
 * Blöcke voraus auf der Linie, schneidet so keine Kurven und bremst vor engen Biegungen.</p>
 */
public final class Chauffeur {
    /** Wie weit er vorausschaut (Blöcke). */
    private static final int SICHT = 20;
    /** Abweichungen von der Zielrichtung, die er prüft (Grad). */
    private static final int[] RICHTUNGEN = {0, 12, -12, 25, -25, 40, -40, 55, -55, 75, -75, 100, -100, 135, -135};
    private static final int KEIN = Integer.MIN_VALUE;
    /** Wegpunkte mit weniger Abstand gelten als Straße (Spur fahren). */
    private static final double DICHT = 5.0;
    /** So weit voraus auf der Spur zielt er (Blöcke). */
    private static final double VORAUS = 6.0;
    /** Verzögerung, mit der James vor Kurven bremst (Blöcke/Tick²; Vollbremsung des Wagens: 0,06). */
    private static final double BREMSEN = 0.03;

    /** Zwischenziele und am Ende das Ziel. */
    private final java.util.List<Vec3> punkte;
    private int punkt;
    private final double ankunft;
    private float gewaehlteRichtung = Float.NaN;
    private int ticks, rueckwaerts;
    private Vec3 letztePos;
    private double besterAbstand = Double.MAX_VALUE;
    private int ohneFortschritt;
    /** Wie oft er den Wagen schon auf die Straße zurückgesetzt hat. */
    private int rettungen;

    public enum Lage { FAEHRT, DA, FESTGEFAHREN }

    /**
     * @param punkte  Zwischenziele und zuletzt das Ziel
     * @param ankunft ab diesem Abstand (Blöcke, waagerecht) zum Ziel gilt er als angekommen
     */
    public Chauffeur(java.util.List<Vec3> punkte, double ankunft) {
        this.punkte = java.util.List.copyOf(punkte);
        this.ankunft = ankunft;
    }

    /** Der Wegpunkt, auf den er gerade zufährt (für Meldungen). */
    public Vec3 naechsterPunkt() {
        return punkte.get(punkt);
    }

    /** Ein Tick am Steuer. */
    public Lage fahren(AutoEntity auto) {
        ticks++;
        Vec3 pos = auto.position();
        // Auf der Spur: Punkte, an denen er schon vorbei ist (der nächste liegt näher), überspringen
        while (punkt < punkte.size() - 1 && dicht(punkt) && waagerecht(pos, punkte.get(punkt + 1)) < waagerecht(pos, punkte.get(punkt))) {
            punkt++;
            besterAbstand = Double.MAX_VALUE;
            ohneFortschritt = 0;
        }
        Vec3 ziel = punkte.get(punkt);
        double dx = ziel.x - pos.x, dz = ziel.z - pos.z;
        double abstand = Math.sqrt(dx * dx + dz * dz);
        boolean letzter = punkt == punkte.size() - 1;
        if (!letzter && abstand < 3.5) {
            punkt++;
            besterAbstand = Double.MAX_VALUE;
            return Lage.FAEHRT;
        }
        if (letzter && abstand < ankunft) {
            // Ausrollen und stehen bleiben
            auto.chauffeurEingabe(auto.tempo() > 0.03 ? -1 : 0, 0);
            return Math.abs(auto.tempo()) < 0.03 ? Lage.DA : Lage.FAEHRT;
        }

        // Fortschritt: in 40 Sekunden keine 3 Blöcke näher → er gibt auf
        if (abstand < besterAbstand - 3) {
            besterAbstand = abstand;
            ohneFortschritt = 0;
        } else if (++ohneFortschritt > 800) {
            auto.chauffeurEingabe(0, 0);
            return Lage.FESTGEFAHREN;
        } else if (ohneFortschritt == 300 && dicht(punkt) && rettungen < 5) {
            // Auf der Straße festgefahren (etwa vom Rand abgerutscht): Wagen samt Fahrgästen zurück auf die Fahrbahn
            zurueckAufDieStrasse(auto);
            return Lage.FAEHRT;
        }

        // Steckt er fest? Dann ein Stück zurück und dabei zur anderen Seite einschlagen
        if (ticks % 30 == 0) {
            if (letztePos != null && letztePos.distanceTo(pos) < 0.6 && rueckwaerts == 0) {
                rueckwaerts = 30;
                gewaehlteRichtung = Float.NaN;
            }
            letztePos = pos;
        }
        // Auf der Spur einige Blöcke vorausschauen und dorthin zielen
        Vec3 voraus = vorausPunkt(pos, VORAUS);
        float zielRichtung = (float) (Mth.atan2(voraus.z - pos.z, voraus.x - pos.x) * Mth.RAD_TO_DEG) - 90.0F;
        double vorausAbstand = waagerecht(pos, voraus);
        if (rueckwaerts > 0) {
            rueckwaerts--;
            float fehler = Mth.wrapDegrees(zielRichtung - auto.getYRot());
            auto.chauffeurEingabe(-1, Mth.clamp(fehler / 30.0F, -1, 1));
            return Lage.FAEHRT;
        }

        // Alle 5 Ticks neu planen: freieste Richtung nahe am Ziel
        double frei = SICHT;
        double geradeFrei = freieStrecke(auto, zielRichtung);
        if (geradeFrei >= Math.min(SICHT, vorausAbstand) - 1) {
            // Der Weg dorthin ist frei: genau hinfahren (auf der Straße immer)
            gewaehlteRichtung = zielRichtung;
            frei = geradeFrei;
        } else if (ticks % 5 == 1 || Float.isNaN(gewaehlteRichtung)) {
            double besteWertung = -1e9;
            float beste = zielRichtung;
            double besteFrei = 0;
            for (int abw : RICHTUNGEN) {
                float r = zielRichtung + abw;
                double f = freieStrecke(auto, r);
                double drehen = Math.abs(Mth.wrapDegrees(r - auto.getYRot()));
                double wertung = Math.min(f, Math.min(SICHT, vorausAbstand)) * 2.0 - Math.abs(abw) * 0.06 - drehen * 0.02;
                if (wertung > besteWertung) {
                    besteWertung = wertung;
                    beste = r;
                    besteFrei = f;
                }
            }
            gewaehlteRichtung = beste;
            frei = besteFrei;
        }

        float fehler = Mth.wrapDegrees(gewaehlteRichtung - auto.getYRot());
        // Lenkung: positiv = links (Gierwinkel nimmt ab)
        double lenk = Mth.clamp(-fehler / 28.0F, -1, 1);
        double max = Math.min(auto.hoechstTempo(), Konfig.get().chauffeurTempo / 72.0);
        double bremsweg = letzter ? abstand * 0.05 + 0.12 : Math.max(0.35, abstand * 0.05 + 0.2);
        double soll = Math.min(max, Math.min(frei * 0.09 + 0.1, bremsweg));
        if (Math.abs(fehler) > 45) {
            soll = Math.min(soll, 0.28);
        }
        // Vor Kurven auf der Spur rechtzeitig bremsen: Für Punkte in mehreren Entfernungen voraus gilt ein Kurventempo
        // (je schärfer, desto langsamer), und so schnell darf er jetzt sein, dass er bis dorthin mit sanftem Bremsen
        // (BREMSEN Blöcke/Tick²) darauf herunterkommt. Auf langen Geraden fährt James so bis zu 100 km/h.
        for (double d : new double[]{8, 14, 22, 32, 44}) {
            Vec3 weit = vorausPunkt(pos, d);
            double bis = waagerecht(pos, weit);
            if (bis < 3) {
                continue;
            }
            float kurve = Math.abs(Mth.wrapDegrees((float) (Mth.atan2(weit.z - pos.z, weit.x - pos.x) * Mth.RAD_TO_DEG) - 90.0F - auto.getYRot()));
            double vk = kurve > 60 ? 0.22 : kurve > 30 ? 0.34 : kurve > 15 ? 0.6 : kurve > 8 ? 0.95 : max;
            soll = Math.min(soll, Math.sqrt(vk * vk + 2 * BREMSEN * Math.max(0, bis - 4)));
        }
        soll = Math.max(0.12, soll);
        double gas = auto.tempo() < soll - 0.02 ? 1 : (auto.tempo() > soll + 0.08 ? -1 : 0);
        auto.chauffeurEingabe(gas, lenk);
        return Lage.FAEHRT;
    }

    /**
     * Setzt den Wagen auf den nächsten Wegpunkt, in Fahrtrichtung der Straße. Hängt er danach wieder fest, liegt das
     * Hindernis am Punkt selbst: dann jedes Mal ein Stück weiter vorn aufsetzen, und nie in Wasser oder Lava.
     */
    private void zurueckAufDieStrasse(AutoEntity auto) {
        rettungen++;
        int i = Math.min(punkt + (rettungen - 1) * 4, punkte.size() - 1);
        while (i < punkte.size() - 1 && imWasser(auto.level(), punkte.get(i))) {
            i++;
        }
        punkt = i;
        Vec3 p = punkte.get(punkt), q = punkte.get(Math.min(punkt + 1, punkte.size() - 1));
        float gier = (float) (Mth.atan2(q.z - p.z, q.x - p.x) * Mth.RAD_TO_DEG) - 90.0F;
        auto.setDeltaMovement(Vec3.ZERO);
        auto.chauffeurEingabe(0, 0);
        auto.setYRot(gier);
        auto.teleportTo(p.x, p.y + 0.1, p.z);
        rueckwaerts = 0;
        gewaehlteRichtung = Float.NaN;
        ohneFortschritt = 0;
        besterAbstand = Double.MAX_VALUE;
        de.moinmornhart.stinkendreich.StinkendReich.LOGGER.info("Chauffeur setzt den Wagen zurück auf die Straße bei {}", p);
    }

    /** Stünde ein Wagen an diesem Punkt in Wasser oder Lava? */
    private static boolean imWasser(Level welt, Vec3 p) {
        BlockPos fuss = BlockPos.containing(p);
        return !welt.getFluidState(fuss).isEmpty() || !welt.getFluidState(fuss.above()).isEmpty();
    }

    /** Liegt der nächste Wegpunkt nach {@code i} dicht dran (Straße)? */
    private boolean dicht(int i) {
        return i + 1 < punkte.size() && waagerecht(punkte.get(i), punkte.get(i + 1)) < DICHT;
    }

    /** Der erste Punkt ab dem aktuellen, der mindestens {@code weit} Blöcke entfernt ist (nur über dichte Punkte). */
    private Vec3 vorausPunkt(Vec3 pos, double weit) {
        int j = punkt;
        while (j < punkte.size() - 1 && dicht(j) && waagerecht(pos, punkte.get(j)) < weit) {
            j++;
        }
        return punkte.get(j);
    }

    private static double waagerecht(Vec3 a, Vec3 b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }

    /** Wie weit man in Richtung {@code grad} fahren kann (0 … SICHT Blöcke). */
    static double freieStrecke(AutoEntity auto, float grad) {
        Level welt = auto.level();
        double rx = -Mth.sin(grad * Mth.DEG_TO_RAD), rz = Mth.cos(grad * Mth.DEG_TO_RAD);
        double qx = rz, qz = -rx;    // quer dazu
        int hoehe = auto.getBbHeight() > 1.5F ? 2 : 1;
        int y = Mth.floor(auto.getY() + 0.1);
        Vec3 p = auto.position();
        for (int d = 1; d <= SICHT; d++) {
            double mx = p.x + rx * (d + 1.5), mz = p.z + rz * (d + 1.5);
            int neu = spalte(welt, Mth.floor(mx), Mth.floor(mz), y, hoehe);
            if (neu == KEIN) {
                return d - 1;
            }
            // Der Wagen ist gut zwei Blöcke breit: auch links und rechts muss Platz sein
            for (int seite = -1; seite <= 1; seite += 2) {
                int s = spalte(welt, Mth.floor(mx + qx * 1.1 * seite), Mth.floor(mz + qz * 1.1 * seite), neu, hoehe);
                if (s == KEIN || Math.abs(s - neu) > 1) {
                    return d - 1;
                }
            }
            y = neu;
        }
        return SICHT;
    }

    /**
     * Prüft eine Spalte: Wo steht der Wagen hier, wenn er von Höhe {@code y} kommt? Neue Höhe oder
     * {@link #KEIN}, wenn es nicht weitergeht (Wand, Stufe über einen Block, Wasser, Abgrund).
     */
    private static int spalte(Level welt, int x, int z, int y, int hoehe) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(x, y, z);
        if (!welt.isLoaded(m)) {
            return KEIN;
        }
        if (fest(welt, m)) {
            // Eine Stufe hoch, wenn darüber Platz ist
            for (int h = 1; h <= hoehe + 1; h++) {
                if (fest(welt, m.setY(y + h)) || nass(welt, m)) {
                    return KEIN;
                }
            }
            return y + 1;
        }
        for (int h = 0; h <= hoehe; h++) {
            if (fest(welt, m.setY(y + h)) || nass(welt, m)) {
                return KEIN;
            }
        }
        // Boden darunter suchen (höchstens drei Blöcke tief)
        for (int t = 1; t <= 3; t++) {
            m.setY(y - t);
            if (nass(welt, m)) {
                return KEIN;
            }
            if (fest(welt, m)) {
                return y - t + 1;
            }
        }
        return KEIN;
    }

    private static boolean fest(Level welt, BlockPos pos) {
        BlockState s = welt.getBlockState(pos);
        return !s.getCollisionShape(welt, pos).isEmpty();
    }

    private static boolean nass(Level welt, BlockPos pos) {
        return !welt.getFluidState(pos).isEmpty();
    }

    /**
     * Sucht in der Nähe von {@code mitte} einen Platz, an dem ein Wagen stehen kann (fester Boden, darüber
     * zwei Blöcke frei, auch links und rechts). Für „Wagen vorfahren lassen“.
     */
    public static BlockPos parkplatz(Level welt, BlockPos mitte, int vonRadius, int bisRadius) {
        for (int r = vonRadius; r <= bisRadius; r++) {
            for (int i = 0; i < r * 8; i++) {
                double w = Math.PI * 2 * i / (r * 8);
                int x = mitte.getX() + (int) Math.round(Math.cos(w) * r);
                int z = mitte.getZ() + (int) Math.round(Math.sin(w) * r);
                for (int dy = 3; dy >= -3; dy--) {
                    int y = mitte.getY() + dy;
                    if (stellplatz(welt, x, y, z)) {
                        return new BlockPos(x, y, z);
                    }
                }
            }
        }
        return null;
    }

    private static boolean stellplatz(Level welt, int x, int y, int z) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -2; oz <= 2; oz++) {
                if (!fest(welt, m.set(x + ox, y - 1, z + oz)) || nass(welt, m)) {
                    return false;
                }
                for (int h = 0; h <= 2; h++) {
                    if (fest(welt, m.set(x + ox, y + h, z + oz)) || nass(welt, m)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
