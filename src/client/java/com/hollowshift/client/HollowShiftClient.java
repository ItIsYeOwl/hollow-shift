package com.hollowshift.client;

import com.hollowshift.HollowShift;
import com.hollowshift.client.render.HollowModel;
import com.hollowshift.client.render.HollowRenderer;
import com.hollowshift.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

public final class HollowShiftClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        EntityModelLayerRegistry.registerModelLayer(HollowModel.LAYER, HollowModel::createBodyLayer);
        EntityRendererRegistry.register(ModEntities.HOLLOW, HollowRenderer::new);

        ClientState.register();
        CiCapture.register();

        // The atmosphere pack: muted foliage, unsteady torches, replaced cave sounds, off-key animals.
        // On by default; players can switch it off in Options > Resource Packs.
        FabricLoader.getInstance().getModContainer(HollowShift.MOD_ID).ifPresent(mod ->
                ResourceManagerHelper.registerBuiltinResourcePack(HollowShift.id("hollow_atmosphere"), mod,
                        Component.literal("Hollow Shift: Atmosphere"), ResourcePackActivationType.DEFAULT_ENABLED));

        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public ResourceLocation getFabricId() {
                        return HollowShift.id("tension_shader");
                    }

                    @Override
                    public void onResourceManagerReload(ResourceManager manager) {
                        TensionPostEffect.reset();
                    }
                });
    }
}
