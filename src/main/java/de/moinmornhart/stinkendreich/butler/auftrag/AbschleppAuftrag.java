package de.moinmornhart.stinkendreich.butler.auftrag;

import com.google.gson.JsonObject;
import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.auto.AutoEntity;
import de.moinmornhart.stinkendreich.auto.Garage;
import de.moinmornhart.stinkendreich.auto.HubschrauberEntity;
import de.moinmornhart.stinkendreich.butler.ButlerEntity;
import de.moinmornhart.stinkendreich.butler.Pilot;
import de.moinmornhart.stinkendreich.villa.VillaPlatzierung;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * „Nach Hause bringen“ aus dem Automenü (Philip, 07.11.: „dass James mit dem Flugzeug angeflogen kommt, das daran
 * festbindet und es wieder nach Hause bringt“). James kommt mit dem Hubschrauber, schwebt über den Wagen, hängt ihn an
 * ein Seil (der Wagen hängt {@link #SEIL} Blöcke unter dem Hubschrauber), fliegt zur eigenen Villa, stellt ihn in die
 * Tiefgarage und landet danach auf dem Landeplatz.
 *
 * <p>Damit der Flug auch weitergeht, wenn niemand in der Nähe ist, hält der Hubschrauber seinen Chunk geladen
 * ({@link HubschrauberEntity#chunkHalten}).</p>
 */
public class AbschleppAuftrag extends Auftrag {
    public static final String TYP = "abschleppen";
    /** Länge des Seils (Blöcke zwischen Hubschrauber und Wagendach). */
    private static final double SEIL = 4.5;

    private enum Phase { HOLEN, ANFLUG, ANHAENGEN, TRAGEN, LANDEN }

    private final UUID wagenId;
    private Phase phase = Phase.HOLEN;
    private HubschrauberEntity heli;
    private AutoEntity wagen;
    private Pilot pilot;
    private int phasenTicks;

    public AbschleppAuftrag(UUID wagen) {
        this.wagenId = wagen;
    }

    @Override
    protected Stand tick(ButlerEntity butler) {
        if (!(butler.level() instanceof ServerLevel welt)) {
            return Stand.FERTIG;
        }
        phasenTicks++;
        if (phase != Phase.LANDEN && (wagen == null || wagen.isRemoved())) {
            wagen = welt.getEntity(wagenId) instanceof AutoEntity a ? a : null;
            if (wagen == null) {
                return ende(welt, butler, Component.translatable("stinkendreich.butler.abschleppen.weg"));
            }
        }
        if (heli != null && heli.isRemoved()) {
            heli = null;
            phase = Phase.HOLEN;
        }
        switch (phase) {
            case HOLEN -> {
                // Hubschrauber im Anflug bereitstellen (taucht in Reisehöhe auf, James am Steuer)
                de.moinmornhart.stinkendreich.handy.Handy.heliHolenZu(welt, wagen.position(), butler);
                heli = butler.getVehicle() instanceof HubschrauberEntity h ? h : null;
                if (heli == null) {
                    return fehlschlag(Component.translatable("stinkendreich.butler.flug.keiner"));
                }
                heli.chunkHalten(true);
                pilot = new Pilot(wagen.position());
                melden("Hubschrauber bei " + heli.blockPosition().toShortString() + ", Wagen bei " + wagen.blockPosition().toShortString());
                wechseln(Phase.ANFLUG);
            }
            case ANFLUG -> {
                if (butler.getVehicle() != heli) {
                    Kompat.einsteigen(butler, heli);
                }
                double abstand = Math.hypot(wagen.getX() - heli.getX(), wagen.getZ() - heli.getZ());
                if (abstand < 10 && heli.getY() > wagen.getY() + SEIL) {
                    wechseln(Phase.ANHAENGEN);
                } else {
                    pilot.fliegen(heli);
                }
            }
            case ANHAENGEN -> {
                // Langsam genau über den Wagen schweben (2 Sekunden), dann das Seil einhaken
                Vec3 ueber = wagen.position().add(0, SEIL + 1.5, 0);
                Vec3 neu = heli.position().lerp(ueber, Math.min(1.0, phasenTicks / 40.0));
                heli.chauffeurEingabe(0, 0, 0);
                Kompat.versetzen(heli, neu.x, neu.y, neu.z, heli.getYRot(), 0.0F);
                if (phasenTicks >= 40) {
                    wagen.ejectPassengers();
                    wagen.setNoGravity(true);
                    welt.playSound(null, wagen.blockPosition(), net.minecraft.sounds.SoundEvents.CHAIN_PLACE,
                            net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 0.8F);
                    VillaPlatzierung.VillaInfo villa = heimVilla(butler);
                    Vec3 ziel = villa != null ? Vec3.atBottomCenterOf(villa.spawn()) : Vec3.atBottomCenterOf(butler.heimat());
                    pilot = new Pilot(ziel);
                    melden("Wagen am Seil, fliege nach " + BlockPos.containing(ziel).toShortString());
                    wechseln(Phase.TRAGEN);
                }
            }
            case TRAGEN -> {
                pilot.fliegen(heli);
                anSeil();
                double rest = Math.hypot(pilot.ziel().x - heli.getX(), pilot.ziel().z - heli.getZ());
                if (rest < 8 || phasenTicks > 20 * 60 * 5) {
                    // Angekommen: Wagen in die Garage (ohne Stellplatz: vor der Villa absetzen)
                    wagen.setNoGravity(false);
                    if (!Garage.einparken(welt, wagen, butler.besitzer())) {
                        Kompat.versetzen(wagen, pilot.ziel().x + 4, pilot.ziel().y + 1, pilot.ziel().z, wagen.getYRot(), 0.0F);
                    }
                    VillaPlatzierung.VillaInfo villa = heimVilla(butler);
                    pilot = new Pilot(villa != null ? Vec3.atBottomCenterOf(villa.landeplatz()) : heli.position());
                    ServerPlayerNachricht.an(butler, Component.translatable("stinkendreich.butler.abschleppen.da"));
                    melden("Wagen abgesetzt nach " + phasenTicks / 20 + " s Flug");
                    wechseln(Phase.LANDEN);
                }
            }
            case LANDEN -> {
                if (pilot.fliegen(heli) == Pilot.Lage.GELANDET || phasenTicks > 20 * 90) {
                    return ende(welt, butler, null);
                }
            }
        }
        return Stand.LAEUFT;
    }

    /** Wagen hängt unter dem Hubschrauber und schwingt ein wenig nach. */
    private void anSeil() {
        Vec3 soll = heli.position().add(0, -SEIL - wagen.getBbHeight(), 0);
        Vec3 neu = wagen.position().lerp(soll, 0.35);
        wagen.setDeltaMovement(Vec3.ZERO);
        wagen.resetFallDistance();
        float gier = wagen.getYRot() + net.minecraft.util.Mth.wrapDegrees(heli.getYRot() - wagen.getYRot()) * 0.1F;
        Kompat.versetzen(wagen, neu.x, neu.y, neu.z, gier, 0.0F);
        wagen.setYBodyRot(gier);
    }

    private static void melden(String text) {
        de.moinmornhart.stinkendreich.StinkendReich.LOGGER.info("Abschleppen: {}", text);
    }

    private VillaPlatzierung.VillaInfo heimVilla(ButlerEntity butler) {
        VillaPlatzierung.VillaInfo v = butler.besitzer() == null ? null : VillaPlatzierung.vonBesitzer(butler.besitzer());
        return v != null ? v : VillaPlatzierung.villa().orElse(null);
    }

    private void wechseln(Phase neu) {
        phase = neu;
        phasenTicks = 0;
    }

    private Stand ende(ServerLevel welt, ButlerEntity butler, Component grund) {
        if (wagen != null && !wagen.isRemoved()) {
            wagen.setNoGravity(false);
        }
        if (heli != null) {
            heli.chauffeurEingabe(0, 0, 0);
        }
        if (grund != null) {
            return fehlschlag(grund);
        }
        return Stand.FERTIG;
    }

    @Override
    public void abbrechen(ButlerEntity butler) {
        if (butler.level() instanceof ServerLevel welt) {
            ende(welt, butler, null);
        }
    }

    @Override
    public Component status() {
        return Component.translatable(phase == Phase.TRAGEN ? "stinkendreich.butler.status.schleppt" : "stinkendreich.butler.status.holtwagen");
    }

    @Override
    public String typ() {
        return TYP;
    }

    @Override
    public void schreiben(JsonObject j) {
        j.addProperty("wagen", wagenId.toString());
    }

    static AbschleppAuftrag ausJson(JsonObject j) {
        try {
            return new AbschleppAuftrag(UUID.fromString(text(j, "wagen", "")));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Kleine Hilfe: Meldung an den Herrn des Butlers (über der Schnellleiste), falls er online ist. */
    private static final class ServerPlayerNachricht {
        static void an(ButlerEntity butler, Component text) {
            var herr = butler.besitzerOnline();
            if (herr != null) {
                Kompat.nachricht(herr, text, false);
            }
        }
    }
}
