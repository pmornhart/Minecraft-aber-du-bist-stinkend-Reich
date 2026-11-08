package de.moinmornhart.stinkendreich.butler.auftrag;

import com.google.gson.JsonObject;
import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.Konfig;
import de.moinmornhart.stinkendreich.butler.ButlerEntity;
import de.moinmornhart.stinkendreich.butler.Schmelzen;
import de.moinmornhart.stinkendreich.butler.Schutz;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Jagt Nutztiere (Kuh, Schwein, Schaf, Huhn, Kaninchen), bis genug rohes Fleisch beisammen ist.
 * Geschont werden: Jungtiere, Tiere mit Namen oder an der Leine, Tiere auf dem Villa-Grundstück,
 * und von jeder Art bleiben in der Gegend immer mindestens zwei übrig.
 */
public class JagdAuftrag extends Auftrag {
    public static final String TYP = "jagd";
    // Die Liste der Arten steht seit 26.2 in EntityTypes
    //? if >=26.2 {
    private static final Set<EntityType<?>> BEUTE = Set.of(net.minecraft.world.entity.EntityTypes.COW,
            net.minecraft.world.entity.EntityTypes.PIG, net.minecraft.world.entity.EntityTypes.SHEEP,
            net.minecraft.world.entity.EntityTypes.CHICKEN, net.minecraft.world.entity.EntityTypes.RABBIT);
    //?} else
    /*private static final Set<EntityType<?>> BEUTE = Set.of(EntityType.COW, EntityType.PIG, EntityType.SHEEP, EntityType.CHICKEN, EntityType.RABBIT);*/

    private enum Phase { RAUS, SUCHEN, JAGEN, EINSAMMELN }

    private final int menge;
    private int gesammelt;

    private Phase phase = Phase.RAUS;
    private LivingEntity beute;
    private Vec3 fundort;
    private int phasenTicks;
    private int fehlversuche;

    public JagdAuftrag(int menge) {
        this.menge = menge;
    }

    @Override
    protected Stand tick(ButlerEntity butler) {
        if (!Konfig.get().butlerJagen) {
            return fehlschlag(Component.translatable("stinkendreich.butler.jagenaus"));
        }
        if (gesammelt >= menge) {
            return Stand.FERTIG;
        }
        // Taschen voll: Weiterjagen hätte keinen Sinn, die Beute könnte er nicht mehr aufheben
        if (butler.taschen().freiePlaetze() == 0) {
            butler.sagen(Component.translatable("stinkendreich.butler.voll"));
            return Stand.FERTIG;
        }
        ServerLevel welt = butler.welt();
        phasenTicks++;
        switch (phase) {
            case RAUS -> {
                if (Ausgang.hinaus(butler) != Stand.LAEUFT) {
                    phase = Phase.SUCHEN;
                }
            }
            case SUCHEN -> {
                beute = naechsteBeute(butler);
                if (beute == null) {
                    if (gesammelt > 0) {
                        butler.sagen(Component.translatable("stinkendreich.butler.nur", gesammelt, Items.BEEF.getDefaultInstance().getHoverName()));
                        return Stand.FERTIG;
                    }
                    return fehlschlag(Component.translatable("stinkendreich.butler.keintier"));
                }
                phase = Phase.JAGEN;
                phasenTicks = 0;
                butler.werkzeug(Items.IRON_SWORD);
            }
            case JAGEN -> {
                if (beute == null || !beute.isAlive()) {
                    phase = Phase.EINSAMMELN;
                    phasenTicks = 0;
                    return Stand.LAEUFT;
                }
                fundort = beute.position();
                butler.getLookControl().setLookAt(beute, 30.0F, 30.0F);
                if (butler.distanceToSqr(beute) < 2.4 * 2.4) {
                    if (phasenTicks % 12 == 0) {
                        Kompat.schwingen(butler);
                        Kompat.angreifen(butler, beute, welt, 6.0F);
                    }
                } else if (butler.gehZu(beute, 1.6) == ButlerEntity.Weg.UNERREICHBAR || phasenTicks > 20 * 60) {
                    beute = null;
                    phase = Phase.SUCHEN;
                    if (++fehlversuche > 6) {
                        return fehlschlag(Component.translatable("stinkendreich.butler.keintier"));
                    }
                }
            }
            case EINSAMMELN -> {
                // Kurz warten, bis die Beute fällt, dann alles in der Nähe aufheben
                if (phasenTicks < 15 || fundort == null) {
                    return Stand.LAEUFT;
                }
                List<ItemEntity> liegt = welt.getEntitiesOfClass(ItemEntity.class, new AABB(fundort, fundort).inflate(4), e -> !e.getItem().isEmpty());
                if (liegt.isEmpty() || phasenTicks > 20 * 15) {
                    phase = Phase.SUCHEN;
                    return Stand.LAEUFT;
                }
                ItemEntity naechstes = liegt.stream().min(Comparator.comparingDouble(butler::distanceToSqr)).get();
                if (butler.distanceToSqr(naechstes) < 1.8 * 1.8 || butler.gehZu(naechstes, 1.2) == ButlerEntity.Weg.UNERREICHBAR) {
                    ItemStack s = naechstes.getItem();
                    if (Schmelzen.istRohesEssen(s)) {
                        gesammelt += s.getCount();
                    }
                    ItemStack rest = butler.taschen().einpacken(s);
                    if (rest.isEmpty()) {
                        naechstes.discard();
                    } else {
                        naechstes.setItem(rest);
                    }
                }
            }
        }
        return Stand.LAEUFT;
    }

    private static LivingEntity naechsteBeute(ButlerEntity butler) {
        ServerLevel welt = butler.welt();
        int r = Konfig.get().butlerSuchradius;
        AABB bereich = butler.getBoundingBox().inflate(r, 16, r);
        List<LivingEntity> tiere = welt.getEntitiesOfClass(LivingEntity.class, bereich, e -> BEUTE.contains(e.getType()) && e.isAlive());
        return tiere.stream()
                .filter(t -> !t.isBaby() && !t.hasCustomName() && !(t instanceof Leashable l && l.isLeashed())
                        && !Schutz.inDerVilla(welt, t.blockPosition()))
                .filter(t -> artgenossen(tiere, t) >= 3)
                .min(Comparator.comparingDouble(butler::distanceToSqr))
                .orElse(null);
    }

    /** Wie viele Tiere derselben Art in der Nähe sind (einschließlich des Tiers selbst). */
    private static long artgenossen(List<LivingEntity> tiere, Entity tier) {
        return tiere.stream().filter(t -> t.getType() == tier.getType() && t.distanceToSqr(tier) < 24 * 24).count();
    }

    @Override
    public Component status() {
        return Component.translatable("stinkendreich.butler.status.jagd", gesammelt, menge);
    }

    @Override
    public String typ() {
        return TYP;
    }

    @Override
    public void schreiben(JsonObject j) {
        j.addProperty("menge", menge);
        j.addProperty("gesammelt", gesammelt);
    }

    static JagdAuftrag ausJson(JsonObject j) {
        JagdAuftrag a = new JagdAuftrag(zahl(j, "menge", 8));
        a.gesammelt = zahl(j, "gesammelt", 0);
        return a;
    }
}
