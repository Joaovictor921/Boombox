package com.boombox.bar;

import com.boombox.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Barraquinha: 0 pescaria, 1 argolas, 2 correio elegante (quermesse); 3 frutas, 4 pastel, 5 peixe (feira de domingo).
 * Cada jogada/compra custa 1 esmeralda (o correio é de graça).
 */
public class StallBlock extends FacingBlock {
    public static final IntegerProperty KIND = IntegerProperty.create("kind", 0, 5);
    public static final String[] NAMES = {"Pescaria", "Argolas", "Correio Elegante", "Frutas", "Pastel", "Peixe"};
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 15, 16);

    public StallBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(KIND, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(KIND);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    private static boolean pay(Player p) {
        if (p.getAbilities().instabuild) return true;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(Items.EMERALD)) {
                s.shrink(1);
                return true;
            }
        }
        return false;
    }

    private static void give(Player p, Item item, int n) {
        ItemStack s = new ItemStack(item, n);
        if (!p.getInventory().add(s)) net.minecraft.world.Containers.dropItemStack(p.level(), p.getX(), p.getY(), p.getZ(), s);
    }

    private static void say(Player p, String who, String msg) {
        p.sendSystemMessage(Component.literal("§6<" + who + ">§r " + msg));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel sl) || !(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        int kind = state.getValue(KIND);
        var r = sl.getRandom();
        if (kind == 2) {
            give(player, BarBlocks.CORREIO, 1);
            say(player, "Correio Elegante", "Toma um bilhetinho! Clique em quem você quiser para entregar (é anônimo).");
            Cups.give(sp, Cups.QUERMESSE);
            return InteractionResult.SUCCESS;
        }
        if (kind >= 3 && !GrillBlockEntity.isSunday() && !player.getAbilities().instabuild) {
            say(player, "Feirante", "A feira é só no domingo, freguês! Volta domingo.");
            return InteractionResult.SUCCESS;
        }
        if (!pay(player)) {
            say(player, NAMES[kind], "Custa 1 esmeralda!");
            return InteractionResult.SUCCESS;
        }
        switch (kind) {
            case 0, 1 -> {
                // quermesse: pescaria (60%) e argolas (35%)
                boolean win = r.nextFloat() < (kind == 0 ? 0.6f : 0.35f);
                level.playSound(null, pos, kind == 0 ? SoundEvents.FISHING_BOBBER_SPLASH : SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 1f, 1.2f);
                if (win) {
                    Item[] prizes = {BarBlocks.BRIGADEIRO, BarBlocks.BRIGADEIRO, BarBlocks.PASTEL, BarBlocks.QUENTAO, Items.COOKIE, ModBlocks.BEER};
                    Item prize = prizes[r.nextInt(prizes.length)];
                    give(player, prize, prize == BarBlocks.BRIGADEIRO ? 3 : 1);
                    say(player, NAMES[kind], kind == 0 ? "Pescou um prêmio! Parabéns!" : "Acertou a argola! Leva o prêmio!");
                    sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 8, 0.4, 0.3, 0.4, 0);
                    Cups.give(sp, Cups.QUERMESSE);
                } else {
                    say(player, NAMES[kind], kind == 0 ? "Ih, o peixinho escapou! Tenta de novo?" : "Quase! Bateu na garrafa e caiu fora.");
                }
            }
            case 3 -> {
                give(player, Items.APPLE, 3);
                give(player, r.nextBoolean() ? Items.MELON_SLICE : Items.SWEET_BERRIES, 4);
                say(player, "Feirante", "Leva mais uma de brinde, freguês!");
            }
            case 4 -> {
                give(player, BarBlocks.PASTEL, 2);
                say(player, "Feirante", "Dois pastéis quentinhos! Cuidado que tá pelando.");
            }
            default -> {
                give(player, r.nextBoolean() ? Items.COD : Items.SALMON, 2);
                say(player, "Feirante", "Peixe fresquinho, pescado hoje cedo!");
            }
        }
        if (kind >= 3) Cups.give(sp, Cups.FEIRA);
        level.playSound(null, pos, SoundEvents.VILLAGER_YES, SoundSource.BLOCKS, 1f, 1f);
        return InteractionResult.SUCCESS;
    }
}
