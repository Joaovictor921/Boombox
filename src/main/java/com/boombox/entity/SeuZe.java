package com.boombox.entity;

import com.boombox.ModBlocks;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * Seu Zé: dono do bar, fica atrás do balcão. Com uma esmeralda na mão, clique nele e ele te vende uma cerveja.
 */
public class SeuZe extends PathfinderMob {
    private static final String[] LINES = {
            "Fiado só amanhã!",
            "Quer uma geladinha? Traz uma esmeralda.",
            "Essa música aí é das antigas, das boas.",
            "Aqui o som é baixinho, que é pra conversar.",
            "Pendura não, hein!",
            "Vai um truco aí com o pessoal da mesa?"
    };

    public SeuZe(EntityType<? extends SeuZe> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setCustomName(Component.literal("Seu Zé"));
        setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 30.0).add(Attributes.MOVEMENT_SPEED, 0.2);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MoveTowardsRestrictionGoal(this, 0.5));
        goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 8f, 0.7f));
        goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.3, 0.001f));
        goalSelector.addGoal(4, new RandomLookAroundGoal(this));
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && !hasHome()) setHomeTo(blockPosition(), 1); // não sai de trás do balcão
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (level().isClientSide()) return InteractionResult.SUCCESS;
        ItemStack held = player.getItemInHand(hand);
        if (held.is(Items.EMERALD)) {
            if (!player.getAbilities().instabuild) held.shrink(1);
            ItemStack beer = new ItemStack(ModBlocks.BEER);
            if (!player.getInventory().add(beer)) spawnAtLocation((net.minecraft.server.level.ServerLevel) level(), beer);
            playSound(SoundEvents.VILLAGER_YES, 1f, 0.8f);
            player.sendSystemMessage(Component.literal("§6<Seu Zé>§r Tá aqui, trincando de gelada!"));
        } else {
            player.sendSystemMessage(Component.literal("§6<Seu Zé>§r " + LINES[getRandom().nextInt(LINES.length)]));
        }
        getLookControl().setLookAt(player);
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }
}
