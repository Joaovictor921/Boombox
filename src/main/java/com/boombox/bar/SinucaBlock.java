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
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Mesa de sinuca 3x2 blocos. A parte 0 (canto da frente, à esquerda de quem olha) guarda o jogo e desenha a mesa
 * inteira; as outras 5 só ocupam o espaço. Coordenadas da mesa: x de 0 a 3 (para a direita de quem olha a frente)
 * e z de 0 a 2 (para o fundo).
 */
public class SinucaBlock extends FacingBlock implements EntityBlock {
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 5);
    public static final int LENGTH = 3, WIDTH = 2;
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 14, 16);
    /** Preenchido pelo cliente: pega o taco / dá a tacada (posição da parte 0). */
    public static java.util.function.Consumer<BlockPos> openHook;

    public SinucaBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(PART, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PART);
    }

    /** Direção do eixo x da mesa (comprimento) e do eixo z (largura). */
    public static Direction xDir(Direction facing) {
        return facing.getClockWise();
    }

    public static Direction zDir(Direction facing) {
        return facing.getOpposite();
    }

    public static BlockPos partPos(BlockPos main, Direction facing, int i, int j) {
        return main.relative(xDir(facing), i).relative(zDir(facing), j);
    }

    public static BlockPos mainPos(BlockPos pos, BlockState s) {
        int p = s.getValue(PART);
        Direction f = s.getValue(FACING);
        return pos.relative(xDir(f), -(p % LENGTH)).relative(zDir(f), -(p / LENGTH));
    }

    /** Ponto do mundo → coordenadas da mesa (em blocos). */
    public static double[] toLocal(BlockPos main, Direction facing, Vec3 w) {
        double dx = w.x - main.getX() - 0.5, dz = w.z - main.getZ() - 0.5;
        Direction X = xDir(facing), Z = zDir(facing);
        return new double[]{0.5 + dx * X.getStepX() + dz * X.getStepZ(), 0.5 + dx * Z.getStepX() + dz * Z.getStepZ()};
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState s = super.getStateForPlacement(ctx).setValue(PART, 0);
        Direction f = s.getValue(FACING);
        for (int i = 0; i < LENGTH; i++) {
            for (int j = 0; j < WIDTH; j++) {
                if (!ctx.getLevel().getBlockState(partPos(ctx.getClickedPos(), f, i, j)).canBeReplaced(ctx)) return null;
            }
        }
        return s;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide()) return;
        Direction f = state.getValue(FACING);
        for (int i = 0; i < LENGTH; i++) {
            for (int j = 0; j < WIDTH; j++) {
                if (i + j > 0) level.setBlock(partPos(pos, f, i, j), state.setValue(PART, i + LENGTH * j), Block.UPDATE_ALL);
            }
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockPos main = mainPos(pos, state);
        Direction f = state.getValue(FACING);
        for (int i = 0; i < LENGTH; i++) {
            for (int j = 0; j < WIDTH; j++) {
                BlockPos p = partPos(main, f, i, j);
                if (!p.equals(pos) && level.getBlockState(p).is(this)) {
                    level.destroyBlock(p, i + j == 0 && !player.getAbilities().instabuild);
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide() && openHook != null) openHook.accept(mainPos(pos, state));
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == 0 ? new SinucaBlockEntity(pos, state) : null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != BarBlocks.SINUCA_BE) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<SinucaBlockEntity>) SinucaBlockEntity::serverTick;
    }
}
