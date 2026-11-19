package com.boombox.entity;

import com.boombox.ModBlocks;
import com.boombox.SpeakerSettings;
import com.boombox.SpeakerType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Trio elétrico do carnaval: um caminhãozinho com um paredão em cima que passeia devagar pela vila tocando
 * marchinha e axé, com aldeões dançando atrás. Dura 5 minutos e vai embora.
 */
public class Trio extends PathfinderMob {
    private static final int LIFETIME = 20 * 60 * 5;
    private static final int FOLLOWERS = 6;

    public Trio(EntityType<? extends Trio> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setCustomName(Component.literal("Trio Elétrico"));
        setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 100.0).add(Attributes.MOVEMENT_SPEED, 0.22);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MoveTowardsRestrictionGoal(this, 0.8));
        goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 0.8, 0.02f));
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof net.minecraft.server.level.ServerLevel sl && tickCount % 40 == 0) {
            for (var p : sl.getEntitiesOfClass(net.minecraft.server.level.ServerPlayer.class, getBoundingBox().inflate(20))) {
                com.boombox.bar.Cups.give(p, com.boombox.bar.Cups.CARNAVAL);
            }
        }
        if (!level().isClientSide() && tickCount > LIFETIME) {
            getPassengers().forEach(p -> p.discard());
            discard(); // os aldeões que seguem somem sozinhos quando o trio vai embora
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false; // ninguém para o carnaval
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    /** Começa um carnaval perto desse ponto (em cima do chão). Devolve false se não deu. */
    public static boolean start(ServerLevel level, Vec3 near) {
        if (!level.getEntitiesOfClass(Trio.class, new AABB(BlockPos.containing(near)).inflate(200)).isEmpty()) return false;
        int x = (int) near.x + level.getRandom().nextInt(13) - 6, z = (int) near.z + level.getRandom().nextInt(13) - 6;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        Trio trio = new Trio(ModEntities.TRIO, level);
        trio.setPos(x + 0.5, y, z + 0.5);
        trio.setHomeTo(BlockPos.containing(x, y, z), 32);
        level.addFreshEntity(trio);
        // paredão em cima, tocando sozinho a playlist do carnaval
        ItemStack speaker = new ItemStack(ModBlocks.ITEMS.get(SpeakerType.PARTY_LARGE));
        speaker.set(ModBlocks.SETTINGS, new SpeakerSettings(SpeakerSettings.LIGHT_RAINBOW, 0, 4, true, Optional.empty(),
                "Carnaval", false, false, true));
        MountedSpeaker.fromItem(level, speaker, SpeakerType.PARTY_LARGE, trio);
        // aldeões fantasiados atrás
        for (int i = 0; i < FOLLOWERS; i++) {
            Raver r = new Raver(ModEntities.RAVER, level);
            r.setPos(x + 0.5 + level.getRandom().nextInt(7) - 3, y, z + 0.5 + level.getRandom().nextInt(7) - 3);
            r.follow(trio.getUUID());
            level.addFreshEntity(r);
        }
        MinecraftServer server = level.getServer();
        for (ServerPlayer p : level.players()) {
            if (p.position().distanceTo(near) < 96) {
                p.sendSystemMessage(Component.literal("§e§l♪ Olha o carnaval! §r§eUm trio elétrico está passando pela vila."));
            }
        }
        return true;
    }

    /** De vez em quando, durante o dia, um carnaval aparece numa vila onde tem jogador. */
    public static void maybeStart(MinecraftServer server) {
        ServerLevel level = server.overworld();
        if (!level.isBrightOutside()) return;
        for (ServerPlayer p : level.players()) {
            if (level.getRandom().nextFloat() > 0.05f) continue;
            int villagers = level.getEntitiesOfClass(Villager.class, p.getBoundingBox().inflate(48)).size();
            if (villagers >= 4) start(level, p.position());
        }
    }
}
