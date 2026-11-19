package com.boombox.bar;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** As bolhas existem só no cliente (cada jogador vê as suas, que nem fumaça). */
public class BubbleMachineBlockEntity extends BlockEntity {
    /** Bolha: posição relativa ao bloco, velocidade, tamanho, idade e cor (matiz). */
    public static final class Bubble {
        public double x, y, z, vx, vy, vz;
        public float size, hue;
        public int age, life;
    }

    public final List<Bubble> bubbles = new ArrayList<>();

    public BubbleMachineBlockEntity(BlockPos pos, BlockState state) {
        super(BarBlocks.BUBBLE_BE, pos, state);
    }
}
