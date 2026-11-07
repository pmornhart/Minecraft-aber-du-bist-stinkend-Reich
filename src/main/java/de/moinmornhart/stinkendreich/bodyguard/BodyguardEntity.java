package de.moinmornhart.stinkendreich.bodyguard;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
// Der Eisengolem ist mit 1.21.11 in ein eigenes Paket gezogen
//? if >=1.21.11 {
import net.minecraft.world.entity.animal.golem.IronGolem;
//?} else
/*import net.minecraft.world.entity.animal.IronGolem;*/
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
//? if >=1.21.6 {
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
//?} else {
/*import net.minecraft.nbt.CompoundTag;
*///?}

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Bodyguard: ein Eisengolem im Smoking mit Sonnenbrille und goldener Pistole. Er folgt seinem Chef, kann nie
 * sterben (nur /kill) und beschützt nur: Er greift Monster in der Nähe des Chefs an und jeden, der den Chef angreift.
 * Wie ein echter Personenschützer bleibt er beim Chef und schießt von dort mit der Pistole (Sofort-Treffer, ohne
 * Blockschaden); erst wenn ein Gegner nah herankommt, geht er hin und schlägt zu wie ein Eisengolem (wirft Gegner in
 * die Luft). Spieler und Tiere lässt er in Ruhe. Beim Schießen zielt er: Er dreht sich zum Gegner und hebt den Arm mit
 * der Pistole (der Client liest dafür {@link #zielt()}). Zwei Bodyguards teilen sich die Gegner auf, und wer in einer
 * Wand steckt oder nicht hinterherkommt, steht gleich wieder neben dem Chef.
 */
public class BodyguardEntity extends IronGolem {
    private static final String SCHLUESSEL = "stinkendreich";
    /** Ab diesem Abstand schießt er statt zuzuschlagen (Blöcke). */
    private static final double SCHUSS_AB = 4.5, SCHUSS_BIS = 24.0;
    /** Näher als das kommt ein Gegner, dann geht er zum Nahkampf hin (und bleibt dran, bis der Gegner weiter weg ist). */
    private static final double NAHKAMPF_AB = 5.5, NAHKAMPF_BIS = 8.0;
    private static final int NACHLADEN = 18;
    /** Zielt gerade auf einen Gegner (für den Arm auf dem Client). */
    private static final EntityDataAccessor<Boolean> ZIELT = SynchedEntityData.defineId(BodyguardEntity.class, EntityDataSerializers.BOOLEAN);
    /** Im Nahkampf: Die Pistole ist weggesteckt, er kämpft mit den Fäusten (für den Client). */
    private static final EntityDataAccessor<Boolean> NAHKAMPF = SynchedEntityData.defineId(BodyguardEntity.class, EntityDataSerializers.BOOLEAN);

    private UUID chef;
    private int nachladen;
    /** Für das Erkennen, ob er feststeckt: Stelle bei der letzten Prüfung und wie oft er seitdem nicht vorankam. */
    private Vec3 letzteStelle;
    private int festgesteckt;

    public BodyguardEntity(EntityType<? extends IronGolem> typ, Level welt) {
        super(typ, welt);
        setPersistenceRequired();
        setPlayerCreated(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder daten) {
        super.defineSynchedData(daten);
        daten.define(ZIELT, false);
        daten.define(NAHKAMPF, false);
    }

    /** Ist er im Nahkampf (auf dem Client: Pistole weggesteckt)? */
    public boolean nahkampf() {
        return entityData.get(NAHKAMPF);
    }

    /** Zielt er gerade mit der Pistole (auf dem Client: Arm gehoben)? */
    public boolean zielt() {
        return entityData.get(ZIELT);
    }

    public static AttributeSupplier.Builder attribute() {
        return IronGolem.createAttributes().add(Attributes.MOVEMENT_SPEED, 0.32).add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    protected void registerGoals() {
        // Nahkampf wie ein Eisengolem, aber nur gegen Gegner in der Nähe und nie gegen Creeper (die erledigt er aus der
        // Ferne mit der Pistole); weiter weg bleibt er beim Chef und schießt
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.1, true) {
            @Override
            public boolean canUse() {
                LivingEntity z = getTarget();
                return !isPassenger() && z != null && !(z instanceof Creeper) && distanceTo(z) < NAHKAMPF_AB && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                LivingEntity z = getTarget();
                return z != null && !(z instanceof Creeper) && distanceTo(z) < NAHKAMPF_BIS && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(2, new FolgenZiel());
        goalSelector.addGoal(3, new ZielenZiel());
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    public void setzeChef(Player p) {
        chef = p.getUUID();
        setCustomName(Component.translatable("stinkendreich.bodyguard.name"));
    }

    public UUID chef() {
        return chef;
    }

    private ServerPlayer chefOnline() {
        return chef == null || !(level() instanceof ServerLevel sl) ? null : sl.getServer().getPlayerList().getPlayer(chef);
    }

    @Override
    public boolean removeWhenFarAway(double abstand) {
        return false;
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
        if (nachladen > 0) {
            nachladen--;
        }
        if (tickCount % 10 == 0) {
            // Zielwahl im eigenen Tick, nicht als Ziel-Goal: Minecraft fragt Goals nur jeden zweiten Tick (je nach
            // gerader oder ungerader Nummer) – mit „tickCount % 10“ darin kam ein Bodyguard mit ungerader Nummer nie
            // dran und stand nur herum, während der andere alles machte. Auch null: Ist kein Gegner mehr beim Chef,
            // lässt er ab (statt einem weggelaufenen hinterherzuzielen).
            LivingEntity neu = zielSuchen();
            if (neu != getTarget()) {
                setTarget(neu);
            }
        }
        if (tickCount % 10 == 5) {
            mitfahren();
        }
        LivingEntity ziel = getTarget();
        boolean zielen = false, nah = false;
        if (ziel != null && ziel.isAlive()) {
            double d = distanceTo(ziel);
            // Im Auto wird nur geschossen (auch auf nahe Gegner), Nahkampf gibt es erst nach dem Aussteigen
            zielen = (d > SCHUSS_AB || ziel instanceof Creeper || isPassenger()) && d < SCHUSS_BIS && hasLineOfSight(ziel);
            // Pistole weg, sobald es in den Nahkampf geht (wie das Nahkampf-Goal: Creeper nie)
            nah = !zielen && !isPassenger() && !(ziel instanceof Creeper) && d < (nahkampf() ? NAHKAMPF_BIS : NAHKAMPF_AB);
        }
        if (nahkampf() != nah) {
            entityData.set(NAHKAMPF, nah);
        }
        if (zielen) {
            // Zum Gegner drehen: Kopf und Körper, damit der gehobene Arm wirklich auf ihn zeigt
            getLookControl().setLookAt(ziel, 60.0F, 60.0F);
            setYBodyRot(getYHeadRot());
            if (nachladen == 0) {
                schiessen((ServerLevel) level(), ziel);
            }
        }
        //? if >=1.21.9 {
        if (nah && !getMainHandItem().isEmpty()) {
            // Im Nahkampf steckt die Pistole weg (auch bei Spielern ohne Mod)
            setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, net.minecraft.world.item.ItemStack.EMPTY);
        } else if (!nah && getMainHandItem().isEmpty()) {
            // Die Pistole sieht nur, wer die Mod nicht hat (Server-Version: Schaufensterpuppe); der Golem zeigt sie nicht
            setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(
                    de.moinmornhart.stinkendreich.register.ModItems.GOLDENE_PISTOLE));
            setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 0.0F);
        }
        //?}
        if (zielt() != zielen) {
            entityData.set(ZIELT, zielen);
            if (de.moinmornhart.stinkendreich.dev.Autotest.aktiv()) {
                de.moinmornhart.stinkendreich.StinkendReich.LOGGER.info("Autotest: Bodyguard {} {}", getId(),
                        zielen ? "zielt auf " + ziel.getType().getDescriptionId() + " " + ziel.getId() : "zielt nicht mehr");
            }
            //? if >=1.21.9
            handZeigen(zielen);
        }
        if (tickCount % 40 == 0) {
            feststeckenPruefen();
        }
        if (tickCount % 20 == 0 && de.moinmornhart.stinkendreich.dev.Autotest.aktiv()) {
            // Testprotokoll: was jeder Bodyguard gerade tut
            ServerPlayer c = chefOnline();
            StringBuilder ziele = new StringBuilder();
            goalSelector.getAvailableGoals().stream().filter(net.minecraft.world.entity.ai.goal.WrappedGoal::isRunning)
                    .forEach(g -> ziele.append(g.getGoal().getClass().getSimpleName()).append(' '));
            de.moinmornhart.stinkendreich.StinkendReich.LOGGER.info("Autotest: Bodyguard {} bei {} {} {} Ziel {} Abstand {} Chef {} läuft {}",
                    getId(), (int) getX(), (int) getY(), (int) getZ(), ziel == null ? "-" : ziel.getId(),
                    ziel == null ? "-" : String.format("%.1f", distanceTo(ziel)), c == null ? "-" : String.format("%.1f", distanceTo(c)), ziele);
        }
    }

    /**
     * Mitfahren (Philip, 07.11.: „dass die Bodyguards auch wirklich in Autos sitzen können und mich vom Auto aus
     * verteidigen“): Sitzt der Chef in einem Wagen oder Hubschrauber mit freiem Platz, steigt der Bodyguard dazu und schießt
     * während der Fahrt aus dem Wagen auf Gegner. Steigt der Chef aus, steigt er auch aus und stellt sich neben ihn.
     */
    private void mitfahren() {
        ServerPlayer c = chefOnline();
        Entity wagen = c == null ? null : c.getVehicle();
        if (wagen instanceof de.moinmornhart.stinkendreich.auto.AutoEntity auto && c.level() == level()) {
            if (getVehicle() != auto && auto.getPassengers().size() < auto.art.sitze.length && distanceToSqr(auto) < 32 * 32) {
                stopRiding();
                getNavigation().stop();
                setTarget(null);
                de.moinmornhart.stinkendreich.Kompat.einsteigen(this, auto);
            }
        } else if (getVehicle() instanceof de.moinmornhart.stinkendreich.auto.AutoEntity) {
            stopRiding();
            if (c != null && c.level() == level()) {
                Vec3 frei = Bodyguards.freieStelle((ServerLevel) level(), this, c, c.getYRot() + 180);
                teleportTo(frei.x, frei.y, frei.z);
            }
        }
    }

    /**
     * Steckt er in einer Wand oder kommt seit vier Sekunden nicht voran, obwohl der Chef weiter weg ist (z. B. hinter
     * einer Tür, die ein Golem nicht öffnen kann), steht er gleich wieder an einer freien Stelle neben dem Chef.
     */
    private void feststeckenPruefen() {
        ServerPlayer c = chefOnline();
        if (c == null || c.level() != level() || isPassenger()) {
            return;
        }
        boolean steht = letzteStelle != null && letzteStelle.distanceToSqr(position()) < 0.25;
        boolean mussLaufen = distanceToSqr(c) > 5 * 5 && !zielt();
        festgesteckt = steht && mussLaufen ? festgesteckt + 1 : 0;
        letzteStelle = position();
        if (isInWall() || festgesteckt >= 2) {
            Vec3 frei = Bodyguards.freieStelle((ServerLevel) level(), this, c, c.getYRot() + 180);
            teleportTo(frei.x, frei.y, frei.z);
            getNavigation().stop();
            festgesteckt = 0;
        }
    }

    //? if >=1.21.9 {
    /**
     * Spieler ohne Mod sehen den Bodyguard als Schaufensterpuppe (Server-Version). Zielt er, bekommt die Puppe eine
     * geladene Armbrust mit dem Bild der Pistole in die Hand – Minecraft zeichnet dann die Zielhaltung mit beiden Armen;
     * sonst die Pistole. Spieler mit Mod sehen die Hand eines Golems nicht, für sie ändert sich nichts.
     */
    private void handZeigen(boolean zielen) {
        net.minecraft.world.item.ItemStack hand = zielen ? imAnschlag() : getMainHandItem().copy();
        ((ServerLevel) level()).getChunkSource().sendToTrackingPlayers(this, new net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket(
                getId(), List.of(com.mojang.datafixers.util.Pair.of(net.minecraft.world.entity.EquipmentSlot.MAINHAND, hand))));
    }

    private static net.minecraft.world.item.ItemStack imAnschlag() {
        net.minecraft.world.item.ItemStack s = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CROSSBOW);
        s.set(net.minecraft.core.component.DataComponents.ITEM_MODEL, de.moinmornhart.stinkendreich.StinkendReich.id("goldene_pistole"));
        List<net.minecraft.world.item.ItemStack> pfeil = List.of(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW));
        //? if >=26.1 {
        s.set(net.minecraft.core.component.DataComponents.CHARGED_PROJECTILES, net.minecraft.world.item.component.ChargedProjectiles.ofNonEmpty(pfeil));
        //?} else
        /*s.set(net.minecraft.core.component.DataComponents.CHARGED_PROJECTILES, net.minecraft.world.item.component.ChargedProjectiles.of(pfeil));*/
        return s;
    }
    //?}

    /** Ein Schuss mit der goldenen Pistole: Mündungsfeuer, Leuchtspur, Treffer. */
    private void schiessen(ServerLevel welt, LivingEntity ziel) {
        nachladen = NACHLADEN;
        getLookControl().setLookAt(ziel, 90, 90);
        // Mündung: vom rechten Schultergelenk (der gehobene Arm) eine Armlänge mit Pistole Richtung Gegner
        double gier = Math.toRadians(yBodyRot);
        Vec3 schulter = position().add(-Math.cos(gier) * 0.7, 2.35, -Math.sin(gier) * 0.7);
        Vec3 nach = ziel.position().add(0, ziel.getBbHeight() * 0.6, 0);
        Vec3 von = schulter.add(nach.subtract(schulter).normalize().scale(2.0));
        Vec3 schritt = nach.subtract(von).normalize().scale(0.6);
        Vec3 p = von;
        for (int i = 0; i < 48 && p.distanceToSqr(nach) > 0.5; i++) {
            welt.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            p = p.add(schritt);
        }
        welt.sendParticles(ParticleTypes.FLAME, von.x, von.y, von.z, 4, 0.05, 0.05, 0.05, 0.01);
        welt.playSound(null, blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.HOSTILE, 1.2F, 1.6F);
        ziel.hurt(damageSources().mobAttack(this), 7.0F);
    }

    // ------------------------------------------------------------------
    // Ziele
    // ------------------------------------------------------------------

    /**
     * Bleibt beim Chef: über 6 Blöcke läuft er hinterher (beim Zielen erst über 12, damit er in Ruhe schießen kann),
     * über 28 Blöcke springt er zu ihm.
     */
    private class FolgenZiel extends Goal {
        FolgenZiel() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            ServerPlayer c = chefOnline();
            double weit = zielt() ? 12 : 6;
            return !isPassenger() && c != null && c.level() == level() && distanceToSqr(c) > weit * weit;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;   // sonst käme „tickCount % 10“ unten bei ungerader Nummer nie dran
        }

        @Override
        public void tick() {
            ServerPlayer c = chefOnline();
            if (c == null) {
                return;
            }
            if (distanceToSqr(c) > 28 * 28) {
                Vec3 frei = Bodyguards.freieStelle((ServerLevel) level(), BodyguardEntity.this, c, c.getYRot() + 180);
                teleportTo(frei.x, frei.y, frei.z);
                getNavigation().stop();
            } else if (tickCount % 10 == 0) {
                getNavigation().moveTo(c, 1.1);
            }
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }
    }

    /** Beim Zielen schaut er den Gegner an (geht vor dem Umherschauen, damit der Arm wirklich auf den Gegner zeigt). */
    private class ZielenZiel extends Goal {
        ZielenZiel() {
            setFlags(EnumSet.of(Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return zielt() && getTarget() != null;
        }

        @Override
        public void tick() {
            LivingEntity z = getTarget();
            if (z != null) {
                getLookControl().setLookAt(z, 60.0F, 60.0F);
            }
        }
    }

    /**
     * Wer den Chef angreift oder vom Chef angegriffen wird, und Monster in seiner Nähe (Creeper zuerst). Bei mehreren
     * Monstern nimmt jeder Bodyguard ein anderes, damit nicht einer alles macht und der andere zuschaut.
     */
    private LivingEntity zielSuchen() {
        ServerPlayer c = chefOnline();
        if (c == null || c.level() != level()) {
            return null;
        }
        LivingEntity angreifer = c.getLastHurtByMob();
        if (angreifer != null && angreifer.isAlive() && angreifer != BodyguardEntity.this && angreifer.distanceToSqr(c) < 32 * 32
                && !(angreifer instanceof BodyguardEntity)) {
            return angreifer;
        }
        LivingEntity angegriffen = c.getLastHurtMob();
        if (angegriffen != null && angegriffen.isAlive() && angegriffen instanceof Enemy && angegriffen.distanceToSqr(c) < 32 * 32) {
            return angegriffen;
        }
        List<LivingEntity> monster = new ArrayList<>(level().getEntitiesOfClass(LivingEntity.class,
                c.getBoundingBox().inflate(16), e -> e instanceof Enemy && e.isAlive() && e.distanceToSqr(c) < 16 * 16));
        // Creeper zählen näher, damit sie weg sind, bevor sie zischen
        monster.sort(Comparator.comparingDouble(e -> e.distanceToSqr(c) * (e instanceof Creeper ? 0.25 : 1.0)));
        if (monster.isEmpty()) {
            return null;
        }
        // Was die anderen Bodyguards des Chefs schon im Visier haben, kommt erst danach an die Reihe
        List<LivingEntity> belegt = new ArrayList<>();
        boolean geteilt = false;
        LivingEntity jetzt = getTarget();
        for (BodyguardEntity b : level().getEntitiesOfClass(BodyguardEntity.class, c.getBoundingBox().inflate(48),
                b -> b != BodyguardEntity.this && c.getUUID().equals(b.chef()))) {
            if (b.getTarget() != null) {
                belegt.add(b.getTarget());
                // Haben zwei dasselbe Ziel, sucht sich nur einer (der mit der größeren Nummer) ein anderes
                geteilt |= b.getTarget() == jetzt && getId() > b.getId();
            }
        }
        LivingEntity frei = monster.stream().filter(e -> !belegt.contains(e)).findFirst().orElse(null);
        // Das bisherige Ziel behalten, solange es noch da ist (kein Hin und Her), außer ein anderes ist frei und
        // der andere Bodyguard kümmert sich schon um dieses
        if (jetzt != null && jetzt.isAlive() && monster.contains(jetzt) && !(geteilt && frei != null)) {
            return jetzt;
        }
        return frei != null ? frei : monster.getFirst();
    }

    // ------------------------------------------------------------------
    // Speichern (Chef)
    // ------------------------------------------------------------------

    //? if >=1.21.6 {
    @Override
    protected void addAdditionalSaveData(ValueOutput ausgabe) {
        super.addAdditionalSaveData(ausgabe);
        ausgabe.putString(SCHLUESSEL, chef == null ? "" : chef.toString());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput eingabe) {
        super.readAdditionalSaveData(eingabe);
        chefAusText(eingabe.getStringOr(SCHLUESSEL, ""));
    }
    //?} else if >=1.21.5 {
    /*@Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString(SCHLUESSEL, chef == null ? "" : chef.toString());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        chefAusText(tag.getStringOr(SCHLUESSEL, ""));
    }
    *///?} else {
    /*@Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString(SCHLUESSEL, chef == null ? "" : chef.toString());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        chefAusText(tag.getString(SCHLUESSEL));
    }
    *///?}

    private void chefAusText(String text) {
        try {
            chef = text.isEmpty() ? null : UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            chef = null;
        }
    }
}
