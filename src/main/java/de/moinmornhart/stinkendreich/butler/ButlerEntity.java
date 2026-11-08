package de.moinmornhart.stinkendreich.butler;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.Konfig;
import de.moinmornhart.stinkendreich.butler.auftrag.Auftrag;
import de.moinmornhart.stinkendreich.mechanik.KlientBruecke;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
//? if >=1.21.6 {
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
//?} else {
/*import net.minecraft.nbt.CompoundTag;
*///?}

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Der Butler: folgt seinem Herrn oder bleibt zu Hause und erledigt Aufträge
 * (Erze abbauen, Holz fällen, jagen, schmelzen, kochen, herstellen, Truhen sortieren, liefern).
 *
 * <p>Er ist unverwundbar (außer durch /kill), verschwindet nie von selbst und baut nur natürliche
 * Blöcke ab – nie etwas an der Villa und nie etwas, das ein Spieler gesetzt hat ({@link Schutz}).</p>
 */
public class ButlerEntity extends PathfinderMob {
    /** Was der Butler gerade tut, für das Menü auf dem Client. */
    private static final EntityDataAccessor<Component> STATUS = SynchedEntityData.defineId(ButlerEntity.class, EntityDataSerializers.COMPONENT);
    /** Name des Besitzers (leer = noch niemand), damit der Client weiß, wer das Menü öffnen darf. */
    private static final EntityDataAccessor<String> BESITZER_NAME = SynchedEntityData.defineId(ButlerEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> FOLGT = SynchedEntityData.defineId(ButlerEntity.class, EntityDataSerializers.BOOLEAN);
    /** Sitzt er gerade mit einem Fahr-/Flugauftrag am Steuer? (Auch der Client muss wissen, wer ein Fahrzeug steuert.) */
    private static final EntityDataAccessor<Boolean> AM_STEUER = SynchedEntityData.defineId(ButlerEntity.class, EntityDataSerializers.BOOLEAN);
    private static final String SCHLUESSEL = "stinkendreich";

    /** Reichweite der Hände (vom Auge bis zur Blockmitte). */
    public static final double REICHWEITE = 4.5;

    private UUID besitzer;
    /** Wo er wohnt (in der Villa sein Platz in der Halle). */
    private BlockPos heimat;
    /** Wo er gerade wartet, wenn er nicht folgt (meist die Heimat, nach „bleib hier“ die Stelle). */
    private BlockPos zuhause;
    private BlockPos liefertruhe;
    private final Taschen taschen = new Taschen();
    private final Deque<Auftrag> auftraege = new ArrayDeque<>();
    /** Gespeicherte Orte für den Chauffeur (Name klein geschrieben, ohne Umlaute → Position). */
    private final java.util.Map<String, BlockPos> orte = new java.util.LinkedHashMap<>();

    // Wegfindung (nicht gespeichert)
    private BlockPos wegZiel;
    private int wegNeuIn;
    private int wegFehler;
    private int stillstand;
    private Vec3 letztePos;

    public ButlerEntity(EntityType<? extends ButlerEntity> typ, Level welt) {
        super(typ, welt);
        setPersistenceRequired();
        setCustomNameVisible(true);
        getNavigation().getNodeEvaluator().setCanOpenDoors(true);
        getNavigation().getNodeEvaluator().setCanPassDoors(true);
        setDropChance(EquipmentSlot.MAINHAND, 0.0F);
    }

    /** Grundwerte: viel Leben, zügiger Schritt, findet Wege bis 64 Blöcke, steigt Stufen wie Treppen hoch. */
    public static AttributeSupplier.Builder attribute() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 64.0)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder daten) {
        super.defineSynchedData(daten);
        daten.define(STATUS, Component.empty());
        daten.define(BESITZER_NAME, "");
        daten.define(FOLGT, false);
        daten.define(AM_STEUER, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new ArbeitsZiel());
        goalSelector.addGoal(2, new OpenDoorGoal(this, true));
        goalSelector.addGoal(3, new FolgenZiel());
        goalSelector.addGoal(4, new HeimZiel());
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6) {
            @Override
            public boolean canUse() {
                return untaetig() && !folgt() && !isPassenger() && nahAmZuhause(6) && super.canUse();
            }
        });
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
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

    // Unverwundbar, nur /kill und die Leere wirken
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
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            return;
        }
        if (getCustomName() == null && istButler()) {
            setCustomName(Component.literal(Konfig.get().butlerName));
        }
        if (heimat == null) {
            heimat = blockPosition();
        }
        if (zuhause == null) {
            zuhause = heimat;
        }
        if (tickCount % 20 == 0) {
            if (istButler()) {
                ButlerVerwaltung.pflegen(this);
            }
            if (auftraege.isEmpty()) {
                statusSetzen();
            }
        }
        // jeden Tick nachführen (kostet nur einen Vergleich): springt er per Teleport in einen anderen Chunk, muss der
        // Ladebereich im selben Tick mit, sonst tickt er dort nicht mehr
        chunkHalten();
        // Testprotokoll: alle 10 Sekunden Ort und Auftrag (zeigt, ob er auch weit weg vom Herrn weiterarbeitet)
        if (tickCount % 200 == 0 && istButler() && !auftraege.isEmpty() && de.moinmornhart.stinkendreich.dev.Autotest.aktiv()) {
            de.moinmornhart.stinkendreich.StinkendReich.LOGGER.info("Autotest: James bei {} – {} (Chunk gehalten: {})",
                    blockPosition().toShortString(), auftraege.peek().status().getString(), gehaltenerChunk != Long.MIN_VALUE);
        }
        if (entityData.get(AM_STEUER) != faehrtGerade()) {
            entityData.set(AM_STEUER, faehrtGerade());
        }
        // Am Steuer ohne Auftrag und ohne Gast: aussteigen (beim Hubschrauber erst nach der Landung)
        if (tickCount % 20 == 0 && getVehicle() instanceof de.moinmornhart.stinkendreich.auto.AutoEntity wagen && !faehrtGerade()
                && wagen.onGround() && wagen.getPassengers().stream().noneMatch(e -> e instanceof Player)) {
            stopRiding();
        }
        // Steckt er in einem Block fest (z. B. nach herabfallendem Kies), gräbt er sich frei
        if (tickCount % 10 == 0 && isInWall() && !auftraege.isEmpty()) {
            BlockPos kopf = BlockPos.containing(getEyePosition());
            BlockState s = level().getBlockState(kopf);
            if (Schutz.darfAbbauen(welt(), kopf, s)) {
                level().destroyBlock(kopf, false, this);
            }
        }
    }

    @Override
    protected InteractionResult mobInteract(Player spieler, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (level().isClientSide()) {
            String name = entityData.get(BESITZER_NAME);
            if (!spieler.isShiftKeyDown() && (name.isEmpty() || name.equals(spieler.getScoreboardName()))) {
                KlientBruecke.butler.accept(getId());
            }
            return InteractionResult.SUCCESS;
        }
        ServerPlayer sp = (ServerPlayer) spieler;
        if (besitzer == null) {
            ButlerVerwaltung.besitzerSetzen(this, sp);
        }
        if (!istBesitzer(sp)) {
            Kompat.nachricht(sp, Component.translatable("stinkendreich.butler.fremd", getName(), entityData.get(BESITZER_NAME)), true);
        } else if (spieler.isShiftKeyDown()) {
            inventarZeigen(sp);
        } else if (!de.moinmornhart.stinkendreich.netz.ModErkennung.hatMod(sp)) {
            // Spieler ohne Mod (Server-Version): das Menü als Dialogfenster von Minecraft
            //? if >=1.21.6
            de.moinmornhart.stinkendreich.server.ButlerDialog.oeffnen(sp, this);
        }
        return InteractionResult.SUCCESS;
    }

    /** Öffnet die Taschen des Butlers wie eine Truhe. */
    public void inventarZeigen(ServerPlayer spieler) {
        spieler.openMenu(new SimpleMenuProvider((id, inv, p) -> ChestMenu.threeRows(id, inv, taschen),
                Component.translatable("stinkendreich.butler.taschen", getName())));
    }

    // ------------------------------------------------------------------
    // Besitzer, Zuhause, Modus
    // ------------------------------------------------------------------

    public UUID besitzer() {
        return besitzer;
    }

    public String besitzerName() {
        return entityData.get(BESITZER_NAME);
    }

    public void besitzerEintragen(UUID uuid, String name) {
        besitzer = uuid;
        entityData.set(BESITZER_NAME, name == null ? "" : name);
        // Über dem Kopf steht, wem er gehört: „James · Philip“ (in der Nachbarschaft sieht man so, wessen Butler es ist)
        if (istButler() && name != null && !name.isEmpty()) {
            setCustomName(Component.literal(Konfig.get().butlerName)
                    .append(Component.literal(" · " + name).withStyle(net.minecraft.ChatFormatting.GRAY)));
        }
    }

    public boolean istBesitzer(Player p) {
        return besitzer != null && besitzer.equals(p.getUUID());
    }

    /** Der Besitzer, falls er gerade auf dem Server ist. */
    public ServerPlayer besitzerOnline() {
        if (besitzer == null || welt().getServer() == null) {
            return null;
        }
        return welt().getServer().getPlayerList().getPlayer(besitzer);
    }

    public BlockPos zuhause() {
        return zuhause == null ? blockPosition() : zuhause;
    }

    public void setzeZuhause(BlockPos pos) {
        zuhause = pos.immutable();
    }

    public BlockPos heimat() {
        return heimat == null ? zuhause() : heimat;
    }

    /** Neue Heimat (z. B. beim Erzeugen in der Villa); wartet ab sofort dort. */
    public void setzeHeimat(BlockPos pos) {
        heimat = pos.immutable();
        zuhause = heimat;
    }

    /** Die Villa fährt (Inkognito-Modus): alle gemerkten Plätze in der Villa wandern mit, um dy Blöcke. */
    public void hoeheVerschieben(int dy) {
        heimat = heimat == null ? null : heimat.above(dy);
        zuhause = zuhause == null ? null : zuhause.above(dy);
        liefertruhe = liefertruhe == null ? null : liefertruhe.above(dy);
    }

    public BlockPos liefertruhe() {
        return liefertruhe;
    }

    public void setzeLiefertruhe(BlockPos pos) {
        liefertruhe = pos == null ? null : pos.immutable();
    }

    public boolean folgt() {
        return entityData.get(FOLGT);
    }

    public void setzeFolgen(boolean folgen) {
        entityData.set(FOLGT, folgen);
    }

    public boolean nahAmZuhause(double abstand) {
        return position().distanceToSqr(Vec3.atBottomCenterOf(zuhause())) <= abstand * abstand;
    }

    public Taschen taschen() {
        return taschen;
    }

    public ServerLevel welt() {
        return (ServerLevel) level();
    }

    // ------------------------------------------------------------------
    // Aufträge
    // ------------------------------------------------------------------

    public boolean untaetig() {
        return auftraege.isEmpty();
    }

    /** Ersetzt alle Aufträge durch eine neue Bestellung. */
    public void neueAuftraege(List<Auftrag> liste) {
        stopp();
        auftraege.addAll(liste);
        // Arbeit statt Fahrt: erst aussteigen (sonst grub er vom Fahrersitz aus, während der Herr noch im Wagen saß)
        if (isPassenger() && !faehrtGerade()) {
            stopRiding();
        }
        statusSetzen();
        chunkHalten();
    }

    /** Chunks (3 × 3 um James), die er gerade geladen hält. */
    private final java.util.Set<Long> gehalteneChunks = new java.util.HashSet<>();
    /** Mittlerer gehaltener Chunk (Long.MIN_VALUE = keiner). */
    private long gehaltenerChunk = Long.MIN_VALUE;

    /**
     * Solange James Aufträge hat, bleiben die 3 × 3 Chunks um ihn geladen (der Bereich wandert mit). Sonst stand er still,
     * sobald der Herr weiter als die Simulationsweite weg war: Bestellungen wurden mit „Sehr wohl“ angenommen, aber nie
     * ausgeführt (Philip, 08.11.: „manchmal braucht er extra lange“). Ein einzelner Chunk reichte nicht – ging er über
     * die Chunkgrenze, tickte er im Nachbarchunk nicht mehr und wurde ausgeladen. Im Hubschrauber hält der Hubschrauber
     * selbst den Chunk (sonst würden sich beide gegenseitig den Ladeschein wegnehmen).
     */
    private void chunkHalten() {
        if (!(level() instanceof ServerLevel welt)) {
            return;
        }
        int cx = blockPosition().getX() >> 4, cz = blockPosition().getZ() >> 4;
        boolean halten = !auftraege.isEmpty() && !isRemoved()
                && !(getVehicle() instanceof de.moinmornhart.stinkendreich.auto.HubschrauberEntity);
        long k = halten ? ((long) cx << 32) ^ (cz & 0xffffffffL) : Long.MIN_VALUE;
        if (k == gehaltenerChunk) {
            return;
        }
        java.util.Set<Long> neu = new java.util.HashSet<>();
        if (halten) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    neu.add(((long) (cx + dx) << 32) ^ ((cz + dz) & 0xffffffffL));
                }
            }
        }
        for (long alt : gehalteneChunks) {
            if (!neu.contains(alt)) {
                welt.setChunkForced((int) (alt >> 32), (int) alt, false);
            }
        }
        for (long n : neu) {
            if (!gehalteneChunks.contains(n)) {
                // setChunkForced (wie beim Hubschrauber): damit ticken die Figuren im Chunk wirklich weiter
                welt.setChunkForced((int) (n >> 32), (int) n, true);
            }
        }
        gehalteneChunks.clear();
        gehalteneChunks.addAll(neu);
        gehaltenerChunk = k;
    }

    @Override
    public void remove(RemovalReason grund) {
        super.remove(grund);
        chunkHalten();          // isRemoved() ist jetzt wahr: Ladeschein freigeben
    }

    public void stopp() {
        Auftrag a = auftraege.peek();
        if (a != null) {
            a.abbrechen(this);
        }
        auftraege.clear();
        getNavigation().stop();
        werkzeug(null);
        statusSetzen();
        chunkHalten();
    }

    public Auftrag aktuellerAuftrag() {
        return auftraege.peek();
    }

    /** Sitzt er gerade als Chauffeur oder Pilot mit Auftrag am Steuer? */
    public boolean faehrtGerade() {
        Auftrag a = auftraege.peek();
        return a instanceof de.moinmornhart.stinkendreich.butler.auftrag.FahrAuftrag
                || a instanceof de.moinmornhart.stinkendreich.butler.auftrag.AnrufAuftrag
                || a instanceof de.moinmornhart.stinkendreich.butler.auftrag.FlugAuftrag
                || a instanceof de.moinmornhart.stinkendreich.butler.auftrag.AbschleppAuftrag;
    }

    /**
     * Steuert er das Fahrzeug, in dem er sitzt? Auf dem Server = {@link #faehrtGerade()}, auf dem Client der abgeglichene
     * Wert. Ohne Auftrag steuert der Spieler (Philip, 08.11.: „Leertaste geht beim Fliegen nicht mehr“ – James saß nach
     * dem Herbringen noch im Hubschrauber und galt als Pilot, der Hubschrauber sank).
     */
    public boolean amSteuer() {
        return level().isClientSide() ? entityData.get(AM_STEUER) : faehrtGerade();
    }

    // ------------------------------------------------------------------
    // Orte für den Chauffeur
    // ------------------------------------------------------------------

    /** Gespeicherter Ort zu einem Namen; „meine Mine“ oder „die alte Mine“ finden auch den Ort „mine“. */
    public BlockPos ort(String name) {
        String n = Sprache.normalisieren(name);
        BlockPos genau = orte.get(n);
        if (genau != null || n.isEmpty()) {
            return genau;
        }
        String treffer = null;
        for (String ort : orte.keySet()) {
            if ((n.endsWith(" " + ort) || n.startsWith(ort + " ")) && (treffer == null || ort.length() > treffer.length())) {
                treffer = ort;
            }
        }
        return treffer == null ? null : orte.get(treffer);
    }

    public void ortMerken(String name, BlockPos pos) {
        orte.put(Sprache.normalisieren(name), pos.immutable());
    }

    public boolean ortVergessen(String name) {
        return orte.remove(Sprache.normalisieren(name)) != null;
    }

    /** Alle gespeicherten Orte (nur lesen). */
    public java.util.Map<String, BlockPos> orte() {
        return java.util.Collections.unmodifiableMap(orte);
    }

    public int offeneAuftraege() {
        return auftraege.size();
    }

    private void arbeiten() {
        Auftrag a = auftraege.peek();
        if (a == null) {
            return;
        }
        Auftrag.Stand stand = a.schritt(this);
        if (stand == Auftrag.Stand.FERTIG) {
            auftraege.poll();
            werkzeug(null);
        } else if (stand == Auftrag.Stand.FEHLER) {
            a.abbrechen(this);
            sagen(a.fehler().copy().withStyle(ChatFormatting.RED));
            auftraege.clear();
            werkzeug(null);
        }
        if (tickCount % 10 == 0 || stand != Auftrag.Stand.LAEUFT) {
            statusSetzen();
        }
    }

    private void statusSetzen() {
        Auftrag a = auftraege.peek();
        Component text;
        if (a != null) {
            text = a.status();
        } else if (folgt()) {
            text = Component.translatable("stinkendreich.butler.status.folgt");
        } else {
            text = Component.translatable("stinkendreich.butler.status.wartet");
        }
        entityData.set(STATUS, text);
    }

    public Component status() {
        return entityData.get(STATUS);
    }

    /** Nachricht des Butlers an seinen Besitzer, z. B. „[James] Sehr wohl.“ */
    public void sagen(Component text) {
        ServerPlayer p = besitzerOnline();
        if (p != null) {
            Component zeile = Component.literal("[" + getName().getString() + "] ").withStyle(ChatFormatting.GOLD)
                    .append(text.copy().withStyle(s -> s.getColor() == null ? s.withColor(ChatFormatting.WHITE) : s));
            Kompat.nachricht(p, zeile, false);
        }
    }

    /** Nimmt ein Werkzeug in die Hand (nur zum Ansehen); null = leere Hand. */
    public void werkzeug(Item item) {
        ItemStack soll = item == null ? ItemStack.EMPTY : new ItemStack(item);
        if (!ItemStack.isSameItemSameComponents(getMainHandItem(), soll)) {
            setItemSlot(EquipmentSlot.MAINHAND, soll);
        }
    }

    // ------------------------------------------------------------------
    // Bewegung
    // ------------------------------------------------------------------

    public enum Weg { DA, UNTERWEGS, UNERREICHBAR }

    /**
     * Geht zu einer Position. Liefert {@link Weg#DA}, sobald die Füße höchstens {@code nah} Blöcke
     * von der Blockmitte entfernt sind, und {@link Weg#UNERREICHBAR}, wenn kein Weg zu finden ist
     * oder er festhängt.
     */
    public Weg gehZu(BlockPos ziel, double nah) {
        return gehZu(Vec3.atBottomCenterOf(ziel), ziel, nah);
    }

    /** Geht zu einer Entity (z. B. zum Spieler). */
    public Weg gehZu(Entity ziel, double nah) {
        return gehZu(ziel.position(), ziel.blockPosition(), nah);
    }

    private Weg gehZu(Vec3 punkt, BlockPos block, double nah) {
        if (position().distanceToSqr(punkt) <= nah * nah) {
            getNavigation().stop();
            wegZiel = null;
            return Weg.DA;
        }
        if (wegZiel == null || wegZiel.distSqr(block) > 2) {
            wegZiel = block;
            wegNeuIn = 0;
            wegFehler = 0;
            stillstand = 0;
            letztePos = null;
        }
        boolean amEnde = getNavigation().isDone() && wegNeuIn > 0;     // 0 = erster Weg zu diesem Ziel
        if (--wegNeuIn <= 0 || getNavigation().isDone()) {
            wegNeuIn = 30;
            Path pfad = getNavigation().createPath(block, Math.max(0, (int) Math.floor(nah) - 1));
            // Am Ende des Wegs angekommen, aber noch nicht nah genug, und der neue Weg führt nirgendwohin (z. B. Truhe zwei
            // Blöcke hoch oder hinter einer Theke): nicht jeden Tick neu suchen, sondern gleich „unerreichbar“ melden –
            // hinkommen() springt dann daneben (vorher stand er an jeder solchen Truhe 4 Sekunden)
            boolean leer = pfad != null && amEnde && pfad.getEndNode() != null && pfad.getEndNode().asBlockPos().distManhattan(blockPosition()) <= 1;
            if (leer) {
                wegZiel = null;
                return Weg.UNERREICHBAR;
            }
            if (pfad == null || !fuehrtWeiter(pfad, punkt, nah)) {
                if (++wegFehler >= 3) {
                    wegZiel = null;
                    return Weg.UNERREICHBAR;
                }
            } else {
                wegFehler = 0;
            }
            if (pfad != null) {
                getNavigation().moveTo(pfad, 1.0);
            }
        }
        // Festgefahren? Alle 20 Ticks prüfen, ob er vorankommt
        if (tickCount % 20 == 0) {
            if (letztePos != null && position().distanceToSqr(letztePos) < 0.05) {
                if (++stillstand >= 4) {
                    wegZiel = null;
                    return Weg.UNERREICHBAR;
                }
            } else {
                stillstand = 0;
            }
            letztePos = position();
        }
        return Weg.UNTERWEGS;
    }

    /** Vergisst den aktuellen Weg (z. B. nach einem Teleport). */
    public void wegVergessen() {
        wegZiel = null;
        getNavigation().stop();
    }

    /**
     * Bringt der Weg ihn weiter? Ein vollständiger Weg sowieso. Bei weiten Zielen liefert die
     * Wegsuche nur ein Teilstück (sie schaut höchstens FOLLOW_RANGE Blöcke weit) – das zählt,
     * solange sein Ende deutlich näher am Ziel liegt als der Butler jetzt.
     */
    private boolean fuehrtWeiter(Path pfad, Vec3 punkt, double nah) {
        if (pfad.canReach() || pfad.getEndNode() == null) {
            return pfad.canReach();
        }
        double ende = Vec3.atBottomCenterOf(pfad.getEndNode().asBlockPos()).distanceToSqr(punkt);
        if (ende <= (nah + 1.5) * (nah + 1.5)) {
            return true;
        }
        return Math.sqrt(ende) < position().distanceTo(punkt) - 2.0;
    }

    /**
     * Wie {@link #gehZu(BlockPos, double)}, aber für Öfen, Truhen und Werkbänke: Findet er keinen
     * Weg (etwa tief unten in seinem eigenen Tunnel), springt er direkt daneben. Steht er schon in
     * Reichweite, gilt er als angekommen, auch wenn kein Fußweg bis ganz heran führt.
     */
    public Weg hinkommen(BlockPos ziel, double nah) {
        Weg w = gehZu(ziel, nah);
        if (w != Weg.UNERREICHBAR) {
            return w;
        }
        if (inReichweite(ziel)) {
            getNavigation().stop();
            return Weg.DA;
        }
        return teleportiereNahe(ziel) ? Weg.UNTERWEGS : Weg.UNERREICHBAR;
    }

    /** Ist der Block in Reichweite der Hände? */
    public boolean inReichweite(BlockPos pos) {
        return getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) <= REICHWEITE * REICHWEITE;
    }

    public void schauAuf(BlockPos pos) {
        getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    /**
     * Springt (wie ein zahmer Wolf) an einen sicheren Platz in der Nähe des Ziels.
     * @return false, wenn kein sicherer Platz frei ist
     */
    public boolean teleportiereNahe(BlockPos ziel) {
        BlockPos platz = sichererPlatz(ziel, 3);
        if (platz == null) {
            return false;
        }
        // Erst aussteigen: Als Mitfahrer setzt ihn das Fahrzeug im nächsten Tick wieder auf seinen Sitz zurück (so blieb er
        // im Hubschrauber auf dem Rückflug sitzen, statt zum Herrn zu kommen)
        if (isPassenger()) {
            stopRiding();
        }
        welt().sendParticles(ParticleTypes.POOF, getX(), getY() + 1, getZ(), 12, 0.3, 0.6, 0.3, 0.02);
        teleportTo(platz.getX() + 0.5, platz.getY(), platz.getZ() + 0.5);
        welt().sendParticles(ParticleTypes.POOF, getX(), getY() + 1, getZ(), 12, 0.3, 0.6, 0.3, 0.02);
        playSound(SoundEvents.CHORUS_FRUIT_TELEPORT, 0.4F, 1.4F);
        wegVergessen();
        return true;
    }

    /** Freier Stehplatz (fester Boden, zwei Blöcke Luft, keine Flüssigkeit) nahe {@code mitte}. */
    public BlockPos sichererPlatz(BlockPos mitte, int radius) {
        BlockPos bester = null;
        double besterAbstand = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(mitte.offset(-radius, -2, -radius), mitte.offset(radius, 2, radius))) {
            double d = p.distSqr(mitte);
            if (d < besterAbstand && sichererStand(p)) {
                besterAbstand = d;
                bester = p.immutable();
            }
        }
        return bester;
    }

    private boolean sichererStand(BlockPos p) {
        BlockState boden = level().getBlockState(p.below());
        return !boden.getCollisionShape(level(), p.below()).isEmpty() && boden.getFluidState().isEmpty()
                && frei(p) && frei(p.above());
    }

    private boolean frei(BlockPos p) {
        BlockState s = level().getBlockState(p);
        return s.getCollisionShape(level(), p).isEmpty() && s.getFluidState().isEmpty();
    }

    // ------------------------------------------------------------------
    // Ziele (KI)
    // ------------------------------------------------------------------

    /** Arbeitet die Aufträge ab; hat Vorrang vor allem anderen außer Schwimmen. */
    private class ArbeitsZiel extends Goal {
        ArbeitsZiel() {
            // Ohne JUMP: den hält auch das Schwimm-Ziel (Priorität 0). Mit JUMP verdrängte es im Wasser die Arbeit, und
            // James trieb auf der Stelle, solange er im Wasser war (Aufträge über Flüsse oder in gefluteten Tunneln hingen)
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return !auftraege.isEmpty();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            arbeiten();
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }
    }

    /** Folgt dem Besitzer; ist er weit weg, springt der Butler hinterher. */
    private class FolgenZiel extends Goal {
        FolgenZiel() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            ServerPlayer p = besitzerOnline();
            return folgt() && untaetig() && !isPassenger() && p != null && p.level() == level() && !p.isSpectator() && distanceToSqr(p) > 3.5 * 3.5;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse() && distanceToSqr(besitzerOnline()) > 2.5 * 2.5;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;   // gehZu prüft „tickCount % 20“ – sonst nie dran bei ungerader Nummer (Goals laufen nur jeden 2. Tick)
        }

        @Override
        public void tick() {
            ServerPlayer p = besitzerOnline();
            if (p == null) {
                return;
            }
            getLookControl().setLookAt(p, 10.0F, getMaxHeadXRot());
            if (distanceToSqr(p) > 20 * 20) {
                teleportiereNahe(p.blockPosition());
            } else if (gehZu(p, 2.5) == Weg.UNERREICHBAR && distanceToSqr(p) > 8 * 8) {
                teleportiereNahe(p.blockPosition());
            }
        }

        @Override
        public void stop() {
            wegVergessen();
        }
    }

    /** Im Modus „bleiben“ kehrt er nach getaner Arbeit nach Hause zurück. */
    private class HeimZiel extends Goal {
        HeimZiel() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return untaetig() && !folgt() && !isPassenger() && !nahAmZuhause(8);
        }

        @Override
        public boolean canContinueToUse() {
            return untaetig() && !folgt() && !nahAmZuhause(2);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;   // gehZu prüft „tickCount % 20“ – sonst nie dran bei ungerader Nummer (Goals laufen nur jeden 2. Tick)
        }

        @Override
        public void tick() {
            if (distanceToSqr(Vec3.atBottomCenterOf(zuhause())) > 64 * 64 || gehZu(zuhause(), 1.5) == Weg.UNERREICHBAR) {
                teleportiereNahe(zuhause());
            }
        }

        @Override
        public void stop() {
            wegVergessen();
        }
    }

    // ------------------------------------------------------------------
    // Speichern
    // ------------------------------------------------------------------

    /**
     * Ist das der Butler eines Spielers (und nicht etwa ein Mitarbeiter, der dieselben Fähigkeiten nutzt)? Nur
     * Butler werden in der Butler-Verwaltung geführt.
     */
    public boolean istButler() {
        return true;
    }

    /** Für Unterklassen: eigene Werte mitspeichern. */
    protected void zusatzSchreiben(JsonObject j) {
    }

    /** Für Unterklassen: eigene Werte lesen. */
    protected void zusatzLesen(JsonObject j) {
    }

    private String datenAlsText() {
        JsonObject j = new JsonObject();
        zusatzSchreiben(j);
        if (besitzer != null) {
            j.addProperty("besitzer", besitzer.toString());
            j.addProperty("besitzerName", entityData.get(BESITZER_NAME));
        }
        if (heimat != null) {
            j.add("heimat", posJson(heimat));
        }
        if (zuhause != null) {
            j.add("zuhause", posJson(zuhause));
        }
        if (liefertruhe != null) {
            j.add("liefertruhe", posJson(liefertruhe));
        }
        j.addProperty("folgt", folgt());
        JsonObject o = new JsonObject();
        orte.forEach((name, pos) -> o.add(name, posJson(pos)));
        j.add("orte", o);
        JsonArray liste = new JsonArray();
        for (Auftrag a : auftraege) {
            liste.add(a.alsJson());
        }
        j.add("auftraege", liste);
        return j.toString();
    }

    private void datenAusText(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        try {
            JsonObject j = JsonParser.parseString(text).getAsJsonObject();
            zusatzLesen(j);
            if (j.has("besitzer")) {
                besitzerEintragen(UUID.fromString(j.get("besitzer").getAsString()),
                        j.has("besitzerName") ? j.get("besitzerName").getAsString() : "");
            }
            heimat = j.has("heimat") ? posAusJson(j.getAsJsonArray("heimat")) : null;
            zuhause = j.has("zuhause") ? posAusJson(j.getAsJsonArray("zuhause")) : null;
            liefertruhe = j.has("liefertruhe") ? posAusJson(j.getAsJsonArray("liefertruhe")) : null;
            setzeFolgen(j.has("folgt") && j.get("folgt").getAsBoolean());
            orte.clear();
            if (j.has("orte")) {
                j.getAsJsonObject("orte").entrySet().forEach(e -> orte.put(e.getKey(), posAusJson(e.getValue().getAsJsonArray())));
            }
            auftraege.clear();
            if (j.has("auftraege")) {
                for (JsonElement e : j.getAsJsonArray("auftraege")) {
                    Auftrag a = Auftrag.laden(e.getAsJsonObject());
                    if (a != null) {
                        auftraege.add(a);
                    }
                }
            }
        } catch (Exception ignoriert) {
            // kaputte Daten: Standardwerte behalten
        }
    }

    public static JsonArray posJson(BlockPos p) {
        JsonArray a = new JsonArray();
        a.add(p.getX());
        a.add(p.getY());
        a.add(p.getZ());
        return a;
    }

    public static BlockPos posAusJson(JsonArray a) {
        return new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt());
    }

    //? if >=1.21.6 {
    @Override
    protected void addAdditionalSaveData(ValueOutput ausgabe) {
        super.addAdditionalSaveData(ausgabe);
        ausgabe.putString(SCHLUESSEL, datenAlsText());
        taschen.storeAsItemList(ausgabe.list("Taschen", ItemStack.CODEC));
    }

    @Override
    protected void readAdditionalSaveData(ValueInput eingabe) {
        super.readAdditionalSaveData(eingabe);
        datenAusText(eingabe.getStringOr(SCHLUESSEL, ""));
        taschen.fromItemList(eingabe.listOrEmpty("Taschen", ItemStack.CODEC));
    }
    //?} else if >=1.21.5 {
    /*@Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString(SCHLUESSEL, datenAlsText());
        tag.put("Taschen", taschen.createTag(registryAccess()));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        datenAusText(tag.getStringOr(SCHLUESSEL, ""));
        taschen.fromTag(tag.getListOrEmpty("Taschen"), registryAccess());
    }
    *///?} else {
    /*@Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString(SCHLUESSEL, datenAlsText());
        tag.put("Taschen", taschen.createTag(registryAccess()));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        datenAusText(tag.getString(SCHLUESSEL));
        taschen.fromTag(tag.getList("Taschen", net.minecraft.nbt.Tag.TAG_COMPOUND), registryAccess());
    }
    *///?}
}
