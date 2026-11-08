package de.moinmornhart.stinkendreich.butler.auftrag;

import com.google.gson.JsonObject;
import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.butler.ButlerEntity;
import de.moinmornhart.stinkendreich.butler.Quellen;
import de.moinmornhart.stinkendreich.butler.Taschen;
import de.moinmornhart.stinkendreich.butler.Umgebung;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Holt Gegenstände aus dem Vorrat der Villa (Speisekammer in der Küche), z. B. fertiges Essen.
 */
public class VorratAuftrag extends Auftrag {
    public static final String TYP = "vorrat";

    private final String quelle;
    private final int menge;
    private int geholt;
    private int truheIndex;
    private int deckelTicks = -1;

    public VorratAuftrag(String quelle, int menge) {
        this.quelle = quelle;
        this.menge = menge;
    }

    /** Wie viel davon gerade im Vorrat der Villa liegt. */
    public static int imVorrat(ButlerEntity butler, String quelle) {
        Quellen.Quelle q = Quellen.get(quelle);
        if (q == null) {
            return 0;
        }
        Level welt = butler.level();
        int n = 0;
        for (BlockPos p : Umgebung.villaMarken(butler, "vorrat")) {
            if (welt.getBlockEntity(p) instanceof Container c) {
                n += Taschen.zaehlen(c, q.passt());
            }
        }
        return n;
    }

    @Override
    protected Stand tick(ButlerEntity butler) {
        Quellen.Quelle q = Quellen.get(quelle);
        List<BlockPos> truhen = Umgebung.villaMarken(butler, "vorrat");
        if (q == null || geholt >= menge || truheIndex >= truhen.size()) {
            return Stand.FERTIG;
        }
        BlockPos t = truhen.get(truheIndex);
        if (deckelTicks >= 0) {
            if (++deckelTicks > 12) {
                LieferAuftrag.deckel(butler, t, false);
                deckelTicks = -1;
                truheIndex++;
            }
            return Stand.LAEUFT;
        }
        ButlerEntity.Weg w = butler.hinkommen(t, 2.2);
        if (w == ButlerEntity.Weg.UNERREICHBAR) {
            truheIndex++;
        } else if (w == ButlerEntity.Weg.DA) {
            butler.schauAuf(t);
            if (butler.welt().getBlockEntity(t) instanceof Container c) {
                LieferAuftrag.deckel(butler, t, true);
                for (ItemStack s : Taschen.entnehmen(c, q.passt(), menge - geholt)) {
                    geholt += s.getCount();
                    ItemStack rest = butler.taschen().einpacken(s);
                    if (!rest.isEmpty()) {
                        Taschen.einlegen(c, rest);
                    }
                }
                Kompat.schwingen(butler);
                deckelTicks = 0;
            } else {
                truheIndex++;
            }
        }
        return Stand.LAEUFT;
    }

    @Override
    public Component status() {
        Quellen.Quelle q = Quellen.get(quelle);
        return Component.translatable("stinkendreich.butler.status.vorrat", q == null ? Component.literal(quelle) : q.anzeigeStapel().getHoverName());
    }

    @Override
    public String typ() {
        return TYP;
    }

    @Override
    public void schreiben(JsonObject j) {
        j.addProperty("quelle", quelle);
        j.addProperty("menge", menge);
        j.addProperty("geholt", geholt);
    }

    static VorratAuftrag ausJson(JsonObject j) {
        VorratAuftrag a = new VorratAuftrag(text(j, "quelle", "essen"), zahl(j, "menge", 1));
        a.geholt = zahl(j, "geholt", 0);
        return a;
    }
}
