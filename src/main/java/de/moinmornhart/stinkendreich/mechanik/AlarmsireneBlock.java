package de.moinmornhart.stinkendreich.mechanik;

import de.moinmornhart.stinkendreich.register.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/** Alarmsirene: heult während eines Alarms. */
public class AlarmsireneBlock extends MechanikBlock {
    public AlarmsireneBlock(Properties eigenschaften) {
        super(eigenschaften, Ausrichtung.WAAGERECHT, true, false);
    }

    @Override
    protected Property<?>[] grundEigenschaften() {
        return new Property<?>[]{Eigenschaften.RICHTUNG};
    }

    @Override
    public int takt() {
        return 30;
    }

    @Override
    public void takt(ServerLevel welt, BlockPos pos, BlockState zustand, MechanikBlockEntity daten) {
        VillaZustand z = VillaZustand.fuer(welt.getServer(), pos);
        if (SmartHome.wahl(z, "sirenen", z.alarmAktiv())) {
            welt.playSound(null, pos, ModSounds.SIRENE, SoundSource.BLOCKS, 3.0F, 1.0F);
        }
    }
}
