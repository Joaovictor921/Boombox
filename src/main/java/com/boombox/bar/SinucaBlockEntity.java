package com.boombox.bar;

import com.boombox.entity.Raver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

import java.util.Locale;
import java.util.Random;

/**
 * O jogo de sinuca da mesa, rodando no servidor (todo mundo vê as mesmas bolas). 15 bolas + a branca; lisas valem 1,
 * a 8 (preta) vale 3 e matar a branca dá 1 ponto para o outro. Com aldeões jogando sinuca perto, você joga contra eles
 * (vez de cada um); sem ninguém, é treino. Quando a mesa esvazia, as bolas voltam ao triângulo.
 */
public class SinucaBlockEntity extends BlockEntity {
    public static final int N = 16;
    public static final double R = 0.045, MARGIN = 0.16, POCKET = 0.1, FRICTION = 0.975;
    public static final double X_MAX = SinucaBlock.LENGTH, Z_MAX = SinucaBlock.WIDTH;
    public static final double[][] POCKETS = {
            {MARGIN, MARGIN}, {X_MAX / 2, MARGIN - 0.03}, {X_MAX - MARGIN, MARGIN},
            {MARGIN, Z_MAX - MARGIN}, {X_MAX / 2, Z_MAX - MARGIN + 0.03}, {X_MAX - MARGIN, Z_MAX - MARGIN}};

    public final double[] x = new double[N], z = new double[N], vx = new double[N], vz = new double[N];
    public final boolean[] in = new boolean[N];
    public boolean botTurn, moving, againstBot;
    public int myScore, hisScore;
    public String message = "Clique na mesa para pegar o taco";
    private int wait;
    private boolean potted, foul;
    private final Random rnd = new Random();

    public SinucaBlockEntity(BlockPos pos, BlockState state) {
        super(BarBlocks.SINUCA_BE, pos, state);
        rack();
    }

    /** Arruma as bolas: branca na cabeceira, as 15 no triângulo com a 8 no meio. */
    public void rack() {
        x[0] = 0.75;
        z[0] = Z_MAX / 2;
        int[] order = {1, 9, 2, 10, 8, 3, 11, 4, 12, 5, 13, 6, 14, 7, 15};
        int k = 0;
        for (int row = 0; row < 5; row++) {
            for (int i = 0; i <= row; i++) {
                int b = order[k++];
                x[b] = 2.05 + row * (R * 1.8);
                z[b] = Z_MAX / 2 + (i - row / 2.0) * (R * 2.05);
            }
        }
        for (int i = 0; i < N; i++) {
            vx[i] = vz[i] = 0;
            in[i] = false;
        }
        moving = false;
        myScore = hisScore = 0;
        botTurn = false;
    }

    /** Tacada: ângulo nas coordenadas da mesa e força de 0 a 1. */
    public void shoot(double angle, double power) {
        if (moving || in[0]) return;
        double speed = 0.03 + Math.max(0, Math.min(1, power)) * 0.17; // blocos por tick
        vx[0] = Math.cos(angle) * speed;
        vz[0] = Math.sin(angle) * speed;
        moving = true;
        potted = foul = false;
        if (level != null) level.playSound(null, worldPosition, SoundEvents.STONE_HIT, SoundSource.BLOCKS, 0.8f, 1.8f);
        changed();
    }

    /** Pedido de tacada de um jogador (só na vez dele). */
    public void playerShot(ServerPlayer p, double angle, double power) {
        if (moving) return;
        if (againstBot && botTurn) {
            p.sendOverlayMessage(Component.literal("§eEspera, é a vez do aldeão!"));
            return;
        }
        message = "Tacada de " + p.getName().getString();
        shoot(angle, power);
    }

    private void changed() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SinucaBlockEntity be) {
        if (!(level instanceof ServerLevel sl)) return;
        if (level.getGameTime() % 40 == 0) {
            boolean bot = !sl.getEntitiesOfClass(Raver.class, new AABB(pos).inflate(8), Raver::playingPool).isEmpty();
            if (bot != be.againstBot) {
                be.againstBot = bot;
                be.changed();
            }
        }
        if (be.moving) {
            for (int s = 0; s < 8; s++) be.physics(sl, 1.0 / 8);
            boolean still = true;
            for (int i = 0; i < N; i++) if (!be.in[i] && (Math.abs(be.vx[i]) > 0.0008 || Math.abs(be.vz[i]) > 0.0008)) still = false;
            if (still) be.endShot();
            be.changed();
            return;
        }
        if (be.wait > 0 && --be.wait == 0 && allIn(be)) {
            be.rack();
            be.message = "Bolas no triângulo. Nova partida!";
            be.changed();
        }
        if (be.againstBot && be.botTurn && be.wait == 0 && level.getGameTime() % 50 == 0) be.botShot();
    }

    private static boolean allIn(SinucaBlockEntity be) {
        for (int i = 1; i < N; i++) if (!be.in[i]) return false;
        return true;
    }

    private void physics(ServerLevel sl, double dt) {
        double f = Math.pow(FRICTION, dt);
        for (int i = 0; i < N; i++) {
            if (in[i]) continue;
            x[i] += vx[i] * dt;
            z[i] += vz[i] * dt;
            vx[i] *= f;
            vz[i] *= f;
            for (double[] p : POCKETS) {
                if (Math.hypot(x[i] - p[0], z[i] - p[1]) < POCKET) {
                    in[i] = true;
                    vx[i] = vz[i] = 0;
                    sl.playSound(null, worldPosition, SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 0.7f, 0.8f);
                    if (i == 0) foul = true;
                    else {
                        potted = true;
                        int pts = i == 8 ? 3 : 1;
                        if (againstBot && botTurn) hisScore += pts;
                        else myScore += pts;
                    }
                    break;
                }
            }
            if (in[i]) continue;
            // tabelas (perdem um pouco de força na batida)
            if (x[i] < MARGIN) { x[i] = MARGIN; vx[i] = -vx[i] * 0.8; }
            if (x[i] > X_MAX - MARGIN) { x[i] = X_MAX - MARGIN; vx[i] = -vx[i] * 0.8; }
            if (z[i] < MARGIN) { z[i] = MARGIN; vz[i] = -vz[i] * 0.8; }
            if (z[i] > Z_MAX - MARGIN) { z[i] = Z_MAX - MARGIN; vz[i] = -vz[i] * 0.8; }
        }
        for (int i = 0; i < N; i++) {
            if (in[i]) continue;
            for (int j = i + 1; j < N; j++) {
                if (in[j]) continue;
                double dx = x[j] - x[i], dz = z[j] - z[i], d = Math.sqrt(dx * dx + dz * dz);
                if (d >= 2 * R || d < 1e-6) continue;
                double nx = dx / d, nz = dz / d, overlap = 2 * R - d;
                x[i] -= nx * overlap / 2; z[i] -= nz * overlap / 2;
                x[j] += nx * overlap / 2; z[j] += nz * overlap / 2;
                double rel = (vx[i] - vx[j]) * nx + (vz[i] - vz[j]) * nz;
                if (rel > 0) {
                    vx[i] -= rel * nx; vz[i] -= rel * nz;
                    vx[j] += rel * nx; vz[j] += rel * nz;
                    if (rel > 0.02) sl.playSound(null, worldPosition, SoundEvents.STONE_HIT, SoundSource.BLOCKS, 0.4f, 2f);
                }
            }
        }
    }

    private void endShot() {
        moving = false;
        for (int i = 0; i < N; i++) vx[i] = vz[i] = 0;
        if (foul) {
            if (againstBot && !botTurn) hisScore++;
            else myScore++;
            in[0] = false;
            x[0] = 0.75;
            z[0] = Z_MAX / 2;
            message = "A branca caiu!";
        }
        if (allIn(this)) {
            message = !againstBot ? "Mesa limpa! " + myScore + " pontos"
                    : myScore > hisScore ? "Você ganhou! " + myScore + " x " + hisScore
                    : myScore < hisScore ? "O aldeão ganhou. " + myScore + " x " + hisScore : "Empate! " + myScore + " x " + hisScore;
            wait = 100;
            botTurn = false;
            return;
        }
        if (againstBot && (!potted || foul)) botTurn = !botTurn;
        if (!foul) message = againstBot ? (botTurn ? "Vez do aldeão..." : "Sua vez") : (potted ? "Boa!" : "Sua vez");
    }

    /** O aldeão mira na bola mais fácil para uma caçapa, com um pouco de erro. */
    private void botShot() {
        double best = -1e9, ang = 0;
        for (int b = 1; b < N; b++) {
            if (in[b]) continue;
            for (double[] p : POCKETS) {
                double px = p[0] - x[b], pz = p[1] - z[b], pl = Math.hypot(px, pz);
                double gx = x[b] - px / pl * 2 * R, gz = z[b] - pz / pl * 2 * R;
                double cx = gx - x[0], cz = gz - z[0], cl = Math.hypot(cx, cz);
                double align = (cx * px + cz * pz) / (cl * pl + 1e-6);
                double score = align * 2 - (cl + pl) / 3;
                if (score > best) {
                    best = score;
                    ang = Math.atan2(cz, cx) + (rnd.nextDouble() - 0.5) * 0.06;
                }
            }
        }
        message = "Tacada do aldeão";
        shoot(ang, 0.35 + rnd.nextDouble() * 0.4);
    }

    // ------------------------------------------------------------ salvar / mandar para o cliente
    private String encode() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < N; i++) b.append(String.format(Locale.ROOT, "%.4f,%.4f,%d;", x[i], z[i], in[i] ? 1 : 0));
        return b.toString();
    }

    private void decode(String s) {
        String[] parts = s.split(";");
        for (int i = 0; i < N && i < parts.length; i++) {
            String[] f = parts[i].split(",");
            if (f.length != 3) continue;
            x[i] = Double.parseDouble(f[0]);
            z[i] = Double.parseDouble(f[1]);
            in[i] = f[2].equals("1");
        }
    }

    @Override
    protected void loadAdditional(ValueInput in_) {
        super.loadAdditional(in_);
        String b = in_.getStringOr("Balls", "");
        if (!b.isEmpty()) decode(b);
        myScore = in_.getIntOr("Me", 0);
        hisScore = in_.getIntOr("Him", 0);
        botTurn = in_.getBooleanOr("BotTurn", false);
        againstBot = in_.getBooleanOr("AgainstBot", false);
        moving = in_.getBooleanOr("Moving", false);
        message = in_.getStringOr("Msg", message);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putString("Balls", encode());
        out.putInt("Me", myScore);
        out.putInt("Him", hisScore);
        out.putBoolean("BotTurn", botTurn);
        out.putBoolean("AgainstBot", againstBot);
        out.putBoolean("Moving", moving);
        out.putString("Msg", message);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }
}
