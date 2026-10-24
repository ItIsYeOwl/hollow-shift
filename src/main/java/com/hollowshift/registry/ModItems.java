package com.hollowshift.registry;

import com.hollowshift.HollowShift;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

public final class ModItems {

    /** What a torch becomes when the Hollow touches your inventory. Useless, on purpose. */
    public static final Item BURNT_TORCH = Registry.register(BuiltInRegistries.ITEM,
            HollowShift.id("burnt_torch"), new Item(new Item.Properties()));

    private ModItems() {}

    public static void register() {}
}
