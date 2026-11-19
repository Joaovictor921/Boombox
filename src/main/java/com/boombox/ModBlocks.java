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
                    .lightLevel(s -> 10) // meia-luz: o ambiente fica escuro e os feixes aparecem
                    .noCollision()
                    .noOcclusion()));
    public static final Item RAVE_LED_ITEM = Registry.register(BuiltInRegistries.ITEM,
            ResourceKey.create(Registries.ITEM, BoomboxMod.id("rave_led")),
            new net.minecraft.world.item.BlockItem(RAVE_LED, new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, BoomboxMod.id("rave_led"))).useBlockDescriptionPrefix()));
    public static final BlockEntityType<RaveLedBlockEntity> RAVE_LED_BE = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, BoomboxMod.id("rave_led"),
            new BlockEntityType<>(RaveLedBlockEntity::new, java.util.Set.of(RAVE_LED)));

    // ---- telão
    private static final ResourceKey<Block> RAVE_SCREEN_KEY = ResourceKey.create(Registries.BLOCK, BoomboxMod.id("rave_screen"));
    public static final Block RAVE_SCREEN = Registry.register(BuiltInRegistries.BLOCK, RAVE_SCREEN_KEY,
            new RaveScreenBlock(BlockBehaviour.Properties.of()
                    .setId(RAVE_SCREEN_KEY)
                    .strength(0.8f)
                    .sound(SoundType.METAL)
                    .noOcclusion()));
    public static final Item RAVE_SCREEN_ITEM = Registry.register(BuiltInRegistries.ITEM,
            ResourceKey.create(Registries.ITEM, BoomboxMod.id("rave_screen")),
            new net.minecraft.world.item.BlockItem(RAVE_SCREEN, new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, BoomboxMod.id("rave_screen"))).useBlockDescriptionPrefix()));
    public static final BlockEntityType<RaveScreenBlockEntity> RAVE_SCREEN_BE = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, BoomboxMod.id("rave_screen"),
            new BlockEntityType<>(RaveScreenBlockEntity::new, java.util.Set.of(RAVE_SCREEN)));

    // ---- máquina de fumaça e globo espelhado
    private static final ResourceKey<Block> SMOKE_KEY = ResourceKey.create(Registries.BLOCK, BoomboxMod.id("smoke_machine"));
    public static final Block SMOKE_MACHINE = Registry.register(BuiltInRegistries.BLOCK, SMOKE_KEY,
            new SmokeMachineBlock(BlockBehaviour.Properties.of().setId(SMOKE_KEY).strength(1.0f).sound(SoundType.METAL).noOcclusion()));
    public static final Item SMOKE_MACHINE_ITEM = Registry.register(BuiltInRegistries.ITEM,
            ResourceKey.create(Registries.ITEM, BoomboxMod.id("smoke_machine")),
            new net.minecraft.world.item.BlockItem(SMOKE_MACHINE, new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, BoomboxMod.id("smoke_machine"))).useBlockDescriptionPrefix()));
    public static final BlockEntityType<SmokeMachineBlockEntity> SMOKE_MACHINE_BE = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, BoomboxMod.id("smoke_machine"),
            new BlockEntityType<>(SmokeMachineBlockEntity::new, java.util.Set.of(SMOKE_MACHINE)));

    private static final ResourceKey<Block> DISCO_KEY = ResourceKey.create(Registries.BLOCK, BoomboxMod.id("disco_ball"));
    public static final Block DISCO_BALL = Registry.register(BuiltInRegistries.BLOCK, DISCO_KEY,
            new DiscoBallBlock(BlockBehaviour.Properties.of().setId(DISCO_KEY).strength(0.5f).sound(SoundType.GLASS).noOcclusion().noCollision()));
    public static final Item DISCO_BALL_ITEM = Registry.register(BuiltInRegistries.ITEM,
            ResourceKey.create(Registries.ITEM, BoomboxMod.id("disco_ball")),
            new net.minecraft.world.item.BlockItem(DISCO_BALL, new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, BoomboxMod.id("disco_ball"))).useBlockDescriptionPrefix()));
    public static final BlockEntityType<DiscoBallBlockEntity> DISCO_BALL_BE = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, BoomboxMod.id("disco_ball"),
            new BlockEntityType<>(DiscoBallBlockEntity::new, java.util.Set.of(DISCO_BALL)));

    // ---- microfone (Simple Voice Chat)
    private static final ResourceKey<Item> MIC_KEY = ResourceKey.create(Registries.ITEM, BoomboxMod.id("microphone"));
    public static final Item MICROPHONE = Registry.register(BuiltInRegistries.ITEM, MIC_KEY,
            new MicrophoneItem(new Item.Properties().setId(MIC_KEY).stacksTo(1)));

    // ---- cerveja do Seu Zé
    private static final ResourceKey<Item> BEER_KEY = ResourceKey.create(Registries.ITEM, BoomboxMod.id("beer"));
    public static final Item BEER = Registry.register(BuiltInRegistries.ITEM, BEER_KEY, new BeerItem(new Item.Properties()
            .setId(BEER_KEY)
            .stacksTo(16)
            .food(new net.minecraft.world.food.FoodProperties(1, 0.1f, true),
                    net.minecraft.world.item.component.Consumable.builder()
                            .consumeSeconds(1.6f)
                            .animation(net.minecraft.world.item.ItemUseAnimation.DRINK)
                            .sound(net.minecraft.sounds.SoundEvents.GENERIC_DRINK)
                            .hasConsumeParticles(false)
                            .onConsume(new net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect(
                                    new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NAUSEA, 200, 0)))
                            .build())
            .usingConvertsTo(net.minecraft.world.item.Items.GLASS_BOTTLE)));

    static {
        ALL_ITEMS.add(RAVE_LED_ITEM);
        ALL_ITEMS.add(RAVE_SCREEN_ITEM);
        ALL_ITEMS.add(SMOKE_MACHINE_ITEM);
        ALL_ITEMS.add(DISCO_BALL_ITEM);
        ALL_ITEMS.add(MICROPHONE);
    }

    private ModBlocks() {}

    public static void init() {
        // força o carregamento estático
    }
}
