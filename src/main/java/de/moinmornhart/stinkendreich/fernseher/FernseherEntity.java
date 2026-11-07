package de.moinmornhart.stinkendreich.fernseher;

import de.moinmornhart.stinkendreich.mechanik.KlientBruecke;
import de.moinmornhart.stinkendreich.netz.Netz;
import de.moinmornhart.stinkendreich.netz.OhneMod;
import de.moinmornhart.stinkendreich.register.ModItems;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
//? if >=1.21.6 {
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
//?} else {
/*import net.minecraft.nbt.CompoundTag;
*///?}

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * XXL-Fernseher (5 × 2,8 Blöcke, auf Wunsch größer, siehe {@link #setzeGroesse}) an der Wand. Rechtsklick öffnet das
 * Menü für einen YouTube-Link.
 * Gespeichert und an alle Spieler verteilt werden nur die Video-Kennung (11 Zeichen) und der Stand der Wiedergabe
 * (läuft seit Spielzeit X / angehalten bei Position Y). Jeder Klient zeigt daraus selbst Vorschaubild oder Video
 * (siehe {@code FernseherVideos}), so sehen alle dieselbe Stelle. Auf Wunsch läuft das Video im Browser des PCs.
 * Schleichen + Rechtsklick im Kreativmodus baut ihn ab.
 */
public class FernseherEntity extends Mob {
    private static final EntityDataAccessor<String> VIDEO = SynchedEntityData.defineId(FernseherEntity.class, EntityDataSerializers.STRING);
    /**
     * Wiedergabe: "" = keine, "L&lt;Tick&gt;" = läuft (Position 0 war bei diesem Spielzeit-Tick), "P&lt;Ticks&gt;" = angehalten,
     * "W&lt;Ticks&gt;;&lt;bereit&gt;/&lt;alle&gt;" = wartet an dieser Stelle, bis alle Zuschauer das Video geladen haben.
     */
    private static final EntityDataAccessor<String> WIEDERGABE = SynchedEntityData.defineId(FernseherEntity.class, EntityDataSerializers.STRING);
    /** Bildgröße in Blöcken als „Breite;Höhe“, "" = Standardgröße (der Bunker-Fernseher füllt so seinen Goldrahmen). */
    private static final EntityDataAccessor<String> GROESSE = SynchedEntityData.defineId(FernseherEntity.class, EntityDataSerializers.STRING);
    /** Standardgröße in Blöcken (Modell 80 × 45 Pixel). */
    public static final float STANDARD_BREITE = 5.0F, STANDARD_HOEHE = 2.8125F;
    /** Wer so nah am Fernseher ist, schaut mit: auf ihn wird beim Start gewartet. */
    public static final int ZUSCHAUER_RADIUS = 32;
    /** Länger als 45 Sekunden wird nicht gewartet (z. B. wenn bei einem das Laden hängt). */
    private static final int HOECHSTENS_WARTEN = 20 * 45;
    /** Zuschauer, bei denen das Video an der Wartestelle geladen ist (nur auf dem Server). */
    private final java.util.Set<java.util.UUID> bereit = new java.util.HashSet<>();
    private long wartenSeit;
    private static final Pattern KENNUNG = Pattern.compile("(?:v=|youtu\\.be/|shorts/|embed/|live/)([A-Za-z0-9_-]{11})");
    private static final Pattern NUR_KENNUNG = Pattern.compile("[A-Za-z0-9_-]{11}");

    public FernseherEntity(EntityType<? extends FernseherEntity> typ, Level welt) {
        super(typ, welt);
        setPersistenceRequired();
        setNoAi(true);
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder attribute() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    /** Nimmt die Aktion „fernseher“ vom Menü entgegen: „&lt;Entity-ID&gt;|&lt;Befehl&gt;“ (siehe {@link #befehl}). */
    public static void registrieren() {
        // Zuschauer meldet: Video ist an der Wartestelle geladen („&lt;Entity-ID&gt;|&lt;Wartestelle in Ticks&gt;“)
        Netz.bearbeiter("fernseherbereit", ZUSCHAUER_RADIUS + 8, (spieler, pos, wert) -> {
            String[] t = wert.split("\\|");
            try {
                if (t.length == 2 && spieler.level().getEntity(Integer.parseInt(t[0])) instanceof FernseherEntity tv && tv.wartet()
                        && tv.positionTicks(0) == Long.parseLong(t[1])) {
                    tv.bereit.add(spieler.getUUID());
                }
            } catch (NumberFormatException ignoriert) {
                // ungültige Nachricht
            }
        });
        Netz.bearbeiter("fernseher", 10, (spieler, pos, wert) -> {
            int trenner = wert.indexOf('|');
            if (trenner < 0) {
                return;
            }
            try {
                if (spieler.level().getEntity(Integer.parseInt(wert.substring(0, trenner))) instanceof FernseherEntity tv
                        && tv.distanceToSqr(spieler) < 16 * 16) {
                    tv.befehl(wert.substring(trenner + 1));
                }
            } catch (NumberFormatException ignoriert) {
                // ungültige Nachricht
            }
        });
    }

    /** Twitch: Video (Aufzeichnung) und Kanal (Livestream); Namen 3–25 Zeichen aus Buchstaben, Ziffern und _. */
    private static final Pattern TWITCH_VIDEO = Pattern.compile("twitch\\.tv/videos/(\\d{5,15})");
    private static final Pattern TWITCH_KANAL = Pattern.compile("twitch\\.tv/(?:popout/)?([A-Za-z0-9_]{3,25})(?:[/?#]|$)");
    private static final Pattern NUR_TWITCH = Pattern.compile("tw:[a-z0-9_]{3,25}|twv:\\d{5,15}");
    /** Pfade auf twitch.tv, die kein Kanal sind. */
    private static final java.util.Set<String> TWITCH_KEIN_KANAL = java.util.Set.of("videos", "directory", "settings", "search",
            "downloads", "jobs", "p", "subscriptions", "inventory", "wallet", "drops", "turbo", "prime");

    /**
     * Video-Kennung aus einem Link oder "" wenn keiner. YouTube (watch?v=, youtu.be/, shorts/ …): die 11 Zeichen der
     * Kennung. Twitch (Philip, 07.11.: „anstatt YouTube-Videos auch noch Twitch-Streams“): „tw:kanal“ für einen Kanal
     * (läuft live), „twv:nummer“ für eine Aufzeichnung.
     */
    public static String kennung(String link) {
        String t = link == null ? "" : link.trim();
        if (NUR_KENNUNG.matcher(t).matches() || NUR_TWITCH.matcher(t).matches()) {
            return t;
        }
        if (t.contains("twitch.tv")) {
            Matcher v = TWITCH_VIDEO.matcher(t);
            if (v.find()) {
                return "twv:" + v.group(1);
            }
            Matcher k = TWITCH_KANAL.matcher(t);
            if (k.find() && !TWITCH_KEIN_KANAL.contains(k.group(1).toLowerCase(java.util.Locale.ROOT))) {
                return "tw:" + k.group(1).toLowerCase(java.util.Locale.ROOT);
            }
            return "";
        }
        Matcher m = KENNUNG.matcher(t);
        return t.contains("youtu") && m.find() ? m.group(1) : "";
    }

    /** Ist die Kennung ein Twitch-Kanal oder eine Twitch-Aufzeichnung? */
    public static boolean istTwitch(String kennung) {
        return kennung != null && (kennung.startsWith("tw:") || kennung.startsWith("twv:"));
    }

    /** Webadresse zur Kennung (zum Abspielen mit yt-dlp und zum Öffnen im Browser). */
    public static String adresse(String kennung) {
        if (kennung.startsWith("twv:")) {
            return "https://www.twitch.tv/videos/" + kennung.substring(4);
        }
        if (kennung.startsWith("tw:")) {
            return "https://www.twitch.tv/" + kennung.substring(3);
        }
        return "https://www.youtube.com/watch?v=" + kennung;
    }

    public String video() {
        return entityData.get(VIDEO);
    }

    /**
     * Befehl aus dem Menü: „!abspielen|Link“ (Link leer = aktuelles Video von vorn), „!pause“, „!weiter“,
     * „!spulen|Sekunden“ (auch negativ), „!stopp“. Alles andere ist ein Link: Vorschaubild zeigen, nicht abspielen
     * (leer = ausschalten).
     */
    public void befehl(String befehl) {
        long jetzt = level().getGameTime();
        String[] teile = befehl.split("\\|", 2);
        String rest = teile.length > 1 ? teile[1] : "";
        switch (teile[0]) {
            case "!abspielen" -> {
                String k = kennung(rest);
                if (k.isEmpty()) {
                    k = video();
                }
                if (!k.isEmpty()) {
                    setzeVideo(k);
                    warten(0);
                }
            }
            case "!pause" -> {
                if (laeuft() || wartet()) {
                    entityData.set(WIEDERGABE, "P" + positionTicks(jetzt));
                }
            }
            case "!weiter" -> {
                if (angehalten()) {
                    warten(positionTicks(jetzt));
                }
            }
            case "!spulen" -> {
                try {
                    long neu = Math.max(0, positionTicks(jetzt) + Long.parseLong(rest.trim()) * 20);
                    if (laeuft() || wartet()) {
                        warten(neu);
                    } else if (angehalten()) {
                        entityData.set(WIEDERGABE, "P" + neu);
                    }
                } catch (NumberFormatException ignoriert) {
                    // ungültige Sekundenzahl
                }
            }
            case "!stopp" -> entityData.set(WIEDERGABE, "");
            default -> {
                setzeVideo(kennung(befehl));
                entityData.set(WIEDERGABE, "");
            }
        }
    }

    public String wiedergabe() {
        return entityData.get(WIEDERGABE);
    }

    /** Läuft gerade ein Video? */
    public boolean laeuft() {
        return wiedergabe().startsWith("L") && !video().isEmpty();
    }

    /** Wartet der Fernseher, bis alle Zuschauer das Video geladen haben? */
    public boolean wartet() {
        return wiedergabe().startsWith("W") && !video().isEmpty();
    }

    /** Beim Warten: „bereit/alle“ (für den Hinweis über der Hotbar), sonst "". */
    public String wartenStand() {
        String w = wiedergabe();
        int i = w.indexOf(';');
        return w.startsWith("W") && i > 0 ? w.substring(i + 1) : "";
    }

    /**
     * Gemeinsam schauen (Philip: „dass beide das Gleiche sehen … wenn es bei meinem Freund noch runterlädt, dass es bei
     * mir dann auch noch wartet“): Statt sofort loszulaufen, wartet der Fernseher an dieser Stelle, bis jeder Zuschauer
     * im Umkreis von {@link #ZUSCHAUER_RADIUS} Blöcken das Video geladen hat (höchstens {@link #HOECHSTENS_WARTEN} Ticks),
     * und startet dann für alle im selben Tick.
     */
    private void warten(long position) {
        bereit.clear();
        wartenSeit = level().getGameTime();
        entityData.set(WIEDERGABE, "W" + position + ";0/0");
    }

    /** Zuschauer: Spieler mit der Mod in der Nähe (ohne Mod gibt es kein Video, auf sie wird nicht gewartet). */
    private java.util.List<net.minecraft.server.level.ServerPlayer> zuschauer() {
        return level().getEntitiesOfClass(net.minecraft.server.level.ServerPlayer.class, getBoundingBox().inflate(ZUSCHAUER_RADIUS),
                p -> de.moinmornhart.stinkendreich.netz.ModErkennung.hatMod(p));
    }

    /** Ist ein Video angehalten (Standbild)? */
    public boolean angehalten() {
        return wiedergabe().startsWith("P") && !video().isEmpty();
    }

    /** Abspielposition in Spielticks zur angegebenen Spielzeit (0, wenn nichts läuft). */
    public long positionTicks(long spielzeit) {
        String w = wiedergabe();
        try {
            if (w.startsWith("L")) {
                return Math.max(0, spielzeit - Long.parseLong(w.substring(1)));
            }
            if (w.startsWith("P")) {
                return Math.max(0, Long.parseLong(w.substring(1)));
            }
            if (w.startsWith("W")) {
                int i = w.indexOf(';');
                return Math.max(0, Long.parseLong(i > 0 ? w.substring(1, i) : w.substring(1)));
            }
        } catch (NumberFormatException ignoriert) {
            // beschädigter Wert: wie „keine Wiedergabe“
        }
        return 0;
    }

    public void setzeVideo(String kennung) {
        entityData.set(VIDEO, kennung == null ? "" : kennung);
    }

    /**
     * Macht das Bild breite × hoehe Blöcke groß (unten mittig an der Position des Fernsehers). Das Modell wird dafür
     * gestreckt; das Klickfeld bleibt in der Mitte.
     */
    public void setzeGroesse(float breite, float hoehe) {
        boolean standard = breite == STANDARD_BREITE && hoehe == STANDARD_HOEHE;
        entityData.set(GROESSE, standard || breite <= 0 || hoehe <= 0 ? "" : breite + ";" + hoehe);
    }

    public float breite() {
        return masse()[0];
    }

    public float hoehe() {
        return masse()[1];
    }

    private float[] masse() {
        String g = entityData.get(GROESSE);
        int t = g.indexOf(';');
        if (t > 0) {
            try {
                return new float[]{Float.parseFloat(g.substring(0, t)), Float.parseFloat(g.substring(t + 1))};
            } catch (NumberFormatException ignoriert) {
                // beschädigter Wert: Standardgröße
            }
        }
        return new float[]{STANDARD_BREITE, STANDARD_HOEHE};
    }

    /** Raum, den das Bild einnimmt (zum Zeichnen: ein großer Fernseher verschwindet sonst am Bildrand). */
    public net.minecraft.world.phys.AABB bildKasten() {
        double r = Math.max(breite(), getBbWidth()) / 2;
        return new net.minecraft.world.phys.AABB(getX() - r, getY(), getZ() - r, getX() + r, getY() + Math.max(hoehe(), getBbHeight()), getZ() + r);
    }

    //? if <1.21.2 {
    /*@Override
    public net.minecraft.world.phys.AABB getBoundingBoxForCulling() {
        return bildKasten();
    }
    *///?}

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder daten) {
        super.defineSynchedData(daten);
        daten.define(VIDEO, "");
        daten.define(WIEDERGABE, "");
        daten.define(GROESSE, "");
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && wartet() && tickCount % 5 == 0) {
            var alle = zuschauer();
            long fertig = alle.stream().filter(p -> bereit.contains(p.getUUID())).count();
            long jetzt = level().getGameTime();
            if (fertig >= alle.size() || jetzt - wartenSeit > HOECHSTENS_WARTEN) {
                // Alle bereit (oder genug gewartet): für alle im selben Tick los
                de.moinmornhart.stinkendreich.StinkendReich.LOGGER.info("Fernseher: Start für {} von {} Zuschauern nach {} s",
                        fertig, alle.size(), (jetzt - wartenSeit) / 20);
                entityData.set(WIEDERGABE, "L" + (jetzt - positionTicks(jetzt)));
            } else {
                String stand = "W" + positionTicks(jetzt) + ";" + fertig + "/" + alle.size();
                if (!stand.equals(wiedergabe())) {
                    entityData.set(WIEDERGABE, stand);
                }
            }
        }
        setDeltaMovement(Vec3.ZERO);
        yBodyRot = getYRot();
        yHeadRot = getYRot();
    }

    @Override
    public boolean removeWhenFarAway(double abstand) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    //? if >=1.21.2 {
    @Override
    public boolean isInvulnerableTo(ServerLevel welt, DamageSource quelle) {
        return !quelle.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || super.isInvulnerableTo(welt, quelle);
    }
    //?} else {
    /*@Override
    public boolean isInvulnerableTo(DamageSource quelle) {
        return !quelle.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || super.isInvulnerableTo(quelle);
    }
    *///?}

    @Override
    protected InteractionResult mobInteract(Player spieler, InteractionHand hand) {
        if (spieler.isShiftKeyDown() && spieler.isCreative()) {
            if (!level().isClientSide()) {
                Block.popResource(level(), blockPosition(), new ItemStack(ModItems.FERNSEHER));
                discard();
            }
            return InteractionResult.SUCCESS;
        }
        if (level().isClientSide()) {
            KlientBruecke.fernseher.accept(getId());
        } else {
            // Spieler ohne Mod (Server-Version): das Menü als Dialogfenster
            OhneMod.zeigen(spieler, "fernseher", blockPosition());
        }
        return InteractionResult.SUCCESS;
    }

    //? if >=1.21.6 {
    @Override
    protected void addAdditionalSaveData(ValueOutput ausgabe) {
        super.addAdditionalSaveData(ausgabe);
        ausgabe.putString("stinkendreich_video", video());
        ausgabe.putString("stinkendreich_wiedergabe", wiedergabe());
        ausgabe.putString("stinkendreich_groesse", entityData.get(GROESSE));
    }

    @Override
    protected void readAdditionalSaveData(ValueInput eingabe) {
        super.readAdditionalSaveData(eingabe);
        setzeVideo(kennung(eingabe.getStringOr("stinkendreich_video", "")));
        entityData.set(WIEDERGABE, eingabe.getStringOr("stinkendreich_wiedergabe", ""));
        entityData.set(GROESSE, eingabe.getStringOr("stinkendreich_groesse", ""));
    }
    //?} else if >=1.21.5 {
    /*@Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("stinkendreich_video", video());
        tag.putString("stinkendreich_wiedergabe", wiedergabe());
        tag.putString("stinkendreich_groesse", entityData.get(GROESSE));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setzeVideo(kennung(tag.getStringOr("stinkendreich_video", "")));
        entityData.set(WIEDERGABE, tag.getStringOr("stinkendreich_wiedergabe", ""));
        entityData.set(GROESSE, tag.getStringOr("stinkendreich_groesse", ""));
    }
    *///?} else {
    /*@Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("stinkendreich_video", video());
        tag.putString("stinkendreich_wiedergabe", wiedergabe());
        tag.putString("stinkendreich_groesse", entityData.get(GROESSE));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setzeVideo(kennung(tag.getString("stinkendreich_video")));
        entityData.set(WIEDERGABE, tag.getString("stinkendreich_wiedergabe"));
        entityData.set(GROESSE, tag.getString("stinkendreich_groesse"));
    }
    *///?}
}
