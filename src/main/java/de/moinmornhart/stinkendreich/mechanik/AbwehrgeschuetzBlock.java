package de.moinmornhart.stinkendreich.mechanik;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Abwehrgeschütz auf der Mauer: in der Festung (Stufe 4) schießt es zweimal pro Sekunde mit einem Laser auf das
 * nächste Monster oder den nächsten Eindringling bis 32 Blöcke weit, wenn es freie Sicht hat (siehe {@link Festung}).
 */
public class AbwehrgeschuetzBlock extends MechanikBlock {
    private static final VoxelShape FORM = Block.box(2, 0, 2, 14, 11, 14);
    public static final double REICHWEITE = 32.0;
    public static final float SCHADEN = 9.0F;

    public AbwehrgeschuetzBlock(Properties eigenschaften) {
        super(eigenschaften, Ausrichtung.WAAGERECHT, false, false);
    }

    @Override
    protected Property<?>[] grundEigenschaften() {
        return new Property<?>[]{Eigenschaften.RICHTUNG, Eigenschaften.AN};
    }

    @Override
    protected BlockState standardZustand(BlockState s) {
        return s.setValue(Eigenschaften.AN, false);
    }

    @Override
    protected VoxelShape getShape(BlockState zustand, BlockGetter welt, BlockPos pos, CollisionContext kontext) {
        return FORM;
    }

    @Override
    public int takt() {
        return 10;
    }

    @Override
    public void takt(ServerLevel welt, BlockPos pos, BlockState zustand, MechanikBlockEntity daten) {
        VillaZustand z = VillaZustand.fuer(welt.getServer(), pos);
        boolean aktiv = SmartHome.wahl(z, "geschuetze", Festung.bereit(z, pos, true));
        if (aktiv != zustand.getValue(Eigenschaften.AN)) {
            // Ausfahren bzw. Einfahren: Kopf mit den Läufen hebt sich aus dem Sockel
            welt.setBlock(pos, zustand.setValue(Eigenschaften.AN, aktiv), 3);
            welt.playSound(null, pos, aktiv ? net.minecraft.sounds.SoundEvents.PISTON_EXTEND : net.minecraft.sounds.SoundEvents.PISTON_CONTRACT,
                    net.minecraft.sounds.SoundSource.BLOCKS, 1.2F, 0.7F);
            welt.sendParticles(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.getX() + 0.5, pos.getY() + 0.8,
                    pos.getZ() + 0.5, 4, 0.2, 0.1, 0.2, 0.02);
            return;
        }
        if (aktiv) {
            Festung.schiessen(welt, pos, z, REICHWEITE, SCHADEN);
        }
    }
}
