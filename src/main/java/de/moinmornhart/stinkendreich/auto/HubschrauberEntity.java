package de.moinmornhart.stinkendreich.auto;

import de.moinmornhart.stinkendreich.Konfig;
import de.moinmornhart.stinkendreich.mechanik.KlientBruecke;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Der Luxus-Hubschrauber. Sitzt ein Pilot drin, läuft der Rotor in gut vier Sekunden hoch; erst dann
 * hebt er ab. Steuerung: Leertaste steigen, Strg sinken (ohne beides hält er die Höhe), W/S vor und
 * zurück (mit Trägheit), A/D drehen. Ohne Pilot läuft der Rotor aus, und er sinkt langsam zu Boden.
 *
 * <p>Gesteuert wird wie beim Auto auf dem Rechner des Piloten; fliegt der Butler, rechnet der Server.
 * Neigen und Rollen sind nur Darstellung und werden auf jedem Rechner aus der Bewegung berechnet.</p>
 */
public class HubschrauberEntity extends AutoEntity {
    /** Rotordrehzahl 0 … 1 (ab 0,85 trägt er). */
    private float rotor;
    /** Geschwindigkeit nach vorn in Blöcken pro Tick (auf dem steuernden Rechner). */
    private double vorwaerts;
    private double steigen;

    public HubschrauberEntity(EntityType<? extends AutoEntity> typ, Level welt) {
        super(typ, welt, Autotyp.HUBSCHRAUBER);
        setNoGravity(true);
    }

    /** Chunk, den der Hubschrauber gerade zwangsgeladen hält (Long.MIN_VALUE = keiner). */
    private long gehalten = Long.MIN_VALUE;

    /**
     * Solange James fliegt, hält der Hubschrauber seinen Chunk geladen (der Ladeschein wandert mit). Sonst bliebe er
     * stehen, sobald er außerhalb der Simulationsweite eines Spielers ist – etwa wenn er 90 Blöcke entfernt im Anflug
     * auftaucht oder einen Wagen nach Hause trägt, während du woanders bist. In zwangsgeladenen Chunks ticken Figuren.
     */
    public void chunkHalten(boolean an) {
        if (!(level() instanceof net.minecraft.server.level.ServerLevel welt)) {
            return;
        }
        int cx = blockPosition().getX() >> 4, cz = blockPosition().getZ() >> 4;
        long k = an ? ((long) cx << 32) ^ (cz & 0xffffffffL) : Long.MIN_VALUE;
        if (k == gehalten) {
            return;
        }
        if (gehalten != Long.MIN_VALUE) {
            welt.setChunkForced((int) (gehalten >> 32), (int) gehalten, false);
        }
        if (an) {
            welt.setChunkForced(cx, cz, true);
        }
        gehalten = k;
    }

    @Override
    public void remove(RemovalReason grund) {
        chunkHalten(false);
        super.remove(grund);
    }

    /** Rotor sofort auf voller Drehzahl (Hubschrauber taucht im Anflug in der Luft auf). */
    public void rotorAn() {
        rotor = 1.0F;
        steigen = 0;
    }

    /** Rotordrehzahl 0 … 1. */
    public float rotor() {
        return rotor;
    }

    /** Höchsttempo nach vorn (Blöcke pro Tick). */
    @Override
    public double hoechstTempo() {
        return Math.max(0.3, Konfig.get().hubschrauberTempo / 72.0);
    }

    @Override
    public double tempo() {
        return vorwaerts;
    }

    @Override
    public void travel(Vec3 eingabe) {
        LivingEntity pilot = getControllingPassenger();
        double gas = 0, drehen = 0, hoch = 0;
        if (pilot instanceof Player p) {
            gas = p.zza;
            drehen = p.xxa;
            if (level().isClientSide()) {
                hoch = (KlientBruecke.handbremse.getAsBoolean() ? 1 : 0) - (KlientBruecke.sinken.getAsBoolean() ? 1 : 0);
            }
            setYHeadRot(getYRot());
        }
        //? if >=1.21.6 {
        else if (fahrerOhneMod() instanceof net.minecraft.server.level.ServerPlayer sp) {
            // Spieler ohne Mod: Leertaste steigt, Sprinttaste (Strg) sinkt (Schleichen heißt bei ihm aussteigen)
            net.minecraft.world.entity.player.Input tasten = sp.getLastClientInput();
            gas = (tasten.forward() ? 1 : 0) - (tasten.backward() ? 1 : 0);
            drehen = (tasten.left() ? 1 : 0) - (tasten.right() ? 1 : 0);
            hoch = (tasten.jump() ? 1 : 0) - (tasten.sprint() ? 1 : 0);
            setYHeadRot(getYRot());
        }
        //?}
        else if (pilot instanceof de.moinmornhart.stinkendreich.butler.ButlerEntity butler) {
            // Der Butler fliegt (Server); ohne Flugauftrag landet er
            if (butler.faehrtGerade()) {
                gas = chauffeurGas;
                drehen = chauffeurLenk;
                hoch = chauffeurHoch;
            } else {
                gas = -Mth.clamp(vorwaerts / hoechstTempo() * 3.0, -1, 1);
                hoch = Math.abs(vorwaerts) < 0.2 ? -1 : 0;
            }
            setYHeadRot(getYRot());
        }
        boolean traegt = rotor > 0.85F;
        double max = hoechstTempo();

        // Drehen um die Hochachse (in der Luft und am Boden mit laufendem Rotor)
        setYRot(getYRot() - (float) (drehen * 3.0 * rotor));
        yBodyRot = getYRot();

        // Vorwärts mit Trägheit; am Boden rollt er nicht
        if (traegt && !onGround()) {
            vorwaerts += (gas * max - vorwaerts) * (gas == 0 ? 0.02 : 0.035);
        } else {
            vorwaerts *= 0.6;
        }
        // Steigen und Sinken; ohne Auftrieb sinkt er langsam (Autorotation)
        if (traegt) {
            double soll = hoch > 0 ? 0.32 : (hoch < 0 ? -0.28 : 0.0);
            steigen += (soll - steigen) * 0.12;
            if (onGround() && steigen < 0) {
                steigen = 0;
            }
        } else {
            steigen = onGround() ? 0 : Math.max(-0.45, steigen - 0.03);
        }
        Vec3 richtung = new Vec3(-Mth.sin(getYRot() * Mth.DEG_TO_RAD), 0, Mth.cos(getYRot() * Mth.DEG_TO_RAD));
        move(MoverType.SELF, new Vec3(richtung.x * vorwaerts, steigen, richtung.z * vorwaerts));
        if (horizontalCollision) {
            vorwaerts *= 0.3;
        }
        if (verticalCollision) {
            steigen = 0;
        }
        setDeltaMovement(richtung.x * vorwaerts, steigen, richtung.z * vorwaerts);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide()) {
            chunkHalten(getControllingPassenger() instanceof de.moinmornhart.stinkendreich.butler.ButlerEntity b && b.faehrtGerade());
        }
        // Rotor läuft mit Pilot hoch und ohne aus (auf jedem Rechner gleich, die Insassen sind abgeglichen)
        rotor = getControllingPassenger() != null || fahrerOhneMod() != null ? Math.min(1.0F, rotor + 0.012F) : Math.max(0.0F, rotor - 0.006F);
        rotorWinkelAlt = rotorWinkel;
        rotorWinkel += rotor * 0.9F;
        if (rotorWinkel > Mth.TWO_PI * 100) {
            rotorWinkel -= Mth.TWO_PI * 100;
            rotorWinkelAlt -= Mth.TWO_PI * 100;
        }
        // Neigen beim Beschleunigen, Rollen in der Kurve (aus der tatsächlichen Bewegung)
        double dx = getX() - xo, dz = getZ() - zo;
        double vor = dx * -Mth.sin(getYRot() * Mth.DEG_TO_RAD) + dz * Mth.cos(getYRot() * Mth.DEG_TO_RAD);
        float sollNeigung = (float) Mth.clamp(vor / hoechstTempo() * 14.0, -10.0, 14.0);
        float sollRollen = Mth.clamp(-Mth.wrapDegrees(getYRot() - yRotO) * 4.0F, -18.0F, 18.0F);
        neigung += (sollNeigung - neigung) * 0.15F;
        rollen += (sollRollen - rollen) * 0.15F;
        // Kein Fallschaden für Hubschrauber und Insassen
        resetFallDistance();
        for (Entity e : getPassengers()) {
            e.resetFallDistance();
        }
    }
}
