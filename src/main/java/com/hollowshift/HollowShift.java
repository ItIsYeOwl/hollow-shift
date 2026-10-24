package com.hollowshift;

import com.hollowshift.core.DirectorHook;
import com.hollowshift.core.FakeMobs;
import com.hollowshift.core.HollowCommands;
import com.hollowshift.core.TensionSystem;
import com.hollowshift.net.ModPayloads;
import com.hollowshift.registry.ModEntities;
import com.hollowshift.registry.ModItems;
import com.hollowshift.registry.ModSounds;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HollowShift implements ModInitializer {

    public static final String MOD_ID = "hollowshift";
    public static final Logger LOG = LoggerFactory.getLogger("The Hollow Shift");

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        HollowConfig.get();
        ModSounds.register();
        ModItems.register();
        ModEntities.register();
        ModPayloads.register();
        TensionSystem.register();
        DirectorHook.register();
        FakeMobs.register();
        HollowCommands.register();
        LOG.info("The world has begun to shift.");
    }
}
