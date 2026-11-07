package de.moinmornhart.stinkendreich;

import de.moinmornhart.stinkendreich.auto.AutoSteuerung;
import de.moinmornhart.stinkendreich.butler.ButlerBefehle;
import de.moinmornhart.stinkendreich.butler.ButlerVerwaltung;
import de.moinmornhart.stinkendreich.butler.SpielerBloecke;
import de.moinmornhart.stinkendreich.dev.Autotest;
import de.moinmornhart.stinkendreich.mechanik.Aufzug;
import de.moinmornhart.stinkendreich.mechanik.SicherheitszentraleBlock;
import de.moinmornhart.stinkendreich.mechanik.TastenfeldBlock;
import de.moinmornhart.stinkendreich.mechanik.VillaZustand;
import de.moinmornhart.stinkendreich.musik.MusikanlageBlock;
import de.moinmornhart.stinkendreich.netz.Netz;
import de.moinmornhart.stinkendreich.register.ModBloecke;
import de.moinmornhart.stinkendreich.register.ModDeko;
import de.moinmornhart.stinkendreich.register.ModEntities;
import de.moinmornhart.stinkendreich.register.ModItems;
import de.moinmornhart.stinkendreich.register.ModMusik;
import de.moinmornhart.stinkendreich.register.ModSounds;
import de.moinmornhart.stinkendreich.register.ModTab;
import de.moinmornhart.stinkendreich.villa.VillaBefehle;
import de.moinmornhart.stinkendreich.villa.VillaPlatzierung;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Einstiegspunkt der Mod „Minecraft, aber du bist stinkend reich“ (Server und Client).
 */
public class StinkendReich implements ModInitializer {
    public static final String MOD_ID = "stinkendreich";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Ersatzwert, falls die Mod-Daten fehlen (Stonecutter setzt ihn nur in den nicht aktiven Versionen neu ein). */
    private static final String VERSION_QUELLTEXT = /*$ mod_version*/ "0.1.0";
    /**
     * Mod-Version aus fabric.mod.json, z. B. „0.1.0“ aus „0.1.0+mc26.3“. Die Datei bekommt sie beim
     * Bauen immer aus mod.version; die Update-Prüfung vergleicht mit genau diesem Wert.
     */
    public static final String VERSION = FabricLoader.getInstance().getModContainer(MOD_ID)
            .map(m -> m.getMetadata().getVersion().getFriendlyString().replaceFirst("\\+.*$", ""))
            .orElse(VERSION_QUELLTEXT);
    /** Minecraft-Version, für die diese .jar gebaut wurde. */
    public static final String MINECRAFT = /*$ minecraft*/ "26.3";

    @Override
    public void onInitialize() {
        LOGGER.info("Stinkend Reich {} für Minecraft {} startet", VERSION, MINECRAFT);
        Konfig.laden();
        if (net.fabricmc.loader.api.FabricLoader.getInstance().getEnvironmentType() == net.fabricmc.api.EnvType.SERVER) {
            WeltVonPaper.reparieren();
        }
        ModSounds.init();
        ModItems.init();
        ModBloecke.init();
        ModDeko.init();
        de.moinmornhart.stinkendreich.register.ModDecken.init();
        ModEntities.init();
        ModMusik.init();
        ModTab.init();
        Netz.registrieren();
        de.moinmornhart.stinkendreich.netz.ModErkennung.registrieren();
        VillaZustand.registrieren();
        Aufzug.registrieren();
        de.moinmornhart.stinkendreich.mechanik.Graben.registrieren();
        de.moinmornhart.stinkendreich.fernseher.FernbedienungBlock.registrieren();
        de.moinmornhart.stinkendreich.mechanik.Zugbruecke.registrieren();
        de.moinmornhart.stinkendreich.mechanik.SmartHome.registrieren();
        de.moinmornhart.stinkendreich.mechanik.Inkognito.registrieren();
        de.moinmornhart.stinkendreich.mechanik.Rutsche.registrieren();
        de.moinmornhart.stinkendreich.mechanik.Goldbad.registrieren();
        de.moinmornhart.stinkendreich.klavier.Klavier.registrieren();
        de.moinmornhart.stinkendreich.mechanik.Geheimgang.registrieren();
        de.moinmornhart.stinkendreich.mechanik.Haifalle.registrieren();
        de.moinmornhart.stinkendreich.strasse.Strassennetz.registrieren();
        de.moinmornhart.stinkendreich.auto.Garage.registrieren();
        de.moinmornhart.stinkendreich.handy.Handy.registrieren();
        de.moinmornhart.stinkendreich.fernseher.FernseherEntity.registrieren();
        de.moinmornhart.stinkendreich.ausruestung.AgentKraefte.registrieren();
        SicherheitszentraleBlock.aktionenRegistrieren();
        TastenfeldBlock.aktionenRegistrieren();
        MusikanlageBlock.aktionenRegistrieren();
        SpielerBloecke.registrieren();
        // Vor der Villa, damit die Butler-Liste geladen ist, wenn die Villa ihren Butler einstellt
        ButlerVerwaltung.registrieren();
        VillaPlatzierung.registrieren();
        de.moinmornhart.stinkendreich.villa.VillaNachruestung.registrieren();
        de.moinmornhart.stinkendreich.villa.VillaBaustelle.registrieren();
        VillaBefehle.registrieren();
        ButlerBefehle.registrieren();
        // Server-Version: liegt Polymer auf dem Server, sehen auch Spieler ohne Mod alles (ab 1.21.9)
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("polymer-core")) {
            //? if >=1.21.6 {
            de.moinmornhart.stinkendreich.server.ServerPaket.registrieren();
            //?} else
            /*LOGGER.warn("Polymer gefunden, die Server-Version für Spieler ohne Mod gibt es bisher nur ab Minecraft 1.21.9");*/
        }
        AutoSteuerung.registrieren();
        Autotest.registrieren();
        // Dedizierter Server: neue Versionen nur ins Protokoll schreiben (Spieler sehen den Hinweis im Client)
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (server.isDedicatedServer()) {
                de.moinmornhart.stinkendreich.update.UpdatePruefer.pruefen();
            }
        });
    }

    /** Erzeugt eine ID im Namensraum der Mod, z. B. {@code stinkendreich:villa}. */
    public static Identifier id(String pfad) {
        return Identifier.fromNamespaceAndPath(MOD_ID, pfad);
    }
}
