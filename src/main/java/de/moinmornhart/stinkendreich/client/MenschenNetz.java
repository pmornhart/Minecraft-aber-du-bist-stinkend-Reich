package de.moinmornhart.stinkendreich.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Menschengestalt im Layout einer Spieler-Haut (64 × 64, zwei Schichten), wie sie auch Schaufensterpuppen, Standbilder
 * nutzen (tools/assets/spielerhaut.py): linker Arm und linkes Bein haben eigene Flächen, die
 * zweite Schicht (Jacke, Ärmel, Hosenbeine) liegt 0,25 Pixel über dem Körper, die Haar-Schicht (Hut) 0,5 Pixel. Kopf und
 * Hut kommen aus {@link HumanoidModel#createMesh}, damit Minecraft sie in jeder Version wie gewohnt bewegt.
 */
public final class MenschenNetz {
    private MenschenNetz() {
    }

    /** {@code schmal}: Arme mit 3 statt 4 Pixeln (wie der Spieler-Skin „Alex“). */
    public static MeshDefinition erstellen(boolean schmal) {
        MeshDefinition netz = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        PartDefinition wurzel = netz.getRoot();
        CubeDeformation ueber = new CubeDeformation(0.25F);
        float b = schmal ? 3.0F : 4.0F;
        float armY = schmal ? 2.5F : 2.0F;
        wurzel.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(16, 16).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F)
                .texOffs(16, 32).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, ueber), PartPose.ZERO);
        wurzel.addOrReplaceChild("right_arm", CubeListBuilder.create()
                .texOffs(40, 16).addBox(1.0F - b, -2.0F, -2.0F, b, 12.0F, 4.0F)
                .texOffs(40, 32).addBox(1.0F - b, -2.0F, -2.0F, b, 12.0F, 4.0F, ueber), PartPose.offset(-5.0F, armY, 0.0F));
        wurzel.addOrReplaceChild("left_arm", CubeListBuilder.create()
                .texOffs(32, 48).addBox(-1.0F, -2.0F, -2.0F, b, 12.0F, 4.0F)
                .texOffs(48, 48).addBox(-1.0F, -2.0F, -2.0F, b, 12.0F, 4.0F, ueber), PartPose.offset(5.0F, armY, 0.0F));
        wurzel.addOrReplaceChild("right_leg", CubeListBuilder.create()
                .texOffs(0, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F)
                .texOffs(0, 32).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, ueber), PartPose.offset(-1.9F, 12.0F, 0.0F));
        wurzel.addOrReplaceChild("left_leg", CubeListBuilder.create()
                .texOffs(16, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F)
                .texOffs(0, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, ueber), PartPose.offset(1.9F, 12.0F, 0.0F));
        return netz;
    }
}
