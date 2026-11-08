package de.moinmornhart.stinkendreich.mechanik;

import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.register.ModBloecke;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Laser-Sender: Ab Sicherheitsstufe 3 spannt er einen roten Laserstrahl bis zur nächsten Wand
 * (höchstens 24 Blöcke). Eindringlinge im Strahl lösen Alarm aus und nehmen Schaden.
 * Bewohner und Spieler mit Zugangskarte können den Strahl gefahrlos durchqueren.
 */
public class LaserSenderBlock extends MechanikBlock {
    public static final int REICHWEITE = 24;
    /** Größte Länge eines Strahls (Himmelslaser auf der Mauer, Wert {@code reichweite} aus dem Bauplan). */
    public static final int MAX_REICHWEITE = 64;

    public LaserSenderBlock(Properties eigenschaften) {
        super(eigenschaften, Ausrichtung.ALLE, false, false);
    }

    @Override
    protected Property<?>[] grundEigenschaften() {
        return new Property<?>[]{Eigenschaften.RICHTUNG_ALLE};
    }

    @Override
    public int takt() {
        return 5;
    }

    @Override
    public void takt(ServerLevel welt, BlockPos pos, BlockState zustand, MechanikBlockEntity daten) {
        VillaZustand z = VillaZustand.fuer(welt.getServer(), pos);
        // Himmelslaser (nach oben) fahren in der Festung als Welle rund um die Villa hoch
        boolean an = SmartHome.wahl(z, "laser", z.stufe() >= 3 && (zustand.getValue(Eigenschaften.RICHTUNG_ALLE) != Direction.UP
                || !z.festung() || Festung.bereit(z, pos, true)));
        Direction richtung = zustand.getValue(Eigenschaften.RICHTUNG_ALLE);
        BlockState strahl = ModBloecke.LASERSTRAHL.defaultBlockState().setValue(LaserstrahlBlock.ACHSE, richtung.getAxis());

        int reichweite = Mth.clamp(daten.zahl("reichweite", REICHWEITE), 1, MAX_REICHWEITE);
        // Animation: der Strahl wächst je Takt um zwei Blöcke und schrumpft beim Abschalten genauso von oben
        int laenge = 0;
        BlockPos.MutableBlockPos p = pos.mutable();
        for (int i = 1; i <= reichweite; i++) {
            p.move(richtung);
            if (!welt.getBlockState(p).is(ModBloecke.LASERSTRAHL)) {
                break;
            }
            laenge = i;
        }
        if (!an) {
            for (int i = laenge; i > Math.max(0, laenge - 2); i--) {
                welt.removeBlock(pos.relative(richtung, i), false);
            }
            return;
        }
        for (int neu = 0; neu < 2 && laenge < reichweite; neu++) {
            BlockPos q = pos.relative(richtung, laenge + 1);
            if (!welt.getBlockState(q).isAir()) {
                break;
            }
            welt.setBlock(q, strahl, 3);
            laenge++;
        }
        if (laenge == 0) {
            return;
        }
        // Wer steht im Strahl? In der Festung (Stufe 4) trifft er auch Monster und brennt richtig
        BlockPos ende = pos.relative(richtung, laenge);
        AABB bereich = new AABB(Vec3.atCenterOf(pos.relative(richtung)), Vec3.atCenterOf(ende)).inflate(0.3);
        boolean festung = z.festung();
        for (LivingEntity e : welt.getEntitiesOfClass(LivingEntity.class, bereich,
                x -> festung ? Festung.ziel(x, z) : z.istEindringling(x))) {
            if (e instanceof net.minecraft.world.entity.player.Player) {
                z.alarm(e.blockPosition(), Component.translatable("stinkendreich.alarm.grund.laser", e.getDisplayName()));
            }
            Kompat.schaden(e, welt, festung ? 12.0F : 3.0F);
            e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 30, 0));
            if (festung) {
                e.igniteForSeconds(4.0F);
            }
        }
    }
}
