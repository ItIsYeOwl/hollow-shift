package com.hollowshift.registry;

import com.hollowshift.HollowShift;
import com.hollowshift.entity.HollowEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {

    /** Never spawns naturally. Only the Director summons it. */
    public static final EntityType<HollowEntity> HOLLOW = Registry.register(BuiltInRegistries.ENTITY_TYPE,
            HollowShift.id("hollow"),
            EntityType.Builder.of(HollowEntity::new, MobCategory.MONSTER)
                    .sized(0.6f, 2.45f)
                    .eyeHeight(2.2f)
                    .clientTrackingRange(16) // in chunks: visible from far away, on purpose
                    .build("hollow"));

    private ModEntities() {}

    public static void register() {
        FabricDefaultAttributeRegistry.register(HOLLOW, HollowEntity.createAttributes());
    }
}
