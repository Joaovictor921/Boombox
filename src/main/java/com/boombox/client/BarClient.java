package com.boombox.client;

import com.boombox.bar.BarBlocks;
import com.boombox.bar.ConfettiCannonBlock;
import com.boombox.bar.ConfettiCannonBlockEntity;
import com.boombox.bar.SinucaBlock;
import com.boombox.client.render.BarRenderers;
import com.boombox.entity.ModEntities;
import com.boombox.entity.SeuZe;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/** Parte de cliente das coisas do boteco e da festa (3.1). */
public final class BarClient {
    /** Blocos com alguém em cima (para a pista de dança acender). */
    private static final Set<Long> STEPPED = new HashSet<>();
    private static final int[] CONFETTI = {0xFF3060, 0xFFD020, 0x30E070, 0x30A0FF, 0xC040FF, 0xFF8020, 0xFFFFFF};

    private BarClient() {}

    public static void register() {
        BlockEntityRendererRegistry.register(BarBlocks.TABLE_BE, BarRenderers.Table::new);
        BlockEntityRendererRegistry.register(BarBlocks.LOUSA_BE, BarRenderers.Lousa::new);
        BlockEntityRendererRegistry.register(BarBlocks.TV_BE, BarRenderers.Tv::new);
        BlockEntityRendererRegistry.register(BarBlocks.SINUCA_BE, BarRenderers.Sinuca::new);
        BlockEntityRendererRegistry.register(BarBlocks.DANCE_FLOOR_BE, BarRenderers.DanceFloor::new);
        EntityRendererRegistry.register(ModEntities.SEAT, NoopRenderer::new);
        EntityRendererRegistry.register(ModEntities.BOUNCER, BarRenderers.BouncerRenderer::new);
        EntityRendererRegistry.register(ModEntities.TRIO, BarRenderers.TrioRenderer::new);
        ConfettiCannonBlock.clientTicker = BarClient::confetti;
        SeuZe.openMenuHook = id -> Minecraft.getInstance().gui.setScreen(new ZeMenuScreen(id));
        SinucaBlock.openHook = pos -> Minecraft.getInstance().gui.setScreen(new SinucaScreen());
    }

    public static boolean stepped(BlockPos pos) {
        return STEPPED.contains(pos.asLong());
    }

    /** A cada tick: quem está pisando em quê (só perto do jogador). */
    public static void tick(Minecraft mc) {
        STEPPED.clear();
        if (mc.level == null || mc.player == null) return;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e.distanceToSqr(mc.player) > 32 * 32 || !e.onGround()) continue;
            STEPPED.add(BlockPos.containing(e.getX(), e.getY() - 0.2, e.getZ()).asLong());
        }
    }

    /** Canhão de confete: nas batidas fortes solta papel picado; a cada 4 rajadas, fogos. */
    private static void confetti(Level level, BlockPos pos, BlockState state, ConfettiCannonBlockEntity be) {
        Minecraft mc = Minecraft.getInstance();
        ClientBoomboxManager.State near = ClientBoomboxManager.playingNear(mc, Vec3.atCenterOf(pos), 24);
        float[] lv = new float[2];
        ClientBoomboxManager.levelsOf(near, lv);
        float bass = lv[0];
        long now = level.getGameTime();
        boolean show = com.boombox.bar.ShowTime.isShowNow();
        if (near != null && bass > 0.78f && be.lastBass < 0.6f && now - be.lastBurst > (show ? 10 : 30)) {
            be.lastBurst = now;
            be.bursts++;
            var r = level.getRandom();
            for (int i = 0; i < 40; i++) {
                int c = CONFETTI[r.nextInt(CONFETTI.length)];
                level.addParticle(new DustParticleOptions(c, 1.2f), true, true, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5,
                        (r.nextDouble() - 0.5) * 0.6, 0.6 + r.nextDouble() * 0.5, (r.nextDouble() - 0.5) * 0.6);
            }
            if (be.bursts % 4 == 0 || show) {
                double y = pos.getY() + 7 + r.nextInt(4);
                for (int i = 0; i < 50; i++) {
                    level.addParticle(ParticleTypes.FIREWORK, true, true, pos.getX() + 0.5, y, pos.getZ() + 0.5,
                            (r.nextDouble() - 0.5) * 0.5, (r.nextDouble() - 0.5) * 0.5, (r.nextDouble() - 0.5) * 0.5);
                }
                level.playLocalSound(pos.getX() + 0.5, y, pos.getZ() + 0.5, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.AMBIENT, 2f, 1f, false);
            }
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.BLOCKS, 1f, 1.3f, false);
        }
        be.lastBass = bass;
    }
}
