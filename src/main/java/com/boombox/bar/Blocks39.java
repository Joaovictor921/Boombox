package com.boombox.bar;

import com.boombox.BoomboxMod;
import com.boombox.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.Function;

/** Coisas da 3.9: medalha de craque da partida e o chinelo (trave da pelada na rua). */
public final class Blocks39 {
    private Blocks39() {}

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

    /** Chinelo no chão: baixinho, sem colisão (a bola e as crianças passam por cima). */
    public static class ChineloBlock extends FacingBlock {
        private static final VoxelShape SHAPE = Block.box(3, 0, 1, 13, 2, 15);

        public ChineloBlock(Properties p) {
            super(p);
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return SHAPE;
        }

        @Override
        protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return Shapes.empty();
        }
    }

    public static final Item MEDALHA_CRAQUE = item("medalha_craque", Item::new, new Item.Properties().stacksTo(16));
    public static final Block CHINELO = block("chinelo", ChineloBlock::new, BlockBehaviour.Properties.of().strength(0.1f).sound(SoundType.WOOL)
            .noOcclusion().noCollision(), new Item.Properties().stacksTo(16));

    public static void init() {
        // força o carregamento estático
    }
}
