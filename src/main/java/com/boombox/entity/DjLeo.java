package com.boombox.entity;

import net.minecraft.network.chat.Component;
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
import net.minecraft.world.level.Level;

/**
 * DJ Leo: mora numa casinha de rave em cada vila, comandando o som estourado.
 * Fica dentro de casa (perto de onde nasceu), dança quando tem música e conversa com quem clica nele.
 */
public class DjLeo extends PathfinderMob {
    private static final String[] LINES = {
            "Aumenta esse som aí!",
            "Aqui é rave de verdade, parceiro!",
            "Essa caixa tá estourada mesmo, faz parte.",
            "Chega mais, a pista é sua!",
            "Pode pedir música não, quem manda no som sou eu!",
            "O grave tá batendo no peito? Então tá certo.",
            "Rave raiz: luz de LED e caixa no talo.",
            "Se o vizinho reclamar, a gente aumenta."
    };

    public DjLeo(EntityType<? extends DjLeo> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setCustomName(Component.literal("DJ Leo"));
        setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MoveTowardsRestrictionGoal(this, 0.6));
        goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 10f, 0.6f));
        goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.4, 0.001f));
        goalSelector.addGoal(4, new RandomLookAroundGoal(this));
    }

    @Override
    public void tick() {
        super.tick();
        // não sai de casa: fica num raio pequeno de onde nasceu
        if (!level().isClientSide() && !hasHome()) setHomeTo(blockPosition(), 3);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!level().isClientSide()) {
            player.sendSystemMessage(Component.literal("§d<DJ Leo>§r " + LINES[getRandom().nextInt(LINES.length)]));
            getLookControl().setLookAt(player);
        }
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
