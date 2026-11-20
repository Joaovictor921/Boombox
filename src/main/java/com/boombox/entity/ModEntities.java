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

    private static final ResourceKey<EntityType<?>> RAVER_KEY = ResourceKey.create(Registries.ENTITY_TYPE, BoomboxMod.id("raver"));
    public static final EntityType<Raver> RAVER = Registry.register(BuiltInRegistries.ENTITY_TYPE, RAVER_KEY,
            EntityType.Builder.of(Raver::new, MobCategory.MISC).sized(0.6f, 1.95f).eyeHeight(1.62f).build(RAVER_KEY));
    private static final ResourceKey<Item> RAVER_EGG_KEY = ResourceKey.create(Registries.ITEM, BoomboxMod.id("raver_spawn_egg"));
    public static final Item RAVER_SPAWN_EGG = Registry.register(BuiltInRegistries.ITEM, RAVER_EGG_KEY,
            new SpawnEggItem(new Item.Properties().setId(RAVER_EGG_KEY).spawnEgg(RAVER)));

    private static final ResourceKey<EntityType<?>> SEU_ZE_KEY = ResourceKey.create(Registries.ENTITY_TYPE, BoomboxMod.id("seu_ze"));
    public static final EntityType<SeuZe> SEU_ZE = Registry.register(BuiltInRegistries.ENTITY_TYPE, SEU_ZE_KEY,
            EntityType.Builder.of(SeuZe::new, MobCategory.MISC).sized(0.6f, 1.95f).eyeHeight(1.62f).build(SEU_ZE_KEY));
    private static final ResourceKey<Item> SEU_ZE_EGG_KEY = ResourceKey.create(Registries.ITEM, BoomboxMod.id("seu_ze_spawn_egg"));
    public static final Item SEU_ZE_SPAWN_EGG = Registry.register(BuiltInRegistries.ITEM, SEU_ZE_EGG_KEY,
            new SpawnEggItem(new Item.Properties().setId(SEU_ZE_EGG_KEY).spawnEgg(SEU_ZE)));

    private static final ResourceKey<EntityType<?>> MOUNTED_KEY = ResourceKey.create(Registries.ENTITY_TYPE, BoomboxMod.id("mounted_speaker"));
    public static final EntityType<MountedSpeaker> MOUNTED_SPEAKER = Registry.register(BuiltInRegistries.ENTITY_TYPE, MOUNTED_KEY,
            EntityType.Builder.<MountedSpeaker>of(MountedSpeaker::new, MobCategory.MISC).sized(0.8f, 1.2f).clientTrackingRange(10).build(MOUNTED_KEY));

    private static <T extends net.minecraft.world.entity.Entity> EntityType<T> reg(String name, EntityType.Builder<T> b) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, BoomboxMod.id(name));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, b.build(key));
    }

    public static final EntityType<Seat> SEAT = reg("seat", EntityType.Builder.<Seat>of(Seat::new, MobCategory.MISC).sized(0.2f, 0.2f));
    public static final EntityType<Bouncer> BOUNCER = reg("bouncer",
            EntityType.Builder.<Bouncer>of(Bouncer::new, MobCategory.MISC).sized(0.6f, 1.95f).eyeHeight(1.62f));
    public static final EntityType<Ball> BALL = reg("ball", EntityType.Builder.<Ball>of(Ball::new, MobCategory.MISC).sized(0.5f, 0.5f).clientTrackingRange(8).updateInterval(1));
    public static final EntityType<Balloon> BALLOON = reg("balloon", EntityType.Builder.<Balloon>of(Balloon::new, MobCategory.MISC).sized(0.6f, 2.2f).clientTrackingRange(8));
    public static final EntityType<Volleyball> VOLLEYBALL = reg("volleyball", EntityType.Builder.<Volleyball>of(Volleyball::new, MobCategory.MISC)
            .sized(0.5f, 0.5f).clientTrackingRange(8).updateInterval(1));
    public static final EntityType<PoolFloat> POOL_FLOAT = reg("pool_float", EntityType.Builder.<PoolFloat>of(PoolFloat::new, MobCategory.MISC)
            .sized(1.3f, 0.45f).clientTrackingRange(8));
    public static final EntityType<Trio> TRIO = reg("trio", EntityType.Builder.<Trio>of(Trio::new, MobCategory.MISC).sized(2.2f, 1.6f));
    private static final ResourceKey<Item> BOUNCER_EGG_KEY = ResourceKey.create(Registries.ITEM, BoomboxMod.id("bouncer_spawn_egg"));
    public static final Item BOUNCER_SPAWN_EGG = Registry.register(BuiltInRegistries.ITEM, BOUNCER_EGG_KEY,
            new SpawnEggItem(new Item.Properties().setId(BOUNCER_EGG_KEY).spawnEgg(BOUNCER)));

    private ModEntities() {}

    public static void init() {
        FabricDefaultAttributeRegistry.register(DJ_LEO, DjLeo.createAttributes());
        FabricDefaultAttributeRegistry.register(RAVER, Raver.createAttributes());
        ModBlocks.ALL_ITEMS.add(DJ_LEO_SPAWN_EGG);
        ModBlocks.ALL_ITEMS.add(RAVER_SPAWN_EGG);
        FabricDefaultAttributeRegistry.register(SEU_ZE, SeuZe.createAttributes());
        ModBlocks.ALL_ITEMS.add(SEU_ZE_SPAWN_EGG);
        ModBlocks.ALL_ITEMS.add(ModBlocks.BEER);
        FabricDefaultAttributeRegistry.register(BOUNCER, Bouncer.createAttributes());
        FabricDefaultAttributeRegistry.register(TRIO, Trio.createAttributes());
        ModBlocks.ALL_ITEMS.add(BOUNCER_SPAWN_EGG);
    }
}
