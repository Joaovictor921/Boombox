package com.boombox;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
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
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /** Quem coloca a caixa vira o dono (para o Modo DJ). */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && placer instanceof Player p && level.getBlockEntity(pos) instanceof BoomboxBlockEntity be) {
            be.setSettings(be.settings().withOwner(p.getUUID(), p.getName().getString()));
        }
    }

    /** Corante na caixa de festa: pinta o gabinete (como as caixas coloridas de verdade). */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!type.isParty() || !(stack.getItem() instanceof DyeItem)) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        if (!(level.getBlockEntity(pos) instanceof BoomboxBlockEntity be)) return InteractionResult.PASS;
        var dye = stack.get(net.minecraft.core.component.DataComponents.DYE);
        if (dye == null) return super.useItemOn(stack, state, level, pos, player, hand, hit);
        if (be.settings().dye() == dye.getId()) return InteractionResult.PASS;
        if (!level.isClientSide()) {
            if (!be.settings().canControl(player.getUUID())) {
                player.sendOverlayMessage(Component.literal("§cModo DJ: só " + be.settings().lockedBy() + " pode mexer nesta caixa"));
                return InteractionResult.FAIL;
            }
            be.setSettings(be.settings().withDye(dye.getId()));
            stack.consume(1, player);
            level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1f, 1f);
        }
        return InteractionResult.SUCCESS;
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
