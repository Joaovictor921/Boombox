package com.boombox.bar;

import com.boombox.entity.Balloon;
import com.boombox.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Aniversário de aldeão: de vez em quando (ao meio-dia, numa vila perto de um jogador) um aldeão faz aniversário.
 * Aparece uma mesa com brigadeiro, bolo com vela e balões; os aldeões em volta vêm para perto, cantam parabéns e
 * no fim todo mundo grita "é pique". Quem estiver na festa ganha o copo de lembrança. Depois de uns 2 minutos a
 * festa acaba e as coisas somem. Use /aniversario para fazer uma festa agora.
 */
public final class Birthday {
    private static final int LENGTH = 20 * 120;
    private static Party party;
    private static long lastDay = -1;

    private Birthday() {}

    private static final class Party {
        ServerLevel level;
        Villager hero;
        BlockPos table, cake;
        final List<Balloon> balloons = new ArrayList<>();
        int t;
        Component oldName;
    }

    /** A cada tick do servidor. */
    public static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        if (party != null) {
            run(party);
            return;
        }
        long time = level.getOverworldClockTime();
        long day = time / 24000;
        // meio-dia: 30% de chance de ter aniversário numa vila perto de algum jogador
        if (time % 24000 >= 6000 && time % 24000 < 6100 && day != lastDay) {
            lastDay = day;
            if (level.getRandom().nextFloat() < 0.3f) {
                for (ServerPlayer p : level.players()) if (start(level, p)) break;
            }
        }
    }

    /** Começa uma festa perto do jogador (precisa de um aldeão por perto). */
    public static boolean start(ServerLevel level, ServerPlayer near) {
        if (party != null) return false;
        var villagers = level.getEntitiesOfClass(Villager.class, near.getBoundingBox().inflate(64), v -> v.onGround() && !v.isBaby()
                && !com.boombox.entity.Sports.nearSportsArea(level, v.position(), 6)); // 3.5.1: festa longe do campo e da quadra
        if (villagers.isEmpty()) return false;
        Villager hero = villagers.get(level.getRandom().nextInt(villagers.size()));
        // procura um cantinho livre no chão perto dele para a mesa e o bolo
        BlockPos base = hero.blockPosition();
        BlockPos table = null, cake = null;
        for (BlockPos p : BlockPos.betweenClosed(base.offset(-4, -1, -4), base.offset(4, 1, 4))) {
            BlockPos q = p.relative(Direction.EAST);
            if (free(level, p) && free(level, q)) {
                table = p.immutable();
                cake = q.immutable();
                break;
            }
        }
        if (table == null) return false;
        Party pt = new Party();
        pt.level = level;
        pt.hero = hero;
        pt.table = table;
        pt.cake = cake;
        pt.oldName = hero.getCustomName();
        level.setBlock(table, BarBlocks.TABLE.defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(table) instanceof PlasticTableBlockEntity be) {
            for (int i = 0; i < 4; i++) be.put(new ItemStack(BarBlocks.BRIGADEIRO, 3));
        }
        level.setBlock(cake, Blocks.CANDLE_CAKE.defaultBlockState().setValue(CandleCakeBlock.LIT, true), Block.UPDATE_ALL);
        for (int i = 0; i < 6; i++) {
            Balloon b = ModEntities.BALLOON.create(level, EntitySpawnReason.EVENT);
            if (b == null) continue;
            double a = i * Math.PI / 3;
            b.snapTo(table.getX() + 1 + Math.cos(a) * 2.2, table.getY(), table.getZ() + 0.5 + Math.sin(a) * 2.2, 0, 0);
            b.randomColor();
            b.life = LENGTH + 100;
            level.addFreshEntity(b);
            pt.balloons.add(b);
        }
        hero.setCustomName(Component.literal("§d🎂 Aniversariante"));
        hero.setCustomNameVisible(true);
        for (ServerPlayer p : level.players()) {
            if (p.distanceTo(hero) < 96) {
                p.sendSystemMessage(Component.literal("§d§l🎂 Tem aniversário de aldeão na vila! §r§dChega lá pra cantar parabéns e comer brigadeiro."));
            }
        }
        party = pt;
        return true;
    }

    private static boolean free(ServerLevel level, BlockPos p) {
        return level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()
                && level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP);
    }

    private static void run(Party pt) {
        ServerLevel level = pt.level;
        pt.t++;
        var center = net.minecraft.world.phys.Vec3.atCenterOf(pt.table);
        if (pt.t % 60 == 1) {
            // os aldeões de perto vêm para a festa
            for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(pt.table).inflate(24))) {
                if (v.distanceToSqr(center) > 9) v.getNavigation().moveTo(center.x + level.getRandom().nextInt(5) - 2, center.y, center.z + level.getRandom().nextInt(5) - 2, 0.6);
            }
            if (pt.hero.isAlive() && pt.hero.distanceToSqr(center) > 4) pt.hero.getNavigation().moveTo(center.x, center.y, center.z, 0.6);
        }
        if (pt.t == 20 * 40) {
            say(level, center, "§d♪ Todo mundo cantando parabéns pro aniversariante! ♪ §7(palmas)");
            level.playSound(null, pt.table, SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1f, 1f);
        }
        if (pt.t > 20 * 40 && pt.t < 20 * 60 && pt.t % 10 == 0) {
            level.sendParticles(ParticleTypes.NOTE, center.x, center.y + 1.6, center.z, 3, 1.5, 0.4, 1.5, 1);
            if (pt.t % 20 == 0) level.playSound(null, pt.table, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.NEUTRAL, 0.7f, 0.8f + level.getRandom().nextFloat() * 0.6f);
        }
        if (pt.t == 20 * 60) {
            say(level, center, "§e§lÉ pique, é pique, é pique, é pique, é pique! É hora, é hora! Rá-tim-bum! 🎉");
            level.sendParticles(ParticleTypes.FIREWORK, center.x, center.y + 2, center.z, 60, 1, 1, 1, 0.15);
            level.playSound(null, pt.table, SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.NEUTRAL, 1f, 1f);
            // apaga a vela (soprou!)
            BlockState c = level.getBlockState(pt.cake);
            if (c.is(Blocks.CANDLE_CAKE)) level.setBlock(pt.cake, c.setValue(CandleCakeBlock.LIT, false), Block.UPDATE_ALL);
            for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new AABB(pt.table).inflate(16))) {
                Cups.give(p, Cups.ANIVERSARIO);
            }
            for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(pt.table).inflate(12))) {
                if (v.onGround()) v.getJumpControl().jump();
            }
        }
        if (pt.t >= LENGTH) end(pt);
    }

    private static void say(ServerLevel level, net.minecraft.world.phys.Vec3 at, String msg) {
        for (ServerPlayer p : level.players()) if (p.position().distanceTo(at) < 32) p.sendSystemMessage(Component.literal(msg));
    }

    private static void end(Party pt) {
        ServerLevel level = pt.level;
        if (level.getBlockState(pt.table).is(BarBlocks.TABLE)) {
            if (level.getBlockEntity(pt.table) instanceof PlasticTableBlockEntity be) be.items.clear();
            level.removeBlock(pt.table, false);
        }
        BlockState c = level.getBlockState(pt.cake);
        if (c.is(Blocks.CANDLE_CAKE) || c.is(Blocks.CAKE)) level.removeBlock(pt.cake, false);
        for (Balloon b : pt.balloons) b.discard();
        if (pt.hero.isAlive()) {
            pt.hero.setCustomName(pt.oldName);
            pt.hero.setCustomNameVisible(false);
        }
        party = null;
    }
}
