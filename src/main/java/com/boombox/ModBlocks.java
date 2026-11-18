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

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public final class ModBlocks {
    /** Música guardada no item / bloco. */
    public static final DataComponentType<SongData> SONG = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            BoomboxMod.id("song"),
            DataComponentType.<SongData>builder()
                    .persistent(SongData.CODEC)
                    .networkSynchronized(SongData.STREAM_CODEC)
                    .ignoreSwapAnimation()
                    .build());

    public static final Map<SpeakerType, Block> BLOCKS = new EnumMap<>(SpeakerType.class);
    public static final Map<SpeakerType, Item> ITEMS = new EnumMap<>(SpeakerType.class);
    /** Todos os itens do mod, na ordem da aba do criativo. */
    public static final List<Item> ALL_ITEMS = new ArrayList<>();

    static {
        for (SpeakerType type : SpeakerType.values()) {
            ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, BoomboxMod.id(type.id()));
            ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, BoomboxMod.id(type.id()));
            Block block = Registry.register(BuiltInRegistries.BLOCK, blockKey,
                    new BoomboxBlock(type, BlockBehaviour.Properties.of()
                            .setId(blockKey)
                            .strength(type == SpeakerType.PARTY_LARGE ? 1.5f : 1.0f)
                            .sound(type.isParty() ? SoundType.METAL : SoundType.WOOD)
                            .noOcclusion()));
            Item.Properties props = new Item.Properties().setId(itemKey).useBlockDescriptionPrefix().stacksTo(1);
            // todas as caixas dá para levar tocando na mão (cada uma com a sua animação)
            Item item = Registry.register(BuiltInRegistries.ITEM, itemKey, new BoomboxItem(block, props));
            BLOCKS.put(type, block);
            ITEMS.put(type, item);
            ALL_ITEMS.add(item);
        }
    }

    public static final Block BOOMBOX = BLOCKS.get(SpeakerType.BOOMBOX);
    public static final Item BOOMBOX_ITEM = ITEMS.get(SpeakerType.BOOMBOX);

    public static final BlockEntityType<BoomboxBlockEntity> BOOMBOX_BE = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, BoomboxMod.id("boombox"),
            new BlockEntityType<>(BoomboxBlockEntity::new, new HashSet<>(BLOCKS.values())));

    private ModBlocks() {}

    public static void init() {
        // força o carregamento estático
    }
}
