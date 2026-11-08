package de.moinmornhart.stinkendreich.butler.auftrag;

import com.google.gson.JsonObject;
import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.butler.ButlerEntity;
import de.moinmornhart.stinkendreich.butler.Quellen;
import de.moinmornhart.stinkendreich.butler.Taschen;
import de.moinmornhart.stinkendreich.butler.Umgebung;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.List;

/**
 * Bringt das Bestellte zum Besitzer (in sein Inventar, Rest vor die Füße) oder in eine Truhe:
 * die mit „/butler truhe“ festgelegte, sonst die Lieferkiste der Villa.
 */
public class LieferAuftrag extends Auftrag {
    public static final String TYP = "liefern";

    private final String quelle;
    private final int menge;
    private final boolean inTruhe;
    private BlockPos truhe;
    private boolean truheGesucht;
    private int geliefert = -1;
    private int deckelTicks;

    /** @param menge Anzahl, oder -1 für alles Passende */
    public LieferAuftrag(String quelle, int menge, boolean inTruhe) {
        this.quelle = quelle;
        this.menge = menge;
        this.inTruhe = inTruhe;
    }

    @Override
    protected Stand tick(ButlerEntity butler) {
        Quellen.Quelle q = Quellen.get(quelle);
        if (q == null) {
            return fehlschlag(Component.translatable("stinkendreich.butler.unbekannt", quelle));
        }
        int vorhanden = butler.taschen().zaehlen(q.passt());
        int anzahl = menge < 0 ? vorhanden : Math.min(menge, vorhanden);
        if (anzahl <= 0 && geliefert < 0) {
            return Stand.FERTIG; // nichts zu liefern (die Meldung kam schon vom Sammeln)
        }
        if (geliefert >= 0) {
            // Truhendeckel kurz offen lassen, dann schließen
            if (++deckelTicks >= 15) {
                deckel(butler, truhe, false);
                return Stand.FERTIG;
            }
            return Stand.LAEUFT;
        }
        if (inTruhe && !truheGesucht) {
            truheGesucht = true;
            truhe = butler.liefertruhe();
            if (truhe == null) {
                List<BlockPos> kiste = Umgebung.villaMarken(butler, "lieferkiste");
                truhe = kiste.isEmpty() ? null : kiste.get(0);
            }
            if (truhe != null && !(butler.welt().getBlockEntity(truhe) instanceof Container)) {
                truhe = null;
            }
        }
        if (inTruhe && truhe != null) {
            return inDieTruhe(butler, q, anzahl);
        }
        return zumBesitzer(butler, q, anzahl);
    }

    private Stand zumBesitzer(ButlerEntity butler, Quellen.Quelle q, int anzahl) {
        ServerPlayer p = butler.besitzerOnline();
        if (p == null) {
            return Stand.LAEUFT; // wartet, bis der Herr wieder da ist
        }
        if (p.level() != butler.level()) {
            // Der Herr ist im Nether oder im End: warten, bis er zurück ist (die Koordinaten dort gelten hier nicht)
            return Stand.LAEUFT;
        }
        if (butler.distanceToSqr(p) > 48 * 48) {
            butler.teleportiereNahe(p.blockPosition());
            return Stand.LAEUFT;
        }
        ButlerEntity.Weg w = butler.gehZu(p, 2.5);
        if (w == ButlerEntity.Weg.UNERREICHBAR) {
            butler.teleportiereNahe(p.blockPosition());
            return Stand.LAEUFT;
        }
        if (w != ButlerEntity.Weg.DA) {
            return Stand.LAEUFT;
        }
        butler.getLookControl().setLookAt(p, 30.0F, 30.0F);
        int gegeben = 0;
        for (ItemStack s : butler.taschen().entnehmen(q.passt(), anzahl)) {
            gegeben += s.getCount();
            if (!p.getInventory().add(s) && !s.isEmpty()) {
                net.minecraft.world.level.block.Block.popResource(p.level(), p.blockPosition(), s);
            }
        }
        Kompat.schwingen(butler);
        butler.playSound(SoundEvents.ITEM_PICKUP, 0.6F, 1.0F);
        butler.sagen(Component.translatable("stinkendreich.butler.bitteschoen", gegeben, q.anzeigeStapel().getHoverName()));
        return Stand.FERTIG;
    }

    private Stand inDieTruhe(ButlerEntity butler, Quellen.Quelle q, int anzahl) {
        // hinkommen: in Reichweite gilt als angekommen, sonst springt er notfalls daneben (z. B. unter einer niedrigen Decke)
        ButlerEntity.Weg w = butler.hinkommen(truhe, 2.2);
        if (w == ButlerEntity.Weg.UNERREICHBAR || !(butler.welt().getBlockEntity(truhe) instanceof Container)) {
            // Kein Platz neben der Truhe (oder sie ist weg): dem Herrn persönlich bringen
            truhe = null;
            return Stand.LAEUFT;
        }
        if (w != ButlerEntity.Weg.DA || !(butler.welt().getBlockEntity(truhe) instanceof Container c)) {
            return Stand.LAEUFT;
        }
        butler.schauAuf(truhe);
        deckel(butler, truhe, true);
        int gelegt = 0;
        for (ItemStack s : butler.taschen().entnehmen(q.passt(), anzahl)) {
            ItemStack rest = Taschen.einlegen(c, s);
            gelegt += s.getCount() - rest.getCount();
            if (!rest.isEmpty()) {
                butler.taschen().einpacken(rest);
            }
        }
        Kompat.schwingen(butler);
        butler.sagen(Component.translatable(gelegt < anzahl ? "stinkendreich.butler.truhevoll" : "stinkendreich.butler.intruhe",
                gelegt, q.anzeigeStapel().getHoverName()));
        geliefert = gelegt;
        return Stand.LAEUFT;
    }

    /** Öffnet oder schließt den Deckel einer Truhe sichtbar und hörbar. */
    public static void deckel(ButlerEntity butler, BlockPos pos, boolean auf) {
        if (pos == null) {
            return;
        }
        var welt = butler.welt();
        var be = welt.getBlockEntity(pos);
        var zustand = welt.getBlockState(pos);
        if (be instanceof ChestBlockEntity && zustand.getBlock() instanceof ChestBlock) {
            welt.blockEvent(pos, zustand.getBlock(), 1, auf ? 1 : 0);
            welt.playSound(null, pos, auf ? SoundEvents.CHEST_OPEN : SoundEvents.CHEST_CLOSE, net.minecraft.sounds.SoundSource.BLOCKS, 0.5F, 1.0F);
        } else if (be instanceof BarrelBlockEntity) {
            welt.playSound(null, pos, auf ? SoundEvents.BARREL_OPEN : SoundEvents.BARREL_CLOSE, net.minecraft.sounds.SoundSource.BLOCKS, 0.5F, 1.0F);
        }
    }

    @Override
    public void abbrechen(ButlerEntity butler) {
        if (geliefert >= 0) {
            deckel(butler, truhe, false);
        }
    }

    @Override
    public Component status() {
        Quellen.Quelle q = Quellen.get(quelle);
        Component name = q == null ? Component.literal(quelle) : q.anzeigeStapel().getHoverName();
        return Component.translatable(inTruhe ? "stinkendreich.butler.status.liefertruhe" : "stinkendreich.butler.status.liefert", name);
    }

    @Override
    public String typ() {
        return TYP;
    }

    @Override
    public void schreiben(JsonObject j) {
        j.addProperty("quelle", quelle);
        j.addProperty("menge", menge);
        j.addProperty("truhe", inTruhe);
    }

    static LieferAuftrag ausJson(JsonObject j) {
        return new LieferAuftrag(text(j, "quelle", "essen"), zahl(j, "menge", -1), j.has("truhe") && j.get("truhe").getAsBoolean());
    }
}
