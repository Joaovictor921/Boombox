package com.boombox.bar;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Mesa de sinuca (2 blocos, como uma cama): clique para jogar. A parte "principal" desenha a mesa inteira
 * com as bolas; a outra metade só ocupa o espaço.
 */
public class SinucaBlock extends FacingBlock implements EntityBlock {
    /** true = metade principal (onde fica a mesa desenhada). */
    public static final BooleanProperty MAIN = BooleanProperty.create("main");
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 13, 16);
    /** Preenchido pelo cliente: abre o jogo de sinuca. */
    public static java.util.function.Consumer<BlockPos> openHook;

    public SinucaBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(MAIN, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(MAIN);
    }

    /** A outra metade fica à direita de quem coloca (ao lado da principal). */
    public static Direction otherHalf(BlockState s) {
        Direction f = s.getValue(FACING);
        return s.getValue(MAIN) ? f.getClockWise() : f.getCounterClockWise();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState s = super.getStateForPlacement(ctx).setValue(MAIN, true);
        BlockPos other = ctx.getClickedPos().relative(otherHalf(s));
        return ctx.getLevel().getBlockState(other).canBeReplaced(ctx) ? s : null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide()) level.setBlock(pos.relative(otherHalf(state)), state.setValue(MAIN, false), Block.UPDATE_ALL);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockPos other = pos.relative(otherHalf(state));
        if (level.getBlockState(other).is(this)) level.destroyBlock(other, !state.getValue(MAIN) && !player.getAbilities().instabuild);
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockPos main = state.getValue(MAIN) ? pos : pos.relative(otherHalf(state));
        if (level.isClientSide() && openHook != null) openHook.accept(main);
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(MAIN) ? new SinucaBlockEntity(pos, state) : null;
    }
}
