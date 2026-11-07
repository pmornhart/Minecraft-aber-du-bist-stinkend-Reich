package de.moinmornhart.stinkendreich.auto;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.moinmornhart.stinkendreich.Konfig;
import de.moinmornhart.stinkendreich.mechanik.KlientBruecke;
import de.moinmornhart.stinkendreich.register.ModItems;
import de.moinmornhart.stinkendreich.register.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.phys.Vec3;
//? if >=1.21.6 {
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
//?} else {
/*import net.minecraft.nbt.CompoundTag;
*///?}

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Das Bonzenauto: ein flacher Sportwagen mit vier Sitzen. Fahrphysik mit Trägheit (Gas, Bremse,
 * Rückwärtsgang), geschwindigkeitsabhängiger Lenkung, Driften mit der Handbremse (Leertaste),
 * Steigungen bis einen Block hoch, Scheinwerfer (echtes Licht), Hupe und acht Lackfarben (Farbstoff).
 *
 * <p>Gesteuert wird wie bei Pferd und Boot auf dem Rechner des Fahrers; die anderen sehen die
 * Bewegung über die normale Abgleichung. Einheiten: Blöcke pro Tick (1 Block/Tick = 72 km/h).</p>
 */
public class AutoEntity extends Mob {
    private static final EntityDataAccessor<Integer> FARBE = SynchedEntityData.defineId(AutoEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> LICHT = SynchedEntityData.defineId(AutoEntity.class, EntityDataSerializers.BOOLEAN);
    /** Name auf dem Nummernschild: der Herr der Villa, zu der der Wagen gehört (leer = noch keiner). */
    private static final EntityDataAccessor<String> KENNZEICHEN = SynchedEntityData.defineId(AutoEntity.class, EntityDataSerializers.STRING);
    private static final String SCHLUESSEL = "stinkendreich";
    /** Welches Fahrzeug (Bonzenauto, Geländewagen, Supersportwagen, Stretch-Limo). */
    public final Autotyp art;

    /** Lackfarben (Reihenfolge = Texturen auto/0.png … 7.png). */
    public static final String[] FARBEN = {"schwarz", "weiss", "rot", "gelb", "gold", "blau", "gruen", "pink"};

    // Fahrzustand (auf dem steuernden Rechner)
    private double tempo;
    /** Gas (-1…1), Lenkung (-1 rechts … 1 links) und Steigen (-1…1, nur Hubschrauber) vom Butler am Steuer (Server). */
    protected double chauffeurGas, chauffeurLenk, chauffeurHoch;
    private double lenkung;
    private Vec3 schub = Vec3.ZERO;
    private boolean driftet;
    private int crashSperre;

    // Darstellung (auf jedem Client aus der Bewegung berechnet)
    public float radWinkel, radWinkelAlt;
    public float lenkWinkel;
    /** Nur Hubschrauber: Rotorwinkel, Neigen (Nase runter) und Rollen in Grad. */
    public float rotorWinkel, rotorWinkelAlt, neigung, rollen;
    private double anzeigeTempo;

    // Scheinwerfer: gesetzte Lichtblöcke (Server)
    private final Set<BlockPos> lichtBloecke = new HashSet<>();
    private final List<BlockPos> geladeneLichter = new ArrayList<>();
    private int hupeSperre;

    // Schlüssel, Schloss und Kofferraum (Server)
    /** Besitzer des Wagens: wer zuerst einen Schlüssel genommen hat (null = noch niemand). */
    private java.util.UUID besitzer;
    private boolean verschlossen;
    /** Kofferraum mit 27 Plätzen (wie eine Truhe). */
    private final net.minecraft.world.SimpleContainer kofferraum = new net.minecraft.world.SimpleContainer(27);

    public AutoEntity(EntityType<? extends AutoEntity> typ, Level welt, Autotyp art) {
        super(typ, welt);
        this.art = art;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder attribute(Autotyp art) {
        AttributeSupplier.Builder b = Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 100.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.STEP_HEIGHT, 1.05);
        // Ab 1.21.6 richtet sich die Kamera beim Fahren nach diesem Wert (Verfolgerkamera)
        //? if >=1.21.6
        b.add(Attributes.CAMERA_DISTANCE, art == Autotyp.STRETCHLIMO || art.fliegt() ? 11.0 : 7.0);
        return b;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder daten) {
        super.defineSynchedData(daten);
        daten.define(FARBE, 0);
        daten.define(LICHT, false);
        daten.define(KENNZEICHEN, "");
    }

    /** Was auf dem Nummernschild steht (Name des Besitzers, leer = Schild ohne Schrift). */
    public String kennzeichen() {
        return entityData.get(KENNZEICHEN);
    }

    public void setzeKennzeichen(String name) {
        entityData.set(KENNZEICHEN, name == null ? "" : name);
    }

    /**
     * Wagen ohne Kennzeichen bekommen den Namen des Herrn der Villa, auf deren Gelände sie stehen (bei der Spawn-Villa
     * steht der erst fest, wenn der erste Spieler kommt). Alle 5 Sekunden, nur auf dem Server.
     */
    private void kennzeichenPruefen() {
        if (tickCount % 100 != 7 || !kennzeichen().isEmpty() || !(level() instanceof net.minecraft.server.level.ServerLevel welt)) {
            return;
        }
        var villa = de.moinmornhart.stinkendreich.villa.VillaPlatzierung.bei(blockPosition());
        if (villa != null && villa.enthaelt(blockPosition())) {
            setzeKennzeichen(de.moinmornhart.stinkendreich.villa.VillaPlatzierung.herrName(welt.getServer(), villa));
            de.moinmornhart.stinkendreich.StinkendReich.LOGGER.info("Kennzeichen {} für {} bei {}", kennzeichen(), art.name, blockPosition().toShortString());
        }
    }

    public int farbe() {
        return Mth.clamp(entityData.get(FARBE), 0, FARBEN.length - 1);
    }

    public void setzeFarbe(int farbe) {
        entityData.set(FARBE, Mth.clamp(farbe, 0, FARBEN.length - 1));
    }

    public boolean licht() {
        return entityData.get(LICHT);
    }

    /** Geschwindigkeit in km/h, wie sie auf dem Tacho steht. */
    public int kmh() {
        return (int) Math.round(anzeigeTempo * 72.0);
    }

    public boolean driftet() {
        return driftet;
    }

    // ------------------------------------------------------------------
    // Grundverhalten
    // ------------------------------------------------------------------

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
    protected boolean canAddPassenger(Entity passagier) {
        return getPassengers().size() < art.sitze.length;
    }

    /**
     * Fahrer: sitzt der Butler im Wagen, fährt er (Minecraft setzt einsteigende Spieler sonst immer auf
     * den ersten Platz der Insassenliste); sonst der Spieler auf Platz 0.
     */
    @Override
    public LivingEntity getControllingPassenger() {
        de.moinmornhart.stinkendreich.butler.ButlerEntity butler = chauffeur();
        if (butler != null) {
            return butler;
        }
        if (fahrerOhneMod() != null) {
            return null;    // Spieler ohne Mod: der Server fährt mit seinen Tasten (siehe travel)
        }
        return getFirstPassenger() instanceof Player p ? p : null;
    }

    /**
     * Server-Version: Sitzt am Steuer ein Spieler ohne Mod, kann sein Minecraft den Wagen nicht selbst bewegen. Dann
     * fährt der Server ihn mit den Tasten, die der Spieler drückt (W/S/A/D, Leertaste = Handbremse).
     */
    public net.minecraft.server.level.ServerPlayer fahrerOhneMod() {
        //? if >=1.21.6 {
        if (!level().isClientSide() && chauffeur() == null && getFirstPassenger() instanceof net.minecraft.server.level.ServerPlayer sp
                && !de.moinmornhart.stinkendreich.netz.ModErkennung.hatMod(sp)) {
            return sp;
        }
        //?}
        return null;
    }

    /** Der Butler, falls er im Wagen sitzt (dann am Steuer). */
    public de.moinmornhart.stinkendreich.butler.ButlerEntity chauffeur() {
        for (Entity e : getPassengers()) {
            if (e instanceof de.moinmornhart.stinkendreich.butler.ButlerEntity b) {
                return b;
            }
        }
        return null;
    }

    /** Fährt gerade der Butler? */
    public boolean chauffeurFaehrt() {
        return chauffeur() != null;
    }

    /** Eingaben des Chauffeurs für den nächsten Tick (siehe {@code butler.Chauffeur}). */
    public void chauffeurEingabe(double gas, double lenk) {
        chauffeurEingabe(gas, lenk, 0);
    }

    /** Eingaben des Butlers mit Steigen/Sinken (Hubschrauber, siehe {@code butler.Pilot}). */
    public void chauffeurEingabe(double gas, double lenk, double hoch) {
        chauffeurGas = Mth.clamp(gas, -1, 1);
        chauffeurLenk = Mth.clamp(lenk, -1, 1);
        chauffeurHoch = Mth.clamp(hoch, -1, 1);
    }

    /** Aktuelle Geschwindigkeit in Blöcken pro Tick (vorwärts positiv). */
    public double tempo() {
        return tempo;
    }

    /** Höchstgeschwindigkeit dieses Wagens in Blöcken pro Tick. */
    public double hoechstTempo() {
        return Math.max(0.3, Konfig.get().autoHoechstTempo / 72.0 * art.tempo);
    }

    /**
     * Sitze je Fahrzeug (siehe {@link Autotyp}); Platz 0 ist der Fahrer. Fährt der Butler, sitzen die Gäste
     * von hinten nach vorn (in der Limousine also ganz hinten im Fond).
     */
    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passagier, EntityDimensions masse, float skala) {
        int index = Math.max(0, getPassengers().indexOf(passagier));
        Entity butler = chauffeur();
        if (butler != null) {
            if (passagier == butler) {
                index = 0;
            } else {
                // Gäste der Reihe nach von hinten (der Butler zählt nicht mit)
                int gast = 0;
                for (Entity e : getPassengers()) {
                    if (e == passagier) {
                        break;
                    }
                    if (e != butler) {
                        gast++;
                    }
                }
                index = art.sitze.length - 1 - gast;
            }
        }
        int platz = Mth.clamp(index, 0, art.sitze.length - 1);
        double[] s = art.sitze[platz];
        return new Vec3(s[0], s[1], s[2]).yRot(-getYRot() * Mth.DEG_TO_RAD);
    }

    /**
     * Insassen drehen sich mit dem Wagen mit und können sich höchstens 105° zur Seite umsehen
     * (wie im Boot), damit die Kamera hinter dem Auto bleibt.
     */
    @Override
    protected void positionRider(Entity passagier, Entity.MoveFunction bewegen) {
        super.positionRider(passagier, bewegen);
        float drehung = Mth.wrapDegrees(getYRot() - yRotO);
        passagier.setYRot(passagier.getYRot() + drehung);
        passagier.setYHeadRot(passagier.getYHeadRot() + drehung);
        blickBegrenzen(passagier);
    }

    @Override
    public void onPassengerTurned(Entity passagier) {
        blickBegrenzen(passagier);
    }

    private void blickBegrenzen(Entity passagier) {
        passagier.setYBodyRot(getYRot());
        float abweichung = Mth.wrapDegrees(passagier.getYRot() - getYRot());
        float erlaubt = Mth.clamp(abweichung, -105.0F, 105.0F);
        passagier.yRotO += erlaubt - abweichung;
        passagier.setYRot(passagier.getYRot() + erlaubt - abweichung);
        passagier.setYHeadRot(passagier.getYRot());
    }

    @Override
    protected InteractionResult mobInteract(Player spieler, InteractionHand hand) {
        // Wer schon drinsitzt, klickt beim Rechtsklick oft das eigene Fahrzeug an (der Hubschrauber umschließt ihn):
        // dann geht der Klick an den Gegenstand in der Hand durch, z. B. an das Handy
        if (spieler.getVehicle() == this) {
            return InteractionResult.PASS;
        }
        ItemStack stapel = spieler.getItemInHand(hand);
        net.minecraft.world.item.DyeColor farbstoff = de.moinmornhart.stinkendreich.Kompat.farbstoff(stapel);
        if (farbstoff != null) {
            // Umlackieren mit Farbstoff
            int neu = farbeZu(farbstoff.getSerializedName());
            if (neu >= 0 && neu != farbe()) {
                if (!level().isClientSide()) {
                    setzeFarbe(neu);
                    if (!spieler.isCreative()) {
                        stapel.shrink(1);
                    }
                    playSound(net.minecraft.sounds.SoundEvents.DYE_USE, 1.0F, 1.0F);
                }
                return InteractionResult.SUCCESS;
            }
        }
        if (spieler.isShiftKeyDown()) {
            // Schleichen + Rechtsklick: Automenü (Kofferraum, Schloss, Schlüssel, nach Hause bringen, einpacken)
            if (spieler instanceof net.minecraft.server.level.ServerPlayer sp) {
                AutoMenue.oeffnen(sp, this);
            }
            return InteractionResult.SUCCESS;
        }
        if (stapel.is(ModItems.AUTOSCHLUESSEL) && getUUID().equals(AutoschluesselItem.wagen(stapel))) {
            // Mit dem passenden Schlüssel in der Hand: auf- bzw. abschließen
            if (!level().isClientSide()) {
                verschliessen(!verschlossen, spieler);
            }
            return InteractionResult.SUCCESS;
        }
        if (verschlossen && !AutoschluesselItem.hatSchluessel(spieler, this)) {
            if (!level().isClientSide()) {
                de.moinmornhart.stinkendreich.Kompat.nachricht(spieler, net.minecraft.network.chat.Component.translatable("stinkendreich.schluessel.verschlossen")
                        .withStyle(net.minecraft.ChatFormatting.RED), true);
                playSound(net.minecraft.sounds.SoundEvents.IRON_DOOR_CLOSE, 0.6F, 1.6F);
            }
            return InteractionResult.SUCCESS;
        }
        if (!level().isClientSide() && canAddPassenger(spieler)) {
            boolean fahrer = getPassengers().isEmpty();
            spieler.startRiding(this);
            if (fahrer) {
                level().playSound(null, blockPosition(), ModSounds.ANLASSER, SoundSource.NEUTRAL, 1.0F, 1.0F);
            }
        }
        return InteractionResult.SUCCESS;
    }

    // ------------------------------------------------------------------
    // Schlüssel, Schloss, Kofferraum, nach Hause bringen
    // ------------------------------------------------------------------

    /** Herrenlos und leerer Kofferraum (darf als doppelter Wagen in der Garage verschwinden)? */
    public boolean istLeer() {
        return besitzer == null && kofferraum.isEmpty();
    }

    public boolean verschlossen() {
        return verschlossen;
    }

    public java.util.UUID besitzer() {
        return besitzer;
    }

    public boolean istBesitzer(Player p) {
        return besitzer != null && besitzer.equals(p.getUUID());
    }

    /** Darf der Spieler Schloss, Kofferraum und Menü bedienen? Offener Wagen: jeder; abgeschlossen: nur mit Schlüssel. */
    public boolean darfBedienen(Player p) {
        return !verschlossen || AutoschluesselItem.hatSchluessel(p, this);
    }

    /** Ab- oder aufschließen, mit Piepen und kurzem Blinken der Scheinwerfer. */
    public void verschliessen(boolean zu, Player wer) {
        verschlossen = zu;
        level().playSound(null, blockPosition(), ModSounds.PIEP_OK, SoundSource.NEUTRAL, 0.8F, zu ? 1.4F : 1.0F);
        level().playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.NEUTRAL, 0.5F, zu ? 1.6F : 1.9F);
        blinken = zu ? 8 : 4;
        if (wer != null) {
            de.moinmornhart.stinkendreich.Kompat.nachricht(wer, net.minecraft.network.chat.Component.translatable(
                    zu ? "stinkendreich.schluessel.zu" : "stinkendreich.schluessel.auf").withStyle(net.minecraft.ChatFormatting.GOLD), true);
        }
    }

    /** Ticks, die die Scheinwerfer nach dem Schließen noch blinken (Server). */
    private int blinken;

    /** Schlüssel geben: Der Erste wird Besitzer; danach bekommt nur der Besitzer Ersatzschlüssel. */
    public void schluesselGeben(net.minecraft.server.level.ServerPlayer p) {
        if (besitzer != null && !istBesitzer(p)) {
            de.moinmornhart.stinkendreich.Kompat.nachricht(p, net.minecraft.network.chat.Component.translatable("stinkendreich.schluessel.fremd")
                    .withStyle(net.minecraft.ChatFormatting.RED), true);
            return;
        }
        besitzer = p.getUUID();
        ItemStack s = AutoschluesselItem.fuer(this);
        if (!p.getInventory().add(s)) {
            Block.popResource(p.level(), p.blockPosition(), s);
        }
        de.moinmornhart.stinkendreich.Kompat.nachricht(p, net.minecraft.network.chat.Component.translatable("stinkendreich.schluessel.bekommen")
                .withStyle(net.minecraft.ChatFormatting.GOLD), true);
    }

    /** Kofferraum öffnen (abgeschlossen nur mit Schlüssel). */
    public void kofferraumOeffnen(net.minecraft.server.level.ServerPlayer p) {
        if (!darfBedienen(p)) {
            de.moinmornhart.stinkendreich.Kompat.nachricht(p, net.minecraft.network.chat.Component.translatable("stinkendreich.schluessel.verschlossen")
                    .withStyle(net.minecraft.ChatFormatting.RED), true);
            return;
        }
        level().playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.CHEST_OPEN, SoundSource.NEUTRAL, 0.7F, 0.8F);
        p.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inv, sp) -> net.minecraft.world.inventory.ChestMenu.threeRows(id, inv, kofferraum),
                net.minecraft.network.chat.Component.translatable("stinkendreich.automenue.kofferraum")));
    }

    /** James holt den Wagen mit dem Hubschrauber ab und stellt ihn in die Garage. */
    public void heimBringen(net.minecraft.server.level.ServerPlayer p) {
        de.moinmornhart.stinkendreich.butler.ButlerEntity b = de.moinmornhart.stinkendreich.butler.ButlerVerwaltung.vonSpieler(p);
        if (b == null) {
            de.moinmornhart.stinkendreich.butler.ButlerVerwaltung.rufen(p);
            b = de.moinmornhart.stinkendreich.butler.ButlerVerwaltung.vonSpieler(p);
        }
        if (b == null) {
            return;
        }
        if (getPassengers().contains(b)) {
            b.stopRiding();
        }
        b.neueAuftraege(List.of(new de.moinmornhart.stinkendreich.butler.auftrag.AbschleppAuftrag(getUUID())));
        b.sagen(net.minecraft.network.chat.Component.translatable("stinkendreich.butler.abschleppen.los"));
    }

    /** Wieder zum Gegenstand machen; der Inhalt des Kofferraums fällt heraus. */
    public void einpacken() {
        ejectPassengers();
        lichterLoeschen();
        for (int i = 0; i < kofferraum.getContainerSize(); i++) {
            if (!kofferraum.getItem(i).isEmpty()) {
                spawnAtLocationSicher(kofferraum.removeItemNoUpdate(i));
            }
        }
        spawnAtLocationSicher(new ItemStack(ModItems.auto(art)));
        discard();
    }

    private void spawnAtLocationSicher(ItemStack stapel) {
        Block.popResource(level(), blockPosition(), stapel);
    }

    /** Index der Lackfarbe zu einer Farbstoff-Farbe, -1 wenn es den Lack nicht gibt. */
    private static int farbeZu(String farbstoff) {
        return switch (farbstoff) {
            case "black", "gray" -> 0;
            case "white", "light_gray" -> 1;
            case "red" -> 2;
            case "yellow" -> 3;
            case "orange", "brown" -> 4;
            case "blue", "light_blue", "cyan" -> 5;
            case "green", "lime" -> 6;
            case "pink", "magenta", "purple" -> 7;
            default -> -1;
        };
    }

    /** Hupe (vom Fahrer über die Taste H). */
    public void hupen() {
        if (hupeSperre <= 0) {
            hupeSperre = 12;
            level().playSound(null, blockPosition(), ModSounds.HUPE, SoundSource.NEUTRAL, 2.0F, 1.0F);
        }
    }

    /** Scheinwerfer an/aus (Taste G). */
    public void lichtUmschalten() {
        entityData.set(LICHT, !licht());
        playSound(net.minecraft.sounds.SoundEvents.LEVER_CLICK, 0.6F, licht() ? 1.4F : 1.0F);
        if (!licht()) {
            lichterLoeschen();
        }
    }

    // ------------------------------------------------------------------
    // Fahren
    // ------------------------------------------------------------------

    @Override
    public void travel(Vec3 eingabe) {
        LivingEntity fahrer = getControllingPassenger();
        double gas = 0, lenk = 0;
        boolean handbremse = false;
        if (fahrer instanceof Player p) {
            gas = p.zza;
            lenk = p.xxa;
            handbremse = level().isClientSide() && KlientBruecke.handbremse.getAsBoolean();
            // Der Fahrer schaut nach vorn, kann sich aber umsehen
            setYHeadRot(getYRot());
        }
        //? if >=1.21.6 {
        else if (fahrerOhneMod() instanceof net.minecraft.server.level.ServerPlayer sp) {
            net.minecraft.world.entity.player.Input tasten = sp.getLastClientInput();
            gas = (tasten.forward() ? 1 : 0) - (tasten.backward() ? 1 : 0);
            lenk = (tasten.left() ? 1 : 0) - (tasten.right() ? 1 : 0);
            handbremse = tasten.jump();
            setYHeadRot(getYRot());
        }
        //?}
        else if (fahrer instanceof de.moinmornhart.stinkendreich.butler.ButlerEntity butler) {
            // Der Butler fährt (nur auf dem Server, er ist dann der steuernde Rechner); ohne Fahrauftrag bremst er
            if (butler.faehrtGerade()) {
                gas = chauffeurGas;
                lenk = chauffeurLenk;
            } else {
                gas = tempo > 0.02 ? -1 : (tempo < -0.02 ? 1 : 0);
            }
            setYHeadRot(getYRot());
        }
        double max = hoechstTempo();
        double kraft = art.beschleunigung;

        // Gas, Bremse, Rückwärtsgang
        if (gas > 0) {
            if (tempo < 0) {
                tempo = Math.min(0, tempo + 0.06);
            } else {
                double kurve = 1.0 - (tempo / max) * (tempo / max);
                tempo = Math.min(max, tempo + (0.004 + 0.016 * kurve) * kraft);
            }
        } else if (gas < 0) {
            if (tempo > 0) {
                tempo = Math.max(0, tempo - 0.06);
            } else {
                tempo = Math.max(-0.35, tempo - 0.012);
            }
        } else {
            tempo *= 0.985;
            tempo -= Math.signum(tempo) * Math.min(Math.abs(tempo), 0.002);
        }
        if (handbremse) {
            tempo *= 0.975;
        }
        if (isInWater()) {
            tempo *= 0.85;
        }

        // Lenkung: weich nachgeführt, bei hohem Tempo weniger Einschlag
        lenkung += (lenk - lenkung) * 0.3;
        double betrag = Math.abs(tempo);
        double wirkung = Math.min(1.0, betrag / 0.25) * (1.0 - 0.5 * Math.min(1.0, betrag / max));
        float drehung = (float) (lenkung * 4.2 * wirkung * Math.signum(tempo) * (handbremse ? 1.6 : 1.0));
        setYRot(getYRot() - drehung);
        yBodyRot = getYRot();

        // Haftung: ohne Handbremse folgt der Wagen der Lenkrichtung, beim Driften rutscht er
        Vec3 richtung = new Vec3(-Mth.sin(getYRot() * Mth.DEG_TO_RAD), 0, Mth.cos(getYRot() * Mth.DEG_TO_RAD));
        Vec3 soll = richtung.scale(tempo);
        driftet = handbremse && betrag > 0.35 && Math.abs(lenkung) > 0.2;
        double haftung = driftet ? 0.07 : (onGround() ? 0.55 : 0.05);
        schub = schub.add(soll.subtract(schub).scale(haftung));

        // Schwerkraft
        double y = getDeltaMovement().y;
        y = onGround() ? -0.04 : (y - 0.08) * 0.98;

        double vorherX = getX(), vorherZ = getZ();
        move(MoverType.SELF, new Vec3(schub.x, y, schub.z));
        // Nur ein echter Aufprall bremst: Kommt der Wagen trotz Berührung noch gut voran (Bordstein oder Geländer nur
        // gestreift, über eine Stufe gerutscht), fährt er fast ungebremst weiter
        double gewollt = Math.hypot(schub.x, schub.z), geschafft = Math.hypot(getX() - vorherX, getZ() - vorherZ);
        boolean gestreift = horizontalCollision && gewollt > 1.0E-3 && geschafft >= 0.6 * gewollt;
        if (gestreift) {
            tempo *= 0.985;
        }

        // Aufprall
        if (horizontalCollision && !gestreift) {
            if (betrag > 0.5 && crashSperre <= 0) {
                playSound(ModSounds.CRASH, 1.0F, 1.0F);
                crashSperre = 20;
            }
            tempo *= 0.35;
            schub = schub.scale(0.35);
        }
        if (crashSperre > 0) {
            crashSperre--;
        }
        setDeltaMovement(schub.x, onGround() ? 0 : y, schub.z);
    }

    @Override
    public void tick() {
        super.tick();
        // Nach dem Ab-/Aufschließen blinken die Scheinwerfer (gerade Anzahl: danach wie vorher)
        if (blinken > 0 && !level().isClientSide()) {
            if (blinken-- % 2 == 0) {
                entityData.set(LICHT, !licht());
            }
        }
        // Kein Fallschaden für Auto und Insassen (auch nicht nach einem Sprung von zehn Blöcken)
        resetFallDistance();
        for (Entity e : getPassengers()) {
            e.resetFallDistance();
        }
        kennzeichenPruefen();
        yBodyRot = getYRot();
        yHeadRot = getYRot();
        if (hupeSperre > 0) {
            hupeSperre--;
        }
        // Darstellung aus der tatsächlichen Bewegung (gilt auf jedem Client gleich)
        double dx = getX() - xo, dz = getZ() - zo;
        double gefahren = Math.sqrt(dx * dx + dz * dz);
        Vec3 richtung = new Vec3(-Mth.sin(getYRot() * Mth.DEG_TO_RAD), 0, Mth.cos(getYRot() * Mth.DEG_TO_RAD));
        double vor = dx * richtung.x + dz * richtung.z;
        anzeigeTempo = gefahren;
        radWinkelAlt = radWinkel;
        radWinkel += (float) (vor / 0.3125); // Radius 5/16 Block
        if (level().isClientSide()) {
            LivingEntity f = getControllingPassenger();
            // Lenkrad-Anzeige: beim Spieler aus der Eingabe, beim Chauffeur aus der Kurvenfahrt
            float soll = f instanceof Player p ? p.xxa : (f != null ? Mth.clamp(-Mth.wrapDegrees(getYRot() - yRotO) / 3.0F, -1, 1) : 0);
            lenkWinkel += (soll * 0.45F - lenkWinkel) * 0.3F;
            // Reifenqualm beim Driften und beim scharfen Bremsen
            boolean quer = gefahren > 0.3 && Math.abs(vor) < gefahren * 0.92 && !art.fliegt();
            if (quer || driftet) {
                for (int seite = -1; seite <= 1; seite += 2) {
                    Vec3 rad = position().add(richtung.scale(-art.hinterachse)).add(new Vec3(richtung.z, 0, -richtung.x).scale(0.9 * seite));
                    for (int i = 0; i < 3; i++) {
                        double rx = (random.nextDouble() - 0.5) * 0.5, rz = (random.nextDouble() - 0.5) * 0.5;
                        level().addParticle(ParticleTypes.CLOUD, rad.x + rx, getY() + 0.15, rad.z + rz, rx * 0.1, 0.04, rz * 0.1);
                    }
                }
                if (tickCount % 6 == 0) {
                    level().playLocalSound(getX(), getY(), getZ(), ModSounds.REIFEN, SoundSource.NEUTRAL, 0.5F, 1.0F, false);
                }
            }
        } else {
            scheinwerfer();
        }
    }

    // ------------------------------------------------------------------
    // Scheinwerfer: unsichtbare Lichtblöcke vor dem Wagen
    // ------------------------------------------------------------------

    private void scheinwerfer() {
        if (!geladeneLichter.isEmpty()) {
            // Nach dem Laden: alte Lichtblöcke aus der letzten Sitzung entfernen
            lichtBloecke.addAll(geladeneLichter);
            geladeneLichter.clear();
            lichterLoeschen();
        }
        if (!licht() || tickCount % 4 != 0) {
            return;
        }
        Vec3 richtung = new Vec3(-Mth.sin(getYRot() * Mth.DEG_TO_RAD), 0, Mth.cos(getYRot() * Mth.DEG_TO_RAD));
        Set<BlockPos> neu = new HashSet<>();
        for (int abstand : new int[]{3, 7, 11}) {
            Vec3 p = position().add(richtung.scale(abstand + art.front)).add(0, 1, 0);
            BlockPos pos = BlockPos.containing(p);
            if (level().getBlockState(pos).isAir() || level().getBlockState(pos).is(Blocks.LIGHT)) {
                neu.add(pos);
            }
        }
        for (BlockPos alt : lichtBloecke) {
            if (!neu.contains(alt) && level().getBlockState(alt).is(Blocks.LIGHT)) {
                level().setBlock(alt, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        for (BlockPos p : neu) {
            if (level().getBlockState(p).isAir()) {
                level().setBlock(p, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15), Block.UPDATE_ALL);
            }
        }
        lichtBloecke.clear();
        lichtBloecke.addAll(neu);
    }

    private void lichterLoeschen() {
        for (BlockPos p : lichtBloecke) {
            if (level().getBlockState(p).is(Blocks.LIGHT)) {
                level().setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        lichtBloecke.clear();
    }

    @Override
    public void remove(RemovalReason grund) {
        if (!level().isClientSide()) {
            lichterLoeschen();
        }
        super.remove(grund);
    }

    // ------------------------------------------------------------------
    // Speichern
    // ------------------------------------------------------------------

    private String datenAlsText() {
        JsonObject j = new JsonObject();
        j.addProperty("farbe", farbe());
        j.addProperty("licht", licht());
        j.addProperty("kennzeichen", kennzeichen());
        if (besitzer != null) {
            j.addProperty("besitzer", besitzer.toString());
        }
        j.addProperty("verschlossen", verschlossen);
        JsonArray l = new JsonArray();
        for (BlockPos p : lichtBloecke) {
            l.add(p.asLong());
        }
        j.add("lichtbloecke", l);
        return j.toString();
    }

    private void datenAusText(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        try {
            JsonObject j = JsonParser.parseString(text).getAsJsonObject();
            setzeFarbe(j.has("farbe") ? j.get("farbe").getAsInt() : 0);
            entityData.set(LICHT, j.has("licht") && j.get("licht").getAsBoolean());
            setzeKennzeichen(j.has("kennzeichen") ? j.get("kennzeichen").getAsString() : "");
            besitzer = j.has("besitzer") ? java.util.UUID.fromString(j.get("besitzer").getAsString()) : null;
            verschlossen = j.has("verschlossen") && j.get("verschlossen").getAsBoolean();
            if (j.has("lichtbloecke")) {
                j.getAsJsonArray("lichtbloecke").forEach(e -> geladeneLichter.add(BlockPos.of(e.getAsLong())));
            }
        } catch (Exception ignoriert) {
            // kaputte Daten: Standardwerte behalten
        }
    }

    //? if >=1.21.6 {
    @Override
    protected void addAdditionalSaveData(ValueOutput ausgabe) {
        super.addAdditionalSaveData(ausgabe);
        ausgabe.putString(SCHLUESSEL, datenAlsText());
        kofferraum.storeAsItemList(ausgabe.list("Kofferraum", ItemStack.CODEC));
    }

    @Override
    protected void readAdditionalSaveData(ValueInput eingabe) {
        super.readAdditionalSaveData(eingabe);
        datenAusText(eingabe.getStringOr(SCHLUESSEL, ""));
        kofferraum.fromItemList(eingabe.listOrEmpty("Kofferraum", ItemStack.CODEC));
    }
    //?} else if >=1.21.5 {
    /*@Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString(SCHLUESSEL, datenAlsText());
        tag.put("Kofferraum", kofferraum.createTag(registryAccess()));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        datenAusText(tag.getStringOr(SCHLUESSEL, ""));
        kofferraum.fromTag(tag.getListOrEmpty("Kofferraum"), registryAccess());
    }
    *///?} else {
    /*@Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString(SCHLUESSEL, datenAlsText());
        tag.put("Kofferraum", kofferraum.createTag(registryAccess()));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        datenAusText(tag.getString(SCHLUESSEL));
        kofferraum.fromTag(tag.getList("Kofferraum", net.minecraft.nbt.Tag.TAG_COMPOUND), registryAccess());
    }
    *///?}
}
