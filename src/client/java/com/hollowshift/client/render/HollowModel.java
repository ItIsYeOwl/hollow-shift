package com.hollowshift.client.render;

import com.hollowshift.HollowShift;
import com.hollowshift.entity.HollowEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * A player-shaped figure, stretched: taller body, longer legs, and arms that hang
 * past its knees. 2.5 blocks tall. Uses the humanoid walk animation, which looks
 * wrong in exactly the right way on these proportions.
 */
public class HollowModel extends HumanoidModel<HollowEntity> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(HollowShift.id("hollow"), "main");

    public HollowModel(ModelPart root) {
        super(root);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // Everything is raised 8px compared to a player so the longer legs still meet the ground.
        root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4, -8, -4, 8, 8, 8), PartPose.offset(0, -8, 0));
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.offset(0, -8, 0));
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(16, 16)
                .addBox(-4, 0, -2, 8, 16, 4), PartPose.offset(0, -8, 0));
        root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(40, 16)
                .addBox(-2, -2, -1.5f, 3, 22, 3), PartPose.offset(-5, -6, 0));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(40, 16).mirror()
                .addBox(-1, -2, -1.5f, 3, 22, 3), PartPose.offset(5, -6, 0));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 16)
                .addBox(-2, 0, -2, 4, 16, 4), PartPose.offset(-1.9f, 8, 0));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(0, 16).mirror()
                .addBox(-2, 0, -2, 4, 16, 4), PartPose.offset(1.9f, 8, 0));
        return LayerDefinition.create(mesh, 64, 64);
    }
}
