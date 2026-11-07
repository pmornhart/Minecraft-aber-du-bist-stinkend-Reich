package de.moinmornhart.stinkendreich;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Alle Einstellungen der Mod. Liegt als {@code config/stinkendreich.json} im Minecraft-Ordner
 * und wird beim ersten Start mit Standardwerten und Erklärungen angelegt.
 */
public final class Konfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static Konfig aktuell = new Konfig();

    // ---------- Villa ----------
    /** Villa in jeder neuen Welt am Spawn bauen. */
    public boolean villaBauen = true;
    /**
     * Auch in einer Welt von vorher (ohne Villa) die erste Villa bauen: beim ersten Betreten, im Hintergrund, auf dem
     * nächsten Platz ohne Spielerbauten rund um den Spawn.
     */
    public boolean villaInBestehendenWelten = true;
    /** Breite des Übergangs vom ebenen Grundstück ins natürliche Gelände (Blöcke). */
    public int villaAnblendung = 24;
    /** Spieler beim allerersten Betreten einer Welt vor die Villa stellen. */
    public boolean villaSpawnSetzen = true;
    /** Auf Servern bekommt jeder neue Spieler seine eigene Villa (Nachbarschaft). */
    public boolean mehrereVillen = true;
    /** Abstand der Villen in der Nachbarschaft (Blöcke von Mitte zu Mitte). */
    public int villenAbstand = 320;
    /**
     * Server-Version mit Polymer: das Ressourcenpaket für Spieler ohne Mod selbst bereitstellen (schaltet Polymers
     * „AutoHost“ ein, der ist sonst aus). Gilt auch für eine geöffnete Einzelspielerwelt (LAN, NoRisk „Hosted Worlds“).
     */
    public boolean serverRessourcenpaket = true;

    /** Wie viele versteckte Entschärfknöpfe kurz hintereinander gedrückt werden müssen. */
    public int tresorKnoepfe = 2;
    /** Lava statt Wasser im Graben, solange die Alarmanlage an und ein Bewohner zu Hause ist. */
    public boolean lavaGraben = true;

    // ---------- Butler ----------
    /** Name des Butlers (steht über seinem Kopf und vor seinen Nachrichten). */
    public String butlerName = "James";
    /** Arbeitstempo des Butlers (1.0 = so schnell wie ein Spieler mit Eisenwerkzeug). */
    public double butlerTempo = 1.0;
    /** Wie weit der Butler um seinen Startpunkt nach Erzen, Bäumen und Tieren sucht (Blöcke). */
    public int butlerSuchradius = 48;
    /** Darf der Butler für „Essen holen“ Tiere jagen? Es bleiben immer mindestens zwei Tiere je Art übrig. */
    public boolean butlerJagen = true;
    /** Umkreis um den Spieler, in dem „Truhen sortieren“ die Truhen und Fässer einsammelt (Blöcke). */
    public int butlerSortierRadius = 8;
    /** Jeder Spieler darf sich mit /butler rufen einen eigenen Butler holen. */
    public boolean butlerFuerJeden = true;
    /** Chat-Nachrichten, die mit diesem Wort (oder dem Namen des Butlers) beginnen, gehen an den Butler. */
    public String butlerAnrede = "Butler";

    /** Anthropic-API-Key für den Chat mit dem Butler. Leer = Butler nur über Befehle und Menü. */
    public String anthropicApiKey = "";
    /** Claude-Modell für den Butler-Chat. */
    public String butlerModell = "claude-opus-5-5";

    // ---------- Bonzenauto ----------
    /** Höchstgeschwindigkeit des Bonzenautos in km/h. */
    public double autoHoechstTempo = 110.0;
    /** Höchstgeschwindigkeit des Hubschraubers in km/h. */
    public double hubschrauberTempo = 130.0;
    /** Höchstgeschwindigkeit, wenn der Butler chauffiert (km/h). */
    public double chauffeurTempo = 100.0;
    /**
     * Vor der ersten Fahrt zu einem weiter entfernten Ziel (Dorf, Endportal, Netherportal, Mine, gemerkte Orte)
     * baut der Chauffeur eine echte Straße dorthin, ausgehend vom Kreisel vor der Villa. Aus = er fährt querfeldein.
     */
    public boolean strassenBauen = true;
    /** Ab dieser Entfernung (Blöcke) lohnt sich eine Straße. */
    public int strassenAbEntfernung = 96;

    // ---------- Musik ----------
    /** Hörweite rund um die Musikanlage und jeden ihrer Lautsprecher (Blöcke). */
    public int musikRadius = 24;
    /** Lautsprecher in diesem Umkreis um eine Musikanlage spielen deren Musik mit (Blöcke). */
    public int lautsprecherRadius = 16;
    /** Ordner für eigene Musik (OGG, MP3), relativ zum Minecraft-Ordner. */
    public String musikOrdner = "stinkendreich/musik";
    /** Client-ID der eigenen Spotify-App von developer.spotify.com. Leer = Spotify-Reiter zeigt nur die Anleitung. */
    public String spotifyClientId = "";
    /** Port für die Rückmeldung nach der Spotify-Anmeldung (Redirect-URI http://127.0.0.1:Port/callback). */
    public int spotifyPort = 8888;

    // ---------- Fernseher ----------
    /** YouTube-Videos im XXL-Fernseher abspielen (Bild und Ton im Spiel). Aus = nur Vorschaubild und Browser-Knopf. */
    public boolean fernseherVideos = true;
    /** Zustimmung, dass die Mod yt-dlp, FFmpeg und Deno herunterlädt (wird im Spiel einmal gefragt). */
    public boolean fernseherHilfsprogrammeLaden = false;
    /** Lautstärke der Fernseher, 0.0 bis 1.0 (dazu gilt der Regler „Jukebox/Notenblöcke“). */
    public double fernseherLautstaerke = 1.0;

    // ---------- Update ----------
    /** Beim Start auf GitHub nach einer neuen Version suchen. */
    public boolean updatePruefen = true;
    /** Optionaler GitHub-Token, nur nötig, falls das Repo privat ist. */
    public String githubToken = "";

    /** Stand der Datei (für das Anpassen alter Standardwerte beim Laden, nicht von Hand ändern). */
    public int konfigStand = 1;

    private Konfig() {
    }

    public static Konfig get() {
        return aktuell;
    }

    /** Lädt die Datei; fehlende Werte werden ergänzt und die Datei neu geschrieben. */
    public static void laden() {
        Path pfad = FabricLoader.getInstance().getConfigDir().resolve(StinkendReich.MOD_ID + ".json");
        try {
            if (Files.exists(pfad)) {
                JsonObject json = GSON.fromJson(Files.readString(pfad, StandardCharsets.UTF_8), JsonObject.class);
                json.remove("_erklaerung");
                Konfig geladen = GSON.fromJson(json, Konfig.class);
                if (geladen != null) {
                    if (!json.has("konfigStand")) {
                        // Stand 1 (0.4.8): James darf als Chauffeur 100 km/h fahren (Philip: „der kann auch mal die 100
                        // brettern“); der alte Standard 60 wird angehoben, eigene Werte bleiben
                        if (geladen.chauffeurTempo == 60.0) {
                            geladen.chauffeurTempo = 100.0;
                        }
                        geladen.konfigStand = 1;
                    }
                    aktuell = geladen;
                }
            }
            speichern(pfad);
        } catch (Exception e) {
            StinkendReich.LOGGER.error("Konfiguration {} konnte nicht gelesen werden, nutze Standardwerte", pfad, e);
        }
    }

    /** Schreibt die aktuellen Einstellungen in die Datei (z. B. nach einer Zustimmung im Spiel). */
    public static void speichern() {
        Path pfad = FabricLoader.getInstance().getConfigDir().resolve(StinkendReich.MOD_ID + ".json");
        try {
            speichern(pfad);
        } catch (IOException e) {
            StinkendReich.LOGGER.warn("Konfiguration {} konnte nicht gespeichert werden", pfad, e);
        }
    }

    private static void speichern(Path pfad) throws IOException {
        JsonObject json = GSON.toJsonTree(aktuell).getAsJsonObject();
        Map<String, String> erklaerung = new LinkedHashMap<>();
        erklaerung.put("villaBauen", "Villa in jeder neuen Welt am Spawn bauen (true/false)");
        erklaerung.put("villaInBestehendenWelten", "Auch in einer Welt von vorher die erste Villa bauen, auf einem freien Platz ohne Spielerbauten (true/false)");
        erklaerung.put("villaAnblendung", "Breite des Übergangs vom ebenen Grundstück ins Gelände in Blöcken");
        erklaerung.put("mehrereVillen", "Jeder neue Spieler bekommt seine eigene Villa (true/false)");
        erklaerung.put("villenAbstand", "Abstand der Villen in Blöcken (mindestens 240)");
        erklaerung.put("serverRessourcenpaket", "Mit Polymer: Ressourcenpaket für Spieler ohne Mod automatisch bereitstellen (true/false)");
        erklaerung.put("villaSpawnSetzen", "Spieler beim ersten Betreten vor die Villa stellen");
        erklaerung.put("tresorKnoepfe", "Anzahl versteckter Knöpfe, die zum Entschärfen der Tresorfallen nötig sind");
        erklaerung.put("butlerName", "Name des Butlers");
        erklaerung.put("butlerTempo", "Arbeitstempo des Butlers, 1.0 = wie ein Spieler mit Eisenwerkzeug");
        erklaerung.put("butlerSuchradius", "Suchradius des Butlers für Erze, Bäume und Tiere in Blöcken");
        erklaerung.put("butlerJagen", "Butler darf für Essen Tiere jagen (es bleiben immer mindestens zwei je Art übrig)");
        erklaerung.put("butlerSortierRadius", "Umkreis für „Truhen sortieren“ in Blöcken");
        erklaerung.put("butlerFuerJeden", "Jeder Spieler darf sich mit /butler rufen einen eigenen Butler holen");
        erklaerung.put("butlerAnrede", "Chat-Nachrichten, die mit diesem Wort oder dem Butler-Namen beginnen, gehen an den Butler");
        erklaerung.put("anthropicApiKey", "API-Key von console.anthropic.com für den Butler-Chat. Leer lassen = ohne Claude");
        erklaerung.put("butlerModell", "Claude-Modell für den Butler");
        erklaerung.put("hubschrauberTempo", "Höchstgeschwindigkeit des Hubschraubers in km/h");
        erklaerung.put("chauffeurTempo", "So schnell fährt der Butler als Chauffeur höchstens, in km/h");
        erklaerung.put("autoHoechstTempo", "Höchstgeschwindigkeit des Bonzenautos in km/h (zum Vergleich: Sprinten sind etwa 20 km/h)");
        erklaerung.put("musikRadius", "Hörweite der Musikanlage und jedes Lautsprechers in Blöcken");
        erklaerung.put("lautsprecherRadius", "Lautsprecher in diesem Umkreis um eine Musikanlage spielen mit (Blöcke)");
        erklaerung.put("musikOrdner", "Ordner für eigene Musik (OGG und MP3), relativ zum Minecraft-Ordner");
        erklaerung.put("spotifyClientId", "Client-ID deiner eigenen Spotify-App (developer.spotify.com, siehe README unter „Musik“). Kein Secret nötig. Leer = ohne Spotify");
        erklaerung.put("spotifyPort", "Port für die Spotify-Anmeldung. Bei Spotify muss genau http://127.0.0.1:<Port>/callback als Redirect-URI eingetragen sein");
        erklaerung.put("fernseherVideos", "YouTube-Videos im Fernseher abspielen (true/false); aus = nur Vorschaubild und Browser-Knopf");
        erklaerung.put("fernseherHilfsprogrammeLaden", "Zustimmung zum einmaligen Herunterladen von yt-dlp, FFmpeg und Deno nach stinkendreich/werkzeuge (wird im Spiel gefragt)");
        erklaerung.put("fernseherLautstaerke", "Lautstärke der Fernseher von 0.0 bis 1.0");
        erklaerung.put("updatePruefen", "Beim Start nach neuen Versionen auf GitHub suchen");
        erklaerung.put("githubToken", "Nur nötig, wenn das GitHub-Repo privat ist");
        JsonObject mitErklaerung = new JsonObject();
        mitErklaerung.add("_erklaerung", GSON.toJsonTree(erklaerung));
        json.entrySet().forEach(e -> mitErklaerung.add(e.getKey(), e.getValue()));
        Files.createDirectories(pfad.getParent());
        Files.writeString(pfad, GSON.toJson(mitErklaerung), StandardCharsets.UTF_8);
    }
}
