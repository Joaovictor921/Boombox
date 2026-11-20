package com.boombox.bar;

import com.boombox.BoomboxMod;
import com.boombox.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/** Coisas da 3.4: petiscos, neon, corda VIP, luau, piscina, vôlei e troféus. */
public final class Blocks34 {
    private Blocks34() {}

    private static Item item(String name, Function<Item.Properties, Item> make, Item.Properties props) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, BoomboxMod.id(name));
        Item it = Registry.register(BuiltInRegistries.ITEM, key, make.apply(props.setId(key)));
        ModBlocks.ALL_ITEMS.add(it);
        return it;
    }

    private static Block block(String name, Function<BlockBehaviour.Properties, Block> make, BlockBehaviour.Properties props, Item.Properties ip) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, BoomboxMod.id(name));
        Block b = Registry.register(BuiltInRegistries.BLOCK, key, make.apply(props.setId(key)));
        ResourceKey<Item> ik = ResourceKey.create(Registries.ITEM, BoomboxMod.id(name));
        ModBlocks.ALL_ITEMS.add(Registry.register(BuiltInRegistries.ITEM, ik, new BlockItem(b, ip.setId(ik).useBlockDescriptionPrefix())));
        return b;
    }

    private static Block block(String name, Function<BlockBehaviour.Properties, Block> make, BlockBehaviour.Properties props) {
        return block(name, make, props, new Item.Properties());
    }

    private static <T extends net.minecraft.world.level.block.entity.BlockEntity> BlockEntityType<T> be(
            String name, BlockEntityType.BlockEntitySupplier<T> make, Block... blocks) {
        return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, BoomboxMod.id(name), new BlockEntityType<>(make, Set.of(blocks)));
    }

    private static Item.Properties petisco(int food, float sat, MobEffectInstance effect) {
        return new Item.Properties().stacksTo(16).food(new FoodProperties(food, sat, false),
                Consumable.builder().consumeSeconds(2.0f).onConsume(new ApplyStatusEffectsConsumeEffect(effect)).build());
    }

    // ---------------- petiscos do Seu Zé (travessa que fica em cima da mesa)
    public static final Item FRITAS = item("porcao_fritas", Item::new, petisco(6, 0.6f, new MobEffectInstance(MobEffects.SPEED, 20 * 25, 0)));
    public static final Item CALABRESA = item("porcao_calabresa", Item::new, petisco(8, 0.8f, new MobEffectInstance(MobEffects.STRENGTH, 20 * 25, 0)));
    public static final Item FRANGO = item("porcao_frango", Item::new, petisco(8, 0.9f, new MobEffectInstance(MobEffects.REGENERATION, 20 * 8, 0)));

    /** É uma travessa de petisco (desenhada grande, no meio da mesa)? */
    public static boolean isPetisco(ItemStack s) {
        return s.is(FRITAS) || s.is(CALABRESA) || s.is(FRANGO);
    }

    // ---------------- área VIP
    public static final Item ESPUMANTE = item("espumante", Item::new, new Item.Properties().stacksTo(16)
            .food(new FoodProperties(2, 0.2f, true), Consumable.builder().consumeSeconds(1.6f)
                    .animation(ItemUseAnimation.DRINK).sound(SoundEvents.GENERIC_DRINK).hasConsumeParticles(false)
                    .onConsume(new ApplyStatusEffectsConsumeEffect(List.of(
                            new MobEffectInstance(MobEffects.LUCK, 20 * 120, 0),
                            new MobEffectInstance(MobEffects.JUMP_BOOST, 20 * 30, 0)))).build())
            .usingConvertsTo(Items.GLASS_BOTTLE));
    public static final Block VELVET_ROPE = block("velvet_rope", VelvetRopeBlock::new,
            BlockBehaviour.Properties.of().strength(0.8f).sound(SoundType.METAL).noOcclusion().dynamicShape());
    public static final Block NEON_SIGN = block("neon_sign", NeonSignBlock::new,
            BlockBehaviour.Properties.of().strength(0.4f).sound(SoundType.GLASS).noOcclusion().lightLevel(s -> 10));
    public static final BlockEntityType<NeonSignBlockEntity> NEON_SIGN_BE = be("neon_sign", NeonSignBlockEntity::new, NEON_SIGN);

    // ---------------- 3.5: placar do campo e da quadra
    public static final Block PLACAR = block("placar", PlacarBlock::new,
            BlockBehaviour.Properties.of().strength(1.0f).sound(SoundType.METAL).noOcclusion().lightLevel(s -> 6));
    public static final BlockEntityType<PlacarBlock.Entity> PLACAR_BE = be("placar", PlacarBlock.Entity::new, PLACAR);

    // ---------------- luau
    public static final Item VIOLAO = item("violao", p -> new InstrumentItem(p, SoundEvents.NOTE_BLOCK_GUITAR), new Item.Properties().stacksTo(1));
    public static final Block TIKI_TORCH = block("tiki_torch", TikiTorchBlock::new, BlockBehaviour.Properties.of().strength(0.4f)
            .sound(SoundType.BAMBOO).noOcclusion().noCollision().lightLevel(s -> s.getValue(TikiTorchBlock.LIT) ? 15 : 0));

    // ---------------- vôlei de praia
    public static final Block VOLLEY_NET = block("volley_net", VolleyNetBlock::new, BlockBehaviour.Properties.of().strength(0.6f)
            .sound(SoundType.WOOL).noOcclusion());

    // ---------------- troféus
    private static BlockBehaviour.Properties trophy() {
        return BlockBehaviour.Properties.of().strength(1.0f).sound(SoundType.METAL).noOcclusion();
    }

    private static Block trophyBlock(BlockBehaviour.Properties p) {
        return new FacingBlock(p) {
            @Override
            protected net.minecraft.world.phys.shapes.VoxelShape getShape(net.minecraft.world.level.block.state.BlockState s,
                    net.minecraft.world.level.BlockGetter l, BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext c) {
                return Block.box(4, 0, 4, 12, 14, 12);
            }
        };
    }

    public static final Block TROFEU_SINUCA = block("trofeu_sinuca", Blocks34::trophyBlock, trophy(), new Item.Properties().stacksTo(16)
            .rarity(net.minecraft.world.item.Rarity.EPIC));
    public static final Block TACA_VARZEA = block("taca_varzea", Blocks34::trophyBlock, trophy(), new Item.Properties().stacksTo(16)
            .rarity(net.minecraft.world.item.Rarity.EPIC));
    public static final Block TROFEU_VOLEI = block("trofeu_volei", Blocks34::trophyBlock, trophy(), new Item.Properties().stacksTo(16)
            .rarity(net.minecraft.world.item.Rarity.EPIC));

    // ---------------- itens que colocam entidades: bolas e a boia de flamingo
    /** Coloca uma entidade em cima do bloco clicado (ou na água, mirando nela). */
    public static class SpawnItem extends Item {
        private final Supplier<EntityType<? extends Entity>> type;
        private final boolean water;

        public SpawnItem(Properties p, Supplier<EntityType<? extends Entity>> type, boolean water) {
            super(p);
            this.type = type;
            this.water = water;
        }

        private boolean spawn(Level level, Vec3 at, float yaw) {
            if (!(level instanceof ServerLevel sl)) return true;
            Entity e = type.get().create(sl, EntitySpawnReason.SPAWN_ITEM_USE);
            if (e == null) return false;
            e.snapTo(at.x, at.y, at.z, yaw, 0);
            sl.addFreshEntity(e);
            return true;
        }

        @Override
        public InteractionResult useOn(UseOnContext ctx) {
            Vec3 at = Vec3.atBottomCenterOf(ctx.getClickedPos().relative(ctx.getClickedFace()));
            if (!spawn(ctx.getLevel(), at, ctx.getPlayer() == null ? 0 : ctx.getPlayer().getYRot())) return InteractionResult.FAIL;
            if (ctx.getPlayer() != null && !ctx.getPlayer().getAbilities().instabuild) ctx.getItemInHand().shrink(1);
            return InteractionResult.SUCCESS;
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (!water) return InteractionResult.PASS;
            BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
            if (hit.getType() != HitResult.Type.BLOCK || !level.getFluidState(hit.getBlockPos()).is(net.minecraft.tags.FluidTags.WATER)) {
                return InteractionResult.PASS;
            }
            Vec3 at = Vec3.atBottomCenterOf(hit.getBlockPos()).add(0, 0.9, 0);
            if (!spawn(level, at, player.getYRot())) return InteractionResult.FAIL;
            if (!player.getAbilities().instabuild) player.getItemInHand(hand).shrink(1);
            return InteractionResult.SUCCESS;
        }
    }

    public static final Item FLAMINGO = item("boia_flamingo", p -> new SpawnItem(p, () -> com.boombox.entity.ModEntities.POOL_FLOAT, true),
            new Item.Properties().stacksTo(4));
    public static final Item BOLA_VOLEI = item("bola_volei", p -> new SpawnItem(p, () -> com.boombox.entity.ModEntities.VOLLEYBALL, false),
            new Item.Properties().stacksTo(4));
    public static final Item BOLA_FUTEBOL = item("bola_futebol", p -> new SpawnItem(p, () -> com.boombox.entity.ModEntities.BALL, false),
            new Item.Properties().stacksTo(4));

    public static void init() {
        // força o carregamento estático
    }
}
