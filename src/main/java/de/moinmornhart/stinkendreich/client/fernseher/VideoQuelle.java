package de.moinmornhart.stinkendreich.client.fernseher;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Wo Bild und Ton eines YouTube-Videos liegen. yt-dlp liefert dazu die Beschreibung des Videos als JSON: die Adressen
 * von Bild- und Tonspur (bevorzugt H.264 bis 480p, das kann jeder Rechner leicht dekodieren), die HTTP-Kopfzeilen,
 * die YouTube erwartet, die Länge und ob es ein Livestream ist.
 *
 * @param bild       Adresse (oder Datei) der Bildspur
 * @param ton        Adresse (oder Datei) der Tonspur
 * @param kopfzeilen HTTP-Kopfzeilen für FFmpeg (ohne User-Agent)
 * @param agent      User-Agent für FFmpeg (leer = Standard)
 * @param dauer      Länge in Sekunden (0 = unbekannt)
 * @param live       Livestream (dann wird nicht gespult)
 */
record VideoQuelle(String bild, String ton, Map<String, String> kopfzeilen, String agent, double dauer, boolean live,
                   String seite, String bildFormat, String tonFormat, String infoDatei) {
    /** Bild bis 480p (H.264 bevorzugt) und die beste Tonspur, notfalls eine Datei mit beidem. */
    private static final String FORMAT = "bv*[height<=480][vcodec^=avc1]+ba[acodec^=mp4a]/bv*[height<=480]+ba/b[height<=480]/b";

    static VideoQuelle holen(String kennung) throws IOException, InterruptedException {
        String test = VideoWerkzeuge.testvideo();
        if (!test.isEmpty()) {
            // Nur im Test: dieser Spieler lädt künstlich langsam (gemeinsam schauen: die anderen müssen warten)
            String langsam = System.getenv("STINKENDREICH_TESTVIDEO_LANGSAM");
            if (langsam != null && langsam.equals(net.minecraft.client.Minecraft.getInstance().getUser().getName())) {
                Thread.sleep(10_000);
            }
            return new VideoQuelle(test, test, Map.of(), "", 0, false, "", "", "", "");
        }
        VideoWerkzeuge.ytdlpAktualisieren();
        Path ytdlp = VideoWerkzeuge.finden(VideoWerkzeuge.Programm.YTDLP);
        if (ytdlp == null) {
            throw new IOException("yt-dlp fehlt");
        }
        List<String> befehl = new ArrayList<>(List.of(ytdlp.toString(), "--no-playlist", "--no-warnings", "--ignore-config",
                "-j", "-f", FORMAT));
        Path deno = VideoWerkzeuge.finden(VideoWerkzeuge.Programm.DENO);
        if (deno != null) {
            befehl.add("--js-runtimes");
            befehl.add("deno:" + deno);
        }
        // Die Kennung ist geprüft (YouTube: 11 Zeichen aus A–Z, a–z, 0–9, _ und -; Twitch: Kanalname bzw. Nummer), die
        // Adresse beginnt immer mit https:// und wird also nie als Option gelesen
        befehl.add(de.moinmornhart.stinkendreich.fernseher.FernseherEntity.adresse(kennung));
        Process p = new ProcessBuilder(befehl).start();
        ByteArrayOutputStream fehlerText = new ByteArrayOutputStream();
        Thread fehlerLeser = mitlesen(p.getErrorStream(), fehlerText);
        byte[] json;
        try (InputStream rein = p.getInputStream()) {
            json = rein.readAllBytes();
        }
        if (!p.waitFor(90, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new IOException("yt-dlp antwortet nicht");
        }
        fehlerLeser.join(2000);
        if (p.exitValue() != 0 || json.length == 0) {
            throw new IOException(kurz(fehlerText.toString(StandardCharsets.UTF_8)));
        }
        JsonObject j = JsonParser.parseString(new String(json, StandardCharsets.UTF_8)).getAsJsonObject();
        // Die Beschreibung aufheben: Die beiden yt-dlp-Läufe für Bild und Ton lesen sie (--load-info-json), statt YouTube
        // noch einmal zu fragen. Das spart beim Start mehrere Sekunden.
        String info = "";
        try {
            Path datei = Files.createTempFile("stinkendreich-video-", ".json");
            Files.write(datei, json);
            datei.toFile().deleteOnExit();
            info = datei.toString();
        } catch (IOException e) {
            // ohne Datei fragt yt-dlp eben noch einmal nach
        }
        double dauer = zahl(j, "duration");
        boolean live = j.has("is_live") && j.get("is_live").isJsonPrimitive() && j.get("is_live").getAsBoolean();
        JsonObject bildFormat = j;
        JsonObject tonFormat = j;
        if (j.has("requested_formats") && j.get("requested_formats").isJsonArray()) {
            for (JsonElement e : j.getAsJsonArray("requested_formats")) {
                JsonObject f = e.getAsJsonObject();
                if (!"none".equals(text(f, "vcodec"))) {
                    bildFormat = f;
                } else {
                    tonFormat = f;
                }
            }
        }
        String bild = text(bildFormat, "url");
        String ton = text(tonFormat, "url");
        if (bild.isEmpty()) {
            throw new IOException("keine Videoadresse gefunden");
        }
        Map<String, String> kopf = new LinkedHashMap<>();
        String agent = "";
        JsonObject h = bildFormat.has("http_headers") ? bildFormat.getAsJsonObject("http_headers")
                : j.has("http_headers") ? j.getAsJsonObject("http_headers") : new JsonObject();
        for (Map.Entry<String, JsonElement> e : h.entrySet()) {
            String wert = e.getValue().getAsString().replace("\r", "").replace("\n", "");
            if (e.getKey().equalsIgnoreCase("User-Agent")) {
                agent = wert;
            } else {
                kopf.put(e.getKey(), wert);
            }
        }
        return new VideoQuelle(bild, ton.isEmpty() ? bild : ton, kopf, agent, dauer, live,
                de.moinmornhart.stinkendreich.fernseher.FernseherEntity.adresse(kennung), text(bildFormat, "format_id"), text(tonFormat, "format_id"), info);
    }

    /**
     * yt-dlp lädt eine Spur selbst und gibt sie auf stdout aus (für FFmpeg). Seit 2026 lehnt YouTube viele Abrufe mit
     * 403 ab, wenn FFmpeg die Adresse direkt öffnet; yt-dlp kennt die nötigen Kopfzeilen und Tricks.
     */
    ProcessBuilder ytdlpSpur(boolean bildSpur) throws IOException {
        Path ytdlp = VideoWerkzeuge.finden(VideoWerkzeuge.Programm.YTDLP);
        if (ytdlp == null) {
            throw new IOException("yt-dlp fehlt");
        }
        List<String> befehl = new ArrayList<>(List.of(ytdlp.toString(), "--no-playlist", "--no-warnings", "--ignore-config",
                "--quiet", "--no-part", "-f", bildSpur ? bildFormat : tonFormat, "-o", "-"));
        Path deno = VideoWerkzeuge.finden(VideoWerkzeuge.Programm.DENO);
        if (deno != null) {
            befehl.add("--js-runtimes");
            befehl.add("deno:" + deno);
        }
        if (!infoDatei.isEmpty() && Files.exists(Path.of(infoDatei))) {
            befehl.add("--load-info-json");
            befehl.add(infoDatei);
        } else {
            befehl.add(seite);
        }
        return new ProcessBuilder(befehl);
    }

    /** Spur über yt-dlp holen? (nur bei YouTube mit bekannter Format-Nummer, nicht bei Testvideos) */
    boolean ueberYtdlp(boolean bildSpur) {
        return !seite.isEmpty() && !(bildSpur ? bildFormat : tonFormat).isEmpty();
    }

    /** Liest einen Datenstrom im Hintergrund mit (sonst blockiert das Programm, wenn sein Puffer voll ist). */
    static Thread mitlesen(InputStream rein, ByteArrayOutputStream ziel) {
        Thread t = new Thread(() -> {
            try (rein) {
                byte[] b = new byte[4096];
                int n;
                while ((n = rein.read(b)) > 0) {
                    synchronized (ziel) {
                        if (ziel.size() < 64 * 1024) {
                            ziel.write(b, 0, n);
                        }
                    }
                }
            } catch (IOException ignoriert) {
                // Programm beendet
            }
        }, "Stinkend Reich Fernseher Ausgabe");
        t.setDaemon(true);
        t.start();
        return t;
    }

    /** Letzte sinnvolle Zeile einer Fehlermeldung, gekürzt. */
    static String kurz(String text) {
        String letzte = "unbekannter Fehler";
        for (String zeile : text.split("\\R")) {
            if (!zeile.isBlank()) {
                letzte = zeile.trim();
            }
        }
        letzte = letzte.replaceFirst("^ERROR:\\s*", "").replaceFirst("^\\[youtube\\]\\s*[A-Za-z0-9_-]{11}:\\s*", "");
        return letzte.length() > 160 ? letzte.substring(0, 157) + "…" : letzte;
    }

    private static String text(JsonObject j, String schluessel) {
        return j.has(schluessel) && j.get(schluessel).isJsonPrimitive() ? j.get(schluessel).getAsString() : "";
    }

    private static double zahl(JsonObject j, String schluessel) {
        try {
            return j.has(schluessel) && j.get(schluessel).isJsonPrimitive() ? j.get(schluessel).getAsDouble() : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
