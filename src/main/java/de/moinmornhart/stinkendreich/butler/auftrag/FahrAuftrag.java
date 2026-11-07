package de.moinmornhart.stinkendreich.butler.auftrag;

import com.google.gson.JsonObject;
import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.auto.AutoEntity;
import de.moinmornhart.stinkendreich.auto.Garage;
import de.moinmornhart.stinkendreich.auto.Autotyp;
import de.moinmornhart.stinkendreich.butler.ButlerEntity;
import de.moinmornhart.stinkendreich.butler.Chauffeur;
import de.moinmornhart.stinkendreich.register.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

/**
 * Der Butler als Chauffeur: setzt sich ans Steuer, wartet, bis der Herr eingestiegen ist (er sitzt hinten),
 * und fährt ihn zum Ziel (gespeicherter Ort, Villa oder die Stelle, auf die der Herr geschaut hat). Ohne
 * Ziel („hol den Wagen“) lässt er nur den Wagen vorfahren und wartet am Steuer auf Anweisungen.
 *
 * <p>Den Wagen nimmt er in dieser Reihenfolge: den, in dem der Herr sitzt; einen in der Nähe (24 Blöcke);
 * sonst lässt er einen geladenen Wagen vorfahren (am liebsten die Stretch-Limo).</p>
 */
public class FahrAuftrag extends Auftrag {
    public static final String TYP = "fahren";
    private static final double NAH = 24.0, VORFAHREN = 256.0;

    private enum Phase { WAGEN, EINSTEIGEN, WARTEN, BAUEN, FAHREN, AUSSTEIGEN, HEIMFAHRT }

    /** Ab dieser Entfernung zum Herrn parkt James den Wagen gleich in der Garage (er sieht es nicht mehr). */
    private static final double AUSSER_SICHT = 48.0;
    /** Höchstens so lange fährt James sichtbar Richtung Garage (Ticks). */
    private static final int HEIMFAHRT_HOECHSTENS = 20 * 120;

    /** Ziel oder null (nur vorfahren). */
    private final Vec3 ziel;
    /** Anzeigename des Ziels für Status und Nachrichten. */
    private final String zielName;
    private Phase phase = Phase.WAGEN;
    private AutoEntity auto;
    private Chauffeur chauffeur;
    private int warten;
    /** Der Wagen ist vorgefahren (dann hat James schon „Bitte steigen Sie ein“ gesagt). */
    private boolean vorgefahren;
    /** Straße, die gerade für diese Fahrt gebaut wird (Phase BAUEN). */
    private de.moinmornhart.stinkendreich.strasse.StrassenBau bau;
    private int bauVersuche;

    public FahrAuftrag(Vec3 ziel, String zielName) {
        this.ziel = ziel;
        this.zielName = zielName;
    }

    @Override
    protected Stand tick(ButlerEntity butler) {
        ServerPlayer herr = butler.besitzerOnline();
        if (herr == null || herr.level() != butler.level()) {
            if ((phase == Phase.AUSSTEIGEN || phase == Phase.HEIMFAHRT) && auto != null && !auto.isRemoved()) {
                einparken(butler);
            }
            return Stand.FERTIG;
        }
        if (auto != null && auto.isRemoved()) {
            auto = null;
            phase = Phase.WAGEN;
        }
        switch (phase) {
            case WAGEN -> {
                auto = wagenFinden(butler, herr);
                vorgefahren = auto != null && butler.getVehicle() == auto;
                if (auto == null) {
                    return fehlschlag(Component.translatable("stinkendreich.butler.fahren.keinwagen"));
                }
                phase = Phase.EINSTEIGEN;
            }
            case EINSTEIGEN -> {
                if (butler.distanceTo(auto) > NAH) {
                    butler.teleportiereNahe(auto.blockPosition());
                }
                if (butler.distanceTo(auto) > 3.0 && ticks < 200) {
                    ButlerEntity.Weg w = butler.gehZu(auto, 2.5);
                    if (w != ButlerEntity.Weg.UNERREICHBAR && w != ButlerEntity.Weg.DA) {
                        return Stand.LAEUFT;
                    }
                }
                ansSteuer(butler, auto);
                phase = Phase.WARTEN;
                warten = 0;
                if (!sitztDrin(herr) && !vorgefahren) {
                    butler.sagen(Component.translatable("stinkendreich.butler.fahren.einsteigen"));
                }
            }
            case WARTEN -> {
                if (butler.getVehicle() != auto) {
                    phase = Phase.EINSTEIGEN;
                    return Stand.LAEUFT;
                }
                auto.chauffeurEingabe(auto.tempo() > 0.03 ? -1 : 0, 0);
                if (sitztDrin(herr)) {
                    if (ziel == null) {
                        butler.sagen(Component.translatable("stinkendreich.butler.fahren.wohin"));
                        return Stand.FERTIG;
                    }
                    if (strasseLohnt(auto.position())) {
                        var netz = de.moinmornhart.stinkendreich.strasse.Strassennetz.get(butler.welt().getServer());
                        if (netzRoute(netz, auto.position()) == null) {
                            bau = netz.bauen(butler.welt(), ziel, zielName);
                            if (bau != null) {
                                butler.sagen(Component.translatable("stinkendreich.strasse.bau", zielName));
                                phase = Phase.BAUEN;
                                return Stand.LAEUFT;
                            }
                        }
                    }
                    losfahren(butler);
                } else if (++warten > 20 * 90) {
                    butler.sagen(Component.translatable("stinkendreich.butler.fahren.ausgestiegen"));
                    return Stand.FERTIG;
                }
            }
            case BAUEN -> {
                auto.chauffeurEingabe(auto.tempo() > 0.03 ? -1 : 0, 0);
                if (butler.getVehicle() != auto) {
                    return Stand.FERTIG;
                }
                if (!bau.fertig()) {
                    if (ticks % 40 == 0) {
                        Kompat.nachricht(herr, Component.translatable("stinkendreich.strasse.fortschritt", zielName, bau.prozent())
                                .withStyle(net.minecraft.ChatFormatting.GOLD), true);
                    }
                    return Stand.LAEUFT;
                }
                var netz = de.moinmornhart.stinkendreich.strasse.Strassennetz.get(butler.welt().getServer());
                if (netzRoute(netz, auto.position()) == null && ++bauVersuche < 3) {
                    // Es lief noch der Bau einer anderen Straße: jetzt die eigene bauen
                    bau = netz.bauen(butler.welt(), ziel, zielName);
                    if (bau != null) {
                        return Stand.LAEUFT;
                    }
                }
                butler.sagen(Component.translatable("stinkendreich.strasse.fertig", zielName));
                losfahren(butler);
            }
            case FAHREN -> {
                if (butler.getVehicle() != auto) {
                    return Stand.FERTIG;
                }
                if (!sitztDrin(herr)) {
                    // Unterwegs ausgestiegen: James bringt den Wagen nach Hause
                    auto.chauffeurEingabe(-1, 0);
                    heimfahrtStarten(butler);
                    return Stand.LAEUFT;
                }
                switch (chauffeur.fahren(auto)) {
                    case DA -> {
                        // Angekommen: James wartet am Steuer, bis der Herr ausgestiegen ist, und bringt den Wagen dann heim
                        butler.sagen(Component.translatable("stinkendreich.butler.fahren.da"));
                        phase = Phase.AUSSTEIGEN;
                        warten = 0;
                    }
                    case FESTGEFAHREN -> {
                        de.moinmornhart.stinkendreich.StinkendReich.LOGGER.info("Chauffeur steckt fest bei {} (nächster Wegpunkt {})",
                                auto.blockPosition(), chauffeur.naechsterPunkt());
                        // Kurz vor dem Ziel (z. B. zwischen den Häusern eines Dorfs): angekommen, den Rest zu Fuß
                        if (Math.hypot(ziel.x - auto.getX(), ziel.z - auto.getZ()) < 48) {
                            butler.sagen(Component.translatable("stinkendreich.butler.fahren.fastda"));
                            phase = Phase.AUSSTEIGEN;
                            warten = 0;
                            return Stand.LAEUFT;
                        }
                        return fehlschlag(Component.translatable("stinkendreich.butler.fahren.fest"));
                    }
                    default -> {
                    }
                }
            }
            case AUSSTEIGEN -> {
                auto.chauffeurEingabe(auto.tempo() > 0.03 ? -1 : 0, 0);
                if (butler.getVehicle() != auto) {
                    return Stand.FERTIG;
                }
                if (!sitztDrin(herr)) {
                    if (++warten > 30) {
                        heimfahrtStarten(butler);
                    }
                } else if (++warten > 20 * 120) {
                    // Der Herr bleibt sitzen: James steigt aus und überlässt ihm den Wagen
                    butler.welt().getServer().execute(butler::stopRiding);
                    return Stand.FERTIG;
                }
            }
            case HEIMFAHRT -> {
                if (butler.getVehicle() != auto) {
                    einparken(butler);
                    return Stand.FERTIG;
                }
                if (sitztDrin(herr)) {
                    // Doch wieder eingestiegen: warten, wohin es geht
                    auto.chauffeurEingabe(auto.tempo() > 0.03 ? -1 : 0, 0);
                    return Stand.LAEUFT;
                }
                Chauffeur.Lage lage = chauffeur.fahren(auto);
                boolean ausserSicht = herr.distanceTo(auto) > AUSSER_SICHT;
                if (lage != Chauffeur.Lage.FAEHRT || ausserSicht || ++warten > HEIMFAHRT_HOECHSTENS) {
                    einparken(butler);
                    return Stand.FERTIG;
                }
            }
        }
        return Stand.LAEUFT;
    }

    /**
     * Nach der Fahrt (Philip: „dass er dann wieder mit der Limo nach Hause fährt“, „immer in die Garage fahren und da
     * dann parken“): James fährt sichtbar los Richtung Villa – über das Straßennetz, durchs Tor und die Rampe hinunter
     * in die Tiefgarage. Ist der Herr weit genug weg, steht der Wagen gleich auf seinem Stellplatz.
     */
    private void heimfahrtStarten(ButlerEntity butler) {
        butler.sagen(Component.translatable("stinkendreich.butler.fahren.garage"));
        chauffeur = new Chauffeur(heimweg(butler, auto.position()), 5.0);
        phase = Phase.HEIMFAHRT;
        warten = 0;
    }

    /** Weg vom Wagen bis an den Fuß der Garagenrampe (Spawn-Villa) bzw. zur eigenen Villa. */
    private static List<Vec3> heimweg(ButlerEntity butler, Vec3 start) {
        var spawn = de.moinmornhart.stinkendreich.villa.VillaPlatzierung.villa();
        java.util.UUID herr = butler.besitzer();
        var eigene = herr == null ? null : de.moinmornhart.stinkendreich.villa.VillaPlatzierung.vonBesitzer(herr);
        if (spawn.isEmpty() || (eigene != null && eigene.nr() != spawn.get().nr())) {
            var v = eigene != null ? eigene : spawn.orElse(null);
            return v == null ? List.of(start) : List.of(Vec3.atBottomCenterOf(v.weltPos(TOR_X, v.plan().bodenY + 1, VOR_DER_BRUECKE_Z)));
        }
        var v = spawn.get();
        int y = v.plan().bodenY + 1;
        List<Vec3> weg = new java.util.ArrayList<>();
        if (!aufDemGrundstueck(v, start)) {
            var netz = de.moinmornhart.stinkendreich.strasse.Strassennetz.get(butler.welt().getServer());
            List<Vec3> heim = netz.routeHeim(start);
            if (heim != null) {
                weg.addAll(heim);
            }
            weg.add(Vec3.atBottomCenterOf(v.weltPos(TOR_X, y, VOR_DER_BRUECKE_Z)));
            weg.add(Vec3.atBottomCenterOf(v.weltPos(TOR_X, y, HINTER_DEM_TOR_Z)));
        }
        // Hält der Wagen vor der Haustür: erst zurück auf den Teppich vor dem Tor (zwischen den Beeten und Brunnen hindurch)
        if (aufDemGrundstueck(v, start) && start.z - v.ursprung().getZ() < 90) {
            weg.add(Vec3.atBottomCenterOf(v.weltPos(TOR_X, y, 92)));
        }
        // Zufahrt zur Garage (Bogen um 80/88), Garagentor, Rampe hinunter (Bauplan: villa.py, Tiefgarage)
        double rampeX = 87.0, rampeOben = 66.5;
        int unten = y;
        for (var m : v.plan().marke("stellplatz")) {
            unten = m.get(1).getAsInt();
        }
        for (double[] p : new double[][]{{73, y, 95.5}, {80, y, 95}, {84.95, y, 92.95}, {rampeX, y, 88}, {rampeX, y, 75},
                {rampeX, y, rampeOben}, {rampeX, unten, 56}, {rampeX, unten, 47}}) {
            BlockPos w = v.ursprung();
            weg.add(new Vec3(w.getX() + p[0], w.getY() + p[1], w.getZ() + p[2]));
        }
        return weg;
    }

    /** Wagen auf seinen Stellplatz in der Tiefgarage, James nach Hause. */
    private void einparken(ButlerEntity butler) {
        AutoEntity wagen = auto;
        java.util.UUID herr = butler.besitzer();
        auto.chauffeurEingabe(0, 0);
        // Erst nach dem Tick (nicht mitten im Fahrzeug-Tick)
        butler.welt().getServer().execute(() -> {
            butler.stopRiding();
            if (!wagen.isRemoved()) {
                Garage.einparken(butler.welt(), wagen, herr);
            }
            butler.teleportiereNahe(butler.heimat());
        });
    }

    /** Weg zum Ziel festlegen (über das Straßennetz, wenn es eins gibt) und losfahren. */
    private void losfahren(ButlerEntity butler) {
        List<Vec3> weg = null;
        if (strasseLohnt(auto.position()) || zielAufDemGrundstueck()) {
            weg = netzRoute(de.moinmornhart.stinkendreich.strasse.Strassennetz.get(butler.welt().getServer()), auto.position());
        }
        chauffeur = new Chauffeur(weg != null ? weg : route(auto.position(), ziel), 5.0);
        phase = Phase.FAHREN;
    }

    /** Lohnt sich für diese Fahrt eine Straße? (Ziel außerhalb der Villa und weit genug weg, Straßenbau an) */
    private boolean strasseLohnt(Vec3 start) {
        var villa = de.moinmornhart.stinkendreich.villa.VillaPlatzierung.villa();
        if (!de.moinmornhart.stinkendreich.Konfig.get().strassenBauen || villa.isEmpty() || zielAufDemGrundstueck()
                || auto.level().dimension() != net.minecraft.world.level.Level.OVERWORLD) {
            return false;
        }
        Vec3 von = aufDemGrundstueck(villa.get(), start) ? de.moinmornhart.stinkendreich.strasse.Strassennetz.kreiselMitte() : start;
        return von != null && Math.hypot(ziel.x - von.x, ziel.z - von.z) > de.moinmornhart.stinkendreich.Konfig.get().strassenAbEntfernung;
    }

    private boolean zielAufDemGrundstueck() {
        return de.moinmornhart.stinkendreich.villa.VillaPlatzierung.villa().map(v -> aufDemGrundstueck(v, ziel)).orElse(false);
    }

    /**
     * Weg über das Straßennetz: von der Villa durchs Tor zum Kreisel und weiter, von unterwegs zur nächsten Straße,
     * nach Hause bis zum Kreisel und durchs Tor. Null, wenn es keinen gibt (noch keine Straße zum Ziel).
     */
    private List<Vec3> netzRoute(de.moinmornhart.stinkendreich.strasse.Strassennetz netz, Vec3 start) {
        var villa = de.moinmornhart.stinkendreich.villa.VillaPlatzierung.villa();
        if (villa.isEmpty()) {
            return null;
        }
        var v = villa.get();
        int y = v.plan().bodenY + 1;
        Vec3 aussen = Vec3.atBottomCenterOf(v.weltPos(TOR_X, y, VOR_DER_BRUECKE_Z));
        Vec3 innen = Vec3.atBottomCenterOf(v.weltPos(TOR_X, y, HINTER_DEM_TOR_Z));
        List<Vec3> weg = new java.util.ArrayList<>();
        if (zielAufDemGrundstueck()) {
            List<Vec3> heim = netz.routeHeim(start);
            if (heim == null) {
                return null;     // weit weg vom Netz: route() fährt außen um das Grundstück herum
            }
            weg.addAll(heim);
            weg.add(aussen);
            weg.add(innen);
            weg.add(ziel);
            return weg;
        }
        boolean startDrin = aufDemGrundstueck(v, start);
        List<Vec3> netzWeg = netz.route(startDrin ? null : start, ziel, zielName);
        if (netzWeg == null) {
            return null;
        }
        if (startDrin) {
            weg.add(innen);
            weg.add(aussen);
        }
        weg.addAll(netzWeg);
        return weg;
    }

    private boolean sitztDrin(ServerPlayer herr) {
        return auto != null && herr.getVehicle() == auto;
    }

    /** Einfahrt der Villa (Strukturkoordinaten): vor der Brücke und hinter dem Tor, mittig auf dem roten Teppich. */
    private static final double TOR_X = 55.5, VOR_DER_BRUECKE_Z = 117.5, HINTER_DEM_TOR_Z = 95.5;
    /** Wo der Wagen „zu Hause“ hält: auf dem Teppich vor der Haustür (Tür bei z 67), zwischen Beeten und Brunnen. */
    public static final double VOR_DER_TUER_Z = 72.5;

    /** Haltepunkt vor der Haustür einer Villa (Weltkoordinaten). */
    public static Vec3 vorDerTuer(de.moinmornhart.stinkendreich.villa.VillaPlatzierung.VillaInfo v) {
        return Vec3.atBottomCenterOf(v.weltPos(TOR_X, v.plan().bodenY + 1, VOR_DER_TUER_Z));
    }

    /**
     * Weg zum Ziel: Liegt genau eines von Start und Ziel auf dem Villa-Grundstück, geht es über die Brücke
     * und durchs Tor (der Graben ringsum ist sonst nicht zu überwinden).
     */
    static List<Vec3> route(Vec3 start, Vec3 ziel) {
        var villa = de.moinmornhart.stinkendreich.villa.VillaPlatzierung.villa();
        if (villa.isEmpty()) {
            return List.of(ziel);
        }
        var v = villa.get();
        boolean startDrin = aufDemGrundstueck(v, start), zielDrin = aufDemGrundstueck(v, ziel);
        if (startDrin == zielDrin) {
            return List.of(ziel);
        }
        int y = v.plan().bodenY + 1;
        Vec3 aussen = Vec3.atBottomCenterOf(v.weltPos(TOR_X, y, VOR_DER_BRUECKE_Z));
        Vec3 innen = Vec3.atBottomCenterOf(v.weltPos(TOR_X, y, HINTER_DEM_TOR_Z));
        List<Vec3> weg = new java.util.ArrayList<>();
        if (startDrin) {
            weg.add(innen);
            weg.add(aussen);
            weg.addAll(umfahren(v, aussen, ziel));
        } else {
            weg.addAll(umfahren(v, start, aussen));
            weg.add(aussen);
            weg.add(innen);
        }
        weg.add(ziel);
        return weg;
    }

    /** Abstand der Umfahrung vom Grundstück (Graben, Mauer, Minengürtel liegen dazwischen). */
    private static final double UMFAHRUNG = 16;

    /**
     * Wegpunkte, die von {@code von} nach {@code nach} außen um das Grundstück herumführen statt quer hindurch (Philip,
     * 07.11.: James fuhr ihn auf dem Heimweg geradewegs in den Graben). Erst seitlich am Grundstück vorbei, dann vor
     * die Südseite, wo Brücke und Tor liegen.
     */
    private static List<Vec3> umfahren(de.moinmornhart.stinkendreich.villa.VillaPlatzierung.VillaInfo v, Vec3 von, Vec3 nach) {
        double x0 = v.ursprung().getX() - UMFAHRUNG, x1 = v.ursprung().getX() + v.plan().breiteX + UMFAHRUNG;
        double z0 = v.ursprung().getZ() - UMFAHRUNG, z1 = v.ursprung().getZ() + v.plan().breiteZ + UMFAHRUNG;
        List<Vec3> weg = new java.util.ArrayList<>();
        // Liegt die Strecke vor der Südseite, ist nichts zu tun
        if (von.z >= z1 - UMFAHRUNG / 2 && nach.z >= z1 - UMFAHRUNG / 2) {
            return weg;
        }
        Vec3 aussenSeite = von.z >= z1 - UMFAHRUNG / 2 ? nach : von;
        double seiteX = aussenSeite.x < (x0 + x1) / 2 ? x0 : x1;
        double y = v.ursprung().getY() + v.plan().bodenY + 1;
        Vec3 seiteNord = new Vec3(seiteX, y, Math.max(z0, Math.min(z1, aussenSeite.z))), seiteSued = new Vec3(seiteX, y, z1);
        if (von == aussenSeite) {
            weg.add(seiteNord);
            weg.add(seiteSued);
        } else {
            weg.add(seiteSued);
            weg.add(seiteNord);
        }
        return weg;
    }

    private static boolean aufDemGrundstueck(de.moinmornhart.stinkendreich.villa.VillaPlatzierung.VillaInfo v, Vec3 p) {
        double x = p.x - v.ursprung().getX(), z = p.z - v.ursprung().getZ();
        return x >= 0 && x < v.plan().breiteX && z >= 0 && z < v.plan().breiteZ;
    }

    /** Wagen des Herrn, einer in der Nähe, oder einer, der vorfährt. */
    private static AutoEntity wagenFinden(ButlerEntity butler, ServerPlayer herr) {
        if (herr.getVehicle() instanceof AutoEntity eigener && !eigener.art.fliegt()) {
            return eigener;
        }
        // James sitzt schon am Steuer (gerade mit der Limo vorgefahren): genau diesen Wagen nehmen, keinen neuen
        if (butler.getVehicle() instanceof AutoEntity seiner && !seiner.art.fliegt()) {
            return seiner;
        }
        // In der Nähe heißt auch: etwa auf gleicher Höhe (nicht in der Tiefgarage unter dem Herrn)
        List<AutoEntity> nah = butler.level().getEntitiesOfClass(AutoEntity.class, herr.getBoundingBox().inflate(NAH),
                a -> !a.art.fliegt() && (a.getPassengers().isEmpty() || a.chauffeurFaehrt()) && Math.abs(a.getY() - herr.getY()) < 4);
        if (!nah.isEmpty()) {
            nah.sort(Comparator.comparingDouble(a -> a.distanceToSqr(herr)));
            return nah.get(0);
        }
        List<AutoEntity> weit = butler.level().getEntitiesOfClass(AutoEntity.class, herr.getBoundingBox().inflate(VORFAHREN),
                a -> !a.art.fliegt() && a.getPassengers().isEmpty());
        if (weit.isEmpty()) {
            return null;
        }
        // Am liebsten die Stretch-Limo, sonst der nächste Wagen
        weit.sort(Comparator.comparing((AutoEntity a) -> a.art != Autotyp.STRETCHLIMO).thenComparingDouble(a -> a.distanceToSqr(herr)));
        AutoEntity wagen = weit.get(0);
        BlockPos platz = Chauffeur.parkplatz(herr.level(), herr.blockPosition(), 4, 10);
        if (platz == null) {
            return null;
        }
        // Der Wagen steht in der Blickrichtung des Herrn
        float blick = herr.getYRot();
        Kompat.versetzen(wagen, platz.getX() + 0.5, platz.getY(), platz.getZ() + 0.5, blick, 0.0F);
        wagen.setYBodyRot(blick);
        butler.teleportiereNahe(platz);
        ansSteuer(butler, wagen);
        wagen.level().playSound(null, wagen.blockPosition(), ModSounds.HUPE, SoundSource.NEUTRAL, 2.0F, 1.0F);
        butler.sagen(Component.translatable("stinkendreich.butler.fahren.vorgefahren", wagen.getName()));
        return wagen;
    }

    /** Setzt den Butler ans Steuer; sitzt der Herr am Steuer, rutscht er dadurch nach hinten (siehe AutoEntity). */
    private static void ansSteuer(ButlerEntity butler, AutoEntity auto) {
        if (butler.getVehicle() == auto) {
            return;
        }
        butler.stopRiding();
        Kompat.einsteigen(butler, auto);
        auto.level().playSound(null, auto.blockPosition(), ModSounds.ANLASSER, SoundSource.NEUTRAL, 1.0F, 1.0F);
    }

    @Override
    public void abbrechen(ButlerEntity butler) {
        if (auto != null) {
            auto.chauffeurEingabe(0, 0);
        }
    }

    @Override
    public Component status() {
        if (phase == Phase.FAHREN && auto != null && chauffeur != null) {
            Vec3 p = auto.position();
            int rest = (int) Math.sqrt(Math.pow(ziel.x - p.x, 2) + Math.pow(ziel.z - p.z, 2));
            return Component.translatable("stinkendreich.butler.status.faehrt", zielName, rest);
        }
        return Component.translatable(phase == Phase.WARTEN ? "stinkendreich.butler.status.wartetimwagen" : "stinkendreich.butler.status.holtwagen");
    }

    @Override
    public String typ() {
        return TYP;
    }

    @Override
    public void schreiben(JsonObject j) {
        if (ziel != null) {
            j.addProperty("x", ziel.x);
            j.addProperty("y", ziel.y);
            j.addProperty("z", ziel.z);
        }
        j.addProperty("name", zielName);
    }

    static FahrAuftrag ausJson(JsonObject j) {
        Vec3 z = j.has("x") ? new Vec3(j.get("x").getAsDouble(), j.get("y").getAsDouble(), j.get("z").getAsDouble()) : null;
        return new FahrAuftrag(z, text(j, "name", ""));
    }
}
