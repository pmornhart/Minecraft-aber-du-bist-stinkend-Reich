package de.moinmornhart.stinkendreich.client.fernseher;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import de.moinmornhart.stinkendreich.StinkendReich;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bilder für den Fernseher: zu einer Video-Kennung lädt es Vorschaubild und Titel von YouTube
 * (öffentliche Adressen img.youtube.com und die oEmbed-Schnittstelle) und malt daraus eine Textur:
 * Bild im 16:9-Format, unten der Titel, in der Mitte ein Abspielzeichen. Ohne Video zeigt der Fernseher
 * sein Standbild. Geladen wird im Hintergrund, nur einmal je Video.
 */
public final class FernseherBild {
    /** Texturgröße: das Vierfache der Modell-Abwicklung (256 × 64), Bildschirm bei (8, 8) mit 320 × 180. */
    private static final int BREITE = 1024, HOEHE = 256, BX = 8, BY = 8, BB = 320, BH = 180;
    public static final Identifier AUS = StinkendReich.id("textures/entity/fernseher/aus.png");
    private static final Identifier LAEDT = StinkendReich.id("textures/entity/fernseher/laedt.png");
    private static final Map<String, Identifier> FERTIG = new ConcurrentHashMap<>();
    private static final Map<String, String> TITEL = new ConcurrentHashMap<>();
    private static final Set<String> LAEDT_GERADE = ConcurrentHashMap.newKeySet();
    private static final HttpClient NETZ = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    private FernseherBild() {
    }

    /** Textur für den Bildschirm; startet beim ersten Aufruf das Laden. */
    public static Identifier textur(String video) {
        if (video == null || video.isEmpty()) {
            return AUS;
        }
        Identifier fertig = FERTIG.get(video);
        if (fertig != null) {
            return fertig;
        }
        if (LAEDT_GERADE.add(video)) {
            Thread t = new Thread(() -> laden(video), "Stinkend Reich Fernseher");
            t.setDaemon(true);
            t.start();
        }
        return LAEDT;
    }

    /** Titel des Videos, sobald geladen (sonst leer). */
    public static String titel(String video) {
        return TITEL.getOrDefault(video, "");
    }

    private static void laden(String video) {
        BufferedImage vorschau = null;
        if (de.moinmornhart.stinkendreich.fernseher.FernseherEntity.istTwitch(video)) {
            // Twitch: ohne Anmeldung gibt es kein Vorschaubild; der Titel nennt Kanal bzw. Aufzeichnung
            TITEL.put(video, video.startsWith("tw:") ? "Twitch · " + video.substring(3) + " (live)" : "Twitch · Video " + video.substring(4));
        } else try {
            for (String name : new String[]{"maxresdefault.jpg", "hqdefault.jpg"}) {
                HttpResponse<byte[]> antwort = NETZ.send(HttpRequest.newBuilder(
                        URI.create("https://img.youtube.com/vi/" + video + "/" + name)).timeout(Duration.ofSeconds(10)).build(),
                        HttpResponse.BodyHandlers.ofByteArray());
                if (antwort.statusCode() == 200) {
                    vorschau = ImageIO.read(new ByteArrayInputStream(antwort.body()));
                    if (vorschau != null && vorschau.getWidth() > 200) {
                        break;
                    }
                }
            }
            String adresse = "https://www.youtube.com/oembed?format=json&url="
                    + URLEncoder.encode("https://www.youtube.com/watch?v=" + video, StandardCharsets.UTF_8);
            HttpResponse<String> info = NETZ.send(HttpRequest.newBuilder(URI.create(adresse)).timeout(Duration.ofSeconds(10)).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (info.statusCode() == 200) {
                JsonObject j = JsonParser.parseString(info.body()).getAsJsonObject();
                TITEL.put(video, j.has("title") ? j.get("title").getAsString() : "");
            }
        } catch (Exception e) {
            StinkendReich.LOGGER.warn("Fernseher: Vorschau für {} nicht geladen ({})", video, e.getMessage());
        }
        byte[] png;
        try {
            BufferedImage bild = malen(vorschau, TITEL.getOrDefault(video, ""));
            ByteArrayOutputStream aus = new ByteArrayOutputStream();
            ImageIO.write(bild, "png", aus);
            png = aus.toByteArray();
        } catch (Exception e) {
            StinkendReich.LOGGER.warn("Fernseher: Bild nicht gemalt", e);
            return;
        }
        Minecraft.getInstance().execute(() -> {
            try {
                NativeImage nativ = NativeImage.read(new ByteArrayInputStream(png));
                Identifier id = StinkendReich.id("fernseher/" + video.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_")
                        + "_" + Integer.toHexString(video.hashCode()));
                //? if >=1.21.5 {
                DynamicTexture textur = new DynamicTexture(() -> "Fernseher " + video, nativ);
                //?} else
                /*DynamicTexture textur = new DynamicTexture(nativ);*/
                Minecraft.getInstance().getTextureManager().register(id, textur);
                FERTIG.put(video, id);
            } catch (Exception e) {
                StinkendReich.LOGGER.warn("Fernseher: Textur nicht angelegt", e);
            }
        });
    }

    /** Malt die ganze Textur: schwarzer Rahmen, Vorschaubild (auf 16:9 zugeschnitten), Titelleiste, Abspielzeichen. */
    static BufferedImage malen(BufferedImage vorschau, String titel) {
        BufferedImage bild = new BufferedImage(BREITE, HOEHE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = bild.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(10, 10, 12));
        g.fillRect(0, 0, BREITE, HOEHE);
        if (vorschau != null) {
            int w = vorschau.getWidth(), h = vorschau.getHeight();
            int zh = Math.min(h, w * 9 / 16);
            int y0 = (h - zh) / 2;
            g.drawImage(vorschau, BX, BY, BX + BB, BY + BH, 0, y0, w, y0 + zh, null);
        } else {
            g.setColor(new Color(30, 30, 40));
            g.fillRect(BX, BY, BB, BH);
        }
        // Abspielzeichen in der Mitte
        int mx = BX + BB / 2, my = BY + BH / 2;
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55F));
        g.setColor(Color.BLACK);
        g.fillOval(mx - 22, my - 22, 44, 44);
        g.setComposite(AlphaComposite.SrcOver);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(2));
        g.drawOval(mx - 22, my - 22, 44, 44);
        g.fillPolygon(new int[]{mx - 7, mx - 7, mx + 12}, new int[]{my - 12, my + 12, my}, 3);
        // Titelleiste
        if (!titel.isEmpty()) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.65F));
            g.setColor(Color.BLACK);
            g.fillRect(BX, BY + BH - 30, BB, 30);
            g.setComposite(AlphaComposite.SrcOver);
            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
            FontMetrics fm = g.getFontMetrics();
            String text = titel;
            while (fm.stringWidth(text) > BB - 16 && text.length() > 4) {
                text = text.substring(0, text.length() - 2);
            }
            if (!text.equals(titel)) {
                text = text.trim() + "…";
            }
            g.drawString(text, BX + 8, BY + BH - 10);
        }
        g.dispose();
        return bild;
    }
}
