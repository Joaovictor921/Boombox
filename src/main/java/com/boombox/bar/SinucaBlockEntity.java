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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Sinuca de bola 8, rodando no servidor (todo mundo vê as mesmas bolas). Regras de bar:
 * <ul>
 *     <li>Na saída a mesa está aberta; a primeira bola encaçapada depois dela define quem fica com as
 *     <b>lisas</b> (1 a 7) e quem fica com as <b>listradas</b> (9 a 15).</li>
 *     <li>Encaçapou bola sua sem falta, joga de novo; senão passa a vez.</li>
 *     <li>Falta: matar a branca, não acertar nenhuma bola, ou tocar primeiro numa bola que não é sua
 *     (ou na 8 antes da hora). A branca volta para a cabeceira e a vez passa.</li>
 *     <li>A 8 é a última: quem encaçapa a 8 depois de limpar as suas, sem falta, ganha. Matar a 8 antes
 *     (ou com falta) perde a partida. Na saída, a 8 que cai volta para o lugar.</li>
 * </ul>
 * Lado 0 e lado 1: o primeiro jogador a tacar fica com o lado da vez; com aldeões jogando sinuca perto,
 * o lado 1 é do aldeão.
 */
public class SinucaBlockEntity extends BlockEntity {
    public static final int N = 16;
    public static final double R = 0.04, MARGIN = 0.16, POCKET = 0.1;
    public static final double X_MAX = SinucaBlock.LENGTH, Z_MAX = SinucaBlock.WIDTH;
    public static final double HEAD_X = 0.75, FOOT_X = 2.1;
    public static final String BOT = "Aldeão";
    public static final double[][] POCKETS = {
            {MARGIN, MARGIN}, {X_MAX / 2, MARGIN - 0.03}, {X_MAX - MARGIN, MARGIN},
            {MARGIN, Z_MAX - MARGIN}, {X_MAX / 2, Z_MAX - MARGIN + 0.03}, {X_MAX - MARGIN, Z_MAX - MARGIN}};

    public final double[] x = new double[N], z = new double[N], vx = new double[N], vz = new double[N];
    public final boolean[] in = new boolean[N];
    /** Cliente: posições da sincronia anterior (para as bolas andarem lisinho). */
    public final double[] px = new double[N], pz = new double[N];
    /** Cliente: quando chegou a última atualização (nanoTime). As bolas vão de px até x em 1 tick e param lá. */
    public long syncNanos;

    /** Cliente: quanto (0 a 1) já andou de px até x desde a última atualização. */
    public double syncAlpha() {
        return syncNanos == 0 ? 1 : Math.max(0, Math.min(1, (System.nanoTime() - syncNanos) / 50_000_000.0));
    }
    public boolean moving, againstBot;
    /** Grupo de cada lado: 0 = mesa aberta, 1 = lisas, 2 = listradas. */
    public final int[] group = new int[2];
    /** Nome de quem joga em cada lado ("" = ainda livre). */
    public final String[] names = {"", ""};
    public int turn, winner = -1;
    public boolean breakShot = true;
    /** Tacadas de cada lado na partida (para "matou a 8 de primeira"). */
    private final int[] shots = new int[2];
    public String message = "Clique na mesa para pegar o taco";
    private int wait;
    // durante a tacada
    private int firstHit = -1;
    private boolean cuePotted;
    private final List<Integer> pottedNow = new ArrayList<>();
    private final Random rnd = new Random();

    public SinucaBlockEntity(BlockPos pos, BlockState state) {
        super(BarBlocks.SINUCA_BE, pos, state);
        rack();
    }

    /** Lisas (1) ou listradas (2); a 8 e a branca não têm grupo. */
    public static int groupOf(int ball) {
        return ball >= 1 && ball <= 7 ? 1 : ball >= 9 ? 2 : 0;
    }

    public int remaining(int grp) {
        int n = 0;
        for (int i = 1; i < N; i++) if (!in[i] && groupOf(i) == grp) n++;
        return n;
    }

    /** Arruma as bolas: branca na cabeceira, as 15 no triângulo com a 8 no meio e uma lisa e uma listrada nos cantos de trás. */
    public void rack() {
        x[0] = HEAD_X;
        z[0] = Z_MAX / 2;
        int[] order = {1, 9, 2, 10, 8, 3, 11, 4, 12, 5, 13, 6, 14, 7, 15};
        // embaralha mantendo a 1 na ponta, a 8 no meio e cantos de trás com grupos diferentes
        List<Integer> rest = new ArrayList<>(List.of(2, 3, 4, 5, 6, 7, 9, 10, 11, 12, 13, 14, 15));
        java.util.Collections.shuffle(rest, rnd);
        int k = 0;
        for (int i = 0; i < 15; i++) {
            if (i == 0) order[i] = 1;
            else if (i == 4) order[i] = 8;
            else order[i] = rest.get(k++);
        }
        // cantos da última fileira (posições 10 e 14): uma lisa e uma listrada
        if (groupOf(order[10]) == groupOf(order[14])) {
            for (int i = 11; i < 14; i++) {
                if (groupOf(order[i]) != groupOf(order[14])) {
                    int t = order[i]; order[i] = order[10]; order[10] = t;
                    break;
                }
            }
        }
        k = 0;
        for (int row = 0; row < 5; row++) {
            for (int i = 0; i <= row; i++) {
                int b = order[k++];
                x[b] = FOOT_X + row * (R * 1.74);
                z[b] = Z_MAX / 2 + (i - row / 2.0) * (R * 2.01);
            }
        }
        for (int i = 0; i < N; i++) {
            vx[i] = vz[i] = 0;
            in[i] = false;
            px[i] = x[i];
            pz[i] = z[i];
        }
        moving = false;
        group[0] = group[1] = 0;
        names[0] = tournamentRunning() ? tourPlayer : "";
        names[1] = againstBot ? BOT : "";
        if (!tournamentRunning()) botName = "";
        turn = 0;
        winner = -1;
        breakShot = true;
        shots[0] = shots[1] = 0;
    }

    /** Tacada: ângulo nas coordenadas da mesa e força de 0 a 1. */
    public void shoot(double angle, double power) {
        if (moving || in[0] || winner >= 0) return;
        double speed = 0.02 + Math.max(0, Math.min(1, power)) * 0.2; // blocos por tick
        vx[0] = Math.cos(angle) * speed;
        vz[0] = Math.sin(angle) * speed;
        moving = true;
        shots[turn]++;
        firstHit = -1;
        cuePotted = false;
        pottedNow.clear();
        if (level != null) level.playSound(null, worldPosition, SoundEvents.STONE_HIT, SoundSource.BLOCKS, 0.4f + (float) power * 0.6f, 1.8f);
        changed();
    }

    /** Nome de quem deve jogar agora ("" = qualquer um pode assumir esse lado). */
    public String turnName() {
        return names[turn];
    }

    /** Pedido de tacada de um jogador (só na vez dele). */
    public void playerShot(ServerPlayer p, double angle, double power) {
        if (moving) return;
        if (winner >= 0) {
            p.sendOverlayMessage(Component.literal("§eA partida acabou; as bolas já voltam ao triângulo."));
            return;
        }
        String me = p.getName().getString();
        if (tournamentRunning() && !me.equals(tourPlayer)) {
            p.sendOverlayMessage(Component.literal("§eTá rolando o torneio de " + tourPlayer + ". Espera acabar!"));
            return;
        }
        if (againstBot && names[1].isEmpty()) names[1] = BOT;
        if (names[turn].isEmpty()) {
            // lado livre: quem tacar fica com ele (sozinho na mesa, você joga dos dois lados, como treino)
            names[turn] = me;
        } else if (!names[turn].equals(me)) {
            p.sendOverlayMessage(Component.literal("§eEspera, é a vez de " + names[turn] + "!"));
            return;
        }
        message = "Tacada de " + me;
        shoot(angle, power);
    }

    private void changed() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SinucaBlockEntity be) {
        if (!(level instanceof ServerLevel sl)) return;
        if (level.getGameTime() % 40 == 0) {
            boolean bot = be.tournamentRunning() || !sl.getEntitiesOfClass(Raver.class, new AABB(pos).inflate(8), Raver::playingPool).isEmpty();
            if (bot != be.againstBot) {
                be.againstBot = bot;
                if (bot && be.names[1].isEmpty()) be.names[1] = BOT;
                be.changed();
            }
        }
        if (be.moving) {
            for (int s = 0; s < 8; s++) be.physics(sl);
            boolean still = true;
            for (int i = 0; i < N; i++) if (!be.in[i] && (be.vx[i] != 0 || be.vz[i] != 0)) still = false;
            if (still) be.endShot(sl);
            be.changed();
            return;
        }
        if (be.wait > 0 && --be.wait == 0) {
            be.rack();
            be.message = "Bolas no triângulo. Nova partida!";
            be.changed();
        }
        if (be.winner < 0 && be.wait == 0 && BOT.equals(be.names[be.turn]) && be.againstBot && level.getGameTime() % 50 == 0) be.botShot();
    }

    /** Um passo de 1/8 de tick. Atrito de rolamento (desacelera sempre um pouco) + perda nas tabelas e batidas. */
    private void physics(ServerLevel sl) {
        double dt = 1.0 / 8;
        for (int i = 0; i < N; i++) {
            if (in[i]) continue;
            x[i] += vx[i] * dt;
            z[i] += vz[i] * dt;
            double sp = Math.hypot(vx[i], vz[i]);
            if (sp > 0) {
                double ns = Math.max(0, sp * Math.pow(0.988, dt) - 0.0009 * dt);
                if (ns < 0.0006) ns = 0;
                vx[i] *= ns / sp;
                vz[i] *= ns / sp;
            }
            for (double[] p : POCKETS) {
                if (Math.hypot(x[i] - p[0], z[i] - p[1]) < POCKET) {
                    in[i] = true;
                    vx[i] = vz[i] = 0;
                    sl.playSound(null, worldPosition, SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 0.7f, 0.8f);
                    if (i == 0) cuePotted = true;
                    else pottedNow.add(i);
                    break;
                }
            }
            if (in[i]) continue;
            // tabelas (perdem força na batida)
            if (x[i] < MARGIN) { x[i] = MARGIN; vx[i] = -vx[i] * 0.75; cushion(sl, vx[i]); }
            if (x[i] > X_MAX - MARGIN) { x[i] = X_MAX - MARGIN; vx[i] = -vx[i] * 0.75; cushion(sl, vx[i]); }
            if (z[i] < MARGIN) { z[i] = MARGIN; vz[i] = -vz[i] * 0.75; cushion(sl, vz[i]); }
            if (z[i] > Z_MAX - MARGIN) { z[i] = Z_MAX - MARGIN; vz[i] = -vz[i] * 0.75; cushion(sl, vz[i]); }
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
                    double imp = rel * 0.97; // bolas quase perfeitamente elásticas
                    vx[i] -= imp * nx; vz[i] -= imp * nz;
                    vx[j] += imp * nx; vz[j] += imp * nz;
                    if (firstHit < 0 && (i == 0 || j == 0)) firstHit = i == 0 ? j : i;
                    if (rel > 0.01) sl.playSound(null, worldPosition, SoundEvents.STONE_HIT, SoundSource.BLOCKS, (float) Math.min(1, rel * 6), 2f);
                }
            }
        }
    }

    private void cushion(ServerLevel sl, double v) {
        if (Math.abs(v) > 0.03) sl.playSound(null, worldPosition, SoundEvents.WOOL_HIT, SoundSource.BLOCKS, 0.5f, 0.8f);
    }

    private String side(int s) {
        if (BOT.equals(names[s]) && !botName.isEmpty()) return botName;
        return names[s].isEmpty() ? (s == 0 ? "Jogador 1" : "Jogador 2") : names[s];
    }

    // ------------------------------------------------------------ torneio de sinuca (3.4)
    private static final String[] RIVALS = {"Tião da Caçapa", "Biu Taco Fino", "Nenzinho", "Dona Cida", "Carlão do Giz", "Juninho Efeito",
            "Peixoto", "Rosinha Tabela", "Zé Bolinha", "Mestre Valdir"};
    /** Quem está no torneio ("" = sem torneio), fase (0 = semifinal, 1 = final), adversários e nome do aldeão da vez. */
    public String tourPlayer = "", botName = "";
    public int tourStage;
    private final String[] tourOpp = {"", "", ""};

    public boolean tournamentRunning() {
        return !tourPlayer.isEmpty();
    }

    /** Começa o torneio: chave com 4 (você e 3 aldeões). Semifinal contra um; a outra semi é jogada ao lado. */
    public boolean startTournament(ServerPlayer p) {
        if (tournamentRunning() && !tourPlayer.equals(p.getName().getString())) return false;
        List<String> pool = new ArrayList<>(List.of(RIVALS));
        java.util.Collections.shuffle(pool, rnd);
        for (int i = 0; i < 3; i++) tourOpp[i] = pool.get(i);
        tourPlayer = p.getName().getString();
        tourStage = 0;
        botName = tourOpp[0];
        againstBot = true;
        rack();
        message = "Torneio! Semifinal: " + tourPlayer + " x " + botName;
        tell("§6§l🎱 Torneio de Sinuca do Seu Zé! §r§fChave: §e" + tourPlayer + " x " + tourOpp[0] + "§f e §e" + tourOpp[1] + " x " + tourOpp[2]
                + "§f. Ganhe a semifinal e a final para levar o troféu.");
        changed();
        return true;
    }

    private void tell(String msg) {
        if (!(level instanceof ServerLevel sl)) return;
        for (ServerPlayer pl : sl.players()) {
            if (pl.blockPosition().distSqr(worldPosition) < 24 * 24) pl.sendSystemMessage(Component.literal(msg));
        }
    }

    /** Fim de uma partida do torneio. */
    private void tournamentResult(ServerLevel sl, boolean playerWon) {
        if (!tournamentRunning()) return;
        ServerPlayer p = sl.getServer().getPlayerList().getPlayerByName(tourPlayer);
        if (tourStage == 0) {
            String other = rnd.nextBoolean() ? tourOpp[1] : tourOpp[2];
            String loserOther = other.equals(tourOpp[1]) ? tourOpp[2] : tourOpp[1];
            tell("§7Na outra semifinal, §f" + other + "§7 ganhou de §f" + loserOther + "§7.");
            if (playerWon) {
                tourStage = 1;
                botName = other;
                tell("§a§lVocê está na FINAL! §r§fAdversário: §e" + other + "§f. Ele joga melhor, hein...");
            } else {
                tell("§cEliminado na semifinal! §f" + botName + " ganhou e vai pegar " + other + " na final.");
                endTournament();
            }
        } else {
            if (playerWon) {
                tell("§6§l🏆 " + tourPlayer.toUpperCase() + " É CAMPEÃO DO TORNEIO DE SINUCA! 🏆");
                if (p != null) {
                    ItemStack trophy = new ItemStack(Blocks34.TROFEU_SINUCA);
                    if (!p.getInventory().add(trophy)) net.minecraft.world.Containers.dropItemStack(p.level(), p.getX(), p.getY(), p.getZ(), trophy);
                    com.boombox.Achievements.award(p, "rei_da_sinuca");
                }
                sl.sendParticles(net.minecraft.core.particles.ParticleTypes.FIREWORK, worldPosition.getX() + 0.5, worldPosition.getY() + 2, worldPosition.getZ() + 0.5, 60, 1, 1, 1, 0.15);
            } else {
                tell("§cVice-campeão! §f" + botName + " levou o troféu dessa vez.");
            }
            endTournament();
        }
        changed();
    }

    private void endTournament() {
        tourPlayer = "";
        tourStage = 0;
    }

    public static String groupName(int g) {
        return g == 1 ? "lisas" : g == 2 ? "listradas" : "mesa aberta";
    }

    private void endShot(ServerLevel sl) {
        moving = false;
        int me = turn, opp = 1 - turn;
        boolean foul = cuePotted || firstHit < 0;
        String why = cuePotted ? "matou a branca" : firstHit < 0 ? "não acertou nenhuma bola" : "";
        if (!foul && !breakShot) {
            if (group[me] != 0) {
                boolean on8 = remaining(group[me]) == 0 && !pottedNow.stream().anyMatch(b -> groupOf(b) == group[me]);
                if (firstHit == 8 && !on8) { foul = true; why = "tocou primeiro na 8"; }
                else if (firstHit != 8 && groupOf(firstHit) != group[me]) { foul = true; why = "tocou primeiro na bola do outro"; }
            } else if (firstHit == 8) {
                foul = true;
                why = "tocou primeiro na 8";
            }
        }
        boolean potted8 = pottedNow.contains(8);
        if (potted8) {
            if (breakShot && !foul) {
                // 8 na saída: volta para o lugar dela
                in[8] = false;
                x[8] = FOOT_X + 2 * R * 1.74;
                z[8] = Z_MAX / 2;
                unstack(8);
                message = "A 8 caiu na saída e voltou para o lugar.";
            } else {
                boolean cleared = group[me] != 0 && remaining(group[me]) == 0;
                winner = cleared && !foul ? me : opp;
                message = winner == me ? side(me) + " matou a 8 e ganhou a partida!"
                        : side(me) + " matou a 8 antes da hora" + (foul ? " (com falta)" : "") + ". " + side(opp) + " ganhou!";
                sl.playSound(null, worldPosition, winner == me ? SoundEvents.PLAYER_LEVELUP : SoundEvents.VILLAGER_NO, SoundSource.BLOCKS, 1f, 1f);
                if (tournamentRunning()) tournamentResult(sl, names[winner].equals(tourPlayer));
                if (winner == me && !names[me].equals(BOT)) {
                    ServerPlayer p = sl.getServer().getPlayerList().getPlayerByName(names[me]);
                    if (p != null) {
                        com.boombox.Achievements.award(p, "matou_a_8");
                        // limpou a mesa sem o outro tacar nenhuma vez
                        if (shots[opp] == 0 && !names[opp].equals(names[me])) com.boombox.Achievements.award(p, "matou_a_8_de_primeira");
                    }
                }
                wait = 120;
                return;
            }
        }
        // mesa aberta: a primeira bola encaçapada (sem falta, fora da saída) define os grupos
        if (group[me] == 0 && !foul && !breakShot) {
            for (int b : pottedNow) {
                if (groupOf(b) != 0) {
                    group[me] = groupOf(b);
                    group[opp] = 3 - group[me];
                    message = side(me) + " fica com as " + groupName(group[me]) + "!";
                    break;
                }
            }
        }
        boolean pottedOwn = pottedNow.stream().anyMatch(b -> b != 8 && (group[me] == 0 || groupOf(b) == group[me]));
        if (cuePotted) {
            in[0] = false;
            x[0] = HEAD_X;
            z[0] = Z_MAX / 2;
            unstack(0);
        }
        if (foul) {
            turn = opp;
            message = "Falta: " + side(me) + " " + why + ". Vez de " + side(opp) + ".";
        } else if (pottedOwn) {
            if (!message.contains("fica com")) message = "Boa, " + side(me) + "! Joga de novo.";
        } else {
            turn = opp;
            message = "Vez de " + side(opp) + ".";
        }
        breakShot = false;
        // treino sozinho na mesa: o lado da vez continua com você
        if (!againstBot && names[turn].isEmpty() && !names[1 - turn].equals(BOT)) names[turn] = names[1 - turn];
    }

    /** Afasta a bola recolocada de outras que estejam no mesmo lugar. */
    private void unstack(int b) {
        for (int tries = 0; tries < 40; tries++) {
            boolean ok = true;
            for (int i = 0; i < N; i++) {
                if (i == b || in[i]) continue;
                if (Math.hypot(x[i] - x[b], z[i] - z[b]) < 2 * R) { ok = false; break; }
            }
            if (ok) return;
            x[b] += (b == 0 ? -1 : 1) * R * 0.7;
            if (x[b] < MARGIN || x[b] > X_MAX - MARGIN) { x[b] = b == 0 ? HEAD_X : FOOT_X; z[b] += R * 2.2; }
        }
    }

    /** O aldeão mira na bola mais fácil do grupo dele para uma caçapa, com um pouco de erro. */
    private void botShot() {
        int g = group[turn];
        boolean on8 = g != 0 && remaining(g) == 0;
        double best = -1e9, ang = 0, dist = 1;
        for (int b = 1; b < N; b++) {
            if (in[b]) continue;
            if (on8 ? b != 8 : (b == 8 || (g != 0 && groupOf(b) != g))) continue;
            for (double[] p : POCKETS) {
                double qx = p[0] - x[b], qz = p[1] - z[b], ql = Math.hypot(qx, qz);
                double gx = x[b] - qx / ql * 2 * R, gz = z[b] - qz / ql * 2 * R;
                double cx = gx - x[0], cz = gz - z[0], cl = Math.hypot(cx, cz);
                double align = (cx * qx + cz * qz) / (cl * ql + 1e-6);
                if (align < 0.2) continue;
                double score = align * 2 - (cl + ql) / 3 - (blocked(x[0], z[0], gx, gz, b) ? 3 : 0);
                if (score > best) {
                    best = score;
                    ang = Math.atan2(cz, cx);
                    dist = cl + ql;
                }
            }
        }
        if (best == -1e9) ang = rnd.nextDouble() * Math.PI * 2;
        double err = breakShot ? 0.05 : tournamentRunning() ? (tourStage == 1 ? 0.026 : 0.038) : 0.045;
        ang += (rnd.nextDouble() - 0.5) * err;
        message = "Tacada de " + side(turn);
        shoot(ang, breakShot ? 0.9 : Math.min(1, 0.25 + dist * 0.22 + rnd.nextDouble() * 0.1));
    }

    /** Alguma bola no caminho reto entre a branca e o ponto de contato? */
    private boolean blocked(double ax, double az, double bx, double bz, int target) {
        double dx = bx - ax, dz = bz - az, len = Math.hypot(dx, dz);
        if (len < 1e-6) return false;
        dx /= len; dz /= len;
        for (int i = 1; i < N; i++) {
            if (i == target || in[i]) continue;
            double t = (x[i] - ax) * dx + (z[i] - az) * dz;
            if (t <= 0 || t >= len) continue;
            double perp = Math.abs((x[i] - ax) * dz - (z[i] - az) * dx);
            if (perp < 2 * R) return true;
        }
        return false;
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
        if (!b.isEmpty()) {
            // começa do ponto em que a bola está sendo desenhada agora (sem pulo para trás)
            double a = syncAlpha();
            for (int i = 0; i < N; i++) {
                px[i] = px[i] + (x[i] - px[i]) * a;
                pz[i] = pz[i] + (z[i] - pz[i]) * a;
            }
            decode(b);
            if (level == null || level.isClientSide()) syncNanos = System.nanoTime();
        }
        group[0] = in_.getIntOr("G0", 0);
        group[1] = in_.getIntOr("G1", 0);
        names[0] = in_.getStringOr("P0", "");
        names[1] = in_.getStringOr("P1", "");
        turn = in_.getIntOr("Turn", 0);
        winner = in_.getIntOr("Winner", -1);
        breakShot = in_.getBooleanOr("Break", true);
        againstBot = in_.getBooleanOr("AgainstBot", false);
        moving = in_.getBooleanOr("Moving", false);
        message = in_.getStringOr("Msg", message);
        tourPlayer = in_.getStringOr("TourPlayer", "");
        tourStage = in_.getIntOr("TourStage", 0);
        botName = in_.getStringOr("BotName", "");
        String[] o = in_.getStringOr("TourOpp", "||").split("[|]", -1);
        for (int i = 0; i < 3 && i < o.length; i++) tourOpp[i] = o[i];
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putString("Balls", encode());
        out.putInt("G0", group[0]);
        out.putInt("G1", group[1]);
        out.putString("P0", names[0]);
        out.putString("P1", names[1]);
        out.putInt("Turn", turn);
        out.putInt("Winner", winner);
        out.putBoolean("Break", breakShot);
        out.putBoolean("AgainstBot", againstBot);
        out.putBoolean("Moving", moving);
        out.putString("Msg", message);
        out.putString("TourPlayer", tourPlayer);
        out.putInt("TourStage", tourStage);
        out.putString("BotName", botName);
        out.putString("TourOpp", String.join("|", tourOpp));
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
