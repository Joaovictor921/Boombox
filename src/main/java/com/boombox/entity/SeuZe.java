package com.boombox.entity;

import com.boombox.ModBlocks;
import com.boombox.bar.BarBlocks;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Seu Zé: dono do bar, atrás do balcão. Clique nele para abrir o cardápio: cerveja, torresmo, coxinha e quentão,
 * pagando com esmeralda ou pendurando no fiado (anotado na lousa). Devendo demais, ele não vende até você pagar.
 */
public class SeuZe extends PathfinderMob {
    /** Cardápio: nome e preço (em esmeraldas), na ordem dos botões. */
    public static final String[] MENU = {"Cerveja", "Torresmo", "Coxinha", "Quentão"};
    public static final int[] PRICES = {1, 1, 2, 1};
    /** Quanto dá para pendurar no máximo. */
    public static final int MAX_DEBT = 6;
    /** Fiado para mostrar na lousa: "Nome=3;Outro=1". */
    private static final EntityDataAccessor<String> DEBTS = SynchedEntityData.defineId(SeuZe.class, EntityDataSerializers.STRING);
    /** Preenchido pelo cliente: abre o cardápio (id da entidade). */
    public static java.util.function.IntConsumer openMenuHook;

    private final Map<UUID, Integer> debt = new LinkedHashMap<>();
    private final Map<UUID, String> names = new LinkedHashMap<>();

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
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DEBTS, "");
    }

    /** Lista do fiado (nome, valor) para a lousa e o cardápio. */
    public Map<String, Integer> debtsForDisplay() {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (String part : entityData.get(DEBTS).split(";")) {
            int i = part.lastIndexOf('=');
            if (i > 0) out.put(part.substring(0, i), Integer.parseInt(part.substring(i + 1)));
        }
        return out;
    }

    private void syncDebts() {
        StringBuilder b = new StringBuilder();
        debt.forEach((id, v) -> {
            if (v > 0) b.append(names.getOrDefault(id, "?").replace(";", "").replace("=", "")).append('=').append(v).append(';');
        });
        entityData.set(DEBTS, b.toString());
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MoveTowardsRestrictionGoal(this, 0.5));
        goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 8f, 0.7f));
        goalSelector.addGoal(3, new RandomLookAroundGoal(this));
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && !hasHome()) setHomeTo(blockPosition(), 1); // não sai de trás do balcão
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (level().isClientSide() && openMenuHook != null) openMenuHook.accept(getId());
        getLookControl().setLookAt(player);
        return InteractionResult.SUCCESS;
    }

    private static int countEmeralds(Player p) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(Items.EMERALD)) n += s.getCount();
        }
        return n;
    }

    private static void takeEmeralds(Player p, int amount) {
        for (int i = 0; i < p.getInventory().getContainerSize() && amount > 0; i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (!s.is(Items.EMERALD)) continue;
            int take = Math.min(amount, s.getCount());
            s.shrink(take);
            amount -= take;
        }
    }

    private static Item menuItem(int i) {
        return switch (i) {
            case 1 -> BarBlocks.TORRESMO;
            case 2 -> BarBlocks.COXINHA;
            case 3 -> BarBlocks.QUENTAO;
            default -> ModBlocks.BEER;
        };
    }

    private void say(Player p, String msg) {
        p.sendSystemMessage(Component.literal("§6<Seu Zé>§r " + msg));
    }

    /** Pedido do cardápio. mode: 0 = paga com esmeralda, 1 = pendura no fiado, 2 = paga o fiado. */
    public void order(ServerPlayer p, int item, int mode) {
        if (distanceTo(p) > 8) return;
        boolean free = p.getAbilities().instabuild;
        int owed = debt.getOrDefault(p.getUUID(), 0);
        if (mode == 2) {
            int pay = free ? owed : Math.min(owed, countEmeralds(p));
            if (pay <= 0) {
                say(p, owed == 0 ? "Você não me deve nada, freguês!" : "Sem esmeralda não dá pra pagar, né?");
                return;
            }
            if (!free) takeEmeralds(p, pay);
            debt.put(p.getUUID(), owed - pay);
            names.put(p.getUUID(), p.getName().getString());
            syncDebts();
            say(p, owed - pay == 0 ? "Fiado quitado! Pode pedir à vontade." : "Pagou " + pay + ", ainda falta " + (owed - pay) + ".");
            playSound(SoundEvents.VILLAGER_YES, 1f, 0.8f);
            return;
        }
        if (item < 0 || item >= MENU.length) return;
        int price = PRICES[item];
        if (owed >= MAX_DEBT) {
            say(p, "Você tá me devendo " + owed + " esmeraldas! Paga o fiado primeiro, depois a gente conversa.");
            playSound(SoundEvents.VILLAGER_NO, 1f, 0.8f);
            return;
        }
        if (mode == 0) {
            if (!free && countEmeralds(p) < price) {
                say(p, "Isso custa " + price + " esmeralda(s). Se quiser, eu penduro...");
                return;
            }
            if (!free) takeEmeralds(p, price);
        } else {
            if (owed + price > MAX_DEBT) {
                say(p, "Pendurar mais não dá. O limite do fiado é " + MAX_DEBT + "!");
                return;
            }
            debt.put(p.getUUID(), owed + price);
            names.put(p.getUUID(), p.getName().getString());
            syncDebts();
        }
        ItemStack out = new ItemStack(menuItem(item));
        if (!p.getInventory().add(out)) spawnAtLocation((ServerLevel) level(), out);
        say(p, mode == 0 ? "Tá aqui: " + MENU[item] + ", saindo!" : MENU[item] + " pendurado(a). Tô anotando na lousa, hein!");
        playSound(SoundEvents.VILLAGER_YES, 1f, 0.8f);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        StringBuilder b = new StringBuilder();
        debt.forEach((id, v) -> b.append(id).append('=').append(names.getOrDefault(id, "?").replace(";", "").replace("=", ""))
                .append('=').append(v).append(';'));
        out.putString("Fiado", b.toString());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        debt.clear();
        names.clear();
        for (String part : in.getStringOr("Fiado", "").split(";")) {
            String[] f = part.split("=");
            if (f.length != 3) continue;
            try {
                UUID id = UUID.fromString(f[0]);
                names.put(id, f[1]);
                debt.put(id, Integer.parseInt(f[2]));
            } catch (IllegalArgumentException ignored) {}
        }
        syncDebts();
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
