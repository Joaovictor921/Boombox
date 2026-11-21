package com.boombox.entity;

import com.boombox.server.ServerBoomboxManager;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Aldeão festeiro: mora na rave do DJ Leo. Fica pela pista de dança e, quando tem música tocando,
 * pula no ritmo e levanta os braços (a dança é desenhada no cliente).
 * A roupa (bioma e profissão) é sorteada para cada um ficar diferente.
 */
public class Raver extends PathfinderMob {
    /** Roupa: tipo de bioma * 16 + profissão (15 = sem profissão). -1 = ainda não sorteada. */
    private static final EntityDataAccessor<Integer> LOOK = SynchedEntityData.defineId(Raver.class, EntityDataSerializers.INT);
    /** Freguês do bar do Seu Zé: não dança, fica com a cerveja na mão. */
    private static final EntityDataAccessor<Boolean> BAR = SynchedEntityData.defineId(Raver.class, EntityDataSerializers.BOOLEAN);
    /** Jogando truco na mesa: cartas na mão, bate na mesa e comemora. */
    private static final EntityDataAccessor<Boolean> CARDS = SynchedEntityData.defineId(Raver.class, EntityDataSerializers.BOOLEAN);
    /** Jogando sinuca: taco na mão, dá uma tacada de vez em quando. */
    private static final EntityDataAccessor<Boolean> POOL = SynchedEntityData.defineId(Raver.class, EntityDataSerializers.BOOLEAN);
    /** Pagode: 1 = pandeiro, 2 = cavaquinho (0 = nenhum). */
    private static final EntityDataAccessor<Integer> INSTRUMENT = SynchedEntityData.defineId(Raver.class, EntityDataSerializers.INT);

    /** 3.3: papel especial (bêbado, feirante, boleiro) — ver RaverRoles. */
    private static final EntityDataAccessor<Integer> ROLE = SynchedEntityData.defineId(Raver.class, EntityDataSerializers.INT);
    /** Pulseira de luz no braço (pisca com a música). */
    private static final EntityDataAccessor<Boolean> GLOW = SynchedEntityData.defineId(Raver.class, EntityDataSerializers.BOOLEAN);
    /** 3.4: cor da camisa do time (futebol do campeonato, vôlei); 0 = cor padrão do papel. */
    private static final EntityDataAccessor<Integer> SHIRT = SynchedEntityData.defineId(Raver.class, EntityDataSerializers.INT);
    /** 3.5: goleiro voando na bola (-1 esquerda, 1 direita, 0 em pé); some sozinho depois de uns ticks. */
    private static final EntityDataAccessor<Integer> DIVE = SynchedEntityData.defineId(Raver.class, EntityDataSerializers.INT);
    private int diveTicks;
    /** 3.4: fogueira do luau (só no servidor) e a noite em que já procurou uma. */
    net.minecraft.core.BlockPos luauFire;
    long luauDay = -1;
    /** Só no servidor: lugares e alvos dos papéis. */
    net.minecraft.core.BlockPos barHome, houseSpot, fieldHome;
    java.util.UUID ball;
    public int stall;
    public net.minecraft.world.phys.Vec3 queueSpot, queueLook;
    /** Depois de entrar na balada: alguns ticks andando antes de escolher o "canto" dele. */
    public int settle;

    public int role() {
        return entityData.get(ROLE);
    }

    public void setRole(int role) {
        entityData.set(ROLE, role);
    }

    public boolean glow() {
        return entityData.get(GLOW);
    }

    public void setGlow(boolean g) {
        entityData.set(GLOW, g);
    }

    public int instrument() {
        return entityData.get(INSTRUMENT);
    }

    public int dive() {
        return entityData.get(DIVE);
    }

    public void setDive(int dir) {
        entityData.set(DIVE, dir);
        diveTicks = 16;
    }

    public int getShirt() {
        return entityData.get(SHIRT);
    }

    public void setShirt(int argb) {
        entityData.set(SHIRT, argb);
    }

    /** Luau: o violeiro da roda (3 = violão, desenhado como o cavaquinho "palhetando"). */
    void setLuauGuitar(boolean on) {
        int want = on ? 3 : 0;
        if (instrument() != want && (instrument() == 0 || instrument() == 3)) entityData.set(INSTRUMENT, want);
    }

    /** Carnaval: segue o trio elétrico (some quando o trio vai embora). */
    private java.util.UUID followTarget;

    public void follow(java.util.UUID trio) {
        followTarget = trio;
    }

    public boolean playingPool() {
        return entityData.get(POOL);
    }

    /** Preenchido pelo cliente: abre a tela do organizador (id da entidade, esporte 1 = futebol, 2 = vôlei). */
    public static java.util.function.BiConsumer<Integer, Integer> openSetupHook;

    /** Preenchido pelo cliente: abre a mesa de truco contra este aldeão (id da entidade). */
    public static java.util.function.IntConsumer openTrucoHook;
    public static final String[] TYPES = {"plains", "desert", "savanna", "snow", "taiga", "jungle", "swamp"};
    public static final String[] PROFESSIONS = {"farmer", "librarian", "cleric", "butcher", "shepherd", "fisherman",
            "fletcher", "leatherworker", "mason", "cartographer", "toolsmith", "weaponsmith", "armorer", "nitwit"};

    public Raver(EntityType<? extends Raver> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.5);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(LOOK, -1);
        builder.define(BAR, false);
        builder.define(CARDS, false);
        builder.define(POOL, false);
        builder.define(INSTRUMENT, 0);
        builder.define(ROLE, 0);
        builder.define(GLOW, false);
        builder.define(SHIRT, 0);
        builder.define(DIVE, 0);
    }

    private boolean tvNear(ServerLevel level) {
        for (var p : net.minecraft.core.BlockPos.betweenClosed(blockPosition().offset(-10, -3, -10), blockPosition().offset(10, 4, 10))) {
            if (level.getBlockState(p).is(com.boombox.bar.BarBlocks.TV)) return true;
        }
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    public boolean atBar() {
        return entityData.get(BAR);
    }

    public boolean playingCards() {
        return entityData.get(CARDS);
    }

    /** Comemora (pulo e festa) ou reclama de perder. */
    public void celebrate(boolean happy) {
        if (onGround()) jumpFromGround();
        playSound(happy ? SoundEvents.VILLAGER_CELEBRATE : SoundEvents.VILLAGER_NO, 1f, 1f);
    }

    @Override
    protected net.minecraft.world.InteractionResult mobInteract(Player player, net.minecraft.world.InteractionHand hand) {
        // organizador do campo / da quadra: abre a tela de amistoso ou campeonato
        if (role() == RaverRoles.ORG_FUT || role() == RaverRoles.ORG_VOLEI) {
            if (level().isClientSide() && openSetupHook != null) openSetupHook.accept(getId(), role() == RaverRoles.ORG_FUT ? 1 : 2);
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
        // freguês do bar: clique para jogar truco com ele
        if (atBar()) {
            if (level().isClientSide() && openTrucoHook != null) openTrucoHook.accept(getId());
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    public int look() {
        return entityData.get(LOOK);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MoveTowardsRestrictionGoal(this, 0.6));
        goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 8f, 0.3f));
        goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.45, 0.02f));
        goalSelector.addGoal(4, new RandomLookAroundGoal(this));
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        if (diveTicks > 0 && --diveTicks == 0) entityData.set(DIVE, 0);
        if (look() < 0) {
            int type = getRandom().nextInt(TYPES.length);
            int prof = getRandom().nextInt(4) == 0 ? 15 : getRandom().nextInt(PROFESSIONS.length);
            entityData.set(LOOK, type * 16 + prof);
        }
        if (RaverRoles.tick(this, level)) return;
        if (instrument() > 0) {
            var want = instrument() == 1 ? com.boombox.bar.BarBlocks.PANDEIRO : instrument() == 3 ? com.boombox.bar.Blocks34.VIOLAO : com.boombox.bar.BarBlocks.CAVAQUINHO;
            if (!getMainHandItem().is(want)) setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(want));
            if (!hasHome()) setHomeTo(blockPosition(), 2);
            if (tickCount % 6 == 0 && ServerBoomboxManager.isPlayingNear(level, position(), 16) && getRandom().nextInt(3) != 0) {
                float[] scale = {0.6f, 0.75f, 0.84f, 1.0f, 1.12f, 1.26f};
                playSound(instrument() == 1 ? SoundEvents.NOTE_BLOCK_SNARE.value() : SoundEvents.NOTE_BLOCK_BANJO.value(), 0.5f,
                        scale[getRandom().nextInt(scale.length)]);
            }
            return;
        }
        if (followTarget != null) {
            var t = level.getEntity(followTarget);
            if (t == null) {
                if (tickCount > 60) discard(); // acabou o carnaval
            } else if (tickCount % 10 == 0 && distanceTo(t) > 4) {
                getNavigation().moveTo(t, 0.8);
            }
        } else if (!hasHome() && settle == 0) setHomeTo(blockPosition(), atBar() ? 3 : 6);
        // gol na TV do bar: todo mundo perto comemora
        if (atBar() && tickCount % 20 == 0 && com.boombox.bar.FootballMatch.sinceGoal(level.getGameTime()) < 20 && tvNear(level)) {
            celebrate(true);
            if (getRandom().nextInt(3) == 0) level.players().stream().filter(p -> p.distanceTo(this) < 12).forEach(p ->
                    p.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§a§lGOOOOOL!")));
        }
        if (atBar()) {
            // no bar: cerveja na mão (garrafa de mel) e um gole de vez em quando
            var want = playingPool() ? net.minecraft.world.item.Items.STICK
                    : playingCards() ? net.minecraft.world.item.Items.PAPER : com.boombox.ModBlocks.BEER;
            if (playingPool() && getRandom().nextInt(120) == 0) playSound(SoundEvents.STONE_HIT, 0.6f, 1.6f); // tacada
            if (!getMainHandItem().is(want)) setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(want));
            if (playingCards()) {
                // bate a carta na mesa e, de vez em quando, comemora a vaza
                if (getRandom().nextInt(70) == 0) playSound(SoundEvents.WOOD_HIT, 0.7f, 1.3f);
                if (getRandom().nextInt(500) == 0) {
                    celebrate(true);
                    if (getRandom().nextBoolean()) level().players().stream().filter(p -> p.distanceTo(this) < 10).forEach(p ->
                            p.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§a<Aldeão> TRUCO, LADRÃO!")));
                }
            } else if (getRandom().nextInt(260) == 0) {
                playSound(SoundEvents.GENERIC_DRINK.value(), 0.6f, 0.9f + getRandom().nextFloat() * 0.2f);
            }
            // petisco na mesa do lado: belisca de vez em quando
            if (!playingPool() && getRandom().nextInt(300) == 0) {
                for (var p : net.minecraft.core.BlockPos.betweenClosed(blockPosition().offset(-2, -1, -2), blockPosition().offset(2, 1, 2))) {
                    if (level.getBlockEntity(p) instanceof com.boombox.bar.PlasticTableBlockEntity t
                            && t.items.stream().anyMatch(com.boombox.bar.Blocks34::isPetisco)) {
                        var food = t.items.stream().filter(com.boombox.bar.Blocks34::isPetisco).findFirst().get();
                        getLookControl().setLookAt(p.getX() + 0.5, p.getY() + 1, p.getZ() + 0.5);
                        playSound(SoundEvents.GENERIC_EAT.value(), 0.7f, 1f);
                        level.sendParticles(new net.minecraft.core.particles.ItemParticleOption(net.minecraft.core.particles.ParticleTypes.ITEM,
                                food.getItem()), getX(), getEyeY() - 0.2, getZ(), 6, 0.15, 0.1, 0.15, 0.04);
                        break;
                    }
                }
            }
            return;
        }
        // música perto: pula no ritmo de vez em quando e comemora
        if (tickCount % 5 == 0 && onGround() && ServerBoomboxManager.isPlayingNear(level, position(), 24)) {
            if (getRandom().nextInt(3) == 0) {
                jumpFromGround();
                if (getRandom().nextInt(12) == 0) playSound(SoundEvents.VILLAGER_CELEBRATE, 0.8f, 0.9f + getRandom().nextFloat() * 0.3f);
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putInt("Look", look());
        out.putBoolean("Bar", atBar());
        out.putBoolean("Cards", playingCards());
        out.putBoolean("Pool", playingPool());
        out.putInt("Instrument", instrument());
        out.putInt("Role", role());
        out.putBoolean("Glow", glow());
        out.putInt("Shirt", getShirt());
        out.putInt("Stall", stall);
        if (queueSpot != null) out.putBoolean("Queued", true);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        entityData.set(LOOK, in.getIntOr("Look", -1));
        entityData.set(BAR, in.getBooleanOr("Bar", false));
        entityData.set(CARDS, in.getBooleanOr("Cards", false));
        entityData.set(POOL, in.getBooleanOr("Pool", false));
        entityData.set(INSTRUMENT, in.getIntOr("Instrument", 0));
        entityData.set(ROLE, in.getIntOr("Role", 0));
        entityData.set(GLOW, in.getBooleanOr("Glow", false));
        entityData.set(SHIRT, in.getIntOr("Shirt", 0));
        stall = in.getIntOr("Stall", 0);
        // estava na fila quando o mundo foi salvo: a fila se refaz sozinha
        if (in.getBooleanOr("Queued", false)) queueSpot = net.minecraft.world.phys.Vec3.ZERO;
        // o bêbado também é freguês do bar (cerveja na mão e truco)
        if (role() == RaverRoles.DRUNK) entityData.set(BAR, true);
    }

    @Override
    public boolean hurtServer(ServerLevel level, net.minecraft.world.damagesource.DamageSource source, float amount) {
        // várzea: bater num boleiro é falta (e não machuca)
        if ((role() == RaverRoles.TEAM_A || role() == RaverRoles.TEAM_B) && source.getEntity() instanceof Player p) {
            for (Ball b : level.getEntitiesOfClass(Ball.class, getBoundingBox().inflate(40))) {
                int t = b.teamOf(this);
                if (t >= 0) b.foul(t, this);
                p.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§cFalta! §fNada de bater no adversário."));
            }
            playSound(SoundEvents.VILLAGER_HURT, 1f, 1f);
            return false;
        }
        // barraqueiro e afins não brigam
        return super.hurtServer(level, source, amount);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.VILLAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.VILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.VILLAGER_DEATH;
    }


    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }
}
