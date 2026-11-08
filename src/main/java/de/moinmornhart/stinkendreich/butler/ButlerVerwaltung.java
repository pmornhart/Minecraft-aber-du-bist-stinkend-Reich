package de.moinmornhart.stinkendreich.butler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.Konfig;
import de.moinmornhart.stinkendreich.StinkendReich;
import de.moinmornhart.stinkendreich.register.ModEntities;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Führt Buch, welcher Spieler welchen Butler hat (Datei {@code stinkendreich-butler.json} im
 * Weltordner), erzeugt den Butler der Villa und holt einen Butler herbei – auch wenn er gerade
 * weit weg in einem nicht geladenen Gebiet steht.
 */
public final class ButlerVerwaltung {
    private static final String DATEI = StinkendReich.MOD_ID + "-butler.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Besitzer → Eintrag. Butler ohne Besitzer (frisch in der Villa) stehen unter {@link #VILLA}. */
    private static final Map<String, Eintrag> EINTRAEGE = new HashMap<>();
    private static final String VILLA = "villa";
    private static final List<Ruf> RUFE = new ArrayList<>();
    private static Path pfad;

    private static final class Eintrag {
        String butler;
        String dimension;
        int x, y, z;
    }

    /** Ein laufender Ruf: Der Chunk des Butlers wird kurz geladen, bis er auftaucht. */
    /**
     * Ein Butler, auf dessen Laden gewartet wird. {@code folgen}: ausdrücklich gerufen (herbeiholen, dann folgt er);
     * {@code danach}: Aufträge, die ausgeführt werden, sobald er da ist (nichts geht verloren).
     */
    private static final class Ruf {
        final UUID spieler, butler;
        final ServerLevel welt;
        final int cx, cz;
        long bis;
        boolean folgen;
        final List<java.util.function.Consumer<ButlerEntity>> danach = new ArrayList<>();

        Ruf(UUID spieler, UUID butler, ServerLevel welt, int cx, int cz, long bis) {
            this.spieler = spieler;
            this.butler = butler;
            this.welt = welt;
            this.cx = cx;
            this.cz = cz;
            this.bis = bis;
        }
    }


    private ButlerVerwaltung() {
    }

    public static void registrieren() {
        ServerLifecycleEvents.SERVER_STARTED.register(ButlerVerwaltung::laden);
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
            for (Ruf r : RUFE) {
                r.welt.getChunkSource().updateChunkForced(new net.minecraft.world.level.ChunkPos(r.cx, r.cz), false);
            }
            RUFE.clear();
            speichern();
        });
        ServerTickEvents.END_SERVER_TICK.register(ButlerVerwaltung::rufeBearbeiten);
        // Der erste Spieler einer Villa-Welt bekommt den Butler der Villa
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer p = handler.getPlayer();
            Eintrag villa = EINTRAEGE.get(VILLA);
            if (villa != null && !EINTRAEGE.containsKey(p.getUUID().toString())) {
                EINTRAEGE.remove(VILLA);
                EINTRAEGE.put(p.getUUID().toString(), villa);
                speichern();
                ButlerEntity b = finden(server, UUID.fromString(villa.butler));
                if (b != null) {
                    b.besitzerEintragen(p.getUUID(), p.getScoreboardName());
                }
            }
        });
    }

    // ------------------------------------------------------------------
    // Erzeugen
    // ------------------------------------------------------------------

    /** Stellt den Butler der Villa an seinen Platz (bei der Spawn-Villa noch ohne Besitzer). */
    public static void villaButlerErzeugen(ServerLevel welt, BlockPos platz, ServerPlayer besitzer) {
        villaButlerErzeugen(welt, platz, besitzer == null ? null : besitzer.getUUID(), besitzer == null ? "" : besitzer.getScoreboardName());
    }

    /** Wie oben, der Besitzer muss dafür nicht online sein (Nachbarvilla, die fertig wird, während er weg ist). */
    public static void villaButlerErzeugen(ServerLevel welt, BlockPos platz, java.util.UUID besitzer, String besitzerName) {
        ButlerEntity b = erzeugen(welt, platz, besitzer, besitzerName);
        if (b != null) {
            EINTRAEGE.put(besitzer == null ? VILLA : besitzer.toString(), eintrag(b));
            speichern();
        }
    }

    private static ButlerEntity erzeugen(ServerLevel welt, BlockPos platz, ServerPlayer besitzer) {
        return erzeugen(welt, platz, besitzer == null ? null : besitzer.getUUID(), besitzer == null ? "" : besitzer.getScoreboardName());
    }

    private static ButlerEntity erzeugen(ServerLevel welt, BlockPos platz, java.util.UUID besitzer, String besitzerName) {
        ButlerEntity b = ModEntities.BUTLER.create(welt
                //? if >=1.21.2
                , net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED
        );
        if (b == null) {
            return null;
        }
        b.teleportTo(platz.getX() + 0.5, platz.getY(), platz.getZ() + 0.5);
        b.setCustomName(Component.literal(Konfig.get().butlerName));
        b.setzeHeimat(platz);
        if (besitzer != null) {
            b.besitzerEintragen(besitzer, besitzerName);
        }
        welt.addFreshEntity(b);
        return b;
    }

    /**
     * James zurücksetzen: alle Aufträge abbrechen, jeden James des Spielers (und jeden herrenlosen in seiner Villa)
     * entfernen und genau einen frischen James an seinen Platz in der Halle der Villa stellen. Was die alten in den
     * Taschen hatten, fällt dort heraus, wo sie standen. James in gerade nicht geladenen Gebieten gehen von selbst,
     * sobald man in ihre Nähe kommt (sie gehören dann nicht mehr dem Spieler).
     */
    public static void zuruecksetzen(ServerPlayer p) {
        MinecraftServer server = p.level().getServer();
        var villa = de.moinmornhart.stinkendreich.villa.VillaPlatzierung.vonBesitzer(p.getUUID());
        if (villa == null) {
            villa = de.moinmornhart.stinkendreich.villa.VillaPlatzierung.nr(
                    de.moinmornhart.stinkendreich.mechanik.VillaZustand.vonSpieler(server, p).nr());
        }
        int weg = 0;
        for (ServerLevel l : server.getAllLevels()) {
            List<ButlerEntity> alte = new ArrayList<>();
            for (Entity e : l.getAllEntities()) {
                if (e instanceof ButlerEntity b && b.istButler() && (p.getUUID().equals(b.besitzer())
                        || (b.besitzer() == null && villa != null && villa.inDerNaehe(b.blockPosition(), 0)))) {
                    alte.add(b);
                }
            }
            for (ButlerEntity b : alte) {
                b.stopp();
                for (int i = 0; i < b.taschen().getContainerSize(); i++) {
                    if (!b.taschen().getItem(i).isEmpty()) {
                        net.minecraft.world.level.block.Block.popResource(l, b.blockPosition(), b.taschen().removeItemNoUpdate(i));
                    }
                }
                b.discard();
                weg++;
            }
        }
        EINTRAEGE.remove(p.getUUID().toString());
        BlockPos platz = p.blockPosition();
        ServerLevel welt = server.overworld();
        if (villa != null) {
            com.google.gson.JsonArray m = villa.plan().ersteMarke("butler");
            platz = villa.weltPos(m.get(0).getAsDouble(), m.get(1).getAsDouble(), m.get(2).getAsDouble());
        } else if (p.level() instanceof ServerLevel eigene) {
            welt = eigene;
        }
        ButlerEntity neu = erzeugen(welt, platz, p);
        if (neu != null) {
            EINTRAEGE.put(p.getUUID().toString(), eintrag(neu));
            neu.sagen(Component.translatable("stinkendreich.butler.hallo", p.getDisplayName()));
        }
        speichern();
        Kompat.nachricht(p, Component.translatable("stinkendreich.butler.zurueckgesetzt", weg).withStyle(ChatFormatting.GOLD), false);
    }

    // ------------------------------------------------------------------
    // Besitzer
    // ------------------------------------------------------------------

    /** Macht den Spieler zum Besitzer eines herrenlosen Butlers. */
    static void besitzerSetzen(ButlerEntity b, ServerPlayer p) {
        Eintrag alt = EINTRAEGE.get(p.getUUID().toString());
        if (alt != null && !alt.butler.equals(b.getUUID().toString())) {
            // Hat schon einen Butler: dieser bleibt herrenlos
            return;
        }
        b.besitzerEintragen(p.getUUID(), p.getScoreboardName());
        EINTRAEGE.values().removeIf(e -> e.butler.equals(b.getUUID().toString()));
        EINTRAEGE.put(p.getUUID().toString(), eintrag(b));
        speichern();
        b.sagen(Component.translatable("stinkendreich.butler.hallo", p.getDisplayName()));
    }

    /**
     * Läuft jede Sekunde für jeden geladenen Butler: Besitzer aus dem Buch übernehmen, Ort merken,
     * und überzählige Doppelgänger (z. B. nach einem Ruf, als der alte nicht geladen war) entfernen.
     */
    static void pflegen(ButlerEntity b) {
        String eigene = b.getUUID().toString();
        if (b.besitzer() == null) {
            for (Map.Entry<String, Eintrag> e : EINTRAEGE.entrySet()) {
                if (!e.getKey().equals(VILLA) && e.getValue().butler.equals(eigene)) {
                    ServerPlayer p = b.welt().getServer().getPlayerList().getPlayer(UUID.fromString(e.getKey()));
                    b.besitzerEintragen(UUID.fromString(e.getKey()), p != null ? p.getScoreboardName() : "");
                }
            }
            return;
        }
        Eintrag e = EINTRAEGE.get(b.besitzer().toString());
        if (e == null) {
            EINTRAEGE.put(b.besitzer().toString(), eintrag(b));
        } else if (!e.butler.equals(eigene)) {
            // Der Besitzer hat inzwischen einen anderen Butler: Taschen ausleeren und gehen
            for (int i = 0; i < b.taschen().getContainerSize(); i++) {
                if (!b.taschen().getItem(i).isEmpty()) {
                    net.minecraft.world.level.block.Block.popResource(b.welt(), b.blockPosition(), b.taschen().removeItemNoUpdate(i));
                }
            }
            b.discard();
            return;
        }
        if (b.tickCount % 100 == 0) {
            Eintrag neu = eintrag(b);
            EINTRAEGE.put(b.besitzer().toString(), neu);
            // Besitzername aktuell halten (Umbenennung des Spielers)
            ServerPlayer p = b.besitzerOnline();
            if (p != null && !p.getScoreboardName().equals(b.besitzerName())) {
                b.besitzerEintragen(p.getUUID(), p.getScoreboardName());
            }
        }
    }

    private static Eintrag eintrag(ButlerEntity b) {
        Eintrag e = new Eintrag();
        e.butler = b.getUUID().toString();
        e.dimension = Kompat.schluesselId(b.level().dimension());
        e.x = b.blockPosition().getX();
        e.y = b.blockPosition().getY();
        e.z = b.blockPosition().getZ();
        return e;
    }

    // ------------------------------------------------------------------
    // Finden und Rufen
    // ------------------------------------------------------------------

    /** Sucht einen geladenen Butler in allen Dimensionen. */
    public static ButlerEntity finden(MinecraftServer server, UUID uuid) {
        for (ServerLevel l : server.getAllLevels()) {
            Entity e = l.getEntity(uuid);
            if (e instanceof ButlerEntity b && b.isAlive()) {
                return b;
            }
        }
        return null;
    }

    /** Der Butler des Spielers, falls geladen. */
    public static ButlerEntity vonSpieler(ServerPlayer p) {
        Eintrag e = EINTRAEGE.get(p.getUUID().toString());
        return e == null ? null : finden(p.level().getServer(), UUID.fromString(e.butler));
    }

    /**
     * Ruft den Butler des Spielers herbei. Hat er noch keinen, wird einer erzeugt (falls erlaubt).
     * Steht er in einem nicht geladenen Gebiet, wird es kurz geladen.
     */
    public static void rufen(ServerPlayer p) {
        rufen(p, true, null);
    }

    /**
     * Führt {@code danach} mit dem Butler des Spielers aus: sofort, wenn er geladen ist, sonst sobald er geladen wurde –
     * ohne ihn herbeizuteleportieren (seine Aufträge bringen ihn hin). Vorher gingen Bestellungen an einen nicht
     * geladenen James verloren („Sie haben noch keinen Butler“), und man musste zweimal bestellen.
     */
    public static void mitButler(ServerPlayer p, java.util.function.Consumer<ButlerEntity> danach) {
        ButlerEntity b = vonSpieler(p);
        if (b != null) {
            danach.accept(b);
            return;
        }
        rufen(p, false, danach);
    }

    private static void rufen(ServerPlayer p, boolean folgen, java.util.function.Consumer<ButlerEntity> danach) {
        MinecraftServer server = p.level().getServer();
        Eintrag e = EINTRAEGE.get(p.getUUID().toString());
        if (e == null) {
            if (!Konfig.get().butlerFuerJeden && !EINTRAEGE.isEmpty()) {
                Kompat.nachricht(p, Component.translatable("stinkendreich.butler.keinereigener").withStyle(ChatFormatting.RED), false);
                return;
            }
            ButlerEntity b = erzeugen((ServerLevel) p.level(), p.blockPosition(), p);
            if (b != null) {
                EINTRAEGE.put(p.getUUID().toString(), eintrag(b));
                speichern();
                b.sagen(Component.translatable("stinkendreich.butler.hallo", p.getDisplayName()));
                if (danach != null) {
                    danach.accept(b);
                }
            }
            return;
        }
        UUID uuid = UUID.fromString(e.butler);
        ButlerEntity b = finden(server, uuid);
        if (b != null) {
            if (folgen) {
                herbei(b, p, true);
            }
            if (danach != null) {
                danach.accept(b);
            }
            return;
        }
        // Wird er schon geholt? Dann nur anhängen (vorher: jeder Druck ein neuer Ruf, „Zur Stelle“ doppelt)
        for (Ruf r : RUFE) {
            if (r.butler.equals(uuid)) {
                r.folgen |= folgen;
                if (danach != null) {
                    r.danach.add(danach);
                }
                Kompat.nachricht(p, Component.translatable("stinkendreich.butler.unterwegs"), true);
                return;
            }
        }
        // Nicht geladen: Chunk kurz zwangsladen, bis der Butler auftaucht (höchstens 5 Sekunden)
        ServerLevel welt = null;
        for (ServerLevel l : server.getAllLevels()) {
            if (Kompat.schluesselId(l.dimension()).equals(e.dimension)) {
                welt = l;
            }
        }
        if (welt == null) {
            welt = server.overworld();
        }
        int cx = e.x >> 4, cz = e.z >> 4;
        // Ladeschein ohne sofortiges, blockierendes Laden; Figuren laden danach im Hintergrund (kann dauern)
        welt.getChunkSource().updateChunkForced(new net.minecraft.world.level.ChunkPos(cx, cz), true);
        Ruf ruf = new Ruf(p.getUUID(), uuid, welt, cx, cz, server.getTickCount() + 300);
        ruf.folgen = folgen;
        if (danach != null) {
            ruf.danach.add(danach);
        }
        RUFE.add(ruf);
        Kompat.nachricht(p, Component.translatable("stinkendreich.butler.unterwegs"), true);
    }

    private static void rufeBearbeiten(MinecraftServer server) {
        for (Iterator<Ruf> it = RUFE.iterator(); it.hasNext(); ) {
            Ruf r = it.next();
            ButlerEntity b = finden(server, r.butler);
            ServerPlayer p = server.getPlayerList().getPlayer(r.spieler);
            boolean abgelaufen = server.getTickCount() > r.bis;
            // Abgelaufen, aber die Figuren des Chunks sind noch gar nicht geladen (langsame Platte): weiter warten,
            // statt einen zweiten James einzustellen (höchstens noch einmal 15 Sekunden)
            if (b == null && abgelaufen && !Kompat.ganzGeladen(r.welt, new net.minecraft.core.BlockPos(r.cx << 4, 64, r.cz << 4)) && server.getTickCount() < r.bis + 300) {
                continue;
            }
            if (b == null && !abgelaufen) {
                continue;
            }
            r.welt.getChunkSource().updateChunkForced(new net.minecraft.world.level.ChunkPos(r.cx, r.cz), false);
            it.remove();
            if (p == null) {
                continue;
            }
            if (b != null) {
                if (r.folgen) {
                    herbei(b, p, true);
                }
                for (var d : r.danach) {
                    d.accept(b);
                }
            } else {
                // Verschollen (z. B. mit /kill entfernt): neuen Butler einstellen
                EINTRAEGE.remove(p.getUUID().toString());
                java.util.function.Consumer<ButlerEntity> alle = neu -> r.danach.forEach(d -> d.accept(neu));
                rufen(p, r.folgen, r.danach.isEmpty() ? null : alle);
            }
        }
    }

    /**
     * Holt einen geladenen Butler zum Spieler, auch über Dimensionen hinweg. Ausdrücklich gerufen ({@code folgen}): Er
     * lässt die laufende Arbeit liegen und folgt (vorher lief er nach „Zur Stelle“ gleich zurück zu Tunnel oder Ofen).
     */
    private static void herbei(ButlerEntity b, ServerPlayer p, boolean folgen) {
        if (folgen && !b.untaetig() && !b.faehrtGerade()) {
            b.stopp();
        }
        if (b.level() != p.level()) {
            Kompat.befehl(p.level().getServer(), String.format(Locale.ROOT, "execute in %s run tp %s %.2f %.2f %.2f",
                    Kompat.schluesselId(p.level().dimension()), b.getUUID(), p.getX(), p.getY(), p.getZ()));
            b = finden(p.level().getServer(), b.getUUID());
            if (b == null) {
                return;
            }
        } else if (b.distanceToSqr(p) > 6 * 6) {
            b.teleportiereNahe(p.blockPosition());
        }
        // Wer ihn ruft, dem folgt er; sonst würde ihn das Heimgehen gleich wieder nach Hause teleportieren
        b.setzeFolgen(true);
        b.sagen(Component.translatable("stinkendreich.butler.zurstelle"));
    }

    // ------------------------------------------------------------------
    // Datei
    // ------------------------------------------------------------------

    private static void laden(MinecraftServer server) {
        EINTRAEGE.clear();
        RUFE.clear();
        pfad = server.getWorldPath(LevelResource.ROOT).resolve(DATEI);
        if (!Files.exists(pfad)) {
            return;
        }
        try {
            JsonObject j = GSON.fromJson(Files.readString(pfad, StandardCharsets.UTF_8), JsonObject.class);
            for (var e : j.entrySet()) {
                EINTRAEGE.put(e.getKey(), GSON.fromJson(e.getValue(), Eintrag.class));
            }
        } catch (Exception ex) {
            StinkendReich.LOGGER.error("Butler-Liste konnte nicht gelesen werden", ex);
        }
    }

    private static void speichern() {
        if (pfad == null) {
            return;
        }
        try {
            Files.writeString(pfad, GSON.toJson(EINTRAEGE), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            StinkendReich.LOGGER.error("Butler-Liste konnte nicht gespeichert werden", ex);
        }
    }
}
