package de.moinmornhart.stinkendreich.auto;

import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.villa.VillaPlatzierung;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Die Tiefgarage als Zuhause der Wagen (Philip: „Er soll immer in die Garage fahren und da dann parken“). Gerufene
 * Wagen kommen aus der Garage ({@link #vorladen}) und James stellt sie nach der Fahrt wieder auf ihren Stellplatz
 * ({@link #einparken}), statt sie irgendwo in der Welt stehen zu lassen.
 *
 * <p>Die Garage liegt oft in einem Chunk, den gerade niemand geladen hat. Deshalb wird ihr Chunk für ein paar
 * Sekunden zwangsgeladen. Bevor er wieder freigegeben wird, räumt {@link #doppelteEntfernen} auf: Stehen zwei Wagen
 * derselben Art auf einem Stellplatz (etwa eine früher neu gerufene Limo neben der aus der Garage), bleibt einer.</p>
 */
public final class Garage {
    /** So lange bleibt der Chunk der Garage geladen (Ticks). */
    private static final int HALTEN = 100;

    private record Halt(ServerLevel welt, int cx, int cz, double[] platz, Autotyp art, long bis) {
    }

    private static final List<Halt> HALTE = new ArrayList<>();

    private Garage() {
    }

    public static void registrieren() {
        ServerTickEvents.END_SERVER_TICK.register(Garage::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
            for (Halt h : HALTE) {
                h.welt().setChunkForced(h.cx(), h.cz(), false);
            }
            HALTE.clear();
        });
    }

    /** Stellplatz eines Wagens dieser Art in der Villa des Spielers (sonst der Spawn-Villa) oder null. */
    public static double[] stellplatz(java.util.UUID herr, Autotyp art) {
        VillaPlatzierung.VillaInfo v = herr == null ? null : VillaPlatzierung.vonBesitzer(herr);
        if (v == null) {
            v = VillaPlatzierung.villa().orElse(null);
        }
        return v == null ? null : VillaPlatzierung.stellplatz(v, art);
    }

    /**
     * Lädt den Stellplatz eines Wagens dieser Art kurz, damit der Wagen darauf geladen ist, wenn James ihn holt
     * (sonst kam für jeden Anruf eine neue Limo dazu).
     */
    public static void vorladen(ServerLevel welt, java.util.UUID herr, Autotyp art) {
        double[] platz = stellplatz(herr, art);
        if (platz != null) {
            halten(welt, platz, art);
        }
    }

    /**
     * Stellt den Wagen auf seinen Stellplatz in der Tiefgarage. Gibt es für seine Art keinen Stellplatz (kein Wagen dieser
     * Art im Fuhrpark), bleibt er, wo er ist. Mitfahrer müssen vorher ausgestiegen sein.
     */
    public static boolean einparken(ServerLevel welt, AutoEntity auto, java.util.UUID herr) {
        double[] platz = stellplatz(herr, auto.art);
        if (platz == null) {
            return false;
        }
        auto.ejectPassengers();
        halten(welt, platz, auto.art);
        float blick = (float) platz[3];
        Kompat.versetzen(auto, platz[0], platz[1], platz[2], blick, 0.0F);
        auto.setYBodyRot(blick);
        auto.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        auto.chauffeurEingabe(0, 0);
        de.moinmornhart.stinkendreich.StinkendReich.LOGGER.info("Garage: {} eingeparkt bei {} {} {}", auto.art.name(),
                (int) platz[0], (int) platz[1], (int) platz[2]);
        return true;
    }

    private static void halten(ServerLevel welt, double[] platz, Autotyp art) {
        int cx = (int) Math.floor(platz[0]) >> 4, cz = (int) Math.floor(platz[2]) >> 4;
        welt.setChunkForced(cx, cz, true);
        HALTE.add(new Halt(welt, cx, cz, platz, art, welt.getServer().getTickCount() + HALTEN));
    }

    private static void tick(MinecraftServer server) {
        for (Iterator<Halt> it = HALTE.iterator(); it.hasNext(); ) {
            Halt h = it.next();
            if (server.getTickCount() < h.bis()) {
                continue;
            }
            doppelteEntfernen(h);
            it.remove();
            // Ein anderer Halt auf demselben Chunk (z. B. gleich wieder gerufen) hält ihn weiter
            boolean nochGebraucht = HALTE.stream().anyMatch(o -> o.welt() == h.welt() && o.cx() == h.cx() && o.cz() == h.cz());
            if (!nochGebraucht) {
                h.welt().setChunkForced(h.cx(), h.cz(), false);
            }
        }
    }

    /** Zwei Wagen derselben Art auf einem Stellplatz: der leere zweite verschwindet. */
    private static void doppelteEntfernen(Halt h) {
        double[] p = h.platz();
        List<AutoEntity> da = h.welt().getEntitiesOfClass(AutoEntity.class,
                new AABB(p[0] - 2, p[1] - 1, p[2] - 2, p[0] + 2, p[1] + 3, p[2] + 2),
                a -> a.art == h.art() && a.getPassengers().isEmpty());
        // Wagen mit Besitzer oder vollem Kofferraum zuerst: verschwinden darf nur ein leerer, herrenloser
        da.sort(java.util.Comparator.comparing(a -> a.istLeer() ? 1 : 0));
        for (int i = 1; i < da.size(); i++) {
            if (!da.get(i).istLeer()) {
                continue;
            }
            de.moinmornhart.stinkendreich.StinkendReich.LOGGER.info("Garage: doppelten Wagen ({}) entfernt", h.art().name());
            da.get(i).discard();
        }
    }
}
