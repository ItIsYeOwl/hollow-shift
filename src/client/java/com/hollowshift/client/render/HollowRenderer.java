package com.hollowshift.client.render;

import com.hollowshift.HollowShift;
import com.hollowshift.entity.HollowEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** No shadow. It doesn't cast one. */
public class HollowRenderer extends HumanoidMobRenderer<HollowEntity, HollowModel> {

    private static final ResourceLocation TEXTURE = HollowShift.id("textures/entity/hollow.png");

    public HollowRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HollowModel(ctx.bakeLayer(HollowModel.LAYER)), 0.0f);
    }

    @Override
    public ResourceLocation getTextureLocation(HollowEntity entity) {
        return TEXTURE;
    }
}
