package de.moinmornhart.stinkendreich.butler.auftrag;

import com.google.gson.JsonObject;
import de.moinmornhart.stinkendreich.butler.ButlerEntity;
import net.minecraft.network.chat.Component;

/**
 * Ein Arbeitsschritt des Butlers, z. B. „32 Eisenerz abbauen“ oder „im Ofen schmelzen“.
 * Größere Bestellungen bestehen aus mehreren Aufträgen hintereinander (siehe {@code Planer}).
 * Aufträge laufen nur auf dem Server und werden mit dem Butler gespeichert.
 */
public abstract class Auftrag {
    public enum Stand { LAEUFT, FERTIG, FEHLER }

    /** Grund, falls der Auftrag mit {@link Stand#FEHLER} endet (geht an den Besitzer). */
    protected Component fehler = Component.empty();
    /** Ticks seit Beginn dieses Auftrags. */
    protected int ticks;

    /** Ein Arbeitsschritt pro Spieltick. */
    public final Stand schritt(ButlerEntity butler) {
        ticks++;
        return tick(butler);
    }

    protected abstract Stand tick(ButlerEntity butler);

    /** Kurzer Text für das Butler-Menü, z. B. „Baue Eisenerz ab (12/32)“. */
    public abstract Component status();

    /** Kennung zum Speichern. */
    public abstract String typ();

    /** Eigene Werte zum Speichern schreiben. */
    public abstract void schreiben(JsonObject j);

    /** Wird beim Abbrechen aufgerufen (z. B. Abbau-Risse wegnehmen, Truhe schließen). */
    public void abbrechen(ButlerEntity butler) {
    }

    public Component fehler() {
        return fehler;
    }

    protected Stand fehlschlag(Component grund) {
        fehler = grund;
        return Stand.FEHLER;
    }

    /** Stellt einen gespeicherten Auftrag wieder her (null, wenn unbekannt). */
    public static Auftrag laden(JsonObject j) {
        String typ = j.has("typ") ? j.get("typ").getAsString() : "";
        return switch (typ) {
            case SammelAuftrag.TYP -> SammelAuftrag.ausJson(j);
            case HolzAuftrag.TYP -> HolzAuftrag.ausJson(j);
            case JagdAuftrag.TYP -> JagdAuftrag.ausJson(j);
            case OfenAuftrag.TYP -> OfenAuftrag.ausJson(j);
            case HerstellAuftrag.TYP -> HerstellAuftrag.ausJson(j);
            case LieferAuftrag.TYP -> LieferAuftrag.ausJson(j);
            case VorratAuftrag.TYP -> VorratAuftrag.ausJson(j);
            case SortierAuftrag.TYP -> SortierAuftrag.ausJson(j);
            case GehAuftrag.TYP -> GehAuftrag.ausJson(j);
            case FahrAuftrag.TYP -> FahrAuftrag.ausJson(j);
            case FlugAuftrag.TYP -> FlugAuftrag.ausJson(j);
            case AbschleppAuftrag.TYP -> AbschleppAuftrag.ausJson(j);
            case AnrufAuftrag.TYP -> AnrufAuftrag.ausJson(j);
            default -> null;
        };
    }

    /** Speichert den Auftrag samt Typ. */
    public final JsonObject alsJson() {
        JsonObject j = new JsonObject();
        j.addProperty("typ", typ());
        schreiben(j);
        return j;
    }

    protected static int zahl(JsonObject j, String name, int standard) {
        return j.has(name) ? j.get(name).getAsInt() : standard;
    }

    protected static String text(JsonObject j, String name, String standard) {
        return j.has(name) ? j.get(name).getAsString() : standard;
    }
}
