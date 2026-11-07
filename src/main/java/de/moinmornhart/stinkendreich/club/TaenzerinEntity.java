package de.moinmornhart.stinkendreich.club;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
//? if >=1.21.6 {
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
//?} else {
/*import net.minecraft.nbt.CompoundTag;
*///?}

import java.util.EnumSet;

/**
 * Show-Tänzerin im Geheimclub. Zwei Arten:
 * <ul>
 *   <li>{@code stange}: tanzt an einer Tanzstange und dreht sich langsam um sie herum (der Anker ist die Mitte der
 *   Stange, gesetzt beim ersten Tick aus der Startposition),</li>
 *   <li>{@code laufen}: läuft durch den Club (höchstens {@link #LAUF_RADIUS} Blöcke vom Anker weg) und tanzt, wenn
 *   sie stehen bleibt.</li>
 * </ul>
 * Sie ist unverwundbar (nur /kill), verschwindet nie von selbst und lässt sich nicht wegschieben, solange sie an der
 * Stange tanzt. Das Kostüm (eins von {@link #VARIANTEN}) sucht der Bauplan aus.
 */
public class TaenzerinEntity extends PathfinderMob {
    public static final int VARIANTEN = 4;
    private static final EntityDataAccessor<Integer> VARIANTE = SynchedEntityData.defineId(TaenzerinEntity.class, EntityDataSerializers.INT);
    /** Tanz für das Modell: 0 = läuft, 1 = tanzt frei (Arme oben), 2 = tanzt an der Stange. */
    private static final EntityDataAccessor<Integer> TANZ = SynchedEntityData.defineId(TaenzerinEntity.class, EntityDataSerializers.INT);
    public static final int LAEUFT = 0, FREI = 1, STANGE = 2;
    private static final String SCHLUESSEL = "stinkendreich";
    /** Abstand der Füße von der Mitte der Stange. */
    private static final double STANGE_RADIUS = 0.45;
    /** Drehung um die Stange pro Tick in Grad (eine Runde in 15 Sekunden). */
    private static final float STANGE_TEMPO = 1.2F;
    private static final int LAUF_RADIUS = 5;

    private boolean anStange = true;
    /** Mitte der Stange bzw. Mitte des Laufbereichs; null bis zum ersten Tick. */
    private Vec3 anker;
    private float winkel;

    public TaenzerinEntity(EntityType<? extends TaenzerinEntity> typ, Level welt) {
        super(typ, welt);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder attribute() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder daten) {
        super.defineSynchedData(daten);
        daten.define(VARIANTE, 0);
        daten.define(TANZ, STANGE);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new SchlenderZiel());
        goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(3, new RandomLookAroundGoal(this));
    }

    /** Kostüm 0 … {@link #VARIANTEN} − 1 (für die Textur auf dem Client). */
    public int variante() {
        return Math.floorMod(entityData.get(VARIANTE), VARIANTEN);
    }

    /** Wie sie gerade tanzt ({@link #LAEUFT}, {@link #FREI} oder {@link #STANGE}). */
    public int tanz() {
        return entityData.get(TANZ);
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
        return !anStange && super.isPushable();
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
        if (anker == null) {
            anker = position();
        }
        if (tickCount % 100 == 17) {
            ankerPruefen();
        }
        if (anStange) {
            tanzeAnStange();
        } else {
            boolean steht = getNavigation().isDone() && getDeltaMovement().horizontalDistanceSqr() < 0.0004;
            entityData.set(TANZ, steht ? FREI : LAEUFT);
        }
    }

    /** Fährt die Villa im Inkognito-Modus um dy Blöcke, fährt der Anker mit (sonst zieht es sie zur alten Höhe). */
    public void hoeheVerschieben(int dy) {
        if (anker != null) {
            anker = anker.add(0, dy, 0);
        }
    }

    /**
     * Passt der Anker noch zum Club? An der Stange steht im Anker-Block die Tanzstange, beim Laufen steht sie auf dem
     * Clubboden (polierter Schwarzstein). Falls nicht – etwa weil die Villa vor 0.4.8 im Inkognito-Modus abgetaucht ist
     * und der Anker oben blieb (Philips Bild vom 07.11.: drei Tänzerinnen draußen vor der Felswand) –, wird in derselben
     * Spalte bis 80 Blöcke darüber und darunter die nächste passende Höhe gesucht und die Tänzerin dorthin gesetzt.
     */
    private void ankerPruefen() {
        if (de.moinmornhart.stinkendreich.mechanik.Inkognito.aktiv(level().getServer())) {
            return;
        }
        net.minecraft.core.BlockPos a = net.minecraft.core.BlockPos.containing(anker);
        if (!level().hasChunkAt(a) || passt(a)) {
            return;
        }
        for (int d = 1; d <= 80; d++) {
            for (int richtung : new int[]{-1, 1}) {
                net.minecraft.core.BlockPos q = a.above(d * richtung);
                if (passt(q)) {
                    anker = new Vec3(anker.x, anker.y + d * richtung, anker.z);
                    getNavigation().stop();
                    teleportTo(anStange ? getX() : anker.x, getY() + d * richtung, anStange ? getZ() : anker.z);
                    de.moinmornhart.stinkendreich.StinkendReich.LOGGER.info("Tänzerin: Anker um {} Blöcke zurück in den Club gesetzt", d * richtung);
                    return;
                }
            }
        }
    }

    private boolean passt(net.minecraft.core.BlockPos a) {
        if (anStange) {
            var id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level().getBlockState(a).getBlock());
            return id != null && id.getPath().equals("tanzstange");
        }
        var unten = level().getBlockState(a.below());
        return unten.is(net.minecraft.world.level.block.Blocks.POLISHED_BLACKSTONE) && !level().getBlockState(a).isSolid();
    }

    /** Kreist langsam um die Stange; die Stange ist dabei rechts von ihr (rechte Hand an der Stange). */
    private void tanzeAnStange() {
        setNoGravity(true);
        getNavigation().stop();
        winkel = (winkel + STANGE_TEMPO) % 360.0F;
        double bogen = Math.toRadians(winkel);
        // Leichtes Auf und Ab, als würde sie an der Stange hochgehen und wieder herunter
        double hoehe = 0.12 * (1.0 - Math.cos(bogen * 2.0));
        setPos(anker.x + Math.cos(bogen) * STANGE_RADIUS, anker.y + hoehe, anker.z + Math.sin(bogen) * STANGE_RADIUS);
        setDeltaMovement(Vec3.ZERO);
        // Blickrichtung = Laufrichtung auf dem Kreis; Minecraft misst den Winkel so, dass dann die Mitte rechts liegt
        setYRot(winkel);
        yBodyRot = winkel;
        yHeadRot = winkel;
        entityData.set(TANZ, STANGE);
    }

    /** Beim Laufen: alle paar Sekunden ein neues Ziel in der Nähe des Ankers, dazwischen stehen und tanzen. */
    private class SchlenderZiel extends Goal {
        private int warten;

        SchlenderZiel() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (anStange || anker == null) {
                return false;
            }
            if (--warten > 0) {
                return false;
            }
            warten = 60 + getRandom().nextInt(140);
            return true;
        }

        @Override
        public void start() {
            double x = anker.x + (getRandom().nextDouble() * 2.0 - 1.0) * LAUF_RADIUS;
            double z = anker.z + (getRandom().nextDouble() * 2.0 - 1.0) * LAUF_RADIUS;
            getNavigation().moveTo(x, anker.y, z, 0.8);
        }

        @Override
        public boolean canContinueToUse() {
            return !getNavigation().isDone();
        }
    }

    // ------------------------------------------------------------------
    // Speichern: „Art|Kostüm|Anker x|y|z“ (Anker fehlt im Bauplan, er entsteht beim ersten Tick)
    // ------------------------------------------------------------------

    private String datenAlsText() {
        String text = (anStange ? "stange" : "laufen") + "|" + variante();
        if (anker != null) {
            text += "|" + anker.x + "|" + anker.y + "|" + anker.z;
        }
        return text;
    }

    private void datenAusText(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        String[] teile = text.split("\\|");
        anStange = !teile[0].equals("laufen");
        try {
            if (teile.length > 1) {
                entityData.set(VARIANTE, Integer.parseInt(teile[1]));
            }
            if (teile.length > 4) {
                anker = new Vec3(Double.parseDouble(teile[2]), Double.parseDouble(teile[3]), Double.parseDouble(teile[4]));
            }
        } catch (NumberFormatException e) {
            // Unlesbare Zahlen: Standardkostüm, Anker aus der aktuellen Position
        }
        if (!anStange) {
            setNoGravity(false);
        }
    }

    //? if >=1.21.6 {
    @Override
    protected void addAdditionalSaveData(ValueOutput ausgabe) {
        super.addAdditionalSaveData(ausgabe);
        ausgabe.putString(SCHLUESSEL, datenAlsText());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput eingabe) {
        super.readAdditionalSaveData(eingabe);
        datenAusText(eingabe.getStringOr(SCHLUESSEL, ""));
    }
    //?} else if >=1.21.5 {
    /*@Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString(SCHLUESSEL, datenAlsText());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        datenAusText(tag.getStringOr(SCHLUESSEL, ""));
    }
    *///?} else {
    /*@Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString(SCHLUESSEL, datenAlsText());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        datenAusText(tag.getString(SCHLUESSEL));
    }
    *///?}
}
