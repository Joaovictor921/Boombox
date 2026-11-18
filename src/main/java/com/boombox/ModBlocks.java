package com.boombox;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.Set;

public final class ModBlocks {
    private static final ResourceKey<Block> BLOCK_KEY = ResourceKey.create(Registries.BLOCK, BoomboxMod.id("boombox"));
    private static final ResourceKey<Item> ITEM_KEY = ResourceKey.create(Registries.ITEM, BoomboxMod.id("boombox"));

    /** Música guardada no item / bloco. */
    public static final DataComponentType<SongData> SONG = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            BoomboxMod.id("song"),
            DataComponentType.<SongData>builder()
                    .persistent(SongData.CODEC)
                    .networkSynchronized(SongData.STREAM_CODEC)
                    .ignoreSwapAnimation()
                    .build());

    public static final Block BOOMBOX = Registry.register(BuiltInRegistries.BLOCK, BLOCK_KEY,
            new BoomboxBlock(BlockBehaviour.Properties.of()
                    .setId(BLOCK_KEY)
                    .strength(1.0f)
                    .sound(SoundType.WOOD)
                    .noOcclusion()));

    public static final Item BOOMBOX_ITEM = Registry.register(BuiltInRegistries.ITEM, ITEM_KEY,
            new BoomboxItem(BOOMBOX, new Item.Properties().setId(ITEM_KEY).useBlockDescriptionPrefix().stacksTo(1)));

    public static final BlockEntityType<BoomboxBlockEntity> BOOMBOX_BE = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, BoomboxMod.id("boombox"),
            new BlockEntityType<>(BoomboxBlockEntity::new, Set.of(BOOMBOX)));

    private ModBlocks() {}

    public static void init() {
        // força o carregamento estático
    }
}
