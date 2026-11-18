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

    /** Luz, cor, dono e Modo DJ guardados no item / bloco. */
    public static final DataComponentType<SpeakerSettings> SETTINGS = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            BoomboxMod.id("settings"),
            DataComponentType.<SpeakerSettings>builder()
                    .persistent(SpeakerSettings.CODEC)
                    .networkSynchronized(SpeakerSettings.STREAM_CODEC)
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

    // ---- LED de rave
    private static final ResourceKey<Block> RAVE_LED_KEY = ResourceKey.create(Registries.BLOCK, BoomboxMod.id("rave_led"));
    public static final Block RAVE_LED = Registry.register(BuiltInRegistries.BLOCK, RAVE_LED_KEY,
            new RaveLedBlock(BlockBehaviour.Properties.of()
                    .setId(RAVE_LED_KEY)
                    .strength(0.3f)
                    .sound(SoundType.GLASS)
                    .lightLevel(s -> 8) // meia-luz: o ambiente fica escuro e os feixes aparecem
                    .noCollision()
                    .noOcclusion()));
    public static final Item RAVE_LED_ITEM = Registry.register(BuiltInRegistries.ITEM,
            ResourceKey.create(Registries.ITEM, BoomboxMod.id("rave_led")),
            new net.minecraft.world.item.BlockItem(RAVE_LED, new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, BoomboxMod.id("rave_led"))).useBlockDescriptionPrefix()));
    public static final BlockEntityType<RaveLedBlockEntity> RAVE_LED_BE = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, BoomboxMod.id("rave_led"),
            new BlockEntityType<>(RaveLedBlockEntity::new, java.util.Set.of(RAVE_LED)));

    static {
        ALL_ITEMS.add(RAVE_LED_ITEM);
    }

    private ModBlocks() {}

    public static void init() {
        // força o carregamento estático
    }
}
