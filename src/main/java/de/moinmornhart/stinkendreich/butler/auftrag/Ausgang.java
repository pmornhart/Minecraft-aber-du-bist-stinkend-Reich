package de.moinmornhart.stinkendreich.butler.auftrag;

import de.moinmornhart.stinkendreich.butler.ButlerEntity;
import de.moinmornhart.stinkendreich.butler.Schutz;
import de.moinmornhart.stinkendreich.butler.Umgebung;
import de.moinmornhart.stinkendreich.villa.VillaPlatzierung;
import net.minecraft.core.BlockPos;

/**
 * Bringt den Butler vom Villa-Grundstück hinaus (durch das Tor über die Brücke), bevor er
 * draußen gräbt, fällt oder jagt. Auf dem Grundstück selbst baut er nie etwas ab.
 */
final class Ausgang {
    /** Punkt vor dem Tor, relativ zur Strukturdatei der Villa (Brücke führt nach Süden). */
    private static final double AUSGANG_X = 55.5, AUSGANG_Z = 117.5;

    private Ausgang() {
    }

    /** {@link Auftrag.Stand#LAEUFT}, solange er noch auf dem Grundstück ist. */
    static Auftrag.Stand hinaus(ButlerEntity butler) {
        if (!Schutz.inDerVilla(butler.level(), butler.blockPosition())) {
            return Auftrag.Stand.FERTIG;
        }
        BlockPos ziel = punkt(butler);
        if (ziel == null) {
            return Auftrag.Stand.FERTIG;
        }
        ButlerEntity.Weg w = butler.gehZu(ziel, 2.0);
        if (w == ButlerEntity.Weg.UNERREICHBAR) {
            BlockPos platz = butler.sichererPlatz(ziel, 4);
            if (platz == null || !butler.teleportiereNahe(platz)) {
                return Auftrag.Stand.FERTIG;
            }
        }
        return w == ButlerEntity.Weg.DA ? Auftrag.Stand.FERTIG : Auftrag.Stand.LAEUFT;
    }

    /**
     * Weltposition vor dem Tor (Höhe des Erdgeschosses) der Villa, auf deren Grundstück der Butler gerade steht (sonst
     * seiner eigenen), oder null ohne Villa.
     */
    static BlockPos punkt(ButlerEntity butler) {
        VillaPlatzierung.VillaInfo v = VillaPlatzierung.bei(butler.blockPosition());
        if (v == null) {
            v = Umgebung.eigeneVilla(butler);
        }
        return v == null ? null : v.weltPos(AUSGANG_X, v.plan().bodenY + 1, AUSGANG_Z);
    }
}
