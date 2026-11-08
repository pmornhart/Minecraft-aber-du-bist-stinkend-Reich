package de.moinmornhart.stinkendreich.mechanik;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/** Rote Warnleuchte: blinkt, solange Alarm herrscht, und während die Zugbrücke in der Nähe fährt. */
public class WarnleuchteBlock extends MechanikBlock {
    public WarnleuchteBlock(Properties eigenschaften) {
        super(eigenschaften, Ausrichtung.KEINE, false, false);
    }

    @Override
    protected Property<?>[] grundEigenschaften() {
        return new Property<?>[]{Eigenschaften.LEUCHTET};
    }

    @Override
    protected BlockState standardZustand(BlockState s) {
        return s.setValue(Eigenschaften.LEUCHTET, false);
    }

    @Override
    public int takt() {
        return 10;
    }

    @Override
    public void takt(ServerLevel welt, BlockPos pos, BlockState zustand, MechanikBlockEntity daten) {
        VillaZustand z = VillaZustand.fuer(welt.getServer(), pos);
        boolean alarm = SmartHome.wahl(z, "sirenen", z.alarmAktiv()) || Zugbruecke.warnen(pos);
        // Blinken: im Alarm (und wenn die Brücke fährt) abwechselnd an und aus
        boolean an = alarm && (welt.getGameTime() / 10) % 2 == 0;
        if (an != zustand.getValue(Eigenschaften.LEUCHTET)) {
            welt.setBlock(pos, zustand.setValue(Eigenschaften.LEUCHTET, an), 3);
        }
    }
}
