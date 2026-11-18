package com.boombox;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BoomboxBlock extends Block implements EntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    private final SpeakerType type;
    private final VoxelShape shapeNS;
    private final VoxelShape shapeEW;

    public BoomboxBlock(SpeakerType type, Properties properties) {
        super(properties);
        this.type = type;
        if (type == SpeakerType.BOOMBOX) {
            shapeNS = Block.box(0.5, 0, 4, 15.5, 13.5, 11.5);
            shapeEW = Block.box(4, 0, 0.5, 11.5, 13.5, 15.5);
        } else {
            double hw = type.width / 2.0, hd = type.depth / 2.0;
            shapeNS = Block.box(8 - hw, 0, 8 - hd, 8 + hw, type.height, 8 + hd);
            shapeEW = Block.box(8 - hd, 0, 8 - hw, 8 + hd, type.height, 8 + hw);
        }
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH));
    }

    public SpeakerType type() {
        return type;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        Direction d = state.getValue(FACING);
        return (d == Direction.NORTH || d == Direction.SOUTH) ? shapeNS : shapeEW;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide() && BoomboxMod.openScreenHook != null) {
            BoomboxMod.openScreenHook.accept(pos.immutable());
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BoomboxBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlocks.BOOMBOX_BE) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<BoomboxBlockEntity>) BoomboxBlockEntity::serverTick;
    }
}
