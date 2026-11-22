package com.boombox.entity;

import com.boombox.net.Payloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * 3.5: coisas comuns do futebol e do vôlei: botões, organizador (amistoso ou campeonato), nomes e cores de time.
 * <p>
 * Botões no futebol: esquerdo = chutar, direito = passar, R = carrinho, G = colocar o pé (desarme sem falta).
 * No vôlei: esquerdo = manchete (passa para o parceiro), V = levantar, direito = cortar.
 */
public final class Sports {
    public static final int FUT_SHOOT = 1, FUT_PASS = 2, FUT_SLIDE = 3, FUT_POKE = 4, VOL_BUMP = 5, VOL_SET = 6, VOL_SPIKE = 7, LEAVE = 8, GK_DIVE = 9;
    public static final int NONE = 0, FOOTBALL = 1, VOLLEY = 2, SINUCA = 3;
    /** 3.7: tecla da tabela (abre a tela da copa ou da chave de sinuca mais perto). */
    public static final int OPEN_TABLE = 10;
    public static final int AMISTOSO = 0, CAMPEONATO = 1, JUNTAR = 2, TABELA = 3, CONTRA = 4; // CONTRA (3.7) = entrar no time adversário // TABELA (3.6) = só ver a tabela da copa // 3.5.5: JUNTAR = entrar no time de quem já está jogando

    /** Cores que dá para escolher para o time (e que os times sorteados usam). */
    public static final int[] COLORS = {0x2D5BE0, 0xE03030, 0x2E8B3A, 0xF0D020, 0xF0F0F0, 0x202020, 0x8E24AA, 0xE66A10, 0x10B0C8, 0xFF6FA8, 0x6D4C2F, 0x8A0E1E};
    public static final String[] COLOR_NAMES = {"Azul", "Vermelho", "Verde", "Amarelo", "Branco", "Preto", "Roxo", "Laranja", "Celeste", "Rosa", "Marrom", "Grená"};

    private static final String[] FUT_NAMES = {"Real Taiga", "Atlético Savana", "Grêmio da Neve", "União da Planície", "Juventude do Deserto",
            "Ferroviário do Pântano", "Estrela do Mar", "Independente do Morro", "Esporte Clube Ladeira", "Vila Nova da Mina", "Bonsucesso do Rio",
            "Operário da Pedreira", "Unidos da Ponte", "Atlético Cascalho", "Tropeiros da Serra", "Ipê Amarelo FC", "Lagoa Azul EC",
            "Canarinho do Brejo", "Sereno FC"};
    private static final String[] VOL_NAMES = {"Areia Quente", "Tubarões da Praia", "Coqueiral", "Maré Alta", "Sol Nascente", "Onda Azul",
            "Gaivotas", "Pé na Areia", "Canga Branca", "Brisa do Mar", "Duna Dourada", "Concha Rosa", "Recife de Coral", "Maresia",
            "Farol do Sul", "Pôr do Sol", "Arraia Voadora"};

    private Sports() {}

    public static void sendState(ServerPlayer p, int sport) {
        sendState(p, sport, false);
    }

    public static void sendState(ServerPlayer p, int sport, boolean league) {
        sendState(p, sport, league, -1);
    }

    public static void sendState(ServerPlayer p, int sport, boolean league, int position) {
        ServerPlayNetworking.send(p, new Payloads.SportState(sport, league, position));
    }

    /** 3.6: manda a tabela da copa (grupos e chaveamento) para a tela da pessoa. */
    public static void sendCup(ServerPlayer p, Cup cup, int sport) {
        if (p instanceof net.fabricmc.fabric.api.entity.FakePlayer) return;
        ServerPlayNetworking.send(p, new Payloads.CupState(sport, cup.encode()));
    }

    /** Uma cor bem diferente dessa (camisa do adversário quando as duas são parecidas). */
    public static int contrast(int c) {
        for (int x : COLORS) if (!similar(x, c)) return x;
        return 0xFFFFFF - c;
    }

    /** 3.7: a tela do chaveamento: da copa em que a pessoa joga, ou da copa / torneio de sinuca mais perto. */
    public static void openTable(ServerPlayer p, ServerLevel sl) {
        for (Ball b : sl.getEntitiesOfClass(Ball.class, p.getBoundingBox().inflate(120))) {
            if (b.teamOf(p) >= 0 && b.cup() != null) { sendCup(p, b.cup(), FOOTBALL); return; }
        }
        for (Volleyball v : sl.getEntitiesOfClass(Volleyball.class, p.getBoundingBox().inflate(80))) {
            if (v.sideOf(p) >= 0 && v.cup() != null) { sendCup(p, v.cup(), VOLLEY); return; }
        }
        for (var pos : net.minecraft.core.BlockPos.betweenClosed(p.blockPosition().offset(-12, -4, -12), p.blockPosition().offset(12, 4, 12))) {
            if (sl.getBlockEntity(pos) instanceof com.boombox.bar.SinucaBlockEntity t && !t.bracket[0].isEmpty()) { t.sendBracket(p); return; }
        }
        for (Ball b : sl.getEntitiesOfClass(Ball.class, p.getBoundingBox().inflate(70))) {
            if (b.cup() != null) { sendCup(p, b.cup(), FOOTBALL); return; }
        }
        for (Volleyball v : sl.getEntitiesOfClass(Volleyball.class, p.getBoundingBox().inflate(40))) {
            if (v.cup() != null) { sendCup(p, v.cup(), VOLLEY); return; }
        }
        p.sendSystemMessage(Component.literal("§7Nenhuma copa ou torneio por perto. Marque um campeonato com o organizador do campo ou da quadra, ou fale com o Seu Zé para o torneio de sinuca."));
    }

    /** Distância entre duas cores (para não sortear times parecidos). */
    public static boolean similar(int a, int b) {
        int dr = ((a >> 16) & 255) - ((b >> 16) & 255), dg = ((a >> 8) & 255) - ((b >> 8) & 255), db = (a & 255) - (b & 255);
        return dr * dr + dg * dg + db * db < 110 * 110;
    }

    /** Sorteia n times diferentes (nome e cor), sem repetir e sem cor parecida com as que já existem. */
    public static List<String[]> randomTeams(RandomSource r, boolean volley, int n, List<String> usedNames, List<Integer> usedColors) {
        List<String> names = new ArrayList<>(List.of(volley ? VOL_NAMES : FUT_NAMES));
        names.removeAll(usedNames);
        List<Integer> colors = new ArrayList<>();
        for (int c : COLORS) colors.add(c);
        List<String[]> out = new ArrayList<>();
        List<Integer> taken = new ArrayList<>(usedColors);
        for (int i = 0; i < n; i++) {
            String name = names.isEmpty() ? "Time " + (i + 1) : names.remove(r.nextInt(names.size()));
            int color = COLORS[r.nextInt(COLORS.length)];
            java.util.Collections.shuffle(colors, new java.util.Random(r.nextLong()));
            for (int c : colors) {
                boolean ok = true;
                for (int t : taken) if (similar(c, t)) ok = false;
                if (ok) { color = c; break; }
            }
            taken.add(color);
            out.add(new String[]{name, Integer.toString(color)});
        }
        return out;
    }

    /** Organizador perto (para a tela) → bola de futebol ou de vôlei mais perto dele. */
    public static Ball footballNear(ServerLevel sl, Vec3 at) {
        var l = sl.getEntitiesOfClass(Ball.class, new AABB(at, at).inflate(48));
        l.sort((a, b) -> Double.compare(a.distanceToSqr(at), b.distanceToSqr(at)));
        return l.isEmpty() ? null : l.get(0);
    }

    public static Volleyball volleyNear(ServerLevel sl, Vec3 at) {
        var l = sl.getEntitiesOfClass(Volleyball.class, new AABB(at, at).inflate(32));
        l.sort((a, b) -> Double.compare(a.distanceToSqr(at), b.distanceToSqr(at)));
        return l.isEmpty() ? null : l.get(0);
    }

    /**
     * 3.5.1: esse ponto está perto (até "margin" blocos) de um campo de várzea ou de uma quadra de vôlei? Usado para
     * não começar carnaval, aniversário e outros eventos em cima do jogo.
     */
    public static boolean nearSportsArea(ServerLevel sl, Vec3 at, double margin) {
        for (Ball b : sl.getEntitiesOfClass(Ball.class, new AABB(at, at).inflate(70 + margin))) {
            if (b.hasGoals() && b.inArea(at, margin)) return true;
        }
        for (Volleyball v : sl.getEntitiesOfClass(Volleyball.class, new AABB(at, at).inflate(30 + margin))) {
            if (v.hasCourt() && v.inArea(at, margin)) return true;
        }
        return false;
    }

    /** Leva para fora do lugar quem não faz parte do jogo (monstro some). */
    static void evict(net.minecraft.server.level.ServerLevel sl, net.minecraft.world.entity.Entity e, net.minecraft.world.phys.Vec3 out) {
        if (e instanceof net.minecraft.world.entity.monster.Enemy) {
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, e.getX(), e.getY() + 0.8, e.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
            e.discard();
            return;
        }
        if (e instanceof Trio t) {
            t.getPassengers().forEach(net.minecraft.world.entity.Entity::discard);
            t.discard();
            return;
        }
        int y = sl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(out.x), (int) Math.floor(out.z));
        e.stopRiding();
        e.teleportTo(out.x, y, out.z);
        if (e instanceof net.minecraft.world.entity.Mob m) {
            m.getNavigation().stop();
            if (m instanceof net.minecraft.world.entity.PathfinderMob pm && pm.hasHome()) pm.clearHome();
        }
    }

    /** 3.5.3: posições que dá para escolher no futebol. */
    public static final String[] POSITIONS = {"Goleiro", "Zagueiro", "Lateral", "Volante", "Meia", "Ponta", "Atacante"};
    /** Lugar (número da posição dos aldeões, "stall") de cada posição no time de 5: goleiro, defesa, meio, ala e ataque. */
    public static final int[] POSITION_SLOT = {1, 3, 3, 6, 6, 9, 11};
    /** Para cada posição, os números (stall) dos aldeões que ela substitui, em ordem de preferência. */
    public static final int[][] POSITION_SLOTS = {{1}, {12, 3, 4}, {12, 2, 5}, {13, 6}, {13, 7, 8}, {14, 9, 10}, {15, 11}};

    public static void action(ServerPlayer p, int action) {
        action(p, action, 0f);
    }

    /** Partida em que essa pessoa está jogando (futebol primeiro). */
    public static void action(ServerPlayer p, int action, float power) {
        ServerLevel sl = (ServerLevel) p.level();
        if (action == OPEN_TABLE) {
            openTable(p, sl);
            return;
        }
        if (action == LEAVE) {
            // 3.5.2: sair do amistoso / desistir do campeonato (futebol ou vôlei)
            for (Ball b : sl.getEntitiesOfClass(Ball.class, p.getBoundingBox().inflate(120))) {
                if (b.teamOf(p) >= 0) { b.leave(p); return; }
            }
            for (Volleyball v : sl.getEntitiesOfClass(Volleyball.class, p.getBoundingBox().inflate(120))) {
                if (v.sideOf(p) >= 0) { v.leave(p); return; }
            }
            sendState(p, NONE); // não estava em jogo nenhum: só limpa os botões
            return;
        }
        if (action <= FUT_POKE || action == GK_DIVE) {
            for (Ball b : sl.getEntitiesOfClass(Ball.class, p.getBoundingBox().inflate(80))) {
                if (b.teamOf(p) >= 0) { b.playerAction(p, action, power); return; }
            }
        } else {
            for (Volleyball v : sl.getEntitiesOfClass(Volleyball.class, p.getBoundingBox().inflate(48))) {
                if (v.sideOf(p) >= 0) { v.playerAction(p, action, power); return; }
            }
        }
    }
}
