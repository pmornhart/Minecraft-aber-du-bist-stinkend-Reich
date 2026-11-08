package de.moinmornhart.stinkendreich.butler.auftrag;

import com.google.gson.JsonObject;
import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.auto.AutoEntity;
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
 * ein Seil (der Wagen hängt {@link #SEIL} Blöcke unter dem Hubschrauber), fliegt zur eigenen Villa, sinkt über dem roten
 * Teppich vor der Haustür und setzt den Wagen dort sanft ab (Philip, 08.11.: „dass die Autos mit dem Heli dann da hin
 * kommen“) und landet danach auf dem Landeplatz.
 *
 * <p>Damit der Flug auch weitergeht, wenn niemand in der Nähe ist, hält der Hubschrauber seinen Chunk geladen
 * ({@link HubschrauberEntity#chunkHalten}).</p>
 */
public class AbschleppAuftrag extends Auftrag {
    public static final String TYP = "abschleppen";
    /** Länge des Seils (Blöcke zwischen Hubschrauber und Wagendach). */
    private static final double SEIL = 4.5;

    private enum Phase { HOLEN, ANFLUG, ANHAENGEN, TRAGEN, ABSETZEN, LANDEN }

    private final UUID wagenId;
    private Phase phase = Phase.HOLEN;
    private HubschrauberEntity heli;
    private AutoEntity wagen;
    private Pilot pilot;
    private int phasenTicks;
    /** Position des Hubschraubers zu Beginn des Absetzens. */
    private Vec3 absetzStart;

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
                // Nach einem Neustart mitten im Tragen hing der Wagen sonst schwerelos in der Luft
                wagen.setNoGravity(false);
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
                if (abstand < 10) {        // die Höhe stellt ANHAENGEN selbst ein (vorher hing er knapp zu tief eine Minute fest)
                    wechseln(Phase.ANHAENGEN);
                } else if (phasenTicks > 20 * 60) {
                    // Nach einer Minute noch nicht über dem Wagen (z. B. daneben gelandet oder hängt fest): direkt über den
                    // Wagen setzen und weitermachen, statt ewig zu warten
                    Vec3 ueber = wagen.position().add(0, SEIL + 1.5, 0);
                    heli.chauffeurEingabe(0, 0, 0);
                    Kompat.versetzen(heli, ueber.x, ueber.y, ueber.z, heli.getYRot(), 0.0F);
                    melden("Anflug dauerte zu lange, Hubschrauber über den Wagen gesetzt");
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
                    pilot = new Pilot(absetzOrt(butler));
                    melden("Wagen am Seil, fliege nach " + BlockPos.containing(pilot.ziel()).toShortString());
                    wechseln(Phase.TRAGEN);
                }
            }
            case TRAGEN -> {
                pilot.fliegen(heli);
                anSeil();
                double rest = Math.hypot(pilot.ziel().x - heli.getX(), pilot.ziel().z - heli.getZ());
                // Ab 20 Blöcken übernimmt ABSETZEN (schwebt weich genau über den Teppich) – der Pilot selbst landet nahe Häusern
                // auf einem freien Platz bis 16 Blöcke daneben und kam so nie nah genug heran
                if (rest < 20 || phasenTicks > 20 * 60 * 5) {
                    absetzStart = heli.position();
                    wechseln(Phase.ABSETZEN);
                }
            }
            case ABSETZEN -> {
                // Über dem Teppich einschweben und sinken, bis der Wagen auf dem Boden steht (4 Sekunden), dann ausklinken
                Vec3 ort = pilot.ziel();
                Vec3 ueber = new Vec3(ort.x, ort.y + wagen.getBbHeight() + SEIL, ort.z);
                double t = Math.min(1.0, phasenTicks / 100.0);
                t = t * t * (3 - 2 * t);
                Vec3 neu = absetzStart.lerp(ueber, t);
                heli.chauffeurEingabe(0, 0, 0);
                // Nase zum Tor (Süden), damit der Wagen gleich wieder losfahren kann
                float gier = heli.getYRot() + net.minecraft.util.Mth.wrapDegrees(0.0F - heli.getYRot()) * 0.08F;
                Kompat.versetzen(heli, neu.x, neu.y, neu.z, gier, 0.0F);
                anSeil();
                if (phasenTicks >= 110) {
                    wagen.setNoGravity(false);
                    Kompat.versetzen(wagen, ort.x, ort.y, ort.z, 0.0F, 0.0F);
                    wagen.setYBodyRot(0.0F);
                    wagen.setDeltaMovement(Vec3.ZERO);
                    welt.playSound(null, wagen.blockPosition(), net.minecraft.sounds.SoundEvents.CHAIN_BREAK,
                            net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 1.0F);
                    VillaPlatzierung.VillaInfo villa = heimVilla(butler);
                    pilot = new Pilot(villa != null ? Vec3.atBottomCenterOf(villa.landeplatz()) : heli.position().add(0, 0, 20));
                    de.moinmornhart.stinkendreich.handy.Handy.nachricht(butler.besitzerOnline(), Component.translatable("stinkendreich.butler.abschleppen.da"));
                    melden("Wagen abgesetzt vor der Haustür bei " + wagen.blockPosition().toShortString());
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

    /** Absetzplatz: roter Teppich vor der Haustür der eigenen Villa (ohne Villa: James' Zuhause). */
    private Vec3 absetzOrt(ButlerEntity butler) {
        VillaPlatzierung.VillaInfo villa = heimVilla(butler);
        return villa != null ? FahrAuftrag.vorDerTuer(villa) : Vec3.atBottomCenterOf(butler.heimat());
    }

    private VillaPlatzierung.VillaInfo heimVilla(ButlerEntity butler) {
        return de.moinmornhart.stinkendreich.butler.Umgebung.eigeneVilla(butler);
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
        return Component.translatable(phase == Phase.TRAGEN || phase == Phase.ABSETZEN ? "stinkendreich.butler.status.schleppt" : "stinkendreich.butler.status.holtwagen");
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
}
