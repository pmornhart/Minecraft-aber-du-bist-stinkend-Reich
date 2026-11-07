package de.moinmornhart.stinkendreich.handy;

import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.auto.Autotyp;
import de.moinmornhart.stinkendreich.auto.HubschrauberEntity;
import de.moinmornhart.stinkendreich.butler.ButlerEntity;
import de.moinmornhart.stinkendreich.register.ModEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Hubschrauber-Abholung. Steht ein freier Hubschrauber in der Nähe (96 Blöcke), steigt James dort ein und fliegt los.
 * Steht er weiter weg (meist auf dem Landeplatz der Villa, wo außerhalb der Simulationsweite nichts tickt), fliegt James
 * die Strecke „unsichtbar“: Der Hubschrauber taucht mit James am Steuer und laufendem Rotor in Reiseflughöhe
 * {@link #ANFLUG} Blöcke vom Ziel (Spieler oder Wagen) entfernt auf – in der Richtung, aus der er kommt – und fliegt von dort sichtbar heran
 * und landet neben dem Spieler (FlugAuftrag). Vorher stand er einfach neben dem Spieler (Philip, 07.11.: „nicht einfach
 * neben mir spawnen“).
 */
final class HeliBereitstellung {
    /** So weit weg taucht der Hubschrauber im Anflug auf (innerhalb der Simulationsweite, außerhalb der Nähe). */
    private static final double ANFLUG = 90;

    private HeliBereitstellung() {
    }

    static void bereitstellen(ServerPlayer herr, ButlerEntity butler) {
        if (herr.level() instanceof ServerLevel welt) {
            bereitstellen(welt, herr.position(), butler, false);
        }
    }

    /**
     * Hubschrauber für einen Flug zu {@code ort}. {@code einsteigen}: James sitzt danach auf jeden Fall am Steuer (auch wenn
     * der Hubschrauber schon in der Nähe stand).
     */
    static void bereitstellen(ServerLevel welt, Vec3 ort, ButlerEntity butler, boolean einsteigen) {
        net.minecraft.world.phys.AABB suche = new net.minecraft.world.phys.AABB(ort, ort).inflate(512);
        HubschrauberEntity naechster = welt.getEntitiesOfClass(HubschrauberEntity.class, suche,
                        h -> h.getPassengers().stream().allMatch(e -> e == butler)).stream()
                .min(java.util.Comparator.comparingDouble(h -> h.distanceToSqr(ort))).orElse(null);
        if (naechster != null && naechster.position().distanceTo(ort) <= 96) {
            naechster.chunkHalten(true);
            if (einsteigen && butler.getVehicle() != naechster) {
                butler.stopRiding();
                butler.teleportiereNahe(naechster.blockPosition());
                Kompat.einsteigen(butler, naechster);
            }
            return;
        }
        // Woher er kommt: vom bisherigen Standort, sonst von der eigenen Villa, sonst von Süden
        Vec3 herkunft = naechster != null ? naechster.position()
                : de.moinmornhart.stinkendreich.villa.VillaPlatzierung.villa().map(v -> Vec3.atCenterOf(v.spawn())).orElse(ort.add(0, 0, 200));
        double dx = herkunft.x - ort.x, dz = herkunft.z - ort.z, l = Math.max(1e-3, Math.hypot(dx, dz));
        double x = ort.x + dx / l * ANFLUG, z = ort.z + dz / l * ANFLUG;
        // Reiseflughöhe: über dem höchsten Gelände zwischen Auftauchpunkt und Spieler (soweit geladen)
        double hoechste = ort.y;
        for (double d = 0; d <= ANFLUG; d += 6) {
            int bx = Mth.floor(ort.x + dx / l * d), bz = Mth.floor(ort.z + dz / l * d);
            if (welt.hasChunkAt(new net.minecraft.core.BlockPos(bx, 0, bz))) {
                hoechste = Math.max(hoechste, welt.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz));
            }
        }
        double y = hoechste + 22;
        // Blick zum Spieler (Gierwinkel wie bei Minecraft: 0 = Süden)
        float gier = (float) (Mth.atan2(-dz, -dx) * Mth.RAD_TO_DEG) - 90.0F;
        HubschrauberEntity heli = naechster;
        if (heli != null) {
            heli.ejectPassengers();
            heli.setDeltaMovement(Vec3.ZERO);
            Kompat.versetzen(heli, x, y, z, gier, 0.0F);
        } else {
            heli = (HubschrauberEntity) ModEntities.auto(Autotyp.HUBSCHRAUBER).create(welt
                    //? if >=1.21.2
                    , net.minecraft.world.entity.EntitySpawnReason.EVENT
            );
            if (heli == null) {
                return;
            }
            Kompat.versetzen(heli, x, y, z, gier, 0.0F);
            welt.addFreshEntity(heli);
        }
        heli.rotorAn();
        heli.chunkHalten(true);
        butler.stopRiding();
        butler.teleportiereNahe(heli.blockPosition());
        Kompat.einsteigen(butler, heli);
    }
}
