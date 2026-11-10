package de.moinmornhart.stinkendreich.client.fernseher;

import de.moinmornhart.stinkendreich.Konfig;
import de.moinmornhart.stinkendreich.client.KompatClient;
import de.moinmornhart.stinkendreich.fernseher.FernseherEntity;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Verwaltet die Video-Abspieler aller Fernseher in der Nähe. Jeden Tick vergleicht es den Stand vom Server
 * („läuft seit“ / „angehalten bei“) mit dem, was hier läuft, und startet, hält an oder springt an die richtige
 * Stelle. Fernseher weiter als 48 Blöcke weg spielen nicht (spart Rechenzeit); kommt man zurück, geht es an der
 * Stelle weiter, an der alle anderen gerade sind. Über dem Hotbar steht, was gerade passiert (Laden, Fehler).
 */
public final class FernseherVideos {
    private static final Map<Integer, VideoAbspieler> SPIELER = new HashMap<>();
    /**
     * Abgelöste Abspieler (nach Pause → weiter, Spulen): Ihr letztes Bild bleibt stehen, bis der neue sein erstes Bild
     * hat (Philip: „dass … das Bild, was zuletzt da war, auch so bleibt und nicht wieder das Thumbnail kommt“).
     */
    private static final Map<Integer, VideoAbspieler> VORHER = new HashMap<>();
    private static final double REICHWEITE = 48;
    /** Fehler je Video und Stelle nur einmal im Chat melden. */
    private static final Set<String> GEMELDET = new HashSet<>();
    private static int takt;
    /**
     * Schon als bereit gemeldet: „Fernseher-ID@Video@Wartestelle“. Wird gelöscht, sobald der Fernseher nicht mehr wartet –
     * vorher blieb „Fernseher@0“ stehen, und beim nächsten Video ab Anfang meldete sich niemand mehr bereit: Der Fernseher
     * wartete jedes Mal 45 Sekunden (Philip, 10.11.: „gemeinsam YouTube schauen geht nicht“).
     */
    private static final Set<String> GEMELDET_BEREIT = new HashSet<>();

    private FernseherVideos() {
    }

    public static void registrieren() {
        ClientTickEvents.END_CLIENT_TICK.register(FernseherVideos::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(FernseherVideos::alleBeenden));
    }

    /** Textur mit dem laufenden Video für diesen Fernseher, sonst null (dann das Vorschaubild). */
    public static Identifier textur(int tvId) {
        VideoAbspieler p = SPIELER.get(tvId);
        Identifier t = p == null ? null : p.textur();
        VideoAbspieler alt = VORHER.get(tvId);
        if (alt == null) {
            return t;
        }
        if (t != null || p == null || p.zustand() == VideoAbspieler.Zustand.FEHLER) {
            VORHER.remove(tvId).close();         // der neue zeigt sein erstes Bild: das alte Standbild darf weg
            return t;
        }
        return alt.textur();
    }

    /** Neuer Abspieler für diesen Fernseher; der bisherige hält an und zeigt sein letztes Bild weiter, bis der neue so weit ist. */
    private static void ersetzen(int tvId, VideoAbspieler neu) {
        VideoAbspieler alt = SPIELER.put(tvId, neu);
        if (alt == null) {
            return;
        }
        alt.anhalten();
        VideoAbspieler davor = VORHER.put(tvId, alt);
        if (davor != null && davor != alt) {
            davor.close();
        }
    }

    private static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            if (!SPIELER.isEmpty()) {
                alleBeenden();
            }
            return;
        }
        Set<Integer> aktiv = new HashSet<>();
        FernseherEntity naechster = null;
        if (Konfig.get().fernseherVideos) {
            boolean bereit = VideoWerkzeuge.bereit();
            for (FernseherEntity tv : mc.level.getEntitiesOfClass(FernseherEntity.class, mc.player.getBoundingBox().inflate(REICHWEITE))) {
                VideoAbspieler p = SPIELER.get(tv.getId());
                if (tv.angehalten()) {
                    // Standbild: das letzte Bild bleibt stehen
                    if (p != null && p.kennung.equals(tv.video())) {
                        p.anhalten();
                        aktiv.add(tv.getId());
                    }
                    continue;
                }
                if (!tv.wartet()) {
                    String vorn = tv.getId() + "@";
                    GEMELDET_BEREIT.removeIf(k -> k.startsWith(vorn));
                }
                if (tv.wartet() && !bereit) {
                    // Hier kann das Video (noch) nicht laufen (Hilfsprogramme fehlen oder laden gerade): trotzdem „bereit“
                    // melden, damit die anderen nicht 45 Sekunden auf diesen Rechner warten
                    long stelle = tv.positionTicks(0);
                    if (GEMELDET_BEREIT.add(tv.getId() + "@" + tv.video() + "@" + stelle)) {
                        KompatClient.sende("fernseherbereit", tv.blockPosition(), tv.getId() + "|" + stelle);
                    }
                    if (tv.distanceToSqr(mc.player) < 16 * 16 && (naechster == null || tv.distanceToSqr(mc.player) < naechster.distanceToSqr(mc.player))) {
                        naechster = tv;
                    }
                    continue;
                }
                if (tv.wartet() && bereit) {
                    // Gemeinsam schauen: an der Startstelle vorladen, anhalten und dem Server „bereit“ melden
                    long stelle = tv.positionTicks(0);
                    if (p == null || !p.kennung.equals(tv.video()) || Math.abs(p.start - stelle / 20.0) > 0.5 || !p.halten) {
                        p = new VideoAbspieler(tv.getId(), tv.video(), stelle / 20.0, tv.getX(), tv.getY() + 1.4, tv.getZ());
                        p.halten = true;
                        ersetzen(tv.getId(), p);
                    }
                    // Bereit, sobald das Video an der Stelle geladen ist – oder wenn es hier gar nicht lädt (Fehler):
                    // dann wenigstens die anderen nicht aufhalten
                    boolean fertig = p.startbereit() || p.zustand() == VideoAbspieler.Zustand.FEHLER;
                    if (fertig && GEMELDET_BEREIT.add(tv.getId() + "@" + tv.video() + "@" + stelle)) {
                        KompatClient.sende("fernseherbereit", tv.blockPosition(), tv.getId() + "|" + stelle);
                    }
                    aktiv.add(tv.getId());
                    if (tv.distanceToSqr(mc.player) < 16 * 16 && (naechster == null || tv.distanceToSqr(mc.player) < naechster.distanceToSqr(mc.player))) {
                        naechster = tv;
                    }
                    continue;
                }
                if (!tv.laeuft()) {
                    continue;
                }
                if (p != null && p.halten && p.kennung.equals(tv.video())) {
                    p.halten = false;                // alle bereit: jetzt überall gleichzeitig los
                }
                if (tv.distanceToSqr(mc.player) < 16 * 16 && (naechster == null || tv.distanceToSqr(mc.player) < naechster.distanceToSqr(mc.player))) {
                    naechster = tv;
                }
                if (!bereit) {
                    continue;
                }
                double soll = tv.positionTicks(mc.level.getGameTime()) / 20.0;
                if (p == null || !p.kennung.equals(tv.video()) || p.mussNeuStarten(soll)) {
                    p = new VideoAbspieler(tv.getId(), tv.video(), soll, tv.getX(), tv.getY() + 1.4, tv.getZ());
                    ersetzen(tv.getId(), p);
                }
                aktiv.add(tv.getId());
            }
        }
        SPIELER.entrySet().removeIf(e -> {
            if (aktiv.contains(e.getKey())) {
                return false;
            }
            e.getValue().close();
            return true;
        });
        VORHER.entrySet().removeIf(e -> {
            if (SPIELER.containsKey(e.getKey())) {
                return false;
            }
            e.getValue().close();
            return true;
        });
        boolean pause = mc.isPaused();
        for (VideoAbspieler p : SPIELER.values()) {
            p.tick(pause);
            if (p.zustand() == VideoAbspieler.Zustand.FEHLER && GEMELDET.add(p.kennung + "@" + (int) p.start)) {
                KompatClient.chatNachricht(mc, Component.translatable("stinkendreich.fernseher.status.fehler", p.fehler())
                        .withStyle(ChatFormatting.RED));
            }
        }
        if (naechster != null && ++takt % 20 == 0) {
            status(mc, naechster);
        }
    }

    /** Hinweis über der Hotbar, solange beim nächsten Fernseher etwas lädt oder fehlt. */
    private static void status(Minecraft mc, FernseherEntity tv) {
        Component text = null;
        if (!VideoWerkzeuge.bereit()) {
            if (VideoWerkzeuge.laedt()) {
                text = Component.translatable("stinkendreich.fernseher.status.laden", VideoWerkzeuge.stand());
            } else if (!Konfig.get().fernseherHilfsprogrammeLaden) {
                text = Component.translatable("stinkendreich.fernseher.status.freigabe");
            } else if (VideoWerkzeuge.fehler().isEmpty()) {
                VideoWerkzeuge.laden();
            } else {
                text = Component.translatable("stinkendreich.fernseher.status.fehler", VideoWerkzeuge.fehler());
            }
        } else {
            VideoAbspieler p = SPIELER.get(tv.getId());
            if (tv.wartet() && p != null && p.startbereit()) {
                text = Component.translatable("stinkendreich.fernseher.status.warten", tv.wartenStand());
            } else if (p != null && p.zustand() == VideoAbspieler.Zustand.LAEDT) {
                text = Component.translatable("stinkendreich.fernseher.status.video");
            }
        }
        if (text != null) {
            de.moinmornhart.stinkendreich.Kompat.nachricht(mc.player, text, true);
        }
    }

    private static void alleBeenden() {
        SPIELER.values().forEach(VideoAbspieler::close);
        SPIELER.clear();
        VORHER.values().forEach(VideoAbspieler::close);
        VORHER.clear();
        GEMELDET.clear();
        GEMELDET_BEREIT.clear();
    }
}
