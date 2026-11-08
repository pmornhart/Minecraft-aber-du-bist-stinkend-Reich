package de.moinmornhart.stinkendreich.mechanik;

import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.register.ModBloecke;
import de.moinmornhart.stinkendreich.register.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Getarnte Bodenplatte im Schmuck-Tresor. Wer sie als Eindringling betritt (kein Bewohner, keine Zugangskarte),
 * während das System scharf ist (Stufe ≥ 1) und die Fallen nicht entschärft sind, löst die Falle aus:
 * Alarm, Gitter vor der Tür (15 Sekunden), Giftgas und kurze Blindheit. Bewohner gehen einfach hinein.
 */
public class FallenplatteBlock extends MechanikBlock {
    private static final int RADIUS_GITTER = 10;

    public FallenplatteBlock(Properties eigenschaften) {
        super(eigenschaften, Ausrichtung.KEINE, false, false);
    }

    @Override
    protected Property<?>[] grundEigenschaften() {
        return new Property<?>[0];
    }

    @Override
    public void stepOn(Level welt, BlockPos pos, BlockState zustand, Entity entity) {
        if (welt.isClientSide() || !(entity instanceof Player spieler) || spieler.isSpectator()) {
            return;
        }
        ServerLevel server = (ServerLevel) welt;
        VillaZustand z = VillaZustand.fuer(server.getServer(), pos);
        MechanikBlockEntity daten = daten(welt, pos);
        long jetzt = welt.getGameTime();
        if (!SmartHome.wahl(z, "fallen", z.stufe() >= 1) || !z.istEindringling(spieler) || z.tresorEntschaerft() || daten == null
                || jetzt - daten.zahl("zuletzt", -1000) < 100) {
            return;
        }
        daten.setze("zuletzt", (int) jetzt);
        z.alarm(pos, Component.translatable("stinkendreich.alarm.grund.tresor", spieler.getScoreboardName()));
        Kompat.nachricht(spieler, Component.translatable("stinkendreich.tresor.falle").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
        spieler.addEffect(new MobEffectInstance(MobEffects.POISON, 20 * 6, 0));
        spieler.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20 * 4, 0));
        welt.playSound(null, pos, ModSounds.TRESOR, SoundSource.BLOCKS, 1.5F, 0.7F);
        gitterSetzen(server, pos, false);
        welt.scheduleTick(pos, this, 20 * 15);
    }

    @Override
    protected void tick(BlockState zustand, ServerLevel welt, BlockPos pos, RandomSource zufall) {
        gitterSetzen(welt, pos, true);
    }

    /** Öffnet oder schließt alle Gitterfallen in der Nähe. */
    private static void gitterSetzen(ServerLevel welt, BlockPos mitte, boolean offen) {
        for (BlockPos p : BlockPos.betweenClosed(mitte.offset(-RADIUS_GITTER, -3, -RADIUS_GITTER), mitte.offset(RADIUS_GITTER, 4, RADIUS_GITTER))) {
            BlockState s = welt.getBlockState(p);
            if (s.is(ModBloecke.GITTERFALLE)) {
                TorfluegelBlock.setzeOffen(welt, p, s, offen);
            }
        }
    }
}
