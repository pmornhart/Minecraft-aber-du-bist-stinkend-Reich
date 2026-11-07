package de.moinmornhart.stinkendreich.strasse;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.moinmornhart.stinkendreich.StinkendReich;
import de.moinmornhart.stinkendreich.villa.VillaPlatzierung;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Das Straßennetz der Welt: ein Kreisel vor dem Tor der Villa und Straßen, die von dort (oder von einer anderen
 * Straße) zu den Zielen führen. Jede Straße kennt ihre Eltern-Straße und die Stelle, an der sie abzweigt, so
 * entsteht ein Baum mit dem Kreisel als Wurzel. Darauf sucht {@link #route} den Weg für den Chauffeur.
 *
 * <p>Gespeichert wird im Weltordner als {@code stinkendreich-strassen.json}. Gebaut wird immer nur eine Straße zur
 * Zeit ({@link StrassenBau}), Stück für Stück in jedem Server-Tick.</p>
 */
public final class Strassennetz {
    private static final String DATEI = StinkendReich.MOD_ID + "-strassen.json";
    private static final Gson GSON = new GsonBuilder().create();

    /** Mitte des Kreisels in Villa-Koordinaten (auf der Achse von Tor und Haustür, südlich der Brücke). */
    static final double KREISEL_X = 55.0, KREISEL_Z = 131.0;
    /** Halbmesser: Insel, Außenrand der Fahrbahn, Fahrspur in der Mitte. */
    static final double INSEL = 4.5, AUSSEN = 10.5, SPUR = 7.5;
    /** Abstand der Wegpunkte für den Chauffeur entlang einer Straße (Blöcke). */
    private static final int WEGPUNKT_ABSTAND = 3;

    /** Eine gebaute Straße: Name des Ziels, Eltern-Straße (−1 = Kreisel), Abzweig-Index dort, Mittellinie. */
    public record Strasse(String name, int eltern, int abzweig, List<BlockPos> punkte) {
        BlockPos ende() {
            return punkte.get(punkte.size() - 1);
        }
    }

    private static Strassennetz aktuell;

    private MinecraftServer server;
    private Path pfad;
    private boolean kreiselGebaut;
    private final List<Strasse> strassen = new ArrayList<>();
    private StrassenBau bau;
    /** Treppe vom Straßenende hinunter ins Ziel (Mine, Endportal, Netherportal), die gerade entsteht. */
    private Treppenbau treppe;
    /** Fertige Treppen dieser Sitzung je Ziel („mine“, „endportal“ …), nur für die Testhilfe /butler treppenblick. */
    private final Map<String, Treppenbau> gebauteTreppen = new java.util.HashMap<>();
    /** Straßen, deren Treppe fertig ist (oder die keine brauchen). */
    private final java.util.Set<String> treppenFertig = new java.util.HashSet<>();

    private Strassennetz() {
    }

    public static void registrieren() {
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
            if (aktuell != null && aktuell.bau != null) {
                aktuell.bau.freigeben();
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            aktuell = null;
            VILLA_ZIELE.clear();
        });
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            if (aktuell != null && aktuell.server == s) {
                if (aktuell.bau != null) {
                    aktuell.bauTick();
                } else if (aktuell.treppe != null || (s.getTickCount() % 100 == 50 && aktuell.naechsteTreppe())) {
                    aktuell.treppeTick();
                } else if (!aktuell.warteschlange.isEmpty() && s.getTickCount() % 20 == 0) {
                    aktuell.naechsteAutomatisch();
                }
                if (s.getTickCount() % Strassenkehrer.ALLE == 0) {
                    aktuell.kehren();
                    aktuell.wegweiserPruefen();
                }
            }
        });
        // Sobald jemand die Welt betritt, entstehen die Straßen zu Dorf, Mine, Netherportal und Endportal von selbst
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> automatikStarten(server));
    }

    /**
     * Startet einmal je Spielsitzung die Straßen zu Dorf, Mine, Netherportal und Endportal und bindet die Nachbarvillen
     * an (schon vorhandene Straßen werden beim Abarbeiten übersprungen). Beim Betreten der Welt und sobald die erste
     * Villa einer Welt von vorher steht.
     */
    public static void automatikStarten(MinecraftServer server) {
        Strassennetz netz = get(server);
        if (!netz.automatikGestartet && VillaPlatzierung.villa().isPresent()) {
            netz.automatikGestartet = true;
            netz.warteschlange.addAll(AUTOMATISCH);
            for (int i = 1; i < VillaPlatzierung.alle().size(); i++) {
                netz.villaAnbinden(i);
            }
        }
    }

    /** Kennung einer Nachbarvilla in der Warteschlange: „villa:“ + Nummer. */
    private static final String VILLA = "villa:";

    /** Eine Nachbarvilla ans Netz anschließen (die Straße entsteht, sobald kein anderer Bau läuft). */
    public void villaAnbinden(int nr) {
        if (nr > 0 && !warteschlange.contains(VILLA + nr)) {
            warteschlange.addFirst(VILLA + nr);             // Villen vor Dorf, Mine & Co.
        }
    }

    /**
     * Straße vom Netz bis ans Ende der Zufahrt einer Nachbarvilla. Die Zufahrt führt schon vom Tor auf natürliche
     * Geländehöhe hinab bzw. hinauf, die Straße schließt dort an. Liegt das Netz hinter der Villa (nördlich ihrer
     * Südkante), entsteht zuerst ein Zubringer seitlich an Villa und Minengürtel vorbei, danach das letzte Stück.
     */
    private void villaStrasseBauen(ServerLevel welt, VillaPlatzierung.VillaInfo v) {
        if (v == null || v.nr() == 0) {
            return;
        }
        String name = villaName(v);
        Vec3 punkt = villaZiel(welt, v);
        Vec3 netz = kreiselMitte();
        if (netz == null || strasseZu(name, punkt) >= 0) {
            return;
        }
        double suedkante = v.ursprung().getZ() + v.plan().breiteZ;
        String zubringer = name + " – Zubringer";
        if (netz.z < suedkante - 10 && strasseZu(zubringer, punkt) < 0 && !hatStrasse(zubringer)) {
            double seiteX = netz.x < v.ursprung().getX() + v.plan().breiteX / 2.0
                    ? v.ursprung().getX() - 64 : v.ursprung().getX() + v.plan().breiteX + 64;
            StrassenBau b = bauen(welt, new Vec3(seiteX, punkt.y, suedkante + 24), zubringer);
            StinkendReich.LOGGER.info("Straßennetz: baue {} ({})", zubringer, b == null ? "ging nicht" : "seitlich vorbei");
            warteschlange.addFirst(VILLA + v.nr());            // danach das letzte Stück bis zur Zufahrt
            return;
        }
        StrassenBau b = bauen(welt, punkt, name);
        if (b != null && b.name().equals(name)) {
            // Höhe der Zufahrt am Ziel (punkt ist die Stehhöhe über ihrem Ende, drei Blöcke dahinter; ohne gefundene
            // Zufahrt der Spawnpunkt: unbekannt)
            BlockPos p = BlockPos.containing(punkt.x, punkt.y, punkt.z - 3);
            if (welt.getBlockState(p.below()).is(StrassenBau.ASPHALT.getBlock()) || welt.getBlockState(p).is(Blocks.POLISHED_ANDESITE_SLAB)) {
                b.zielHoehe(p.getY() - 1);
            }
            b.anfahrt(new Vec3(punkt.x, punkt.y, punkt.z + 10));     // von vorn an die Zufahrt (sie zeigt nach Süden)
        }
        StinkendReich.LOGGER.info("Straßennetz: baue Straße zur {} ({})", name, b == null ? "ging nicht" : BlockPos.containing(punkt));
    }

    private boolean hatStrasse(String name) {
        return strassen.stream().anyMatch(s -> s.name().equals(name));
    }

    /** Name der Straße zu einer Villa: „Villa von Name“ (ohne Besitzer: „Villa Nummer“). */
    public static String villaName(VillaPlatzierung.VillaInfo v) {
        return v.besitzerName().isEmpty()
                ? net.minecraft.network.chat.Component.translatable("stinkendreich.strasse.ziel.villa_nr", v.nr()).getString()
                : net.minecraft.network.chat.Component.translatable("stinkendreich.strasse.ziel.villa", v.besitzerName()).getString();
    }

    /** Wo die Straße zu einer Nachbarvilla endet: kurz hinter dem Ende ihrer Zufahrt (je Villa einmal gesucht). */
    private static final Map<Integer, Vec3> VILLA_ZIELE = new java.util.concurrent.ConcurrentHashMap<>();

    public static Vec3 villaZiel(ServerLevel welt, VillaPlatzierung.VillaInfo v) {
        return VILLA_ZIELE.computeIfAbsent(v.nr(), n -> {
            BlockPos ende = VillaPlatzierung.zufahrtEnde(welt, v);
            return ende == null ? Vec3.atBottomCenterOf(v.spawn()) : new Vec3(ende.getX() + 0.5, ende.getY(), ende.getZ() + 3.5);
        });
    }

    /** Ziele, zu denen gleich beim Betreten Straßen gebaut werden (in dieser Reihenfolge). */
    private static final List<String> AUTOMATISCH = List.of("dorf", "mine", "netherportal", "endportal");
    /** Noch zu bauende Ziele der Automatik (nicht gespeichert: beim nächsten Start wird erneut geprüft). */
    private final java.util.ArrayDeque<String> warteschlange = new java.util.ArrayDeque<>();
    private boolean automatikGestartet;

    /** Nächstes Ziel der Automatik suchen und bauen; gibt es keins oder schon eine Straße, das übernächste. */
    private void naechsteAutomatisch() {
        var villa = VillaPlatzierung.villa().orElse(null);
        String fest = warteschlange.poll();
        if (villa == null || fest == null) {
            return;
        }
        ServerLevel welt = server.overworld();
        if (fest.startsWith(VILLA)) {
            villaStrasseBauen(welt, VillaPlatzierung.nr(Integer.parseInt(fest.substring(VILLA.length()))));
            return;
        }
        StrassenZiele.Ziel ziel = StrassenZiele.finden(welt, fest, villa.spawn());
        if (ziel == null) {
            StinkendReich.LOGGER.info("Straßennetz: kein Ziel „{}“ in Reichweite", fest);
            return;
        }
        Vec3 punkt = Vec3.atBottomCenterOf(ziel.pos());
        String name = net.minecraft.network.chat.Component.translatable("stinkendreich.strasse.ziel." + fest).getString();
        if (strasseZu(name, punkt) >= 0) {
            return;
        }
        StrassenBau b = bauen(welt, punkt, name);
        StinkendReich.LOGGER.info("Straßennetz: baue Straße nach {} ({})", name, b == null ? "ging nicht" : ziel.pos());
    }

    /**
     * Alle Straßen neu bauen (Befehl /butler strassen neu): Das Netz vergisst seine Straßen und baut die zu Dorf, Mine,
     * Netherportal und Endportal mit dem aktuellen Straßenbau neu. Alte Straßenblöcke bleiben liegen; die neuen
     * Straßen führen darüber bzw. daneben.
     */
    /**
     * Prüfhilfe (/butler strassen pruefen): je Straße, wie viele Punkte unter der Erde liegen (über der Fahrbahn steht
     * bis zum Himmel noch ein fester Block) und wie viele als Brücke über Wasser führen.
     */
    public List<String> pruefen() {
        ServerLevel welt = server.overworld();
        List<String> zeilen = new ArrayList<>();
        for (Strasse s : strassen) {
            int unter = 0, bruecke = 0, keinBelag = 0, hindernis = 0;
            for (BlockPos p : s.punkte()) {
                welt.getChunk(p.getX() >> 4, p.getZ() >> 4);
                int oben = welt.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ()) - 1;
                if (oben > p.getY() + 1) {
                    unter++;
                    if (unter <= 3) {
                        StringBuilder drueber = new StringBuilder();
                        for (int h = 1; h <= oben - p.getY(); h++) {
                            drueber.append(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(welt.getBlockState(p.above(h)).getBlock()).getPath()).append(' ');
                        }
                        StinkendReich.LOGGER.info("STRASSE {} unter der Erde bei {} (Oberfläche {}, darüber {})", s.name(), p.toShortString(), oben, drueber);
                    }
                }
                // Ein ganzer fester Block direkt auf der Fahrbahn (halbe Stufen sind in Ordnung)
                for (int quer = -2; quer <= 2; quer++) {
                    BlockPos q = p.offset(quer, 1, 0);
                    net.minecraft.world.level.block.state.BlockState drauf = welt.getBlockState(q);
                    if (net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(welt.getBlockState(q.below()).getBlock()).getPath().equals("gray_concrete")
                            && drauf.isCollisionShapeFullBlock(welt, q)) {
                        hindernis++;
                        if (hindernis <= 3) {
                            StinkendReich.LOGGER.info("STRASSE {} Hindernis {} bei {}", s.name(),
                                    net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(drauf.getBlock()).getPath(), q.toShortString());
                        }
                        break;
                    }
                }
                net.minecraft.world.level.block.state.BlockState belag = welt.getBlockState(p);
                if (belag.isAir() || !belag.getFluidState().isEmpty()) {
                    keinBelag++;
                }
                for (int h = 1; h <= 12; h++) {
                    if (!welt.getFluidState(p.below(h)).isEmpty()) {
                        bruecke++;
                        break;
                    }
                    if (!welt.getBlockState(p.below(h)).isAir()) {
                        break;
                    }
                }
            }
            String z = s.name() + ": " + s.punkte().size() + " Punkte, " + unter + " unter der Erde, " + bruecke + " Brücke über Wasser, "
                    + keinBelag + " ohne Fahrbahn, " + hindernis + " Hindernisse";
            StinkendReich.LOGGER.info("STRASSE {}", z);
            zeilen.add(z);
        }
        return zeilen;
    }

    public void neuBauen() {
        strassen.clear();
        treppe = null;
        treppenFertig.clear();
        if (bau != null) {
            bau.freigeben();
        }
        bau = null;
        warteschlange.clear();
        warteschlange.addAll(AUTOMATISCH);
        automatikGestartet = true;
        wegweiser.neuPlanen();
        speichern();
        StinkendReich.LOGGER.info("Straßennetz: alle Straßen werden neu gebaut");
    }

    public static Strassennetz get(MinecraftServer server) {
        if (aktuell == null || aktuell.server != server) {
            aktuell = laden(server);
        }
        return aktuell;
    }

    // ------------------------------------------------------------------
    // Kreisel
    // ------------------------------------------------------------------

    /** Mitte des Kreisels in Weltkoordinaten (Höhe = Fahrbahnblock) oder null ohne Villa. */
    public static Vec3 kreiselMitte() {
        return VillaPlatzierung.villa().map(v -> new Vec3(v.ursprung().getX() + KREISEL_X, v.ursprung().getY() + v.plan().bodenY,
                v.ursprung().getZ() + KREISEL_Z)).orElse(null);
    }

    /** Punkt auf dem Kreisel (Fahrspur) in Richtung {@code gier} (Minecraft-Gierwinkel). */
    static Vec3 aufDemKreisel(Vec3 mitte, double gier, double radius) {
        double r = Math.toRadians(gier);
        return new Vec3(mitte.x - Math.sin(r) * radius, mitte.y + 1, mitte.z + Math.cos(r) * radius);
    }

    /** Gierwinkel von a nach b. */
    static double gier(double ax, double az, double bx, double bz) {
        return Math.toDegrees(Math.atan2(-(bx - ax), bz - az));
    }

    // ------------------------------------------------------------------
    // Bauen
    // ------------------------------------------------------------------

    public StrassenBau aktuellerBau() {
        return bau;
    }

    /** Gibt es schon eine Straße zu diesem Ziel (Name gleich oder Ende höchstens 32 Blöcke vom Ziel)? */
    public int strasseZu(String name, Vec3 ziel) {
        for (int i = 0; i < strassen.size(); i++) {
            Strasse s = strassen.get(i);
            if (s.name().equals(name) || Vec3.atCenterOf(s.ende()).distanceTo(ziel) < 32) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Startet den Bau einer Straße zum Ziel (läuft danach von selbst Tick für Tick). Gibt es schon eine, wird
     * nichts gebaut. Sie beginnt am Kreisel oder zweigt von der Straße ab, die dem Ziel am nächsten kommt.
     */
    public StrassenBau bauen(ServerLevel welt, Vec3 ziel, String name) {
        if (bau != null) {
            return bau;
        }
        Vec3 mitte = kreiselMitte();
        if (mitte == null) {
            return null;
        }
        if (!kreiselGebaut) {
            StrassenBau.kreiselBauen(welt, mitte);
            kreiselGebaut = true;
            speichern();
        }
        // Start: Abzweig von der nächsten Straße, wenn das deutlich näher am Ziel liegt als der Kreisel
        double gierKreisel = gier(mitte.x, mitte.z, ziel.x, ziel.z);
        // Nach Norden liegt die Villa: dorthin geht keine neue Straße ab
        double nachNorden = wrap(gierKreisel - 180.0);
        if (Math.abs(nachNorden) < 50) {
            gierKreisel = 180.0 + (nachNorden < 0 ? -50 : 50);
        }
        Vec3 start = aufDemKreisel(mitte, gierKreisel, AUSSEN + 0.5);
        int eltern = -1, abzweig = 0;
        double besteEntfernung = start.distanceTo(ziel) - 50;
        for (int i = 0; i < strassen.size(); i++) {
            List<BlockPos> pkt = strassen.get(i).punkte();
            for (int j = 0; j < pkt.size(); j += 4) {
                double d = Vec3.atCenterOf(pkt.get(j)).distanceTo(ziel);
                VillaPlatzierung.VillaInfo drin = VillaPlatzierung.bei(pkt.get(j));
                if (drin != null && drin.inDerNaehe(pkt.get(j), 8)) {
                    continue;                                   // überbaut (Villa später darauf gesetzt): nicht abzweigen
                }
                if (naheFreiemPlatz(pkt.get(j))) {
                    continue;                                   // an der Kante eines freien Platzes ginge es nicht weiter
                }
                if (d < besteEntfernung) {
                    besteEntfernung = d;
                    eltern = i;
                    abzweig = j;
                }
            }
        }
        if (eltern >= 0) {
            BlockPos p = strassen.get(eltern).punkte().get(abzweig);
            start = new Vec3(p.getX() + 0.5, p.getY() + 1, p.getZ() + 0.5);
        }
        bau = new StrassenBau(welt, name, ziel, start, (float) gier(start.x, start.z, ziel.x, ziel.z), eltern, abzweig);
        return bau;
    }

    /** Liegt die Stelle auf oder dicht (12 Blöcke) an einem freigehaltenen Platz für eine künftige Villa? */
    private static boolean naheFreiemPlatz(BlockPos p) {
        for (int dx = -12; dx <= 12; dx += 12) {
            for (int dz = -12; dz <= 12; dz += 12) {
                if (VillaPlatzierung.reservierterPlatz(p.getX() + dx, p.getZ() + dz) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Sucht eine Straße zu Mine, Endportal oder Netherportal ohne Treppe und beginnt sie (true, wenn eine begonnen wurde). */
    private boolean naechsteTreppe() {
        for (Strasse st : strassen) {
            String art = Wegweiser.festesZiel(st.name());
            if (art != null && !art.equals("dorf") && !treppenFertig.contains(st.name() + "@" + Treppenbau.STIL) && !st.punkte().isEmpty()) {
                treppe = new Treppenbau(server.overworld(), st.name(), art, st.ende());
                return true;
            }
        }
        return false;
    }

    private void treppeTick() {
        if (treppe == null) {
            return;
        }
        treppe.tick();
        if (treppe.fertig()) {
            treppenFertig.add(treppe.name + "@" + Treppenbau.STIL);
            gebauteTreppen.put(treppe.art, treppe);
            treppe = null;
            speichern();
        }
    }

    private void bauTick() {
        bau.tick();
        if (bau.fertig()) {
            if (bau.punkte().size() > 8) {
                strassen.add(new Strasse(bau.name(), bau.eltern(), bau.abzweig(), List.copyOf(bau.punkte())));
                speichern();
            }
            bau = null;
        }
    }

    // ------------------------------------------------------------------
    // Weg suchen
    // ------------------------------------------------------------------

    /** Ort im Netz: Straße und Index (Straße −1 = Kreisel, dann ist {@code index} der Gierwinkel). */
    private record Stelle(int strasse, int index) {
    }

    /**
     * Weg über das Netz vom Start zum Ziel als Wegpunkte für den Chauffeur, oder null, wenn es zum Ziel noch keine
     * Straße gibt oder der Start zu weit vom Netz entfernt ist. {@code start} kann {@code null} sein: dann beginnt
     * der Weg am Kreisel (Abfahrt von der Villa).
     */
    public List<Vec3> route(Vec3 start, Vec3 ziel, String name) {
        Vec3 mitte = kreiselMitte();
        int zielStrasse = strasseZu(name, ziel);
        if (mitte == null || zielStrasse < 0) {
            return null;
        }
        Stelle von = start == null ? new Stelle(-1, 180) : naechsteStelle(start, mitte, 80);
        if (von == null) {
            return null;
        }
        Stelle nach = new Stelle(zielStrasse, strassen.get(zielStrasse).punkte().size() - 1);
        List<Stelle> a = vorfahren(von), b = vorfahren(nach);
        // Gemeinsame Straße suchen (sonst treffen sich beide Wege am Kreisel)
        int ia = -1, ib = -1;
        outer:
        for (int i = 0; i < a.size(); i++) {
            for (int j = 0; j < b.size(); j++) {
                if (a.get(i).strasse() == b.get(j).strasse() && a.get(i).strasse() >= 0) {
                    ia = i;
                    ib = j;
                    break outer;
                }
            }
        }
        List<Vec3> weg = new ArrayList<>();
        if (ia >= 0) {
            hinauf(a, ia, weg);
            entlang(a.get(ia).strasse(), a.get(ia).index(), b.get(ib).index(), weg);
            hinab(b, ib, weg);
        } else {
            // Bis zum Anfang der Wurzelstraße, um den Kreisel herum (gegen den Uhrzeigersinn) und wieder hinaus
            hinauf(a, a.size() - 1, weg);
            double gierVon = a.get(a.size() - 1).index(), gierNach = b.get(b.size() - 1).index();
            kreiselFahrt(mitte, gierVon, gierNach, weg);
            hinab(b, b.size() - 1, weg);
        }
        // Endet die Straße vor dem Ziel (etwa am Dorfrand, weil dort Häuser stehen), hält der Wagen am Straßenende
        BlockPos ende = strassen.get(zielStrasse).ende();
        if (Math.hypot(ende.getX() + 0.5 - ziel.x, ende.getZ() + 0.5 - ziel.z) < 12) {
            weg.add(ziel);
        }
        return weg;
    }

    /** Liegt die Stelle höchstens 80 Blöcke vom Straßennetz entfernt (dann kann James mit der Limo fahren)? */
    public boolean amNetz(Vec3 pos) {
        Vec3 mitte = kreiselMitte();
        return mitte != null && (pos.distanceTo(mitte) < 80 || naechsteStelle(pos, mitte, 80) != null);
    }

    /**
     * Weg vom Start über das Netz bis zum Kreisel und dort zur Ausfahrt nach Norden (zur Villa), oder null, wenn der
     * Start weiter als 80 Blöcke vom Netz entfernt ist.
     */
    public List<Vec3> routeHeim(Vec3 start) {
        Vec3 mitte = kreiselMitte();
        if (mitte == null || !kreiselGebaut) {
            return null;
        }
        Stelle von = naechsteStelle(start, mitte, 80);
        if (von == null) {
            return null;
        }
        List<Stelle> a = vorfahren(von);
        List<Vec3> weg = new ArrayList<>();
        hinauf(a, a.size() - 1, weg);
        kreiselFahrt(mitte, a.get(a.size() - 1).index(), 180, weg);
        weg.add(new Vec3(mitte.x, mitte.y + 1, mitte.z - AUSSEN - 2));
        return weg;
    }

    /** Kette von der Stelle bis zum Kreisel: (Straße, Index), dann (Eltern, Abzweig) … bis (−1, Gierwinkel). */
    private List<Stelle> vorfahren(Stelle s) {
        List<Stelle> kette = new ArrayList<>();
        Stelle jetzt = s;
        int sicher = 0;
        while (jetzt.strasse() >= 0 && sicher++ < 64) {
            kette.add(jetzt);
            Strasse st = strassen.get(jetzt.strasse());
            if (st.eltern() < 0) {
                Vec3 m = kreiselMitte();
                BlockPos p0 = st.punkte().get(0);
                kette.add(new Stelle(-1, (int) Math.round(gier(m.x, m.z, p0.getX() + 0.5, p0.getZ() + 0.5))));
                return kette;
            }
            jetzt = new Stelle(st.eltern(), st.abzweig());
        }
        kette.add(jetzt);
        return kette;
    }

    /** Von a[0] hinauf bis vor a[bis]: jeweils die Straße zurück bis zu ihrem Anfang. */
    private void hinauf(List<Stelle> a, int bis, List<Vec3> weg) {
        for (int i = 0; i < bis; i++) {
            Stelle s = a.get(i);
            if (s.strasse() >= 0) {
                entlang(s.strasse(), s.index(), 0, weg);
            }
        }
    }

    /** Von b[ab] hinab bis b[0]: jeweils die Straße vom Anfang bis zur Stelle. */
    private void hinab(List<Stelle> b, int ab, List<Vec3> weg) {
        for (int i = ab - 1; i >= 0; i--) {
            Stelle s = b.get(i);
            entlang(s.strasse(), 0, s.index(), weg);
        }
    }

    /** Wegpunkte entlang einer Straße von Index {@code von} bis {@code bis} (vorwärts oder rückwärts). */
    private void entlang(int strasse, int von, int bis, List<Vec3> weg) {
        List<BlockPos> pkt = strassen.get(strasse).punkte();
        int schritt = von <= bis ? WEGPUNKT_ABSTAND : -WEGPUNKT_ABSTAND;
        for (int i = von; schritt > 0 ? i < bis : i > bis; i += schritt) {
            weg.add(Vec3.atBottomCenterOf(pkt.get(i).above()));
        }
        weg.add(Vec3.atBottomCenterOf(pkt.get(bis).above()));
    }

    /** Um den Kreisel herum (in Fahrtrichtung gegen den Uhrzeigersinn, von oben gesehen) zur Ausfahrt. */
    private static void kreiselFahrt(Vec3 mitte, double gierVon, double gierNach, List<Vec3> weg) {
        weg.add(aufDemKreisel(mitte, gierVon, SPUR));
        // Gegen den Uhrzeigersinn heißt im Minecraft-Gierwinkel: der Winkel nimmt ab
        double d = wrap(gierVon - gierNach);
        if (d < 0) {
            d += 360;
        }
        for (double w = 30; w < d; w += 30) {
            weg.add(aufDemKreisel(mitte, gierVon - w, SPUR));
        }
        weg.add(aufDemKreisel(mitte, gierNach, SPUR));
    }

    /** Nächste Stelle im Netz (Straße oder Kreisel) höchstens {@code max} Blöcke vom Punkt, sonst null. */
    private Stelle naechsteStelle(Vec3 p, Vec3 mitte, double max) {
        Stelle beste = null;
        double besteD = max;
        double dk = Math.hypot(p.x - mitte.x, p.z - mitte.z) - AUSSEN;
        if (dk < besteD) {
            besteD = Math.max(0, dk);
            beste = new Stelle(-1, (int) Math.round(gier(mitte.x, mitte.z, p.x, p.z)));
        }
        for (int i = 0; i < strassen.size(); i++) {
            List<BlockPos> pkt = strassen.get(i).punkte();
            for (int j = 0; j < pkt.size(); j += 2) {
                BlockPos q = pkt.get(j);
                double d = Math.hypot(q.getX() + 0.5 - p.x, q.getZ() + 0.5 - p.z);
                if (d < besteD) {
                    besteD = d;
                    beste = new Stelle(i, j);
                }
            }
        }
        return beste;
    }

    static double wrap(double grad) {
        double g = grad % 360;
        if (g >= 180) {
            g -= 360;
        }
        if (g < -180) {
            g += 360;
        }
        return g;
    }

    // ------------------------------------------------------------------
    // Auskunft
    // ------------------------------------------------------------------

    /** Führt eine Straße durch das Rechteck (x0, z0)–(x1, z1)? (Für die Platzwahl neuer Villen.) */
    public boolean strasseIn(int x0, int z0, int x1, int z1) {
        for (Strasse s : strassen) {
            for (BlockPos p : s.punkte()) {
                if (p.getX() >= x0 && p.getX() <= x1 && p.getZ() >= z0 && p.getZ() <= z1) {
                    return true;
                }
            }
        }
        // auch die Straße, die gerade entsteht
        return bau != null && bau.punkte().stream().anyMatch(p -> p.getX() >= x0 && p.getX() <= x1 && p.getZ() >= z0 && p.getZ() <= z1);
    }

    private final Strassenkehrer kehrer = new Strassenkehrer();
    private final Wegweiser wegweiser = new Wegweiser();

    /** Testhilfe: Standort und Blickpunkt auf der zuletzt gebauten Treppe (siehe {@link Treppenbau#blick}). */
    public double[] treppenBlick(String art, double anteil) {
        Treppenbau t = gebauteTreppen.get(art);
        return t == null ? null : t.blick(anteil);
    }

    /** Testhilfe: Standort und Blickpunkt vor dem Wegweiser {@code nr} (siehe {@link Wegweiser#blick}). */
    public double[] wegweiserBlick(int nr) {
        return wegweiser.blick(server.overworld(), nr);
    }

    /** Wegweiser an Kreisel und Abzweigen aufstellen bzw. anpassen (siehe {@link Wegweiser}). */
    private void wegweiserPruefen() {
        // Heimweg: „Villa von …“; ohne Besitzer (noch niemand eingezogen) einfach „Villa“
        String heim = VillaPlatzierung.villa().map(v -> v.besitzerName().isEmpty()
                ? net.minecraft.network.chat.Component.translatable("stinkendreich.strasse.ziel.villa_heim").getString() : villaName(v)).orElse(null);
        if (wegweiser.tick(server.overworld(), strassen, kreiselMitte(), heim)) {
            speichern();
        }
    }

    /** Liegengebliebenes an den Straßen wegräumen (fertige Straßen und die, die gerade entsteht). */
    private void kehren() {
        List<List<BlockPos>> strecken = new ArrayList<>();
        for (Strasse s : strassen) {
            strecken.add(s.punkte());
        }
        if (bau != null) {
            strecken.add(bau.punkte());
        }
        kehrer.kehren(server.overworld(), strecken);
        kehrer.wasserFernhalten(server.overworld());
    }

    public List<Strasse> strassen() {
        return Collections.unmodifiableList(strassen);
    }

    /** Ziele mit Straße und ihre Länge (für /strasse und das Handy). */
    public Map<String, Integer> uebersicht() {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (Strasse s : strassen) {
            m.put(s.name(), s.punkte().size());
        }
        return m;
    }

    // ------------------------------------------------------------------
    // Speichern
    // ------------------------------------------------------------------

    private static Strassennetz laden(MinecraftServer server) {
        Strassennetz n = new Strassennetz();
        n.server = server;
        n.pfad = server.getWorldPath(LevelResource.ROOT).resolve(DATEI);
        try {
            if (Files.exists(n.pfad)) {
                JsonObject j = GSON.fromJson(Files.readString(n.pfad, StandardCharsets.UTF_8), JsonObject.class);
                n.kreiselGebaut = j.has("kreisel") && j.get("kreisel").getAsBoolean();
                for (var e : j.getAsJsonArray("strassen")) {
                    JsonObject s = e.getAsJsonObject();
                    List<BlockPos> punkte = new ArrayList<>();
                    for (var p : s.getAsJsonArray("punkte")) {
                        punkte.add(BlockPos.of(p.getAsLong()));
                    }
                    n.strassen.add(new Strasse(s.get("name").getAsString(), s.get("eltern").getAsInt(), s.get("abzweig").getAsInt(), punkte));
                }
                if (j.has("wegweiser")) {
                    n.wegweiser.laden(j.getAsJsonObject("wegweiser"));
                }
                if (j.has("treppen")) {
                    j.getAsJsonArray("treppen").forEach(t -> n.treppenFertig.add(t.getAsString()));
                }
            }
        } catch (Exception ex) {
            StinkendReich.LOGGER.warn("Straßennetz nicht lesbar: {}", ex.toString());
        }
        return n;
    }

    private void speichern() {
        JsonObject j = new JsonObject();
        j.addProperty("kreisel", kreiselGebaut);
        JsonArray liste = new JsonArray();
        for (Strasse s : strassen) {
            JsonObject o = new JsonObject();
            o.addProperty("name", s.name());
            o.addProperty("eltern", s.eltern());
            o.addProperty("abzweig", s.abzweig());
            JsonArray p = new JsonArray();
            s.punkte().forEach(b -> p.add(b.asLong()));
            o.add("punkte", p);
            liste.add(o);
        }
        j.add("strassen", liste);
        j.add("wegweiser", wegweiser.speichern());
        JsonArray treppen = new JsonArray();
        treppenFertig.forEach(treppen::add);
        j.add("treppen", treppen);
        try {
            Files.writeString(pfad, GSON.toJson(j), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            StinkendReich.LOGGER.warn("Straßennetz nicht gespeichert: {}", ex.toString());
        }
    }
}
