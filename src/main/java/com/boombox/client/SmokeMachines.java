package com.boombox.client;

import com.boombox.SmokeMachineBlock;
import com.boombox.SmokeMachineBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * Máquinas de fumaça no cliente: soltam fumaça enquanto tem música perto e deixam o ar "enfumaçado"
 * em volta delas. Os canhões de luz usam isso para mostrar os feixes mais fortes dentro da fumaça.
 */
public final class SmokeMachines {
    /** Máquina ligada → última vez que soltou fumaça. */
    private static final Map<BlockPos, Long> ACTIVE = new HashMap<>();
    private static final double HAZE_RADIUS = 18;

    private SmokeMachines() {}

    public static void tick(Level level, BlockPos pos, BlockState state, SmokeMachineBlockEntity be) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 c = Vec3.atCenterOf(pos);
        if (ClientBoomboxManager.playingNear(mc, c, 24) == null) {
            ACTIVE.remove(pos);
            return;
        }
        ACTIVE.put(pos.immutable(), System.currentTimeMillis());
        var rnd = level.getRandom();
        long t = level.getGameTime();
        // jato a cada 4 segundos e um fiozinho no resto do tempo
        boolean burst = t % 80 < 16;
        if (!burst && rnd.nextInt(5) != 0) return;
        Direction f = state.getValue(SmokeMachineBlock.FACING);
        double speed = burst ? 0.12 : 0.04;
        for (int i = 0; i < (burst ? 2 : 1); i++) {
            level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, true,
                    c.x + f.getStepX() * 0.55, pos.getY() + 0.35, c.z + f.getStepZ() * 0.55,
                    f.getStepX() * speed + (rnd.nextDouble() - 0.5) * 0.02, 0.005,
                    f.getStepZ() * speed + (rnd.nextDouble() - 0.5) * 0.02);
        }
    }

    /** Quanto de fumaça tem no ar nesse ponto (0 a 1). */
    public static float hazeAt(Vec3 p) {
        long now = System.currentTimeMillis();
        float best = 0;
        for (Map.Entry<BlockPos, Long> e : ACTIVE.entrySet()) {
            if (now - e.getValue() > 3000) continue;
            double d = Math.sqrt(Vec3.atCenterOf(e.getKey()).distanceToSqr(p));
            best = Math.max(best, (float) Math.max(0, 1 - d / HAZE_RADIUS));
        }
        if (com.boombox.bar.ShowTime.isShowNow()) best = Math.max(best, 0.5f); // show: fumaça extra
        return Math.min(1f, best * 1.4f);
    }

    public static void clear() {
        ACTIVE.clear();
    }
}
