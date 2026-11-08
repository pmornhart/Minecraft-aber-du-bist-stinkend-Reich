package de.moinmornhart.stinkendreich.butler.auftrag;

import com.google.gson.JsonObject;
import de.moinmornhart.stinkendreich.butler.ButlerEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Kommt zum Besitzer („Komm her“) oder geht nach Hause. Ist der Weg zu weit oder versperrt,
 * springt der Butler wie ein zahmes Tier an sein Ziel.
 */
public class GehAuftrag extends Auftrag {
    public static final String TYP = "gehen";

    /** Wie lange höchstens auf einen Herrn in einer anderen Dimension gewartet wird (fünf Minuten). */
    private static final int ANDERE_WELT_MAX = 20 * 60 * 5;

    private final boolean heim;
    /** Ticks, die der Herr schon in einer anderen Dimension ist. */
    private int andereWeltTicks;

    public GehAuftrag(boolean heim) {
        this.heim = heim;
    }

    @Override
    protected Stand tick(ButlerEntity butler) {
        if (heim) {
            if (butler.distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(butler.zuhause())) > 64 * 64) {
                butler.teleportiereNahe(butler.zuhause());
            }
            ButlerEntity.Weg w = butler.gehZu(butler.zuhause(), 1.5);
            if (w == ButlerEntity.Weg.UNERREICHBAR) {
                butler.teleportiereNahe(butler.zuhause());
                return Stand.FERTIG;
            }
            return w == ButlerEntity.Weg.DA ? Stand.FERTIG : Stand.LAEUFT;
        }
        ServerPlayer p = butler.besitzerOnline();
        if (p == null) {
            return Stand.FERTIG;
        }
        if (p.level() != butler.level()) {
            // Der Herr ist in einer anderen Dimension: dort kann er nicht hin. Warten, bis er zurück ist
            // (die Koordinaten der anderen Dimension gelten hier nicht), nach fünf Minuten aufgeben.
            return ++andereWeltTicks > ANDERE_WELT_MAX ? Stand.FERTIG : Stand.LAEUFT;
        }
        andereWeltTicks = 0;
        if (butler.distanceToSqr(p) > 32 * 32) {
            butler.teleportiereNahe(p.blockPosition());
        }
        ButlerEntity.Weg w = butler.gehZu(p, 2.5);
        if (w == ButlerEntity.Weg.UNERREICHBAR) {
            butler.teleportiereNahe(p.blockPosition());
            return Stand.FERTIG;
        }
        return w == ButlerEntity.Weg.DA ? Stand.FERTIG : Stand.LAEUFT;
    }

    @Override
    public Component status() {
        return Component.translatable(heim ? "stinkendreich.butler.status.heim" : "stinkendreich.butler.status.kommt");
    }

    @Override
    public String typ() {
        return TYP;
    }

    @Override
    public void schreiben(JsonObject j) {
        j.addProperty("heim", heim);
    }

    static GehAuftrag ausJson(JsonObject j) {
        return new GehAuftrag(j.has("heim") && j.get("heim").getAsBoolean());
    }
}
