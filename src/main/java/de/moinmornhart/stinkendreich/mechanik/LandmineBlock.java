package de.moinmornhart.stinkendreich.mechanik;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Landmine im Gelände rund ums Grundstück: ein flacher Teller, über den man hinweggeht. Nur in der Festung scharf, dann
 * leuchtet das rote Licht, und wer als Monster oder Eindringling darauf tritt, löst eine Explosion ohne Blockschaden aus
 * (siehe {@link Festung}). Danach ist sie nach 3 Sekunden wieder scharf.
 */
public class LandmineBlock extends MechanikBlock {
    private static final VoxelShape FORM = Block.box(3, 0, 3, 13, 2, 13);
    private static final int PAUSE_TICKS = 60;

    public LandmineBlock(Properties eigenschaften) {
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
    protected VoxelShape getShape(BlockState zustand, BlockGetter welt, BlockPos pos, CollisionContext kontext) {
        return FORM;
    }

    @Override
    public int takt() {
        return 4;
    }

    @Override
    public void takt(ServerLevel welt, BlockPos pos, BlockState zustand, MechanikBlockEntity daten) {
        VillaZustand z = VillaZustand.fuer(welt.getServer(), pos);
        boolean scharf = SmartHome.wahl(z, "minen", Festung.bereit(z, pos, false));
        boolean licht = scharf;                                     // rotes Licht = scharf
        if (licht != zustand.getValue(Eigenschaften.LEUCHTET)) {
            welt.setBlock(pos, zustand.setValue(Eigenschaften.LEUCHTET, licht), 3);
        }
        long jetzt = welt.getGameTime();
        if (!scharf || jetzt - daten.zahl("zuletzt", -1000) < PAUSE_TICKS) {
            return;
        }
        AABB feld = new AABB(pos.getX() - 0.1, pos.getY(), pos.getZ() - 0.1, pos.getX() + 1.1, pos.getY() + 0.5, pos.getZ() + 1.1);
        var opfer = welt.getEntitiesOfClass(LivingEntity.class, feld, e -> Festung.ziel(e, z));
        if (opfer.isEmpty()) {
            return;
        }
        daten.setze("zuletzt", (int) jetzt);
        Festung.explosion(welt, Vec3.atBottomCenterOf(pos), z);
        for (LivingEntity e : opfer) {
            if (e instanceof Player p) {
                z.alarm(pos, Component.translatable("stinkendreich.alarm.grund.mine", p.getScoreboardName()));
                break;
            }
        }
    }
}
