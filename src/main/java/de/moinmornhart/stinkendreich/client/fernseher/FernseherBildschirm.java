package de.moinmornhart.stinkendreich.client.fernseher;

import de.moinmornhart.stinkendreich.Konfig;
import de.moinmornhart.stinkendreich.client.KompatClient;
import de.moinmornhart.stinkendreich.client.bildschirm.MenueBildschirm;
import de.moinmornhart.stinkendreich.fernseher.FernseherEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.net.URI;

/**
 * Menü des Fernsehers: YouTube-Link einfügen (Strg + V) und im Fernseher abspielen, anhalten, weiter, spulen,
 * stoppen, auf dem PC im Browser öffnen oder ausschalten. Gültig sind Links wie youtube.com/watch?v=…, youtu.be/…,
 * youtube.com/shorts/… . Fehlen die Hilfsprogramme für Videos, fragt erst {@link FernseherFreigabeBildschirm}.
 */
public class FernseherBildschirm extends MenueBildschirm {
    private final int tvId;
    private EditBox eingabe;

    public FernseherBildschirm(int tvId) {
        super(Component.translatable("stinkendreich.fernseher.titel"));
        this.tvId = tvId;
    }

    private FernseherEntity tv() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getEntity(tvId) instanceof FernseherEntity t ? t : null;
    }

    @Override
    protected int gesamtHoehe() {
        return 280;
    }

    @Override
    protected void knoepfe() {
        FernseherEntity tv = tv();
        String jetzt = tv == null ? "" : tv.video();
        boolean laeuft = tv != null && tv.laeuft();
        boolean angehalten = tv != null && tv.angehalten();
        if (!jetzt.isEmpty()) {
            String titel = FernseherBild.titel(jetzt);
            textzeile(Component.translatable("stinkendreich.fernseher.laeuft", titel.isEmpty() ? jetzt : titel));
        }
        eingabe = new EditBox(font, width / 2 - BREITE / 2, aktuellesY(), BREITE, HOEHE,
                Component.translatable("stinkendreich.fernseher.link"));
        eingabe.setMaxLength(300);
        eingabe.setHint(Component.translatable("stinkendreich.fernseher.link"));
        addRenderableWidget(eingabe);
        setInitialFocus(eingabe);
        weiter(HOEHE + 4);
        // Abspielen: neuer Link, sonst das aktuelle Video von vorn
        knopf(Component.translatable("stinkendreich.fernseher.im_tv"), () -> {
            String kennung = FernseherEntity.kennung(eingabe.getValue());
            if (kennung.isEmpty()) {
                kennung = jetzt;
            }
            if (kennung.isEmpty()) {
                eingabe.setValue("");
                eingabe.setHint(Component.translatable("stinkendreich.fernseher.ungueltig"));
                return;
            }
            if (Konfig.get().fernseherVideos && !VideoWerkzeuge.bereit() && !Konfig.get().fernseherHilfsprogrammeLaden) {
                KompatClient.zeige(new FernseherFreigabeBildschirm(tvId, kennung));
                return;
            }
            if (Konfig.get().fernseherHilfsprogrammeLaden && !VideoWerkzeuge.bereit()) {
                VideoWerkzeuge.laden();
            }
            senden("!abspielen|" + kennung);
            schliessen();
        });
        if (laeuft || angehalten) {
            knopf(Component.translatable(laeuft ? "stinkendreich.fernseher.anhalten" : "stinkendreich.fernseher.weiter"), () -> {
                senden(laeuft ? "!pause" : "!weiter");
                schliessen();
            });
            // Spulen: zwei halbe Knöpfe nebeneinander
            int halb = BREITE / 2 - 2;
            addRenderableWidget(Button.builder(Component.literal("« 10 s"), b -> senden("!spulen|-10"))
                    .bounds(width / 2 - BREITE / 2, aktuellesY(), halb, HOEHE).build());
            addRenderableWidget(Button.builder(Component.literal("30 s »"), b -> senden("!spulen|30"))
                    .bounds(width / 2 + 2, aktuellesY(), halb, HOEHE).build());
            weiter(HOEHE + 4);
            knopf(Component.translatable("stinkendreich.fernseher.stopp"), () -> {
                senden("!stopp");
                schliessen();
            });
        }
        knopf(Component.translatable("stinkendreich.fernseher.abspielen"), () -> {
            String kennung = FernseherEntity.kennung(eingabe.getValue());
            if (kennung.isEmpty()) {
                kennung = jetzt;
            }
            if (!kennung.isEmpty()) {
                if (!kennung.equals(jetzt)) {
                    senden(kennung);
                } else if (laeuft) {
                    senden("!pause");
                }
                KompatClient.oeffneLink(URI.create(de.moinmornhart.stinkendreich.fernseher.FernseherEntity.adresse(kennung)));
                schliessen();
            }
        });
        knopf(Component.translatable("stinkendreich.fernseher.aus"), () -> {
            senden("");
            schliessen();
        });
        textzeile(Component.translatable(Konfig.get().fernseherVideos ? "stinkendreich.fernseher.hinweis_video"
                : "stinkendreich.fernseher.hinweis"));
        knopf(Component.translatable("gui.done"), this::schliessen);
    }

    private void senden(String befehl) {
        FernseherEntity tv = tv();
        if (tv != null) {
            KompatClient.sende("fernseher", tv.blockPosition(), tv.getId() + "|" + befehl);
        }
    }
}
