package com.boombox.entity;

import com.boombox.BoomboxMod;
import com.boombox.ModBlocks;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

public final class ModEntities {
    private static final ResourceKey<EntityType<?>> DJ_LEO_KEY = ResourceKey.create(Registries.ENTITY_TYPE, BoomboxMod.id("dj_leo"));
    public static final EntityType<DjLeo> DJ_LEO = Registry.register(BuiltInRegistries.ENTITY_TYPE, DJ_LEO_KEY,
            EntityType.Builder.of(DjLeo::new, MobCategory.MISC).sized(0.6f, 1.95f).eyeHeight(1.62f).build(DJ_LEO_KEY));

    private static final ResourceKey<Item> EGG_KEY = ResourceKey.create(Registries.ITEM, BoomboxMod.id("dj_leo_spawn_egg"));
    public static final Item DJ_LEO_SPAWN_EGG = Registry.register(BuiltInRegistries.ITEM, EGG_KEY,
            new SpawnEggItem(new Item.Properties().setId(EGG_KEY).spawnEgg(DJ_LEO)));

    private ModEntities() {}

    public static void init() {
        FabricDefaultAttributeRegistry.register(DJ_LEO, DjLeo.createAttributes());
        ModBlocks.ALL_ITEMS.add(DJ_LEO_SPAWN_EGG);
    }
}
