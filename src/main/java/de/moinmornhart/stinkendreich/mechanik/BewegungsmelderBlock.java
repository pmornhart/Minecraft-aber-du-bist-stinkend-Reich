package de.moinmornhart.stinkendreich.mechanik;

import de.moinmornhart.stinkendreich.Kompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Bewegungsmelder an der Wand. Gibt ein Redstone-Signal, solange sich vor ihm etwas bewegt
 * (Lichtautomatik). Ab Stufe 1 löst er Alarm aus, wenn ein Eindringling erkannt wird.
 */
public class BewegungsmelderBlock extends MechanikBlock {
    public BewegungsmelderBlock(Properties eigenschaften) {
        super(eigenschaften, Ausrichtung.WAAGERECHT, true, true);
    }

    @Override
    protected Property<?>[] grundEigenschaften() {
        return new Property<?>[]{Eigenschaften.RICHTUNG, Eigenschaften.AN};
    }

    @Override
    public int takt() {
        return 10;
    }

    @Override
    public void takt(ServerLevel welt, BlockPos pos, BlockState zustand, MechanikBlockEntity daten) {
        Direction vorne = zustand.getValue(Eigenschaften.RICHTUNG);
        AABB bereich = new AABB(pos).expandTowards(vorne.getStepX() * 7.0, 0, vorne.getStepZ() * 7.0).inflate(3, 2, 3);
        List<LivingEntity> gesehen = welt.getEntitiesOfClass(LivingEntity.class, bereich, e -> !e.isSpectator());
        boolean bewegung = !gesehen.isEmpty();
        if (bewegung != zustand.getValue(Eigenschaften.AN)) {
            welt.setBlock(pos, zustand.setValue(Eigenschaften.AN, bewegung), 3);
            Kompat.nachbarnAktualisieren(welt, pos, this);
            Kompat.nachbarnAktualisieren(welt, pos.relative(vorne.getOpposite()), this);
        }
        VillaZustand z = VillaZustand.fuer(welt.getServer(), pos);
        if (SmartHome.wahl(z, "melder", z.stufe() >= 1)) {
            for (LivingEntity e : gesehen) {
                if (z.istEindringling(e)) {
                    z.alarm(pos, Component.translatable("stinkendreich.alarm.grund.bewegung", e.getDisplayName()));
                    break;
                }
            }
        }
    }
}
