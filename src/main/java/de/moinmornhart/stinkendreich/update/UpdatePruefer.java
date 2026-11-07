package de.moinmornhart.stinkendreich.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.moinmornhart.stinkendreich.Konfig;
import de.moinmornhart.stinkendreich.StinkendReich;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Sucht in den GitHub-Releases dieses Projekts nach einer neueren Version der Mod für die
 * laufende Minecraft-Version und lädt sie auf Wunsch herunter. Eingespielt wird die neue .jar
 * beim Beenden des Spiels (die alte ist bis dahin noch in Benutzung).
 */
public final class UpdatePruefer {
    public static final String REPO = "MoinMornhart/Minecraft-aber-du-bist-stinkend-Reich";
    public static final String RELEASES = "https://github.com/" + REPO + "/releases";

    /** Eine gefundene neuere Version. */
    public record Neu(String version, String dateiname, String adresse, long groesse, String seite) {
    }

    private static volatile Neu gefunden;
    private static volatile Path heruntergeladen;
    private static volatile boolean einspielenGeplant;

    private UpdatePruefer() {
    }

    public static Optional<Neu> gefunden() {
        return Optional.ofNullable(gefunden);
    }

    public static boolean heruntergeladen() {
        return heruntergeladen != null;
    }

    /** Fragt GitHub im Hintergrund; das Ergebnis steht danach in {@link #gefunden()}. */
    public static CompletableFuture<Optional<Neu>> pruefen() {
        if (!Konfig.get().updatePruefen) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpResponse<String> antwort = client().send(anfrage("https://api.github.com/repos/" + REPO + "/releases?per_page=30", true)
                        .header("Accept", "application/vnd.github+json").build(), HttpResponse.BodyHandlers.ofString());
                if (antwort.statusCode() != 200) {
                    StinkendReich.LOGGER.info("Update-Prüfung: GitHub antwortet mit {}", antwort.statusCode());
                    return Optional.<Neu>empty();
                }
                gefunden = auswaehlen(JsonParser.parseString(antwort.body()).getAsJsonArray());
                if (gefunden != null) {
                    StinkendReich.LOGGER.info("Neue Version {} für Minecraft {} verfügbar: {}", gefunden.version(), StinkendReich.MINECRAFT, gefunden.seite());
                }
                return Optional.ofNullable(gefunden);
            } catch (Exception e) {
                StinkendReich.LOGGER.info("Update-Prüfung nicht möglich ({})", e.getClass().getSimpleName());
                return Optional.<Neu>empty();
            }
        });
    }

    /** Neueste Version mit einer .jar für genau diese Minecraft-Version, falls neuer als die laufende. */
    static Neu auswaehlen(JsonArray releases) {
        Neu bester = null;
        // Läuft die Sammel-Datei für alle Minecraft-Versionen, wird wieder die Sammel-Datei angeboten
        String passend = alleInEiner() ? "+alle.jar" : "+mc" + StinkendReich.MINECRAFT + ".jar";
        for (JsonElement e : releases) {
            JsonObject r = e.getAsJsonObject();
            if (r.has("draft") && r.get("draft").getAsBoolean() || r.has("prerelease") && r.get("prerelease").getAsBoolean()) {
                continue;
            }
            String version = r.get("tag_name").getAsString().replaceFirst("^[vV]", "");
            if (vergleichen(version, StinkendReich.VERSION) <= 0 || (bester != null && vergleichen(version, bester.version()) <= 0)) {
                continue;
            }
            for (JsonElement a : r.getAsJsonArray("assets")) {
                JsonObject asset = a.getAsJsonObject();
                String name = asset.get("name").getAsString();
                if (name.startsWith(StinkendReich.MOD_ID + "-") && name.endsWith(passend)) {
                    bester = new Neu(version, name, asset.get("browser_download_url").getAsString(), asset.get("size").getAsLong(),
                            r.get("html_url").getAsString());
                }
            }
        }
        return bester;
    }

    /** Vergleicht Versionsnummern wie 0.10.2 und 0.9.0 Stelle für Stelle. */
    static int vergleichen(String a, String b) {
        String[] x = a.split("[.+-]"), y = b.split("[.+-]");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? zahl(x[i]) : 0, q = i < y.length ? zahl(y[i]) : 0;
            if (p != q) {
                return Integer.compare(p, q);
            }
        }
        return 0;
    }

    private static int zahl(String s) {
        try {
            return Integer.parseInt(s.replaceAll("\\D", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Lädt die neue .jar neben die alte (Endung .neu) und plant das Einspielen beim Beenden.
     * @return Meldungstext für den Spieler (deutsch, schon fertig formuliert über Sprachschlüssel)
     */
    public static CompletableFuture<String> herunterladen() {
        Neu neu = gefunden;
        if (neu == null) {
            return CompletableFuture.completedFuture("stinkendreich.update.keins");
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path eigene = eigeneJar();
                if (eigene == null) {
                    return "stinkendreich.update.entwicklung";
                }
                Path ziel = eigene.resolveSibling(neu.dateiname() + ".neu");
                HttpResponse<InputStream> antwort = client().send(anfrage(neu.adresse(), false).header("Accept", "application/octet-stream").build(),
                        HttpResponse.BodyHandlers.ofInputStream());
                if (antwort.statusCode() != 200) {
                    return "stinkendreich.update.fehler";
                }
                try (InputStream in = antwort.body()) {
                    Files.copy(in, ziel, StandardCopyOption.REPLACE_EXISTING);
                }
                if (neu.groesse() > 0 && Files.size(ziel) != neu.groesse()) {
                    Files.deleteIfExists(ziel);
                    return "stinkendreich.update.fehler";
                }
                heruntergeladen = ziel;
                einspielenBeimBeenden(eigene, ziel, eigene.resolveSibling(neu.dateiname()));
                return "stinkendreich.update.bereit";
            } catch (Exception e) {
                StinkendReich.LOGGER.warn("Update-Download fehlgeschlagen ({})", e.getClass().getSimpleName());
                return "stinkendreich.update.fehler";
            }
        });
    }

    /** Kennung der Sammel-Datei für alle Minecraft-Versionen (tools/alle_versionen.py). */
    private static final String ALLE = StinkendReich.MOD_ID + "_alle";

    /** Wurde die Mod aus der Sammel-Datei für alle Minecraft-Versionen geladen? */
    static boolean alleInEiner() {
        return FabricLoader.getInstance().isModLoaded(ALLE);
    }

    /**
     * Die .jar, aus der die Mod geladen wurde (null in der Entwicklungsumgebung). Bei der Sammel-Datei ist das die
     * Sammel-Datei selbst, nicht die darin eingebettete Fassung.
     */
    private static Path eigeneJar() {
        return FabricLoader.getInstance().getModContainer(alleInEiner() ? ALLE : StinkendReich.MOD_ID)
                .flatMap(m -> m.getOrigin().getPaths().stream().findFirst())
                .filter(p -> p.toString().endsWith(".jar") && Files.isRegularFile(p))
                .orElse(null);
    }

    /**
     * Beim Beenden startet ein kleiner Hilfsprozess, der wartet, bis Minecraft wirklich zu ist,
     * dann die alte .jar löscht und die neue an ihren Platz stellt (unter Windows ist eine
     * laufende .jar gesperrt, deshalb geht das nicht direkt).
     */
    private static synchronized void einspielenBeimBeenden(Path alt, Path neu, Path endgueltig) {
        if (einspielenGeplant) {
            return;
        }
        einspielenGeplant = true;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
                List<String> befehl = windows
                        ? List.of("cmd", "/c", "ping -n 4 127.0.0.1 >nul & del /f \"" + alt + "\" & move /y \"" + neu + "\" \"" + endgueltig + "\"")
                        : List.of("sh", "-c", "sleep 3; rm -f '" + alt + "'; mv -f '" + neu + "' '" + endgueltig + "'");
                new ProcessBuilder(befehl).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            } catch (IOException e) {
                // dann bleibt die .neu-Datei liegen und kann von Hand umbenannt werden
            }
        }, "Stinkend Reich – Update einspielen"));
    }

    private static HttpClient client() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    /** Anfrage an GitHub; der optionale Token geht nur an die API, nie an Download-Server. */
    private static HttpRequest.Builder anfrage(String adresse, boolean mitToken) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(adresse)).timeout(Duration.ofSeconds(60))
                .header("User-Agent", StinkendReich.MOD_ID + "/" + StinkendReich.VERSION);
        String token = Konfig.get().githubToken;
        if (mitToken && token != null && !token.isBlank()) {
            b.header("Authorization", "Bearer " + token.trim());
        }
        return b;
    }
}
