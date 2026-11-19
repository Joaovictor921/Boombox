package com.boombox.bar;

import com.boombox.BoomboxMod;
import com.boombox.ModBlocks;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.Set;
import java.util.function.Function;

/** Coisas do boteco e da festa (3.1): porções, fone, pulseira VIP, cadeiras, mesa, lousa, TV, sinuca, pista e confete. */
public final class BarBlocks {
    private BarBlocks() {}

    private static Item item(String name, Function<Item.Properties, Item> make, Item.Properties props) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, BoomboxMod.id(name));
        Item it = Registry.register(BuiltInRegistries.ITEM, key, make.apply(props.setId(key)));
        ModBlocks.ALL_ITEMS.add(it);
        return it;
    }

    private static Block block(String name, Function<BlockBehaviour.Properties, Block> make, BlockBehaviour.Properties props) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, BoomboxMod.id(name));
        Block b = Registry.register(BuiltInRegistries.BLOCK, key, make.apply(props.setId(key)));
        ResourceKey<Item> ik = ResourceKey.create(Registries.ITEM, BoomboxMod.id(name));
        ModBlocks.ALL_ITEMS.add(Registry.register(BuiltInRegistries.ITEM, ik,
                new BlockItem(b, new Item.Properties().setId(ik).useBlockDescriptionPrefix())));
        return b;
    }

    private static <T extends net.minecraft.world.level.block.entity.BlockEntity> BlockEntityType<T> be(
            String name, BlockEntityType.BlockEntitySupplier<T> make, Block... blocks) {
        return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, BoomboxMod.id(name), new BlockEntityType<>(make, Set.of(blocks)));
    }

    private static Consumable eat(MobEffectInstance effect) {
        return Consumable.builder().onConsume(new ApplyStatusEffectsConsumeEffect(effect)).build();
    }

    // ---------------- porções e bebida
    public static final Item TORRESMO = item("torresmo", Item::new, new Item.Properties()
            .food(new FoodProperties(5, 0.6f, false), eat(new MobEffectInstance(MobEffects.SPEED, 20 * 20, 0))));
    public static final Item COXINHA = item("coxinha", Item::new, new Item.Properties()
            .food(new FoodProperties(6, 0.8f, false), eat(new MobEffectInstance(MobEffects.HASTE, 20 * 30, 0))));
    public static final Item QUENTAO = item("quentao", Item::new, new Item.Properties().stacksTo(16)
            .food(new FoodProperties(2, 0.3f, true), Consumable.builder().consumeSeconds(1.6f)
                    .animation(ItemUseAnimation.DRINK).sound(SoundEvents.GENERIC_DRINK).hasConsumeParticles(false)
                    .onConsume(new ApplyStatusEffectsConsumeEffect(java.util.List.of(
                            new MobEffectInstance(MobEffects.REGENERATION, 20 * 8, 0),
                            new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 30, 0)))).build())
            .usingConvertsTo(Items.GLASS_BOTTLE));

    // ---------------- pulseira VIP e fone
    public static final Item VIP = item("vip_bracelet", Item::new, new Item.Properties().stacksTo(1));
    public static final ResourceKey<EquipmentAsset> HEADPHONES_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, BoomboxMod.id("headphones"));
    public static final Item HEADPHONES = item("headphones", Item::new, new Item.Properties().stacksTo(1)
            .component(net.minecraft.core.component.DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.HEAD)
                    .setAsset(HEADPHONES_ASSET).setEquipSound(SoundEvents.ARMOR_EQUIP_LEATHER).build()));

    // ---------------- móveis de plástico, lousa, TV e sinuca
    private static BlockBehaviour.Properties plastic() {
        return BlockBehaviour.Properties.of().strength(0.6f).sound(SoundType.STONE).noOcclusion();
    }

    public static final Block CHAIR_YELLOW = block("plastic_chair_yellow", PlasticChairBlock::new, plastic());
    public static final Block CHAIR_RED = block("plastic_chair_red", PlasticChairBlock::new, plastic());
    public static final Block CHAIR_BLUE = block("plastic_chair_blue", PlasticChairBlock::new, plastic());
    public static final Block TABLE = block("plastic_table", PlasticTableBlock::new, plastic());
    public static final BlockEntityType<PlasticTableBlockEntity> TABLE_BE = be("plastic_table", PlasticTableBlockEntity::new, TABLE);
    public static final Block LOUSA = block("fiado_board", LousaBlock::new, BlockBehaviour.Properties.of().strength(0.5f).sound(SoundType.WOOD).noOcclusion().noCollision());
    public static final BlockEntityType<LousaBlockEntity> LOUSA_BE = be("fiado_board", LousaBlockEntity::new, LOUSA);
    public static final Block TV = block("old_tv", TvBlock::new, BlockBehaviour.Properties.of().strength(0.8f).sound(SoundType.METAL).noOcclusion());
    public static final BlockEntityType<TvBlockEntity> TV_BE = be("old_tv", TvBlockEntity::new, TV);
    public static final Block SINUCA = block("pool_table", SinucaBlock::new, BlockBehaviour.Properties.of().strength(1.5f).sound(SoundType.WOOD).noOcclusion());
    public static final BlockEntityType<SinucaBlockEntity> SINUCA_BE = be("pool_table", SinucaBlockEntity::new, SINUCA);

    // ---------------- festa: pista que acende e canhão de confete
    public static final Block DANCE_FLOOR = block("dance_floor", DanceFloorBlock::new,
            BlockBehaviour.Properties.of().strength(1.0f).sound(SoundType.GLASS).lightLevel(s -> 6));
    public static final BlockEntityType<DanceFloorBlockEntity> DANCE_FLOOR_BE = be("dance_floor", DanceFloorBlockEntity::new, DANCE_FLOOR);
    public static final Block CONFETTI = block("confetti_cannon", ConfettiCannonBlock::new,
            BlockBehaviour.Properties.of().strength(1.0f).sound(SoundType.METAL).noOcclusion());
    public static final BlockEntityType<ConfettiCannonBlockEntity> CONFETTI_BE = be("confetti_cannon", ConfettiCannonBlockEntity::new, CONFETTI);

    // ---------------- 3.2: churrasco, praia, pagode, camarote e bolhas
    public static final Item ESPETINHO = item("espetinho", Item::new, new Item.Properties()
            .food(new FoodProperties(7, 0.9f, false), eat(new MobEffectInstance(MobEffects.STRENGTH, 20 * 20, 0))));
    public static final Item LINGUICA = item("linguica", Item::new, new Item.Properties()
            .food(new FoodProperties(6, 0.8f, false), eat(new MobEffectInstance(MobEffects.SPEED, 20 * 15, 0))));
    public static final Item PAO_DE_ALHO = item("pao_de_alho", Item::new, new Item.Properties()
            .food(new FoodProperties(5, 0.7f, false), eat(new MobEffectInstance(MobEffects.REGENERATION, 20 * 6, 0))));
    public static final Item AGUA_DE_COCO = item("agua_de_coco", Item::new, new Item.Properties().stacksTo(16)
            .food(new FoodProperties(3, 0.4f, true), Consumable.builder().consumeSeconds(1.6f)
                    .animation(ItemUseAnimation.DRINK).sound(SoundEvents.GENERIC_DRINK).hasConsumeParticles(false)
                    .onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 20 * 60, 0))).build()));
    public static final Item PANDEIRO = item("pandeiro", p -> new InstrumentItem(p, SoundEvents.NOTE_BLOCK_SNARE), new Item.Properties().stacksTo(1));
    public static final Item CAVAQUINHO = item("cavaquinho", p -> new InstrumentItem(p, SoundEvents.NOTE_BLOCK_BANJO), new Item.Properties().stacksTo(1));

    public static final Block GRILL = block("grill", GrillBlock::new, BlockBehaviour.Properties.of().strength(1.5f).sound(SoundType.METAL).noOcclusion()
            .lightLevel(s -> 4));
    public static final BlockEntityType<GrillBlockEntity> GRILL_BE = be("grill", GrillBlockEntity::new, GRILL);
    public static final Block SOFA = block("vip_sofa", p -> new SittableBlock(p,
            net.minecraft.world.phys.shapes.Shapes.or(Block.box(0, 0, 0, 16, 7, 16), Block.box(0, 7, 13, 16, 16, 16)), 0.4),
            BlockBehaviour.Properties.of().strength(0.8f).sound(SoundType.WOOL).noOcclusion());
    public static final Block ICE_BUCKET = block("ice_bucket", IceBucketBlock::new, BlockBehaviour.Properties.of().strength(0.5f).sound(SoundType.METAL).noOcclusion());
    public static final Block BUBBLE_MACHINE = block("bubble_machine", BubbleMachineBlock::new,
            BlockBehaviour.Properties.of().strength(0.8f).sound(SoundType.METAL).noOcclusion());
    public static final BlockEntityType<BubbleMachineBlockEntity> BUBBLE_BE = be("bubble_machine", BubbleMachineBlockEntity::new, BUBBLE_MACHINE);
    public static final Block BEACH_CHAIR = block("beach_chair", p -> new SittableBlock(p, Block.box(1, 0, 1, 15, 6, 15), 0.25),
            BlockBehaviour.Properties.of().strength(0.5f).sound(SoundType.WOOD).noOcclusion());
    public static final Block PARASOL = block("parasol", p -> new Block(p) {
                @Override
                protected net.minecraft.world.phys.shapes.VoxelShape getShape(net.minecraft.world.level.block.state.BlockState s,
                        net.minecraft.world.level.BlockGetter l, net.minecraft.core.BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext c) {
                    return Block.box(7, 0, 7, 9, 16, 9);
                }
            }, BlockBehaviour.Properties.of().strength(0.5f).sound(SoundType.WOOL).noOcclusion());

    public static void init() {
        // força o carregamento estático
    }
}
